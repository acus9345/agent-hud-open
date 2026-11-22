package org.agenthud.android

import kotlinx.serialization.json.Json
import org.agenthud.android.data.ChangesResponse
import org.agenthud.android.data.DesktopBlockPrefs
import org.agenthud.android.data.DesktopPreferences
import org.agenthud.android.data.ProviderReading
import org.agenthud.android.data.QuotaWindowReading
import org.agenthud.android.data.SnapshotResponse
import org.agenthud.android.data.SyncStore
import org.agenthud.android.data.UsageRange
import org.agenthud.android.data.applyDesktopPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SyncEngineTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun parsesExactDesktopWireSnapshotWithoutRecordIdField() {
        val store = SyncStore()
        // Matches src/crates/share/src/record.rs (no `id` field, uses `modifiedAtMs`)
        val wireSnapshot = """
            {
              "schemaVersion": 1,
              "deviceID": "dev-1",
              "deviceName": "LAPTOP-33J49HQ8",
              "revision": "rev-1",
              "generatedAtMs": 1795329800000,
              "cursor": {"baseVersion": "2026-11-22T06:00:00.000Z", "changeSequence": 1},
              "records": [
                {
                  "schemaVersion": 4,
                  "kind": "readings",
                  "deviceID": "dev-1",
                  "key": "shared",
                  "modifiedAtMs": 1795329800000,
                  "payload": {
                    "names": {"claude": "Claude", "codex": "Codex"},
                    "providers": [
                      {
                        "provider": "claude",
                        "vendor": "claude",
                        "accountLabel": "pro@example.com",
                        "plan": "Pro",
                        "quotas": [
                          {"windowID": "5h", "windowName": "Current session", "remainingPercent": 62.0},
                          {"windowID": "7d", "windowName": "Weekly limit", "remainingPercent": 5.0, "level": "critical"}
                        ]
                      }
                    ]
                  }
                },
                {
                  "schemaVersion": 4,
                  "kind": "consumer",
                  "deviceID": "dev-1",
                  "key": "claude-model:opus-5.5",
                  "modifiedAtMs": 1795329800000,
                  "payload": {"vendor": "Claude Code", "model": "Opus 5.5"}
                },
                {
                  "schemaVersion": 4,
                  "kind": "usage",
                  "deviceID": "dev-1",
                  "key": "2026-11-21",
                  "modifiedAtMs": 1795329800000,
                  "payload": {
                    "day": "2026-11-21",
                    "tokens": [
                      {"startMs": 1000, "agentId": "claude-model:opus-5.5", "in": 50000, "out": 10000, "cacheRead": 200000, "listCost": {"USD": 1.25}}
                    ],
                    "costs": []
                  }
                },
                {
                  "schemaVersion": 4,
                  "kind": "usage",
                  "deviceID": "dev-1",
                  "key": "2026-11-20",
                  "modifiedAtMs": 1795329800000,
                  "payload": {"day": "2026-11-20", "tokens": [], "costs": []}
                }
              ]
            }
        """.trimIndent()

        val snapshot = json.decodeFromString<SnapshotResponse>(wireSnapshot)
        store.applySnapshot(snapshot, etag = "\"rev-1\"")
        assertEquals(4, store.recordCount())

        // Verify canonicalId matches Rust `record_id` for deletion
        val tempRecord = snapshot.records.last()
        val deletedId = tempRecord.canonicalId()
        assertTrue(deletedId.startsWith("v4."))

        val wireChanges = """
            {
              "schemaVersion": 1,
              "deviceID": "dev-1",
              "deviceName": "LAPTOP-33J49HQ8",
              "fromCursor": {"baseVersion": "2026-11-22T06:00:00.000Z", "changeSequence": 1},
              "toCursor": {"baseVersion": "2026-11-22T06:00:00.000Z", "changeSequence": 2},
              "revision": "rev-2",
              "generatedAtMs": 1795329810000,
              "records": [
                {
                  "schemaVersion": 4,
                  "kind": "usage",
                  "deviceID": "dev-1",
                  "key": "2026-11-22",
                  "modifiedAtMs": 1795329810000,
                  "payload": {
                    "day": "2026-11-22",
                    "tokens": [
                      {"startMs": 2000, "agentId": "codex-model:gpt-5", "in": 20000, "out": 5000, "cacheRead": 75000, "listCost": {"USD": 0.75}}
                    ],
                    "costs": []
                  }
                }
              ],
              "deletedIDs": ["$deletedId"]
            }
        """.trimIndent()

        store.applyChanges(json.decodeFromString<ChangesResponse>(wireChanges))
        assertEquals(4, store.recordCount())
        assertEquals("rev-2", store.revision)

        val today = LocalDate.of(2026, 11, 22)
        val summary7d = store.usageSummary(UsageRange.DAYS_7, today = today)
        assertEquals(360_000L, summary7d.totalTokens)
        assertEquals(2.0, summary7d.estimatedCostUsd, 0.001)
        assertEquals(2, summary7d.activeDays)
        assertEquals(2, summary7d.byAgent.size)
        assertEquals("claude", summary7d.byAgent.first().agentId)
        assertEquals("Claude Code", summary7d.byAgent.first().displayName)
        assertEquals("Opus 5.5", summary7d.byModel.first().model)

        val widget = store.buildWidgetSnapshot("LAPTOP-33J49HQ8", online = true, companion = null, nowMs = 1000L)
        assertEquals(1, widget.rows.size)
        assertEquals(95, widget.rows.first().usedPercent)
        assertTrue(widget.rows.first().critical)
    }

    @Test
    fun filtersAndOrdersProvidersAndLimitsFromDesktopPreferences() {
        val providers = listOf(
            ProviderReading(
                provider = "codex",
                quotas = listOf(QuotaWindowReading(windowID = "monthly", windowName = "Monthly limit", remainingPercent = 100.0)),
            ),
            ProviderReading(
                provider = "claude",
                quotas = listOf(
                    QuotaWindowReading(windowID = "5h", windowName = "Current session", remainingPercent = 90.0),
                    QuotaWindowReading(windowID = "7d", windowName = "Weekly limit", remainingPercent = 0.0),
                ),
            ),
            ProviderReading(
                provider = "hidden-agent",
                quotas = listOf(QuotaWindowReading(windowID = "w1", windowName = "Limit", remainingPercent = 50.0)),
            ),
        )
        val prefs = DesktopPreferences(
            order = listOf("claude", "codex"),
            hudAgents = listOf("claude", "openai"),
            blocks = mapOf(
                "claude" to DesktopBlockPrefs(hiddenRows = listOf("5h")),
            ),
        )
        val result = applyDesktopPreferences(providers, prefs)
        assertEquals(listOf("claude", "codex"), result.map { it.provider })
        assertEquals(listOf("7d"), result.first().quotas.map { it.windowID })
    }
}
