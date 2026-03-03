package com.euhomy.fridge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.euhomy.fridge.ble.BleScanner
import com.euhomy.fridge.model.BleDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = BleScanner(app)

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var scanJob: Job? = null

    val isBluetoothEnabled: Boolean get() = scanner.isBluetoothEnabled()

    fun startScan() {
        if (_isScanning.value) return
        _devices.value = emptyList()
        _error.value   = null
        _isScanning.value = true
        scanJob = viewModelScope.launch {
            scanner.scan()
                .catch { e ->
                    _error.value = e.message
                    _isScanning.value = false
                }
                .collect { _devices.value = it }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
