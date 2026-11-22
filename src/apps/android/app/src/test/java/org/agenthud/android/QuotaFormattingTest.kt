package org.agenthud.android

import org.agenthud.android.data.QuotaFormatting
import org.agenthud.android.data.QuotaWindowReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaFormattingTest {
    @Test
    fun cleansDuplicatedAndVerboseWindowLabels() {
        assertEquals(
            "Gemini 4 Argon · Weekly limit",
            QuotaFormatting.cleanWindowLabel("Gemini 4 Argon · Gemini 4 Argon Weekly Limit"),
        )
        assertEquals(
            "Gemini Models · 5-hour limit",
            QuotaFormatting.cleanWindowLabel("Gemini Models · Five-Hour Limit Remaining"),
        )
        assertEquals(
            "Weekly limit · All models",
            QuotaFormatting.cleanWindowLabel("Weekly Limit · All models"),
        )
    }

    @Test
    fun formatsTokensAndMoney() {
        assertEquals("4.10B", QuotaFormatting.formatTokens(4_100_000_000L))
        assertEquals("105M", QuotaFormatting.formatTokens(105_000_000L))
        assertEquals("8.5M", QuotaFormatting.formatTokens(8_500_000L))
        assertEquals("29K", QuotaFormatting.formatTokens(29_000L))
        assertEquals("$0.00", QuotaFormatting.formatMoney(0.0, "USD"))
        assertEquals("$7.05", QuotaFormatting.formatMoney(7.05, "USD"))
        assertEquals("$1732", QuotaFormatting.formatMoney(1732.27, "USD"))
    }

    @Test
    fun flagsCriticalQuotaLevelsWithoutYellow() {
        val healthy = QuotaWindowReading(windowID = "w1", remainingPercent = 45.0, level = "normal")
        val nearMax = QuotaWindowReading(windowID = "w2", remainingPercent = 5.0, level = "warning")
        val exhausted = QuotaWindowReading(windowID = "w3", remainingPercent = 0.0, outlook = "exhausted")
        assertFalse(QuotaFormatting.isCriticalLevel(healthy))
        assertTrue(QuotaFormatting.isCriticalLevel(nearMax))
        assertTrue(QuotaFormatting.isCriticalLevel(exhausted))
    }
}
