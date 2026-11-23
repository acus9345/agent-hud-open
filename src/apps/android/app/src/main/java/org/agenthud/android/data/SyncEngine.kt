package org.agenthud.android.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class UsageRange(val label: String, val days: Int) {
    TODAY("Today", 1),
    DAYS_7("7d", 7),
    DAYS_30("30d", 30),
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    LIVE_SSE,
    POLLING,
    UNAUTHORIZED,
    ERROR,
}

data class UsageDayPoint(
    val day: String,
    val label: String,
    val totalTokens: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long = 0L,
    val reasoningTokens: Long = 0L,
    val costUsd: Double,
    val byModel: Map<String, Long> = emptyMap(),
)

data class AgentUsageRow(
    val agentId: String,
    val displayName: String,
    val totalTokens: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val costUsd: Double,
    val share: Double,
)

data class ModelUsageRow(
    val model: String,
    val vendor: String,
    val totalTokens: Long,
    val costUsd: Double,
    val share: Double,
)

data class UsageSummary(
    val range: UsageRange = UsageRange.DAYS_30,
    val totalTokens: Long = 0L,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWriteTokens: Long = 0L,
    val reasoningTokens: Long = 0L,
    val cacheHitRate: Double = 0.0,
    val estimatedCostUsd: Double = 0.0,
    val activeDays: Int = 0,
    val daily: List<UsageDayPoint> = emptyList(),
    val byAgent: List<AgentUsageRow> = emptyList(),
    val byModel: List<ModelUsageRow> = emptyList(),
)

data class AppUiState(
    val devices: List<PairedDevice> = emptyList(),
    val activeDeviceId: String? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectionError: String? = null,
    val lastSyncedAtMs: Long = 0L,
    val readings: ReadingsPayload = ReadingsPayload(),
    val usageRange: UsageRange = UsageRange.DAYS_30,
    val usageSummary: UsageSummary = UsageSummary(),
    val todayUsage: UsageSummary = UsageSummary(range = UsageRange.TODAY),
    val weekUsage: UsageSummary = UsageSummary(range = UsageRange.DAYS_7),
    val monthUsage: UsageSummary = UsageSummary(range = UsageRange.DAYS_30),
    val companionSupported: Boolean = true,
    val companionState: CompanionStateResponse? = null,
    val liveMonitorEnabled: Boolean = true,
    val isRefreshing: Boolean = false,
    val isPairing: Boolean = false,
    val pairError: String? = null,
    val bannerMessage: String? = null,
    val answeringApprovalIds: Set<String> = emptySet(),
) {
    val activeDevice: PairedDevice?
        get() = devices.firstOrNull { it.deviceId == activeDeviceId } ?: devices.firstOrNull()

    val pendingApprovals: List<ApprovalItem>
        get() = companionState?.approvals.orEmpty()

    val sessions: List<SessionSummary>
        get() = companionState?.sessions.orEmpty()

    val runningSessionCount: Int
        get() = sessions.count { it.state == "running" || it.state == "waiting" }

    val desktopPrefs: DesktopPreferences?
        get() = companionState?.preferences

    val showRemainingPercent: Boolean
        get() = desktopPrefs?.quotaDisplay == "remaining"

    val visibleProviders: List<ProviderReading>
        get() = applyDesktopPreferences(readings.providers, desktopPrefs)
}

/**
 * Mirrors desktop HUD visibility & order (`presentation.json` + `settings`):
 * filters hidden providers, hidden accounts, and hidden quota windows, and orders
 * both providers and quota rows to match the PC's exact layout.
 */
