package com.euhomy.fridge.data

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_FILE     = "euhomy_prefs"
private const val KEY_SHOW_F     = "show_fahrenheit"
private const val KEY_AUTO_CONN  = "auto_connect"

/**
 * Plain (unencrypted) app preferences — display settings, not secrets.
 */
class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** Whether temperatures should be displayed in °F (default: false = °C). */
    var showFahrenheit: Boolean
        get()      = prefs.getBoolean(KEY_SHOW_F, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_F, value).apply()

    /** Whether to auto-connect to the last known device on app start. */
    var autoConnect: Boolean
        get()      = prefs.getBoolean(KEY_AUTO_CONN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CONN, value).apply()
}
