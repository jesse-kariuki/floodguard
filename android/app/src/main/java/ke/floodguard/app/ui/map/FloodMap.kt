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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.exponential
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.`in` as inExpression
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
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.net.URI

/** A light grey basemap, so the road colours are the only colour on the map. */
const val BASEMAP_STYLE = "https://tiles.openfreemap.org/styles/positron"

private val NAIROBI = LatLng(-1.2921, 36.8219)
private val NAIROBI_BOUNDS = LatLngBounds.Builder()
    .include(LatLng(-1.48, 36.62))
    .include(LatLng(-1.13, 37.08))
    .build()

private const val ROADS_SOURCE = "fg-roads"
private const val ROADS_LAYER_PREFIX = "fg-roads-"
private const val PIN_SOURCE = "fg-pin"
private const val PIN_LAYER = "fg-pin-circle"

/** Imperative handlce for camera moves and the location dot, which do not fit Compose state well. */
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

/** Tier codes in draw order: unscored at the bottom, High on top. */
private val TIERS = listOf("U", "L", "M", "H")

/**
 * One road layer per zone and tier, each with a fixed filter, plus a catch-all per tier for roads in a zone we do
 * not know. Every colour change (tier mode, tier toggles, each replayed day) is then a constant paint value, which
 * MapLibre applies in one frame. A per-road expression instead makes it re-tile all 83k roads from the 17 MB
 * GeoJSON, which took over 30 s in central Nairobi on a phone and left the old colours showing meanwhile.
 */
private data class RoadLayer(val id: String, val zone: String?, val tier: String)

private fun roadLayers(zoneIds: List<String>): List<RoadLayer> = TIERS.flatMap { tier ->
    zoneIds.map { RoadLayer("$ROADS_LAYER_PREFIX$it-$tier", it, tier) } + RoadLayer("$ROADS_LAYER_PREFIX-other-$tier", null, tier)
}

/**
 * The flood map: a light basemap with road segments drawn in their susceptibility colour.
 * Dark blue = High, mid blue = Medium, pale blue = Low, grey = no model data. High is drawn on top.
 * Medium fades in as you zoom, so the city-wide view shows only the real hotspots.
 */
