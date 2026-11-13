#!/usr/bin/env python3
"""Assemble the PowerChain "Engineering Tools" tab flow and merge it into an existing flows.json.

Usage:
    python build_flow.py                       # build standalone + merged (uses the attached flows.json)
    python build_flow.py --merge other.json    # merge into a different dashboard export
    python build_flow.py --order 6 --tab-id X  # control tab position / id

Produces, next to this script:
    flows-engineering-tab.json          importable on its own
    flows-engineering-tab.merged.json   your existing dashboard + the new tab
    flows-agent-bridge.json             optional MQTT bridge tab for the AFIF Link agent
"""
import argparse, copy, json, os, sys, uuid

HERE = os.path.dirname(os.path.abspath(__file__))
TPL = os.path.join(HERE, "templates")

DEFAULT_FLOWS = r"C:\Users\afifk\OneDrive\Desktop\flows.json"


def read(name):
    with open(os.path.join(TPL, name), "r", encoding="utf-8") as f:
        return f.read()


def load(path):
    with open(path, "r", encoding="utf-8-sig") as f:
        return json.load(f)


# ---------------------------------------------------------------- flow definition
def build_tab(order=6, tab_id="eng-tools-tab"):
    access_html = read("access_window.html")
    toolkit_html = read("toolkit.html")

    nodes = []

    nodes.append({
        "id": tab_id, "type": "ui_tab", "name": "Engineering Tools",
        "icon": "build", "order": order, "disabled": False, "hidden": False,
    })

    nodes.append({
        "id": "eng-access-group", "type": "ui_group",
        "name": "Access Window", "tab": tab_id, "order": 1,
        "disp": True, "width": "30", "collapse": False,
    })
    nodes.append({
        "id": "eng-tools-group", "type": "ui_group",
        "name": "Engineering Toolkit", "tab": tab_id, "order": 2,
        "disp": True, "width": "30", "collapse": False,
    })
    nodes.append({
        "id": "eng-log-group", "type": "ui_group",
        "name": "Calculation Log", "tab": tab_id, "order": 3,
        "disp": True, "width": "15", "collapse": False,
    })
    nodes.append({
        "id": "eng-status-group", "type": "ui_group",
        "name": "Link Status", "tab": tab_id, "order": 4,
        "disp": True, "width": "15", "collapse": False,
    })

    # --- the access window itself
    nodes.append({
        "id": "eng-access-window", "type": "ui_template", "z": tab_id,
        "group": "eng-access-group", "name": "Access Window",
        "order": 1, "width": 30, "height": 18,
        "format": access_html,
        "storeOutMessages": True, "fwdInMessages": True, "resendOnRefresh": True,
        "templateScope": "local", "x": 380, "y": 140, "wires": [[]],
    })

    # --- initial target pushed into the window on deploy
    nodes.append({
        "id": "eng-target-inject", "type": "inject", "z": tab_id,
        "name": "Default target", "props": [{"p": "payload"}],
        "repeat": "", "crontab": "", "once": True, "onceDelay": 0.6,
        "topic": "", "payload": "{\"src\":\"https://powerchain.uk/ui/#!/5\",\"title\":\"Engineering Tools\"}",
        "payloadType": "json", "x": 160, "y": 140, "wires": [["eng-target-fn"]],
    })
    nodes.append({
        "id": "eng-target-fn", "type": "function", "z": tab_id,
        "name": "Set window target",
        "func": (
            "// Accepts a URL string, {src,title}, or passes the current msg through.\n"
            "var p = msg.payload;\n"
            "if (typeof p === 'string') { msg.src = p; }\n"
            "else if (p && typeof p === 'object') {\n"
            "  if (p.src) msg.src = p.src;\n"
            "  if (p.title) msg.title = p.title;\n"
            "  if (p.reload !== undefined) msg.reload = p.reload;\n"
            "}\n"
            "if (!msg.src && !msg.title && !msg.reload) return null;\n"
            "return msg;"
        ),
        "outputs": 1, "timeout": "", "noerr": 0, "initialize": "", "finalize": "",
        "libs": [], "x": 380, "y": 220, "wires": [["eng-access-window"]],
    })

    # --- calculators
    nodes.append({
        "id": "eng-toolkit", "type": "ui_template", "z": tab_id,
        "group": "eng-tools-group", "name": "Engineering Toolkit",
        "order": 1, "width": 30, "height": 14,
        "format": toolkit_html,
        "storeOutMessages": True, "fwdInMessages": True, "resendOnRefresh": True,
        "templateScope": "local", "x": 360, "y": 340, "wires": [["eng-calc-stamp"]],
    })

    nodes.append({
        "id": "eng-calc-stamp", "type": "function", "z": tab_id,
        "name": "Stamp calculation",
        "func": (
            "var p = msg.payload || {};\n"
            "var out = {\n"
            "  id: (Date.now().toString(36) + Math.random().toString(36).slice(2,6)),\n"
            "  ts: new Date().toISOString(),\n"
            "  tool: p.tool || (msg.topic || '').replace('eng/',''),\n"
            "  inputs: p.inputs || {},\n"
            "  outputs: p.outputs || {}\n"
            "};\n"
            "msg.payload = out;\n"
            "msg.topic = 'eng/' + out.tool;\n"
            "node.status({fill:'green', shape:'dot', text: out.tool});\n"
            "return msg;"
        ),
        "outputs": 1, "timeout": "", "noerr": 0, "initialize": "", "finalize": "",
        "libs": [], "x": 620, "y": 340,
        "wires": [["eng-calc-log", "eng-calc-debug"]],
    })

    nodes.append({
        "id": "eng-calc-log", "type": "ui_template", "z": tab_id,
        "group": "eng-log-group", "name": "Calculation Log",
        "order": 1, "width": 15, "height": 8,
        "format": read("calc_log.html"),
        "storeOutMessages": True, "fwdInMessages": True, "resendOnRefresh": True,
        "templateScope": "local", "x": 880, "y": 340, "wires": [[]],
    })

    nodes.append({
        "id": "eng-calc-debug", "type": "debug", "z": tab_id,
        "name": "calc result", "active": True, "tosidebar": True,
        "console": False, "tostatus": False, "complete": "payload",
        "targetType": "msg", "statusVal": "", "statusType": "auto",
        "x": 880, "y": 400, "wires": [],
    })

    # --- link status widget
    nodes.append({
        "id": "eng-status-text", "type": "ui_template", "z": tab_id,
        "group": "eng-status-group", "name": "Link Status",
        "order": 1, "width": 15, "height": 4,
        "format": read("link_status.html"),
        "storeOutMessages": True, "fwdInMessages": True, "resendOnRefresh": True,
        "templateScope": "local", "x": 380, "y": 480, "wires": [[]],
    })

    return nodes


