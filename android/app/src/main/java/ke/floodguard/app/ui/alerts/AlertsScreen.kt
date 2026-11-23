package ke.floodguard.app.ui.alerts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ke.floodguard.app.data.ApiClient
import ke.floodguard.app.data.OfficialAlertsResponse
import ke.floodguard.app.data.RoadsAtRiskResponse
import ke.floodguard.app.ui.common.Disclaimer
import ke.floodguard.app.ui.common.ErrorBox
import ke.floodguard.app.ui.common.H2
import ke.floodguard.app.ui.common.Muted
import ke.floodguard.app.ui.common.SectionCard
import ke.floodguard.app.ui.common.StaleBanner
import ke.floodguard.app.ui.common.formatTime
import ke.floodguard.app.ui.common.levelName
import ke.floodguard.app.ui.common.levelStyle
import ke.floodguard.app.ui.map.Load
import ke.floodguard.app.ui.theme.FgColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AlertsData(val roads: RoadsAtRiskResponse, val official: OfficialAlertsResponse, val kmdFeedDown: Boolean)

class AlertsViewModel(private val api: ApiClient) : ViewModel() {
    private val _state = MutableStateFlow<Load<AlertsData>>(Load.Loading)
    val state: StateFlow<Load<AlertsData>> = _state
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            // coroutineScope contains a failed request: a bare async inside runCatching would still crash the app.
            _state.value = runCatching {
                coroutineScope {
                    val roads = async { api.roadsAtRisk(1) }
                    val official = async { api.officialAlerts() }
                    // If the last cycle could not read the Kenya Met feed, "no warnings" would be a false all-clear.
                    val status = async { runCatching { api.status() }.getOrNull() }
                    val feedDown = status.await()?.lastCycle?.sourceErrors?.containsKey("kmd_cap") ?: false
                    Load.Ready(AlertsData(roads.await(), official.await(), feedDown))
                }
            }.getOrElse { Load.Failed(it.message ?: "Could not load alerts.") }
            _refreshing.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(vm: AlertsViewModel, onShowRoad: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val context = LocalContext.current

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = modifier.fillMaxSize()) {
        when (val s = state) {
            Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Load.Failed -> Column(Modifier.statusBarsPadding().padding(16.dp)) { ErrorBox(s.message, vm::refresh) }
            is Load.Ready -> LazyColumn(
                Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("Alerts", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                item {
                    SectionCard {
                        H2("Roads under a rain alert now")
                        StaleBanner(s.value.roads.data.state)
                        val roads = s.value.roads.roads
                        if (roads.isEmpty() && s.value.roads.data.isFresh) {
                            Muted("No named roads are under a rain alert right now.")
                        }
                        roads.take(30).forEach { road ->
                            val style = levelStyle(levelName(road.level))
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(road.roadName, Modifier.weight(1f), fontSize = 15.sp)
                                Text(style.title, Modifier.background(style.bg, RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 3.dp), color = style.fg, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold)
                                TextButton(onClick = { onShowRoad(road.roadName) }) { Text("Map") }
                            }
                        }
                    }
                }
                item { H2("Kenya Met warnings") }
                val kmd = s.value.official.kmdAlerts.sortedByDescending { it.coversNairobi }
                if (s.value.kmdFeedDown) item {
                    ErrorBox("FloodGuard could not reach the Kenya Met warnings feed on its last check, so warnings " +
                        "may be missing here. Check meteo.go.ke directly.")
                }
                if (kmd.isEmpty() && !s.value.kmdFeedDown) item { Muted("No active Kenya Met warnings.") }
                items(kmd, key = { it.identifier }) { a ->
                    SectionCard {
                        if (a.coversNairobi) Text("COVERS NAIROBI", color = FgColors.Danger, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                        Text(a.headline ?: a.event ?: "Warning", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        a.severity?.let { Muted("Severity: $it") }
                        a.area?.let { Muted(it) }
                        a.description?.let { Text(it, fontSize = 14.sp) }
                        a.instruction?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                        Muted("Issued ${formatTime(a.sentAt)} · expires ${formatTime(a.expiresAt)}")
                        a.url?.let { url ->
                            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                                Text("Open on Kenya Met")
                            }
                        }
                    }
                }
                val gdacs = s.value.official.gdacsEvents
                if (gdacs.isNotEmpty()) {
                    item { H2("GDACS flood events in Kenya") }
                    items(gdacs) { e ->
                        SectionCard {
                            Text(e.name ?: "Flood event", fontWeight = FontWeight.SemiBold)
                            Muted("Alert level ${e.alertLevel ?: "—"} · ${formatTime(e.from)} to ${formatTime(e.to)}")
                        }
                    }
                }
                item { Disclaimer() }
            }
        }
    }
}
