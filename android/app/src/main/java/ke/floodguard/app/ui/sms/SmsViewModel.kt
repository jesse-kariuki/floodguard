package ke.floodguard.app.ui.sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ke.floodguard.app.data.ApiClient
import ke.floodguard.app.data.ApiException
import ke.floodguard.app.data.Place
import ke.floodguard.app.data.RoadSearchResult
import ke.floodguard.app.data.SentAlert
import ke.floodguard.app.data.SubscribeRequest
import ke.floodguard.app.data.Subscriber
import ke.floodguard.app.data.SubscriberUpdate
import ke.floodguard.app.data.TokenStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class Account(val subscriber: Subscriber, val history: List<SentAlert>)

@OptIn(FlowPreview::class)
class SmsViewModel(private val api: ApiClient, private val tokens: TokenStore) : ViewModel() {
    val token: StateFlow<String?> = tokens.token

    // Sign-up form
    val phone = MutableStateFlow("")
    val place = MutableStateFlow<Place?>(null)
    val language = MutableStateFlow("en")
    val minLevel = MutableStateFlow(1)
    val consent = MutableStateFlow(false)
    val code = MutableStateFlow("")
    private val _sentTo = MutableStateFlow<String?>(null)
    /** The normalised number a code was sent to; non-null means the code step is showing. */
    val sentTo: StateFlow<String?> = _sentTo

    val roadQuery = MutableStateFlow("")
    private val _roadResults = MutableStateFlow<List<RoadSearchResult>>(emptyList())
    val roadResults: StateFlow<List<RoadSearchResult>> = _roadResults

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account

    init {
        roadQuery.debounce(300).distinctUntilChanged().onEach { q ->
            _roadResults.value = if (q.trim().length < 2) emptyList()
            else runCatching { api.searchRoads(q.trim()).results }.getOrDefault(emptyList())
        }.launchIn(viewModelScope)
        token.onEach { if (it != null) loadAccount() else _account.value = null }.launchIn(viewModelScope)
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                block()
            } catch (e: ApiException) {
                if (e.status == 401 && tokens.token.value != null) tokens.clear()   // token revoked, e.g. by STOP
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = e.message ?: "Something went wrong."
            } finally {
                _busy.value = false
            }
        }
    }

    fun pickRoad(r: RoadSearchResult) {
        setPlace(Place(r.latitude, r.longitude, r.roadName.take(40)))
        roadQuery.value = ""
    }

    /** A new place makes any earlier error (such as "outside coverage") out of date. */
    fun setPlace(p: Place) {
        place.value = p
        _error.value = null
    }

    fun requestCode() = run {
        val p = place.value ?: throw IllegalStateException("Choose a place to watch.")
        val r = api.subscribe(SubscribeRequest(phone.value.trim(), p.latitude, p.longitude, p.label, language.value,
            minLevel.value, consent.value))
        _sentTo.value = r.phone
        code.value = ""
    }

    fun verify() = run {
        val r = api.verify(_sentTo.value ?: return@run, code.value)
        tokens.save(r.token)
        _sentTo.value = null
    }

    fun editDetails() {
        _sentTo.value = null
    }

    fun loadAccount() = run {
        val t = tokens.token.value ?: return@run
        _account.value = Account(api.me(t), api.myAlerts(t).alerts)
    }

    fun save(changes: SubscriberUpdate, onDone: () -> Unit) = run {
        val t = tokens.token.value ?: return@run
        val updated = api.updateMe(t, changes)
        _account.value = _account.value?.copy(subscriber = updated)
        onDone()
    }

    fun delete() = run {
        val t = tokens.token.value ?: return@run
        api.deleteMe(t)
        tokens.clear()
        consent.value = false
    }
}
