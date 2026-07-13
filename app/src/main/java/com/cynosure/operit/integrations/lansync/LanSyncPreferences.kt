package com.cynosure.operit.integrations.lansync

import android.content.Context
import android.os.Build
import java.security.SecureRandom
import java.util.UUID

class LanSyncPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("lan_sync", Context.MODE_PRIVATE)

    val deviceId: String
        get() = preferences.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    var deviceName: String
        get() = preferences.getString(KEY_DEVICE_NAME, null) ?: Build.MODEL
        set(value) = preferences.edit().putString(KEY_DEVICE_NAME, value.trim()).apply()

    var port: Int
        get() = preferences.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = preferences.edit().putInt(KEY_PORT, value).apply()

    var pairingCode: String
        get() = preferences.getString(KEY_PAIRING_CODE, null) ?: generatePairingCode().also {
            preferences.edit().putString(KEY_PAIRING_CODE, it).apply()
        }
        set(value) = preferences.edit().putString(KEY_PAIRING_CODE, value).apply()

    fun rotatePairingCode(): String = generatePairingCode().also { pairingCode = it }

    private fun generatePairingCode(): String = (100000 + SecureRandom().nextInt(900000)).toString()

    companion object {
        const val DEFAULT_PORT = 19876
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_PORT = "port"
        private const val KEY_PAIRING_CODE = "pairing_code"
    }
}