@Composable
fun FloodMap(
    controller: MapController,
    roads: RoadMap?,
    visibleTiers: Set<String>,
    zoneIds: List<String>,
    pin: Pair<Double, Double>?,
    onTap: (latitude: Double, longitude: Double, roadName: String?, tierCode: String?, zoneId: String?) -> Unit,
    modifier: Modifier = Modifier,
    replayLevels: Map<String, Map<String, Int>?>? = null,
    onReplayDrawn: (Map<String, Map<String, Int>?>) -> Unit = {},
) {
    val context = LocalContext.current
    val tapHandler by rememberUpdatedState(onTap)
    val drawnHandler by rememberUpdatedState(onReplayDrawn)
    var style by remember { mutableStateOf<Style?>(null) }
    var layers by remember { mutableStateOf<List<RoadLayer>>(emptyList()) }
    val layerIds by rememberUpdatedState(layers.map { it.id }.toTypedArray())

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
                    val hits = map.queryRenderedFeatures(RectF(point.x - r, point.y - r, point.x + r, point.y + r), *layerIds)
                    val best = hits.maxByOrNull { tierRank(it.getStringPropertyOrNull("t")) }
                    tapHandler(latLng.latitude, latLng.longitude, best?.getStringPropertyOrNull("n"),
                        best?.getStringPropertyOrNull("t"), best?.getStringPropertyOrNull("z"))
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
        if (existing != null) existing.setUri(uri) else s.addSource(GeoJsonSource(ROADS_SOURCE, uri))
    }

    // (Re)build the layers when the zone list arrives. This is the one step that makes MapLibre lay roads out.
    LaunchedEffect(style, roads, zoneIds) {
        val s = style ?: return@LaunchedEffect
        if (roads == null || s.getSource(ROADS_SOURCE) == null) return@LaunchedEffect
        val wanted = roadLayers(zoneIds)
        if (wanted.map { it.id } == layers.map { it.id }) return@LaunchedEffect
        layers.forEach { s.removeLayer(it.id) }
        val known = zoneIds.toTypedArray()
        // Below the basemap's labels, so street names stay readable on top of the colours.
        val firstLabel = s.layers.firstOrNull { it is SymbolLayer }?.id
        wanted.forEach { rl ->
            val inTier = eq(get("t"), rl.tier)
            val filter = if (rl.zone != null) all(inTier, eq(get("z"), rl.zone))
            else all(inTier, not(inExpression(coalesce(get("z"), literal("")), literal(known))))
            val layer = LineLayer(rl.id, ROADS_SOURCE).withFilter(filter).withProperties(
                lineColor(Color.parseColor(tierHex(rl.tier))),
                lineWidth(interpolate(exponential(1.5), zoom(), stop(9, 0.6f), stop(12, 1.6f), stop(15, 4f), stop(18, 10f))),
                lineOpacity(tierOpacity(rl.tier)),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
            if (firstLabel != null) s.addLayerBelow(layer, firstLabel) else s.addLayer(layer)
        }
        layers = wanted
    }

    LaunchedEffect(style, layers, visibleTiers, replayLevels) {
        val s = style ?: return@LaunchedEffect
        if (layers.isEmpty()) return@LaunchedEffect
        layers.forEach { rl ->
            val layer = s.getLayerAs<LineLayer>(rl.id) ?: return@forEach
            if (replayLevels != null) {
                // Replay: every road, coloured by the alert level its zone and tier would have had that day.
                val level = rl.zone?.let { replayLevels[it]?.get(rl.tier) }
                val hex = level?.let { FgColors.LEVEL_HEX.getOrNull(it) } ?: FgColors.UNKNOWN_HEX
                layer.setProperties(lineColor(Color.parseColor(hex)), lineOpacity(VISIBLE_OPACITY))
            } else {
                layer.setProperties(
                    lineColor(Color.parseColor(tierHex(rl.tier))),
                    if (rl.tier in visibleTiers) lineOpacity(tierOpacity(rl.tier)) else lineOpacity(0f),
                )
            }
        }
        if (replayLevels == null) return@LaunchedEffect
        // Tell the screen once the map has drawn the new colours.
        val applied = replayLevels
        mapView.addOnDidBecomeIdleListener(object : MapView.OnDidBecomeIdleListener {
            var done = false
            override fun onDidBecomeIdle() {
                if (done) return
                done = true
                mapView.post { mapView.removeOnDidBecomeIdleListener(this) }
                drawnHandler(applied)
            }
        })
        controller.map?.triggerRepaint()
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
                    // Charcoal, not blue: a blue pin would vanish on the blue susceptibility roads.
                    circleRadius(9f), circleColor("#14202B"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
                )
            )
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private val VISIBLE_OPACITY: Expression = interpolate(exponential(1.0), zoom(), stop(9, 0.75f), stop(14, 0.9f))

/** Medium is faint at city zoom and full strength from street zoom, so the overview is not a wall of colour. */
private val MEDIUM_OPACITY: Expression = interpolate(exponential(1.0), zoom(), stop(11, 0.2f), stop(13.5, 0.85f))

private fun tierOpacity(tier: String): Expression = if (tier == "M") MEDIUM_OPACITY else VISIBLE_OPACITY

private fun tierHex(tier: String) = when (tier) {
    "H" -> FgColors.HIGH_HEX
    "M" -> FgColors.MEDIUM_HEX
    "L" -> FgColors.LOW_HEX
    else -> FgColors.NO_DATA_HEX
}

private fun Feature.getStringPropertyOrNull(key: String): String? =
    if (hasNonNullValueForProperty(key)) getStringProperty(key) else null
