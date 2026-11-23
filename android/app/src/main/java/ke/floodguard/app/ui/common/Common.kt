package ke.floodguard.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.floodguard.app.ui.theme.FgColors
import ke.floodguard.app.ui.theme.tierColor
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

const val DISCLAIMER = "Decision support only, not an official warning. Follow Kenya Met and county instructions, " +
    "and never cross moving water."

/** Live alert level styling. "unknown" is grey, never green: missing data must not read as safe. */
data class LevelStyle(val bg: Color, val fg: Color, val title: String, val advice: String)

fun levelStyle(levelName: String): LevelStyle = when (levelName) {
    "none" -> LevelStyle(Color(0xFFE3EEF7), Color(0xFF0B3D66), "No alert",
        "No heavy rain is forecast near here in the next 24 hours. Conditions can change, so check again later.")
    "watch" -> LevelStyle(Color(0xFFFFF4CC), Color(0xFF6B4E00), "Watch",
        "Moderate rain is forecast nearby. Low spots on this road may hold water. Plan ahead.")
    "warning" -> LevelStyle(Color(0xFFFFE0C2), Color(0xFF7A3300), "Warning",
        "Heavy rain is forecast nearby. Flooding on this road is possible. Avoid travel during the rain if you can.")
    "severe" -> LevelStyle(Color(0xFFF9D3D0), Color(0xFF7D140E), "Severe",
        "Very heavy rain is forecast. Flooding is likely. Avoid this road and never cross moving water.")
    else -> LevelStyle(Color(0xFFE6E8EB), Color(0xFF3A4550), "Unknown",
        "FloodGuard does not have fresh forecast data right now. This is not an all-clear. Follow Kenya Met updates.")
}

val LEVEL_NAMES = listOf("none", "watch", "warning", "severe")
fun levelName(level: Int?): String = level?.let { LEVEL_NAMES.getOrNull(it) } ?: "unknown"

val REASON_TEXT = mapOf(
    "ground_saturated" to "Ground is already saturated from recent rain",
    "river_above_p99" to "River flow is unusually high",
    "rain_nearby" to "Heavier rain is forecast in a neighbouring area",
)

fun mm(value: Double?): String = value?.let { String.format(Locale.US, "%.1f mm", it) } ?: "—"

fun formatAge(minutes: Double?): String = when {
    minutes == null -> "unknown"
    minutes < 1 -> "just now"
    minutes < 60 -> "${minutes.roundToInt()} min ago"
    minutes < 48 * 60 -> "${(minutes / 60).roundToInt()} h ago"
    else -> "${(minutes / 1440).roundToInt()} days ago"
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.UK)

fun formatTime(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        OffsetDateTime.parse(iso.replace(" ", "T").let { if (it.endsWith("Z") || it.contains('+')) it else it + "Z" })
            .atZoneSameInstant(ZoneId.systemDefault()).format(TIME_FORMAT)
    }.getOrDefault(iso)
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, FgColors.Border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun H2(text: String) = Text(text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = FgColors.Text)

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, color = FgColors.Muted, fontSize = 14.sp, modifier = modifier)

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = FgColors.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** Susceptibility tier as a coloured dot plus label: the same colours as the map. */
@Composable
fun TierBadge(tier: String) {
    val color = tierColor(tier)
    Row(
        Modifier.border(1.dp, color, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(tier.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FgColors.Text)
    }
}

@Composable
fun LevelBanner(levelName: String, place: String?, modifier: Modifier = Modifier) {
    val style = levelStyle(levelName)
    Column(
        modifier.fillMaxWidth().background(style.bg, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Live rain alert" + (place?.let { " · $it" } ?: ""), color = style.fg, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold)
        Text(style.title, color = style.fg, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(style.advice, color = style.fg, fontSize = 14.sp)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFDECEA), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, color = FgColors.Danger, fontSize = 14.sp)
        if (onRetry != null) OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun StaleBanner(state: String) {
    if (state == "fresh") return
    Text(
        if (state == "no_data") "No forecast data yet. This is not an all-clear."
        else "Forecast data is out of date. This is not an all-clear.",
        modifier = Modifier.fillMaxWidth().background(Color(0xFFE6E8EB), RoundedCornerShape(12.dp)).padding(12.dp),
        color = Color(0xFF3A4550), fontSize = 14.sp, style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun Disclaimer(text: String = DISCLAIMER) = Muted(text, Modifier.padding(vertical = 8.dp))
