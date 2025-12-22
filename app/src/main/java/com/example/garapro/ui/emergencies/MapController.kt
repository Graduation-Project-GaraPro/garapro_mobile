package com.example.garapro.ui.emergencies

import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.animation.LinearInterpolator
import com.example.garapro.R
import com.google.gson.JsonObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

class MapController(private val map: MapLibreMap, private val context: android.content.Context) {

    private val CUSTOMER_SOURCE_ID = "customer-source"
    private val CUSTOMER_LAYER_ID = "customer-layer"
    private val TECHNICIAN_SOURCE_ID = "technician-source"
    private val TECHNICIAN_LAYER_ID = "technician-layer"
    private val ROUTE_SOURCE_ID = "route-source"
    private val ROUTE_LAYER_ID = "route-layer"
    
    // Animation state
    private var currentTechLat: Double = 0.0
    private var currentTechLng: Double = 0.0
    private var animator: ValueAnimator? = null

    fun initialize(style: Style) {
        
        // Load icons
        if (style.getImage("icon-car") == null) {
            val bitmap = bitmapFromDrawableRes(context, R.drawable.ic_car_marker)
            if (bitmap != null) style.addImage("icon-car", bitmap)
        }
        if (style.getImage("icon-customer") == null) {
            val bitmap = bitmapFromDrawableRes(context, R.drawable.ic_customer_marker)
            if (bitmap != null) style.addImage("icon-customer", bitmap)
        }
        if (style.getImage("icon-garage") == null) {
            val bitmap = bitmapFromDrawableRes(context, R.drawable.ic_garage_marker)
            if (bitmap != null) style.addImage("icon-garage", bitmap)
        }

        // Customer Source & Layer
        if (style.getSource(CUSTOMER_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(CUSTOMER_SOURCE_ID))
            
            if (style.getImage("icon-customer") != null && style.getImage("icon-garage") != null) {
                style.addLayer(
                    SymbolLayer(CUSTOMER_LAYER_ID, CUSTOMER_SOURCE_ID).withProperties(
                        PropertyFactory.iconImage(Expression.switchCase(
                             Expression.get("isGarage"), Expression.literal("icon-garage"),
                             Expression.literal("icon-customer")
                        )),
                        PropertyFactory.iconSize(1.5f),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
                    )
                )
            } else {
                style.addLayer(
                    CircleLayer(CUSTOMER_LAYER_ID, CUSTOMER_SOURCE_ID).withProperties(
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleColor(Color.RED),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor(Color.WHITE)
                    )
                )
            }
        }

        // Technician Source & Layer - CHANGED to SymbolLayer for Car Icon
        if (style.getSource(TECHNICIAN_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(TECHNICIAN_SOURCE_ID))
            
            // Check if we successfully loaded the image, else fallback to Circle
            if (style.getImage("icon-car") != null) {
                 style.addLayer(
                    SymbolLayer(TECHNICIAN_LAYER_ID, TECHNICIAN_SOURCE_ID).withProperties(
                        PropertyFactory.iconImage("icon-car"),
                        PropertyFactory.iconSize(1.0f),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconRotate(Expression.get("bearing")),
                        PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER)
                    )
                )
            } else {
                // Fallback to blue circle if no icon
                style.addLayer(
                    CircleLayer(TECHNICIAN_LAYER_ID, TECHNICIAN_SOURCE_ID).withProperties(
                        PropertyFactory.circleRadius(10f),
                        PropertyFactory.circleColor(Color.BLUE),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor(Color.WHITE)
                    )
                )
            }
        }

        // Route Source & Layer
        if (style.getSource(ROUTE_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
            style.addLayer(
                LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                    PropertyFactory.lineWidth(4f), // Neater width
                    PropertyFactory.lineColor(Color.RED), // Red color
                    PropertyFactory.lineOpacity(0.8f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
        }
    }

    fun updateCustomerLocation(lat: Double, lng: Double, isGarage: Boolean = false) {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(CUSTOMER_SOURCE_ID)
        
        val props = JsonObject().apply {
             addProperty("isGarage", isGarage)
        }
        
        source?.setGeoJson(com.example.garapro.data.utils.FeatureUtils.createPoint(lat, lng, props))
    }

    fun setCustomerVisibility(visible: Boolean) {
        val style = map.style ?: return
        val layer = style.getLayer(CUSTOMER_LAYER_ID)
        layer?.setProperties(PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE))
    }

    fun updateTechnicianLocation(targetLat: Double, targetLng: Double) {
        android.util.Log.d("TechLocationDebug", "MapController: Updating technician location to $targetLat, $targetLng")
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(TECHNICIAN_SOURCE_ID) ?: return

        // If it's the first update, just set it
        if (currentTechLat == 0.0 && currentTechLng == 0.0) {
            currentTechLat = targetLat
            currentTechLng = targetLng
            source.setGeoJson(createPointFeature(targetLat, targetLng))
            return
        }

        // Calculate bearing for rotation
        val bearing = calculateBearing(currentTechLat, currentTechLng, targetLat, targetLng)

        // Cancel previous animation
        animator?.cancel()

        val startLat = currentTechLat
        val startLng = currentTechLng

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000 // 2 seconds animation
            interpolator = LinearInterpolator()
            addUpdateListener { valueAnimator ->
                val fraction = valueAnimator.animatedFraction
                val lat = startLat + (targetLat - startLat) * fraction
                val lng = startLng + (targetLng - startLng) * fraction
                
                // Update current pos
                currentTechLat = lat
                currentTechLng = lng
                
                source.setGeoJson(createPointFeature(lat, lng, bearing))
            }
            start()
        }
    }

    private fun calculateBearing(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Float {
        val lat1 = Math.toRadians(startLat)
        val lat2 = Math.toRadians(endLat)
        val deltaLng = Math.toRadians(endLng - startLng)

        val y = Math.sin(deltaLng) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLng)
        
        val bearing = Math.toDegrees(Math.atan2(y, x)).toFloat()
        return (bearing + 360) % 360
    }

    private fun createPointFeature(lat: Double, lng: Double, bearing: Float = 0f): String {
        val props = JsonObject().apply {
            addProperty("bearing", bearing)
        }
        return com.example.garapro.data.utils.FeatureUtils.createPoint(lat, lng, props)
    }

    fun drawRoute(geoJson: String?) {
        android.util.Log.d("RouteDebug", "MapController: Drawing route. GeoJson length: ${geoJson?.length ?: 0}")
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
        if (geoJson == null) {
            source?.setGeoJson("""{"type": "FeatureCollection", "features": []}""")
        } else {
            source?.setGeoJson(geoJson)
        }
    }

    fun clearRoute() {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
        // Set empty feature collection
        val empty = JsonObject().apply {
            addProperty("type", "FeatureCollection")
            add("features", com.google.gson.JsonArray())
        }
        source?.setGeoJson(empty.toString())
    }

    fun animateCamera(lat: Double, lng: Double, zoom: Double? = null) {
        val position = CameraPosition.Builder()
            .target(LatLng(lat, lng))
        if (zoom != null) {
            position.zoom(zoom)
        }
        map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(position.build()), 1000)
    }

    private fun bitmapFromDrawableRes(context: android.content.Context, @androidx.annotation.DrawableRes resourceId: Int): android.graphics.Bitmap? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, resourceId)
        if (drawable is android.graphics.drawable.BitmapDrawable) {
            return drawable.bitmap
        }
        if (drawable != null) {
            val bitmap = android.graphics.Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }
        return null
    }
}