def build_agent_bridge():
    """Optional second tab: MQTT bridge to the AFIF Link device agent."""
    tab = "afif-link-tab"
    nodes = [{
        "id": tab, "type": "ui_tab", "name": "Device Link",
        "icon": "devices", "order": 7, "disabled": False, "hidden": False,
    }, {
        "id": "afif-link-group", "type": "ui_group",
        "name": "AFIF Link – phone agent", "tab": tab, "order": 1,
        "disp": True, "width": "30", "collapse": False,
    }, {
        "id": "afif-link-status", "type": "ui_template", "z": tab,
        "group": "afif-link-group", "name": "Agent Status",
        "order": 1, "width": 30, "height": 6,
        "format": read("link_status.html"),
        "storeOutMessages": True, "fwdInMessages": True, "resendOnRefresh": True,
        "templateScope": "local", "x": 380, "y": 160, "wires": [[]],
    }, {
        "id": "afif-broker", "type": "mqtt-broker", "name": "AFIF Lab broker",
        "broker": "127.0.0.1", "port": "1883", "clientid": "",
        "autoConnect": True, "usetls": False, "protocolVersion": "4",
        "keepalive": "60", "cleansession": True,
    }, {
        "id": "afif-mqtt-in", "type": "mqtt in", "z": tab,
        "name": "agent state", "topic": "afif/link/+/state",
        "qos": "0", "datatype": "json", "broker": "afif-broker",
        "nl": False, "rap": True, "rh": 0, "inputs": 0,
        "x": 170, "y": 240, "wires": [["afif-state-fn"]],
    }, {
        "id": "afif-state-fn", "type": "function", "z": tab,
        "name": "Normalise state",
        "func": (
            "var d = (msg.topic || '').split('/')[2] || 'unknown';\n"
            "var s = msg.payload || {};\n"
            "msg.payload = {\n"
            "  device: d, online: true, ts: new Date().toLocaleTimeString(),\n"
            "  battery: s.battery, charging: s.charging, storageFree: s.storageFree,\n"
            "  network: s.network, rssi: s.rssi, uptime: s.uptime, ip: s.ip\n"
            "};\n"
            "return msg;"
        ),
        "outputs": 1, "timeout": "", "noerr": 0, "initialize": "", "finalize": "",
        "libs": [], "x": 400, "y": 240, "wires": [["afif-link-status"]],
    }]
    return nodes


