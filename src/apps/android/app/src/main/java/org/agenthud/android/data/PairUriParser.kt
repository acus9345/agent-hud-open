package org.agenthud.android.data

import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

object PairUriParser {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses either an `agenthud://pair?payload=<base64url JSON>` invitation link
     * or a raw JSON invitation object.
     */
    fun parse(rawInput: String): Result<PairInvitation> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Pairing link is empty"))
        }
        return runCatching {
            val payloadJson = if (trimmed.startsWith("{")) {
                trimmed
            } else {
                extractPayloadJson(trimmed)
            }
            val invitation = json.decodeFromString<PairInvitation>(payloadJson)
            require(invitation.deviceID.isNotBlank()) { "Invitation missing deviceID" }
            require(invitation.endpoint.startsWith("https://")) { "Invitation endpoint must be https://" }
            require(invitation.certificateSHA256.length == 64 && invitation.certificateSHA256.all { it.isHexDigit() }) {
                "Invitation certificateSHA256 must be 64 hex characters"
            }
            require(invitation.token.isNotBlank()) { "Invitation missing token" }
            invitation.copy(
                endpoint = invitation.endpoint.trimEnd('/'),
                alternateEndpoints = invitation.alternateEndpoints.map { it.trimEnd('/') }.filter { it.startsWith("https://") },
                certificateSHA256 = invitation.certificateSHA256.lowercase(),
            )
        }
    }

    private fun extractPayloadJson(uriString: String): String {
        val uri = URI(uriString)
        require(uri.scheme.equals("agenthud", ignoreCase = true) && uri.host.equals("pair", ignoreCase = true)) {
            "Expected an agenthud://pair?payload=… link"
        }
        val query = uri.rawQuery ?: throw IllegalArgumentException("Missing query payload")
        val payloadParam = query.split('&')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
            }
            .firstOrNull { it.first == "payload" }
            ?.second
            ?: throw IllegalArgumentException("Missing payload parameter")

        val unescaped = URLDecoder.decode(payloadParam, StandardCharsets.UTF_8.name())
        val normalized = unescaped.replace('-', '+').replace('_', '/')
        val padded = when (normalized.length % 4) {
            2 -> "$normalized=="
            3 -> "$normalized="
            else -> normalized
        }
        val bytes = Base64.getDecoder().decode(padded)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
