#!/usr/bin/env python3
"""Poll the GitHub Actions run for a commit and report job outcomes + artifacts."""
import json
import os
import sys
import time
import urllib.request

REPO = "afifkhoury2019/AFIF-Airdroid"
SHA = sys.argv[1] if len(sys.argv) > 1 else ""
TOKEN = os.environ["GITHUB_TOKEN"]
MAX_WAIT = int(sys.argv[2]) if len(sys.argv) > 2 else 720


def api(path):
    req = urllib.request.Request("https://api.github.com" + path)
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "afif-poll")
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


deadline = time.time() + MAX_WAIT
run = None
while time.time() < deadline:
    runs = api("/repos/%s/actions/runs?per_page=10" % REPO)["workflow_runs"]
    run = next((r for r in runs if r["head_sha"].startswith(SHA[:10])), None) if SHA else (runs[0] if runs else None)
    if run:
        break
    print("waiting for run to appear…", flush=True)
    time.sleep(10)

if not run:
    print("NO RUN FOUND for", SHA)
    sys.exit(1)

print("run %s  status=%s" % (run["id"], run["status"]), flush=True)

while time.time() < deadline:
    run = api("/repos/%s/actions/runs/%s" % (REPO, run["id"]))
    jobs = api("/repos/%s/actions/runs/%s/jobs" % (REPO, run["id"]))["jobs"]
    state = " | ".join("%s:%s/%s" % (j["name"], j["status"], j["conclusion"]) for j in jobs)
    print("%-9s %s" % (run["status"], state), flush=True)
    if run["status"] == "completed":
        break
    time.sleep(20)

print("\n=== FINAL ===")
print("conclusion:", run["conclusion"])
for j in jobs:
    print(" - %-28s %s" % (j["name"], j["conclusion"]))
    for st in j.get("steps", []):
        if st.get("conclusion") not in (None, "success"):
            print("     ! %s -> %s" % (st["name"], st["conclusion"]))

arts = api("/repos/%s/actions/runs/%s/artifacts" % (REPO, run["id"]))["artifacts"]
for a in arts:
    print("artifact: %s (%d bytes) id=%s" % (a["name"], a["size_in_bytes"], a["id"]))

try:
    rel = api("/repos/%s/releases/latest" % REPO)
except Exception:
    rel = None
if rel and rel.get("assets"):
    print("\nrelease:", rel["tag_name"])
    for a in rel["assets"]:
        print("  %s  %s" % (a["name"], a["browser_download_url"]))
