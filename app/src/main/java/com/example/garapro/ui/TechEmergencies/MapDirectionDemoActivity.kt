package com.example.garapro.ui.TechEmergencies

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.garapro.R
import com.example.garapro.data.model.emergencies.DirectionResponse
import com.example.garapro.data.model.techEmergencies.EmergencyStatus
import com.example.garapro.data.model.techEmergencies.TechnicianLocationBody
import com.example.garapro.data.remote.GoongClient
import com.example.garapro.data.remote.RetrofitInstance
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import kotlin.math.min

class MapDirectionDemoActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private lateinit var tvInstruction: TextView
    private lateinit var btnToggleNav: Button

    private lateinit var btnStartJob: Button

    private lateinit var btnCompleteJob: Button

    private lateinit var btnPickupCustomer: Button
    private lateinit var btnCall: Button

    private lateinit var btnCancelJob: Button
    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    //test gửi 1 lâần
    private var hasSentTestLocation = false


    private var currentLocation: LatLng? = null
    private var branchLocation: LatLng? = null

    private var customerLocation: LatLng? = null
    private var emergencyId: String? = null

    private lateinit var customerPhone: String



    private var destinationLatLng: LatLng? = null
    private var emergencyStatus: Int = 0

    // Route data
    private var routePoints: List<LatLng> = emptyList()
    private var steps: List<DirectionResponse.Step> = emptyList()
    private var currentStepIndex = 0
    private var currentRoutePointIndex = 0

    // Navigation state
    private var isNavigating = false
    private var hasRoute = false

    private var lastLocationForRouting: LatLng? = null
    private var lastRerouteTime = 0L

    private var lastBearing: Float = 0f

    private var lastSpeed: Float = 0f
    private val MIN_DISTANCE_TO_REROUTE = 30f      // m
    private val MIN_TIME_TO_REROUTE_MS = 8_000L  // 10s


    private val BEARING_SPEED_THRESHOLD = 1.2f      // > 1.2 m/s (~4.3km/h) mới coi là đang chạy




    private val MIN_STEP_ADVANCE_MS = 1200L

    private var lastStepAdvanceTime = 0L

    // Camera
    private var lastGpsPos: LatLng? = null

    private var lastSnappedPos: LatLng? = null
    private var hasFitRouteBoundsOnce = false
    // TTS
    private var tts: TextToSpeech? = null

    private var lastLocationSent: Location? = null

    private var lastSendLocationTime = 0L

    private val MIN_TIME_BETWEEN_LOCATION_SEND_MS = 10_000L  // 10s

    private val MIN_DISTANCE_TO_SEND_M = 20f

    private lateinit var tvDebug: TextView

    private var lastSnapDist: Float = -1f
    private var lastSnapIndex: Int = -1
    // For smoothing movement

    private var isRequestingRoute = false

    private var lastSpokenStepIndex = -1
    private lateinit var viewModel: TechEmergenciesViewModel

    private var hasArrived = false

    companion object {
        private const val PERMISSION_LOCATION = 1999
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        MapLibre.getInstance(
            this,
            getString(R.string.goong_map_key),
            WellKnownTileServer.Mapbox
        )

        setContentView(R.layout.activity_map_direction_demo)


        mapView = findViewById(R.id.mapView)
        tvInstruction = findViewById(R.id.tvInstruction)
        btnToggleNav = findViewById(R.id.btnToggleNav)

        btnStartJob = findViewById(R.id.btnStartJob)
        btnStartJob.visibility = View.VISIBLE

        btnPickupCustomer = findViewById(R.id.btnPickupCustomer)
        btnPickupCustomer.visibility = View.GONE


        btnCompleteJob = findViewById(R.id.btnCompleteJob)
        btnCompleteJob.visibility = View.GONE

        btnCall = findViewById(R.id.btnCall)
        btnCancelJob = findViewById(R.id.btnCancelJob)

        emergencyStatus = intent.getIntExtra("status", 0)
        val destLat = intent.getDoubleExtra("latitude", 0.0)
        val destLng = intent.getDoubleExtra("longitude", 0.0)
        destinationLatLng = if (destLat != 0.0 && destLng != 0.0) {
            LatLng(destLat, destLng)
        } else null

        val branchName = intent.getStringExtra("branchName") ?: ""
        val branchLat = intent.getDoubleExtra("branchLatitude", 0.0)
        val branchLng = intent.getDoubleExtra("branchLongitude", 0.0)

        viewModel = ViewModelProvider(this)[TechEmergenciesViewModel::class.java]

        emergencyId = intent.getStringExtra("emergencyId") ?: ""

        customerLocation = if (destLat != 0.0 && destLng != 0.0) LatLng(destLat, destLng) else null
        branchLocation   = if (branchLat != 0.0 && branchLng != 0.0) LatLng(branchLat, branchLng) else null


        destinationLatLng = when (EmergencyStatus.fromInt(emergencyStatus)) {
            EmergencyStatus.Towing -> branchLocation
            EmergencyStatus.Completed -> null
            else -> customerLocation
        }

        Log.d("estatus",emergencyStatus.toString())

        customerPhone = intent.getStringExtra("customerPhone") ?: ""

        isNavigating = when (EmergencyStatus.fromInt(emergencyStatus)) {
            EmergencyStatus.InProgress, EmergencyStatus.Towing -> true
            else -> false
        }

        if (isNavigating) {
            btnToggleNav.text = "Stop"
            tvInstruction.text = when (EmergencyStatus.fromInt(emergencyStatus)) {
                EmergencyStatus.Towing -> "Navigating to branch..."
                else -> "Navigating to customer's location..."
            }
        } else {
            btnToggleNav.text = "NAV"
            tvInstruction.text = "Press Start Navigation to begin guidance"
        }


        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        fusedLocation = LocationServices.getFusedLocationProviderClient(this)



        setupTTS()
        setupButton()
        checkPermission()
        updateButtonsByStatus()
    }



    private fun setupButton() {


        btnPickupCustomer.setOnClickListener {

            viewModel.updateStatus(emergencyId ?: return@setOnClickListener, EmergencyStatus.Towing.value)

            viewModel.updateResult.observe(this) { ok ->
                if (ok) {
                    Toast.makeText(this, "Pickup confirmed! Moving to garage...", Toast.LENGTH_SHORT).show()

                    hasArrived = false
                    emergencyStatus = EmergencyStatus.Towing.value
                    btnPickupCustomer.visibility = View.GONE
                    btnCancelJob.visibility = View.GONE
                    destinationLatLng = branchLocation
                    resetNavStateForNewRoute()
                    updateDestinationMarker(branchLocation)
                    updateDestinationMarker(destinationLatLng)
                    currentLocation?.let { getDirectionRoute(it) }
                }
            }
        }


        btnStartJob.setOnClickListener {
            viewModel.updateStatus(emergencyId ?: return@setOnClickListener, EmergencyStatus.InProgress.value)

            viewModel.updateResult.observe(this) { ok ->
                if (ok) {
                    Toast.makeText(this, "Status updated: InProgress", Toast.LENGTH_SHORT).show()

                    emergencyStatus = EmergencyStatus.InProgress.value
                    btnStartJob.visibility = View.GONE
                    isNavigating = true
                    btnToggleNav.text = "Stop"

                    // Route lại từ vị trí hiện tại
                    currentLocation?.let { getDirectionRoute(it) }
                } else {
                    Toast.makeText(this, "Update failed!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnCompleteJob.setOnClickListener {

            viewModel.updateStatus(emergencyId ?: return@setOnClickListener, EmergencyStatus.Completed.value)

            viewModel.updateResult.observe(this) { ok ->
                if (ok) {
                    Toast.makeText(this, "Job completed!", Toast.LENGTH_SHORT).show()

                    val data = Intent().apply {
                        putExtra("completed", true)
                    }
                    setResult(RESULT_OK, data)
                    finish()
                }
            }
        }

        btnCancelJob.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cancel Emergency")
                .setMessage("Are you sure you want to cancel this emergency?")
                .setPositiveButton("Yes") { _, _ ->
                    cancelEmergency()
                }
                .setNegativeButton("No", null)
                .show()
        }




        btnToggleNav.setOnClickListener {
            isNavigating = !isNavigating

            if (isNavigating) {
                // Start NAV
                if (destinationLatLng == null) {
                    Toast.makeText(this, "No customer location available", Toast.LENGTH_SHORT).show()
                    isNavigating = false
                    btnToggleNav.text = "NAV"
                    return@setOnClickListener
                }

                btnToggleNav.text = "Stop"

                tvInstruction.text = when (EmergencyStatus.fromInt(emergencyStatus)) {
                    EmergencyStatus.Towing -> "Calculating route to branch..."
                    else -> "Calculating route to customer location..."
                }

                // Reset route tracking
                hasRoute = false
                lastLocationForRouting = null
                lastRerouteTime = 0L

                // If GPS is available, calculate the route immediately
                currentLocation?.let { loc ->
                    hasRoute = true
                    lastLocationForRouting = loc
                    lastRerouteTime = System.currentTimeMillis()
                    getDirectionRoute(loc)
                }

            } else {
                // Stop NAV
                btnToggleNav.text = "NAV"
                tvInstruction.text = "Navigation stopped"
                tts?.speak("Navigation stopped", TextToSpeech.QUEUE_FLUSH, null, "NAV_STOP")
            }
        }

        btnCall.setOnClickListener {
            makePhoneCall(customerPhone)
        }

    }

    private fun makePhoneCall(phone: String) {
        if (phone.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$phone")
            startActivity(intent)
        }
    }


    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("vi", "VN")
            }
        }
    }



    private fun cancelEmergency() {
        viewModel.updateStatus(
            emergencyId ?: return,
            EmergencyStatus.Canceled.value
        )

        viewModel.updateResult.observe(this) { ok ->
            if (ok) {
                Toast.makeText(this, "Emergency cancelled", Toast.LENGTH_SHORT).show()

                val resultIntent = Intent().apply {
                    putExtra("cancelled", true)
                    putExtra("emergencyId", emergencyId)
                }

                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Cancel failed", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun dynamicStepThresholdMeters(speedMps: Float): Float {
        return when {
            speedMps >= 8f -> 45f
            speedMps >= 4f -> 30f
            else -> 18f
        }
    }

    private fun checkNextStepByDistance(pos: LatLng) {
        if (!isNavigating) return
        if (currentStepIndex >= steps.size) return

        val end = steps[currentStepIndex].endLocation ?: return
        val endPos = LatLng(end.lat, end.lng)

        val d = distanceBetween(pos, endPos)
        val now = System.currentTimeMillis()
        val thresh = dynamicStepThresholdMeters(lastSpeed)

        if (d <= thresh && now - lastStepAdvanceTime >= MIN_STEP_ADVANCE_MS) {
            currentStepIndex = (currentStepIndex + 1).coerceAtMost(steps.size)
            lastStepAdvanceTime = now
            updateInstruction()
        }
    }


    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_LOCATION
            )
        } else {
            // Có quyền rồi  kiểm tra GPS
            if (ensureLocationEnabled()) {
                startLocationUpdates()
            }
        }
    }


    private fun maybeSendLocationToServer(loc: Location) {
        // Nếu không có emergencyId thì không cần gửi
        val emergencyIdLocal = emergencyId
        if (emergencyIdLocal.isNullOrEmpty()) return

        val now = System.currentTimeMillis()
        val last = lastLocationSent

        // Nếu đã gửi trước đó, check khoảng cách & thời gian
        if (last != null) {
            val dist = last.distanceTo(loc)  // mét
            if (dist < MIN_DISTANCE_TO_SEND_M &&
                now - lastSendLocationTime < MIN_TIME_BETWEEN_LOCATION_SEND_MS
            ) {
                // Chưa đi xa và chưa đủ thời gian -> không gửi
                return
            }
        } else {
            // Lần đầu: vẫn hạn chế spam nếu vừa mới gọi
            if (now - lastSendLocationTime < MIN_TIME_BETWEEN_LOCATION_SEND_MS) return
        }

        lastLocationSent = Location(loc)
        lastSendLocationTime = now

        sendLocationToServer(emergencyIdLocal, loc)
    }

    private fun sendLocationToServer(emergencyId: String, loc: Location) {
        val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6 else null  // m/s -> km/h
        val bearing = if (loc.hasBearing()) loc.bearing.toDouble() else null

        val body = TechnicianLocationBody(
            emergencyRequestId = emergencyId,
            latitude = loc.latitude,
            longitude = loc.longitude,
            speedKmh = speedKmh,
            bearing = bearing,
            recomputeRoute = true
        )

        // Chạy âm thầm trong IO thread, không đụng UI
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.techEmergencyService.updateLocation(body)
                if (response.isSuccessful) {
                    Log.d("TechLocation", "Send location OK: " +
                            "lat=${loc.latitude}, lng=${loc.longitude}, speedKmh=$speedKmh, bearing=$bearing")
                } else {
                    Log.e(
                        "TechLocation",
                        "Send location FAILED: code=${response.code()} body=${response.errorBody()?.string()}"
                    )
                }
            } catch (e: Exception) {
                Log.e("TechLocation", "Exception when sending technician location", e)
            }
        }
    }


    private fun ensureLocationEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val enabled =
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!enabled) {
            AlertDialog.Builder(this)
                .setTitle("Enable Location")
                .setMessage("The app needs location services to provide navigation. Please enable GPS in Settings.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        return enabled
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_LOCATION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            if (ensureLocationEnabled()) {
                startLocationUpdates()
            }
        } else {
            Toast.makeText(this, "Location permission is required for navigation", Toast.LENGTH_SHORT).show()

        }
    }

    override fun onMapReady(map: MapLibreMap) {
        this.map = map

        val styleUrl =
            "https://tiles.goong.io/assets/goong_map_web.json?api_key=${getString(R.string.goong_map_key)}"

        map.setStyle(styleUrl) { style ->
            initRouteLayer(style)
            initCarMarker(style)
            initDestinationMarker(style)

            val target = destinationLatLng ?: LatLng(15.75, 108.33)
            // Tạm zoom tới FPT Đà Nẵng
            val camera = CameraPosition.Builder()
                .target(target)
                .zoom(13.0)
                .build()
            map.cameraPosition = camera
        }
    }

    private fun initRouteLayer(style: Style) {
        style.addSource(GeoJsonSource("route-source", FeatureCollection.fromFeatures(arrayOf())))
        style.addLayer(
            LineLayer("route-layer", "route-source").withProperties(
                PropertyFactory.lineWidth(7f),
                PropertyFactory.lineColor("#ff0000"),
                PropertyFactory.lineJoin("round"),
                PropertyFactory.lineCap("round")
            )
        )
    }

    private fun initCarMarker(style: Style) {
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_car)
        if (drawable == null) {
            Log.e("Map", "ic_car drawable is null")
            return
        }

        // Tạo bitmap to hơn để icon trông "đậm" và nổi
        val width = (drawable.intrinsicWidth.takeIf { it > 0 } ?: 64) * 2
        val height = (drawable.intrinsicHeight.takeIf { it > 0 } ?: 64) * 2

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Vẽ nền tròn mờ phía sau cho nổi hơn route (tuỳ chọn, có thể bỏ)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 200
        }
        val radius = min(width, height) / 2f
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)


        // Vẽ icon xe phủ lên trên
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        style.addImage("car-icon", bitmap)

        val target = destinationLatLng ?: LatLng(15.75, 108.33)

        // source xe
        style.addSource(
            GeoJsonSource(
                "car-source",
                Point.fromLngLat(target.longitude, target.latitude)
            )
        )

        style.addLayer(
            SymbolLayer("car-layer", "car-source").withProperties(
                PropertyFactory.iconImage("car-icon"),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAllowOverlap(true),

                // Xoay icon theo map (hướng Bắc là 0 độ)
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                // Tâm xoay ở giữa icon
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                // Góc ban đầu
                PropertyFactory.iconRotate(0.0f)
            )
        )
    }

    private fun initDestinationMarker(style: Style) {
        // Dùng icon từ drawable
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_location)
        if (drawable == null) {
            Log.e("Map", "ic_destination_pin is null")
            return
        }

        // Tạo bitmap từ drawable
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 64
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 64

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        // Add icon vào style
        style.addImage("dest-icon", bitmap)

        // Vị trí ban đầu của destination
        val target = destinationLatLng ?: LatLng(15.75, 108.33)

        // source
        style.addSource(
            GeoJsonSource(
                "dest-source",
                Point.fromLngLat(target.longitude, target.latitude)
            )
        )

        // layer
        style.addLayer(
            SymbolLayer("dest-layer", "dest-source").withProperties(
                PropertyFactory.iconImage("dest-icon"),
                PropertyFactory.iconSize(2.0f),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
            )
        )
    }


    private fun updateDestinationMarker(pos: LatLng?) {
        if (pos == null) return
        map?.getStyle()
            ?.getSourceAs<GeoJsonSource>("dest-source")
            ?.setGeoJson(Point.fromLngLat(pos.longitude, pos.latitude))
    }

    private fun updateButtonsByStatus() {
        Log.d("estatus",EmergencyStatus.fromInt(emergencyStatus).toString())
        when (EmergencyStatus.fromInt(emergencyStatus)) {


            EmergencyStatus.Assigned-> {
                btnStartJob.visibility = View.VISIBLE
                btnToggleNav.visibility = View.VISIBLE
                btnPickupCustomer.visibility = View.GONE
                btnCompleteJob.visibility = View.GONE
            }

            EmergencyStatus.InProgress -> {
                btnStartJob.visibility = View.GONE
                btnToggleNav.visibility = View.VISIBLE
                btnPickupCustomer.visibility = View.GONE   // sẽ hiện khi đến nơi
                btnCompleteJob.visibility = View.GONE
            }

            EmergencyStatus.Towing -> {
                btnStartJob.visibility = View.GONE
                btnToggleNav.visibility = View.VISIBLE
                btnPickupCustomer.visibility = View.GONE
                btnCompleteJob.visibility = View.GONE    // sẽ hiện khi đến branch
            }

            EmergencyStatus.Completed -> {
                btnStartJob.visibility = View.GONE
                btnToggleNav.visibility = View.GONE
                btnPickupCustomer.visibility = View.GONE
                btnCompleteJob.visibility = View.GONE

                Toast.makeText(this, "Job already completed", Toast.LENGTH_SHORT).show()
                finish()
            }

            else -> {
                btnStartJob.visibility = View.GONE
                btnToggleNav.visibility = View.VISIBLE
                btnPickupCustomer.visibility = View.GONE
                btnCompleteJob.visibility = View.GONE
            }
        }
    }



    private fun getDirectionRoute(origin: LatLng) {
        val dest = destinationLatLng ?: return

        // đang gọi API rồi thì không gọi thêm
        if (isRequestingRoute) return
        isRequestingRoute = true

        val originStr = "${origin.latitude},${origin.longitude}"
        val destStr = "${dest.latitude},${dest.longitude}"

        tvInstruction.text = "Calculating route to destination..."


        GoongClient.getApiService().getDirection(
            originStr, destStr, "car", getString(R.string.goong_api_key)
        )?.enqueue(object : Callback<DirectionResponse?> {
            override fun onResponse(
                call: Call<DirectionResponse?>,
                response: Response<DirectionResponse?>
            ) {
                isRequestingRoute = false

                if (!response.isSuccessful || response.body() == null) {
                    Toast.makeText(
                        this@MapDirectionDemoActivity,
                        "Failed to get direction",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }


                val direction = response.body()!!
                val route = direction.routes?.firstOrNull()
                val poly = route?.overviewPolyline?.points ?: return

                val leg = route.legs?.firstOrNull()
                steps = leg?.steps?.filterNotNull() ?: emptyList()

                Log.d("steps",steps.toString())
                currentStepIndex = 0
                currentRoutePointIndex = 0
                hasRoute = true

                // VẼ POLYLINE, CHÈN ORIGIN Ở ĐẦU
                drawPolyline(poly, origin)

                if (isNavigating) {
                    updateInstruction()
                } else {
                    tvInstruction.text = "Route is ready. Tap Start Navigation to begin guidance."
                }
            }

            override fun onFailure(call: Call<DirectionResponse?>, t: Throwable) {
                isRequestingRoute = false
                Toast.makeText(
                    this@MapDirectionDemoActivity,
                    "Error: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }




    private fun speak(text: String) {
        if (isNavigating) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NAV_TTS")
        }
    }

    private fun plainTextFromHtml(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun updateInstruction() {
        if (currentStepIndex >= steps.size) {
            tvInstruction.text = "Arrived at destination"
            if (lastSpokenStepIndex != currentStepIndex) {
                speak("Bạn đã đến nơi")
                lastSpokenStepIndex = currentStepIndex
            }
            return
        }

        val step = steps[currentStepIndex]
        val instrText = plainTextFromHtml(step.instructions)
        val distanceText = step.distance?.text ?: ""
        val display = "$distanceText, $instrText"

        tvInstruction.text = display
        if (lastSpokenStepIndex != currentStepIndex) {
            speak(display)
            lastSpokenStepIndex = currentStepIndex
        }
    }

    // decode polyline
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }

        return poly
    }

    // THAY HÀM CŨ
    private fun drawPolyline(encoded: String, origin: LatLng?) {
        if (encoded.isEmpty()) return

        // decode full route từ server
        val decoded = decodePolyline(encoded).toMutableList()

        // chèn origin (GPS) vào đầu poly để route bắt đầu ngay dưới xe
        if (origin != null) {
            decoded.add(0, LatLng(origin.latitude, origin.longitude))
        }

        routePoints = decoded
        currentRoutePointIndex = 0

        if (routePoints.isEmpty()) return

        val points = routePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
        val line = LineString.fromLngLats(points)
        val feature = Feature.fromGeometry(line)

        map?.getStyle()?.getSourceAs<GeoJsonSource>("route-source")
            ?.setGeoJson(FeatureCollection.fromFeature(feature))

        // fit lần đầu để user thấy toàn tuyến
        if (!hasFitRouteBoundsOnce) {
            val bounds = LatLngBounds.Builder()
            routePoints.forEach { bounds.include(it) }
            map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
            hasFitRouteBoundsOnce = true
        }
    }




    private fun updateRemainingRoute() {
        if (routePoints.isEmpty()) return
        val snapPos = lastSnappedPos ?: return

        // luôn vẽ poly từ vị trí snap hiện tại trở đi
        val startIndex = currentRoutePointIndex

        val remaining = mutableListOf<LatLng>()
        // điểm đầu chính là vị trí snap hiện tại (ngay dưới icon)
        remaining.add(snapPos)

        for (i in (startIndex + 1) until routePoints.size) {
            remaining.add(routePoints[i])
        }

        if (remaining.size < 2) return

        val points = remaining.map { Point.fromLngLat(it.longitude, it.latitude) }
        val line = LineString.fromLngLats(points)
        val feature = Feature.fromGeometry(line)

        map?.getStyle()?.getSourceAs<GeoJsonSource>("route-source")
            ?.setGeoJson(FeatureCollection.fromFeature(feature))
    }

    private fun distanceBetween(a: LatLng, b: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            a.latitude, a.longitude,
            b.latitude, b.longitude,
            results
        )
        return results[0]
    }

    private fun closestPointOnRouteWithIndex(pos: LatLng): Triple<LatLng, Float, Int>? {
        if (routePoints.isEmpty()) return null

        var minDist = Float.MAX_VALUE
        var closestIndex = -1

        routePoints.forEachIndexed { index, p ->
            val d = distanceBetween(pos, p)
            if (d < minDist) {
                minDist = d
                closestIndex = index
            }
        }

        if (closestIndex == -1) return null
        return Triple(routePoints[closestIndex], minDist, closestIndex)
    }

    // Trả về Triple(điểm gần nhất trên route, khoảng cách, index)
    private fun snapToRoute(
        gpsPos: LatLng,
        route: List<LatLng>,
        currentIndex: Int,          // currentRoutePointIndex hiện tại
        maxSnapDistMeters: Float = 30f,
        searchWindowBack: Int = 10, // cho phép nhìn lùi lại tối đa 10 points
        searchWindowForward: Int = 40 // cho phép nhìn tới trước 40 points
    ): SnapResult? {
        if (route.size < 2) return null

        val size = route.size

        // Xác định khoảng index để search quanh vị trí hiện tại
        val startIndex = maxOf(0, currentIndex - searchWindowBack)
        val endIndex = minOf(size - 2, currentIndex + searchWindowForward) // -2 vì dùng i & i+1

        var bestDist = Float.MAX_VALUE
        var bestSnapLatLng: LatLng? = null
        var bestIndex = -1

        // Đổi LatLng -> "toạ độ phẳng" tương đối để tính projection gần đúng
        // Đây là phép xấp xỉ OK trong phạm vi vài km
        fun toXY(latLng: LatLng, refLat: Double): Pair<Double, Double> {
            val latRad = Math.toRadians(refLat)
            val x = (latLng.longitude) * Math.cos(latRad)
            val y = latLng.latitude
            return Pair(x, y)
        }

        // Dùng gpsPos làm mốc
        val (gpsX, gpsY) = toXY(gpsPos, gpsPos.latitude)

        for (i in startIndex..endIndex) {
            val p1 = route[i]
            val p2 = route[i + 1]

            val (x1, y1) = toXY(p1, gpsPos.latitude)
            val (x2, y2) = toXY(p2, gpsPos.latitude)

            val vx = x2 - x1
            val vy = y2 - y1
            val segLen2 = vx * vx + vy * vy
            if (segLen2 == 0.0) continue

            // Vector từ p1 đến gps
            val wx = gpsX - x1
            val wy = gpsY - y1

            // t = độ dài chiếu wx,wy lên đoạn [0,1]
            var t = (wx * vx + wy * vy) / segLen2
            t = t.coerceIn(0.0, 1.0)

            // Điểm projected trong hệ toạ độ phẳng
            val projX = x1 + t * vx
            val projY = y1 + t * vy

            // Chuyển ngược về LatLng (x ≈ lon*cos(lat), y ≈ lat)
            val projLat = projY
            val projLon = projX / Math.cos(Math.toRadians(gpsPos.latitude))

            val projLatLng = LatLng(projLat, projLon)

            // Tính khoảng cách thật giữa GPS  điểm snap
            val distArr = FloatArray(1)
            Location.distanceBetween(
                gpsPos.latitude, gpsPos.longitude,
                projLatLng.latitude, projLatLng.longitude,
                distArr
            )
            val dist = distArr[0]

            if (dist < bestDist) {
                bestDist = dist
                bestSnapLatLng = projLatLng
                // lấy index segment bắt đầu (i), không +1 nữa
                bestIndex = i
            }
        }

        // Nếu vẫn không tìm được hoặc quá xa route -> không snap
        if (bestSnapLatLng == null || bestDist > maxSnapDistMeters) {
            return null
        }

        return SnapResult(
            snappedPos = bestSnapLatLng,
            distanceToRoute = bestDist,
            routeIndex = bestIndex
        )
    }

    private fun updateCarMarker(pos: LatLng) {
        map?.getStyle()?.let { style ->
            style.getSourceAs<GeoJsonSource>("car-source")
                ?.setGeoJson(Point.fromLngLat(pos.longitude, pos.latitude))

            style.getLayerAs<SymbolLayer>("car-layer")
                ?.setProperties(
                    PropertyFactory.iconRotate(lastBearing) // xoay icon theo hướng xe
                )
        }
    }



    
    private fun updateCamera(pos: LatLng) {
        val currentBearing = map?.cameraPosition?.bearing ?: 0.0

        // nếu xe đang chạy đủ nhanh thì xoay theo lastBearing, còn không thì giữ nguyên
        val bearingForCamera =
            if (lastSpeed > BEARING_SPEED_THRESHOLD) lastBearing.toDouble()
            else currentBearing

        val camera = CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(pos)
                .zoom(17.0)
                .tilt(45.0)
                .bearing(bearingForCamera)
                .build()
        )
        map?.animateCamera(camera, 1000)
    }




    private fun checkNextStep(routeIndex: Int) {
        if (!isNavigating) return
        if (currentStepIndex >= steps.size) return
        if (routePoints.isEmpty()) return


        if (routeIndex <= currentRoutePointIndex + 2) return

        currentStepIndex = (currentStepIndex + 1).coerceAtMost(steps.size - 1)
        currentRoutePointIndex = routeIndex

        updateInstruction()
        updateRemainingRoute()
    }


    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            500L // muốn nhanh hơn thì xuống 300L
        )
            .setMinUpdateIntervalMillis(300L)
            .setMaxUpdateDelayMillis(0L) // KHÔNG batch location -> realtime nhất có thể
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return

                // ====== VỊ TRÍ GPS THÔ ======
                val rawPos = LatLng(loc.latitude, loc.longitude)
                currentLocation = rawPos

                // ====== SPEED & BEARING ======
                val speed = if (loc.hasSpeed()) loc.speed else 0f // m/s
                lastSpeed = speed

                if (speed > BEARING_SPEED_THRESHOLD && loc.hasBearing()) {
                    val newBearing = loc.bearing
                    lastBearing = smoothBearing(lastBearing, newBearing)
                }

                // ====== AUTO ROUTE LẦN ĐẦU NẾU ĐANG NAV ======
                if (!hasRoute && map != null && isNavigating) {
                    hasRoute = true
                    lastLocationForRouting = rawPos
                    lastRerouteTime = System.currentTimeMillis()
                    getDirectionRoute(rawPos)
                }

                var displayPos = rawPos

                // ====== SNAP VÀO ROUTE + REROUTE KHI LỆCH XA ======
                if (hasRoute && routePoints.isNotEmpty()) {

                    val snapResult = snapToRoute(
                        gpsPos = rawPos,
                        route = routePoints,
                        currentIndex = currentRoutePointIndex,
                        maxSnapDistMeters = 25f
                    )

                    if (snapResult != null) {
                        val snapPoint = snapResult.snappedPos
                        val snapIndex = snapResult.routeIndex

                        // icon bám vào route
                        displayPos = snapPoint
                        lastSnappedPos = snapPoint

                        lastSnapDist = snapResult.distanceToRoute
                        lastSnapIndex = snapIndex
                        val now = System.currentTimeMillis()

//                        if (isNavigating) {
//                            // kiểm tra xem có nên nhảy step không
//                            checkNextStep(snapIndex)
//                        }
                        when {
                            // ĐI TIẾN HOẶC ĐỨNG NGAY TRÊN CÙNG 1 SEGMENT
                            snapIndex >= currentRoutePointIndex -> {
                                currentRoutePointIndex = snapIndex
                                updateRemainingRoute()           // LUÔN cắt route bắt đầu từ snap mới nhất
                                lastLocationForRouting = rawPos
                                lastRerouteTime = now
                            }

                            // ĐI LÙI / QUAY ĐẦU > 5 POINT → REROUTE
                            isNavigating && currentRoutePointIndex - snapIndex > 5 -> {
                                hasRoute = false
                                lastLocationForRouting = rawPos
                                lastRerouteTime = now
                                getDirectionRoute(rawPos)
                                Log.d("Nav", "Reroute: moving backwards on route")
                            }

                            else -> {
                                // vẫn on-route, khác biệt nhỏ -> không làm gì
                            }
                        }

                    } else if (isNavigating) {
                        // Không snap được (quá xa route) -> dùng GPS thô + cân nhắc reroute
                        val moved = lastLocationForRouting
                            ?.let { distanceBetween(rawPos, it) }
                            ?: Float.MAX_VALUE

                        val now = System.currentTimeMillis()
                        if (moved > MIN_DISTANCE_TO_REROUTE &&
                            now - lastRerouteTime > MIN_TIME_TO_REROUTE_MS
                        ) {
                            lastLocationForRouting = rawPos
                            lastRerouteTime = now
                            getDirectionRoute(rawPos)
                            Log.d("Nav", "Reroute: off route, moved=$moved")
                        }
                    }
                }
                if (isNavigating && steps.isNotEmpty()) {
                    checkNextStepByDistance(displayPos)
                }


                destinationLatLng?.let { dest ->
//                    val dRaw = distanceBetween(rawPos, dest)
                    val d = distanceBetween(displayPos, dest)


                    if ((!hasArrived  && d < 50f)) {
                        hasArrived = true
                        when (EmergencyStatus.fromInt(emergencyStatus)) {
                            EmergencyStatus.InProgress -> {
                                btnPickupCustomer.visibility = View.VISIBLE
                                btnCancelJob.visibility = View.VISIBLE
                                tvInstruction.text = "You have arrived. Tap to pick up the customer."
                            }

                            EmergencyStatus.Towing -> {
                                btnCompleteJob.visibility = View.VISIBLE
                                btnCancelJob.visibility = View.GONE
                                tvInstruction.text =
                                    "You have arrived at the branch. Tap to complete the job."
                            }

                            else -> {}
                        }
                    }
                }

                // ====== DEBUG HUD ======
//                val rawLat = rawPos.latitude
//                val rawLng = rawPos.longitude
//                val snapLat = lastSnappedPos?.latitude
//                val snapLng = lastSnappedPos?.longitude
//
//                val debugText = buildString {
//                    appendLine("GPS: %.6f, %.6f".format(rawLat, rawLng))
//                    appendLine("Speed: %.1f m/s | hasRoute=$hasRoute".format(lastSpeed))
//                    appendLine("snapDist=%.1f m idx=$lastSnapIndex currIdx=$currentRoutePointIndex".format(lastSnapDist))
//                    if (snapLat != null && snapLng != null) {
//                        appendLine("Snap: %.6f, %.6f".format(snapLat, snapLng))
//                    }
//                }
//                tvDebug.text = debugText
                updateCarMarker(displayPos)

                if (isNavigating) {
                    updateCamera(displayPos)

                }

                lastGpsPos = rawPos

                maybeSendLocationToServer(loc)

//                if (!hasSentTestLocation) {
//                    hasSentTestLocation = true
//                    maybeSendLocationToServer(loc)
//                }
            }
        }

        fusedLocation.requestLocationUpdates(
            request, locationCallback, Looper.getMainLooper()
        )
    }


    private fun resetNavStateForNewRoute() {
        hasRoute = false
        routePoints = emptyList()
        steps = emptyList()
        currentStepIndex = 0
        currentRoutePointIndex = 0
        lastStepAdvanceTime = 0L
        lastSpokenStepIndex = -1
        hasFitRouteBoundsOnce = false
        lastSnappedPos = null
        lastSnapIndex = -1
        lastSnapDist = -1f
        hasArrived = false
    }


    private fun smoothBearing(old: Float, new: Float): Float {
        // tính chênh lệch có wrap 360 độ
        var diff = (new - old + 540f) % 360f - 180f
        // chỉ chỉnh một phần nhỏ của diff để xoay mượt
        val factor = 1.0f  // 0.0–1.0, càng nhỏ càng mượt
        val result = old + diff * factor
        return (result + 360f) % 360f
    }
    // Lifecycle
    override fun onStart() {
        super.onStart()
        mapView.onStart()

//        // 1) Tắt service
//        stopBgTrackingService()
//
//        // 2) Bật lại location updates cho Activity (vẽ map)
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
//            == PackageManager.PERMISSION_GRANTED && ensureLocationEnabled()
//        ) {
//            startLocationUpdates()
//        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        // Nếu đang NAV mà user tắt GPS giữa chừng → nhắc bật lại
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            ensureLocationEnabled()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()

        if(isChangingConfigurations) return

//        // 1) Tắt location updates của Activity để không chạy song song
//        if (::locationCallback.isInitialized) {
//            try { fusedLocation.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
//        }

        // 2) Bật service background
//        startBgTrackingServiceIfNeeded()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()

        fusedLocation.removeLocationUpdates(locationCallback)
//        if (::locationCallback.isInitialized) {
//            try {
//                fusedLocation.removeLocationUpdates(locationCallback)
//            } catch (_: Exception) {
//            }
//        }

        tts?.stop()
        tts?.shutdown()
    }
}


data class SnapResult(
    val snappedPos: LatLng,
    val distanceToRoute: Float,
    val routeIndex: Int  // index của điểm trên route gần nhất (vertex phía trước)
)
