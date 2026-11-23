package ke.floodguard.app

import ke.floodguard.app.data.RiskResponse
import ke.floodguard.app.ui.common.formatAge
import ke.floodguard.app.ui.common.levelName
import ke.floodguard.app.ui.common.levelStyle
import ke.floodguard.app.ui.common.mm
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattingTest {
    @Test
    fun missingLevelIsUnknownNeverSafe() {
        assertEquals("unknown", levelName(null))
        assertEquals("unknown", levelName(9))
        assertEquals("Unknown", levelStyle("unknown").title)
        assertEquals("Severe", levelStyle(levelName(3)).title)
    }

    @Test
    fun formatsAgesAndRain() {
        assertEquals("just now", formatAge(0.4))
        assertEquals("12 min ago", formatAge(12.2))
        assertEquals("3 h ago", formatAge(180.0))
        assertEquals("unknown", formatAge(null))
        assertEquals("4.4 mm", mm(4.4))
        assertEquals("—", mm(null))
    }

    @Test
    fun parsesBackendRiskResponse() {
        // Trimmed copy of a real /api/v1/risk response, with an extra field the app must ignore.
        val body = """{"data":{"state":"stale","issue_time":"2026-11-23T09:00:00Z","age_minutes":200.1,"run_status":"ok"},
            "zone_id":"WX_012","level":null,"level_name":"unknown","local_susceptibility":0.41,
            "local_susceptibility_tier":"Medium","distance_to_road_m":12.5,"nearby_roads":[{"road_name":"Mombasa Road",
            "susceptibility":0.7,"tier":"High"}],"weather":null,"disclaimer":"Decision support only.","new_field":1}"""
        val risk = Json { ignoreUnknownKeys = true }.decodeFromString<RiskResponse>(body)
        assertNull(risk.level)
        assertEquals("unknown", risk.levelName)
        assertEquals(false, risk.data.isFresh)
        assertEquals("High", risk.nearbyRoads.single().tier)
    }
}
