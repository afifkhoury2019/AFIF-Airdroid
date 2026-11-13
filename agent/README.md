# AFIF Link — Android device agent

Kotlin app that links one Android phone to the PowerChain Node-RED dashboard over MQTT.

| | |
|---|---|
| Application ID | `uk.afif.link` |
| Min / target SDK | 26 / 34 |
| Language | Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7 |
| Transport | Eclipse Paho MQTT v3 (TCP or WebSocket) |

## Build

The repo builds in GitHub Actions — no local Android SDK required:

```
Actions → build → Build AFIF Link APK → Artifacts → afif-link-debug-<version>.apk
```

A signed release needs three repository secrets plus the keystore:

| Secret | Value |
|---|---|
| `KEYSTORE_B64` | `base64 -w0 your.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

With no `KEYSTORE_B64` the workflow produces a debug APK (installable, not publishable).

Locally, if you do have a JDK 17 + Android SDK:

```bash
cd agent
gradle assembleDebug
```

## If Google Play Protect blocks the install

Play Protect flags **sideloaded APKs that request SMS access** and are signed with a
throwaway debug key. The wording ("can request sensitive data … identity theft") describes
the *permission pattern*, not evidence of malicious code in this app — it is the same
heuristic that catches real banking trojans.

CI produces two builds so you can pick the risk you actually need:

| File | SMS access | Notes |
|---|---|---|
| `afif-link-standard-*.apk` | **none** | no telephony code compiled in; far less likely to be flagged |
| `afif-link-sms-*.apk` | read / send / receive | the warning is expected — this is the exact pattern Play Protect looks for |

To install anyway while testing: **Play Store → profile → Play Protect → ⚙ → turn off
"Scan apps with Play Protect"**, install, then turn scanning back on.

Two more things that trip people up:

* **Uninstall the previous build first.** Each CI run generates a fresh debug keystore, so
  consecutive debug APKs have different signatures and Android refuses the upgrade with
  "App not installed".
* **A signed release removes the debug flag**, which is the single biggest scoring factor.
  Add the four keystore secrets below and CI switches to `assemble*Release` automatically.
  That gives you a stable signature, so updates install over the top.

For wider distribution the clean answer is a Play Console **internal testing** track —
Google signs the app, no warning, no sideloading.

## First run

1. Install the APK, open **AFIF Link**.
2. Grant SMS + notification permissions when asked; tap **Notification access** and enable
   the service (needed for `notify.dump`).
3. Enter the broker, e.g. `tcp://192.168.1.10:1883` or `ws://192.168.1.10:9001/mqtt`.
4. Set a device id (`phone-01`) and a shared token. Use the **same** token in the flow.
5. **Save & start**. Status turns `connected`, then `subscribed afif/link/phone-01/cmd`.

## Protocol

Envelope in, on `afif/link/<device>/cmd`:

```json
{ "id": "42", "cmd": "sms.send", "args": { "to": "+9627…", "text": "hi" }, "token": "…" }
```

Reply on `afif/link/<device>/res`:

```json
{ "id": "42", "cmd": "sms.send", "ok": true, "ts": 1731690000000 }
```

| Command | Args | Effect |
|---|---|---|
| `ping` | — | liveness |
| `info` | — | battery, charging, network, IP, storage, uptime, model |
| `sms.list` | `limit` | newest inbox messages *(sms flavour only)* |
| `sms.send` | `to`, `text` | sends SMS, multipart split handled *(sms flavour only)* |
| `notify.dump` | `limit` | cached notifications (needs notification access) |
| `file.list` | `path` | JSON listing of the app's external files dir |
| `toast` | `text` | shows a toast on the phone |
| `vibrate` | `ms` | vibrates |

In the `standard` flavour the SMS commands reply
`{"ok": false, "message": "SMS is not part of this build; use the -sms flavour"}`.

Telemetry on `afif/link/<device>/state` every 30 s; inbound SMS on `…/event`.

## Local file window

`http://<phone-ip>:8080/?token=<shared token>` serves the app's external files directory
(`/sdcard/Android/data/uk.afif.link/files`). Read-only, traversal-checked, token-gated.

Point the dashboard **Access Window** preset "Phone agent (LAN)" at it. On an `https://`
dashboard the browser blocks `http://` frames — use a reverse proxy or open it in a tab.

## Not implemented

* Screen mirroring / remote input (MediaProjection consent flow, VNC or WebRTC).
* Camera, contacts, call log.
* Encryption beyond MQTT TLS.
