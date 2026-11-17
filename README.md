# TextPup Companion App (Android)

> Zero-knowledge, dual-SIM cellular SMS & MMS companion gateway for Android devices.

The **TextPup Companion** (`io.loperlabs.textpup`) transforms an Android device into a private, self-hosted cellular SMS gateway. It pairs securely with your self-hosted **TextPup Service** via a single QR code scan, forwarding inbound SMS/MMS messages and dispatching outbound messages over your carrier networks with dual-SIM routing.

---

## Features

- **Instant QR Code Pairing:** Pair with your private TextPup server in seconds via an ephemeral cryptographic token.
- **Dual-SIM Carrier Telemetry:** Reports SIM slots, carrier names, phone numbers, and battery status.
- **Bi-Directional Cellular Messaging:** 
  - Forwards inbound SMS and MMS to your server in real time via encrypted WebSockets.
  - Receives outbound dispatch commands and sends via chosen SIM subscription (`SubscriptionManager`).
- **One-Tap SMS History Sync:** Synchronizes existing phone conversations into the server with chronological ordering and carrier attribution.
- **Persistent Enclave Service:** Runs an efficient Android foreground service with a partial wake lock to guarantee continuous connectivity without sleeping.
- **Multi-Device Ready:** Works seamlessly with the TextPup Multi-Device Cellular Hub.

---

## Tech Stack & Architecture

- **Language:** Kotlin 1.9+
- **UI Framework:** Jetpack Compose (Material 3)
- **Networking:** OkHttp 4 (Persistent WebSocket client with auto-reconnect and ping telemetry)
- **Camera & Scanning:** CameraX + Google ML Kit Barcode Scanning
- **Telephony:** Android Telephony API (`SubscriptionManager`, `SmsManager`, MMS `Telephony.Mms` Content Provider)
- **Min SDK:** 26 (Android 8.0 Oreo)
- **Target SDK:** 35 (Android 15)

---

## Getting Started

### Prerequisites

- Android Studio Koala / Ladybug or newer
- JDK 17
- Android SDK Platform 35 & Build Tools

### Building the Debug APK

```bash
# Clone the repository
git clone https://github.com/your-org/textpup-app.git
cd textpup-app

# Build debug APK
./gradlew assembleDebug

# Output APK:
# app/build/outputs/apk/debug/app-debug.apk
```

### Installing via ADB

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Permissions & Telephony Enclave

On first launch, TextPup requires standard telephony permissions to function as your SMS gateway:

1. `RECEIVE_SMS`, `READ_SMS`, `SEND_SMS`: Inbound/outbound SMS routing.
2. `RECEIVE_MMS`, `READ_MMS`: Inbound/outbound MMS multimedia extraction.
3. `READ_PHONE_STATE`: Multi-SIM subscription detection.
4. `CAMERA`: Scanning the setup/pairing QR code.
5. `POST_NOTIFICATIONS`: Foreground service status indicator.

---

## License

MIT License. See `LICENSE` for details.
