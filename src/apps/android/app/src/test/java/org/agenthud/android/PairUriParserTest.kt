package org.agenthud.android

import org.agenthud.android.data.PairUriParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class PairUriParserTest {
    @Test
    fun parsesValidAgentHudPairUri() {
        val fp = "a".repeat(64)
        val rawJson = """
            {
              "schemaVersion": 1,
              "deviceID": "dev-123",
              "deviceName": "Work PC",
              "endpoint": "https://192.168.1.42:49210/",
              "alternateEndpoints": ["https://100.64.0.9:49210/"],
              "certificateSHA256": "${fp.uppercase()}",
              "token": "inv-secret-token",
              "expiresAtMs": 1900000000000
            }
        """.trimIndent()
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(rawJson.toByteArray(StandardCharsets.UTF_8))
        val uri = "agenthud://pair?payload=$payload"

        val parsed = PairUriParser.parse(uri).getOrThrow()
        assertEquals("dev-123", parsed.deviceID)
        assertEquals("Work PC", parsed.deviceName)
        assertEquals("https://192.168.1.42:49210", parsed.endpoint)
        assertEquals(listOf("https://100.64.0.9:49210"), parsed.alternateEndpoints)
        assertEquals(fp, parsed.certificateSHA256)
        assertEquals("inv-secret-token", parsed.token)
    }

    @Test
    fun rejectsNonHttpsOrInvalidFingerprint() {
        val rawJson = """
            {
              "deviceID": "dev-1",
              "deviceName": "PC",
              "endpoint": "http://192.168.1.42:49210",
              "certificateSHA256": "deadbeef",
              "token": "abc"
            }
        """.trimIndent()
        val result = PairUriParser.parse(rawJson)
        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsWrongScheme() {
        val result = PairUriParser.parse("https://example.com/pair?payload=e30")
        assertTrue(result.isFailure)
    }
}
