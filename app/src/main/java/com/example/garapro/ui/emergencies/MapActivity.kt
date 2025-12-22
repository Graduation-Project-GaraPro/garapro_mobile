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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.example.garapro.utils.formatDistance
import com.example.garapro.utils.formatPrice
import kotlinx.coroutines.withContext
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private var mapView: MapView? = null
    private var maplibreMap: MapLibreMap? = null
    private val markerPositions: MutableList<LatLng> = ArrayList()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationPermissionGranted = false

    // UI Components
    private lateinit var topAppBar: View
    private lateinit var btnBack: ImageButton
    private lateinit var fabBack: FloatingActionButton
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
    private var mapController: MapController? = null

    // Adapter
    private lateinit var garageAdapter: GarageAdapter
    private var styleLoaded = false
    private var activityActive = false

    private var lastTappedLatLng: LatLng? = null
    private var selectedVehicleId: String? = null
    private var pendingIssueDescription: String? = null
    private var pendingLatLng: LatLng? = null
    // private var emergencyHub: EmergencySignalRService? = null
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
        
        // Initialize SignalR via ViewModel
        val service = EmergencySignalRService(hubUrl)
        service.setupListeners()
        viewModel.setSignalRService(service)
        service.start {
             userId?.let { viewModel.connectAndJoin(it) }
        }

        // Initialize Map
        loadingIndicator.visibility = View.VISIBLE
        initMapView(savedInstanceState)
    }

    // Logic xử lý intent sẽ được gọi sau khi map & location sẵn sàng
    private fun processIntentData() {
        val forceNew = intent.getBooleanExtra("force_new", false)
        val eid = intent.getStringExtra("emergency_id")
        val hasId = eid?.isNotBlank() == true
        
        Log.d("MapActivity", "processIntentData forceNew=$forceNew, hasId=$hasId, eid=${eid ?: ""}")

        if (hasId) {
            handleExistingEmergencyIntent(intent)
            eid?.let { viewModel.checkExistingEmergency(it) }
        } else if (forceNew) {
            blockHubUI = true
            // FIX: Don't auto-start emergency. Just reset state and let user click the button.
            viewModel.resetState()
            updateUIVisibility(topBar = false, fabEmer = true, fabLoc = true)
        } else {
            handleDefaultLaunch()
        }
    }

    private fun handleExistingEmergencyIntent(intent: Intent) {
        val st = intent.getStringExtra("status")?.lowercase()
        if (st == "inprogress" || st == "in_progress") {
            val garage = viewModel.assignedGarage.value
                ?: emergencyBottomSheet.lastSelectedGarage()
                ?: return

           // emergencyBottomSheet.showTracking(garage, null)

            updateUIVisibility(topBar = true, fabEmer = false, fabLoc = false)
            tvTitle.text = "Tracking technician"
            enableTrackingUI()
            emergencyBottomSheet.showTracking(garage, null)
            routeFetchPending = true
        } else if (st == "accepted") {
            val g = viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
            if (g != null) emergencyBottomSheet.showAcceptedWaitingForTechnician(g)
        }
    }

    private fun handleDefaultLaunch() {
        val userPrefs = getSharedPreferences(com.example.garapro.utils.Constants.USER_PREFERENCES, Context.MODE_PRIVATE)
        val authPrefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val uid = userPrefs.getString("user_id", null) ?: authPrefs.getString("user_id", null)
        
        if (uid.isNullOrBlank()) {
            // User not logged in or ID missing, just stay on map
        } else {
            lifecycleScope.launchWhenCreated {
                checkActiveEmergencies(uid)
            }
        }
    }

    private suspend fun checkActiveEmergencies(uid: String) {
        try {
            val resp = withContext(Dispatchers.IO) {
                RetrofitInstance.emergencyService.getEmergenciesByCustomer(uid)
            }
            if (resp.isSuccessful && (resp.body()?.isNotEmpty() == true)) {
                startActivity(Intent(this@MapActivity, EmergencyListActivity::class.java))
                finishSafely()
            } else {
                // No active emergencies, stay on map
            }
        } catch (_: Exception) {
            // Error checking, stay on map
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
        fabBack = findViewById(R.id.fabBack) // Init new button
        tvTitle = findViewById(R.id.tvTitle)
        bottomSheetContainer = findViewById(R.id.bottomSheetContainer)
        fabEmergency = findViewById(R.id.fabEmergency)
        fabCurrentLocation = findViewById(R.id.fabCurrentLocation)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        // Hide top app bar initially
        topAppBar.visibility = View.GONE
        fabBack.visibility = View.VISIBLE // Show by default when top bar is hidden
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

        // Setup OnDismiss to handle "Cancel Request"
        emergencyBottomSheet.setOnDismissClickListener {
             navigateHome()
        }

        // Thiết lập listener cho nút "Chọn garage khác"
        emergencyBottomSheet.setOnChooseAnotherListener {
            val currentEmergency = viewModel.getCurrentEmergency()
            
            Log.d("MapActivity_DEBUG", "Nhấn Chọn Garage Khác. ID Emergency: ${currentEmergency?.id}")
            
            // Kiểm tra xem có emergency đang hoạt động không
            if (currentEmergency == null) {
                Log.e("MapActivity_DEBUG", "Không có emergency đang hoạt động")
                return@setOnChooseAnotherListener
            }
            
            // Đóng bottom sheet trước (Sử dụng dismissSilently để tránh trigger về Home)
            emergencyBottomSheet.dismissSilently()
            
            // Dùng coroutine thay vì Handler (an toàn hơn với lifecycle)
             lifecycleScope.launch {
                 // Đợi animation đóng bottom sheet hoàn tất (300ms đủ)
                 delay(300)
                 
                 if (isFinishing || isDestroyed) {
                     Log.e("MapActivity_DEBUG", "Activity is finishing, aborting retry.")
                     return@launch
                 }

                 Log.d("MapActivity_DEBUG", "Bắt đầu reset trạng thái...")
                 
                 // 1. Xóa danh sách garage đã từ chối -> cho phép chọn lại
                 rejectedGarageIds.clear()
                 Log.d("MapActivity_DEBUG", "Đã clear rejectedGarageIds")
                 
                 // 2. Reset trạng thái về ban đầu
                 Log.d("MapActivity_DEBUG", "Calling resetStateForRetry...")
                 viewModel.resetStateForRetry()
                 
                 // 3. Kiểm tra và refresh danh sách garage nếu cần
                 val garages = viewModel.nearbyGarages.value
                 if (garages.isNullOrEmpty()) {
                     Log.d("MapActivity_DEBUG", "Danh sách garage trống, đang refresh...")
                     
                     // Lấy vị trí để tìm garage gần đó
                     val location = pendingLatLng ?: lastTappedLatLng
                     
                     if (location != null) {
                         // Tìm garage gần vị trí này
                         viewModel.refreshNearbyGarages(location.latitude, location.longitude)
                     } else {
                         Log.e("MapActivity_DEBUG", "Không có thông tin vị trí")
                         Toast.makeText(this@MapActivity, "Không tìm thấy vị trí. Hãy chạm vào bản đồ để chọn vị trí.", Toast.LENGTH_SHORT).show()
                     }
                 }
                 
                 // Luôn hiển thị UI (dù danh sách trống thì hiện thông báo trống) để người dùng biết app đang phản hồi
                 Log.d("MapActivity_DEBUG", "Calling showEmergencyUI()...")
                 showEmergencyUI()
             }
         }
    }

    private fun setupClickListeners() {
        fabBack.setOnClickListener {
            // Check state
            if (waitingForGarageActive || trackingActive || viewModel.assignedGarage.value != null) {
                val garage = viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
                if (garage != null) {
                     emergencyBottomSheet.showTracking(garage, null)
                     updateUIVisibility(topBar = true, fabEmer = false, fabLoc = false)
                } else {
                     finishSafely()
                }
            } else {
                finishSafely()
            }
        }

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

    private fun updateUIVisibility(
        topBar: Boolean = false,
        fabEmer: Boolean = true,
        fabLoc: Boolean = true,
        loading: Boolean = false
    ) {
        try {
            topAppBar.visibility = if (topBar) View.VISIBLE else View.GONE
            fabEmergency.visibility = if (fabEmer) View.VISIBLE else View.GONE
            fabCurrentLocation.visibility = if (fabLoc) View.VISIBLE else View.GONE
            loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        } catch (_: Exception) { }
    }

    private fun setupObservers() {
        viewModel.emergencyState.observe(this) { state ->
            when (state) {
                is EmergencyState.Loading -> updateUIVisibility(loading = true, fabEmer = false, fabLoc = false)
                is EmergencyState.Success -> {
                    updateUIVisibility(loading = false, fabEmer = true, fabLoc = true)
                    showEmergencyUI()
                    state.emergency.id.takeIf { it.isNotBlank() }?.let { saveLastEmergencyId(it) }
                }
                is EmergencyState.WaitingForGarage -> handleWaitingForGarage(state)
                is EmergencyState.Confirmed -> handleConfirmedState(state)
                is EmergencyState.TowingStarted -> handleTowingStarted(state)
                is EmergencyState.Towing -> handleTowingState(state)
            is EmergencyState.Expired -> {
                val garage = state.garage ?: emergencyBottomSheet.lastSelectedGarage()
                if (garage != null) {
                    emergencyBottomSheet.showExpired(garage)
                } else {
                    hideEmergencyUI()
                    Toast.makeText(this, "Yêu cầu đã hết hạn", Toast.LENGTH_LONG).show()
                }
            }
            is EmergencyState.Completed -> handleCompletedState(state)
            is EmergencyState.Error -> handleErrorState(state)
                else -> {}
            }
        }
        
        // Update BottomSheet if it is showing Tracking info
        if (viewModel.emergencyState.value is EmergencyState.Confirmed) {
             val state = viewModel.emergencyState.value as EmergencyState.Confirmed
             if (state.emergency.status == com.example.garapro.data.model.emergencies.EmergencyStatus.IN_PROGRESS) {

                 emergencyBottomSheet.updateTrackingInfo(
                     viewModel.distanceMeters.value,
                     viewModel.etaMinutes.value
                 )
             }
        }
        
        setupDataObservers()
    }

    private fun setupDataObservers() {
        viewModel.nearbyGarages.observe(this) { garages ->
            val filtered = garages.filter { it.id !in rejectedGarageIds }
            
            // Check if we need to auto-show the bottom sheet (e.g. after refresh)
            val state = viewModel.emergencyState.value
            if (state is EmergencyState.Success && !emergencyBottomSheet.isShowing() && filtered.isNotEmpty()) {
                 // Ensure main thread
                 mainHandler.post { showEmergencyUI() }
            } else if (emergencyBottomSheet.isShowing()) {
                 emergencyBottomSheet.updateGarages(filtered)
            }
        }

        viewModel.selectedGarage.observe(this) { garage ->
            if (emergencyBottomSheet.isShowing()) emergencyBottomSheet.updateSelectedGarage(garage)
            garage?.let { viewModel.joinBranchGroup(it.id) }
        }

        viewModel.routeGeoJson.observe(this) { fc -> mapController?.drawRoute(fc) }

        viewModel.technicianLocation.observe(this) { loc ->
            loc?.let { 
                mapController?.updateTechnicianLocation(it.first, it.second)
                if (cameraFollowTechnician) moveCameraToLocation(LatLng(it.first, it.second))
            }
        }

        viewModel.customerLocation.observe(this) { loc ->
            // In Towing mode, the destination marker is the Garage, handled explicitly in handleTowingState.
            // We should ignore updates to customer location to avoid overwriting the garage marker.
            val state = viewModel.emergencyState.value
            if (state is EmergencyState.Towing || state is EmergencyState.TowingStarted) {
                return@observe
            }
            loc?.let { mapController?.updateCustomerLocation(it.first, it.second) }
        }

        viewModel.etaMinutes.observe(this) { m ->
            if (m != null) emergencyBottomSheet.updateTrackingEta(m)
        }

        viewModel.distanceMeters.observe(this) { dist ->
            if (dist != null) emergencyBottomSheet.updateTrackingDistance(dist / 1000.0)
        }

        viewModel.technicianName.observe(this) { name ->
            technicianName = name
            
            // Check current state. If Assigned or InProgress, update the special UI
            val state = viewModel.emergencyState.value
            val garage = viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
            
            if (garage != null) {
                if (state is EmergencyState.Confirmed) {
                    val em = state.emergency
                    if (em.status == com.example.garapro.data.model.emergencies.EmergencyStatus.ASSIGNED) {
                        emergencyBottomSheet.showTechnicianAssigned(garage, name, technicianPhone)
                    } else if (em.status == com.example.garapro.data.model.emergencies.EmergencyStatus.IN_PROGRESS) {
                        emergencyBottomSheet.showTechnicianEnRoute(
                            garage, 
                            name, 
                            technicianPhone,
                            viewModel.distanceMeters.value,
                            viewModel.etaMinutes.value
                        )
                        emergencyBottomSheet.setOnViewMapClickListener {
                            cameraFollowTechnician = true
                            viewModel.fetchRouteNow()
                        }
                    }
                }
            } else {
                 emergencyBottomSheet.updateTrackingTechnician(name, technicianPhone)
            }
        }

        viewModel.technicianPhone.observe(this) { phone ->
            technicianPhone = phone
            emergencyBottomSheet.updateTrackingTechnician(technicianName, phone)
        }

        viewModel.isTechnicianArrived.observe(this) { arrived ->
             if (arrived) handleTechnicianArrived()
        }
    }

    private fun handleWaitingForGarage(state: EmergencyState.WaitingForGarage) {
        Log.d("EmergencyState", "WaitingForGarage: ${state.garage.name}")
        updateUIVisibility(topBar = false, fabEmer = false, fabLoc = false)
        waitingForGarageActive = true
        mapView?.post { emergencyBottomSheet.showWaitingForGarage(state.garage) }
        viewModel.getCurrentEmergency()?.id?.takeIf { it.isNotBlank() }?.let { saveLastEmergencyId(it) }
    }

    private fun handleConfirmedState(state: EmergencyState.Confirmed) {
        if (blockHubUI) return
        updateUIVisibility(loading = false, fabEmer = false, fabLoc = false, topBar = true)
        waitingForGarageActive = false
        
        val emergency = state.emergency
        val garage = viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
        
        if (emergency.status == com.example.garapro.data.model.emergencies.EmergencyStatus.IN_PROGRESS) {
            if (viewModel.isTechnicianArrived.value == true) {
                return
            }

            // IN_PROGRESS: Show "Technician En Route" with Live Data
            if (garage != null) {
                emergencyBottomSheet.showTechnicianEnRoute(
                    garage, 
                    technicianName, 
                    technicianPhone,
                    viewModel.distanceMeters.value,
                    viewModel.etaMinutes.value
                )
                // Set click listeners similar to TOWING
                emergencyBottomSheet.setOnViewMapClickListener {
                    cameraFollowTechnician = true
                    viewModel.fetchRouteNow()
                }
            } else {
                setupTrackingUI(garage, emergency) // Fallback
            }
        } else if (emergency.status == com.example.garapro.data.model.emergencies.EmergencyStatus.ASSIGNED) {
            // ASSIGNED: Show "Preparing..."
            if (garage != null) {
                emergencyBottomSheet.showTechnicianAssigned(garage, technicianName, technicianPhone)
            }
        } else {
            // ACCEPTED (Waiting for assignment)
            // Ẩn thanh top bar khi đang hiển thị bottom sheet
            topAppBar.visibility = View.GONE
            if (garage != null) {
                emergencyBottomSheet.showAcceptedWaitingForTechnician(garage)
            }
            Toast.makeText(this, "Garage accepted! Waiting for technician assignment", Toast.LENGTH_SHORT).show()
        }
        emergency.id.takeIf { it.isNotBlank() }?.let { saveLastEmergencyId(it) }
    }

    private fun setupTrackingUI(garage: Garage?, emergency: com.example.garapro.data.model.emergencies.Emergency) {
        val trackingGarage = garage ?: com.example.garapro.data.model.emergencies.Garage(
            id = emergency.assignedGarageId ?: "",
            name = "Garage", latitude = 0.0, longitude = 0.0,
            address = "", phone = "", isAvailable = true, price = 0.0, rating = 0f, distance = 0.0
        )
        
        tvTitle.text = "Tracking technician"
        enableTrackingUI()
        emergencyBottomSheet.showTracking(trackingGarage, null)
        
        emergencyBottomSheet.setOnViewMapClickListener {
            cameraFollowTechnician = true
            // refreshTrackingFromApi() -> REMOVED to prevent infinite loop
            tvTitle.text = "Tracking technician"
            enableTrackingUI()
            // viewModel.fetchRouteNow() -> REMOVED, already handled by periodic updates
            
            // Hide TopBar in Tracking Mode
            topAppBar.visibility = View.GONE
        }
        
        // Only trigger fetch if not already tracking/fetching to avoid loop
        if (!trackingActive) {
            refreshTrackingFromApi()
            if (styleLoaded) {
                viewModel.fetchRouteNow()
                routeFetchPending = false
            } else {
                routeFetchPending = true
            }
        }
    }

    private fun handleErrorState(state: EmergencyState.Error) {
        updateUIVisibility(loading = false)
        if (waitingForGarageActive) return
        
        val msg = state.message
        val lower = msg.lowercase()
        
        if (lower.contains("existing emergency") || lower.contains("active emergency") || lower.contains("429")) {
             showErrorDialog("Request already exists", "You already have an active request.") {
                 val garage = viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
                 if (garage != null) {
                     emergencyBottomSheet.showAccepted(garage, null)
                     emergencyBottomSheet.setOnTrackClickListener { startTrackingTechnician() }
                 } else showEmergencyUI()
             }
        } else if (lower.contains("rejected") || lower.contains("declined")) {
            val garage = emergencyBottomSheet.lastSelectedGarage()
            if (garage != null) {
                emergencyBottomSheet.showRejected(garage, msg)
            } else {
                showErrorDialog("Garage rejected", "The garage cannot accept your request.") { showEmergencyUI() }
            }
        } else {
            val friendly = if (lower.contains("timeout")) "Network timeout. Check connection." else msg
            MaterialAlertDialogBuilder(this)
                .setTitle("Action Failed")
                .setMessage(friendly)
                .setNegativeButton("Close") { _, _ -> navigateHome() }
                .setPositiveButton("Retry") { _, _ ->
                    if (pendingLatLng != null) proceedFetchNearbyAndShow() else getCurrentLocationForEmergency()
                }
                .show()
        }
    }

    private fun showErrorDialog(title: String, msg: String, onPositive: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(msg)
            .setNegativeButton("Close", null)
            .setPositiveButton("OK") { _, _ -> onPositive() }
            .show()
    }

    private fun handleTechnicianArrived() {
         technicianArrived = true
         val garage = viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
         if (garage != null) {
              emergencyBottomSheet.setOnCloseClickListener { finishSafely() }
              emergencyBottomSheet.showArrived(garage, technicianName, technicianPhone)
              updateUIVisibility(topBar = false, fabEmer = false, fabLoc = false)
         }
    }

    private fun handleCompletedState(state: EmergencyState.Completed) {
        Log.d("MapActivity", "Handling Completed State. Emergency ID: ${state.emergency?.id}")
        
        // Clear route, markers, etc.
        mapController?.clearRoute()
        mapController?.setCustomerVisibility(true) // Reset visibility if needed or hide everything
        trackingActive = false
        cameraFollowTechnician = false
        updateUIVisibility(topBar = false, fabEmer = false, fabLoc = false)
        emergencyBottomSheet.dismiss()

        // Show Completed BottomSheet
        val garage = viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
        Log.d("MapActivity", "Completed State - Garage: ${garage?.name}, Tech: $technicianName, Phone: $technicianPhone")
        
        if (garage != null) {
            emergencyBottomSheet.showCompleted(garage, technicianName, technicianPhone)
            emergencyBottomSheet.setOnCloseClickListener {
                navigateHome()
            }
        } else {
             MaterialAlertDialogBuilder(this)
                .setTitle("Mission Completed")
                .setMessage("The emergency service has been completed successfully.")
                .setPositiveButton("Finish") { _, _ ->
                     navigateHome()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun handleTowingStarted(state: EmergencyState.TowingStarted) {
        trackingActive = false
        cameraFollowTechnician = false
        
        // Hide Customer Marker when Towing starts, but show Garage Marker (destination)
        mapController?.setCustomerVisibility(true) // Ensure layer is visible
        val garage = viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
        val gLat = garage?.latitude
        val gLng = garage?.longitude
        if (gLat != null && gLng != null) {
            // Update "Customer" marker to be Garage location for towing destination
            mapController?.updateCustomerLocation(gLat, gLng, isGarage = true)
        }

        if (garage != null) {
             emergencyBottomSheet.showTowing(garage, technicianName, technicianPhone)
             emergencyBottomSheet.setOnCloseClickListener {
                  viewModel.startTowingRoute()
             }
        }
    }

    private fun handleTowingState(state: EmergencyState.Towing) {
        trackingActive = false
        cameraFollowTechnician = true
        
        // Ensure Garage marker is shown as destination
        mapController?.setCustomerVisibility(true)
        val garage = viewModel.assignedGarage.value ?: emergencyBottomSheet.lastSelectedGarage()
        val gLat = garage?.latitude
        val gLng = garage?.longitude
        if (gLat != null && gLng != null) {
            mapController?.updateCustomerLocation(gLat, gLng, isGarage = true)
        }

        val dist = viewModel.distanceMeters.value
        val eta = viewModel.etaMinutes.value
        
        if (garage != null) {
             emergencyBottomSheet.showTowing(
                 garage = garage, 
                 techName = technicianName, 
                 techPhone = technicianPhone,
                 distanceMeters = dist,
                 etaMinutes = eta
             )
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
        updateUIVisibility(topBar = true, fabEmer = false, fabLoc = true)

        val allGarages = viewModel.nearbyGarages.value ?: emptyList()
        var filtered = allGarages.filter { it.id !in rejectedGarageIds }
        
        if (filtered.isEmpty()) {
            (pendingLatLng ?: lastTappedLatLng)?.let { 
                viewModel.refreshNearbyGarages(it.latitude, it.longitude) 
            }
            if (allGarages.isNotEmpty()) filtered = allGarages
        }
        
        emergencyBottomSheet.show(
            garages = filtered,
            selectedGarage = viewModel.selectedGarage.value,
            onConfirm = { handleEmergencyConfirm() },
            onDismiss = { 
                // Only navigate home if we are NOT in a retry flow
                // Check if bottom sheet is being dismissed for retry
                if (viewModel.emergencyState.value !is EmergencyState.Success) {
                     navigateHome()
                }
            }
        )
    }

    private fun handleEmergencyConfirm() {
        val emergency = viewModel.getCurrentEmergency() ?: return
        val garage = viewModel.selectedGarage.value ?: return
        val vehicleId = selectedVehicleId
        val issue = pendingIssueDescription
        
        if (vehicleId.isNullOrBlank()) {
            promptAddVehicle()
            return
        }
        
        if (issue.isNullOrBlank()) {
            showIssueDescriptionSheet { desc ->
                pendingIssueDescription = desc
                submitEmergencyRequest(vehicleId, garage.id, desc, emergency)
            }
        } else {
            submitEmergencyRequest(vehicleId, garage.id, issue, emergency)
        }
    }

    private fun submitEmergencyRequest(vehicleId: String, garageId: String, issue: String, emergency: com.example.garapro.data.model.emergencies.Emergency) {
        topAppBar.visibility = View.GONE
        blockHubUI = false
        viewModel.createEmergencyRequest(
            vehicleId,
            garageId,
            issue,
            emergency.latitude,
            emergency.longitude
        )
    }

    private fun promptAddVehicle() {
        try {
            MaterialAlertDialogBuilder(this)
                .setTitle("No vehicle found")
                .setMessage("You need a vehicle to create an emergency. Add one now?")
                .setNegativeButton("Close", null)
                .setPositiveButton("Add vehicle") { _, _ ->
                    val intent = Intent(this, com.example.garapro.MainActivity::class.java).apply {
                        putExtra("screen", "VehiclesFragment")
                        putExtra("action", "open_create_vehicle")
                    }
                    startActivity(intent)
                }
                .show()
        } catch (_: Exception) {
            Toast.makeText(this, "Please add a vehicle first", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveLastEmergencyId(id: String) {
        val prefs = getSharedPreferences(
            com.example.garapro.utils.Constants.USER_PREFERENCES,
            Context.MODE_PRIVATE
        )
        prefs.edit().putString("last_emergency_id", id).apply()
    }





    private fun showVehicleSelectionSheet(
        vehicles: List<Vehicle>,
        onSelected: (String) -> Unit
    ) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.fragment_vehicle_selection, null)
        dialog.setContentView(view)
        
        // CRITICAL: Prevent dismissal
        dialog.setCancelable(false)
        dialog.behavior.isDraggable = false
        
        val rv = view.findViewById<RecyclerView>(R.id.rvVehicles)
        val tvSelected = view.findViewById<TextView>(R.id.tvSelectedVehicle)
        val btnNext = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNext)
        val btnAdd = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddVehicle)
        
        // Handle "Add Vehicle" logic
        btnAdd?.setOnClickListener {
             // Navigate to Add Vehicle
             val intent = Intent(this, com.example.garapro.MainActivity::class.java).apply {
                 putExtra("screen", "VehiclesFragment")
                 putExtra("action", "open_create_vehicle")
             }
             startActivity(intent)
             dialog.dismiss() 
        }

        // Handle Empty State
        if (vehicles.isEmpty()) {
            rv.visibility = View.GONE
            view.findViewById<View>(R.id.emptyState)?.visibility = View.VISIBLE
            tvSelected.text = "Please add a vehicle to continue"
            btnNext.visibility = View.GONE
        } else {
            rv.visibility = View.VISIBLE
            view.findViewById<View>(R.id.emptyState)?.visibility = View.GONE
        }

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
        val adapter = VehicleAdapter(rrVehicles) { v ->
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
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_issue_description, null)
        dialog.setContentView(view)
        
        // CRITICAL: Prevent dismissal
        dialog.setCancelable(false)
        dialog.behavior.isDraggable = false

        val etIssue = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etIssue)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmit)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val tvWordCount = view.findViewById<TextView>(R.id.tvWordCount)

        etIssue.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s?.toString()?.trim() ?: ""
                val words = if (text.isEmpty()) 0 else text.split("\\s+".toRegex()).size
                tvWordCount.text = "$words words"
                
                // Validation: At least 10 words
                btnSubmit.isEnabled = words >= 5
            }
        })

        btnCancel.setOnClickListener {
            // Allow going back to vehicle selection or cancel entirely
            dialog.dismiss()
            pendingLatLng = null
            markerPositions.clear()
            maplibreMap?.clear() // Clear marker
            hideEmergencyUI()
        }

        btnSubmit.setOnClickListener {
            val desc = etIssue.text.toString().trim()
            dialog.dismiss()
            onDone(desc)
        }

        dialog.show()
    }

    private fun hideEmergencyUI() {
        updateUIVisibility(topBar = false, fabEmer = true, fabLoc = false)
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
            viewModel.stopSignalR()
        } catch (_: Exception) {
        }
        try {
            fallbackStyleRunnable?.let { mainHandler.removeCallbacks(it) }
        } catch (_: Exception) {
        }
        fallbackStyleRunnable = null
        finish()
    }

    private fun navigateHome() {
        try {
            viewModel.stopRoutePolling()
        } catch (_: Exception) {
        }
        try {
            viewModel.stopSignalR()
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
        this.mapController = MapController(map, this)
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
                    mapController?.initialize(style)
                    setupMapListeners()
                    
                    // STEP 2: Map Loaded -> Check GPS
                    checkLocationAndStart()
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
                            mapController?.initialize(style)
                            setupMapListeners()
                            
                            // STEP 2: Map Loaded -> Check GPS
                            checkLocationAndStart()
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

    private fun enableTrackingUI() {
        trackingActive = true
        technicianLatLng?.let { moveCameraToLocation(it) }
        emergencyBottomSheet.updateTrackingSkeleton(technicianLatLng == null)
        // Toast.makeText(this, "Đang theo dõi kỹ thuật viên", Toast.LENGTH_SHORT).show()
        updateUIVisibility(topBar = true, fabEmer = false, fabLoc = false)
    }

    private fun openExternalMap(garage: com.example.garapro.data.model.emergencies.Garage?) {
        val tech = technicianLatLng
        val targetLat: Double
        val targetLng: Double
        
        if (tech != null && tech.latitude != 0.0) {
            targetLat = tech.latitude
            targetLng = tech.longitude
        } else if (garage != null && garage.latitude != 0.0) {
            targetLat = garage.latitude
            targetLng = garage.longitude
        } else if (pendingLatLng != null) {
             targetLat = pendingLatLng!!.latitude
             targetLng = pendingLatLng!!.longitude
        } else {
             targetLat = 21.0295797
             targetLng = 105.8524247
        }
        
        val addr = garage?.address
        if (!addr.isNullOrBlank() && tech == null) {
             if (tryLaunchMap("geo:0,0?q=${java.net.URLEncoder.encode(addr, "UTF-8")}")) return
             if (tryLaunchMap("https://www.google.com/maps/search/?api=1&query=${java.net.URLEncoder.encode(addr, "UTF-8")}")) return
        }

        if (tryLaunchMap("google.navigation:q=$targetLat,$targetLng")) return
        if (tryLaunchMap("https://www.google.com/maps/dir/?api=1&destination=$targetLat,$targetLng")) return
        // Toast.makeText(this, "Không thể mở bản đồ", Toast.LENGTH_SHORT).show()
    }

    private fun tryLaunchMap(uriString: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uriString))
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun setupMapListeners() {
        maplibreMap?.addOnMapClickListener { point ->
            // Toast.makeText(
            //     this@MapActivity,
            //     "Clicked: ${point.latitude}, ${point.longitude}",
            //     Toast.LENGTH_SHORT
            // ).show()
            addMarkerAtPosition(point)
            lastTappedLatLng = point
            true
        }


    }



    private fun refreshTrackingFromApi() {
        val id = (viewModel.getCurrentEmergency()?.id
            ?: intent.getStringExtra("emergency_id"))
            ?.takeIf { it.isNotBlank() } ?: return
        
        val prefs = getSharedPreferences(
            com.example.garapro.utils.Constants.USER_PREFERENCES,
            Context.MODE_PRIVATE
        )
        val uid = prefs.getString("user_id", null) ?: getSharedPreferences(
            "auth_prefs",
            Context.MODE_PRIVATE
        ).getString("user_id", null)
        
        viewModel.refreshEmergencyData(id, uid)
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
                    // viewModel.startRoutePolling()
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
                   // viewModel.startRoutePolling()
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


    private fun addMarkerAtPosition(position: LatLng, title: String = "Location") {
        if (!activityActive || !styleLoaded) return

        // CHỈ GIỮ MARKER CỦA CUSTOMER
        if (title == "Current location" || title == "Assistance location" || title == "Vị trí hiện tại") {
            markerPositions.clear()
            markerPositions.add(position)
            mapController?.updateCustomerLocation(position.latitude, position.longitude)
        } else {
            // Bỏ qua các marker khác (không thêm vào)
            return
        }
    }

    private fun checkLocationAndStart() {
        if (!checkLocationPermission()) return // Will request permission
        getCurrentLocationForStartup()
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
                getCurrentLocationForStartup()
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
                    callback(LatLng(loc.latitude, loc.longitude))
                } else {
                    var isCallbackCalled = false
                    val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                        .setMinUpdateIntervalMillis(2000L)
                        .setMaxUpdates(1)
                        .build()
                    
                    val cb = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            if (isCallbackCalled) return
                            val l = result.lastLocation
                            if (l != null) {
                                isCallbackCalled = true
                                fusedLocationClient.removeLocationUpdates(this)
                                callback(LatLng(l.latitude, l.longitude))
                            }
                        }
                    }
                    fusedLocationClient.requestLocationUpdates(req, cb, Looper.getMainLooper())
                    
                    // Fallback timeout
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isCallbackCalled) {
                            isCallbackCalled = true
                            fusedLocationClient.removeLocationUpdates(cb)
                            // Fallback to the initial location if available (even if low accuracy)
                            if (loc != null) {
                                callback(LatLng(loc.latitude, loc.longitude))
                            } else {
                                callback(null)
                            }
                        }
                    }, 10000L)
                }
            }
            .addOnFailureListener { _ -> callback(null) }
    }



    @SuppressLint("MissingPermission")
    private fun getCurrentLocationForStartup() {
        ensureLocationSettingsEnabled {
            fetchAccurateLocation { latLng ->
                if (latLng != null) {
                     moveCameraToLocation(latLng)
                     addMarkerAtPosition(latLng, "Vị trí hiện tại")
                     // GPS Done -> Process Intent Logic
                     showLoading(false)
                     processIntentData()
                } else {
                     Toast.makeText(this, "Không thể lấy vị trí. Dùng vị trí mặc định.", Toast.LENGTH_SHORT).show()
                     showLoading(false)
                     processIntentData()
                }
            }
        }
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
                getCurrentLocationForStartup()
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntentData()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        activityActive = true
        
        val forceNew = intent.getBooleanExtra("force_new", false)
        if (!forceNew && !blockHubUI) {
             resumeAppState()
        }
    }

    private fun resumeAppState() {
        try {
            val prefs = getSharedPreferences(com.example.garapro.utils.Constants.USER_PREFERENCES, Context.MODE_PRIVATE)
            val userId = prefs.getString("user_id", null) ?: getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).getString("user_id", null)
            val eid = viewModel.getCurrentEmergency()?.id ?: intent.getStringExtra("emergency_id") ?: prefs.getString("last_emergency_id", null)
            val branchId = viewModel.assignedGarage.value?.id ?: prefs.getString("last_assigned_garage_id", null)
            
            viewModel.checkExistingEmergency(eid)
            viewModel.resumeConnection(userId, eid, branchId)
        } catch (_: Exception) { }
    }

    override fun onPause() {
        super.onPause()
        try {
            mapView?.onPause()
        } catch (_: Exception) {}
        styleLoaded = false
        activityActive = false
    }

    override fun onStop() {
        super.onStop()
        try {
            mapView?.onStop()
        } catch (_: Exception) {}
        activityActive = false
        viewModel.stopRoutePolling()
        viewModel.stopSignalR()
        styleLoaded = false
    }

    override fun onSaveInstanceState(@NonNull outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            mapView?.onSaveInstanceState(outState)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        // Try destroying map view BEFORE super.onDestroy to ensure GL context is cleaned up
        // while the activity is still valid.
        try {
            mapView?.onDestroy()
        } catch (e: Exception) {
            Log.e("MapActivity", "Error destroying map: ${e.message}")
        }
        mapView = null 
        activityActive = false
        try {
            viewModel.stopSignalR()
        } catch (_: Exception) {
        }
        styleLoaded = false
        
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            mapView?.onLowMemory()
        } catch (_: Exception) {}
    }
}

