package com.euhomy.fridge.model

/**
 * Complete snapshot of the fridge's reported state.
 * Fields are null when the device has not yet reported them.
 */
data class FridgeState(
    /** Actual internal temperature (°C). DP 112. */
    val currentTempC:   Int?     = null,
    /** User-set target temperature (°C). DP 114. */
    val setpointC:      Int?     = null,
    /** Power state. DP 101. */
    val isOn:           Boolean? = null,
    /** Cooling mode. DP 103. */
    val mode:           String?  = null,   // "max" | "eco"
    /** Battery voltage in millivolts. DP 122. */
    val batteryMv:      Int?     = null,
    /** Temperature unit. DP 105. */
    val isFahrenheit:   Boolean? = null,
    /** Panel lock. DP 102. */
    val isLocked:       Boolean? = null,
    /** Battery protection level. DP 104. */
    val batteryProt:    String?  = null,   // "l" | "m" | "h"
    /** Raw fault bitmap. DP when identified. */
    val faultBitmap:    Long?    = null,
    /** All raw DPs received from the device, keyed by DP ID. Used for debug/discovery. */
    val rawDps:         Map<Int, String> = emptyMap(),
) {
    /** Battery voltage in volts (convenience). */
    val batteryVolts: Float? get() = batteryMv?.let { it / 1000f }

    /** True when any fault bit is set. */
    val hasFault: Boolean get() = faultBitmap != null && faultBitmap != 0L
}
