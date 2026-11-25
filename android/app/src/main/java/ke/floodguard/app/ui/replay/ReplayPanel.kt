package ke.floodguard.app.ui.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.floodguard.app.data.ReplayDay
import ke.floodguard.app.data.ReplayTimeline
import ke.floodguard.app.ui.common.InfoRow
import ke.floodguard.app.ui.common.Muted
import ke.floodguard.app.ui.common.REASON_TEXT
import ke.floodguard.app.ui.common.TierBadge
import ke.floodguard.app.ui.common.levelName
import ke.floodguard.app.ui.common.levelStyle
import ke.floodguard.app.ui.common.mm
import ke.floodguard.app.ui.map.Load
import ke.floodguard.app.ui.theme.FgColors
import ke.floodguard.app.ui.theme.levelColor
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)
private val SHORT_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
private val EVENT_BLUE = Color(0xFF0B5FA5)

fun formatDay(iso: String): String = runCatching { LocalDate.parse(iso).format(DAY_FORMAT) }.getOrDefault(iso)

/** Date, source and flood shortcuts, plus the whole-period timeline. Sits at the top of the map in replay mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayTopPanel(vm: ReplayViewModel, modifier: Modifier = Modifier) {
    val timeline by vm.timeline.collectAsState()
    val date by vm.date.collectAsState()
    val source by vm.source.collectAsState()
    val expanded by vm.expanded.collectAsState()
    var picking by remember { mutableStateOf(false) }

    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 6.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (expanded) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Replay a past day", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Real archived forecasts, run through today's alert rules", fontSize = 12.sp,
                        color = FgColors.Muted)
                }
                IconButton(onClick = { vm.setExpanded(false) }) {
                    Icon(Icons.Default.ExpandLess, contentDescription = "Hide details and show the map")
                }
                IconButton(onClick = vm::close) { Icon(Icons.Default.Close, contentDescription = "Leave replay") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.step(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                }
                OutlinedButton(onClick = { picking = true }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.DateRange, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(date?.format(if (expanded) DAY_FORMAT else SHORT_FORMAT) ?: "Pick a date", maxLines = 1, softWrap = false)
                }
                IconButton(onClick = { vm.step(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                }
                if (!expanded) {
                    IconButton(onClick = { vm.setExpanded(true) }) {
                        Icon(Icons.Default.ExpandMore, contentDescription = "Show replay details")
                    }
                    IconButton(onClick = vm::close) { Icon(Icons.Default.Close, contentDescription = "Leave replay") }
                }
            }
            if (expanded) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReplaySource.entries.forEach { s ->
                    FilterChip(selected = s == source, onClick = { vm.setSource(s) }, label = { Text(s.label, fontSize = 12.sp) })
                }
            }
            if (expanded) when (val t = timeline) {
                Load.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Muted("Loading the archive…")
                }
                is Load.Failed -> Column {
                    Text(t.message, color = FgColors.Danger, fontSize = 13.sp)
                    TextButton(onClick = vm::loadTimeline) { Text("Try again") }
                }
                is Load.Ready -> {
                    FloodChips(t.value, date, onPick = vm::pickDate)
                    TimelineStrip(t.value, date, onPick = vm::pickDate)
                    val s = t.value.summary
                    Text("${s.eventsDetected} of ${s.eventsTotal} documented floods caught · ${s.alertDays} alert days " +
                        "in ${s.days} · ${s.episodesMatching} of ${s.alertEpisodes} alert spells match a documented flood",
                        fontSize = 11.sp, color = FgColors.Muted)
                }
            }
        }
    }

    if (picking) {
        val range = vm.range()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
            yearRange = (range?.start?.year ?: 2024)..(range?.endInclusive?.year ?: 2026),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = LocalDate.ofEpochDay(Math.floorDiv(utcTimeMillis, 86_400_000L))
                    return range == null || d in range
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.pickDate(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) }
                    picking = false
                }) { Text("Replay") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** One chip per documented flood: a tick if the system caught it, a cross if it missed. */
