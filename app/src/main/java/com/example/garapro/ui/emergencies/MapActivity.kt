package com.example.garapro.ui.emergencies

import com.example.garapro.ui.emergencies.EmergencyViewModel
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.VectorDrawable
import android.location.LocationManager
import org.maplibre.android.style.layers.Property
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.emergencies.Garage
import com.example.garapro.data.model.Vehicles.Vehicle
import com.example.garapro.data.remote.RetrofitInstance
import com.example.garapro.hubs.EmergencySignalRService

import com.google.android.gms.location.*
import com.google.android.gms.common.api.ResolvableApiException
import android.content.Intent
import android.app.Activity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.example.garapro.ui.repairRequest.VehicleAdapter
import com.example.garapro.data.model.repairRequest.Vehicle as RRVehicle
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.sources.GeoJsonSource

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private var mapView: MapView? = null
    private var maplibreMap: MapLibreMap? = null
    private val markerPositions: MutableList<LatLng> = ArrayList()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationPermissionGranted = false

    // UI Components
    private lateinit var topAppBar: View
    private lateinit var btnBack: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var bottomSheetContainer: FrameLayout
    private lateinit var fabEmergency: FloatingActionButton
    private lateinit var fabCurrentLocation: FloatingActionButton
    private lateinit var loadingIndicator: ProgressBar

    private lateinit var emergencyBottomSheet: EmergencyBottomSheet

    // Bottom Sheet
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    // ViewModel
    private lateinit var viewModel: EmergencyViewModel

    // Adapter
    private lateinit var garageAdapter: GarageAdapter
    private var styleLoaded = false
    private var activityActive = false

    private var lastTappedLatLng: LatLng? = null
    private var selectedVehicleId: String? = null
    private var pendingIssueDescription: String? = null
    private var pendingLatLng: LatLng? = null
    private var emergencyHub: EmergencySignalRService? = null
    private val rejectedGarageIds = mutableSetOf<String>()
    private var trackingActive: Boolean = false
    private var cameraFollowTechnician: Boolean = false
    private var technicianLatLng: LatLng? = null
    private var technicianName: String? = null
    private var technicianPhone: String? = null
    private var technicianArrived: Boolean = false
    private var destinationLatLng: LatLng? = null
    private var waitingForGarageActive: Boolean = false
    private var routeFetchPending: Boolean = false
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    private var fallbackStyleRunnable: Runnable? = null
    private var blockHubUI: Boolean = false
    private var arrivalConsecutive: Int = 0
    private var lastArrivalCandidateAt: Long = 0L
    private val ARRIVAL_CONFIRM_COUNT = 1
    private val ARRIVAL_CONFIRM_MS = 4000L
    private var inProgressStartedAt: Long = 0L


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize MapLibre
        val mapLibre = MapLibre.getInstance(
            this,
            getString(R.string.goong_map_key),
            WellKnownTileServer.Mapbox
        )

        setContentView(R.layout.activity_map)

        // Initialize ViewModel
        viewModel = EmergencyViewModel()
        emergencyBottomSheet = EmergencyBottomSheet(this, viewModel)
        // Initialize UI Components
        initViews()
        // setupBottomSheet()
        setupClickListeners()
        setupObservers()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val prefs = getSharedPreferences(
            com.example.garapro.utils.Constants.USER_PREFERENCES,
            Context.MODE_PRIVATE
        )
        val userId = prefs.getString("user_id", null)
        val hubUrl =
            com.example.garapro.utils.Constants.BASE_URL_SIGNALR + "/api/emergencyrequesthub"
        emergencyHub = EmergencySignalRService(hubUrl).apply {
            setupListeners()
            start {
                userId?.let {
                    android.util.Log.d("EmergencyHubJoin", "joinCustomerGroup id=" + it)
                    joinCustomerGroup(it)
                }
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (emergencyHub?.isConnected() != true) {
                val alt1 =
                    com.example.garapro.utils.Constants.BASE_URL_SIGNALR + "/hubs/emergencyrequest"
                android.util.Log.d("EmergencyHub", "attempt reconnect alt1=" + alt1)
                emergencyHub?.reconnectWithUrl(alt1) {
                    userId?.let {
                        android.util.Log.d(
                            "EmergencyHubJoin",
                            "joinCustomerGroup after reconnect id=" + it
                        )
                        emergencyHub?.joinCustomerGroup(it)
                    }
                }
            }
        }, 3000)
        Handler(Looper.getMainLooper()).postDelayed({
            if (emergencyHub?.isConnected() != true) {
                val alt2 = com.example.garapro.utils.Constants.BASE_URL_SIGNALR + "/hubs/emergency"
                android.util.Log.d("EmergencyHub", "attempt reconnect alt2=" + alt2)
                emergencyHub?.reconnectWithUrl(alt2) {
                    userId?.let {
                        android.util.Log.d(
                            "EmergencyHubJoin",
                            "joinCustomerGroup after reconnect id=" + it
                        )
                        emergencyHub?.joinCustomerGroup(it)
                    }
                }
            }
        }, 7000)
        val forceNew = intent.getBooleanExtra("force_new", false)
        val eid = intent.getStringExtra("emergency_id")
        val hasId = eid?.isNotBlank() == true
        android.util.Log.d(
            "MapActivity",
            "onCreate forceNew=" + forceNew + ", hasId=" + hasId + ", eid=" + (eid ?: "")
        )
        if (hasId) {
            initMapView(savedInstanceState)
            checkLocationPermission()
            val st = intent.getStringExtra("status")?.lowercase()
            if (st == "inprogress" || st == "in_progress") {
                val g = viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
                val minutes: Int? = null
                topAppBar.visibility = View.VISIBLE
                tvTitle.text = "Tracking technician"
                enableTrackingUI()
                if (g != null) emergencyBottomSheet.showTracking(
                    g,
                    minutes
                ) else emergencyBottomSheet.showTracking(
                    com.example.garapro.data.model.emergencies.Garage(
                        id = "",
                        name = "Garage",
                        latitude = 0.0,
                        longitude = 0.0,
                        address = "",
                        phone = "",
                        isAvailable = true,
                        price = 0.0,
                        rating = 0f,
                        distance = 0.0
                    ),
                    minutes
                )
                routeFetchPending = true
            } else if (st == "accepted") {
                val g = viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
                if (g != null) emergencyBottomSheet.showAcceptedWaitingForTechnician(g)
            }
            recoverExistingEmergency()
        } else if (forceNew) {
            initMapView(savedInstanceState)
            checkLocationPermission()
            blockHubUI = true
            Handler(Looper.getMainLooper()).post { requestEmergency() }
        } else {
            val userPrefs = getSharedPreferences(
                com.example.garapro.utils.Constants.USER_PREFERENCES,
                Context.MODE_PRIVATE
            )
            val authPrefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val uid = userPrefs.getString("user_id", null) ?: authPrefs.getString("user_id", null)
            if (uid.isNullOrBlank()) {
                initMapView(savedInstanceState)
                checkLocationPermission()
            } else {
                lifecycleScope.launchWhenCreated {
                    try {
                        val resp = withContext(Dispatchers.IO) {
                            com.example.garapro.data.remote.RetrofitInstance.emergencyService.getEmergenciesByCustomer(
                                uid
                            )
                        }
                        if (resp.isSuccessful && (resp.body()?.isNotEmpty() == true)) {
                            startActivity(
                                Intent(
                                    this@MapActivity,
                                    EmergencyListActivity::class.java
                                )
                            )
                            finishSafely()
                        } else {
                            initMapView(savedInstanceState)
                            checkLocationPermission()
                        }
                    } catch (_: Exception) {
                        initMapView(savedInstanceState)
                        checkLocationPermission()
                    }
                }
            }
        }
    }

    private fun initMapView(savedInstanceState: Bundle?) {
        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)
    }

    private fun initViews() {
        topAppBar = findViewById(R.id.topAppBar)
        btnBack = findViewById(R.id.btnBack)
        tvTitle = findViewById(R.id.tvTitle)
        bottomSheetContainer = findViewById(R.id.bottomSheetContainer)
        fabEmergency = findViewById(R.id.fabEmergency)
        fabCurrentLocation = findViewById(R.id.fabCurrentLocation)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        // Hide top app bar initially
        topAppBar.visibility = View.GONE
    }

    private fun setupBottomSheet() {
        // Inflate bottom sheet content
        val bottomSheetView =
            layoutInflater.inflate(R.layout.bottom_sheet_emergency_choose_garage, null)
        bottomSheetContainer.addView(bottomSheetView)

        // Get BottomSheetBehavior từ layout
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetContainer)

        // Cấu hình behavior
        bottomSheetBehavior.apply {
            state = BottomSheetBehavior.STATE_HIDDEN
            isFitToContents = false
            halfExpandedRatio = 0.5f
            expandedOffset = 100
            isHideable = true

            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            // Bottom sheet mở hoàn toàn
                        }

                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            // Bottom sheet đóng
                        }

                        BottomSheetBehavior.STATE_HIDDEN -> {
                            // Ẩn hoàn toàn
                            hideEmergencyUI()
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // Xử lý khi đang kéo
                }
            })
        }

        // Setup RecyclerView
        val rvGarages = bottomSheetView.findViewById<RecyclerView>(R.id.rvGarages)
        garageAdapter = GarageAdapter { garage ->
            viewModel.selectGarage(garage)
        }
        rvGarages.layoutManager = LinearLayoutManager(this)
        rvGarages.adapter = garageAdapter

        // Setup confirm button
        val btnConfirm = bottomSheetView.findViewById<Button>(R.id.btnConfirm)
        btnConfirm.setOnClickListener {
            val emergency = viewModel.getCurrentEmergency()
            emergency?.let {
                topAppBar.visibility = View.GONE
                viewModel.confirmEmergency(it.id)
            }
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            val garage = viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
            if (trackingActive && garage != null) {
                topAppBar.visibility = View.GONE
                emergencyBottomSheet.showTracking(garage, null)
                return@setOnClickListener
            }
            if (garage != null) {
                val tech = technicianLatLng
                if (tech != null) {
                    val destLat =
                        viewModel.getCurrentEmergency()?.latitude ?: pendingLatLng?.latitude
                    val destLng =
                        viewModel.getCurrentEmergency()?.longitude ?: pendingLatLng?.longitude
                    if (destLat != null && destLng != null) {
                        val d = haversineMeters(tech.latitude, tech.longitude, destLat, destLng)
                        if (d <= ARRIVAL_THRESHOLD_METERS) {
                            emergencyBottomSheet.setOnCloseClickListener { finishSafely() }
                            emergencyBottomSheet.showArrived(
                                garage,
                                technicianName,
                                technicianPhone
                            )
                        } else {
                            cameraFollowTechnician = false
                            emergencyBottomSheet.showAccepted(garage, null)
                            emergencyBottomSheet.setOnTrackClickListener { startTrackingTechnician() }
                        }
                    } else {
                        cameraFollowTechnician = false
                        emergencyBottomSheet.showAccepted(garage, null)
                        emergencyBottomSheet.setOnTrackClickListener { startTrackingTechnician() }
                    }
                } else {
                    cameraFollowTechnician = false
                    emergencyBottomSheet.showAccepted(garage, null)
                    emergencyBottomSheet.setOnTrackClickListener { startTrackingTechnician() }
                }
            } else {
                hideEmergencyUI()
            }
        }

        fabEmergency.setOnClickListener {
            requestEmergency()
        }

        fabCurrentLocation.setOnClickListener {
            getCurrentLocation()
        }
    }

    private fun setupObservers() {
        viewModel.emergencyState.observe(this) { state ->
            when (state) {
                is EmergencyState.Loading -> {
                    showLoading(true)
                }

                is EmergencyState.Success -> {
                    showLoading(false)
                    showEmergencyUI()
                    viewModel.getCurrentEmergency()?.id?.takeIf { it.isNotBlank() }
                        ?.let { saveLastEmergencyId(it) }
                }

                is EmergencyState.WaitingForGarage -> {
                    Log.d("EmergencyState", "🟢 WaitingForGarage triggered for ${state.garage.name}")
                    showLoading(false)
                    topAppBar.visibility = View.GONE
                    try {
                        fabEmergency.visibility = View.GONE
                    } catch (_: Exception) {
                    }
                    try {
                        fabCurrentLocation.visibility = View.GONE
                    } catch (_: Exception) {
                    }
                    waitingForGarageActive = true
                    mapView?.post {
                        emergencyBottomSheet?.showWaitingForGarage(state.garage)
                    }
                    viewModel.getCurrentEmergency()?.id?.takeIf { it.isNotBlank() }
                        ?.let { saveLastEmergencyId(it) }
                }

                is EmergencyState.Confirmed -> {
                    if (blockHubUI) return@observe
                    showLoading(false)
                    waitingForGarageActive = false
                    val emergency = viewModel.getCurrentEmergency()
                    val status = emergency?.status
                    val garage =
                        viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
                    if (status == com.example.garapro.data.model.emergencies.EmergencyStatus.IN_PROGRESS) {
                        val minutes: Int? = null
                        if (garage != null) {
                            emergencyBottomSheet.showTracking(garage, minutes)
                        } else {
                            val fallback = com.example.garapro.data.model.emergencies.Garage(
                                id = emergency?.assignedGarageId ?: "",
                                name = "Garage",
                                latitude = 0.0,
                                longitude = 0.0,
                                address = "",
                                phone = "",
                                isAvailable = true,
                                price = 0.0,
                                rating = 0f,
                                distance = 0.0
                            )
                            emergencyBottomSheet.showTracking(fallback, minutes)
                        }
                        emergencyBottomSheet.setOnViewMapClickListener {
                            cameraFollowTechnician = true
                            refreshTrackingFromApi()
                            topAppBar.visibility = View.VISIBLE
                            tvTitle.text = "Tracking technician"
                            enableTrackingUI()
                            val id2 = viewModel.getCurrentEmergency()?.id
                            if (!id2.isNullOrBlank()) viewModel.fetchRouteNow()
                        }
                        topAppBar.visibility = View.VISIBLE
                        tvTitle.text = "Tracking technician"
                        enableTrackingUI()
                        refreshTrackingFromApi()
                        if (styleLoaded) {
                            viewModel.fetchRouteNow()
                            viewModel.startRoutePolling()
                            routeFetchPending = false
                        } else {
                            routeFetchPending = true
                        }
                    } else {
                        if (garage != null) {
                            emergencyBottomSheet.showAcceptedWaitingForTechnician(garage)
                        }
                        Toast.makeText(
                            this,
                            "Garage accepted! Waiting for technician assignment",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    emergency?.id?.takeIf { it.isNotBlank() }?.let { saveLastEmergencyId(it) }
                }

                is EmergencyState.Error -> {
                    showLoading(false)
                    if (waitingForGarageActive) return@observe
                    val msg = state.message
                    val lower = msg.lowercase()
                    val isActiveEmergency =
                        lower.contains("existing emergency") || lower.contains("active emergency") || lower.contains(
                            "đang được xử lý"
                        ) || lower.contains("đang xử lí") || lower.contains("đã có đơn") || lower.contains(
                            "too many requests"
                        ) || lower.contains("429")
                    val isRejected =
                        lower.contains("rejected") || lower.contains("declined") || lower.contains("từ chối")
                    if (isActiveEmergency) {
                        val friendly =
                            "You already have an active emergency request. Please follow the current request or cancel it before creating a new one."
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Request already exists")
                            .setMessage(friendly)
                            .setNegativeButton("Close", null)
                            .setPositiveButton("Follow") { _, _ ->
                                val garage = viewModel.assignedGarage.value
                                    ?: emergencyBottomSheet.lastSelectedGarage()
                                val minutes: Int? = null
                                if (garage != null) {
                                    emergencyBottomSheet.showAccepted(garage, minutes)
                                    emergencyBottomSheet.setOnTrackClickListener { startTrackingTechnician() }
                                } else showEmergencyUI()
                            }
                            .show()
                    } else if (isRejected) {
                        val garage = emergencyBottomSheet.lastSelectedGarage()
                        if (garage != null) {
                            emergencyBottomSheet.showRejected(garage, msg)
                        } else {
                            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                .setTitle("Garage rejected")
                                .setMessage("The garage cannot accept your request. Please choose another garage.")
                                .setNegativeButton("Close", null)
                                .setPositiveButton("Choose another garage") { _, _ -> showEmergencyUI() }
                                .show()
                        }
                    } else {
                        val friendly =
                            if (lower.contains("timeout") || lower.contains("timed out") || lower.contains(
                                    "sockettimeout"
                                )
                            ) {
                                "Network timeout. Please check your internet connection and try again."
                            } else msg
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Action Failed")
                            .setMessage(friendly)
                            .setNegativeButton("Close") { _, _ -> navigateHome() }
                            .setPositiveButton("Retry") { _, _ ->
                                val latLng = pendingLatLng
                                if (latLng != null) proceedFetchNearbyAndShow() else getCurrentLocationForEmergency()
                            }
                            .show()
                    }
                }

                else -> {}
            }
        }

        viewModel.nearbyGarages.observe(this) { garages ->
            val filtered = garages.filter { it.id !in rejectedGarageIds }
            if (emergencyBottomSheet.isShowing()) {
                emergencyBottomSheet.updateGarages(filtered)
            }
        }

        viewModel.selectedGarage.observe(this) { garage ->
            if (emergencyBottomSheet.isShowing()) {
                emergencyBottomSheet.updateSelectedGarage(garage)
            }
            garage?.let { emergencyHub?.joinBranchGroup(it.id) }
        }

        viewModel.routeGeoJson.observe(this) { fc ->
            if (!activityActive || !styleLoaded) return@observe
            val style = maplibreMap?.style ?: return@observe
            try {
                val src = style.getSourceAs<GeoJsonSource>("route-source")
                if (fc != null) src?.setGeoJson(fc) else {
                    val tech = technicianLatLng
                    val cust =
                        viewModel.getCurrentEmergency()?.let { LatLng(it.latitude, it.longitude) }
                    if (tech != null && cust != null) {
                        val coords = JsonArray().apply {
                            add(JsonArray().apply { add(tech.longitude); add(tech.latitude) })
                            add(JsonArray().apply { add(cust.longitude); add(cust.latitude) })
                        }
                        val geom = JsonObject().apply {
                            addProperty("type", "LineString")
                            add("coordinates", coords)
                        }
                        val feature = JsonObject().apply {
                            addProperty("type", "Feature")
                            add("geometry", geom)
                        }
                        val fcFallback = JsonObject().apply {
                            addProperty("type", "FeatureCollection")
                            add("features", JsonArray().apply { add(feature) })
                        }.toString()
                        src?.setGeoJson(fcFallback)
                    }
                }
                if (fc != null) {
                    val obj = com.google.gson.JsonParser.parseString(fc).asJsonObject
                    val features = obj.getAsJsonArray("features")
                    if (features != null && features.size() > 0) {
                        val first = features.get(0).asJsonObject
                        val geom = first.getAsJsonObject("geometry")
                        val type = geom.get("type")?.asString
                        if (type != null && type.equals("LineString", true)) {
                            val coords = geom.getAsJsonArray("coordinates")
                            if (coords != null && coords.size() > 0) {
                                val p = coords.get(0).asJsonArray
                                val lng = p.get(0).asDouble
                                val lat = p.get(1).asDouble
                                val pointFc = JsonObject().apply {
                                    addProperty("type", "FeatureCollection")
                                    add("features", JsonArray().apply {
                                        add(JsonObject().apply {
                                            addProperty("type", "Feature")
                                            add("geometry", JsonObject().apply {
                                                addProperty("type", "Point")
                                                add("coordinates", JsonArray().apply {
                                                    add(lng)
                                                    add(lat)
                                                })
                                            })
                                        })
                                    })
                                }
                                style.getSourceAs<GeoJsonSource>("route-start-source")
                                    ?.setGeoJson(pointFc.toString())
                                if (trackingActive) {
                                    moveCameraToLocation(LatLng(lat, lng))
                                }
                                try {
                                    val last = coords.get(coords.size() - 1).asJsonArray
                                    val lastLng = last.get(0).asDouble
                                    val lastLat = last.get(1).asDouble
                                    destinationLatLng = LatLng(lastLat, lastLng)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        viewModel.etaMinutes.observe(this) { m ->
            if (m != null) emergencyBottomSheet.updateTrackingEta(m)
        }

        // viewModel.distanceMeters.observe(this) { d ->
        //     handleArrivalCandidate(d, technicianLatLng)
        // }

        lifecycleScope.launchWhenStarted {
            emergencyHub?.events?.collect { (event, payload) ->
                android.util.Log.d("EmergencyHubEvent", "event=" + event)
                val lower = event.lowercase()
                if (blockHubUI && (lower.contains("inprogress") || lower.contains("technicianassigned") || lower.contains(
                        "technicianlocationupdated"
                    ))
                ) {
                    android.util.Log.d(
                        "EmergencyHubEvent",
                        "blocked event due to force_new: $event"
                    )
                    return@collect
                }
                if (lower.contains("created")) {
                    try {
                        val obj = com.google.gson.JsonParser.parseString(payload).asJsonObject
                        val eid = arrayOf(
                            "EmergencyRequestId",
                            "emergencyRequestId",
                            "EmergencyId",
                            "EmergenciesId",
                            "RequestId",
                            "Id"
                        ).firstNotNullOfOrNull { k ->
                            if (obj.has(k)) try {
                                obj.get(k).asString
                            } catch (_: Exception) {
                                null
                            } else null
                        }
                        val branchId = arrayOf(
                            "BranchId",
                            "branchId",
                            "BranchID",
                            "GarageId"
                        ).firstNotNullOfOrNull { k ->
                            if (obj.has(k)) try {
                                obj.get(k).asString
                            } catch (_: Exception) {
                                null
                            } else null
                        }
                        android.util.Log.d("EmergencyID", "Raw created payload=" + payload)
                        android.util.Log.d(
                            "EmergencyID",
                            "SignalR created payload id=" + (eid ?: "") + " branch=" + (branchId
                                ?: "")
                        )
                        if (!eid.isNullOrBlank()) viewModel.markCreated(eid, branchId)
                    } catch (_: Exception) {
                    }
                }
                if (lower.contains("approved")) {
                    try {
                        val obj = com.google.gson.JsonParser.parseString(payload).asJsonObject
                        val eid = arrayOf(
                            "EmergencyRequestId",
                            "emergencyRequestId",
                            "EmergencyId",
                            "EmergenciesId",
                            "RequestId",
                            "Id"
                        ).firstNotNullOfOrNull { k ->
                            if (obj.has(k)) try {
                                obj.get(k).asString
                            } catch (_: Exception) {
                                null
                            } else null
                        }
                        val branchId = arrayOf(
                            "BranchId",
                            "branchId",
                            "BranchID",
                            "GarageId"
                        ).firstNotNullOfOrNull { k ->
                            if (obj.has(k)) try {
                                obj.get(k).asString
                            } catch (_: Exception) {
                                null
                            } else null
                        }
                        android.util.Log.d("EmergencyID", "Raw approved payload=" + payload)
                        android.util.Log.d(
                            "EmergencyID",
                            "SignalR approved payload id=" + (eid ?: "") + " branch=" + (branchId
                                ?: "")
                        )
                        viewModel.markApproved(
                            eid ?: viewModel.getCurrentEmergency()?.id ?: "",
                            branchId
                        )
                    } catch (_: Exception) {
                        val curId = viewModel.getCurrentEmergency()?.id ?: ""
                        viewModel.markApproved(curId, null)
                    }
                } else if (lower.contains("rejected")) {
                    try {
                        val obj = com.google.gson.JsonParser.parseString(payload).asJsonObject
                        val reason =
                            if (obj.has("RejectReason")) obj.get("RejectReason").asString else obj.get(
                                "Message"
                            )?.asString
                        val branchId =
                            if (obj.has("BranchId")) obj.get("BranchId").asString else null
                        branchId?.let { rejectedGarageIds.add(it) }
                        val garage = viewModel.assignedGarage.value
                            ?: emergencyBottomSheet.lastSelectedGarage()
                        emergencyBottomSheet.setOnChooseAnotherListener {
                            viewModel.clearSelectedGarage()
                            val latLng = pendingLatLng ?: lastTappedLatLng
                            latLng?.let {
                                viewModel.refreshNearbyGarages(
                                    it.latitude,
                                    it.longitude
                                )
                            }
                            showEmergencyUI()
                        }
                        if (garage != null) emergencyBottomSheet.showRejected(garage, reason)
                        Toast.makeText(this@MapActivity, "Garage rejected", Toast.LENGTH_SHORT)
                            .show()
                    } catch (_: Exception) {
                        emergencyBottomSheet.lastSelectedGarage()?.id?.let {
                            rejectedGarageIds.add(
                                it
                            )
                        }
                        val garage = emergencyBottomSheet.lastSelectedGarage()
                        emergencyBottomSheet.setOnChooseAnotherListener {
                            viewModel.clearSelectedGarage()
                            val latLng = pendingLatLng ?: lastTappedLatLng
                            latLng?.let {
                                viewModel.refreshNearbyGarages(
                                    it.latitude,
                                    it.longitude
                                )
                            }
                            showEmergencyUI()
                        }
                        if (garage != null) emergencyBottomSheet.showRejected(garage, null)
                    }
                } else if (lower.contains("joinedcustomergroup")) {
                    Toast.makeText(this@MapActivity, "Joined: $payload", Toast.LENGTH_SHORT).show()
                } else if (lower.contains("joinedbranchgroup")) {
                    Toast.makeText(this@MapActivity, "Joined: $payload", Toast.LENGTH_SHORT).show()
                } else if (lower.contains("expired")) {
                    val garage = emergencyBottomSheet.lastSelectedGarage()
                    emergencyBottomSheet.setOnChooseAnotherListener {
                        viewModel.clearSelectedGarage()
                        val latLng = pendingLatLng ?: lastTappedLatLng
                        latLng?.let { viewModel.refreshNearbyGarages(it.latitude, it.longitude) }
                        showEmergencyUI()
                    }
                    garage?.let { emergencyBottomSheet.showExpired(it) }
                    Toast.makeText(
                        this@MapActivity,
                        "Request response time has expired",
                        Toast.LENGTH_LONG
                    ).show()
                } else if (lower.contains("canceled")) {
                    var autoCanceledAt: String? = null
                    var branchId: String? = null
                    try {
                        val obj = com.google.gson.JsonParser.parseString(payload).asJsonObject
                        if (obj.has("AutoCanceledAt")) autoCanceledAt =
                            obj.get("AutoCanceledAt").asString
                        if (obj.has("BranchId")) branchId = obj.get("BranchId").asString
                    } catch (_: Exception) {
                    }
                    if (!autoCanceledAt.isNullOrBlank()) {
                        branchId?.let { rejectedGarageIds.add(it) }
                        val garage = viewModel.assignedGarage.value
                            ?: emergencyBottomSheet.lastSelectedGarage()
                        emergencyBottomSheet.setOnChooseAnotherListener {
                            viewModel.clearSelectedGarage()
                            val latLng = pendingLatLng ?: lastTappedLatLng
                            latLng?.let {
                                viewModel.refreshNearbyGarages(
                                    it.latitude,
                                    it.longitude
                                )
                            }
                            showEmergencyUI()
                        }
                        garage?.let { emergencyBottomSheet.showExpired(it) }
                        Toast.makeText(
                            this@MapActivity,
                            "Request response time has expired",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        viewModel.resetState()
                        Toast.makeText(this@MapActivity, "Request canceled", Toast.LENGTH_LONG)
                            .show()
                    }
                }

                // ========== TECHNICIAN LOCATION UPDATED ==========
                if (lower.contains("technicianlocationupdated")) {
                    try {
                        android.util.Log.d("TechRT", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        android.util.Log.d("TechRT", "📥 Technician update received")
                        android.util.Log.d(
                            "TechRT",
                            "   active=$activityActive, styleLoaded=$styleLoaded, tracking=$trackingActive"
                        )

                        // ========== LOG FULL PAYLOAD ==========
                        android.util.Log.d("TechRT", "")
                        android.util.Log.d("TechRT", "📦 FULL PAYLOAD:")
                        android.util.Log.d("TechRT", payload)
                        android.util.Log.d("TechRT", "")

                        val obj = com.google.gson.JsonParser.parseString(payload).asJsonObject

                        // ========== LOG ALL KEYS ==========
                        android.util.Log.d("TechRT", "🔑 KEYS IN PAYLOAD:")
                        obj.keySet().forEach { key ->
                            try {
                                val value = obj.get(key)
                                val valueStr = when {
                                    value.isJsonNull -> "null"
                                    value.isJsonPrimitive -> {
                                        if (value.asJsonPrimitive.isString) "\"${value.asString}\""
                                        else value.toString()
                                    }

                                    value.isJsonObject -> "{...}"
                                    value.isJsonArray -> "[${value.asJsonArray.size()} items]"
                                    else -> value.toString()
                                }
                                android.util.Log.d("TechRT", "  $key = $valueStr")
                            } catch (e: Exception) {
                                android.util.Log.d("TechRT", "  $key = [error]")
                            }
                        }
                        android.util.Log.d("TechRT", "")

                        // BranchId Processing
                        try {
                            val branchId = arrayOf(
                                "BranchId",
                                "branchId",
                                "GarageId",
                                "garageId",
                                "AssignedGarageId",
                                "assignedGarageId"
                            ).firstNotNullOfOrNull { k ->
                                if (obj.has(k)) try {
                                    obj.get(k).asString
                                } catch (_: Exception) {
                                    null
                                } else null
                            }
                            android.util.Log.d("TechRT", "🏢 Branch ID: ${branchId ?: "not found"}")

                            if (!branchId.isNullOrBlank()) {
                                val prefs = getSharedPreferences(
                                    com.example.garapro.utils.Constants.USER_PREFERENCES,
                                    Context.MODE_PRIVATE
                                )
                                prefs.edit().putString("last_assigned_garage_id", branchId).apply()
                                try {
                                    android.util.Log.d(
                                        "EmergencyHubJoin",
                                        "joinBranchGroup id=" + branchId
                                    )
                                    emergencyHub?.joinBranchGroup(branchId)
                                } catch (_: Exception) {
                                }
                            }
                        } catch (_: Exception) {
                        }

                        // Location Parsing
                        val lat = when {
                            obj.has("latitude") -> obj.get("latitude").asDouble
                            obj.has("Latitude") -> obj.get("Latitude").asDouble
                            obj.has("lat") -> obj.get("lat").asDouble
                            else -> Double.NaN
                        }

                        val lng = when {
                            obj.has("longitude") -> obj.get("longitude").asDouble
                            obj.has("Longitude") -> obj.get("Longitude").asDouble
                            obj.has("lng") -> obj.get("lng").asDouble
                            else -> Double.NaN
                        }

                        android.util.Log.d("TechRT", "📍 Location: lat=$lat, lng=$lng")

                        // ========== LOG TECHNICIAN INFO ==========
                        val techName = arrayOf(
                            "TechnicianName",
                            "technicianName",
                            "Name",
                            "name"
                        ).firstNotNullOfOrNull { k ->
                            if (obj.has(k)) try {
                                obj.get(k).asString
                            } catch (_: Exception) {
                                null
                            } else null
                        }

                        val techPhone = arrayOf(
                            "PhoneNumberTecnician", "phoneNumberTecnician",
                            "PhoneNumberTechnician", "phoneNumberTechnician",
                            "TechnicianPhone", "technicianPhone",
                            "Phone", "phone"
                        ).firstNotNullOfOrNull { k ->
                            if (obj.has(k)) try {
                                obj.get(k).asString
                            } catch (_: Exception) {
                                null
                            } else null
                        }

                        android.util.Log.d("TechRT", "👨‍🔧 Technician: ${techName ?: "not found"}")
                        android.util.Log.d("TechRT", "📞 Phone: ${techPhone ?: "not found"}")

                        // ========== LOG ETA & DISTANCE ==========
                        val etaMinutes = try {
                            when {
                                obj.has("EtaMinutes") && !obj.get("EtaMinutes").isJsonNull -> obj.get(
                                    "EtaMinutes"
                                ).asInt

                                obj.has("etaMinutes") && !obj.get("etaMinutes").isJsonNull -> obj.get(
                                    "etaMinutes"
                                ).asInt

                                else -> null
                            }
                        } catch (_: Exception) {
                            null
                        }

                        val distanceKm = try {
                            when {
                                obj.has("DistanceKm") && !obj.get("DistanceKm").isJsonNull -> obj.get(
                                    "DistanceKm"
                                ).asDouble

                                obj.has("distanceKm") && !obj.get("distanceKm").isJsonNull -> obj.get(
                                    "distanceKm"
                                ).asDouble

                                else -> null
                            }
                        } catch (_: Exception) {
                            null
                        }

                        android.util.Log.d("TechRT", "⏱️ ETA: ${etaMinutes ?: "not found"} minutes")
                        android.util.Log.d("TechRT", "📏 Distance: ${distanceKm ?: "not found"} km")

                        // ========== LOG ROUTE ==========
                        if (obj.has("route") && !obj.get("route").isJsonNull) {
                            val routeElement = obj.get("route")
                            android.util.Log.d(
                                "TechRT",
                                "🛣️ Route: EXISTS (${routeElement.javaClass.simpleName})"
                            )

                            if (routeElement.isJsonObject) {
                                val routeObj = routeElement.asJsonObject
                                android.util.Log.d("TechRT", "   Route keys: ${routeObj.keySet()}")

                                if (routeObj.has("coordinates")) {
                                    val coords = routeObj.getAsJsonArray("coordinates")
                                    android.util.Log.d(
                                        "TechRT",
                                        "   Coordinates: ${coords.size()} points"
                                    )
                                }

                                if (routeObj.has("type")) {
                                    android.util.Log.d(
                                        "TechRT",
                                        "   Type: ${routeObj.get("type").asString}"
                                    )
                                }
                            } else if (routeElement.isJsonPrimitive && routeElement.asJsonPrimitive.isString) {
                                val routeStr = routeElement.asString
                                android.util.Log.d(
                                    "TechRT",
                                    "   Route string length: ${routeStr.length}"
                                )
                            }
                        } else {
                            android.util.Log.d("TechRT", "🛣️ Route: NOT FOUND")
                        }

                        android.util.Log.d("TechRT", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                        if (!lat.isNaN() && !lng.isNaN()) {
                            val point = LatLng(lat, lng)
                            technicianLatLng = point

                            if (activityActive && styleLoaded) {
                                val style = maplibreMap?.style

                                // ========== 1. Update Technician Marker ==========
                                var techSrc = style?.getSourceAs<GeoJsonSource>("technician-source")
                                if (techSrc == null && style != null) {
                                    addTechnicianLayer(style)
                                    techSrc = style.getSourceAs("technician-source")
                                }

                                val techFeature = JsonObject().apply {
                                    addProperty("type", "Feature")
                                    add("geometry", JsonObject().apply {
                                        addProperty("type", "Point")
                                        add("coordinates", JsonArray().apply {
                                            add(lng)
                                            add(lat)
                                        })
                                    })
                                }

                                val techFc = JsonObject().apply {
                                    addProperty("type", "FeatureCollection")
                                    add("features", JsonArray().apply { add(techFeature) })
                                }

                                techSrc?.setGeoJson(techFc.toString())
                                android.util.Log.d("TechRT", "✅ Marker updated")

                                // ========== 2. Parse Route from SignalR (FIX!) ==========
                                var routeParsed = false

                                if (obj.has("route") && !obj.get("route").isJsonNull) {
                                    try {
                                        val routeElement = obj.get("route")
                                        android.util.Log.d(
                                            "TechRT",
                                            "🛣️ Route field found, type=${routeElement.javaClass.simpleName}"
                                        )

                                        if (routeElement.isJsonObject) {
                                            val routeObj = routeElement.asJsonObject

                                            if (routeObj.has("coordinates") && routeObj.has("type")) {
                                                val coords = routeObj.getAsJsonArray("coordinates")
                                                val type = routeObj.get("type").asString

                                                android.util.Log.d(
                                                    "TechRT",
                                                    "   type=$type, points=${coords.size()}"
                                                )

                                                if (type == "LineString" && coords.size() >= 2) {
                                                    // Build GeoJSON FeatureCollection
                                                    val geometry = JsonObject().apply {
                                                        addProperty("type", "LineString")
                                                        add("coordinates", coords)
                                                    }

                                                    val feature = JsonObject().apply {
                                                        addProperty("type", "Feature")
                                                        add("geometry", geometry)
                                                    }

                                                    val featureCollection = JsonObject().apply {
                                                        addProperty("type", "FeatureCollection")
                                                        add(
                                                            "features",
                                                            JsonArray().apply { add(feature) })
                                                    }

                                                    val routeGeometry = featureCollection.toString()

                                                    // Update map
                                                    val routeSrc =
                                                        style?.getSourceAs<GeoJsonSource>("route-source")
                                                    routeSrc?.setGeoJson(routeGeometry)

                                                    android.util.Log.d(
                                                        "TechRT",
                                                        "✅ Route drawn: ${coords.size()} points"
                                                    )

                                                    // Start point marker
                                                    try {
                                                        val startPoint = coords.get(0).asJsonArray
                                                        val startLng = startPoint.get(0).asDouble
                                                        val startLat = startPoint.get(1).asDouble

                                                        val startFeature = JsonObject().apply {
                                                            addProperty("type", "Feature")
                                                            add("geometry", JsonObject().apply {
                                                                addProperty("type", "Point")
                                                                add(
                                                                    "coordinates",
                                                                    JsonArray().apply {
                                                                        add(startLng)
                                                                        add(startLat)
                                                                    })
                                                            })
                                                        }

                                                        val startFc = JsonObject().apply {
                                                            addProperty("type", "FeatureCollection")
                                                            add(
                                                                "features",
                                                                JsonArray().apply { add(startFeature) })
                                                        }

                                                        style?.getSourceAs<GeoJsonSource>("route-start-source")
                                                            ?.setGeoJson(startFc.toString())
                                                        android.util.Log.d(
                                                            "TechRT",
                                                            "✅ Start marker set"
                                                        )
                                                    } catch (e: Exception) {
                                                        android.util.Log.e(
                                                            "TechRT",
                                                            "Start marker error: ${e.message}"
                                                        )
                                                    }

                                                    // End point (destination)
                                                    try {
                                                        val endPoint =
                                                            coords.get(coords.size() - 1).asJsonArray
                                                        val endLng = endPoint.get(0).asDouble
                                                        val endLat = endPoint.get(1).asDouble
                                                        destinationLatLng = LatLng(endLat, endLng)
                                                        android.util.Log.d(
                                                            "TechRT",
                                                            "✅ Destination: $endLat, $endLng"
                                                        )
                                                    } catch (e: Exception) {
                                                        android.util.Log.e(
                                                            "TechRT",
                                                            "Destination error: ${e.message}"
                                                        )
                                                    }

                                                    routeParsed = true

                                                    // Stop polling vì đã có route từ SignalR
                                                    try {
                                                        viewModel.stopRoutePolling()
                                                        android.util.Log.d(
                                                            "TechRT",
                                                            "⏸️ Polling stopped"
                                                        )
                                                    } catch (e: Exception) {
                                                        android.util.Log.e(
                                                            "TechRT",
                                                            "Stop polling error: ${e.message}"
                                                        )
                                                    }
                                                } else {
                                                    android.util.Log.w(
                                                        "TechRT",
                                                        "⚠️ Invalid route: type=$type, points=${coords.size()}"
                                                    )
                                                }
                                            } else {
                                                android.util.Log.w(
                                                    "TechRT",
                                                    "⚠️ Route missing coordinates or type"
                                                )
                                            }
                                        } else if (routeElement.isJsonPrimitive && routeElement.asJsonPrimitive.isString) {
                                            // Route is GeoJSON string
                                            val routeStr = routeElement.asString
                                            android.util.Log.d(
                                                "TechRT",
                                                "   Route is string, length=${routeStr.length}"
                                            )

                                            try {
                                                val routeSrc =
                                                    style?.getSourceAs<GeoJsonSource>("route-source")
                                                routeSrc?.setGeoJson(routeStr)
                                                android.util.Log.d(
                                                    "TechRT",
                                                    "✅ Route drawn (string)"
                                                )
                                                routeParsed = true

                                                viewModel.stopRoutePolling()
                                                android.util.Log.d("TechRT", "⏸️ Polling stopped")
                                            } catch (e: Exception) {
                                                android.util.Log.e(
                                                    "TechRT",
                                                    "String route error: ${e.message}"
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e(
                                            "TechRT",
                                            "❌ Route parse error: ${e.message}"
                                        )
                                        e.printStackTrace()
                                    }
                                } else {
                                    android.util.Log.d("TechRT", "⚠️ No route in payload")
                                }

                                if (!routeParsed) {
                                    android.util.Log.d("TechRT", "   Continuing with API polling")
                                }

                                // ========== 3. Camera Follow ==========
                                if (cameraFollowTechnician) {
                                    moveCameraToLocation(point)
                                }

                                emergencyBottomSheet.updateTrackingSkeleton(false)

                                // ========== 4. Technician Info ==========
                                val name = when {
                                    obj.has("TechnicianName") -> obj.get("TechnicianName").asString
                                    obj.has("technicianName") -> obj.get("technicianName").asString
                                    obj.has("Name") -> obj.get("Name").asString
                                    else -> null
                                }

                                val phone = when {
                                    obj.has("PhoneNumberTecnician") -> obj.get("PhoneNumberTecnician").asString
                                    obj.has("phoneNumberTecnician") -> obj.get("phoneNumberTecnician").asString
                                    obj.has("PhoneNumberTechnician") -> obj.get("PhoneNumberTechnician").asString
                                    obj.has("TechnicianPhone") -> obj.get("TechnicianPhone").asString
                                    obj.has("technicianPhone") -> obj.get("technicianPhone").asString
                                    obj.has("Phone") -> obj.get("Phone").asString
                                    else -> null
                                }

                                technicianName = name
                                technicianPhone = phone

                                val curId = viewModel.getCurrentEmergency()?.id
                                saveTechContactForEmergency(curId, name, phone)

                                if (!technicianArrived) {
                                    emergencyBottomSheet.updateTrackingTechnician(name, phone)
                                }

                                if (!trackingActive) {
                                    enableTrackingUI()
                                }

                                // ========== 5. ETA & Distance ==========
                                val etaMinutes = try {
                                    when {
                                        obj.has("EtaMinutes") && !obj.get("EtaMinutes").isJsonNull -> obj.get(
                                            "EtaMinutes"
                                        ).asInt

                                        obj.has("etaMinutes") && !obj.get("etaMinutes").isJsonNull -> obj.get(
                                            "etaMinutes"
                                        ).asInt

                                        else -> null
                                    }
                                } catch (_: Exception) {
                                    null
                                }

                                val distanceKm = try {
                                    when {
                                        obj.has("DistanceKm") && !obj.get("DistanceKm").isJsonNull -> obj.get(
                                            "DistanceKm"
                                        ).asDouble

                                        obj.has("distanceKm") && !obj.get("distanceKm").isJsonNull -> obj.get(
                                            "distanceKm"
                                        ).asDouble

                                        else -> null
                                    }
                                } catch (_: Exception) {
                                    null
                                }

                                android.util.Log.d(
                                    "TechRT",
                                    "📊 ETA=$etaMinutes min, Distance=$distanceKm km"
                                )

                                etaMinutes?.let {
                                    emergencyBottomSheet.updateTrackingEta(it)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("TechRT", "💥 Fatal error: ${e.message}")
                        e.printStackTrace()
                    }
                }

                //check inprogress
                if (lower.contains("emergencyrequestinprogress") || lower.contains("inprogress")) {
                    try {
                        val obj =
                            com.google.gson.JsonParser.parseString(payload).asJsonObject
                        val eid = arrayOf(
                            "EmergencyRequestId",
                            "emergencyRequestId",
                            "EmergencyId",
                            "EmergenciesId",
                            "RequestId",
                            "Id"
                        ).firstNotNullOfOrNull { k ->
                            if (obj.has(k)) try {
                                obj.get(k).asString
                            } catch (_: Exception) {
                                null
                            } else null
                        }
                        if (!eid.isNullOrBlank()) {
                            saveLastEmergencyId(eid)
                        }
                        try {
                            val branchId = arrayOf(
                                "BranchId",
                                "branchId",
                                "GarageId",
                                "garageId",
                                "AssignedGarageId",
                                "assignedGarageId"
                            ).firstNotNullOfOrNull { k ->
                                if (obj.has(k)) try {
                                    obj.get(k).asString
                                } catch (_: Exception) {
                                    null
                                } else null
                            }
                            if (!branchId.isNullOrBlank()) {
                                val prefs = getSharedPreferences(
                                    com.example.garapro.utils.Constants.USER_PREFERENCES,
                                    Context.MODE_PRIVATE
                                )
                                prefs.edit()
                                    .putString("last_assigned_garage_id", branchId)
                                    .apply()
                                try {
                                    android.util.Log.d(
                                        "EmergencyHubJoin",
                                        "joinBranchGroup id=" + branchId
                                    )
                                    emergencyHub?.joinBranchGroup(branchId)
                                } catch (_: Exception) {
                                }
                            }
                        } catch (_: Exception) {
                        }
                        val name = when {
                            obj.has("TechnicianName") -> obj.get("TechnicianName").asString
                            obj.has("technicianName") -> obj.get("technicianName").asString
                            obj.has("Name") -> obj.get("Name").asString
                            else -> null
                        }
                        val phone = when {
                            obj.has("PhoneNumberTecnician") -> obj.get("PhoneNumberTecnician").asString
                            obj.has("phoneNumberTecnician") -> obj.get("phoneNumberTecnician").asString
                            obj.has("PhoneNumberTechnician") -> obj.get("PhoneNumberTechnician").asString
                            obj.has("TechnicianPhone") -> obj.get("TechnicianPhone").asString
                            obj.has("technicianPhone") -> obj.get("technicianPhone").asString
                            obj.has("Phone") -> obj.get("Phone").asString
                            else -> null
                        }
                        technicianName = name
                        technicianPhone = phone
                        val curId = viewModel.getCurrentEmergency()?.id
                        saveTechContactForEmergency(curId, name, phone)
                        if (!technicianArrived) emergencyBottomSheet.updateTrackingTechnician(
                            name,
                            phone
                        )
                        inProgressStartedAt = System.currentTimeMillis()
                        val garage = viewModel.assignedGarage.value
                            ?: emergencyBottomSheet.lastSelectedGarage()
                        val minutes: Int? = null
                        if (garage != null) {
                            if (technicianArrived) {
                                emergencyBottomSheet.setOnCloseClickListener { finishSafely() }
                                emergencyBottomSheet.showArrived(
                                    garage,
                                    technicianName,
                                    technicianPhone
                                )
                                topAppBar.visibility = View.GONE
                                try {
                                    fabEmergency.visibility = View.GONE
                                } catch (_: Exception) {
                                }
                                try {
                                    fabCurrentLocation.visibility = View.GONE
                                } catch (_: Exception) {
                                }
                                return@collect
                            }
                            try {
                                val gLat = garage.latitude
                                val gLng = garage.longitude
                                if ((technicianLatLng == null || (technicianLatLng?.latitude == 0.0 && technicianLatLng?.longitude == 0.0)) && (gLat != 0.0 || gLng != 0.0)) {
                                    val init = LatLng(gLat, gLng)
                                    technicianLatLng = init
                                    if (activityActive && styleLoaded) {
                                        val style = maplibreMap?.style
                                        var techSrc =
                                            style?.getSourceAs<GeoJsonSource>("technician-source")
                                        if (techSrc == null && style != null) {
                                            addTechnicianLayer(style)
                                            techSrc =
                                                style.getSourceAs("technician-source")
                                        }
                                        val techFeature = JsonObject().apply {
                                            addProperty("type", "Feature")
                                            add("geometry", JsonObject().apply {
                                                addProperty("type", "Point")
                                                add(
                                                    "coordinates",
                                                    JsonArray().apply {
                                                        add(gLng)
                                                        add(gLat)
                                                    })
                                            })
                                        }
                                        val techFc = JsonObject().apply {
                                            addProperty("type", "FeatureCollection")
                                            add(
                                                "features",
                                                JsonArray().apply { add(techFeature) })
                                        }
                                        techSrc?.setGeoJson(techFc.toString())
                                        emergencyBottomSheet.updateTrackingSkeleton(
                                            false
                                        )
                                    }
                                }
                            } catch (_: Exception) {
                            }
                            topAppBar.visibility = View.VISIBLE
                            tvTitle.text = "Tracking technician"
                            enableTrackingUI()
                            emergencyBottomSheet.showTracking(garage, minutes)
                            emergencyBottomSheet.setOnViewMapClickListener {
                                cameraFollowTechnician = true
                                refreshTrackingFromApi()
                                topAppBar.visibility = View.VISIBLE
                                tvTitle.text = "Tracking technician"
                                enableTrackingUI()
                                val id2 = viewModel.getCurrentEmergency()?.id
                                if (!id2.isNullOrBlank()) viewModel.fetchRouteNow()
                            }
                            val id = viewModel.getCurrentEmergency()?.id
                            if (!id.isNullOrBlank()) {
                                if (styleLoaded) {
                                    viewModel.fetchRouteNow()
                                    viewModel.startRoutePolling()
                                    routeFetchPending = false
                                } else {
                                    routeFetchPending = true
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                if (lower.contains("technicianassigned")) {
                    try {
                        val obj =
                            com.google.gson.JsonParser.parseString(payload).asJsonObject
                        try {
                            val branchId = arrayOf(
                                "BranchId",
                                "branchId",
                                "GarageId",
                                "garageId",
                                "AssignedGarageId",
                                "assignedGarageId"
                            ).firstNotNullOfOrNull { k ->
                                if (obj.has(k)) try {
                                    obj.get(k).asString
                                } catch (_: Exception) {
                                    null
                                } else null
                            }
                            if (!branchId.isNullOrBlank()) {
                                val prefs = getSharedPreferences(
                                    com.example.garapro.utils.Constants.USER_PREFERENCES,
                                    Context.MODE_PRIVATE
                                )
                                prefs.edit()
                                    .putString("last_assigned_garage_id", branchId)
                                    .apply()
                                try {
                                    android.util.Log.d(
                                        "EmergencyHubJoin",
                                        "joinBranchGroup id=" + branchId
                                    )
                                    emergencyHub?.joinBranchGroup(branchId)
                                } catch (_: Exception) {
                                }
                            }
                        } catch (_: Exception) {
                        }
                        val name = when {
                            obj.has("TechnicianName") -> obj.get("TechnicianName").asString
                            obj.has("technicianName") -> obj.get("technicianName").asString
                            obj.has("Name") -> obj.get("Name").asString
                            else -> null
                        }
                        val phone = when {
                            obj.has("PhoneNumberTecnician") -> obj.get("PhoneNumberTecnician").asString
                            obj.has("phoneNumberTecnician") -> obj.get("phoneNumberTecnician").asString
                            obj.has("PhoneNumberTechnician") -> obj.get("PhoneNumberTechnician").asString
                            obj.has("TechnicianPhone") -> obj.get("TechnicianPhone").asString
                            obj.has("technicianPhone") -> obj.get("technicianPhone").asString
                            obj.has("Phone") -> obj.get("Phone").asString
                            else -> null
                        }
                        technicianName = name
                        technicianPhone = phone
                        val curId = viewModel.getCurrentEmergency()?.id
                        saveTechContactForEmergency(curId, name, phone)
                        if (!technicianArrived) emergencyBottomSheet.updateTrackingTechnician(
                            name,
                            phone
                        )
                        val garage = viewModel.assignedGarage.value
                            ?: emergencyBottomSheet.lastSelectedGarage()
                        val minutes: Int? = null
                        if (garage != null) {
                            if (technicianArrived) {
                                emergencyBottomSheet.setOnCloseClickListener { finishSafely() }
                                emergencyBottomSheet.showArrived(
                                    garage,
                                    technicianName,
                                    technicianPhone
                                )
                                topAppBar.visibility = View.GONE
                                try {
                                    fabEmergency.visibility = View.GONE
                                } catch (_: Exception) {
                                }
                                try {
                                    fabCurrentLocation.visibility = View.GONE
                                } catch (_: Exception) {
                                }
                                return@collect
                            }
                            try {
                                val gLat = garage.latitude
                                val gLng = garage.longitude
                                if ((technicianLatLng == null || (technicianLatLng?.latitude == 0.0 && technicianLatLng?.longitude == 0.0)) && (gLat != 0.0 || gLng != 0.0)) {
                                    val init = LatLng(gLat, gLng)
                                    technicianLatLng = init
                                    if (activityActive && styleLoaded) {
                                        val style = maplibreMap?.style
                                        var techSrc =
                                            style?.getSourceAs<GeoJsonSource>("technician-source")
                                        if (techSrc == null && style != null) {
                                            addTechnicianLayer(style)
                                            techSrc =
                                                style.getSourceAs("technician-source")
                                        }
                                        val techFeature = JsonObject().apply {
                                            addProperty("type", "Feature")
                                            add("geometry", JsonObject().apply {
                                                addProperty("type", "Point")
                                                add(
                                                    "coordinates",
                                                    JsonArray().apply {
                                                        add(gLng)
                                                        add(gLat)
                                                    })
                                            })
                                        }
                                        val techFc = JsonObject().apply {
                                            addProperty("type", "FeatureCollection")
                                            add(
                                                "features",
                                                JsonArray().apply { add(techFeature) })
                                        }
                                        techSrc?.setGeoJson(techFc.toString())
                                        emergencyBottomSheet.updateTrackingSkeleton(
                                            false
                                        )
                                    }
                                }
                            } catch (_: Exception) {
                            }
                            emergencyBottomSheet.setOnTrackClickListener {
                                enableTrackingUI()
                                emergencyBottomSheet.showTracking(garage, minutes)
                                emergencyBottomSheet.setOnViewMapClickListener {
                                    cameraFollowTechnician = true
                                    refreshTrackingFromApi()
                                    topAppBar.visibility = View.VISIBLE
                                    tvTitle.text = "Tracking technician"
                                    enableTrackingUI()
                                    val id2 = viewModel.getCurrentEmergency()?.id
                                    if (!id2.isNullOrBlank()) viewModel.fetchRouteNow()
                                }
                                val id = viewModel.getCurrentEmergency()?.id
                                if (!id.isNullOrBlank()) {
                                    viewModel.fetchRouteNow()
                                }
                            }
                            cameraFollowTechnician = false
                            emergencyBottomSheet.showAccepted(garage, minutes)
                        }
                        viewModel.getCurrentEmergency()?.id?.takeIf { it.isNotBlank() }
                            ?.let { saveLastEmergencyId(it) }
                    } catch (_: Exception) {
                    }
                }
                if (lower.contains("emergencyrequesttowing") || lower.contains("towing")) {
                    android.util.Log.d("TowingEvent", "🚨 Nhận được event towing!")
                    android.util.Log.d("TowingEvent", "Payload: $payload")

                    try {
                        val obj = com.google.gson.JsonParser.parseString(payload).asJsonObject

                        // Parse branch ID
                        try {
                            val branchId = arrayOf(
                                "BranchId", "branchId", "GarageId", "garageId",
                                "AssignedGarageId", "assignedGarageId"
                            ).firstNotNullOfOrNull { k ->
                                if (obj.has(k)) try {
                                    obj.get(k).asString
                                } catch (_: Exception) {
                                    null
                                } else null
                            }

                            if (!branchId.isNullOrBlank()) {
                                val prefs = getSharedPreferences(
                                    com.example.garapro.utils.Constants.USER_PREFERENCES,
                                    Context.MODE_PRIVATE
                                )
                                prefs.edit().putString("last_assigned_garage_id", branchId).apply()
                                try {
                                    android.util.Log.d("EmergencyHubJoin", "joinBranchGroup id=$branchId")
                                    emergencyHub?.joinBranchGroup(branchId)
                                } catch (e: Exception) {
                                    android.util.Log.e("TowingEvent", "Lỗi join branch: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("TowingEvent", "Lỗi parse branchId: ${e.message}")
                        }

                        // Parse technician info
                        val name = when {
                            obj.has("TechnicianName") -> obj.get("TechnicianName").asString
                            obj.has("technicianName") -> obj.get("technicianName").asString
                            obj.has("Name") -> obj.get("Name").asString
                            else -> null
                        }

                        val phone = when {
                            obj.has("PhoneNumberTecnician") -> obj.get("PhoneNumberTecnician").asString
                            obj.has("phoneNumberTecnician") -> obj.get("phoneNumberTecnician").asString
                            obj.has("PhoneNumberTechnician") -> obj.get("PhoneNumberTechnician").asString
                            obj.has("TechnicianPhone") -> obj.get("TechnicianPhone").asString
                            obj.has("technicianPhone") -> obj.get("technicianPhone").asString
                            obj.has("Phone") -> obj.get("Phone").asString
                            else -> null
                        }

                        technicianName = name
                        technicianPhone = phone

                        val curId = viewModel.getCurrentEmergency()?.id
                        saveTechContactForEmergency(curId, name, phone)

                        // 🔥 SỬ DỤNG CÙNG LOGIC NHU performArrivedUI()
                        android.util.Log.d("TowingEvent", "🎯 Gọi performArrivedUI()")
                        performArrivedUI()

                    } catch (e: Exception) {
                        android.util.Log.e("TowingEvent", "❌ LỖI xử lý towing event: ${e.message}")
                        e.printStackTrace()
                    }
                }
//                if (lower.contains("emergencyrequesttowing") || lower.contains("towing")) {
//                    android.util.Log.d("TowingEvent", " Received towing event!")
//                    android.util.Log.d("TowingEvent", "Payload: $payload")
//
//                    try {
//                        val obj =
//                            com.google.gson.JsonParser.parseString(payload).asJsonObject
//                        try {
//                            val branchId = arrayOf(
//                                "BranchId",
//                                "branchId",
//                                "GarageId",
//                                "garageId",
//                                "AssignedGarageId",
//                                "assignedGarageId"
//                            ).firstNotNullOfOrNull { k ->
//                                if (obj.has(k)) try {
//                                    obj.get(k).asString
//                                } catch (_: Exception) {
//                                    null
//                                } else null
//                            }
//                            if (!branchId.isNullOrBlank()) {
//                                val prefs = getSharedPreferences(
//                                    com.example.garapro.utils.Constants.USER_PREFERENCES,
//                                    Context.MODE_PRIVATE
//                                )
//                                prefs.edit()
//                                    .putString("last_assigned_garage_id", branchId)
//                                    .apply()
//                                try {
//                                    android.util.Log.d(
//                                        "EmergencyHubJoin",
//                                        "joinBranchGroup id=" + branchId
//                                    )
//                                    emergencyHub?.joinBranchGroup(branchId)
//                                } catch (_: Exception) {
//                                }
//                            }
//                        } catch (_: Exception) {
//                        }
//                        val name = when {
//                            obj.has("TechnicianName") -> obj.get("TechnicianName").asString
//                            obj.has("technicianName") -> obj.get("technicianName").asString
//                            obj.has("Name") -> obj.get("Name").asString
//                            else -> null
//                        }
//                        val phone = when {
//                            obj.has("PhoneNumberTecnician") -> obj.get("PhoneNumberTecnician").asString
//                            obj.has("phoneNumberTecnician") -> obj.get("phoneNumberTecnician").asString
//                            obj.has("PhoneNumberTechnician") -> obj.get("PhoneNumberTechnician").asString
//                            obj.has("TechnicianPhone") -> obj.get("TechnicianPhone").asString
//                            obj.has("technicianPhone") -> obj.get("technicianPhone").asString
//                            obj.has("Phone") -> obj.get("Phone").asString
//                            else -> null
//                        }
//                        technicianName = name
//                        technicianPhone = phone
//                        val curId = viewModel.getCurrentEmergency()?.id
//                        saveTechContactForEmergency(curId, name, phone)
//                        val garage = viewModel.assignedGarage.value
//                            ?: emergencyBottomSheet.lastSelectedGarage()
//                        if (garage != null) {
//                            technicianArrived = true
//                            trackingActive = false
//                            cameraFollowTechnician = false
//                            emergencyBottomSheet.setOnCloseClickListener { finishSafely() }
//                            emergencyBottomSheet.showArrived(
//                                garage,
//                                technicianName,
//                                technicianPhone
//                            )
//                            topAppBar.visibility = View.GONE
//                            try {
//                                fabEmergency.visibility = View.GONE
//                            } catch (_: Exception) {
//                            }
//                            try {
//                                fabCurrentLocation.visibility = View.GONE
//                            } catch (_: Exception) {
//                            }
//                            refreshTrackingFromApi()
//                        }
//                    } catch (_: Exception) {
//                    }
//                }
            }
        }
    }

    private fun requestEmergency() {
        if (!locationPermissionGranted) {
            checkLocationPermission()
            return
        }

        getCurrentLocationForEmergency()
    }

    private fun getCurrentLocationForEmergency() {
        if (!checkLocationPermission()) return
        ensureLocationSettingsEnabled {
            fetchAccurateLocation { latLng ->
                if (latLng != null) {
                    if (!isVietnamLocation(latLng)) {
                        val fallback = lastTappedLatLng
                        if (fallback != null && isVietnamLocation(fallback)) {
                            showLoading(true)
                            viewModel.requestEmergency(fallback.latitude, fallback.longitude)
                            addMarkerAtPosition(fallback, "Assistance location")
                            moveCameraToLocation(fallback)
                        } else {
                            MaterialAlertDialogBuilder(this)
                                .setTitle("Unable to determine location in Vietnam")
                                .setMessage("Please enable GPS or tap the map to choose an assistance location in Vietnam.")
                                .setPositiveButton("Close", null)
                                .show()
                        }
                        return@fetchAccurateLocation
                    }
                    pendingLatLng = latLng
                    showLoading(true)
                    addMarkerAtPosition(latLng, "Assistance location")
                    moveCameraToLocation(latLng)
                    lifecycleScope.launchWhenStarted {
                        try {
                            val resp =
                                withContext(Dispatchers.IO) { RetrofitInstance.vehicleService.getVehicles() }
                            val vehicles = if (resp.isSuccessful) (resp.body()
                                ?: emptyList()) else emptyList()
                            if (vehicles.isEmpty()) {
                                showLoading(false)
                                try {
                                    val intent = android.content.Intent(
                                        this@MapActivity,
                                        com.example.garapro.MainActivity::class.java
                                    )
                                    intent.putExtra("screen", "VehiclesFragment")
                                    intent.putExtra("action", "open_create_vehicle")
                                    startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        this@MapActivity,
                                        "Please add a vehicle first",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                return@launchWhenStarted
                            }
                            if (vehicles.size == 1) {
                                selectedVehicleId = vehicles.first().vehicleID
                                showIssueDescriptionSheet { desc ->
                                    pendingIssueDescription = desc
                                    proceedFetchNearbyAndShow()
                                }
                            } else {
                                showVehicleSelectionSheet(vehicles) { chosenId ->
                                    selectedVehicleId = chosenId
                                    showIssueDescriptionSheet { desc ->
                                        pendingIssueDescription = desc
                                        proceedFetchNearbyAndShow()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@MapActivity,
                                "Failed to load vehicles: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            showLoading(false)
                        }
                    }
                } else {
                    Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun proceedFetchNearbyAndShow() {
        val latLng = pendingLatLng ?: return
        viewModel.requestEmergency(latLng.latitude, latLng.longitude)
    }

    private fun showEmergencyUI() {
        topAppBar.visibility = View.VISIBLE
        fabEmergency.visibility = View.GONE
        fabCurrentLocation.visibility = View.VISIBLE

        // Hiển thị bottom sheet với danh sách gara
        val allGarages = viewModel.nearbyGarages.value ?: emptyList()
        var filtered = allGarages.filter { it.id !in rejectedGarageIds }
        if (filtered.isEmpty()) {
            val latLng = pendingLatLng ?: lastTappedLatLng
            latLng?.let { viewModel.refreshNearbyGarages(it.latitude, it.longitude) }
            if (allGarages.isNotEmpty()) filtered = allGarages
        }
        emergencyBottomSheet.show(
            garages = filtered,
            selectedGarage = viewModel.selectedGarage.value,
            onConfirm = {
                val emergency = viewModel.getCurrentEmergency()
                val garage = viewModel.selectedGarage.value
                if (emergency == null || garage == null) return@show
                val vehicleId = selectedVehicleId
                val issue = pendingIssueDescription
                if (vehicleId.isNullOrBlank()) {
                    try {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("No vehicle found")
                            .setMessage("You need a vehicle to create an emergency. Add one now?")
                            .setNegativeButton("Close", null)
                            .setPositiveButton("Add vehicle") { _, _ ->
                                val intent = android.content.Intent(
                                    this,
                                    com.example.garapro.MainActivity::class.java
                                )
                                intent.putExtra("screen", "VehiclesFragment")
                                intent.putExtra("action", "open_create_vehicle")
                                startActivity(intent)
                            }
                            .show()
                    } catch (_: Exception) {
                        Toast.makeText(this, "Please add a vehicle first", Toast.LENGTH_LONG)
                            .show()
                    }
                    return@show
                }
                if (issue.isNullOrBlank()) {
                    showIssueDescriptionSheet { desc ->
                        pendingIssueDescription = desc
                        topAppBar.visibility = View.GONE
                        viewModel.createEmergencyRequest(
                            vehicleId,
                            garage.id,
                            desc,
                            emergency.latitude,
                            emergency.longitude
                        )
                    }
                } else {
                    topAppBar.visibility = View.GONE
                    blockHubUI = false
                    viewModel.createEmergencyRequest(
                        vehicleId,
                        garage.id,
                        issue,
                        emergency.latitude,
                        emergency.longitude
                    )
                }
            }
        )
    }

    private fun saveLastEmergencyId(id: String) {
        val prefs = getSharedPreferences(
            com.example.garapro.utils.Constants.USER_PREFERENCES,
            Context.MODE_PRIVATE
        )
        prefs.edit().putString("last_emergency_id", id).apply()
    }

    private fun saveTechContactForEmergency(id: String?, name: String?, phone: String?) {
        if (id.isNullOrBlank()) return
        val prefs = getSharedPreferences(
            com.example.garapro.utils.Constants.USER_PREFERENCES,
            Context.MODE_PRIVATE
        )
        val ed = prefs.edit()
        name?.let { ed.putString("tech_name_" + id, it) }
        phone?.let { ed.putString("tech_phone_" + id, it) }
        ed.apply()
    }

    private fun loadTechContactForEmergency(id: String?): Pair<String?, String?> {
        if (id.isNullOrBlank()) return Pair(null, null)
        val prefs = getSharedPreferences(
            com.example.garapro.utils.Constants.USER_PREFERENCES,
            Context.MODE_PRIVATE
        )
        val n = prefs.getString("tech_name_" + id, null)
        val p = prefs.getString("tech_phone_" + id, null)
        return Pair(n, p)
    }

    private fun recoverExistingEmergency() {
        val prefs = getSharedPreferences(
            com.example.garapro.utils.Constants.USER_PREFERENCES,
            Context.MODE_PRIVATE
        )
        val restoreId =
            intent.getStringExtra("emergency_id") ?: prefs.getString("last_emergency_id", null)
        if (restoreId.isNullOrBlank()) return
        android.util.Log.d("Recover", "recoverExistingEmergency id=" + restoreId)
        lifecycleScope.launchWhenStarted {
            try {
                val resp = withContext(Dispatchers.IO) {
                    com.example.garapro.data.remote.RetrofitInstance.emergencyService.getEmergencyById(
                        restoreId
                    )
                }
                android.util.Log.d("Recover", "resp code=" + resp.code())
                if (resp.isSuccessful) {
                    val emergency = resp.body()
                    if (emergency != null) {
                        android.util.Log.d(
                            "Recover",
                            "status=" + emergency.status.name + ", garageId=" + (emergency.assignedGarageId
                                ?: "")
                        )
                        viewModel.rehydrateEmergency(emergency)
                        try {
                            android.util.Log.d(
                                "EmergencyHubJoin",
                                "joinEmergencyGroup id=" + emergency.id
                            )
                            emergencyHub?.joinEmergencyGroup(emergency.id)
                        } catch (_: Exception) {
                        }
                        val branchPref = prefs.getString("last_assigned_garage_id", null)
                        val branchId = emergency.assignedGarageId ?: branchPref
                        branchId?.let {
                            try {
                                android.util.Log.d(
                                    "EmergencyHubJoin",
                                    "joinBranchGroup id=" + it
                                )
                                emergencyHub?.joinBranchGroup(it)
                            } catch (_: Exception) {
                            }
                        }
                        when (emergency.status) {
                            com.example.garapro.data.model.emergencies.EmergencyStatus.ACCEPTED -> {
                                topAppBar.visibility = View.GONE
                                try {
                                    fabEmergency.visibility = View.GONE
                                } catch (_: Exception) {
                                }
                                try {
                                    fabCurrentLocation.visibility = View.GONE
                                } catch (_: Exception) {
                                }
                                val g = viewModel.assignedGarage.value
                                    ?: emergencyBottomSheet.lastSelectedGarage()
                                if (g != null) emergencyBottomSheet.showAcceptedWaitingForTechnician(
                                    g
                                )
                                else {
                                    emergencyBottomSheet.showAcceptedWaitingForTechnician(
                                        com.example.garapro.data.model.emergencies.Garage(
                                            id = emergency.assignedGarageId ?: "",
                                            name = "Garage",
                                            latitude = 0.0,
                                            longitude = 0.0,
                                            address = "",
                                            phone = "",
                                            isAvailable = true,
                                            price = 0.0,
                                            rating = 0f,
                                            distance = 0.0
                                        )
                                    )
                                }
                                emergencyHub?.joinEmergencyGroup(emergency.id)
                            }

                            com.example.garapro.data.model.emergencies.EmergencyStatus.IN_PROGRESS -> {
                                val g = viewModel.assignedGarage.value
                                    ?: emergencyBottomSheet.lastSelectedGarage()
                                val minutes: Int? = null
                                if (g != null) emergencyBottomSheet.showTracking(
                                    g,
                                    minutes
                                ) else emergencyBottomSheet.showTracking(
                                    com.example.garapro.data.model.emergencies.Garage(
                                        id = emergency.assignedGarageId ?: "",
                                        name = "Garage",
                                        latitude = 0.0,
                                        longitude = 0.0,
                                        address = "",
                                        phone = "",
                                        isAvailable = true,
                                        price = 0.0,
                                        rating = 0f,
                                        distance = 0.0
                                    ),
                                    minutes
                                )
                                val nameExtra = intent.getStringExtra("technician_name")
                                val phoneExtra = intent.getStringExtra("technician_phone")
                                emergencyBottomSheet.updateTrackingTechnician(
                                    nameExtra ?: technicianName, phoneExtra ?: technicianPhone
                                )
                                emergencyBottomSheet.setOnViewMapClickListener {
                                    cameraFollowTechnician = true
                                    refreshTrackingFromApi()
                                    topAppBar.visibility = View.VISIBLE
                                    tvTitle.text = "Tracking technician"
                                    enableTrackingUI()
                                    val id2 = viewModel.getCurrentEmergency()?.id
                                    if (!id2.isNullOrBlank()) viewModel.fetchRouteNow()
                                }
                                topAppBar.visibility = View.VISIBLE
                                tvTitle.text = "Tracking technician"
                                enableTrackingUI()
                                refreshTrackingFromApi()
                                if (styleLoaded) {
                                    viewModel.fetchRouteNow()
                                    viewModel.startRoutePolling()
                                    routeFetchPending = false
                                } else {
                                    routeFetchPending = true
                                }
                            }

                            com.example.garapro.data.model.emergencies.EmergencyStatus.COMPLETED -> {
                                val g = viewModel.assignedGarage.value
                                    ?: emergencyBottomSheet.lastSelectedGarage()
                                val name =
                                    intent.getStringExtra("technician_name") ?: technicianName
                                val phone =
                                    intent.getStringExtra("technician_phone") ?: technicianPhone
                                technicianArrived = true
                                trackingActive = false
                                cameraFollowTechnician = false
                                if (g != null) {
                                    emergencyBottomSheet.setOnCloseClickListener { finishSafely() }
                                    emergencyBottomSheet.showArrived(g, name, phone)
                                } else {
                                    emergencyBottomSheet.setOnCloseClickListener { finishSafely() }
                                    emergencyBottomSheet.showArrived(
                                        com.example.garapro.data.model.emergencies.Garage(
                                            id = emergency.assignedGarageId ?: "",
                                            name = "Garage",
                                            latitude = 0.0,
                                            longitude = 0.0,
                                            address = "",
                                            phone = "",
                                            isAvailable = true,
                                            price = 0.0,
                                            rating = 0f,
                                            distance = 0.0
                                        ),
                                        name,
                                        phone
                                    )
                                }
                                topAppBar.visibility = View.GONE
                                try {
                                    viewModel.stopRoutePolling()
                                } catch (_: Exception) {
                                }
                            }

                            else -> {
                                val g = viewModel.assignedGarage.value
                                    ?: emergencyBottomSheet.lastSelectedGarage()
                                if (g != null) emergencyBottomSheet.showWaitingForGarage(g) else emergencyBottomSheet.showWaitingForGarage(
                                    com.example.garapro.data.model.emergencies.Garage(
                                        id = emergency.assignedGarageId ?: "",
                                        name = "Garage",
                                        latitude = 0.0,
                                        longitude = 0.0,
                                        address = "",
                                        phone = "",
                                        isAvailable = true,
                                        price = 0.0,
                                        rating = 0f,
                                        distance = 0.0
                                    )
                                )
                                topAppBar.visibility = View.GONE
                            }
                        }
                    }
                } else {
                    android.util.Log.w("Recover", "not successful: code=" + resp.code())
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun showVehicleSelectionSheet(
        vehicles: List<Vehicle>,
        onSelected: (String) -> Unit
    ) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.fragment_vehicle_selection, null)
        dialog.setContentView(view)
        val rv = view.findViewById<RecyclerView>(R.id.rvVehicles)
        val tvSelected = view.findViewById<TextView>(R.id.tvSelectedVehicle)
        val btnNext =
            view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNext)
        var chosenId: String? = null
        val rrVehicles = vehicles.map { v ->
            RRVehicle(
                vehicleID = v.vehicleID,
                brandID = v.brandID,
                userID = "",
                modelID = v.modelID ?: "",
                colorID = v.colorID,
                licensePlate = v.licensePlate ?: "",
                vin = v.vin ?: "",
                year = v.year ?: 0,
                odometer = (v.odometer ?: 0L).toInt(),
                lastServiceDate = null,
                nextServiceDate = null,
                warrantyStatus = "",
                brandName = v.brandName ?: "",
                modelName = v.modelName ?: "",
                colorName = v.colorName ?: ""
            )
        }
        val adapter = VehicAdapter(rrVehicles) { v ->
            chosenId = v.vehicleID
            tvSelected.text = "${v.brandName} ${v.modelName} - ${v.licensePlate}"
            btnNext.isEnabled = true
            btnNext.setBackgroundColor(android.graphics.Color.BLACK)
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        adapter.updateData(rrVehicles)
        btnNext.setOnClickListener {
            val id = chosenId
            if (!id.isNullOrBlank()) {
                dialog.dismiss()
                onSelected(id)
            }
        }
        dialog.show()
    }

    private fun showIssueDescriptionSheet(onDone: (String) -> Unit) {
        val input = android.widget.EditText(this)
        input.setText("Flat tire")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Issue description")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                val desc = input.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
                    ?: "Roadside assistance"
                onDone(desc)
            }
            .show()
    }

    private fun hideEmergencyUI() {
        topAppBar.visibility = View.GONE
        fabEmergency.visibility = View.VISIBLE
        fabCurrentLocation.visibility = View.GONE
        emergencyBottomSheet.dismiss()
        viewModel.resetState()
        rejectedGarageIds.clear()
    }

    private fun finishSafely() {
        try {
            activityActive = false
        } catch (_: Exception) {
        }
        try {
            viewModel.stopRoutePolling()
        } catch (_: Exception) {
        }
        try {
            emergencyHub?.stop()
        } catch (_: Exception) {
        }
        try {
            fallbackStyleRunnable?.let { mainHandler.removeCallbacks(it) }
        } catch (_: Exception) {
        }
        fallbackStyleRunnable = null
        try {
            mapView?.onPause()
        } catch (_: Exception) {
        }
        try {
            mapView?.onStop()
        } catch (_: Exception) {
        }
        finish()
    }

    private fun navigateHome() {
        try {
            viewModel.stopRoutePolling()
        } catch (_: Exception) {
        }
        try {
            emergencyHub?.stop()
        } catch (_: Exception) {
        }
        try {
            val intent = Intent(this, com.example.garapro.MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
        }
        finish()
    }


    private fun showLoading(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }


    // Các hàm cũ giữ nguyên từ đây trở xuống...
    override fun onMapReady(@NonNull map: MapLibreMap) {
        this.maplibreMap = map
        loadMapStyle()
    }

    private fun loadMapStyle() {
        styleLoaded = false
        val goongStyleUrl =
            "https://tiles.goong.io/assets/goong_map_web.json?api_key=" + getString(R.string.goong_map_key)
        maplibreMap?.setStyle(
            Style.Builder().fromUri(goongStyleUrl),
            object : Style.OnStyleLoaded {
                override fun onStyleLoaded(@NonNull style: Style) {
                    if (isFinishing) return
                    styleLoaded = true
                    try {
                        fallbackStyleRunnable?.let { mainHandler.removeCallbacks(it) }
                    } catch (_: Exception) {
                    }
                    fallbackStyleRunnable = null
                    setupMap()
                    addSampleMarkers(style)
                    addRouteLayer(style)
                    addTechnicianLayer(style)
                    setupMapListeners()
                    if (trackingActive || routeFetchPending) {
                        val id = viewModel.getCurrentEmergency()?.id
                            ?: intent.getStringExtra("emergency_id")
                        if (!id.isNullOrBlank()) {
                            android.util.Log.d("Route", "fetch after styleLoaded")
                            viewModel.fetchRouteNowFor(id!!)
                            viewModel.startRoutePollingFor(id!!)
                            routeFetchPending = false
                        }
                    }

                }
            })

        fallbackStyleRunnable = Runnable {
            if (!styleLoaded && mapView != null && !isFinishing) {
                val fallback = "https://demotiles.maplibre.org/style.json"
                maplibreMap?.setStyle(
                    Style.Builder().fromUri(fallback),
                    object : Style.OnStyleLoaded {
                        override fun onStyleLoaded(@NonNull style: Style) {
                            styleLoaded = true
                            setupMap()
                            addSampleMarkers(style)
                            addRouteLayer(style)
                            addTechnicianLayer(style)
                            setupMapListeners()
                            if (trackingActive || routeFetchPending) {
                                val id = viewModel.getCurrentEmergency()?.id
                                    ?: intent.getStringExtra("emergency_id")
                                if (!id.isNullOrBlank()) {
                                    android.util.Log.d(
                                        "Route",
                                        "fetch after styleLoaded (fallback style)"
                                    )
                                    viewModel.fetchRouteNowFor(id!!)
                                    viewModel.startRoutePollingFor(id!!)
                                    routeFetchPending = false
                                }
                            }
                        }
                    })
            }
        }
        try {
            fallbackStyleRunnable?.let { mainHandler.postDelayed(it, 4000) }
        } catch (_: Exception) {
        }
    }

    private fun setupMap() {
        val hanoi = LatLng(21.0295797, 105.8524247)
        val position = CameraPosition.Builder()
            .target(hanoi)
            .zoom(12.0)
            .tilt(0.0)
            .build()
        maplibreMap?.setCameraPosition(position)
    }

    private fun addSampleMarkers(style: Style) {
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_location)
        if (drawable is VectorDrawable) {
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            style.addImage("custom-marker", bitmap)
            style.addImage("tech-marker", bitmap)
        } else {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_location)
            style.addImage("custom-marker", bitmap)
            style.addImage("tech-marker", bitmap)
        }

        val featureCollection = JsonObject().apply {
            addProperty("type", "FeatureCollection")
            add("features", JsonArray())
        }

        style.addSource(GeoJsonSource("marker-source", featureCollection.toString()))
        style.addLayer(
            SymbolLayer("marker-layer", "marker-source").withProperties(
                PropertyFactory.iconImage("custom-marker"),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            )
        )
    }

    private fun addTechnicianLayer(style: Style) {
        // Thêm icon xe vào style
        val carDrawable = ContextCompat.getDrawable(this, R.drawable.ic_car)
        if (carDrawable is VectorDrawable) {
            val bitmap = Bitmap.createBitmap(
                carDrawable.intrinsicWidth,
                carDrawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            carDrawable.setBounds(0, 0, canvas.width, canvas.height)
            carDrawable.draw(canvas)
            style.addImage("tech-car-marker", bitmap)
        } else if (carDrawable != null) {
            // Fallback nếu không phải VectorDrawable
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_car)
            style.addImage("tech-car-marker", bitmap)
        }

        // Tạo empty source
        val empty = JsonObject().apply {
            addProperty("type", "FeatureCollection")
            add("features", JsonArray())
        }
        style.addSource(GeoJsonSource("technician-source", empty.toString()))

        // Thay CircleLayer bằng SymbolLayer để hiển thị icon xe
        style.addLayer(
            SymbolLayer("technician-layer", "technician-source").withProperties(
                PropertyFactory.iconImage("tech-car-marker"),
                PropertyFactory.iconSize(1.0f), // Điều chỉnh kích thước (0.5 - 2.0)
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconRotationAlignment("map"),
                PropertyFactory.iconPitchAlignment("map")
            )
        )
    }

    private fun addRouteLayer(style: Style) {
        val empty = JsonObject().apply {
            addProperty("type", "FeatureCollection")
            add("features", JsonArray())
        }
        style.addSource(GeoJsonSource("route-source", empty.toString()))
        style.addLayer(
            LineLayer("route-layer", "route-source").withProperties(
                PropertyFactory.lineColor("#FF0000"),          // Đỏ tươi
                PropertyFactory.lineWidth(5.0f),               // Độ dày vừa phải
                PropertyFactory.lineOpacity(0.9f),             // Hơi trong
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),  // Bo góc
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),    // Bo đầu
                PropertyFactory.lineBlur(0.5f)
            )
        )
        // 2. Source và layer cho VỊ TRÍ KHÁCH HÀNG (customer location)
        style.addSource(
            GeoJsonSource(
                "customer-location-source",
                empty.toString()
            )
        ) // Đổi tên source
        style.addLayer(
            SymbolLayer(
                "customer-location-layer",
                "customer-location-source"
            ).withProperties( // Đổi tên layer
                PropertyFactory.iconImage("customer-marker"),  // Đổi tên icon
                PropertyFactory.iconSize(1.2f),                // Kích thước lớn hơn
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            )
        )

        // 3. Source và layer cho xe kỹ thuật viên
//        style.addSource(GeoJsonSource("technician-source", empty.toString()))
//        style.addLayer(
//            SymbolLayer("technician-layer", "technician-source").withProperties(
//                PropertyFactory.iconImage("tech-car-marker"),
//                PropertyFactory.iconSize(1.3f),
//                PropertyFactory.iconAllowOverlap(true),
//                PropertyFactory.iconIgnorePlacement(true)
//            )
//        )
        //
    }

    private fun enableTrackingUI() {
        trackingActive = true
        val tech = technicianLatLng
        if (tech != null) moveCameraToLocation(tech)
        emergencyBottomSheet.updateTrackingSkeleton(tech == null)
        Toast.makeText(this, "Đang theo dõi kỹ thuật viên", Toast.LENGTH_SHORT).show()
        try {
            fabEmergency.visibility = View.GONE
        } catch (_: Exception) {
        }
        try {
            fabCurrentLocation.visibility = View.GONE
        } catch (_: Exception) {
        }
    }

    private fun openExternalMap(garage: com.example.garapro.data.model.emergencies.Garage?) {
        val tech = technicianLatLng
        if (tech != null) {
            try {
                val uri =
                    android.net.Uri.parse("google.navigation:q=${tech.latitude},${tech.longitude}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
            try {
                val uri =
                    android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${tech.latitude},${tech.longitude}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        val addr = garage?.address
        if (!addr.isNullOrBlank()) {
            try {
                val uri = android.net.Uri.parse(
                    "geo:0,0?q=" + java.net.URLEncoder.encode(
                        addr,
                        "UTF-8"
                    )
                )
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
            try {
                val uri = android.net.Uri.parse(
                    "https://www.google.com/maps/search/?api=1&query=" + java.net.URLEncoder.encode(
                        addr,
                        "UTF-8"
                    )
                )
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        val lat = garage?.latitude ?: pendingLatLng?.latitude
        val lng = garage?.longitude ?: pendingLatLng?.longitude
        if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
            try {
                val uri = android.net.Uri.parse("google.navigation:q=${lat},${lng}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
            try {
                val uri =
                    android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        try {
            val uri =
                android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=21.0295797,105.8524247")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Không thể mở bản đồ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMapListeners() {
        maplibreMap?.addOnMapClickListener { point ->
            Toast.makeText(
                this@MapActivity,
                "Clicked: ${point.latitude}, ${point.longitude}",
                Toast.LENGTH_SHORT
            ).show()
            addMarkerAtPosition(point)
            lastTappedLatLng = point
            true
        }


    }

    private fun checkArrivalAndUpdateUI(tech: LatLng) {
        val dest = destinationLatLng
            ?: (viewModel.getCurrentEmergency()?.let { LatLng(it.latitude, it.longitude) })
            ?: pendingLatLng
            ?: return
        val destLat = dest.latitude
        val destLng = dest.longitude
        if (destLat == 0.0 && destLng == 0.0) return
        val d = haversineMeters(tech.latitude, tech.longitude, destLat, destLng)
        handleArrivalCandidate(d, tech)
    }

    private fun refreshTrackingFromApi() {
        val id = (viewModel.getCurrentEmergency()?.id
            ?: intent.getStringExtra("emergency_id"))
            ?.takeIf { it.isNotBlank() } ?: return
        lifecycleScope.launchWhenStarted {
            try {
                val prefs = getSharedPreferences(
                    com.example.garapro.utils.Constants.USER_PREFERENCES,
                    Context.MODE_PRIVATE
                )
                val uid = prefs.getString("user_id", null) ?: getSharedPreferences(
                    "auth_prefs",
                    Context.MODE_PRIVATE
                ).getString("user_id", null)
                if (!uid.isNullOrBlank()) {
                    val listResp = withContext(Dispatchers.IO) {
                        RetrofitInstance.emergencyService.getEmergenciesByCustomer(uid)
                    }
                    if (listResp.isSuccessful) {
                        val summary =
                            listResp.body()?.firstOrNull { it.emergencyRequestId == id }
                        summary?.assignedTechnicianName?.let { nm -> technicianName = nm }
                        summary?.assignedTechnicianPhone?.let { ph -> technicianPhone = ph }
                        if (technicianPhone.isNullOrBlank()) {
                            val (_, ph) = loadTechContactForEmergency(id)
                            technicianPhone = ph ?: technicianPhone
                        }
                        emergencyBottomSheet.updateTrackingTechnician(
                            technicianName,
                            technicianPhone
                        )
                    }
                }
                val statusResp = withContext(Dispatchers.IO) {
                    RetrofitInstance.emergencyService.getEmergencyById(id)
                }
                if (statusResp.isSuccessful) {
                    val em = statusResp.body()
                    if (em != null) {
                        viewModel.rehydrateEmergency(em)
                        val garage = viewModel.assignedGarage.value
                            ?: emergencyBottomSheet.lastSelectedGarage()
                        when (em.status) {
                            com.example.garapro.data.model.emergencies.EmergencyStatus.IN_PROGRESS -> {
                                if (garage != null) {
                                    if (technicianArrived) {
                                        emergencyBottomSheet.setOnCloseClickListener { finishSafely() }
                                        emergencyBottomSheet.showArrived(
                                            garage,
                                            technicianName,
                                            technicianPhone
                                        )
                                        topAppBar.visibility = View.GONE
                                        try {
                                            fabEmergency.visibility = View.GONE
                                        } catch (_: Exception) {
                                        }
                                        try {
                                            fabCurrentLocation.visibility = View.GONE
                                        } catch (_: Exception) {
                                        }
                                    } else {
                                        try {
                                            val gLat = garage.latitude
                                            val gLng = garage.longitude
                                            if ((technicianLatLng == null || (technicianLatLng?.latitude == 0.0 && technicianLatLng?.longitude == 0.0)) && (gLat != 0.0 || gLng != 0.0)) {
                                                val init = LatLng(gLat, gLng)
                                                technicianLatLng = init
                                                if (activityActive && styleLoaded) {
                                                    val style = maplibreMap?.style
                                                    var techSrc =
                                                        style?.getSourceAs<GeoJsonSource>("technician-source")
                                                    if (techSrc == null && style != null) {
                                                        addTechnicianLayer(style)
                                                        techSrc =
                                                            style.getSourceAs("technician-source")
                                                    }
                                                    val techFeature = JsonObject().apply {
                                                        addProperty("type", "Feature")
                                                        add("geometry", JsonObject().apply {
                                                            addProperty("type", "Point")
                                                            add(
                                                                "coordinates",
                                                                JsonArray().apply {
                                                                    add(gLng)
                                                                    add(gLat)
                                                                })
                                                        })
                                                    }
                                                    val techFc = JsonObject().apply {
                                                        addProperty("type", "FeatureCollection")
                                                        add(
                                                            "features",
                                                            JsonArray().apply { add(techFeature) })
                                                    }
                                                    techSrc?.setGeoJson(techFc.toString())
                                                    emergencyBottomSheet.updateTrackingSkeleton(
                                                        false
                                                    )
                                                }
                                            }
                                        } catch (_: Exception) {
                                        }
                                        topAppBar.visibility = View.VISIBLE
                                        tvTitle.text = "Tracking technician"
                                        enableTrackingUI()
                                        emergencyBottomSheet.showTracking(garage, null)
                                        emergencyBottomSheet.updateTrackingTechnician(
                                            technicianName,
                                            technicianPhone
                                        )
                                        try {
                                            fabEmergency.visibility = View.GONE
                                        } catch (_: Exception) {
                                        }
                                        try {
                                            fabCurrentLocation.visibility = View.GONE
                                        } catch (_: Exception) {
                                        }
                                        val id2 = viewModel.getCurrentEmergency()?.id
                                        if (!id2.isNullOrBlank()) viewModel.fetchRouteNow()
                                    }
                                }
                            }

                            com.example.garapro.data.model.emergencies.EmergencyStatus.COMPLETED -> {
                                technicianArrived = true
                                trackingActive = false
                                if (garage != null) {
                                    emergencyBottomSheet.setOnCloseClickListener { finishSafely() }
                                    emergencyBottomSheet.showArrived(
                                        garage,
                                        technicianName,
                                        technicianPhone
                                    )
                                    topAppBar.visibility = View.GONE
                                }
                            }

                            com.example.garapro.data.model.emergencies.EmergencyStatus.ACCEPTED -> {
                                if (garage != null) emergencyBottomSheet.showAcceptedWaitingForTechnician(
                                    garage
                                )
                                topAppBar.visibility = View.GONE
                                try {
                                    fabEmergency.visibility = View.GONE
                                } catch (_: Exception) {
                                }
                                try {
                                    fabCurrentLocation.visibility = View.GONE
                                } catch (_: Exception) {
                                }
                            }

                            else -> {}
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun startTrackingTechnician() {
        if (technicianArrived) return
        val garage = viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
        val minutes: Int? = null
        if (garage != null) {
            cameraFollowTechnician = true
            topAppBar.visibility = View.VISIBLE
            tvTitle.text = "Tracking technician"
            enableTrackingUI()
            emergencyBottomSheet.showTracking(garage, minutes)
            emergencyBottomSheet.setOnViewMapClickListener {
                cameraFollowTechnician = true
                refreshTrackingFromApi()
                topAppBar.visibility = View.VISIBLE
                tvTitle.text = "Tracking technician"
                enableTrackingUI()
                val id2 = viewModel.getCurrentEmergency()?.id
                if (!id2.isNullOrBlank()) viewModel.fetchRouteNow()
            }
            refreshTrackingFromApi()
            val id = viewModel.getCurrentEmergency()?.id
            if (!id.isNullOrBlank()) {
                if (styleLoaded) {
                    viewModel.fetchRouteNow()
                    viewModel.startRoutePolling()
                    routeFetchPending = false
                } else {
                    routeFetchPending = true
                }
            }
        } else {
            cameraFollowTechnician = true
            topAppBar.visibility = View.VISIBLE
            tvTitle.text = "Tracking technician"
            enableTrackingUI()
            val fallback = com.example.garapro.data.model.emergencies.Garage(
                id = viewModel.getCurrentEmergency()?.assignedGarageId ?: "",
                name = "Garage",
                latitude = 0.0,
                longitude = 0.0,
                address = "",
                phone = "",
                isAvailable = true,
                price = 0.0,
                rating = 0f,
                distance = 0.0
            )
            emergencyBottomSheet.showTracking(fallback, minutes)
            emergencyBottomSheet.updateTrackingTechnician(technicianName, technicianPhone)
            emergencyBottomSheet.setOnViewMapClickListener {
                cameraFollowTechnician = true
                refreshTrackingFromApi()
                topAppBar.visibility = View.VISIBLE
                tvTitle.text = "Tracking technician"
                enableTrackingUI()
                val id2 = viewModel.getCurrentEmergency()?.id
                if (!id2.isNullOrBlank()) viewModel.fetchRouteNow()
            }
            refreshTrackingFromApi()
            val id = viewModel.getCurrentEmergency()?.id
            if (!id.isNullOrBlank()) {
                if (styleLoaded) {
                    viewModel.fetchRouteNow()
                    viewModel.startRoutePolling()
                    routeFetchPending = false
                } else {
                    routeFetchPending = true
                }
            }
        }
    }

    private fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    private fun shouldAllowArrived(): Boolean {
        val now = System.currentTimeMillis()
        val timeOk =
            if (inProgressStartedAt == 0L) true else (now - inProgressStartedAt >= ARRIVAL_CONFIRM_MS)
        return timeOk && (arrivalConsecutive >= ARRIVAL_CONFIRM_COUNT)
    }

    private fun performArrivedUI() {
        try {
            viewModel.stopRoutePolling()
        } catch (_: Exception) {
        }
        val garage = viewModel.assignedGarage.value
            ?: emergencyBottomSheet.lastSelectedGarage()
            ?: viewModel.getCurrentEmergency()?.let {
                com.example.garapro.data.model.emergencies.Garage(
                    id = it.assignedGarageId ?: "",
                    name = "Garage",
                    latitude = 0.0,
                    longitude = 0.0,
                    address = "",
                    phone = "",
                    isAvailable = true,
                    price = 0.0,
                    rating = 0f,
                    distance = 0.0
                )
            }
        if (garage != null) {
            technicianArrived = true
            trackingActive = false
            cameraFollowTechnician = false
            runOnUiThread {
                try {
                    emergencyBottomSheet.setOnCloseClickListener { finishSafely() }
                    emergencyBottomSheet.showArrived(garage, technicianName, technicianPhone)
                    // Toast.makeText(this@MapActivity, " Technician has arrived!", Toast.LENGTH_LONG).show()
                    try {
                        topAppBar.visibility = View.GONE
                    } catch (_: Exception) {
                    }
                    try {
                        fabEmergency.visibility = View.GONE
                    } catch (_: Exception) {
                    }
                    try {
                        fabCurrentLocation.visibility = View.GONE
                    } catch (_: Exception) {
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun handleArrivalCandidate(distanceMeters: Double?, tech: LatLng?) {
        var ok = false
        val dest = destinationLatLng
            ?: (viewModel.getCurrentEmergency()?.let { LatLng(it.latitude, it.longitude) })
            ?: pendingLatLng
        if (tech != null && dest != null && dest.latitude != 0.0 && dest.longitude != 0.0) {
            val d =
                haversineMeters(tech.latitude, tech.longitude, dest.latitude, dest.longitude)
            ok = ok || (d <= ARRIVAL_THRESHOLD_METERS)
        }
        if (ok) {
            val now = System.currentTimeMillis()
            if (now - lastArrivalCandidateAt <= ARRIVAL_CONFIRM_MS) {
                arrivalConsecutive += 1
            } else {
                arrivalConsecutive = 1
            }
            lastArrivalCandidateAt = now
            if (!technicianArrived && shouldAllowArrived() && arrivalConsecutive >= ARRIVAL_CONFIRM_COUNT) {
                performArrivedUI()
            }
        } else {
            arrivalConsecutive = 0
        }
    }

    private fun addMarkerAtPosition(position: LatLng, title: String = "Location") {
        if (!activityActive || !styleLoaded) return

        // CHỈ GIỮ MARKER CỦA CUSTOMER
        if (title == "Current location" || title == "Assistance location" || title == "Vị trí hiện tại") {
            markerPositions.clear()
            markerPositions.add(position)
        } else {
            // Bỏ qua các marker khác (không thêm vào)
            return
        }

        // Tạo marker cho customer location
        val featuresArray = JsonArray()
        markerPositions.forEach { latLng ->
            val feature = JsonObject().apply {
                addProperty("type", "Feature")
                add("geometry", JsonObject().apply {
                    addProperty("type", "Point")
                    add("coordinates", JsonArray().apply {
                        add(latLng.longitude)
                        add(latLng.latitude)
                    })
                })
                add("properties", JsonObject().apply {
                    addProperty("title", "Customer Location")
                })
            }
            featuresArray.add(feature)
        }

        maplibreMap?.getStyle()?.getSourceAs<GeoJsonSource>("marker-source")
            ?.setGeoJson(featureCollection(featuresArray))

        // Chỉ hiển thị toast cho assistance location
        if (title == "Assistance location" || title == "Vị trí hiện tại") {
            Toast.makeText(this, "Marker added: $title", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fine) {
            locationPermissionGranted = true
            return true
        }
        com.example.garapro.ui.common.LocationPermissionDialog.show(this, onAllow = {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        })
        return false
    }

    private fun moveCameraToLocation(latLng: LatLng) {
        if (!activityActive || !styleLoaded) return
        val position = CameraPosition.Builder()
            .target(latLng)
            .zoom(17.0)
            .tilt(0.0)
            .build()
        maplibreMap?.setCameraPosition(position)
    }

    private fun isVietnamLocation(latLng: LatLng): Boolean {
        val lat = latLng.latitude
        val lng = latLng.longitude
        return lat in 8.0..24.0 && lng in 102.0..110.0
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true
                getCurrentLocation()
            } else {
                val rationale = ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                if (!rationale) {
                    com.example.garapro.ui.common.LocationPermissionDialog.showDenied(this) {
                        openAppSettings()
                    }
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = android.net.Uri.parse("package:" + packageName)
        startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        if (!locationPermissionGranted) {
            checkLocationPermission()
            return
        }
        ensureLocationSettingsEnabled {
            try {
                fetchAccurateLocation { latLng ->
                    latLng?.let {
                        moveCameraToLocation(it)
                        addMarkerAtPosition(it, "Vị trí hiện tại")
                    }
                }
            } catch (e: SecurityException) {
                Log.e("Location", "Security exception: ${e.message}")
                Toast.makeText(
                    this,
                    "Không thể truy cập vị trí: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchAccurateLocation(callback: (LatLng?) -> Unit) {
        val cts = com.google.android.gms.tasks.CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            cts.token
        )
            .addOnSuccessListener { loc ->
                if (loc != null && loc.accuracy <= 100f) {
                    Toast.makeText(
                        this,
                        "GPS: ${loc.latitude}, ${loc.longitude} (±${loc.accuracy}m)",
                        Toast.LENGTH_SHORT
                    ).show()
                    callback(LatLng(loc.latitude, loc.longitude))
                } else {
                    val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                        .setMinUpdateIntervalMillis(1000L)
                        .build()
                    val cb = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            val l = result.lastLocation
                            if (l != null && l.accuracy <= 100f) {
                                fusedLocationClient.removeLocationUpdates(this)
                                Toast.makeText(
                                    this@MapActivity,
                                    "GPS: ${l.latitude}, ${l.longitude} (±${l.accuracy}m)",
                                    Toast.LENGTH_SHORT
                                ).show()
                                callback(LatLng(l.latitude, l.longitude))
                            }
                        }
                    }
                    fusedLocationClient.requestLocationUpdates(req, cb, Looper.getMainLooper())
                    Handler(Looper.getMainLooper()).postDelayed({
                        fusedLocationClient.removeLocationUpdates(cb)
                        callback(null)
                    }, 15000L)
                }
            }
            .addOnFailureListener { _ -> callback(null) }
    }

    private fun featureCollection(featuresArray: JsonArray): String {
        return JsonObject().apply {
            addProperty("type", "FeatureCollection")
            add("features", featuresArray)
        }.toString()
    }

    private fun ensureLocationSettingsEnabled(onReady: () -> Unit) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).build()
        val builder = com.google.android.gms.location.LocationSettingsRequest.Builder()
            .addLocationRequest(request)
            .setAlwaysShow(true)
        val client = LocationServices.getSettingsClient(this)
        client.checkLocationSettings(builder.build())
            .addOnSuccessListener { onReady() }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    try {
                        e.startResolutionForResult(this, REQUEST_CHECK_SETTINGS)
                    } catch (_: Exception) {
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Vui lòng bật GPS để lấy vị trí hiện tại",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) {
                getCurrentLocation()
            } else {
                Toast.makeText(this, "GPS chưa được bật", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val REQUEST_CHECK_SETTINGS = 2002
        private const val ARRIVAL_THRESHOLD_METERS = 5.0
    }

    // Lifecycle methods
    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        activityActive = true
        intent.getStringExtra("emergency_id")?.let { id ->
            saveLastEmergencyId(id)
        }
        android.util.Log.d(
            "MapActivity",
            "onResume eid=" + (intent.getStringExtra("emergency_id") ?: "")
        )
        val forceNew = intent.getBooleanExtra("force_new", false)
        if (!forceNew) {
            val userPrefs = getSharedPreferences(
                com.example.garapro.utils.Constants.USER_PREFERENCES,
                Context.MODE_PRIVATE
            )
            val authPrefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            if (intent.getStringExtra("emergency_id").isNullOrBlank()) {
                val userId =
                    userPrefs.getString("user_id", null) ?: authPrefs.getString("user_id", null)
                if (!userId.isNullOrBlank()) {
                    lifecycleScope.launchWhenStarted {
                        try {
                            val listResp = withContext(Dispatchers.IO) {
                                com.example.garapro.data.remote.RetrofitInstance.emergencyService.getEmergenciesByCustomer(
                                    userId
                                )
                            }
                            if (listResp.isSuccessful && (listResp.body()
                                    ?.isNotEmpty() == true)
                            ) {
                                startActivity(
                                    Intent(
                                        this@MapActivity,
                                        EmergencyListActivity::class.java
                                    )
                                )
                                finishSafely()
                                return@launchWhenStarted
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
        if (!forceNew && !blockHubUI) recoverExistingEmergency()

        try {
            val prefs = getSharedPreferences(
                com.example.garapro.utils.Constants.USER_PREFERENCES,
                Context.MODE_PRIVATE
            )
            val userId = prefs.getString("user_id", null) ?: getSharedPreferences(
                "auth_prefs",
                Context.MODE_PRIVATE
            ).getString("user_id", null)
            if (emergencyHub?.isConnected() != true) {
                android.util.Log.d("EmergencyHub", "onResume: hub not connected, starting...")
                emergencyHub?.start {
                    userId?.let {
                        android.util.Log.d("EmergencyHubJoin", "rejoin customer id=" + it)
                        emergencyHub?.joinCustomerGroup(it)
                    }
                    val eid = viewModel.getCurrentEmergency()?.id
                        ?: intent.getStringExtra("emergency_id")
                    eid?.let {
                        android.util.Log.d("EmergencyHubJoin", "rejoin emergency id=" + it)
                        emergencyHub?.joinEmergencyGroup(it)
                    }
                    val branchId = viewModel.assignedGarage.value?.id
                        ?: prefs.getString("last_assigned_garage_id", null)
                    branchId?.let {
                        android.util.Log.d("EmergencyHubJoin", "rejoin branch id=" + it)
                        emergencyHub?.joinBranchGroup(it)
                    }
                }
            } else {
                val eid =
                    viewModel.getCurrentEmergency()?.id ?: intent.getStringExtra("emergency_id")
                eid?.let {
                    android.util.Log.d("EmergencyHubJoin", "ensure joined emergency id=" + it)
                    try {
                        emergencyHub?.joinEmergencyGroup(it)
                    } catch (_: Exception) {
                    }
                }
                val branchId = viewModel.assignedGarage.value?.id
                    ?: prefs.getString("last_assigned_garage_id", null)
                branchId?.let {
                    android.util.Log.d("EmergencyHubJoin", "ensure joined branch id=" + it)
                    try {
                        emergencyHub?.joinBranchGroup(it)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
        styleLoaded = false
        activityActive = false
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
        activityActive = false
        viewModel.stopRoutePolling()
        emergencyHub?.stop()
        styleLoaded = false

    }

    override fun onSaveInstanceState(@NonNull outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
        activityActive = false
        try {
            emergencyHub?.stop()
        } catch (_: Exception) {
        }
        styleLoaded = false
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }
}

// Extension function
fun Double.formatPrice(): String = "%,.0f đ".format(this)