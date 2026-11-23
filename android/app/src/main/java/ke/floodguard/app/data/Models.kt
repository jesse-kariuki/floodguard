package ke.floodguard.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Shapes mirror backend/app/api/*.py. Unknown fields are ignored, so the backend can add fields safely.

@Serializable
data class DataStatus(
    val state: String,                       // fresh | stale | no_data
    @SerialName("issue_time") val issueTime: String? = null,
    @SerialName("age_minutes") val ageMinutes: Double? = null,
    @SerialName("run_status") val runStatus: String? = null,
) {
    val isFresh get() = state == "fresh"
}

@Serializable
data class ZoneWeather(
    @SerialName("zone_id") val zoneId: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val available: Boolean,
    @SerialName("rain_band") val rainBand: Int,
    @SerialName("rain_band_name") val rainBandName: String,
    @SerialName("rain_next_6h_mm") val rainNext6h: Double? = null,
    @SerialName("rain_next_24h_mm") val rainNext24h: Double? = null,
    @SerialName("rain_area_24h_mm") val rainArea24h: Double? = null,
    @SerialName("rain_prev_72h_mm") val rainPrev72h: Double? = null,
    @SerialName("max_hourly_mm") val maxHourly: Double? = null,
    @SerialName("precip_probability_max") val precipProbabilityMax: Double? = null,
    val reasons: List<String> = emptyList(),
)

@Serializable
data class ZonesResponse(val data: DataStatus, val zones: List<ZoneWeather>)

@Serializable
data class NearbyRoad(
    @SerialName("road_name") val roadName: String,
    val susceptibility: Double? = null,
    val tier: String,
)

@Serializable
data class RiskResponse(
    val data: DataStatus,
    @SerialName("zone_id") val zoneId: String,
    val level: Int? = null,
    @SerialName("level_name") val levelName: String,     // none | watch | warning | severe | unknown
    @SerialName("local_susceptibility") val localSusceptibility: Double? = null,
    @SerialName("local_susceptibility_tier") val tier: String,
    @SerialName("distance_to_road_m") val distanceToRoadM: Double,
    @SerialName("nearby_roads") val nearbyRoads: List<NearbyRoad> = emptyList(),
    val weather: ZoneWeather? = null,
    val disclaimer: String,
)

@Serializable
data class RoadSearchResult(
    @SerialName("road_name") val roadName: String,
    val latitude: Double,
    val longitude: Double,
    val segments: Int,
    @SerialName("high_susceptibility_segments") val highSegments: Int,
)

@Serializable
data class RoadSearchResponse(val results: List<RoadSearchResult>)

@Serializable
data class RoadAtRisk(
    @SerialName("road_name") val roadName: String,
    val level: Int,
    val segments: Int,
    val zones: List<String> = emptyList(),
)

@Serializable
data class RoadsAtRiskResponse(val data: DataStatus, val roads: List<RoadAtRisk>)

@Serializable
data class KmdAlert(
    val identifier: String,
    val event: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    val severity: String? = null,
    val area: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val url: String? = null,
    @SerialName("covers_nairobi") val coversNairobi: Boolean = false,
)

@Serializable
data class GdacsEvent(
    @SerialName("event_id") val eventId: JsonElement? = null,
    @SerialName("alert_level") val alertLevel: String? = null,
    val name: String? = null,
    val from: String? = null,
    val to: String? = null,
    val url: String? = null,
)

@Serializable
data class OfficialAlertsResponse(
    @SerialName("kmd_alerts") val kmdAlerts: List<KmdAlert> = emptyList(),
    @SerialName("gdacs_events") val gdacsEvents: List<GdacsEvent> = emptyList(),
)

@Serializable
data class Subscriber(
    val phone: String,
    val status: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("place_label") val placeLabel: String? = null,
    @SerialName("zone_id") val zoneId: String,
    @SerialName("local_susceptibility_tier") val tier: String,
    val language: String,
    @SerialName("min_level") val minLevel: Int,
    @SerialName("last_alert_level") val lastAlertLevel: Int? = null,
    @SerialName("last_alert_at") val lastAlertAt: String? = null,
)

@Serializable
data class SentAlert(val kind: String, val level: Int? = null, val message: String, @SerialName("sent_at") val sentAt: String)

@Serializable
data class MyAlertsResponse(val alerts: List<SentAlert>)

@Serializable
data class SubscribeRequest(
    val phone: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("place_label") val placeLabel: String?,
    val language: String,
    @SerialName("min_level") val minLevel: Int,
    val consent: Boolean,
)

@Serializable
data class SubscribeResponse(val status: String, val phone: String, @SerialName("expires_in_seconds") val expiresInSeconds: Int)

@Serializable
data class VerifyRequest(val phone: String, val code: String)

@Serializable
data class VerifyResponse(val token: String, val subscriber: Subscriber)

@Serializable
data class SubscriberUpdate(
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("place_label") val placeLabel: String? = null,
    val language: String? = null,
    @SerialName("min_level") val minLevel: Int? = null,
)

@Serializable
data class LastCycle(@SerialName("source_errors") val sourceErrors: Map<String, String> = emptyMap())

@Serializable
data class StatusResponse(val data: DataStatus, @SerialName("last_cycle") val lastCycle: LastCycle? = null)

/** A place the user picked: from GPS, a road search, or a tap on the map. */
data class Place(val latitude: Double, val longitude: Double, val label: String?)
