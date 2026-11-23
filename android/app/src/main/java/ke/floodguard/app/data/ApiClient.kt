package ke.floodguard.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val status: Int) : Exception(message)

/** Typed client for the FloodGuard backend. Every call is main-safe. */
class ApiClient(baseUrl: String, cacheDir: File) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // The HTTP cache lets the 2 MB road map revalidate with its ETag instead of downloading again.
    private val http = OkHttpClient.Builder()
        .cache(Cache(File(cacheDir, "http"), 30L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun status(): StatusResponse = get("api/v1/status")
    suspend fun zones(): ZonesResponse = get("api/v1/zones")
    suspend fun risk(lat: Double, lon: Double): RiskResponse =
        get("api/v1/risk", "latitude" to lat.toString(), "longitude" to lon.toString())
    suspend fun searchRoads(q: String): RoadSearchResponse = get("api/v1/roads/search", "q" to q, "limit" to "15")
    suspend fun roadsAtRisk(minLevel: Int = 1): RoadsAtRiskResponse =
        get("api/v1/roads/at-risk", "min_level" to minLevel.toString(), "limit" to "100")
    suspend fun officialAlerts(): OfficialAlertsResponse = get("api/v1/alerts/official")

    suspend fun subscribe(body: SubscribeRequest): SubscribeResponse = send("POST", "api/v1/subscribers", body)
    suspend fun verify(phone: String, code: String): VerifyResponse =
        send("POST", "api/v1/subscribers/verify", VerifyRequest(phone, code))
    suspend fun me(token: String): Subscriber = get("api/v1/subscribers/me", token = token)
    suspend fun updateMe(token: String, changes: SubscriberUpdate): Subscriber =
        send("PATCH", "api/v1/subscribers/me", changes, token)
    suspend fun myAlerts(token: String): MyAlertsResponse = get("api/v1/subscribers/me/alerts", token = token)
    suspend fun deleteMe(token: String) {
        call(request("api/v1/subscribers/me", token = token).delete().build())
    }

    /**
     * Downloads the road network GeoJSON (17 MB unzipped) straight to [dest], streaming so it never sits in the
     * Java heap. Sends the saved ETag, so an unchanged map costs one small 304. Returns [dest].
     */
    suspend fun downloadRoadMap(dest: File): File = withContext(Dispatchers.IO) {
        val etagFile = File(dest.path + ".etag")
        val builder = request("api/v1/map/roads").header("Accept", "application/geo+json")
        if (dest.exists() && etagFile.exists()) builder.header("If-None-Match", etagFile.readText())
        try {
            noCacheHttp.newCall(builder.build()).execute().use { response ->
                if (response.code == 304) return@withContext dest
                if (!response.isSuccessful) {
                    throw ApiException(detailMessage(response.body?.string().orEmpty(), response.code), response.code)
                }
                val tmp = File(dest.path + ".part")
                tmp.outputStream().use { out -> response.body!!.byteStream().copyTo(out, 64 * 1024) }
                if (!tmp.renameTo(dest)) {
                    dest.delete()
                    tmp.renameTo(dest)
                }
                response.header("ETag")?.let { etagFile.writeText(it) } ?: etagFile.delete()
                dest
            }
        } catch (e: IOException) {
            android.util.Log.w("FloodGuard", "Road map download failed", e)
            throw ApiException("Could not download the road map. Check your connection.", 0)
        }
    }

    // The road map manages its own file and ETag, so it skips the HTTP cache rather than storing 17 MB twice.
    private val noCacheHttp by lazy { http.newBuilder().cache(null).readTimeout(120, TimeUnit.SECONDS).build() }

    private suspend inline fun <reified T> get(path: String, vararg query: Pair<String, String>, token: String? = null): T =
        json.decodeFromString(call(request(path, *query, token = token).get().build()))

    private suspend inline fun <reified B, reified T> send(method: String, path: String, body: B, token: String? = null): T {
        val payload = json.encodeToString(body).toRequestBody("application/json".toMediaType())
        return json.decodeFromString(call(request(path, token = token).method(method, payload).build()))
    }

    private fun request(path: String, vararg query: Pair<String, String>, token: String? = null): Request.Builder {
        val url = base.newBuilder().addPathSegments(path).apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        return Request.Builder().url(url).header("Accept", "application/json").apply {
            if (token != null) header("Authorization", "Bearer $token")
        }
    }

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw ApiException(detailMessage(text, it.code), it.code)
                text
            }
        } catch (e: IOException) {      // includes a connection dropped mid-body
            throw ApiException("Could not reach FloodGuard. Check your connection.", 0)
        }
    }

    /** FastAPI sends `detail` as a string for HTTPException and as a list for validation errors. */
    private fun detailMessage(body: String, status: Int): String {
        val detail = runCatching { json.parseToJsonElement(body).jsonObject["detail"] }.getOrNull()
        return when (detail) {
            is JsonPrimitive -> detail.content
            is JsonArray -> (detail.firstOrNull() as? JsonObject)?.let { first ->
                val msg = first["msg"]?.jsonPrimitive?.content ?: "Invalid request."
                val field = (first["loc"] as? JsonArray)?.lastOrNull()?.jsonPrimitive?.content
                if (field != null) "$field: $msg" else msg
            } ?: "Invalid request."
            else -> if (status >= 500) "The FloodGuard server had a problem. Try again shortly." else "Request failed ($status)."
        }
    }
}
