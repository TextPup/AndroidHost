package io.loperlabs.textpup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class CompanionAppTest {

    @Test
    fun testQrPayloadParsing() {
        val testPayload = "textpup://pair?server=http%3A%2F%2F192.168.1.100%3A8080&token=sec_abc123xyz"
        assertTrue(testPayload.startsWith("textpup://pair"))

        val uri = URI(testPayload)
        val queryParams = uri.rawQuery.split("&").associate {
            val parts = it.split("=")
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
        }

        assertEquals("http://192.168.1.100:8080", queryParams["server"])
        assertEquals("sec_abc123xyz", queryParams["token"])
    }

    @Test
    fun testServerUrlNormalizing() {
        val rawHttp = "http://192.168.1.50:8080"
        val wsUrl = rawHttp.replace("http://", "ws://").replace("https://", "wss://")
        val finalUrl = if (!wsUrl.endsWith("/api/gateway/connect")) {
            wsUrl.trimEnd('/') + "/api/gateway/connect"
        } else {
            wsUrl
        }

        assertEquals("ws://192.168.1.50:8080/api/gateway/connect", finalUrl)
    }

    @Test
    fun testSmsExtractionFormatting() {
        val cleanPhone = "+1 (555) 234-5678".replace(Regex("[^0-9+]"), "")
        assertEquals("+15552345678", cleanPhone)
    }
}
