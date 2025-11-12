package com.example.garapro.ui.emergencies

import EmergencyViewModel
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.VectorDrawable
import android.location.LocationManager
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

import com.google.android.gms.location.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
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
//        setupBottomSheet()
        setupClickListeners()
        setupObservers()

        // Initialize map view
        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermission()
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
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_emergency_choose_garage, null)
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
                viewModel.confirmEmergency(it.id)
            }
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            hideEmergencyUI()
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
                }

                is EmergencyState.WaitingForGarage -> {
                    Log.d("EmergencyState", "🟢 WaitingForGarage triggered for ${state.garage.name}")
                    showLoading(false)
                    mapView?.post {
                        emergencyBottomSheet?.showWaitingForGarage(state.garage)
                    }
                }

                is EmergencyState.Confirmed -> {
                    showLoading(false)
                    // Có thể show confirmed screen hoặc đơn giản dismiss
                    emergencyBottomSheet?.dismiss()
                    Toast.makeText(this, "Đã xác nhận với gara!", Toast.LENGTH_SHORT).show()
                }

                is EmergencyState.Error -> {
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                    emergencyBottomSheet.dismiss()
                }
                else -> {}
            }
        }

        viewModel.nearbyGarages.observe(this) { garages ->
            if (emergencyBottomSheet.isShowing()) {
                emergencyBottomSheet.updateGarages(garages)
            }
        }

        viewModel.selectedGarage.observe(this) { garage ->
            if (emergencyBottomSheet.isShowing()) {
                emergencyBottomSheet.updateSelectedGarage(garage)
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
        if (checkLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)

                    // Show loading and request emergency
                    showLoading(true)
                    viewModel.requestEmergency(currentLatLng.latitude, currentLatLng.longitude)

                    // Add emergency location marker
                    addMarkerAtPosition(currentLatLng, "Vị trí cứu hộ")
                    moveCameraToLocation(currentLatLng)
                } ?: run {
                    Toast.makeText(this, "Không thể lấy vị trí", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showEmergencyUI() {
        topAppBar.visibility = View.VISIBLE
        fabEmergency.visibility = View.GONE

        // Hiển thị bottom sheet với danh sách gara
        emergencyBottomSheet.show(
            garages = viewModel.nearbyGarages.value ?: emptyList(),
            selectedGarage = viewModel.selectedGarage.value,
            onConfirm = {
                val emergency = viewModel.getCurrentEmergency()
                emergency?.let {
                    viewModel.confirmEmergency(it.id)
                }
            },
            onDismiss = {
                hideEmergencyUI()
            }
        )
    }

    private fun hideEmergencyUI() {
        topAppBar.visibility = View.GONE
        fabEmergency.visibility = View.VISIBLE
        emergencyBottomSheet.dismiss()
        viewModel.resetState()
    }



    private fun showLoading(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    // Các hàm cũ giữ nguyên từ đây trở xuống...
    override fun onMapReady(@NonNull map: MapLibreMap) {
        this.maplibreMap = map

        val goongStyleUrl = "https://tiles.goong.io/assets/goong_map_web.json?api_key=" + getString(R.string.goong_map_key)

        map.setStyle(Style.Builder().fromUri(goongStyleUrl), object : Style.OnStyleLoaded {
            override fun onStyleLoaded(@NonNull style: Style) {
                setupMap()
                addSampleMarkers(style)
                setupMapListeners()

                Toast.makeText(
                    this@MapActivity,
                    "Goong Map with MapLibre loaded!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
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
        } else {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_location)
            style.addImage("custom-marker", bitmap)
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

    private fun setupMapListeners() {
        maplibreMap?.addOnMapClickListener { point ->
            Toast.makeText(
                this@MapActivity,
                "Clicked: ${point.latitude}, ${point.longitude}",
                Toast.LENGTH_SHORT
            ).show()
            addMarkerAtPosition(point)
            true
        }

        maplibreMap?.addOnCameraMoveListener {
            maplibreMap?.cameraPosition?.let { position ->
                Log.d("MapMove", "Camera: ${position.target}, Zoom: ${position.zoom}")
            }
        }
    }

    private fun addMarkerAtPosition(position: LatLng, title: String = "Location") {
        markerPositions.add(position)
        val featuresArray = JsonArray()
        markerPositions.forEachIndexed { i, latLng ->
            val markerTitle = if (i == markerPositions.size - 1 && title == "Vị trí hiện tại") {
                title
            } else {
                "Location ${i + 1}"
            }
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
                    addProperty("title", markerTitle)
                })
            }
            featuresArray.add(feature)
        }
        maplibreMap?.getStyle()?.getSourceAs<GeoJsonSource>("marker-source")
            ?.setGeoJson(featureCollection(featuresArray))
        Toast.makeText(this, "Marker added: $title", Toast.LENGTH_SHORT).show()
    }

    private fun checkLocationPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionGranted = true
            return true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return false
        }
    }

    private fun moveCameraToLocation(latLng: LatLng) {
        val position = CameraPosition.Builder()
            .target(latLng)
            .zoom(15.0)
            .build()
        maplibreMap?.cameraPosition = position
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
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        if (!locationPermissionGranted) {
            Toast.makeText(this, "Vui lòng cấp quyền truy cập vị trí", Toast.LENGTH_SHORT).show()
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Vui lòng bật GPS để lấy vị trí hiện tại", Toast.LENGTH_LONG).show()
            return
        }

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                if (lastLocation != null) {
                    val cachedLatLng = LatLng(lastLocation.latitude, lastLocation.longitude)
                    moveCameraToLocation(cachedLatLng)
                    addMarkerAtPosition(cachedLatLng, "Vị trí gần nhất")
                }
            }

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                10_000L
            ).build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        moveCameraToLocation(currentLatLng)
                        addMarkerAtPosition(currentLatLng, "Vị trí hiện tại")
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            Handler(Looper.getMainLooper()).postDelayed({
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }, 15_000L)

        } catch (e: SecurityException) {
            Log.e("Location", "Security exception: ${e.message}")
            Toast.makeText(this, "Không thể truy cập vị trí: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun featureCollection(featuresArray: JsonArray): String {
        return JsonObject().apply {
            addProperty("type", "FeatureCollection")
            add("features", featuresArray)
        }.toString()
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    // Lifecycle methods
    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onSaveInstanceState(@NonNull outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }
}

// Extension function
fun Double.formatPrice(): String = "%,.0f đ".format(this)