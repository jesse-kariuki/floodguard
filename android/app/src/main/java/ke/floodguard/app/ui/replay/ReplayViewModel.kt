package ke.floodguard.app.ui.replay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ke.floodguard.app.data.ApiClient
import ke.floodguard.app.data.ReplayDay
import ke.floodguard.app.data.ReplayTimeline
import ke.floodguard.app.ui.map.Load
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Which archived forecast to replay. Day-ahead is the honest default: it is what a day's warning had to go on. */
enum class ReplaySource(val api: String, val label: String) {
    DayAhead("day_ahead", "Day-ahead forecast"),
    LatestRun("latest_run", "Short-notice forecast"),
}

/** A road the user tapped while replaying: its level is worked out from the replayed day's zone levels. */
data class ReplayPick(val roadName: String?, val tierCode: String?, val zoneId: String?)

/**
 * Replay mode: pick a past date and the map recolours to the alert levels FloodGuard would have shown then,
 * from real archived forecasts. Read-only on the backend: it never touches subscribers or sends SMS.
 */
class ReplayViewModel(private val api: ApiClient) : ViewModel() {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    private val _source = MutableStateFlow(ReplaySource.DayAhead)
    val source: StateFlow<ReplaySource> = _source

    private val _timeline = MutableStateFlow<Load<ReplayTimeline>>(Load.Loading)
    val timeline: StateFlow<Load<ReplayTimeline>> = _timeline

    private val _date = MutableStateFlow<LocalDate?>(null)
    val date: StateFlow<LocalDate?> = _date

    private val _day = MutableStateFlow<Load<ReplayDay>?>(null)
    val day: StateFlow<Load<ReplayDay>?> = _day

    private val _pick = MutableStateFlow<ReplayPick?>(null)
    val pick: StateFlow<ReplayPick?> = _pick

    // Details expanded, or collapsed to a slim date bar so the replayed map can be seen. Either way replay stays on.
    private val _expanded = MutableStateFlow(true)
    val expanded: StateFlow<Boolean> = _expanded

    private var dayJob: Job? = null

    fun setExpanded(v: Boolean) {
        _expanded.value = v
    }

    fun open() {
        _active.value = true
        _expanded.value = true
        if (_timeline.value !is Load.Ready) loadTimeline()
    }

    fun close() {
        _active.value = false
        _pick.value = null
    }

    fun loadTimeline() {
        viewModelScope.launch {
            _timeline.value = Load.Loading
            _timeline.value = runCatching { Load.Ready(api.replayTimeline(_source.value.api)) }
                .getOrElse { Load.Failed(it.message ?: "Could not load replay data.") }
            val t = (_timeline.value as? Load.Ready)?.value ?: return@launch
            // Open on the best-documented flood (E3 was read in full), or the first one, so the demo starts somewhere real.
            if (_date.value == null) {
                val first = t.events.firstOrNull { it.eventId == "E3" } ?: t.events.firstOrNull()
                first?.let { pickDate(LocalDate.parse(it.dateStart)) }
            } else {
                _date.value?.let { pickDate(it) }
            }
        }
    }

    fun setSource(s: ReplaySource) {
        if (s == _source.value) return
        _source.value = s
        loadTimeline()
    }

    fun range(): ClosedRange<LocalDate>? = (_timeline.value as? Load.Ready)?.value?.let {
        LocalDate.parse(it.start)..LocalDate.parse(it.end)
    }

    fun pickDate(d: LocalDate) {
        val r = range()
        val day = if (r == null) d else d.coerceIn(r.start, r.endInclusive)
        _date.value = day
        _pick.value = null
        dayJob?.cancel()
        dayJob = viewModelScope.launch {
            _day.value = Load.Loading
            _day.value = runCatching { Load.Ready(api.replay(day.toString(), _source.value.api)) }
                .getOrElse { Load.Failed(it.message ?: "Could not replay this date.") }
        }
    }

    fun step(days: Long) {
        _date.value?.let { pickDate(it.plusDays(days)) }
    }

    fun select(roadName: String?, tierCode: String?, zoneId: String?) {
        _pick.value = ReplayPick(roadName, tierCode, zoneId)
    }

    fun clearPick() {
        _pick.value = null
    }
}