@Composable
private fun FloodChips(t: ReplayTimeline, date: LocalDate?, onPick: (LocalDate) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        t.events.forEach { e ->
            val start = LocalDate.parse(e.dateStart)
            val end = LocalDate.parse(e.dateEnd)
            FilterChip(
                selected = date != null && !date.isBefore(start) && !date.isAfter(end),
                onClick = { onPick(start) },
                label = { Text("${e.eventId} · ${start.format(SHORT_FORMAT)}", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(if (e.detected) Icons.Default.Check else Icons.Default.Close, contentDescription =
                        if (e.detected) "caught" else "missed", Modifier.size(16.dp),
                        tint = if (e.detected) FgColors.Low else FgColors.Danger)
                },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE3EEF7)),
            )
        }
    }
}

/**
 * Every archived day in one strip: a bar on each alert day (taller and redder for heavier bands), a blue tick
 * under each documented flood, and a line at the replayed date. Tap anywhere to jump there.
 */
@Composable
private fun TimelineStrip(t: ReplayTimeline, date: LocalDate?, onPick: (LocalDate) -> Unit) {
    val start = LocalDate.parse(t.start)
    val n = t.bands.size
    val eventIdx = t.events.map { (LocalDate.parse(it.dateStart).toEpochDay() - start.toEpochDay()).toInt() }
    val years = (start.year + 1..LocalDate.parse(t.end).year).map {
        it to (LocalDate.of(it, 1, 1).toEpochDay() - start.toEpochDay()).toInt()
    }
    Column {
        Canvas(
            Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFF4F6F8), RoundedCornerShape(6.dp))
                .pointerInput(t) {
                    detectTapGestures { pos ->
                        val i = (pos.x / size.width * n).toInt().coerceIn(0, n - 1)
                        onPick(start.plusDays(i.toLong()))
                    }
                },
        ) {
            val dx = size.width / n
            val barArea = size.height - 10f
            years.forEach { (_, i) ->
                drawLine(Color(0xFFDCE2E8), Offset(i * dx, 0f), Offset(i * dx, size.height), strokeWidth = 2f)
            }
            t.bands.forEachIndexed { i, band ->
                if ((band ?: 0) < 1) return@forEachIndexed
                val h = barArea * (band!!.coerceAtMost(3)) / 3f
                drawRect(levelColor(band), Offset(i * dx, barArea - h), Size(dx.coerceAtLeast(1.5f), h))
            }
            eventIdx.forEach { i ->
                drawRect(EVENT_BLUE, Offset(i * dx - 2f, size.height - 8f), Size(5f, 8f))
            }
            date?.let {
                val i = (it.toEpochDay() - start.toEpochDay()).toFloat()
                drawLine(Color.Black, Offset(i * dx, 0f), Offset(i * dx, size.height), strokeWidth = 3f)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
            Text(start.year.toString(), fontSize = 10.sp, color = FgColors.Muted)
            years.forEach { (y, i) ->
                Text(y.toString(), fontSize = 10.sp, color = FgColors.Muted,
                    modifier = Modifier.offset(x = maxWidth * (i.toFloat() / n) + 3.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(EVENT_BLUE))
            Text("  documented flood   ", fontSize = 10.sp, color = FgColors.Muted)
            Box(Modifier.size(8.dp).background(levelColor(1)))
            Text("  alert day (taller = more rain)", fontSize = 10.sp, color = FgColors.Muted)
        }
    }
}

private data class VerdictStyle(val bg: Color, val fg: Color, val title: String)

private fun verdictStyle(code: String): VerdictStyle = when (code) {
    "hit" -> VerdictStyle(Color(0xFFFFE0C2), Color(0xFF7A3300), "Flood documented · alert showing")
    "hit_other_day" -> VerdictStyle(Color(0xFFFFF4CC), Color(0xFF6B4E00), "Flood documented · caught")
    "miss" -> VerdictStyle(Color(0xFFF9D3D0), Color(0xFF7D140E), "Flood documented · missed")
    "no_documented_flood" -> VerdictStyle(Color(0xFFFFF4CC), Color(0xFF6B4E00), "Alert · no documented flood")
    else -> VerdictStyle(Color(0xFFE3EEF7), Color(0xFF0B3D66), "No alert")
}

/** What happened on the replayed day, against the flood catalogue. Sits at the bottom of the map. */
@Composable
fun ReplayVerdictCard(vm: ReplayViewModel, modifier: Modifier = Modifier) {
    val day by vm.day.collectAsState()
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 6.dp) {
        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (val d = day) {
                null, Load.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Muted("Replaying…")
                }
                is Load.Failed -> Text(d.message, color = FgColors.Danger, fontSize = 13.sp)
                is Load.Ready -> VerdictBody(d.value)
            }
            LevelLegend()
        }
    }
}

