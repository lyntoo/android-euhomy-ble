package com.euhomy.fridge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.euhomy.fridge.data.CredentialsStore
import com.euhomy.fridge.data.DeviceCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SetupResult {
    data object Idle    : SetupResult()
    data object Saving  : SetupResult()
    data object Success : SetupResult()
    data class  Error(val message: String) : SetupResult()
}

class SetupViewModel(app: Application) : AndroidViewModel(app) {

    private val store = CredentialsStore(app)

    private val _result = MutableStateFlow<SetupResult>(SetupResult.Idle)
    val result: StateFlow<SetupResult> = _result.asStateFlow()

    // Pre-fill form if credentials already stored
    val existing: DeviceCredentials? = store.load()

    fun save(
        macAddress: String,
        localKey:   String,
        deviceId:   String,
        uuid:       String,
        deviceName: String,
    ) {
        val mac = macAddress.trim().uppercase()
        val key = localKey.trim()
        val id  = deviceId.trim()
        val u   = uuid.trim()

        if (mac.isBlank() || key.isBlank() || id.isBlank() || u.isBlank()) {
            _result.value = SetupResult.Error("All fields are required")
            return
        }
        if (!MAC_REGEX.matches(mac)) {
            _result.value = SetupResult.Error("Invalid MAC address format (XX:XX:XX:XX:XX:XX)")
            return
        }

        viewModelScope.launch {
            _result.value = SetupResult.Saving
            try {
                store.save(DeviceCredentials(mac, key, id, u, deviceName.ifBlank { "Euhomy Fridge" }))
                _result.value = SetupResult.Success
            } catch (e: Exception) {
                _result.value = SetupResult.Error("Save failed: ${e.message}")
            }
        }
    }

    fun clearCredentials() {
        store.clear()
    }

    private companion object {
        val MAC_REGEX = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
    }
}
