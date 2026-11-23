package ke.floodguard.app.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ke.floodguard.app.ui.theme.FgColors
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.any
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.exponential
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineSortKey
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.net.URI

/** Free vector street map (OpenStreetMap data), no API key. Swap for any MapLibre style URL. */
const val BASEMAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

private val NAIROBI = LatLng(-1.2921, 36.8219)
private val NAIROBI_BOUNDS = LatLngBounds.Builder()
    .include(LatLng(-1.48, 36.62))
    .include(LatLng(-1.13, 37.08))
    .build()

private const val ROADS_SOURCE = "fg-roads"
const val ROADS_LAYER = "fg-roads-line"
private const val PIN_SOURCE = "fg-pin"
private const val PIN_LAYER = "fg-pin-circle"

/** Imperative handle for camera moves and the location dot, which do not fit Compose state well. */
class MapController {
    internal var map: MapLibreMap? = null
    internal var style: Style? = null

    fun flyTo(latitude: Double, longitude: Double, zoom: Double = 15.0) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom), 900)
    }

    fun showAllNairobi() {
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(NAIROBI_BOUNDS, 40), 900)
    }

    /** Shows the blue location dot. Call only after location permission is granted. */
    @SuppressLint("MissingPermission")
    fun enableLocationDot(context: Context) {
        val map = map ?: return
        val style = style ?: return
        val component = map.locationComponent
        if (!component.isLocationComponentActivated) {
            component.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style).useDefaultLocationEngine(true).build()
            )
        }
        component.isLocationComponentEnabled = true
        component.renderMode = RenderMode.NORMAL
    }
}

private fun tierRank(code: String?) = when (code) { "H" -> 3; "M" -> 2; "L" -> 1; else -> 0 }

/**
 * The flood map: a street basemap with every road segment drawn in its susceptibility colour.
 * Red = High, yellow = Medium, green = Low, grey = no model data. High is drawn on top.
 */
@Composable
fun FloodMap(
    controller: MapController,
    roads: RoadMap?,
    visibleTiers: Set<String>,
    pin: Pair<Double, Double>?,
    onTap: (latitude: Double, longitude: Double, roadName: String?, tierCode: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tapHandler by rememberUpdatedState(onTap)
    var style by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                controller.map = map
                map.cameraPosition = CameraPosition.Builder().target(NAIROBI).zoom(11.2).build()
                map.setMinZoomPreference(9.5)
                map.setLatLngBoundsForCameraTarget(NAIROBI_BOUNDS)
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
                map.setStyle(Style.Builder().fromUri(BASEMAP_STYLE)) { loaded ->
                    controller.style = loaded
                    style = loaded
                }
                map.addOnMapClickListener { latLng ->
                    val point = map.projection.toScreenLocation(latLng)
                    val r = 14f * resources.displayMetrics.density
                    val hits = map.queryRenderedFeatures(RectF(point.x - r, point.y - r, point.x + r, point.y + r), ROADS_LAYER)
                    val best = hits.maxByOrNull { tierRank(it.getStringPropertyOrNull("t")) }
                    tapHandler(latLng.latitude, latLng.longitude, best?.getStringPropertyOrNull("n"),
                        best?.getStringPropertyOrNull("t"))
                    true
                }
            }
        }
    }

    // Forward the activity lifecycle to the MapView; destroy it when this composable leaves.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.map = null
            controller.style = null
            mapView.onDestroy()
        }
    }

    LaunchedEffect(style, roads) {
        val s = style ?: return@LaunchedEffect
        val uri = URI(roads?.uri ?: return@LaunchedEffect)
        val existing = s.getSourceAs<GeoJsonSource>(ROADS_SOURCE)
        if (existing != null) {
            existing.setUri(uri)
            return@LaunchedEffect
        }
        s.addSource(GeoJsonSource(ROADS_SOURCE, uri))
        val layer = LineLayer(ROADS_LAYER, ROADS_SOURCE).withProperties(
            lineColor(
                match(
                    get("t"), color(Color.parseColor(FgColors.NO_DATA_HEX)),
                    stop("H", color(Color.parseColor(FgColors.HIGH_HEX))),
                    stop("M", color(Color.parseColor(FgColors.MEDIUM_HEX))),
                    stop("L", color(Color.parseColor(FgColors.LOW_HEX))),
                )
            ),
            lineSortKey(match(get("t"), literal(0), stop("H", 3), stop("M", 2), stop("L", 1))),
            lineWidth(interpolate(exponential(1.5), zoom(), stop(9, 0.6f), stop(12, 1.6f), stop(15, 4f), stop(18, 10f))),
            lineOpacity(interpolate(exponential(1.0), zoom(), stop(9, 0.75f), stop(14, 0.9f))),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
        )
        // Below the basemap's labels, so street names stay readable on top of the colours.
        val firstLabel = s.layers.firstOrNull { it is SymbolLayer }?.id
        if (firstLabel != null) s.addLayerBelow(layer, firstLabel) else s.addLayer(layer)
    }

    LaunchedEffect(style, roads, visibleTiers) {
        val layer = style?.getLayerAs<LineLayer>(ROADS_LAYER) ?: return@LaunchedEffect
        val parts = visibleTiers.map { eq(get("t"), it) }.toTypedArray()
        layer.setFilter(if (parts.isEmpty()) eq(get("t"), "none") else any(*parts))
    }

    LaunchedEffect(style, pin) {
        val s = style ?: return@LaunchedEffect
        val features = pin?.let { (lat, lon) -> listOf(Feature.fromGeometry(Point.fromLngLat(lon, lat))) } ?: emptyList()
        val collection = FeatureCollection.fromFeatures(features)
        val source = s.getSourceAs<GeoJsonSource>(PIN_SOURCE)
        if (source != null) {
            source.setGeoJson(collection)
        } else {
            s.addSource(GeoJsonSource(PIN_SOURCE, collection))
            s.addLayer(
                CircleLayer(PIN_LAYER, PIN_SOURCE).withProperties(
                    circleRadius(9f), circleColor("#0B5FA5"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
                )
            )
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun Feature.getStringPropertyOrNull(key: String): String? =
    if (hasNonNullValueForProperty(key)) getStringProperty(key) else null
