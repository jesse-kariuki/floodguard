package ke.floodguard.app.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.floodguard.app.data.Locator
import ke.floodguard.app.data.RiskResponse
import ke.floodguard.app.data.ZonesResponse
import ke.floodguard.app.ui.common.Disclaimer
import ke.floodguard.app.ui.common.ErrorBox
import ke.floodguard.app.ui.common.InfoRow
import ke.floodguard.app.ui.common.LevelBanner
import ke.floodguard.app.ui.common.Muted
import ke.floodguard.app.ui.common.REASON_TEXT
import ke.floodguard.app.ui.common.StaleBanner
import ke.floodguard.app.ui.common.TierBadge
import ke.floodguard.app.ui.common.formatAge
import ke.floodguard.app.ui.replay.ReplayRoadSheet
import ke.floodguard.app.ui.replay.ReplayTopPanel
import ke.floodguard.app.ui.replay.ReplayVerdictCard
import ke.floodguard.app.ui.replay.ReplayVerdictChip
import ke.floodguard.app.ui.replay.ReplayViewModel
import ke.floodguard.app.ui.replay.formatDay
import ke.floodguard.app.ui.common.mm
import ke.floodguard.app.ui.theme.FgColors
import ke.floodguard.app.ui.theme.tierColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TIER_CHIPS = listOf("H" to "High", "M" to "Medium", "L" to "Low", "U" to "No data")
private fun tierLabel(code: String?) = TIER_CHIPS.firstOrNull { it.first == code }?.second

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: MapViewModel, replayVm: ReplayViewModel, locator: Locator, modifier: Modifier = Modifier) {
    val roads by vm.roads.collectAsState()
    val zones by vm.zones.collectAsState()
    val selection by vm.selection.collectAsState()
    val tiers by vm.tiers.collectAsState()
    val controller = remember { MapController() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    val focus by vm.focus.collectAsState()
    val roadsOffline by vm.roadsOffline.collectAsState()
    val replaying by replayVm.active.collectAsState()
    val replayDay by replayVm.day.collectAsState()
    val replayPick by replayVm.pick.collectAsState()
    val replayDate by replayVm.date.collectAsState()
    val replayExpanded by replayVm.expanded.collectAsState()
    // The levels the map has actually finished drawing. Until they match the loaded day, keep the loading pill up.
    var drawnLevels by remember { mutableStateOf<Map<String, Map<String, Int>?>?>(null) }
    // Keep the last replayed colours on screen while the next date loads, instead of flashing back to tiers.
    // If that load fails, drop them: the old day's colours must never sit under the new date's label.
    var replayLevels by remember { mutableStateOf<Map<String, Map<String, Int>?>?>(null) }
    LaunchedEffect(replayDay, replaying) {
        val d = replayDay
        replayLevels = when {
            !replaying -> null
            d is Load.Ready -> d.value.levels
            d is Load.Failed -> emptyMap()      // every road "no data" grey
            else -> replayLevels
        }
    }

    LaunchedEffect(focus, roads) {
        val road = focus ?: return@LaunchedEffect
        controller.flyTo(road.latitude, road.longitude, 15.0)
        vm.focusHandled()
    }

    fun goToMyLocation() {
        scope.launch {
            locating = true
            controller.enableLocationDot(context)
            val here = locator.current()
            locating = false
            if (here == null) locationError = "Could not get your location. Is location turned on?"
            else {
                locationError = null
                controller.flyTo(here.latitude, here.longitude, 15.5)
                vm.select(here.latitude, here.longitude, null, null)
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) goToMyLocation()
        else locationError = "Location permission was denied. You can still search or tap the map."
    }

    Box(modifier.fillMaxSize()) {
        FloodMap(
            controller = controller,
            roads = (roads as? Load.Ready)?.value,
            visibleTiers = tiers,
            zoneIds = (zones as? Load.Ready)?.value?.zones?.map { it.zoneId }.orEmpty(),
            pin = if (replaying) null else selection?.let { it.latitude to it.longitude },
            onTap = { lat, lon, name, tier, zone ->
                if (replaying) replayVm.select(name, tier, zone) else vm.select(lat, lon, name, tier)
            },
            modifier = Modifier.fillMaxSize(),
            // While replaying, never fall back to susceptibility colours: until a day loads (or if it fails),
            // every road is "no data" grey, so red High roads cannot be mistaken for a replayed alert.
            replayLevels = if (replaying) replayLevels ?: emptyMap() else null,
            onReplayDrawn = { drawnLevels = it },
        )

        if (replaying) {
            ReplayTopPanel(replayVm, Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp))
            // Until the new day's levels arrive the map still shows the previous day, so say so in the middle of it.
            val fetching = replayDay == null || replayDay == Load.Loading
            val drawing = replayDay is Load.Ready<*> && replayLevels != drawnLevels
            if (fetching || drawing) {
                Surface(
                    Modifier.align(Alignment.Center), shape = RoundedCornerShape(24.dp),
                    color = Color.White, shadowElevation = 8.dp,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                replayDate == null -> "Loading replay…"
                                fetching -> "Loading roads for ${formatDay(replayDate.toString())}…"
                                else -> "Drawing roads for ${formatDay(replayDate.toString())}…"
                            },
                            fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
                horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallFloatingActionButton(onClick = { controller.showAllNairobi() }, containerColor = Color.White) {
                    Icon(Icons.Default.ZoomOutMap, contentDescription = "Show all of Nairobi")
                }
                if (replayExpanded) ReplayVerdictCard(replayVm, Modifier.heightIn(max = 280.dp))
                else ReplayVerdictChip(replayVm)
            }
        } else Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchBox(vm, onPick = { r ->
                    controller.flyTo(r.latitude, r.longitude, 15.0)
                    vm.select(r.latitude, r.longitude, r.roadName, null)
                })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIER_CHIPS.forEach { (code, label) ->
                        FilterChip(
                            selected = code in tiers,
                            onClick = { vm.toggleTier(code) },
                            label = { Text(label) },
                            leadingIcon = { Box(Modifier.size(10.dp).background(tierColor(code), CircleShape)) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = Color.White,
                                selectedContainerColor = Color(0xFFE3EEF7)),
                        )
                    }
                }
                ForecastPill(zones)
                when (val r = roads) {
                    Load.Loading -> Surface(shape = RoundedCornerShape(12.dp), shadowElevation = 2.dp) {
                        Column(Modifier.padding(12.dp).fillMaxWidth()) {
                            Text("Loading the Nairobi road map…", fontSize = 14.sp)
                            Spacer(Modifier.size(6.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    is Load.Failed -> ErrorBox("Road map: ${r.message}", onRetry = vm::loadRoads)
                    is Load.Ready -> Unit
                }
                if (roadsOffline) ErrorBox("Showing the road map saved on this phone. It could not be updated.", vm::loadRoads)
                locationError?.let { ErrorBox(it) }
            }

            Legend(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(12.dp))

            Column(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SmallFloatingActionButton(onClick = {
                    vm.clearSelection()
                    replayVm.open()
                    controller.showAllNairobi()
                }, containerColor = Color.White) {
                    Icon(Icons.Default.History, contentDescription = "Replay a past day", tint = FgColors.Primary)
                }
                SmallFloatingActionButton(onClick = { controller.showAllNairobi() }, containerColor = Color.White) {
                    Icon(Icons.Default.ZoomOutMap, contentDescription = "Show all of Nairobi")
                }
                SmallFloatingActionButton(
                    onClick = {
                        if (locator.hasPermission()) goToMyLocation()
                        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    containerColor = Color.White,
                ) {
                    if (locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.MyLocation, contentDescription = "My location", tint = FgColors.Primary)
                }
            }
        }
    }

    if (replaying) replayPick?.let { pick ->
        ModalBottomSheet(
            onDismissRequest = replayVm::clearPick,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = Color.White,
        ) {
            ReplayRoadSheet(pick, (replayDay as? Load.Ready)?.value)
        }
    }

    if (!replaying) selection?.let { sel ->
        ModalBottomSheet(
            onDismissRequest = vm::clearSelection,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = Color.White,
        ) {
            SelectionSheet(sel, onRetry = { vm.retryRisk() })
        }
    }
}

@Composable
private fun SearchBox(vm: MapViewModel, onPick: (ke.floodguard.app.data.RoadSearchResult) -> Unit) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    Column {
        Surface(shape = RoundedCornerShape(28.dp), shadowElevation = 6.dp, color = Color.White) {
            TextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                placeholder = { Text("Search a road in Nairobi") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        if (focused && results.isNotEmpty()) {
            Surface(
                Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(max = 320.dp),
                shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp, color = Color.White,
            ) {
                LazyColumn {
                    items(results, key = { it.roadName + it.latitude }) { r ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                focus.clearFocus()
                                vm.query.value = ""
                                onPick(r)
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.roadName, fontWeight = FontWeight.SemiBold)
                                Muted("${r.segments} segments · ${r.highSegments} high susceptibility")
                            }
                        }
                        HorizontalDivider(color = FgColors.Border)
                    }
                }
            }
        }
    }
}

/** One line on today's forecast, so the static colours are never mistaken for a live warning. */
@Composable
private fun ForecastPill(zones: Load<ZonesResponse>) {
    val z = (zones as? Load.Ready)?.value ?: return
    val wet = z.zones.count { it.available && it.rainBand >= 1 }
    val (text, bg) = when {
        !z.data.isFresh -> "Forecast data is out of date. Not an all-clear." to Color(0xFFE6E8EB)
        wet > 0 -> "Heavy rain forecast in $wet of ${z.zones.size} areas in the next 24 h. Tap a road for its alert." to
            Color(0xFFFFE0C2)
        else -> "No heavy rain forecast in the next 24 h · updated ${formatAge(z.data.ageMinutes)}" to Color(0xFFE3EEF7)
    }
    Surface(shape = RoundedCornerShape(12.dp), color = bg, shadowElevation = 2.dp) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 13.sp, color = FgColors.Text)
    }
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.95f), shadowElevation = 4.dp) {
        Column(Modifier.padding(10.dp).width(160.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Flood susceptibility", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            listOf("H" to "High", "M" to "Medium", "L" to "Low").forEach { (code, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 22.dp, height = 5.dp).background(tierColor(code), RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontSize = 12.sp)
                }
            }
            Text("Where water tends to collect. Not a live warning.", fontSize = 11.sp, color = FgColors.Muted)
        }
    }
}

