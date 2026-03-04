package com.euhomy.fridge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.euhomy.fridge.ble.DpId
import com.euhomy.fridge.ble.DpType
import com.euhomy.fridge.ble.TuyaBleClient
import com.euhomy.fridge.ble.TuyaDP
import com.euhomy.fridge.ble.TempLimits
import com.euhomy.fridge.data.CredentialsStore
import com.euhomy.fridge.data.DefaultCredentials
import com.euhomy.fridge.data.PreferencesRepository
import com.euhomy.fridge.model.ConnectionState
import com.euhomy.fridge.model.FridgeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FridgeViewModel(app: Application) : AndroidViewModel(app) {

    private val store = CredentialsStore(app)
    private val prefs = PreferencesRepository(app)

    // Use stored credentials; fall back to baked-in preset (private build only).
    private val credentials = store.load() ?: DefaultCredentials.preset

    // Seed the initial state with the last persisted setpoint so the slider
    // shows the correct value immediately — before the device pushes its state.
    private val client: TuyaBleClient? = credentials?.let {
        TuyaBleClient(app, it, FridgeState(setpointC = prefs.lastSetpointC))
    }

    val connectionState: StateFlow<ConnectionState> =
        client?.connectionState ?: MutableStateFlow(ConnectionState.Disconnected)
    val fridgeState: StateFlow<FridgeState> =
        client?.fridgeState ?: MutableStateFlow(FridgeState())

    val hasCredentials: Boolean = credentials != null

    init {
        if (client != null) {
            viewModelScope.launch { client.connect() }
            // Persist setpoint whenever the device confirms a new value.
            viewModelScope.launch {
                fridgeState.collect { state ->
                    state.setpointC?.let { prefs.lastSetpointC = it }
                }
            }
        }
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    fun setTemperature(tempC: Int) {
        val clamped = tempC.coerceIn(TempLimits.MIN_C, TempLimits.MAX_C)
        viewModelScope.launch {
            client?.writeDP(TuyaDP(DpId.TEMP_SET, DpType.INT, clamped))
        }
    }

    fun setMode(mode: String) {
        // Device expects raw bytes: 0x00 = MAX, 0x01 = ECO (NOT the strings "max"/"eco")
        val raw = if (mode == "eco") byteArrayOf(0x01) else byteArrayOf(0x00)
        viewModelScope.launch {
            client?.writeDP(TuyaDP(DpId.MODE, DpType.ENUM, raw))
        }
    }

    fun setPower(on: Boolean) {
        viewModelScope.launch {
            client?.writeDP(TuyaDP(DpId.SWITCH, DpType.BOOL, on))
        }
    }

    fun setLock(locked: Boolean) {
        // Send as BOOL (same as power/switch) — the device firmware likely expects
        // BOOL true/false, not ENUM. Sending ENUM+0x00 was silently ignored.
        viewModelScope.launch {
            client?.writeDP(TuyaDP(DpId.LOCK, DpType.BOOL, locked))
        }
    }

    fun setTempUnit(fahrenheit: Boolean) {
        // Device expects raw bytes: 0x00 = Celsius, 0x01 = Fahrenheit
        val raw = if (fahrenheit) byteArrayOf(0x01) else byteArrayOf(0x00)
        viewModelScope.launch {
            client?.writeDP(TuyaDP(DpId.TEMP_UNIT, DpType.ENUM, raw))
        }
    }

    fun setBatteryProt(level: String) {   // "l" | "m" | "h"
        // Device expects raw bytes: 0x00 = Low, 0x01 = Medium, 0x02 = High
        val raw = when (level) { "m" -> byteArrayOf(0x01); "h" -> byteArrayOf(0x02); else -> byteArrayOf(0x00) }
        viewModelScope.launch {
            client?.writeDP(TuyaDP(DpId.BATTERY_PROT, DpType.ENUM, raw))
        }
    }

    fun refresh() {
        viewModelScope.launch {
            client?.queryAllDPs()
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            client?.disconnect()
            client?.connect()
        }
    }

    override fun onCleared() {
        super.onCleared()
        client?.destroy()
    }
}
