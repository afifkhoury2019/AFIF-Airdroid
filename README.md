# PowerChain Engineering Tools

Node-RED dashboard work for `powerchain.uk`, plus **AFIF Link** — an original Android
device agent that does the job AirDroid was attached for, without AirDroid's cloud.

```
powerchain/
├─ nodered/                  Node-RED tab: access window + engineering calculators
│  ├─ templates/             the ui_template HTML (edit these, not the JSON)
│  ├─ build_flow.py          regenerates the importable flows from the templates
│  ├─ validate_flow.py       structural checks (ids, groups, wires, tags)
│  ├─ flows-engineering-tab.json     <- import this
│  └─ flows-agent-bridge.json        <- optional, MQTT link to the phone
├─ agent/                    AFIF Link Android app (Kotlin)
├─ docs/AIRDROID-ANALYSIS.md what the supplied APK/EXE actually are
└─ .github/workflows/build.yml  CI: validate flows, build APK, publish release
```

## 1 · The dashboard tab

`nodered/flows-engineering-tab.json` is a single importable flow that adds a
**Engineering Tools** tab with four groups:

| Group | What it is |
|---|---|
| Access Window | an embedded, chrome-less window with LED status, URL bar, reload / maximise / pop-out; defaults to `https://powerchain.uk/ui/#!/5` |
| Engineering Toolkit | eight live calculators (3-phase power, energy cost, CT/PT scaling, voltage drop, PF correction, transformer sizing, motor FLA, load factor) |
| Calculation Log | rolling list of whatever you pushed out of the toolkit |
| Link Status | battery / network / IP / free disk of the phone agent |

**Import:** Node-RED → ☰ → Import → paste `flows-engineering-tab.json` → *Import*.
Existing tabs are untouched; the import is append-only.

**After import**, if the tab lands in the wrong position, set *Engineering Tools → Edit →
Order*. Tab order is a plain number.

### Editing the UI

The HTML lives in `nodered/templates/*.html`. Change it there, then:

```bash
python nodered/build_flow.py --no-merge   # regenerate the JSON
python nodered/validate_flow.py           # check ids/wires/tags
```

Never hand-edit `flows-engineering-tab.json` — CI fails if it drifts from the templates.

### Merging into a full dashboard export

```bash
python nodered/build_flow.py --merge path/to/your-flows.json --order 6
```

Writes `flows-engineering-tab.merged.json` with your nodes preserved and ID collisions
refused. Use this only when you want one whole-file import; otherwise the plain import
above is safer.

## 2 · The phone agent

`agent/` builds **AFIF Link** (`uk.afif.link`): a foreground service that speaks MQTT to
your Node-RED host.

```
in : afif/link/<device>/cmd     {"id":"1","cmd":"info","token":"…"}
out: afif/link/<device>/state   telemetry every 30 s
     afif/link/<device>/res     one reply per command
     afif/link/<device>/event   inbound SMS, etc.
```

Commands: `ping`, `info`, `sms.list`, `sms.send`, `notify.dump`, `file.list`, `toast`,
`vibrate`. A read-only, token-protected HTTP server on port 8080 serves the app's external
files directory — that is what the Access Window points at over the LAN.

Build: `agent/README.md` (CI builds it; no local SDK needed).

## 3 · Why not just patch AirDroid

See [docs/AIRDROID-ANALYSIS.md](docs/AIRDROID-ANALYSIS.md). Short version: the APK ships
its application code as a 21 MB packed DEX (`com.licel.dexprotector`), and the desktop
"client" is a 50 MB NSIS installer wrapping an Electron payload. Repackaging is legally and
technically the wrong path; the useful subset is rebuilt here as an original app.

## 4 · Security notes

* The agent's token is optional but **strongly** advised; the file server refuses requests
  without a match when one is set.
* Keep the broker on the LAN or behind TLS. Do not expose port 1883 to the internet.
* The Access Window cannot frame `http://` targets from an `https://` dashboard — the
  template detects this and offers "open in a new tab" instead.
* Nothing in this repo contains AirDroid code, assets, keys or endpoints.

## 5 · Known gaps

* Screen mirroring / remote control is **not** implemented (needs MediaProjection consent).
* The APK has been built by CI, not yet run on a physical device.
* Tab position (`#5`) is a guess until the full dashboard export is merged.