@Composable
private fun SelectionSheet(sel: Selection, onRetry: () -> Unit) {
    val risk = (sel.risk as? Load.Ready)?.value
    val title = sel.roadName ?: risk?.nearbyRoads?.firstOrNull()?.roadName ?: "Selected spot"
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        tierLabel(sel.tierCode)?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Muted("This road segment  ")
                TierBadge(it)
            }
        }
        when (val r = sel.risk) {
            Load.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Muted("Checking today's forecast here…")
            }
            is Load.Failed -> ErrorBox(r.message, onRetry)
            is Load.Ready -> RiskDetails(r.value)
        }
    }
}

@Composable
private fun RiskDetails(risk: RiskResponse) {
    StaleBanner(risk.data.state)
    LevelBanner(risk.levelName, null)
    Muted("Forecast updated ${formatAge(risk.data.ageMinutes)}")
    risk.weather?.let { w ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Rain forecast", fontWeight = FontWeight.SemiBold)
            InfoRow("Next 6 hours", mm(w.rainNext6h))
            InfoRow("Next 24 hours", mm(w.rainNext24h))
            InfoRow("Wettest nearby (24 h)", mm(w.rainArea24h))
            InfoRow("Past 72 hours", mm(w.rainPrev72h))
            w.precipProbabilityMax?.let { InfoRow("Chance of rain", "${it.roundToInt()}%") }
            w.reasons.forEach { Muted("• " + (REASON_TEXT[it] ?: it)) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Area around here", Modifier.weight(1f), color = FgColors.Muted, fontSize = 14.sp)
        TierBadge(risk.tier)
    }
    InfoRow("Distance to nearest road", "${risk.distanceToRoadM.roundToInt()} m")
    if (risk.nearbyRoads.isNotEmpty()) {
        Text("Roads nearby", fontWeight = FontWeight.SemiBold)
        risk.nearbyRoads.take(6).forEach { road ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(road.roadName, Modifier.weight(1f), fontSize = 14.sp)
                TierBadge(road.tier)
            }
        }
    }
    Disclaimer(risk.disclaimer)
}
