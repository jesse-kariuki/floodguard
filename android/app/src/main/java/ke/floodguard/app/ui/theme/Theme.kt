package ke.floodguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object FgColors {
    val Primary = Color(0xFF0B5FA5)
    val Background = Color(0xFFF4F6F8)
    val Text = Color(0xFF14202B)
    val Muted = Color(0xFF5B6B79)
    val Border = Color(0xFFDCE2E8)
    val Danger = Color(0xFFB3261E)

    // Road susceptibility on the map: one calm blue ramp, dark = High, light = Low, grey = no data.
    // Static "where water tends to collect", so it must never borrow the warm alert colours below, and Low is
    // never green: a flood app must not paint roads as "safe".
    const val HIGH_HEX = "#1E3A8A"
    const val MEDIUM_HEX = "#5B8DEF"
    const val LOW_HEX = "#B7CCEB"
    const val NO_DATA_HEX = "#B0B7BF"
    val High = Color(0xFF1E3A8A)
    val Medium = Color(0xFF5B8DEF)
    val Low = Color(0xFFB7CCEB)
    val NoData = Color(0xFFB0B7BF)

    // Live and replayed alert levels: the only warm colours in the app, so red always means "alert".
    // "None" is pale so wet roads stand out; "unknown" is dark grey, never a reassuring colour.
    val LEVEL_HEX = listOf("#C9D3DB", "#F2C200", "#F07800", "#B3261E")    // none, watch, warning, severe
    const val UNKNOWN_HEX = "#5F6B76"
}

fun levelColor(level: Int?): Color =
    Color(android.graphics.Color.parseColor(level?.let { FgColors.LEVEL_HEX.getOrNull(it) } ?: FgColors.UNKNOWN_HEX))

fun tierColor(tier: String?): Color = when (tier) {
    "High", "H" -> FgColors.High
    "Medium", "M" -> FgColors.Medium
    "Low", "L" -> FgColors.Low
    else -> FgColors.NoData
}

@Composable
fun FloodGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FgColors.Primary,
            background = FgColors.Background,
            surface = Color.White,
            onSurface = FgColors.Text,
            onBackground = FgColors.Text,
            error = FgColors.Danger,
        ),
        content = content,
    )
}
