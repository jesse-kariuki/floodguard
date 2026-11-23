package ke.floodguard.app.ui.sms

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.floodguard.app.data.Locator
import ke.floodguard.app.data.Place
import ke.floodguard.app.data.SubscriberUpdate
import ke.floodguard.app.ui.common.Disclaimer
import ke.floodguard.app.ui.common.ErrorBox
import ke.floodguard.app.ui.common.H2
import ke.floodguard.app.ui.common.InfoRow
import ke.floodguard.app.ui.common.Muted
import ke.floodguard.app.ui.common.SectionCard
import ke.floodguard.app.ui.common.TierBadge
import ke.floodguard.app.ui.common.formatTime
import ke.floodguard.app.ui.common.levelName
import ke.floodguard.app.ui.common.levelStyle
import ke.floodguard.app.ui.theme.FgColors
import kotlinx.coroutines.launch

private val LANGUAGES = listOf("en" to "English", "sw" to "Kiswahili")
private val LEVELS = listOf(1 to "Watch", 2 to "Warning", 3 to "Severe only")

@Composable
fun SmsScreen(vm: SmsViewModel, locator: Locator, modifier: Modifier = Modifier) {
    val token by vm.token.collectAsState()
    val error by vm.error.collectAsState()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("SMS alerts", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (token == null) SignUp(vm, locator, error) else Manage(vm, error)
        Disclaimer()
    }
}

