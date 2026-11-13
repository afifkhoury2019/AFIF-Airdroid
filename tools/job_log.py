#!/usr/bin/env python3
"""Fetch a failed GitHub Actions job log and print the interesting tail."""
import io
import json
import os
import sys
import urllib.request
import zipfile

REPO = "afifkhoury2019/AFIF-Airdroid"
TOKEN = os.environ["GITHUB_TOKEN"]
RUN = sys.argv[1]


def api(path):
    req = urllib.request.Request("https://api.github.com" + path)
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "afif-log")
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


jobs = api("/repos/%s/actions/runs/%s/jobs" % (REPO, RUN))["jobs"]
for j in jobs:
    if j["conclusion"] != "failure":
        continue
    print("### job:", j["name"], j["id"])
    req = urllib.request.Request(
        "https://api.github.com/repos/%s/actions/jobs/%s/logs" % (REPO, j["id"]))
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("User-Agent", "afif-log")

    class _NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *a, **k):
            return None

    try:
        with urllib.request.build_opener(_NoRedirect).open(req, timeout=60) as r:
            blob = r.read()
    except urllib.error.HTTPError as e:
        # GitHub redirects to pre-signed blob storage; auth headers must be dropped.
        if e.code in (301, 302, 303, 307, 308):
            with urllib.request.urlopen(e.headers["Location"], timeout=120) as r:
                blob = r.read()
        else:
            raise
    try:
        zf = zipfile.ZipFile(io.BytesIO(blob))
        text = "\n".join(zf.read(n).decode("utf-8", "replace") for n in zf.namelist())
    except zipfile.BadZipFile:
        text = blob.decode("utf-8", "replace")

    lines = [l.rstrip() for l in text.splitlines()]
    hits = [i for i, l in enumerate(lines) if "##[error]" in l or "Error:" in l or "error:" in l]
    start = max(0, (hits[0] - 25)) if hits else max(0, len(lines) - 70)
    for l in lines[start:start + 90]:
        print(l)