/** Collapsed replay: the day's verdict and the colour key in one strip, so the map stays visible. Tap for details. */
@Composable
fun ReplayVerdictChip(vm: ReplayViewModel, modifier: Modifier = Modifier) {
    val day by vm.day.collectAsState()
    Surface(
        onClick = { vm.setExpanded(true) }, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = Color.White, shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (val d = day) {
                is Load.Ready -> {
                    val style = verdictStyle(d.value.verdict)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(style.title, color = style.fg, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.weight(1f).background(style.bg, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp))
                        Icon(Icons.Default.ExpandLess, contentDescription = "Show replay details")
                    }
                }
                is Load.Failed -> Text(d.message, color = FgColors.Danger, fontSize = 13.sp)
                else -> Muted("Replaying…")
            }
            LevelLegend()
        }
    }
}

@Composable
private fun VerdictBody(d: ReplayDay) {
    val style = verdictStyle(d.verdict)
    Column(Modifier.fillMaxWidth().background(style.bg, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(formatDay(d.date), color = style.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(style.title, color = style.fg, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(d.verdictText, color = style.fg, fontSize = 13.sp)
    }
    d.event?.let { e ->
        Text("${e.eventId}: ${e.description}", fontSize = 13.sp)
        InfoRow("Alert the day before", if (e.alertedDayBefore) "Yes" else "No")
        e.firstAlertDate?.let { InfoRow("First alert", formatDay(it)) }
    }
    InfoRow("Wettest forecast nearby (24 h)", mm(d.wettestZone?.rainArea24h))
    InfoRow("Areas alerting", "${d.zonesAlerting} of ${d.zonesTotal}")
    val s = d.segmentsByLevel
    InfoRow("Road segments", "${s["watch"] ?: 0} watch · ${s["warning"] ?: 0} warning · ${s["severe"] ?: 0} severe")
    Text("Forecast: ${d.sourceNote} River data not included.", fontSize = 11.sp, color = FgColors.Muted)
}

@Composable
private fun LevelLegend() {
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0 to "None", 1 to "Watch", 2 to "Warning", 3 to "Severe", null to "No data").forEach { (lv, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 16.dp, height = 5.dp).background(levelColor(lv), RoundedCornerShape(3.dp)))
                Text(" $label", fontSize = 11.sp, softWrap = false)
            }
        }
    }
}

/** A tapped road during replay: the level it would have had that day, and why. */
@Composable
fun ReplayRoadSheet(pick: ReplayPick, day: ReplayDay?) {
    val tierName = mapOf("H" to "High", "M" to "Medium", "L" to "Low", "U" to "No data")[pick.tierCode]
    val level = pick.zoneId?.let { z -> day?.levels?.get(z)?.get(pick.tierCode ?: "U") }
    val zone = day?.zones?.firstOrNull { it.zoneId == pick.zoneId }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(pick.roadName ?: "Road segment", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        tierName?.let { Row(verticalAlignment = Alignment.CenterVertically) { Muted("Susceptibility  "); TierBadge(it) } }
        if (day == null || pick.zoneId == null) {
            Muted("Tap directly on a road to see its replayed level.")
            return@Column
        }
        val ls = levelStyle(levelName(level))
        Column(Modifier.fillMaxWidth().background(ls.bg, RoundedCornerShape(16.dp)).padding(16.dp)) {
            Text("Replayed alert · ${formatDay(day.date)}", color = ls.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(ls.title, color = ls.fg, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
        zone?.let { z ->
            InfoRow("Wettest forecast nearby (24 h)", mm(z.rainArea24h))
            InfoRow("Forecast here (24 h)", mm(z.rainNext24h))
            InfoRow("Past 72 hours", mm(z.rainPrev72h))
            z.reasons.forEach { Muted("• " + (REASON_TEXT[it] ?: it)) }
        }
        Muted("Rain sets the level; susceptibility can raise it but never removes an alert.")
        Text(day.caveats, fontSize = 12.sp, color = FgColors.Muted)
    }
}
