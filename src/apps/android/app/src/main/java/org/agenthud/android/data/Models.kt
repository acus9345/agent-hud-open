package org.agenthud.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class PairInvitation(
    val schemaVersion: Int = 1,
    val deviceID: String,
    val deviceName: String,
    val endpoint: String,
    val alternateEndpoints: List<String> = emptyList(),
    val certificateSHA256: String,
    val token: String,
    val expiresAtMs: Long = 0L,
)

@Serializable
data class PairRequestBody(
    val token: String,
    val clientID: String,
)

@Serializable
data class PairResponse(
    val deviceID: String,
    val deviceName: String,
    val endpoint: String,
    val alternateEndpoints: List<String> = emptyList(),
    val certificateSHA256: String,
    val token: String,
)

@Serializable
data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val endpoint: String,
    val alternateEndpoints: List<String> = emptyList(),
    val certificateSha256: String,
    val token: String,
    val pairedAtMs: Long,
    val lastActiveEndpoint: String? = null,
    val remoteMode: String = "tailscale", // "lan" | "tailscale" | "relay"
    val remoteEndpoint: String? = null,
    val remoteFallbackIp: String? = null,
) {
    fun candidateEndpoints(): List<String> {
        val out = LinkedHashSet<String>()
        if (!lastActiveEndpoint.isNullOrBlank()) out.add(lastActiveEndpoint.trimEnd('/'))
        out.add(endpoint.trimEnd('/'))
        if (remoteMode != "lan") {
            alternateEndpoints.forEach { if (it.isNotBlank()) out.add(it.trimEnd('/')) }
            if (!remoteEndpoint.isNullOrBlank()) out.add(remoteEndpoint.trimEnd('/'))
        }
        return out.toList()
    }
}

@Serializable
data class SyncCursor(
    val baseVersion: String,
    val changeSequence: Long,
) {
    fun asQueryValue(): String = "$baseVersion,$changeSequence"
}

@Serializable
data class SyncRecord(
    val id: String? = null,
    val schemaVersion: Int = 4,
    val kind: String,
    val deviceID: String,
    val key: String,
    val payload: JsonObject,
    val modifiedAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val expiresAtMs: Long? = null,
) {
    /**
     * Computes the exact canonical record ID used by `src/crates/share/src/record.rs`
     * (`v{schemaVersion}.{sha256(len:kind + len:deviceID + len:key)}`) when the wire
     * JSON omits an explicit `id` field, so `deletedIDs` from `/v1/changes` match.
     */
    fun canonicalId(): String {
        if (!id.isNullOrBlank()) return id
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for (component in arrayOf(kind, deviceID, key)) {
            val bytes = component.toByteArray(Charsets.UTF_8)
            digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8))
            digest.update(bytes)
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return "v$schemaVersion.$hex"
    }
}

@Serializable
data class SnapshotResponse(
    val schemaVersion: Int = 1,
    val deviceID: String,
    val deviceName: String,
    val revision: String,
    val generatedAtMs: Long = 0L,
    val records: List<SyncRecord> = emptyList(),
    val cursor: SyncCursor? = null,
)

@Serializable
data class ChangesResponse(
    val schemaVersion: Int = 1,
    val deviceID: String,
    val deviceName: String,
    val fromCursor: SyncCursor? = null,
    val toCursor: SyncCursor? = null,
    val revision: String = "",
    val generatedAtMs: Long = 0L,
    val records: List<SyncRecord> = emptyList(),
    val deletedIDs: List<String> = emptyList(),
)

@Serializable
data class RevisionEventPayload(
    val revision: String = "",
    val cursor: SyncCursor? = null,
)

// ---- Readings record payload ----

@Serializable
data class ReadingsPayload(
    val providers: List<ProviderReading> = emptyList(),
    val names: Map<String, String> = emptyMap(),
)

@Serializable
data class ProviderReading(
    val provider: String,
    val vendor: String = "",
    val accountID: String? = null,
    val accountKey: String? = null,
    val accountLabel: String = "",
    val plan: String? = null,
    val billingID: String? = null,
    val costsComplete: Boolean = true,
    val quotas: List<QuotaWindowReading> = emptyList(),
    val balances: List<BalanceReading> = emptyList(),
    val resetCredits: ResetCreditsReading? = null,
)