fun applyDesktopPreferences(
    providers: List<ProviderReading>,
    prefs: DesktopPreferences?,
): List<ProviderReading> {
    if (prefs == null) return providers

    fun canonicalProvider(raw: String): String = when (raw.removePrefix("api:").lowercase(Locale.US)) {
        "openai" -> "codex"
        else -> raw.removePrefix("api:").lowercase(Locale.US)
    }

    fun blockFor(providerId: String): DesktopBlockPrefs? {
        val norm = canonicalProvider(providerId)
        val candidates = listOf(
            providerId,
            norm,
            "api:$providerId",
            "api:$norm",
            if (norm == "codex") "openai" else norm,
        )
        for (key in candidates) {
            prefs.blocks[key]?.let { return it }
        }
        return null
    }

    val allowedHud = prefs.hudAgents.mapTo(HashSet()) { canonicalProvider(it) }
    val orderKeys = prefs.order.map { canonicalProvider(it) }
    val allowedOrder = orderKeys.toSet()

    val filtered = providers.mapNotNull { provider ->
        val norm = canonicalProvider(provider.provider)
        val block = blockFor(provider.provider)
        if (block?.hidden == true) return@mapNotNull null

        // If PC explicitly curated HUD agents/order, follow it when non-empty
        if (allowedHud.isNotEmpty() && norm !in allowedHud && norm !in allowedOrder) {
            return@mapNotNull null
        }

        val acctKeys = listOfNotNull(provider.accountID, provider.accountKey, provider.accountLabel)
            .filter { it.isNotBlank() }
        if (block != null) {
            if (block.hiddenAccounts.any { h -> acctKeys.any { it.equals(h, ignoreCase = true) } }) {
                return@mapNotNull null
            }
            if (block.accounts.isNotEmpty() &&
                block.accounts.none { a -> acctKeys.any { it.equals(a, ignoreCase = true) } }
            ) {
                return@mapNotNull null
            }
        }

        val visibleQuotas = if (block != null) {
            val kept = provider.quotas.filter { q ->
                val keys = quotaMatchKeys(provider, q)
                block.hiddenRows.none { hidden -> keys.any { it.equals(hidden, ignoreCase = true) } }
            }
            if (block.rows.isNotEmpty()) {
                kept.sortedBy { q ->
                    val keys = quotaMatchKeys(provider, q)
                    val idx = block.rows.indexOfFirst { r -> keys.any { it.equals(r, ignoreCase = true) } }
                    if (idx < 0) Int.MAX_VALUE else idx
                }
            } else {
                kept
            }
        } else {
            provider.quotas
        }

        if (visibleQuotas.isEmpty() && provider.balances.isEmpty() && provider.resetCredits == null) {
            return@mapNotNull null
        }
        provider.copy(quotas = visibleQuotas)
    }

    if (orderKeys.isEmpty()) return filtered
    return filtered.sortedBy { provider ->
        val norm = canonicalProvider(provider.provider)
        val idx = orderKeys.indexOf(norm)
        if (idx < 0) Int.MAX_VALUE else idx
    }
}

private fun quotaMatchKeys(provider: ProviderReading, q: QuotaWindowReading): List<String> = buildList {
    add(q.windowID)
    if (q.windowName.isNotBlank()) add(q.windowName)
    if (q.windowShortName.isNotBlank()) add(q.windowShortName)
    provider.accountID?.takeIf { it.isNotBlank() }?.let { acct ->
        add("$acct/${q.windowID}")
        add("$acct:${q.windowID}")
    }
    provider.accountKey?.takeIf { it.isNotBlank() }?.let { key ->
        add("$key/${q.windowID}")
    }
    add("${provider.provider}:${q.windowID}")
}

/**
 * Pure, JVM-testable state reducer for `/v1/snapshot` and `/v1/changes` sync records.
 */
class SyncStore {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val records = LinkedHashMap<String, SyncRecord>()

    var revision: String? = null
        private set
    var cursor: SyncCursor? = null
        private set
    var etag: String? = null
        private set

    fun clear() {
        records.clear()
        revision = null
        cursor = null
        etag = null
    }

    fun applySnapshot(snapshot: SnapshotResponse, etag: String?) {
        records.clear()
        for (record in snapshot.records) {
            records[record.canonicalId()] = record
        }
        revision = snapshot.revision
        cursor = snapshot.cursor
        this.etag = etag
    }

    fun applyChanges(changes: ChangesResponse) {
        for (deletedId in changes.deletedIDs) {
            records.remove(deletedId)
        }
        for (record in changes.records) {
            records[record.canonicalId()] = record
        }
        if (changes.revision.isNotBlank()) revision = changes.revision
        if (changes.toCursor != null) cursor = changes.toCursor
    }

    fun recordCount(): Int = records.size

    fun readings(): ReadingsPayload {
        val mergedNames = LinkedHashMap<String, String>()
        val mergedProviders = LinkedHashMap<String, ProviderReading>()
        for (record in records.values) {
            if (record.kind != "readings") continue
            val decoded = runCatching {
                json.decodeFromJsonElement<ReadingsPayload>(record.payload)
            }.getOrNull() ?: continue
            mergedNames.putAll(decoded.names)
            for (provider in decoded.providers) {
                val dedupeKey = "${provider.provider}|${provider.accountKey ?: provider.accountID ?: provider.accountLabel}"
                mergedProviders[dedupeKey] = provider
            }
        }
        return ReadingsPayload(
            providers = mergedProviders.values.toList(),
            names = mergedNames,
        )
    }

