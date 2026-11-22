package org.agenthud.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores paired host credentials (bearer token + pinned cert fingerprint)
 * encrypted with an Android Keystore AES-256-GCM key, plus cached widget state.
 */
class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun clientId(): String {
        val existing = prefs.getString(KEY_CLIENT_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = "android-${UUID.randomUUID()}"
        prefs.edit().putString(KEY_CLIENT_ID, created).apply()
        return created
    }

    fun loadDevices(): List<PairedDevice> {
        val cipherText = prefs.getString(KEY_DEVICES_ENC, null) ?: return emptyList()
        val plain = decrypt(cipherText) ?: return emptyList()
        return runCatching { json.decodeFromString<List<PairedDevice>>(plain) }.getOrDefault(emptyList())
    }

    fun saveDevices(devices: List<PairedDevice>) {
        val encoded = json.encodeToString(devices)
        val encrypted = encrypt(encoded)
        prefs.edit().putString(KEY_DEVICES_ENC, encrypted).apply()
    }

    fun upsertDevice(device: PairedDevice): List<PairedDevice> {
        val updated = loadDevices().filterNot { it.deviceId == device.deviceId } + device
        saveDevices(updated)
        setActiveDeviceId(device.deviceId)
        return updated
    }

    fun removeDevice(deviceId: String): List<PairedDevice> {
        val remaining = loadDevices().filterNot { it.deviceId == deviceId }
        saveDevices(remaining)
        if (activeDeviceId() == deviceId) {
            setActiveDeviceId(remaining.firstOrNull()?.deviceId)
        }
        return remaining
    }

    fun activeDeviceId(): String? = prefs.getString(KEY_ACTIVE_DEVICE, null)

    fun setActiveDeviceId(deviceId: String?) {
        prefs.edit().putString(KEY_ACTIVE_DEVICE, deviceId).apply()
    }

    fun isLiveMonitorEnabled(): Boolean = prefs.getBoolean(KEY_LIVE_MONITOR, true)

    fun setLiveMonitorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_MONITOR, enabled).apply()
    }

    fun saveWidgetSnapshot(snapshot: WidgetQuotaSnapshot) {
        prefs.edit().putString(KEY_WIDGET_SNAPSHOT, json.encodeToString(snapshot)).apply()
    }

    fun loadWidgetSnapshot(): WidgetQuotaSnapshot? {
        val raw = prefs.getString(KEY_WIDGET_SNAPSHOT, null) ?: return null
        return runCatching { json.decodeFromString<WidgetQuotaSnapshot>(raw) }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(1 + iv.size + cipherBytes.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val ivLen = combined[0].toInt() and 0xFF
        val iv = combined.copyOfRange(1, 1 + ivLen)
        val cipherBytes = combined.copyOfRange(1 + ivLen, combined.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
    }.getOrNull()

    companion object {
        private const val PREFS_NAME = "agent_hud_companion_store"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "agent_hud_lan_credentials_v1"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_DEVICES_ENC = "paired_devices_enc"
        private const val KEY_ACTIVE_DEVICE = "active_device_id"
        private const val KEY_LIVE_MONITOR = "live_monitor_enabled"
        private const val KEY_WIDGET_SNAPSHOT = "widget_snapshot_json"
    }
}
