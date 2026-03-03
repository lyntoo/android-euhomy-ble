# Tuya BLE protocol — keep data classes used in serialisation
-keep class com.euhomy.fridge.ble.TuyaDP { *; }
-keep class com.euhomy.fridge.data.DeviceCredentials { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose compiler output
-keep class androidx.compose.** { *; }