    fun usageSummary(range: UsageRange, today: LocalDate = LocalDate.now()): UsageSummary {
        val names = readings().names
        val consumers = HashMap<String, ConsumerPayload>()
        val dayRecords = ArrayList<UsageDayPayload>()

        for (record in records.values) {
            when (record.kind) {
                "consumer" -> {
                    val decoded = runCatching {
                        json.decodeFromJsonElement<ConsumerPayload>(record.payload)
                    }.getOrNull() ?: continue
                    consumers[record.key] = decoded
                    consumers[record.canonicalId()] = decoded
                }
                "usage" -> {
                    val decoded = runCatching {
                        json.decodeFromJsonElement<UsageDayPayload>(record.payload)
                    }.getOrNull() ?: continue
                    dayRecords.add(decoded)
                }
            }
        }

        val startDate = today.minusDays((range.days - 1).toLong())
        val dayLabelFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)

        data class DayAcc(
            var input: Long = 0L,
            var output: Long = 0L,
            var cacheRead: Long = 0L,
            var cacheWrite: Long = 0L,
            var reasoning: Long = 0L,
            var costUsd: Double = 0.0,
            val models: LinkedHashMap<String, Long> = LinkedHashMap(),
        ) {
            val total: Long get() = input + output + cacheRead
        }

        val byDay = LinkedHashMap<LocalDate, DayAcc>()
        for (offset in (range.days - 1) downTo 0) {
            byDay[today.minusDays(offset.toLong())] = DayAcc()
        }

        val byAgent = HashMap<String, DayAcc>()
        val agentDisplayNames = HashMap<String, String>()
        val byModel = HashMap<String, Pair<String, DayAcc>>()

        for (dayPayload in dayRecords) {
            val date = runCatching { LocalDate.parse(dayPayload.day) }.getOrNull() ?: continue
            if (date.isBefore(startDate) || date.isAfter(today)) continue
            val dayAcc = byDay.getOrPut(date) { DayAcc() }

            for (bucket in dayPayload.tokens) {
                val bucketCost = extractUsdFromListCost(bucket.listCost)
                dayAcc.input += bucket.inputAndWrite
                dayAcc.output += bucket.outputAndReasoning
                dayAcc.cacheRead += bucket.cacheRead
                dayAcc.cacheWrite += bucket.cacheWrite
                dayAcc.reasoning += bucket.reasoning
                dayAcc.costUsd += bucketCost

                val rawAgent = bucket.agentId.ifBlank { "other" }
                val consumer = consumers[rawAgent]
                val providerId = rawAgent.substringBefore("-model:").ifBlank { rawAgent }
                if (!consumer?.vendor.isNullOrBlank()) {
                    agentDisplayNames[providerId] = consumer!!.vendor
                }
                val agentAcc = byAgent.getOrPut(providerId) { DayAcc() }
                agentAcc.input += bucket.inputAndWrite
                agentAcc.output += bucket.outputAndReasoning
                agentAcc.cacheRead += bucket.cacheRead
                agentAcc.cacheWrite += bucket.cacheWrite
                agentAcc.reasoning += bucket.reasoning
                agentAcc.costUsd += bucketCost

                val modelName = consumer?.model?.takeIf { it.isNotBlank() }
                    ?: rawAgent.substringAfter("-model:", "").takeIf { it.isNotBlank() }
                if (modelName != null) {
                    val vendorLabel = consumer?.vendor?.ifBlank { providerId } ?: providerId
                    val (_, modelAcc) = byModel.getOrPut(modelName) {
                        vendorLabel to DayAcc()
                    }
                    modelAcc.input += bucket.inputAndWrite
                    modelAcc.output += bucket.outputAndReasoning
                    modelAcc.cacheRead += bucket.cacheRead
                    modelAcc.costUsd += bucketCost
                    dayAcc.models[modelName] = (dayAcc.models[modelName] ?: 0L) + bucket.totalTokens
                }
            }

            // Include billed API costs when listCost was absent for that day
            var billedUsd = 0.0
            for (cost in dayPayload.costs) {
                billedUsd += cost.amounts["USD"]?.toDoubleOrNull() ?: 0.0
            }
            if (billedUsd > 0.0 && dayAcc.costUsd == 0.0) {
                dayAcc.costUsd += billedUsd
            }
        }

        var totalInput = 0L
        var totalOutput = 0L
        var totalCacheRead = 0L
        var totalCacheWrite = 0L
        var totalReasoning = 0L
        var totalCost = 0.0
        var activeDays = 0

        val dailyPoints = byDay.entries.sortedBy { it.key }.map { (date, acc) ->
            totalInput += acc.input
            totalOutput += acc.output
            totalCacheRead += acc.cacheRead
            totalCacheWrite += acc.cacheWrite
            totalReasoning += acc.reasoning
            totalCost += acc.costUsd
            if (acc.total > 0L) activeDays += 1
            UsageDayPoint(
                day = date.toString(),
                label = date.format(dayLabelFmt),
                totalTokens = acc.total,
                inputTokens = acc.input,
                outputTokens = acc.output,
                cacheReadTokens = acc.cacheRead,
                cacheWriteTokens = acc.cacheWrite,
                reasoningTokens = acc.reasoning,
                costUsd = acc.costUsd,
                byModel = acc.models.toMap(),
            )
        }