@Serializable
data class QuotaWindowReading(
    val windowID: String,
    val windowName: String = "",
    val windowShortName: String = "",
    val allModels: Boolean = false,
    val status: String = "normal",
    val remainingPercent: Double = 100.0,
    val observedAtMs: Long = 0L,
    val windowSeconds: Long? = null,
    val resetsAtMs: Long? = null,
    val level: String? = null,
    val burnRatePercentPerHour: Double? = null,
    val tokensPerHour: Double? = null,
    val outlook: String? = null,
    val exhaustsAtMs: Long? = null,
    val usedAtResetPercent: Double? = null,
) {
    val usedPercent: Double
        get() = (100.0 - remainingPercent).coerceIn(0.0, 100.0)
}

@Serializable
data class BalanceReading(
    val currency: String,
    val amount: String,
    val observedAtMs: Long = 0L,
    val status: String = "normal",
    val level: String? = null,
    val runsOutAtMs: Long? = null,
)

@Serializable
data class ResetCreditsReading(
    val availableCount: Long = 0L,
    val observedAtMs: Long = 0L,
    val status: String = "normal",
)

// ---- Usage & Consumer record payloads ----

@Serializable
data class UsageDayPayload(
    val day: String,
    val tokens: List<UsageTokenBucket> = emptyList(),
    val costs: List<UsageCostBucket> = emptyList(),
)

@Serializable
data class UsageTokenBucket(
    val startMs: Long = 0L,
    val agentId: String = "",
    @SerialName("in") val inputAndWrite: Long = 0L,
    @SerialName("out") val outputAndReasoning: Long = 0L,
    val cacheRead: Long = 0L,
    val cacheWrite: Long = 0L,
    val reasoning: Long = 0L,
    val account: String? = null,
    val listCost: Map<String, JsonElement>? = null,
) {
    val totalTokens: Long
        get() = inputAndWrite + outputAndReasoning + cacheRead
}

@Serializable
data class UsageCostBucket(
    val startMs: Long = 0L,
    val billingID: String = "",
    val amounts: Map<String, String> = emptyMap(),
)

@Serializable
data class ConsumerPayload(
    val vendor: String = "",
    val model: String = "",
)

// ---- Companion extension payloads (/v1/companion/*) ----

@Serializable
data class DesktopBlockPrefs(
    val hidden: Boolean = false,
    val hiddenRows: List<String> = emptyList(),
    val rows: List<String> = emptyList(),
    val hiddenAccounts: List<String> = emptyList(),
    val accounts: List<String> = emptyList(),
)

@Serializable
data class DesktopPreferences(
    val quotaDisplay: String = "consumed", // "consumed" | "remaining"
    val animations: Boolean = true,
    val order: List<String> = emptyList(),
    val hudAgents: List<String> = emptyList(),
    val blocks: Map<String, DesktopBlockPrefs> = emptyMap(),
    val hiddenSections: List<String> = emptyList(),
)

@Serializable
data class CompanionStateResponse(
    val schemaVersion: Int = 1,
    val generatedAtMs: Long = 0L,
    val approvals: List<ApprovalItem> = emptyList(),
    val sessions: List<SessionSummary> = emptyList(),
    val targets: UserTargetsPayload = UserTargetsPayload(),
    val agentMessages: List<AgentMessageRecord> = emptyList(),
    val events: List<IslandEventItem> = emptyList(),
    val preferences: DesktopPreferences? = null,
)

@Serializable
data class ApprovalOption(
    val label: String,
    val description: String? = null,
)

@Serializable
data class ApprovalQuestion(
    val id: String? = null,
    val answerKey: String,
    val question: String,
    val header: String? = null,
    val options: List<ApprovalOption> = emptyList(),
    val multiSelect: Boolean = false,
)