# ---------------------------------------------------------------- validation
def validate(nodes):
    ids = [n["id"] for n in nodes]
    dupes = sorted({i for i in ids if ids.count(i) > 1})
    errs = []
    if dupes:
        errs.append("duplicate ids: %s" % dupes)
    known = set(ids)
    for n in nodes:
        z = n.get("z")
        if z and z not in known:
            errs.append("%s -> unknown tab %s" % (n.get("id"), z))
        g = n.get("group")
        if g:
            grp = next((x for x in nodes if x["id"] == g), None)
            if not grp:
                errs.append("%s -> unknown group %s" % (n.get("id"), g))
            elif grp.get("tab") and grp["tab"] not in known:
                errs.append("group %s -> unknown tab %s" % (g, grp["tab"]))
        for w in n.get("wires") or []:
            for tgt in w or []:
                if tgt not in known:
                    errs.append("%s -> unknown target %s" % (n.get("id"), tgt))
    return errs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--merge", default=DEFAULT_FLOWS, help="existing flows.json to merge into")
    ap.add_argument("--order", type=int, default=6, help="tab order for the new tab")
    ap.add_argument("--tab-id", default="eng-tools-tab")
    ap.add_argument("--no-merge", action="store_true")
    a = ap.parse_args()

    tab = build_tab(a.order, a.tab_id)
    errs = validate(tab)
    if errs:
        print("FLOW VALIDATION ERRORS:"); [print("  -", e) for e in errs]; sys.exit(1)

    with open(os.path.join(HERE, "flows-engineering-tab.json"), "w", encoding="utf-8") as f:
        json.dump(tab, f, indent=4, ensure_ascii=False)

    bridge = build_agent_bridge()
    with open(os.path.join(HERE, "flows-agent-bridge.json"), "w", encoding="utf-8") as f:
        json.dump(bridge, f, indent=4, ensure_ascii=False)

    print("standalone tab   : %d nodes -> flows-engineering-tab.json" % len(tab))
    print("agent bridge tab : %d nodes -> flows-agent-bridge.json" % len(bridge))

    if a.no_merge or not os.path.exists(a.merge):
        print("merge skipped (no source file)")
        return

    base = load(a.merge)
    existing = {n["id"] for n in base}
    clash = [n["id"] for n in tab if n["id"] in existing]
    if clash:
        print("merge aborted, id clash:", clash); sys.exit(1)

    merged = copy.deepcopy(base) + tab
    errs = validate(merged)
    if errs:
        print("MERGED VALIDATION ERRORS:"); [print("  -", e) for e in errs]; sys.exit(1)

    out = os.path.join(HERE, "flows-engineering-tab.merged.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(merged, f, indent=4, ensure_ascii=False)

    tabs = [n["name"] for n in merged if n["type"] == "ui_tab"]
    print("merged           : %d nodes (%d before) -> %s"
          % (len(merged), len(base), os.path.basename(out)))
    print("tabs now         : %s" % ", ".join(tabs))


if __name__ == "__main__":
    main()
