package com.euhomy.fridge.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_FILE  = "euhomy_credentials"
private const val KEY_MAC     = "mac_address"
private const val KEY_LOCAL   = "local_key"
private const val KEY_DEV_ID  = "device_id"
private const val KEY_UUID    = "uuid"
private const val KEY_NAME    = "device_name"

/**
 * Persists [DeviceCredentials] in Android Keystore-backed EncryptedSharedPreferences.
 *
 * Usage:
 *   val store = CredentialsStore(context)
 *   store.save(creds)
 *   val creds = store.load()   // null if not yet configured
 */
class CredentialsStore(context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    fun save(creds: DeviceCredentials) {
        prefs.edit()
            .putString(KEY_MAC,    creds.macAddress)
            .putString(KEY_LOCAL,  creds.localKey)
            .putString(KEY_DEV_ID, creds.deviceId)
            .putString(KEY_UUID,   creds.uuid)
            .putString(KEY_NAME,   creds.deviceName)
            .apply()
    }

    fun load(): DeviceCredentials? {
        val mac   = prefs.getString(KEY_MAC,    null) ?: return null
        val local = prefs.getString(KEY_LOCAL,  null) ?: return null
        val devId = prefs.getString(KEY_DEV_ID, null) ?: return null
        val uuid  = prefs.getString(KEY_UUID,   null) ?: return null
        val name  = prefs.getString(KEY_NAME,   "Euhomy Fridge") ?: "Euhomy Fridge"
        return DeviceCredentials(mac, local, devId, uuid, name)
    }

    fun clear() = prefs.edit().clear().apply()

    fun hasCredentials(): Boolean = prefs.getString(KEY_MAC, null) != null

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