@Serializable
data class ApprovalItem(
    val id: String,
    val title: String = "",
    val source: String = "",
    val toolName: String = "",
    val input: String = "",
    val sessionId: String? = null,
    val createdAt: String = "",
    val allowsAlways: Boolean = false,
    val deniesWithReply: Boolean = false,
    val kind: String? = null, // "permission" | "question" | "plan"
    val vendor: String? = null,
    val summary: String? = null,
    val detail: String? = null,
    val cwd: String? = null,
    val path: String? = null,
    val removed: String? = null,
    val added: String? = null,
    val alwaysAllowRule: String? = null,
    val allowLabel: String? = null,
    val badge: String? = null,
    val questions: List<ApprovalQuestion> = emptyList(),
    val plan: String? = null,
    val answersQuestion: Boolean = true,
    val answerNote: String? = null,
    val answersPlan: Boolean = true,
)

@Serializable
data class SessionLiveFacts(
    val message: String? = null,
    val outcome: String? = null,
    val turnId: String? = null,
)

@Serializable
data class SessionSummary(
    val id: String,
    val source: String = "",
    val vendorName: String? = null,
    val client: String? = null,
    val title: String? = null,
    val project: String? = null,
    val cwd: String? = null,
    val state: String = "idle", // "running" | "waiting" | "idle"
    val model: String? = null,
    val modelName: String? = null,
    val tokens: Long = 0L,
    val cost: Double? = null,
    val pricedCost: Double? = null,
    val updatedAt: String? = null,
    val lastMessageAt: String? = null,
    val parentId: String? = null,
    val liveFacts: SessionLiveFacts? = null,
)

@Serializable
data class UserTargetsPayload(
    val sessions: List<ReachableSessionTarget> = emptyList(),
    val starts: List<StartOffer> = emptyList(),
)

@Serializable
data class ReachableSessionTarget(
    val id: String,
    val client: String,
    val title: String? = null,
    val folder: String? = null,
    val startFolder: String? = null,
    val state: String = "idle",
    val reachable: Boolean? = null,
    val reason: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class StartOffer(
    val client: String,
    val folders: List<String> = emptyList(),
    val withoutFolder: Boolean = false,
)

@Serializable
data class AgentMessageParty(
    val client: String = "",
    val sessionId: String = "",
    val title: String? = null,
)

@Serializable
data class AgentMessageRecord(
    val id: String,
    val kind: String = "send",
    val chain: String = "",
    val count: Int = 1,
    val from: AgentMessageParty = AgentMessageParty(),
    val to: AgentMessageParty = AgentMessageParty(),
    val replyTo: String? = null,
    val repliedBy: String? = null,
    val sentAtMs: Long = 0L,
    val state: String = "",
    val reason: String? = null,
    val answered: Boolean = false,
    val steered: Boolean = false,
    val text: String? = null,
)

@Serializable
data class IslandEventItem(
    val id: String = "",
    val kind: String = "",
    val at: String = "",
    val source: String = "",
    val title: String? = null,
    val message: String? = null,
)

@Serializable
data class SessionMessageItem(
    val id: String = "",
    val role: String = "",
    val text: String? = null,
    val timestamp: String? = null,
    val isPrompt: Boolean = false,
)

@Serializable
data class SessionDetailResponse(
    val session: SessionSummary? = null,
    val messages: List<SessionMessageItem> = emptyList(),
    val turnCount: Int? = null,
    val callCount: Int? = null,
    val contextTokens: Long? = null,
    val contextWindow: Long? = null,
    val contextFill: Double? = null,
    val cacheHitRate: Double? = null,
    val listCost: Double? = null,
    val pricedCost: Double? = null,
)

@Serializable
data class SendDeliveryResponse(
    val state: String = "refused",
    val reason: String? = null,
    val sessionId: String? = null,
)

@Serializable
data class UploadChunkResponse(
    val ok: Boolean = false,
    val complete: Boolean = false,
    val path: String? = null,
    val filename: String? = null,
    val size: Long = 0L,
)

// ---- Aggregated UI / Widget Snapshots ----

@Serializable
data class WidgetQuotaSnapshot(
    val deviceName: String,
    val online: Boolean,
    val activeSessions: Int,
    val pendingApprovals: Int,
    val updatedAtMs: Long,
    val rows: List<WidgetQuotaRow>,
)

@Serializable
data class WidgetQuotaRow(
    val provider: String,
    val label: String,
    val usedPercent: Int,
    val critical: Boolean,
    val resetLabel: String,
)
