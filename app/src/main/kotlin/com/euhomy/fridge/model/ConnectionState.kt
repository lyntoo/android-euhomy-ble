package com.euhomy.fridge.model

sealed class ConnectionState {
    /** No active BLE session. */
    data object Disconnected : ConnectionState()

    /** BLE connection established, handshake in progress. */
    data object Connecting : ConnectionState()

    /** Handshake complete, DPs are being exchanged. */
    data object Connected : ConnectionState()

    /** An error occurred — [message] describes the problem. */
    data class Error(val message: String) : ConnectionState()
}
