# Euhomy BLE — Android Controller

Native Android companion app for the **Euhomy CFC-25 portable refrigerator**
(and compatible Tuya BLE fridges). Controls the device **locally over Bluetooth,
with no cloud, no proprietary app, no internet required**.

> **See also:** [ha-euhomy-ble](https://github.com/lyntoo/ha-euhomy-ble) —
> Home Assistant integration for the same device.

---

## Why this app?

The Euhomy CFC-25 uses the **Tuya BLE v3 protocol** under the hood. This app
reimplements that protocol natively so you can control your fridge from an
Android phone without relying on the original Euhomy / Tuya app.

### BLE is single-connection only

A BLE device can only be connected to **one host at a time**. If you run the
Home Assistant integration with a BLE proxy, the proxy holds the connection
and this Android app cannot connect simultaneously — and vice versa.

**Use this Android app when:**
- The fridge is away from home (camping, road trip, cottage…)
- Your Home Assistant instance is unreachable or offline
- You want direct phone control without a smart-home hub

**Use the Home Assistant integration when:**
- The fridge is at home and you want automations, history, and dashboards

Simply close whichever connection you don't need — the fridge will accept the
next connection within a few seconds.

---

## Features

| Control | Description |
|---|---|
| Temperature | Set target temperature (−20 °C to +20 °C / −4 °F to +68 °F) |
| Power | Turn compressor on / off |
| Mode | MAX (full power) or ECO (energy saving) |
| Panel lock | Lock / unlock the physical buttons |
| Temperature unit | Switch display between °C and °F |
| Battery protection | Low / Medium / High cutoff level |
| Battery voltage | Live readout in volts |
| Auto-reconnect | Reconnects automatically if BLE drops |

---

## Download

Pre-built APK (debug-signed, production flavor — no credentials included):

➡️ [`apks/euhomy-production.apk`](apks/euhomy-production.apk)

> **Android 8.0+ (API 26) required.**
> Allow installation from unknown sources in your Android settings before installing.

---

## Getting your device credentials

The app needs four values that identify your specific Tuya BLE device.
You retrieve them once through the **Tuya IoT Platform** (free account).

### Step 1 — Create a Tuya IoT account and project

1. Go to [iot.tuya.com](https://iot.tuya.com) and sign up (free).
2. Click **Cloud → Development → Create Cloud Project**.
3. Choose any name, select **Smart Home** as the industry, and pick the API
   data-center closest to you (**America** for North America, **Europe** for EU).
4. On the next screen, enable at minimum:
   - **Device Status Notification**
   - **IoT Core**

### Step 2 — Link your Euhomy app account

1. In your project, go to **Devices → Link Tuya App Account**.
2. Scan the QR code with the **Tuya Smart** app on your phone
   (the Euhomy app is built on Tuya — use the same login credentials).
3. Your fridge should appear under **All Devices**.

### Step 3 — Retrieve credentials with tinytuya

Install [tinytuya](https://github.com/jasonacox/tinytuya) and run its wizard:

```bash
pip install tinytuya
python3 -m tinytuya wizard
```

Enter your **Access ID** and **Access Secret** from the Tuya project
Overview page when prompted. The wizard will output a `devices.json` file
containing all your devices. Find your fridge entry:

```json
{
  "name": "Euhomy Fridge",
  "id": "your_device_id_here",
  "key": "your_local_key_here",
  "uuid": "your_uuid_here",
  "mac": "XX:XX:XX:XX:XX:XX"
}
```

> **Note:** The `uuid` field may equal the `id` field on some Tuya accounts —
> this is normal, just enter the same value in both fields.

### Step 4 — Find the BLE MAC address

The MAC address is **not always in the tinytuya output**. To find it:

- **Android:** Use a BLE scanner app (e.g. *nRF Connect* or *BLE Scanner*).
  Power-cycle the fridge, then look for a device advertising with the name
  **"TY"** or manufacturer ID **0x07D0** (Tuya). The address shown is the MAC.
- **Linux:** `sudo bluetoothctl scan on` — the fridge appears as `TY`.

---

## First launch — entering credentials

1. Install the APK and open the app.
2. The app starts on the **BLE scan screen** — tap your fridge in the list
   (it appears as **"TY"**), or tap **Enter manually** if scan doesn't show it.
3. Fill in the four fields:
   - **BLE MAC Address** — `XX:XX:XX:XX:XX:XX` format
   - **Local Key** — 16-character string from tinytuya
   - **Device ID** — `id` field from tinytuya
   - **UUID** — `uuid` field from tinytuya (may equal Device ID)
4. Tap **Save** — the app connects immediately and loads the fridge state.

Credentials are stored in **Android EncryptedSharedPreferences** (hardware-backed
keystore) and never leave the device.

---

## Build from source

```bash
git clone https://github.com/lyntoo/android-euhomy-ble
cd android-euhomy-ble

# Production build (no credentials baked in)
./gradlew assembleProductionDebug

# Output: app/build/outputs/apk/production/debug/app-production-debug.apk
```

**Requirements:** Android SDK (API 34), JDK 17+, Gradle 8.

The project has two public build flavors:

| Flavor | Description |
|---|---|
| `production` | Standard — user enters credentials at first launch |
| `dev` | Debug — settings screen always accessible from the top bar |

---

## How it works

The fridge speaks **Tuya BLE protocol v3**:

1. **Handshake:** The app sends a `DEVICE_INFO` command encrypted with
   `MD5(local_key[:6])`. The device replies with a random nonce (`srand`).
2. **Session key:** `MD5(local_key[:6] + srand)` is derived and used for all
   subsequent AES-128-CBC encryption.
3. **Data Points (DPs):** Each controllable feature is a DP — a `[id, type, value]`
   triple. The app sends `SEND_DPS` commands and receives `RECEIVE_DP`
   notifications.
4. **Heartbeat:** A `DEVICE_STATUS` query is sent every 30 seconds to keep the
   connection alive and refresh all DP values.

All protocol code is in [`app/src/main/kotlin/com/euhomy/fridge/ble/`](app/src/main/kotlin/com/euhomy/fridge/ble/).

---

## Confirmed Data Points (Euhomy CFC-25)

| DP | Type | Description | Access |
|---|---|---|---|
| 101 | BOOL | Power on/off | R/W |
| 102 | BOOL | Panel lock | R/W |
| 103 | ENUM | Mode: `0x00`=MAX, `0x01`=ECO | R/W |
| 104 | ENUM | Battery protection: `0x00`=L, `0x01`=M, `0x02`=H | R/W |
| 105 | ENUM | Temperature unit: `0x00`=°C, `0x01`=°F | R/W |
| 112 | INT | Current temperature (°C) | R |
| 114 | INT | Target temperature (°C) | R/W |
| 117 | INT | Current temperature (°F mirror) | R |
| 119 | INT | Target temperature (°F mirror) | R |
| 122 | INT | Battery voltage (mV) | R |

---

## Error codes (display-only)

The fridge displays error codes on its physical front panel. These codes are **not transmitted via BLE** — no Data Point is pushed when an error occurs. The panel shows the code locally; the phone app receives no indication.

| Code | Meaning |
|---|---|
| E1 | Battery overvoltage |
| E2 | Fan motor fault |
| E3 | Temperature instability |
| E4 | Compressor fault |
| E5 | PCB fault |
| E6 | Temperature sensor fault |

> Confirmed by live BLE captures with E6 (sensor fault) active: only the normal status DPs
> (temperature, battery, mode…) were received — no fault DP appeared at any point.

---

## License

MIT — do whatever you want with it.