        val totalTokens = totalInput + totalOutput + totalCacheRead
        val cacheDenom = (totalInput + totalCacheRead).coerceAtLeast(1L)
        val cacheHitRate = if (totalCacheRead > 0L) {
            (totalCacheRead.toDouble() / cacheDenom.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val agentRows = byAgent.entries
            .filter { it.value.total > 0L }
            .sortedByDescending { it.value.total }
            .map { (id, acc) ->
                AgentUsageRow(
                    agentId = id,
                    displayName = agentDisplayNames[id] ?: QuotaFormatting.providerDisplayName(id, names),
                    totalTokens = acc.total,
                    inputTokens = acc.input,
                    outputTokens = acc.output,
                    cacheReadTokens = acc.cacheRead,
                    costUsd = acc.costUsd,
                    share = if (totalTokens > 0L) acc.total.toDouble() / totalTokens.toDouble() else 0.0,
                )
            }

        val modelRows = byModel.entries
            .filter { it.value.second.total > 0L }
            .sortedByDescending { it.value.second.total }
            .map { (model, pair) ->
                val (vendor, acc) = pair
                ModelUsageRow(
                    model = model,
                    vendor = vendor,
                    totalTokens = acc.total,
                    costUsd = acc.costUsd,
                    share = if (totalTokens > 0L) acc.total.toDouble() / totalTokens.toDouble() else 0.0,
                )
            }

        return UsageSummary(
            range = range,
            totalTokens = totalTokens,
            inputTokens = totalInput,
            outputTokens = totalOutput,
            cacheReadTokens = totalCacheRead,
            cacheWriteTokens = totalCacheWrite,
            reasoningTokens = totalReasoning,
            cacheHitRate = cacheHitRate,
            estimatedCostUsd = totalCost,
            activeDays = activeDays,
            daily = dailyPoints,
            byAgent = agentRows,
            byModel = modelRows,
        )
    }

    fun buildWidgetSnapshot(
        deviceName: String,
        online: Boolean,
        companion: CompanionStateResponse?,
        nowMs: Long = System.currentTimeMillis(),
    ): WidgetQuotaSnapshot {
        val currentReadings = readings()
        val visible = applyDesktopPreferences(currentReadings.providers, companion?.preferences)
        val rows = ArrayList<WidgetQuotaRow>()
        for (provider in visible) {
            val providerName = QuotaFormatting.providerDisplayName(provider.provider, currentReadings.names)
            val topWindow = provider.quotas.maxByOrNull { it.usedPercent } ?: continue
            rows.add(
                WidgetQuotaRow(
                    provider = providerName,
                    label = QuotaFormatting.cleanWindowLabel(topWindow.windowName, topWindow.windowShortName),
                    usedPercent = topWindow.usedPercent.roundToInt(),
                    critical = QuotaFormatting.isCriticalLevel(topWindow),
                    resetLabel = QuotaFormatting.formatResetTime(topWindow.resetsAtMs, nowMs),
                ),
            )
        }
        val sortedRows = rows.sortedByDescending { it.usedPercent }.take(4)
        val activeSessions = companion?.sessions?.count { it.state == "running" || it.state == "waiting" } ?: 0
        val pendingApprovals = companion?.approvals?.size ?: 0
        return WidgetQuotaSnapshot(
            deviceName = deviceName,
            online = online,
            activeSessions = activeSessions,
            pendingApprovals = pendingApprovals,
            updatedAtMs = nowMs,
            rows = sortedRows,
        )
    }

    private fun extractUsdFromListCost(listCost: Map<String, kotlinx.serialization.json.JsonElement>?): Double {
        if (listCost.isNullOrEmpty()) return 0.0
        val usd = listCost["USD"] ?: listCost["usd"] ?: return 0.0
        return when (usd) {
            is JsonPrimitive -> usd.doubleOrNull ?: usd.content.toDoubleOrNull() ?: 0.0
            is JsonObject -> usd["total"]?.jsonPrimitive?.doubleOrNull
                ?: usd["amount"]?.jsonPrimitive?.doubleOrNull
                ?: 0.0
            else -> 0.0
        }
    }
}

class AgentHudRepository(
    private val deviceStore: DeviceStore,
    private val lanClient: LanClient,
    private val onWidgetUpdated: () -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncStore = SyncStore()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    @Volatile
    private var inForeground = false
    private var consecutiveSyncFailures = 0

    private var activeEventSource: EventSource? = null
    private var syncJob: Job? = null
    private var companionPollJob: Job? = null

    init {
        if (_state.value.activeDevice != null) {
            startSyncLoops()
        }
    }

    private fun initialState(): AppUiState {
        val devices = deviceStore.loadDevices()
        val activeId = deviceStore.activeDeviceId()
            ?.takeIf { id -> devices.any { it.deviceId == id } }
            ?: devices.firstOrNull()?.deviceId
        return AppUiState(
            devices = devices,
            activeDeviceId = activeId,
            liveMonitorEnabled = deviceStore.isLiveMonitorEnabled(),
        )
    }

    fun setForeground(foreground: Boolean) {
        inForeground = foreground
        if (foreground && _state.value.activeDevice != null) {
            refreshNow()
        }
    }

    fun setUsageRange(range: UsageRange) {
        _state.update {
            it.copy(
                usageRange = range,
                usageSummary = syncStore.usageSummary(range),
            )
        }
    }

    fun setLiveMonitorEnabled(enabled: Boolean) {
        deviceStore.setLiveMonitorEnabled(enabled)
        _state.update { it.copy(liveMonitorEnabled = enabled) }
    }

    fun clearBanner() {
        _state.update { it.copy(bannerMessage = null) }
    }

    fun clearPairError() {
        _state.update { it.copy(pairError = null) }
    }

    fun pairFromUri(rawUri: String, onPaired: () -> Unit = {}) {
        val parsed = PairUriParser.parse(rawUri)
        val invitation = parsed.getOrElse { err ->
            _state.update { it.copy(pairError = err.message ?: "Invalid pairing link") }
            return
        }
        scope.launch {
            _state.update { it.copy(isPairing = true, pairError = null) }
            try {
                val (resp, activeEndpoint) = lanClient.pair(invitation, deviceStore.clientId())
                val device = PairedDevice(
                    deviceId = resp.deviceID,
                    deviceName = resp.deviceName.ifBlank { invitation.deviceName },
                    endpoint = resp.endpoint.trimEnd('/'),
                    alternateEndpoints = resp.alternateEndpoints.map { it.trimEnd('/') },
                    certificateSha256 = resp.certificateSHA256.lowercase(),
                    token = resp.token,
                    pairedAtMs = System.currentTimeMillis(),
                    lastActiveEndpoint = activeEndpoint,
                )
                val updatedDevices = deviceStore.upsertDevice(device)
                syncStore.clear()
                _state.update {
                    it.copy(
                        devices = updatedDevices,
                        activeDeviceId = device.deviceId,
                        isPairing = false,
                        pairError = null,
                        companionSupported = true,
                        companionState = null,
                        bannerMessage = "Paired with ${device.deviceName}",
                    )
                }
                startSyncLoops()
                onPaired()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isPairing = false,
                        pairError = e.message ?: "Pairing failed",
                    )
                }
            }
        }
    }

    fun selectDevice(deviceId: String) {
        if (_state.value.activeDeviceId == deviceId) return
        deviceStore.setActiveDeviceId(deviceId)
        syncStore.clear()
        _state.update {
            it.copy(
                activeDeviceId = deviceId,
                connectionState = ConnectionState.CONNECTING,
                connectionError = null,
                readings = ReadingsPayload(),
                usageSummary = UsageSummary(range = it.usageRange),
                companionSupported = true,
                companionState = null,
            )
        }
        startSyncLoops()
    }

    fun removeDevice(deviceId: String) {
        val remaining = deviceStore.removeDevice(deviceId)
        val nextActive = deviceStore.activeDeviceId()
        syncStore.clear()
        stopSyncLoops()
        _state.update {
            it.copy(
                devices = remaining,
                activeDeviceId = nextActive,
                connectionState = ConnectionState.DISCONNECTED,
                connectionError = null,
                readings = ReadingsPayload(),
                usageSummary = UsageSummary(range = it.usageRange),
                companionState = null,
            )
        }
        if (nextActive != null) {
            startSyncLoops()
        }
    }

    fun refreshNow() {
        val device = _state.value.activeDevice ?: return
        scope.launch {
            _state.update { it.copy(isRefreshing = true) }
            try {
                syncOnce(device, forceSnapshot = false)
                pollCompanionOnce(device)
            } finally {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    suspend fun fetchSessionDetail(sessionId: String, messageLimit: Int = 500): Result<SessionDetailResponse> {
        val device = _state.value.activeDevice
            ?: return Result.failure(IllegalStateException("No paired computer"))
        return runCatching {
            lanClient.fetchSessionDetail(device, sessionId, messageLimit = messageLimit)
        }
    }

    suspend fun answerApproval(approvalId: String, decision: JsonObject): Result<Unit> {
        val device = _state.value.activeDevice
            ?: return Result.failure(IllegalStateException("No paired computer"))
        _state.update { it.copy(answeringApprovalIds = it.answeringApprovalIds + approvalId) }
        return try {
            lanClient.answerApproval(device, approvalId, decision)
            // Optimistically remove the answered approval and refresh right away
            _state.update { current ->
                val updatedCompanion = current.companionState?.copy(
                    approvals = current.companionState.approvals.filterNot { it.id == approvalId },
                )
                current.copy(
                    companionState = updatedCompanion,
                    answeringApprovalIds = current.answeringApprovalIds - approvalId,
                    bannerMessage = "Approval sent",
                )
            }
            persistWidgetSnapshot()
            scope.launch {
                delay(400)
                pollCompanionOnce(device)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    answeringApprovalIds = it.answeringApprovalIds - approvalId,
                    bannerMessage = e.message ?: "Failed to send approval",
                )
            }
            Result.failure(e)
        }
    }

    suspend fun openSessionOnDesktop(sessionId: String, clientName: String): Result<Unit> {
        val device = _state.value.activeDevice
            ?: return Result.failure(IllegalStateException("No paired computer"))
        return runCatching {
            lanClient.openSessionOnDesktop(device, sessionId, clientName)
            _state.update { it.copy(bannerMessage = "Opening session on ${device.deviceName}") }
            scope.launch {
                delay(1200)
                pollCompanionOnce(device)
            }
        }
    }

    suspend fun sendToSession(
        clientName: String,
        sessionId: String,
        text: String,
        sendNow: Boolean = false,
    ): Result<SendDeliveryResponse> {
        val device = _state.value.activeDevice
            ?: return Result.failure(IllegalStateException("No paired computer"))
        return runCatching {
            val response = lanClient.sendToSession(device, clientName, sessionId, text, sendNow = sendNow)
            if (response.state == "refused") {
                throw IllegalStateException(response.reason ?: "Session refused the message")
            }
            _state.update { it.copy(bannerMessage = if (sendNow) "Sent now to $clientName" else "Sent to $clientName") }
            scope.launch {
                delay(500)
                pollCompanionOnce(device)
            }
            response
        }
    }

    suspend fun sendNowToSession(
        clientName: String,
        sessionId: String,
        text: String = "",
    ): Result<SendDeliveryResponse> {
        val device = _state.value.activeDevice
            ?: return Result.failure(IllegalStateException("No paired computer"))
        return runCatching {
            val response = lanClient.sendNowToSession(device, clientName, sessionId, text)
            if (response.state == "refused") {
                throw IllegalStateException(response.reason ?: "Session refused immediate send")
            }
            _state.update { it.copy(bannerMessage = "Sent now to $clientName") }
            scope.launch {
                delay(500)
                pollCompanionOnce(device)
            }
            response
        }
    }

    suspend fun startNewSession(clientName: String, folder: String, text: String): Result<SendDeliveryResponse> {
        val device = _state.value.activeDevice
            ?: return Result.failure(IllegalStateException("No paired computer"))
        return runCatching {
            val response = lanClient.startNewSession(device, clientName, folder, text)
            if (response.state == "refused") {
                throw IllegalStateException(response.reason ?: "Could not start session")
            }
            _state.update { it.copy(bannerMessage = "Started $clientName session") }
            scope.launch {
                delay(700)
                pollCompanionOnce(device)
            }
            response
        }
    }

    suspend fun uploadFile(filename: String, bytes: ByteArray): Result<UploadChunkResponse> {
        val device = _state.value.activeDevice
            ?: return Result.failure(IllegalStateException("No paired computer"))
        return runCatching {
            val response = lanClient.uploadFile(device, filename, bytes)
            if (!response.ok || !response.complete || response.path.isNullOrBlank()) {
                throw IllegalStateException("Upload did not complete")
            }
            response
        }
    }

    suspend fun inspectDesktopFile(path: String): Result<FileChunkResponse> {
        val device = _state.value.activeDevice
            ?: return Result.failure(IllegalStateException("No paired computer"))
        return runCatching {
            lanClient.fetchFileChunk(device, path, offset = 0L, length = 0)
        }
    }

    suspend fun downloadDesktopFile(
        path: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<Triple<String, String, ByteArray>> {
        val device = _state.value.activeDevice
            ?: return Result.failure(IllegalStateException("No paired computer"))
        return runCatching {
            val out = java.io.ByteArrayOutputStream()
            var offset = 0L
            var filename = ""
            var mime = "application/octet-stream"
            var total = 0L
            while (true) {
                val chunk = lanClient.fetchFileChunk(device, path, offset = offset, length = 512 * 1024)
                filename = chunk.filename.ifBlank { filename }
                mime = chunk.mimeType
                total = chunk.size
                if (chunk.dataBase64.isNotEmpty()) {
                    val decoded = java.util.Base64.getDecoder().decode(chunk.dataBase64)
                    out.write(decoded)
                    offset += decoded.size.toLong()
                }
                onProgress(offset, total)
                if (!chunk.hasMore || chunk.dataBase64.isEmpty()) break
            }
            Triple(filename.ifBlank { "file" }, mime, out.toByteArray())
        }
    }

    fun setRemoteConfig(
        deviceId: String,
        mode: String,
        rawUrl: String,
        fallbackIp: String,
    ) {
        val validMode = if (mode in setOf("lan", "tailscale", "relay")) mode else "tailscale"
        val cleanedUrl = rawUrl.trim().trimEnd('/').takeIf { it.startsWith("https://", ignoreCase = true) }
        val cleanedIp = fallbackIp.trim().takeIf { it.isNotBlank() }
        val updatedDevices = deviceStore.loadDevices().map {
            if (it.deviceId == deviceId) {
                it.copy(
                    remoteMode = validMode,
                    remoteEndpoint = if (validMode == "lan") null else cleanedUrl,
                    remoteFallbackIp = if (validMode == "relay") cleanedIp else null,
                )
            } else {
                it
            }
        }
        deviceStore.saveDevices(updatedDevices)
        _state.update {
            it.copy(
                devices = updatedDevices,
                bannerMessage = "Updated remote access ($validMode)",
            )
        }
        if (_state.value.activeDeviceId == deviceId) {
            startSyncLoops()
        }
    }

    private fun stopSyncLoops() {
        syncJob?.cancel()
        companionPollJob?.cancel()
        activeEventSource?.cancel()
        activeEventSource = null
    }

    private fun startSyncLoops() {
        stopSyncLoops()
        val device = _state.value.activeDevice ?: return

        syncJob = scope.launch {
            _state.update { it.copy(connectionState = ConnectionState.CONNECTING, connectionError = null) }
            var backoffMs = 2_000L
            while (isActive) {
                val currentDevice = _state.value.activeDevice ?: break
                val ok = syncOnce(currentDevice, forceSnapshot = syncStore.cursor == null)
                if (ok) {
                    backoffMs = 2_000L
                    runSseUntilClosed(currentDevice)
                } else {
                    if (_state.value.connectionState == ConnectionState.UNAUTHORIZED) {
                        delay(30_000L)
                    } else {
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                    }
                }
            }
        }

        companionPollJob = scope.launch {
            while (isActive) {
                val currentDevice = _state.value.activeDevice ?: break
                if (_state.value.connectionState != ConnectionState.UNAUTHORIZED) {
                    pollCompanionOnce(currentDevice)
                }
                val intervalMs = when {
                    !inForeground -> 15_000L
                    _state.value.pendingApprovals.isNotEmpty() || _state.value.runningSessionCount > 0 -> 3_500L
                    else -> 8_000L
                }
                delay(intervalMs)
            }
        }
    }

    private suspend fun runSseUntilClosed(device: PairedDevice) {
        val closedSignal = kotlinx.coroutines.CompletableDeferred<Unit>()
        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                _state.update {
                    it.copy(connectionState = ConnectionState.LIVE_SSE, connectionError = null)
                }
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                when (type) {
                    "changes" -> {
                        val parsed = runCatching { json.decodeFromString<ChangesResponse>(data) }.getOrNull()
                        if (parsed != null) {
                            syncStore.applyChanges(parsed)
                            publishSyncState(device, online = true)
                        }
                    }
                    "revision" -> {
                        scope.launch {
                            syncOnce(device, forceSnapshot = false)
                            pollCompanionOnce(device)
                        }
                    }
                    "reset" -> {
                        scope.launch {
                            syncOnce(device, forceSnapshot = true)
                            pollCompanionOnce(device)
                        }
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!closedSignal.isCompleted) closedSignal.complete(Unit)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (response?.code == 401 || response?.code == 403) {
                    _state.update {
                        it.copy(
                            connectionState = ConnectionState.UNAUTHORIZED,
                            connectionError = "Device was revoked on desktop. Pair again.",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            connectionState = ConnectionState.POLLING,
                            connectionError = t?.message,
                        )
                    }
                }
                if (!closedSignal.isCompleted) closedSignal.complete(Unit)
            }
        }

        activeEventSource?.cancel()
        activeEventSource = lanClient.openEventStream(device, syncStore.cursor, listener)
        try {
            closedSignal.await()
        } finally {
            activeEventSource?.cancel()
            activeEventSource = null
        }
        delay(2_500L)
    }

    private suspend fun syncOnce(device: PairedDevice, forceSnapshot: Boolean): Boolean {
        return try {
            val cursor = syncStore.cursor
            if (!forceSnapshot && cursor != null) {
                try {
                    val (changes, workingUrl) = lanClient.fetchChanges(device, cursor)
                    consecutiveSyncFailures = 0
                    rememberActiveEndpoint(device, workingUrl)
                    syncStore.applyChanges(changes)
                    publishSyncState(device, online = true)
                    return true
                } catch (_: LanCursorExpiredException) {
                    // Fall through to full snapshot
                }
            }

            when (val result = lanClient.fetchSnapshot(device, if (forceSnapshot) null else syncStore.etag)) {
                SnapshotResult.NotModified -> {
                    consecutiveSyncFailures = 0
                    publishSyncState(device, online = true)
                }
                is SnapshotResult.Loaded -> {
                    consecutiveSyncFailures = 0
                    rememberActiveEndpoint(device, result.activeEndpoint)
                    syncStore.applySnapshot(result.snapshot, result.etag)
                    publishSyncState(device, online = true)
                }
            }
            true
        } catch (e: LanAuthException) {
            _state.update {
                it.copy(
                    connectionState = ConnectionState.UNAUTHORIZED,
                    connectionError = e.message ?: "Paired token rejected by desktop",
                )
            }
            persistWidgetSnapshot()
            false
        } catch (e: Exception) {
            consecutiveSyncFailures += 1
            if (consecutiveSyncFailures >= 2 || _state.value.lastSyncedAtMs == 0L) {
                _state.update {
                    it.copy(
                        connectionState = ConnectionState.ERROR,
                        connectionError = e.message ?: "Cannot reach ${device.deviceName}",
                    )
                }
            }
            persistWidgetSnapshot()
            false
        }
    }

    private suspend fun pollCompanionOnce(device: PairedDevice) {
        try {
            val (companion, workingUrl) = lanClient.fetchCompanionState(device)
            consecutiveSyncFailures = 0
            rememberActiveEndpoint(device, workingUrl)
            _state.update {
                it.copy(
                    companionSupported = true,
                    companionState = companion,
                    connectionError = if (it.connectionState == ConnectionState.ERROR) null else it.connectionError,
                    connectionState = if (it.connectionState == ConnectionState.ERROR) ConnectionState.POLLING else it.connectionState,
                    lastSyncedAtMs = System.currentTimeMillis(),
                )
            }
            persistWidgetSnapshot()
        } catch (_: LanNotFoundException) {
            _state.update { it.copy(companionSupported = false) }
        } catch (_: LanAuthException) {
            _state.update {
                it.copy(
                    connectionState = ConnectionState.UNAUTHORIZED,
                    connectionError = "Device was revoked on desktop. Pair again.",
                )
            }
        } catch (_: Exception) {
            // Keep last known companion state while disconnected
        }
    }

    private fun rememberActiveEndpoint(device: PairedDevice, workingUrl: String) {
        if (device.lastActiveEndpoint == workingUrl) return
        val updated = device.copy(lastActiveEndpoint = workingUrl)
        val devices = deviceStore.loadDevices().map {
            if (it.deviceId == device.deviceId) updated else it
        }
        deviceStore.saveDevices(devices)
        _state.update { it.copy(devices = devices) }
    }

    private fun publishSyncState(device: PairedDevice, online: Boolean) {
        val currentRange = _state.value.usageRange
        val readings = syncStore.readings()
        val todayU = syncStore.usageSummary(UsageRange.TODAY)
        val weekU = syncStore.usageSummary(UsageRange.DAYS_7)
        val monthU = syncStore.usageSummary(UsageRange.DAYS_30)
        val usage = when (currentRange) {
            UsageRange.TODAY -> todayU
            UsageRange.DAYS_7 -> weekU
            UsageRange.DAYS_30 -> monthU
        }
        _state.update {
            val nextConn = if (it.connectionState == ConnectionState.LIVE_SSE) {
                ConnectionState.LIVE_SSE
            } else if (online) {
                ConnectionState.POLLING
            } else {
                ConnectionState.ERROR
            }
            it.copy(
                connectionState = nextConn,
                connectionError = if (online) null else it.connectionError,
                lastSyncedAtMs = System.currentTimeMillis(),
                readings = readings,
                usageSummary = usage,
                todayUsage = todayU,
                weekUsage = weekU,
                monthUsage = monthU,
            )
        }
        persistWidgetSnapshot()
    }

    private fun persistWidgetSnapshot() {
        val current = _state.value
        val device = current.activeDevice ?: return
        val online = current.connectionState == ConnectionState.LIVE_SSE ||
            current.connectionState == ConnectionState.POLLING
        val snapshot = syncStore.buildWidgetSnapshot(
            deviceName = device.deviceName,
            online = online,
            companion = current.companionState,
        )
        deviceStore.saveWidgetSnapshot(snapshot)
        onWidgetUpdated()
    }
}
