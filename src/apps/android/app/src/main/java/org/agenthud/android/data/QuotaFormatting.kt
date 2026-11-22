package org.agenthud.android.data

import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object QuotaFormatting {
    /**
     * Normalizes quota window titles the same way desktop `quota-row-format.ts` does:
     * strips trailing "Remaining", collapses duplicated "Group · Group ..." prefixes,
     * and converts "Five Hour" -> "5-hour".
     */
    fun cleanWindowLabel(rawName: String, fallbackShort: String = ""): String {
        val base = rawName.ifBlank { fallbackShort }.trim()
        if (base.isEmpty()) return "Limit"
        var text = base
            .replace(Regex("\\s+Remaining\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("Five[- ]Hour", RegexOption.IGNORE_CASE), "5-hour")
            .replace(Regex("\\s+Limit\\b"), " limit")
            .trim()
        val parts = text.split("·").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size == 2) {
            val prefix = parts[0]
            var suffix = parts[1]
            if (suffix.startsWith(prefix, ignoreCase = true) && suffix.length > prefix.length) {
                val remainder = suffix.substring(prefix.length).trimStart(' ', '-', ':', '·')
                if (remainder.isNotEmpty()) {
                    suffix = remainder.replaceFirstChar { it.uppercase() }
                }
            }
            text = "$prefix · $suffix"
        }
        return text
    }

    fun formatResetTime(resetsAtMs: Long?, nowMs: Long = System.currentTimeMillis()): String {
        if (resetsAtMs == null || resetsAtMs <= 0L) return ""
        val deltaMs = resetsAtMs - nowMs
        if (deltaMs <= 0L) return "Resets now"
        val minutes = deltaMs / 60_000L
        return when {
            minutes < 60L -> "Resets in ${minutes.coerceAtLeast(1)}m"
            minutes < 24L * 60L -> {
                val h = minutes / 60L
                val m = minutes % 60L
                if (m == 0L) "Resets in ${h}h" else "Resets in ${h}h ${m}m"
            }
            minutes < 7L * 24L * 60L -> {
                val fmt = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
                "Resets ${fmt.format(Date(resetsAtMs))}"
            }
            else -> {
                val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                "Resets ${fmt.format(Date(resetsAtMs))}"
            }
        }
    }

    fun formatTokens(count: Long): String {
        val absVal = abs(count)
        return when {
            absVal >= 1_000_000_000L -> String.format(Locale.US, "%.2fB", count / 1_000_000_000.0)
            absVal >= 100_000_000L -> String.format(Locale.US, "%.0fM", count / 1_000_000.0)
            absVal >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            absVal >= 10_000L -> String.format(Locale.US, "%.0fK", count / 1_000.0)
            absVal >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    fun formatMoney(amount: Double, currency: String = "USD"): String {
        val symbol = when (currency.uppercase(Locale.US)) {
            "USD" -> "$"
            "CNY", "RMB" -> "¥"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> "$currency "
        }
        return when {
            amount >= 100.0 -> String.format(Locale.US, "%s%.0f", symbol, amount)
            amount >= 10.0 -> String.format(Locale.US, "%s%.1f", symbol, amount)
            else -> String.format(Locale.US, "%s%.2f", symbol, amount)
        }
    }

    fun formatRelativeTime(isoOrEpoch: String?, nowMs: Long = System.currentTimeMillis()): String {
        if (isoOrEpoch.isNullOrBlank()) return ""
        val ts = runCatching { Instant.parse(isoOrEpoch).toEpochMilli() }.getOrNull() ?: return ""
        return formatRelativeMs(ts, nowMs)
    }

    fun formatRelativeMs(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (timestampMs <= 0L) return ""
        val deltaSec = ((nowMs - timestampMs) / 1000L).coerceAtLeast(0L)
        return when {
            deltaSec < 45L -> "just now"
            deltaSec < 3600L -> "${deltaSec / 60}m ago"
            deltaSec < 86_400L -> "${deltaSec / 3600}h ago"
            else -> "${deltaSec / 86_400}d ago"
        }
    }

    fun providerDisplayName(id: String, names: Map<String, String> = emptyMap()): String {
        names[id]?.takeIf { it.isNotBlank() }?.let { return it }
        return when (id.lowercase(Locale.US)) {
            "claude" -> "Claude"
            "codex", "openai" -> "Codex"
            "antigravity" -> "Antigravity"
            "deepseek" -> "DeepSeek"
            "grok-bot", "grok" -> "Grok Bot"
            "cursor" -> "Cursor"
            "copilot" -> "Copilot"
            "zcode" -> "ZCode"
            "glm" -> "GLM"
            "cline" -> "Cline"
            "pi" -> "Pi"
            "opencode" -> "OpenCode"
            else -> id.replaceFirstChar { it.uppercase() }
        }
    }

    fun formatOutlook(window: QuotaWindowReading, nowMs: Long = System.currentTimeMillis()): String? {
        return when (window.outlook) {
            "exhausted" -> "Exhausted"
            "exhausts" -> {
                val at = window.exhaustsAtMs ?: return null
                val mins = ((at - nowMs) / 60_000L).coerceAtLeast(1L)
                if (mins < 60L) "Runs out in ~${mins}m"
                else "Runs out in ~${mins / 60}h ${mins % 60}m"
            }
            "outlastsReset" -> {
                val pct = window.usedAtResetPercent?.roundToInt() ?: return "On track"
                "~${pct}% by reset"
            }
            else -> window.burnRatePercentPerHour?.takeIf { it > 0.1 }?.let {
                String.format(Locale.US, "%.1f%%/h", it)
            }
        }
    }

    fun isCriticalLevel(window: QuotaWindowReading): Boolean =
        window.level == "critical" || window.usedPercent >= 90.0 || window.outlook == "exhausted"
}
