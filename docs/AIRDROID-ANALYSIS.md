# AirDroid 3.8.0.4 desktop / 4.3.7.1 Android — static observations

Scope: read-only static inspection of the two files supplied by the owner. Nothing was
executed, and no AirDroid code, assets, strings or protocol details were reused in the
AFIF Link app. This note exists to explain *why* a rebuild is the right answer instead of
a patch, and what the rebuild has to match functionally.

## 1. The desktop file is an installer, not the client

| Property | Value |
|---|---|
| File | `AirDroid_Desktop_Client_3.8.0.4.exe` |
| Size | 50,792,888 bytes |
| SHA-256 | `ce7f2dfa2ed42b0013c9342d52b5b870fab1a2ecda4d114c12074887806de60e` |
| Format | PE32 (x86), optional header magic `0x010B` |
| Sections | `.text` 25,506 · `.rdata` 6,386 · `.data` 419,484 · `.ndata` 774,144 · `.rsrc` 176,232 |
| Installer | NSIS (Nullsoft) — `Nullsoft`, `NSIS`, `SFX` present in the image |

Summed section virtual sizes are ≈1.4 MB against a 50.8 MB file, so essentially the whole
payload is a **compressed NSIS data block in the PE overlay**. The `.text` stub only
unpacks it. Consequence: the real Electron application (`resources/app.asar`) cannot be
read without an NSIS-aware extractor (7-Zip `NSIS` handler). None was available on this
host, and the sandboxed provider could not write to `%TEMP%`, so extraction was not
attempted further. This is a recorded gap, not a conclusion about the payload.

## 2. The Android package

| Property | Value |
|---|---|
| Package | `com.sand.airdroid` |
| versionCode / versionName | `40307010` / `4.3.7.1` |
| compileSdk / target | 34 (Android 14) |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| Entries in APK | 4,598 |
| Declarations | 147 activities, 64 services, 31 receivers, 8 providers, 98 permissions, 81 intent filters |

### 2.1 The application code is protected

* `classes.dex` is only 32 KB and contains a loader (`ProtectedSandApp`,
  `ProtectedAppComponentFactory`, `MessageGuardException`, `com.licel.dexprotector`).
* `classes2.dex` … `classes19.dex` are 324-byte placeholders.
* The real code sits in **`assets/classes.dex.dat`, 21,740,557 bytes**, decrypted at
  runtime by native code.

`com.licel.dexprotector` is a commercial DEX packer. Reading that payload would mean
defeating an anti-tamper measure — out of scope here by design, and irrelevant to a
rebuild.

### 2.2 What the native libraries prove about the product

| Library | Role it implies |
|---|---|
| `libvncServer.so` (5.5–6.8 MB per ABI) | RFB/VNC server — full remote screen control |
| `libjingle_peerconnection_so.so` (4.5–8.4 MB) | WebRTC — P2P audio/video and data channel |
| `libadjServer.so` (8.6–14.8 MB) | Device-side service/session engine |
| `libRemoteInput.so` | Injecting remote input events |
| `libMotionDetector.so` | Camera motion detection |
| `libconscrypt_jni.so` | TLS stack |

This matches the advertised feature set: screen mirroring + remote control + file manager
+ SMS/notification relay, transported over WebRTC with VNC for the pixels.

### 2.3 The phone ships its own web UI

`assets/index.html` is a complete bundled web client:

```
version      V4.3.7        apiVersion  p20
webVer       161           publishDate 03/19/2024
encryDes     1             (transport obfuscation enabled)
serverCenterUrl  https://srv3.airdroid.com/
stateUrl         https://stat3.airdroid.com/
uploadUrl        https://upload.airdroid.com/
ucUploadUrl      https://uc.airdroid.com/
```

combined with

```
bundle.properties -> Eclipse Paho MQTT Client
```

So the architecture is: **phone runs a local web server + MQTT session; the desktop and
web clients talk to the account cloud, which brokers a path into that phone session.**

That "local web server on the phone, opened in a window on the desktop" pattern is exactly
the "access window" idea the dashboard needs — and it is the part worth rebuilding,
because the cloud broker is AirDroid's, not yours.

### 2.4 Permissions worth noting

Heavy device management is requested: `READ_SMS`/`SEND_SMS`/`RECEIVE_SMS`,
`READ_CALL_LOG`, `ANSWER_PHONE_CALLS`, `BIND_NOTIFICATION_LISTENER_SERVICE`,
`MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `WRITE_SECURE_SETTINGS`,
`SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`, `PACKAGE_USAGE_STATS`, plus enterprise
permissions for Samsung Knox (`KNOX_*`, 12 of them) and Zebra/Motorola MX
(`ACCESS_MX_MANAGEMENT_FRAMEWORK_SERVICE`).

## 3. What this means for "my own version"

| Goal | Verdict |
|---|---|
| Patch or repackage the AirDroid APK | Not viable. Signature-stripping breaks Play/terms, and the packed DEX makes modification impractical. |
| Reimplement the useful subset | Viable, and what `agent/` does: MQTT transport, telemetry, SMS read/send, notification capture, LAN file server. |
| Replace the cloud broker | Use your own MQTT broker (Mosquitto on the Node-RED host). No third-party account in the path. |

Deliberate differences from AirDroid, so the result stays clearly original:

* No VNC/WebRTC screen streaming in v1 — it needs a MediaProjection consent flow, and it is
  the single largest piece of work. Landed as a roadmap item, not faked.
* No cloud account, no analytics, no ad SDKs, no Knox/MX enterprise surface.
* Everything is explicit and local: one broker URL, one shared token, one port.

## 4. Evidence and gaps

| Claim | Basis |
|---|---|
| PE layout, NSIS wrapper | Direct header/section parsing + REA artifact evidence (`inspect_artifact`, digest `ce7f2dfa…`) |
| Package identity, declarations | AXML decoded from `AndroidManifest.xml` with `re/axml.py` |
| DEX packing | Entry sizes + loader class names in `classes.dex` |
| Native feature inference | Library names and sizes only — **not** behavioural proof |
| Bundled web client config | Literal contents of `assets/index.html` |

Open gaps: the Electron payload inside the NSIS overlay was never read; the packed DEX was
never unpacked; no AirDroid traffic was captured, so no protocol claims are made.
