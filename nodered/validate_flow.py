#!/usr/bin/env python3
"""Structural + template sanity checks for the generated Node-RED flows."""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
TPL = os.path.join(HERE, "templates")

FAIL = []


def check(cond, msg):
    if not cond:
        FAIL.append(msg)


def tag_balance(html, name):
    for tag in ("div", "style", "script"):
        opens = len(re.findall(r"<%s[\s>]" % tag, html))
        closes = len(re.findall(r"</%s>" % tag, html))
        check(opens == closes, "%s: <%s> %d open vs %d close" % (name, tag, opens, closes))
    # no stray </script> inside JS strings
    for m in re.finditer(r"<script>(.*?)</script>", html, re.S):
        body = m.group(1)
        check("</script>" not in body, "%s: nested </script>" % name)


def main():
    files = [f for f in os.listdir(HERE) if f.startswith("flows-") and f.endswith(".json")]
    check(files, "no flow files produced")

    for f in files:
        path = os.path.join(HERE, f)
        with open(path, encoding="utf-8") as fh:
            nodes = json.load(fh)
        check(isinstance(nodes, list), "%s: not a list" % f)

        ids = [n["id"] for n in nodes]
        dupes = {i for i in ids if ids.count(i) > 1}
        check(not dupes, "%s: duplicate ids %s" % (f, dupes))
        known = set(ids)

        for n in nodes:
            t = n.get("type")
            check(t, "%s: node without type" % f)
            z = n.get("z")
            if z:
                check(z in known, "%s/%s: dangling z=%s" % (f, n["id"], z))
            g = n.get("group")
            if g:
                grp = next((x for x in nodes if x["id"] == g), None)
                check(grp is not None, "%s/%s: missing group %s" % (f, n["id"], g))
                if grp:
                    check(grp.get("tab") in known,
                          "%s: group %s tab %s missing" % (f, g, grp.get("tab")))
            for w in n.get("wires") or []:
                for tgt in w or []:
                    check(tgt in known, "%s/%s: wire -> unknown %s" % (f, n["id"], tgt))

            if t == "ui_template":
                check(n.get("format"), "%s/%s: empty format" % (f, n["id"]))
                tag_balance(n["format"], "%s/%s" % (f, n["name"]))
            if t == "function":
                check(n.get("func"), "%s/%s: empty func" % (f, n["id"]))

        tabs = [n["name"] for n in nodes if n.get("type") == "ui_tab"]
        print("%-34s %3d nodes  tabs=%s" % (f, len(nodes), tabs or "-"))

    if FAIL:
        print("\nFAILURES (%d):" % len(FAIL))
        for m in FAIL:
            print("  -", m)
        sys.exit(1)
    print("\nall checks passed")


if __name__ == "__main__":
    main()
