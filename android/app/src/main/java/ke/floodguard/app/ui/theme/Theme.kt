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

    // Road susceptibility on the map: red = High, yellow = Medium, green = Low, grey = no data.
    const val HIGH_HEX = "#D7261E"
    const val MEDIUM_HEX = "#F2B705"
    const val LOW_HEX = "#2E9E48"
    const val NO_DATA_HEX = "#9AA4AE"
    val High = Color(0xFFD7261E)
    val Medium = Color(0xFFF2B705)
    val Low = Color(0xFF2E9E48)
    val NoData = Color(0xFF9AA4AE)
}

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
