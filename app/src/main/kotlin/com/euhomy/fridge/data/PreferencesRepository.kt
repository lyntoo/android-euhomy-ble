package com.euhomy.fridge.data

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_FILE      = "euhomy_prefs"
private const val KEY_SHOW_F      = "show_fahrenheit"
private const val KEY_AUTO_CONN   = "auto_connect"
private const val KEY_SETPOINT_C  = "last_setpoint_c"
private const val NO_CACHED_VALUE = Int.MIN_VALUE

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

    /** Last confirmed setpoint from the device (null = never received). */
    var lastSetpointC: Int?
        get()      = prefs.getInt(KEY_SETPOINT_C, NO_CACHED_VALUE)
                         .takeIf { it != NO_CACHED_VALUE }
        set(value) = if (value != null)
                         prefs.edit().putInt(KEY_SETPOINT_C, value).apply()
                     else
                         prefs.edit().remove(KEY_SETPOINT_C).apply()
}
