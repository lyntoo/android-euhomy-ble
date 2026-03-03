package com.euhomy.fridge.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import com.euhomy.fridge.data.DeviceCredentials
import com.euhomy.fridge.model.ConnectionState
import com.euhomy.fridge.model.FridgeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

private const val TAG = "TuyaBleClient"
private const val HANDSHAKE_TIMEOUT_MS = 30_000L
private const val HEARTBEAT_INTERVAL_MS = 30_000L
private const val RECONNECT_DELAY_MS = 5_000L
private const val MAX_RECONNECT_ATTEMPTS = 5

// Descriptor UUID for Client Characteristic Configuration (CCCD)
private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * Tuya BLE v3 client for the Euhomy CFC-25.
 *
 * Implements the full 2-step handshake (DEVICE_INFO → PAIR) and then
 * exposes a simple API for DP reads/writes.
 *
 * Thread safety: all GATT callbacks arrive on the Android BLE thread; they
 * are dispatched to [Dispatchers.Main] for StateFlow updates, and to the
 * internal coroutine scope for protocol logic.
 */
class TuyaBleClient(
    private val context: Context,
    private val credentials: DeviceCredentials,
) {
    // ── Keys ──────────────────────────────────────────────────────────────────
    private val localKey6: ByteArray = credentials.localKey.take(6).toByteArray(Charsets.US_ASCII)
    private val loginKey:  ByteArray = md5(localKey6)
    private var sessionKey: ByteArray? = null

    // ── State ─────────────────────────────────────────────────────────────────
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _fridgeState = MutableStateFlow(FridgeState())
    val fridgeState: StateFlow<FridgeState> = _fridgeState.asStateFlow()

    // ── Internal state ────────────────────────────────────────────────────────
    private var gatt: BluetoothGatt? = null
    private var seqCounter = 0
    private val mutex = Mutex()
    // Serialises all GATT characteristic writes so packets from different messages never interleave.
    private val writeMutex = Mutex()
    // Timestamp of the last completed write — used to enforce a minimum inter-message gap.
    private var lastWriteMs = 0L
    private val MIN_MESSAGE_GAP_MS = 150L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Pending handshake responses (seq → deferred result)
    private val pending = mutableMapOf<Int, CompletableDeferred<Int>>()

    // Packet reassembler
    private val reassembler = PacketReassembler { payload ->
        scope.launch { handlePayload(payload) }
    }

    private var reconnectAttempts = 0
    private var heartbeatJob: kotlinx.coroutines.Job? = null

    // ── Public API ────────────────────────────────────────────────────────────

    val isConnected: Boolean
        get() = _connectionState.value == ConnectionState.Connected

    /**
     * Connect to the device, run the Tuya handshake, and start heartbeat.
     */
    suspend fun connect() {
        mutex.withLock {
            if (_connectionState.value == ConnectionState.Connected ||
                _connectionState.value == ConnectionState.Connecting) return

            _connectionState.value = ConnectionState.Connecting
            sessionKey = null
            seqCounter = 0
            pending.values.forEach { it.cancel() }
            pending.clear()
            reassembler.reset()
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device: BluetoothDevice? = bluetoothManager.adapter
            ?.getRemoteDevice(credentials.macAddress)

        if (device == null) {
            _connectionState.value = ConnectionState.Error("Device not found: ${credentials.macAddress}")
            return
        }

        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    /**
     * Disconnect cleanly and cancel the heartbeat.
     */
    suspend fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = ConnectionState.Disconnected
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    /**
     * Write a single DP to the device.
     */
    suspend fun writeDP(dp: TuyaDP) {
        if (!isConnected) error("Not connected")
        val payload = encodeDP(dp)
        Log.d(TAG, "writeDP: id=${dp.dpId} type=${dp.dpType} value=${dp.value} hex=${payload.toHex()}")
        // Optimistic update: reflect the command in the UI immediately without waiting for device echo.
        // Convert ByteArray ENUM values to String, matching what decodeAllDPs produces.
        val optimistic = if (dp.dpType == DpType.ENUM && dp.value is ByteArray) {
            TuyaDP(dp.dpId, dp.dpType, (dp.value as ByteArray).toString(Charsets.UTF_8))
        } else {
            dp
        }
        applyDPs(listOf(optimistic))
        sendCommand(TuyaCmd.SEND_DPS, payload)
    }

    /**
     * Request all current DP values from the device.
     */
    suspend fun queryAllDPs() {
        if (!isConnected) error("Not connected")
        sendCommand(TuyaCmd.DEVICE_STATUS, ByteArray(0))
    }

    /**
     * Release resources. Call when the ViewModel is cleared.
     */
    fun destroy() {
        scope.cancel()
        gatt?.close()
        gatt = null
    }

    // ── GATT callback ─────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected, requesting MTU")
                    gatt.requestMtu(23)   // payload = 20 bytes = BLE_MTU
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "GATT disconnected (status=$status)")
                    handleDisconnect()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu, discovering services")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                handleDisconnect()
                return
            }
            Log.d(TAG, "Services discovered, enabling notifications")
            enableNotifications(gatt)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid == UUID.fromString(TuyaUuids.NOTIFY_CHAR)) {
                val data = characteristic.value ?: return
                Log.d(TAG, "RAW NOTIFY (${data.size}B): ${data.toHex()}")
                reassembler.feed(data)
            }
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == UUID.fromString(TuyaUuids.NOTIFY_CHAR)) {
                Log.d(TAG, "RAW NOTIFY (${value.size}B): ${value.toHex()}")
                reassembler.feed(value)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "CCCD enabled, starting handshake")
                scope.launch { runHandshake() }
            } else {
                Log.e(TAG, "CCCD write failed: $status")
                handleDisconnect()
            }
        }
    }

    // ── Notifications setup ───────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun enableNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(UUID.fromString(TuyaUuids.SERVICE))
        if (service == null) {
            Log.e(TAG, "Tuya service not found")
            handleDisconnect()
            return
        }
        val notifyChar = service.getCharacteristic(UUID.fromString(TuyaUuids.NOTIFY_CHAR))
        if (notifyChar == null) {
            Log.e(TAG, "Notify characteristic not found")
            handleDisconnect()
            return
        }
        // Step 1: enable locally
        gatt.setCharacteristicNotification(notifyChar, true)
        // Step 2: write CCCD descriptor (Android does NOT do this automatically)
        val cccd = notifyChar.getDescriptor(CCCD_UUID)
        if (cccd != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
        } else {
            // No CCCD — try handshake directly
            Log.w(TAG, "CCCD descriptor not found, proceeding anyway")
            scope.launch { runHandshake() }
        }
    }

    // ── Handshake ─────────────────────────────────────────────────────────────

    private suspend fun runHandshake() {
        try {
            withTimeout(HANDSHAKE_TIMEOUT_MS) {
                // Step 1: DEVICE_INFO → derive session_key
                val infoDeferred = CompletableDeferred<Int>()
                val infoSeq = nextSeq()
                pending[infoSeq] = infoDeferred
                sendRaw(TuyaCmd.DEVICE_INFO, ByteArray(0), infoSeq)
                infoDeferred.await()   // blocks until _CMD_DEVICE_INFO response sets session_key

                // Step 2: PAIR
                val pairDeferred = CompletableDeferred<Int>()
                val pairSeq = nextSeq()
                pending[pairSeq] = pairDeferred
                sendRaw(TuyaCmd.PAIR, buildPairPayload(), pairSeq)
                val pairResult = pairDeferred.await()
                if (pairResult != 0 && pairResult != 2) {
                    error("Pairing rejected by device: result=$pairResult")
                }
            }

            Log.i(TAG, "Handshake complete — device paired")
            withContext(Dispatchers.Main) {
                _connectionState.value = ConnectionState.Connected
            }
            reconnectAttempts = 0

            // Query initial state
            sendCommand(TuyaCmd.DEVICE_STATUS, ByteArray(0))

            // Start heartbeat
            startHeartbeat()

        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed: $e")
            withContext(Dispatchers.Main) {
                _connectionState.value = ConnectionState.Error("Handshake failed: ${e.message}")
            }
            handleDisconnect()
        }
    }

    private fun buildPairPayload(): ByteArray {
        val buf = ByteArray(44)
        val uuidBytes  = credentials.uuid.toByteArray(Charsets.US_ASCII)
        val keyBytes   = localKey6
        val devIdBytes = credentials.deviceId.toByteArray(Charsets.US_ASCII)
        var offset = 0
        uuidBytes.copyInto(buf, offset);  offset += uuidBytes.size
        keyBytes.copyInto(buf, offset);   offset += keyBytes.size
        devIdBytes.copyInto(buf, offset)
        return buf
    }

    // ── Send ─────────────────────────────────────────────────────────────────

    private fun nextSeq(): Int {
        seqCounter = (seqCounter + 1) and 0x7FFFFFFF
        return seqCounter
    }

    private suspend fun sendCommand(code: Int, data: ByteArray) {
        sendRaw(code, data, nextSeq())
    }

    private suspend fun sendRaw(code: Int, data: ByteArray, seq: Int, responseTo: Int = 0) {
        val currentGatt = gatt ?: return
        val packets = buildPackets(
            seqNum     = seq,
            code       = code,
            data       = data,
            loginKey   = loginKey,
            sessionKey = sessionKey,
            responseTo = responseTo,
        )
        val service   = currentGatt.getService(UUID.fromString(TuyaUuids.SERVICE)) ?: return
        val writeChar = service.getCharacteristic(UUID.fromString(TuyaUuids.WRITE_CHAR)) ?: return

        // Hold writeMutex for the entire message so packets never interleave with other messages.
        // Enforce a minimum gap between consecutive messages: rapid button presses queue up
        // here and are spaced out, preventing the device from being overwhelmed and disconnecting.
        writeMutex.withLock {
            val elapsed = System.currentTimeMillis() - lastWriteMs
            if (elapsed < MIN_MESSAGE_GAP_MS) delay(MIN_MESSAGE_GAP_MS - elapsed)

            for (packet in packets) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    currentGatt.writeCharacteristic(
                        writeChar,
                        packet,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    @Suppress("DEPRECATION")
                    writeChar.value = packet
                    @Suppress("DEPRECATION")
                    val ok = currentGatt.writeCharacteristic(writeChar)
                    if (!ok) Log.w(TAG, "writeCharacteristic() returned false — packet dropped")
                }
                delay(20)
            }
            lastWriteMs = System.currentTimeMillis()
        }
    }

    // ── Incoming message handling ─────────────────────────────────────────────

    private suspend fun handlePayload(payload: ByteArray) {
        val msg = parseMessage(payload, loginKey, sessionKey) ?: return

        when (msg) {
            is ParsedMessage.DeviceInfo -> {
                if (msg.srand.isNotEmpty()) {
                    sessionKey = md5(localKey6 + msg.srand)
                    Log.d(TAG, "session_key derived (srand=${msg.srand.toHex()})")
                }
                pending.remove(msg.responseTo)?.complete(0)
            }

            is ParsedMessage.PairResult -> {
                Log.d(TAG, "PAIR result=${msg.result}")
                pending.remove(msg.responseTo)?.complete(msg.result)
            }

            is ParsedMessage.DpWriteAck -> {
                Log.d(TAG, "DP write ACK result=${msg.result}")
            }

            is ParsedMessage.StatusAck -> {
                pending.remove(msg.responseTo)?.complete(msg.result)
            }

            is ParsedMessage.DpUpdate -> {
                applyDPs(msg.dps)
                // ACK with the same code the device sent (RECEIVE_DP or RECEIVE_TIME_DP)
                sendRaw(msg.code, ByteArray(0), nextSeq(), msg.seqNum)
            }

            is ParsedMessage.SignedDpUpdate -> {
                applyDPs(msg.dps)
                val ackData = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                    .putShort(msg.dpSeq.toShort())
                    .put(msg.flags.toByte())
                    .put(0)
                    .array()
                sendRaw(msg.code, ackData, nextSeq(), msg.seqNum)
            }

            is ParsedMessage.Time1Request -> {
                val ts = System.currentTimeMillis()
                val tz = -(java.util.TimeZone.getDefault().rawOffset / 36_000)
                val resp = ts.toString().toByteArray() +
                    ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(tz.toShort()).array()
                sendRaw(TuyaCmd.TIME1_REQ, resp, nextSeq(), msg.seqNum)
            }

            is ParsedMessage.Time2Request -> {
                val cal = java.util.Calendar.getInstance()
                val tz  = -(java.util.TimeZone.getDefault().rawOffset / 36_000)
                val resp = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
                    .put((cal.get(java.util.Calendar.YEAR) % 100).toByte())
                    .put((cal.get(java.util.Calendar.MONTH) + 1).toByte())
                    .put(cal.get(java.util.Calendar.DAY_OF_MONTH).toByte())
                    .put(cal.get(java.util.Calendar.HOUR_OF_DAY).toByte())
                    .put(cal.get(java.util.Calendar.MINUTE).toByte())
                    .put(cal.get(java.util.Calendar.SECOND).toByte())
                    .put(cal.get(java.util.Calendar.DAY_OF_WEEK).toByte())
                    .putShort(tz.toShort())
                    .array()
                sendRaw(TuyaCmd.TIME2_REQ, resp, nextSeq(), msg.seqNum)
            }

            is ParsedMessage.Unknown ->
                Log.w(TAG, "Unhandled code=0x${msg.code.toString(16)} data=${msg.data.toHex()}")
        }
    }

    private suspend fun applyDPs(dps: List<TuyaDP>) {
        Log.d(TAG, "DP update: ${dps.map { "${it.dpId}=${it.value}" }}")
        withContext(Dispatchers.Main) {
            var state = _fridgeState.value
            for (dp in dps) {
                // Device sends ENUM DPs as raw bytes: 0x00, 0x01, 0x02.
                // decodeAllDPs decodes them as UTF-8 → "\u0000", "\u0001", "\u0002".
                state = when (dp.dpId) {
                    DpId.TEMP_CURRENT -> state.copy(currentTempC = dp.value as? Int)
                    DpId.TEMP_SET     -> state.copy(setpointC    = dp.value as? Int)
                    DpId.SWITCH       -> state.copy(isOn         = dp.value as? Boolean)
                    DpId.BATTERY_MV   -> state.copy(batteryMv    = dp.value as? Int)
                    DpId.MODE         -> state.copy(mode = when (dp.value as? String) {
                        "\u0001" -> "eco"
                        else     -> "max"   // 0x00
                    })
                    DpId.TEMP_UNIT    -> state.copy(isFahrenheit = (dp.value as? String) == "\u0001")
                    DpId.LOCK         -> state.copy(isLocked = when (val v = dp.value) {
                        is Boolean -> v
                        is String  -> v == "\u0001"
                        else       -> state.isLocked
                    })
                    DpId.BATTERY_PROT -> state.copy(batteryProt = when (dp.value as? String) {
                        "\u0001" -> "m"
                        "\u0002" -> "h"
                        else     -> "l"    // 0x00
                    })
                    else -> state
                }
            }
            _fridgeState.value = state
        }
    }

    // ── Heartbeat ────────────────────────────────────────────────────────────

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isConnected) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (isConnected) {
                    Log.d(TAG, "Heartbeat — querying DPs")
                    sendCommand(TuyaCmd.DEVICE_STATUS, ByteArray(0))
                }
            }
        }
    }

    // ── Disconnect / reconnect ────────────────────────────────────────────────

    private fun handleDisconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        pending.values.forEach { it.cancel() }
        pending.clear()
        gatt?.close()
        gatt = null

        scope.launch {
            withContext(Dispatchers.Main) {
                _connectionState.value = ConnectionState.Disconnected
            }
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++
                Log.w(TAG, "Reconnecting (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)…")
                delay(RECONNECT_DELAY_MS)
                connect()
            } else {
                Log.e(TAG, "Max reconnect attempts reached")
                withContext(Dispatchers.Main) {
                    _connectionState.value = ConnectionState.Error("Unable to reconnect after $MAX_RECONNECT_ATTEMPTS attempts")
                }
            }
        }
    }
}

// ── Extension ────────────────────────────────────────────────────────────────
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

