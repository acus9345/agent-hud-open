package org.agenthud.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class LanAuthException(message: String) : Exception(message)
class LanCursorExpiredException(message: String) : Exception(message)
class LanNotFoundException(message: String) : Exception(message)
class LanHttpException(val statusCode: Int, message: String) : Exception(message)

sealed class SnapshotResult {
    data object NotModified : SnapshotResult()
    data class Loaded(
        val snapshot: SnapshotResponse,
        val etag: String?,
        val activeEndpoint: String,
    ) : SnapshotResult()
}

class LanClient {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val clientCache = ConcurrentHashMap<String, OkHttpClient>()

    private fun httpClientFor(
        certSha256: String,
        readTimeoutSeconds: Long = 20L,
        fallback: Pair<String, String>? = null,
    ): OkHttpClient {
        val fbKey = fallback?.let { "${it.first.lowercase()}=${it.second}" }.orEmpty()
        val key = "${certSha256.lowercase()}:$readTimeoutSeconds:$fbKey"
        return clientCache.computeIfAbsent(key) {
            PinnedTls.clientFor(certSha256, readTimeoutSeconds, fallback)
        }
    }

    private fun deviceFallback(device: PairedDevice): Pair<String, String>? {
        val ip = device.remoteFallbackIp?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val url = device.remoteEndpoint?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) }
            ?: device.alternateEndpoints.firstOrNull { it.startsWith("https://", ignoreCase = true) }
            ?: return null
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return host to ip
    }

    suspend fun pair(invitation: PairInvitation, clientId: String): Pair<PairResponse, String> =
        withContext(Dispatchers.IO) {
            val endpoints = LinkedHashSet<String>().apply {
                add(invitation.endpoint.trimEnd('/'))
                invitation.alternateEndpoints.forEach { if (it.isNotBlank()) add(it.trimEnd('/')) }
            }.toList()
            val bodyJson = json.encodeToString(PairRequestBody(token = invitation.token, clientID = clientId))
            val client = httpClientFor(invitation.certificateSHA256)

            var lastIo: IOException? = null
            for (baseUrl in endpoints) {
                val req = Request.Builder()
                    .url("$baseUrl/v1/pair")
                    .header(HEADER_CLIENT_VERSION, CLIENT_VERSION)
                    .header("Accept", "application/json")
                    .post(bodyJson.toRequestBody(JSON_MEDIA))
                    .build()
                try {
                    client.newCall(req).execute().use { resp ->
                        val raw = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            throw mapHttpError(resp.code, raw)
                        }
                        val parsed = json.decodeFromString<PairResponse>(raw)
                        return@withContext parsed to baseUrl
                    }
                } catch (e: IOException) {
                    lastIo = e
                }
            }
            throw lastIo ?: IOException("Could not reach ${invitation.deviceName} on local network")
        }

    suspend fun fetchSnapshot(device: PairedDevice, etag: String?): SnapshotResult =
        withContext(Dispatchers.IO) {
            executeWithFailover(device) { baseUrl, client ->
                val builder = authorizedRequest("$baseUrl/v1/snapshot", device.token)
                    .get()
                if (!etag.isNullOrBlank()) {
                    builder.header("If-None-Match", etag)
                }
                client.newCall(builder.build()).execute().use { resp ->
                    if (resp.code == 304) return@executeWithFailover SnapshotResult.NotModified
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw mapHttpError(resp.code, raw)
                    val parsed = json.decodeFromString<SnapshotResponse>(raw)
                    SnapshotResult.Loaded(
                        snapshot = parsed,
                        etag = resp.header("ETag"),
                        activeEndpoint = baseUrl,
                    )
                }
            }.first
        }

    suspend fun fetchChanges(device: PairedDevice, since: SyncCursor): Pair<ChangesResponse, String> =
        withContext(Dispatchers.IO) {
            val encodedSince = URLEncoder.encode(since.asQueryValue(), StandardCharsets.UTF_8.name())
            executeWithFailover(device) { baseUrl, client ->
                val req = authorizedRequest("$baseUrl/v1/changes?since=$encodedSince", device.token)
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw mapHttpError(resp.code, raw)
                    json.decodeFromString<ChangesResponse>(raw)
                }
            }
        }

    fun openEventStream(
        device: PairedDevice,
        since: SyncCursor?,
        listener: EventSourceListener,
    ): EventSource {
        val baseUrl = device.candidateEndpoints().first()
        val url = if (since != null) {
            val encoded = URLEncoder.encode(since.asQueryValue(), StandardCharsets.UTF_8.name())
            "$baseUrl/v1/events?since=$encoded"
        } else {
            "$baseUrl/v1/events"
        }
        val req = authorizedRequest(url, device.token, sse = true)
            .header("Accept", "text/event-stream")
            .header("X-AgentHUD-Stream", "changes")
            .get()
            .build()
        val sseClient = httpClientFor(device.certificateSha256, readTimeoutSeconds = 0L, fallback = deviceFallback(device))
        return EventSources.createFactory(sseClient).newEventSource(req, listener)
    }

    suspend fun fetchCompanionState(device: PairedDevice): Pair<CompanionStateResponse, String> =
        withContext(Dispatchers.IO) {
            executeWithFailover(device) { baseUrl, client ->
                val req = authorizedRequest("$baseUrl/v1/companion/state", device.token)
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw mapHttpError(resp.code, raw)
                    json.decodeFromString<CompanionStateResponse>(raw)
                }
            }
        }

    suspend fun fetchSessionDetail(
        device: PairedDevice,
        sessionId: String,
        messageLimit: Int = 50,
        callLimit: Int = 20,
    ): SessionDetailResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("id", sessionId)
            put("messageLimit", messageLimit)
            put("callLimit", callLimit)
        }.toString()
        executeWithFailover(device) { baseUrl, client ->
            val req = authorizedRequest("$baseUrl/v1/companion/session-detail", device.token)
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw mapHttpError(resp.code, raw)
                json.decodeFromString<SessionDetailResponse>(raw)
            }
        }.first
    }

    suspend fun answerApproval(
        device: PairedDevice,
        approvalId: String,
        decision: JsonObject,
    ): Unit = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("id", approvalId)
            put("decision", decision)
        }.toString()
        executeWithFailover(device) { baseUrl, client ->
            val req = authorizedRequest("$baseUrl/v1/companion/approval", device.token)
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw mapHttpError(resp.code, raw)
            }
        }
    }

    suspend fun sendToSession(
        device: PairedDevice,
        clientName: String,
        sessionId: String,
        text: String,
    ): SendDeliveryResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("kind", "session")
            put("client", clientName)
            put("sessionId", sessionId)
            put("text", text)
        }.toString()
        executeWithFailover(device) { baseUrl, client ->
            val req = authorizedRequest("$baseUrl/v1/companion/send", device.token)
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw mapHttpError(resp.code, raw)
                json.decodeFromString<SendDeliveryResponse>(raw)
            }
        }.first
    }

    suspend fun startNewSession(
        device: PairedDevice,
        clientName: String,
        folder: String,
        text: String,
    ): SendDeliveryResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("kind", "new")
            put("client", clientName)
            put("folder", folder)
            put("text", text)
        }.toString()
        executeWithFailover(device) { baseUrl, client ->
            val req = authorizedRequest("$baseUrl/v1/companion/send", device.token)
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw mapHttpError(resp.code, raw)
                json.decodeFromString<SendDeliveryResponse>(raw)
            }
        }.first
    }

    suspend fun uploadFile(
        device: PairedDevice,
        filename: String,
        bytes: ByteArray,
    ): UploadChunkResponse = withContext(Dispatchers.IO) {
        require(bytes.size <= 20 * 1024 * 1024) { "File exceeds 20 MB limit" }
        val chunkSize = 512 * 1024
        val totalChunks = ((bytes.size + chunkSize - 1) / chunkSize).coerceAtLeast(1)
        val uploadId = "and-${java.util.UUID.randomUUID().toString().replace("-", "").take(16)}"
        var lastResponse = UploadChunkResponse()

        for (index in 0 until totalChunks) {
            val start = index * chunkSize
            val end = minOf(start + chunkSize, bytes.size)
            val slice = if (bytes.isEmpty()) ByteArray(0) else bytes.copyOfRange(start, end)
            val encoded = java.util.Base64.getEncoder().encodeToString(slice)
            val body = buildJsonObject {
                put("uploadId", uploadId)
                put("filename", filename)
                put("chunkIndex", index)
                put("totalChunks", totalChunks)
                put("dataBase64", encoded)
            }.toString()

            lastResponse = executeWithFailover(device) { baseUrl, client ->
                val req = authorizedRequest("$baseUrl/v1/companion/upload", device.token)
                    .post(body.toRequestBody(JSON_MEDIA))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw mapHttpError(resp.code, raw)
                    json.decodeFromString<UploadChunkResponse>(raw)
                }
            }.first
        }
        lastResponse
    }

    private inline fun <T> executeWithFailover(
        device: PairedDevice,
        block: (baseUrl: String, client: OkHttpClient) -> T,
    ): Pair<T, String> {
        val client = httpClientFor(device.certificateSha256, fallback = deviceFallback(device))
        var lastIo: IOException? = null
        for (baseUrl in device.candidateEndpoints()) {
            // Retry once on transient socket close (e.g. mobile/relay idle drop)
            for (attempt in 0..1) {
                try {
                    return block(baseUrl, client) to baseUrl
                } catch (e: IOException) {
                    lastIo = e
                }
            }
        }
        throw lastIo ?: IOException("Unable to reach ${device.deviceName}")
    }

    private fun authorizedRequest(url: String, token: String, sse: Boolean = false): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header(HEADER_CLIENT_VERSION, CLIENT_VERSION)
            .header("Accept", "application/json")
        if (!sse) {
            builder.header("Connection", "close")
        }
        return builder
    }

    private fun mapHttpError(code: Int, rawBody: String): Exception {
        val detail = extractErrorMessage(rawBody) ?: "HTTP $code"
        return when (code) {
            401, 403 -> LanAuthException(detail)
            404 -> LanNotFoundException(detail)
            410 -> LanCursorExpiredException(detail)
            else -> LanHttpException(code, detail)
        }
    }

    private fun extractErrorMessage(rawBody: String): String? {
        if (rawBody.isBlank()) return null
        return runCatching {
            val obj = json.parseToJsonElement(rawBody).jsonObject
            obj["error"]?.jsonPrimitive?.content ?: obj["message"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    companion object {
        private const val HEADER_CLIENT_VERSION = "X-AgentHUD-Client-Version"
        private const val CLIENT_VERSION = "0.6.11-android (2)"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