@Composable
private fun SignUp(vm: SmsViewModel, locator: Locator, error: String?) {
    val sentTo by vm.sentTo.collectAsState()
    val busy by vm.busy.collectAsState()
    val code by vm.code.collectAsState()

    if (sentTo != null) {
        SectionCard {
            H2("Enter your code")
            Muted("We sent a 6-digit code to $sentTo. It expires in 10 minutes.")
            OutlinedTextField(
                value = code, onValueChange = { vm.code.value = it.filter(Char::isDigit).take(6) },
                label = { Text("Code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            error?.let { ErrorBox(it) }
            BusyButton("Confirm", busy, enabled = code.length == 6, onClick = vm::verify)
            OutlinedButton(onClick = vm::requestCode, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Send a new code")
            }
            TextButton(onClick = vm::editDetails, enabled = !busy) { Text("Change details") }
        }
        return
    }

    val phone by vm.phone.collectAsState()
    val place by vm.place.collectAsState()
    val language by vm.language.collectAsState()
    val minLevel by vm.minLevel.collectAsState()
    val consent by vm.consent.collectAsState()

    SectionCard {
        H2("Get flood alerts by SMS")
        Muted("FloodGuard texts you when heavy rain is forecast near the place you choose, and forwards Kenya Met " +
            "warnings that cover it. Works on any phone once you sign up.")
        OutlinedTextField(
            value = phone, onValueChange = { vm.phone.value = it.take(20) }, label = { Text("Kenyan mobile number") },
            placeholder = { Text("0712345678") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        PlacePicker(vm, locator, place, vm::setPlace)
        Choice("SMS language", LANGUAGES, language) { vm.language.value = it }
        Choice("Alert me from", LEVELS, minLevel) { vm.minLevel.value = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = consent, onCheckedChange = { vm.consent.value = it },
                modifier = Modifier.semantics { contentDescription = "Consent to SMS alerts" })
            Spacer(Modifier.width(12.dp))
            Text("I agree to receive flood SMS at this number. FloodGuard stores my number and chosen place only to " +
                "send these alerts, and I can delete them at any time.", fontSize = 13.sp)
        }
        error?.let { ErrorBox(it) }
        BusyButton("Send me a code", busy, enabled = consent && place != null && phone.trim().length >= 9,
            onClick = vm::requestCode)
    }
}

@Composable
private fun PlacePicker(vm: SmsViewModel, locator: Locator, place: Place?, onPlace: (Place) -> Unit) {
    val query by vm.roadQuery.collectAsState()
    val results by vm.roadResults.collectAsState()
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun locate() = scope.launch {
        locating = true
        val here = locator.current()
        locating = false
        if (here == null) problem = "Could not get your location. Search for a road instead."
        else { problem = null; onPlace(Place(here.latitude, here.longitude, "My location")) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.any { granted -> granted }) locate() else problem = "Location permission was denied."
    }

    Text("Place to watch", fontWeight = FontWeight.SemiBold)
    place?.let { Text("✓ ${it.label ?: "%.4f, %.4f".format(it.latitude, it.longitude)}", color = FgColors.Primary,
        fontWeight = FontWeight.SemiBold) }
    OutlinedButton(
        onClick = {
            if (locator.hasPermission()) locate()
            else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        },
        enabled = !locating, modifier = Modifier.fillMaxWidth(),
    ) {
        if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Use my current location")
    }
    OutlinedTextField(
        value = query, onValueChange = { vm.roadQuery.value = it }, label = { Text("Or search for a road") },
        placeholder = { Text("e.g. Mombasa Road") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    results.take(6).forEach { r ->
        Text(r.roadName, Modifier.fillMaxWidth().clickable { vm.pickRoad(r) }.padding(vertical = 10.dp), fontSize = 15.sp)
        HorizontalDivider(color = FgColors.Border)
    }
    problem?.let { Muted(it) }
}

@Composable
private fun <T> Choice(label: String, options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit) {
    Text(label, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, text) -> FilterChip(selected = v == value, onClick = { onChange(v) }, label = { Text(text) }) }
    }
}

@Composable
private fun BusyButton(title: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth()) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(title)
    }
}

@Composable
private fun Manage(vm: SmsViewModel, error: String?) {
    val account by vm.account.collectAsState()
    val busy by vm.busy.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val acc = account
    if (acc == null) {
        if (error != null) ErrorBox(error, vm::loadAccount) else CircularProgressIndicator()
        return
    }
    val sub = acc.subscriber
    SectionCard {
        H2("You get flood SMS")
        if (!editing) {
            InfoRow("Number", sub.phone)
            InfoRow("Place", sub.placeLabel ?: "%.4f, %.4f".format(sub.latitude, sub.longitude))
            InfoRow("Language", if (sub.language == "sw") "Kiswahili" else "English")
            InfoRow("Alert me from", LEVELS.firstOrNull { it.first == sub.minLevel }?.second ?: sub.minLevel.toString())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Road susceptibility there", Modifier.weight(1f), color = FgColors.Muted, fontSize = 14.sp)
                TierBadge(sub.tier)
            }
            error?.let { ErrorBox(it) }
            OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Change settings") }
        } else {
            var language by remember { mutableStateOf(sub.language) }
            var minLevel by remember { mutableStateOf(sub.minLevel) }
            Choice("SMS language", LANGUAGES, language) { language = it }
            Choice("Alert me from", LEVELS, minLevel) { minLevel = it }
            error?.let { ErrorBox(it) }
            BusyButton("Save", busy, enabled = true) {
                vm.save(SubscriberUpdate(language = language, minLevel = minLevel)) { editing = false }
            }
            TextButton(onClick = { editing = false }, enabled = !busy) { Text("Cancel") }
        }
    }
    SectionCard {
        H2("Alerts sent to you")
        if (acc.history.isEmpty()) Muted("None yet.")
        acc.history.forEach { a ->
            val style = levelStyle(levelName(a.level))
            Column(Modifier.padding(vertical = 4.dp)) {
                Text("${if (a.level != null) style.title else a.kind} · ${formatTime(a.sentAt)}", fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = style.fg)
                Text(a.message, fontSize = 14.sp)
            }
        }
    }
    Button(
        onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = FgColors.Danger),
    ) { Text("Stop alerts and delete my data") }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Stop SMS alerts?") },
            text = { Text("This deletes your number, place and alert history from FloodGuard.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete", color = FgColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
