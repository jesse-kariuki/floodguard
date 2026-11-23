package ke.floodguard.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ke.floodguard.app.data.ApiClient
import ke.floodguard.app.data.RiskResponse
import ke.floodguard.app.data.RoadSearchResult
import ke.floodguard.app.data.ZonesResponse
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

/** Loading state for one request. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/** The road map file; [version] changes when a newer download replaces it, so the map reloads. */
data class RoadMap(val uri: String, val version: Long) {
    companion object {
        // MapLibre wants file:///abs/path; File.toURI() gives file:/abs/path, which it does not load.
        fun of(file: File) = RoadMap("file://" + file.absolutePath, file.lastModified())
    }
}

/** What the user tapped or searched for: the map shows a pin there and the sheet shows its details. */
data class Selection(
    val latitude: Double,
    val longitude: Double,
    val roadName: String?,
    val tierCode: String?,          // H, M, L, U from the map feature, if the tap hit a road
    val risk: Load<RiskResponse> = Load.Loading,
)

@OptIn(FlowPreview::class)
class MapViewModel(private val api: ApiClient, private val roadMapFile: File) : ViewModel() {

    private val _roads = MutableStateFlow<Load<RoadMap>>(Load.Loading)
    /** The road network GeoJSON on disk, which the map reads natively. */
    val roads: StateFlow<Load<RoadMap>> = _roads

    /** True when the download failed and the map shows the copy saved on an earlier run. */
    private val _roadsOffline = MutableStateFlow(false)
    val roadsOffline: StateFlow<Boolean> = _roadsOffline

    private val _zones = MutableStateFlow<Load<ZonesResponse>>(Load.Loading)
    val zones: StateFlow<Load<ZonesResponse>> = _zones

    private val _selection = MutableStateFlow<Selection?>(null)
    val selection: StateFlow<Selection?> = _selection

    /** Tiers drawn on the map. All on by default. */
    private val _tiers = MutableStateFlow(setOf("H", "M", "L", "U"))
    val tiers: StateFlow<Set<String>> = _tiers

    val query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<RoadSearchResult>>(emptyList())
    val results: StateFlow<List<RoadSearchResult>> = _results

    private var riskJob: Job? = null

    init {
        loadRoads()
        loadZones()
        query.debounce(300).distinctUntilChanged().onEach { q ->
            _results.value = if (q.trim().length < 2) emptyList()
            else runCatching { api.searchRoads(q.trim()).results }.getOrDefault(emptyList())
        }.launchIn(viewModelScope)
    }

    fun loadRoads() {
        // Show the saved copy at once while checking for a newer one.
        _roads.value = if (roadMapFile.exists()) Load.Ready(RoadMap.of(roadMapFile)) else Load.Loading
        viewModelScope.launch {
            try {
                val file = api.downloadRoadMap(roadMapFile)
                _roadsOffline.value = false
                _roads.value = Load.Ready(RoadMap.of(file))
            } catch (e: Exception) {
                if (roadMapFile.exists()) _roadsOffline.value = true
                else _roads.value = Load.Failed(e.message ?: "Could not load the road map.")
            }
        }
    }

    fun loadZones() {
        viewModelScope.launch {
            _zones.value = runCatching { Load.Ready(api.zones()) }
                .getOrElse { Load.Failed(it.message ?: "Could not load the forecast.") }
        }
    }

    fun toggleTier(code: String) {
        _tiers.value = _tiers.value.let { if (code in it) it - code else it + code }
    }

    fun select(latitude: Double, longitude: Double, roadName: String?, tierCode: String?) {
        _selection.value = Selection(latitude, longitude, roadName, tierCode)
        riskJob?.cancel()
        riskJob = viewModelScope.launch {
            val risk = runCatching { Load.Ready(api.risk(latitude, longitude)) }
                .getOrElse { Load.Failed(it.message ?: "Could not load the risk here.") }
            _selection.value = _selection.value?.takeIf { it.latitude == latitude && it.longitude == longitude }
                ?.copy(risk = risk)
        }
    }

    /** A place the map should fly to, set from outside the map (for example the Alerts tab). */
    private val _focus = MutableStateFlow<RoadSearchResult?>(null)
    val focus: StateFlow<RoadSearchResult?> = _focus

    fun showRoad(name: String) {
        viewModelScope.launch {
            val results = runCatching { api.searchRoads(name).results }.getOrDefault(emptyList())
            val road = results.firstOrNull { it.roadName.equals(name, ignoreCase = true) } ?: results.firstOrNull()
                ?: return@launch
            _focus.value = road
            select(road.latitude, road.longitude, road.roadName, null)
        }
    }

    fun focusHandled() {
        _focus.value = null
    }

    fun clearSelection() {
        riskJob?.cancel()
        _selection.value = null
    }

    fun retryRisk() = _selection.value?.let { select(it.latitude, it.longitude, it.roadName, it.tierCode) }
}
