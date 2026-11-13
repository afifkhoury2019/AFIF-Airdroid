#!/usr/bin/env python3
"""Push this project folder to GitHub using the REST API — no git binary required.

    set GITHUB_TOKEN=ghp_xxx            (Windows)
    export GITHUB_TOKEN=ghp_xxx         (bash)

    python tools/push_to_github.py --repo your-user/powerchain-tools
    python tools/push_to_github.py --repo your-user/powerchain-tools --private
    python tools/push_to_github.py --repo your-user/powerchain-tools --dry-run

What it does
  1. resolves the token's account
  2. creates the repository if it does not exist (private unless --public)
  3. uploads every included file as a blob
  4. builds one tree, one commit, and moves the branch ref

The token is never printed, never written to disk, and never included in a commit.
Use a fine-grained PAT with "Contents: read and write" (+ "Administration: write" if the
repository must be created). Revoke it when you are done.
"""
import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request

API = "https://api.github.com"

SKIP_DIRS = {".git", ".gradle", "build", "__pycache__", ".idea", "captures", ".cxx"}
SKIP_EXT = (".apk", ".aab", ".ap_", ".jks", ".keystore", ".pyc", ".dex")
SKIP_FILES = {"source-flows.json", "flows-engineering-tab.merged.json", "local.properties"}

DOC = "https://api.github.com"


def token():
    t = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if not t:
        sys.exit("GITHUB_TOKEN is not set")
    return t.strip()


def call(method, path, tok, body=None, ok=(200, 201, 204)):
    url = path if path.startswith("http") else API + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + tok)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    req.add_header("User-Agent", "afif-link-push")
    if data:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip() else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            parsed = json.loads(raw)
        except Exception:
            parsed = {"message": raw[:400]}
        return e.code, parsed


def walk(root):
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS and d != ".git"]
        for f in files:
            p = os.path.join(base, f)
            rel = os.path.relpath(p, root).replace("\\", "/")
            if f in SKIP_FILES or f.endswith(SKIP_EXT):
                continue
            try:
                with open(p, "rb") as fh:
                    blob = fh.read()
            except OSError:
                continue
            yield rel, blob


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", required=True, help="owner/name")
    ap.add_argument("--path", default=".", help="folder to push (default: repo root)")
    ap.add_argument("--branch", default="main")
    ap.add_argument("--message", default=None)
    ap.add_argument("--private", action="store_true", default=True)
    ap.add_argument("--public", dest="private", action="store_false")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    root = os.path.abspath(a.path)
    if not os.path.isdir(root):
        sys.exit("not a directory: " + root)

    files = list(walk(root))
    total = sum(len(b) for _, b in files)
    print("files to push : %d (%.1f KiB)" % (len(files), total / 1024))
    for rel, _ in files:
        print("   ", rel)

    if a.dry_run:
        print("\ndry run - nothing sent")
        return

    tok = token()
    if "/" not in a.repo:
        sys.exit("--repo must be owner/name")
    owner, name = a.repo.split("/", 1)

    st, me = call("GET", "/user", tok)
    if st != 200:
        sys.exit("token rejected (%d): %s" % (st, me.get("message")))
    login = me.get("login")
    print("authenticated : %s" % login)

    st, repo = call("GET", "/repos/%s/%s" % (owner, name), tok)
    if st == 404:
        if owner != login:
            sys.exit("repository %s/%s not found and it belongs to another account" % (owner, name))
        print("creating repository %s/%s (private=%s)" % (owner, name, a.private))
        st, repo = call("POST", "/user/repos", tok, {
            "name": name, "private": a.private, "auto_init": False,
            "description": "PowerChain engineering toolkit for Node-RED + AFIF Link device agent",
        })
        if st not in (200, 201):
            sys.exit("could not create repository: %s" % repo.get("message"))
        time.sleep(2)
    elif st != 200:
        sys.exit("repository lookup failed (%d): %s" % (st, repo.get("message")))

    # current head, if any
    st, ref = call("GET", "/repos/%s/%s/git/ref/heads/%s" % (owner, name, a.branch), tok)
    if st != 200:
        # GitHub refuses blob/tree calls on a repository with no commits.
        # Seed one file through the Contents API so the git database exists,
        # then continue normally (our tree overwrites README.md anyway).
        print("empty repository - seeding initial ref")
        seed = call("PUT", "/repos/%s/%s/contents/README.md" % (owner, name), tok, {
            "message": "Initialise repository",
            "content": base64.b64encode(b"# AFIF-Airdroid\n").decode(),
        })
        if seed[0] not in (200, 201):
            sys.exit("could not seed repository: %s" % seed[1].get("message"))
        time.sleep(2)
        st, ref = call("GET", "/repos/%s/%s/git/ref/heads/%s" % (owner, name, a.branch), tok)

    parent = None
    base_tree = None
    if st == 200:
        parent = ref["object"]["sha"]
        st2, commit = call("GET", "/repos/%s/%s/git/commits/%s" % (owner, name, parent), tok)
        if st2 == 200:
            base_tree = commit["tree"]["sha"]
        print("existing head: %s" % parent[:10])
    else:
        print("no head on %s - first commit" % a.branch)

    entries = []
    for rel, blob in files:
        st, r = call("POST", "/repos/%s/%s/git/blobs" % (owner, name), tok, {
            "content": base64.b64encode(blob).decode(), "encoding": "base64"})
        if st not in (200, 201):
            sys.exit("blob failed for %s: %s" % (rel, r.get("message")))
        entries.append({"path": rel, "mode": "100644", "type": "blob", "sha": r["sha"]})
    print("blobs uploaded: %d" % len(entries))

    tree_body = {"tree": entries}
    if base_tree:
        tree_body["base_tree"] = base_tree
    st, tree = call("POST", "/repos/%s/%s/git/trees" % (owner, name), tok, tree_body)
    if st not in (200, 201):
        sys.exit("tree failed: %s" % tree.get("message"))

    msg = a.message or "PowerChain engineering tab + AFIF Link agent"
    commit_body = {"message": msg, "tree": tree["sha"]}
    if parent:
        commit_body["parents"] = [parent]
    st, commit = call("POST", "/repos/%s/%s/git/commits" % (owner, name), tok, commit_body)
    if st not in (200, 201):
        sys.exit("commit failed: %s" % commit.get("message"))

    if parent:
        st, r = call("PATCH", "/repos/%s/%s/git/refs/heads/%s" % (owner, name, a.branch), tok,
                     {"sha": commit["sha"], "force": False})
    else:
        st, r = call("POST", "/repos/%s/%s/git/refs" % (owner, name), tok,
                     {"ref": "refs/heads/" + a.branch, "sha": commit["sha"]})
    if st not in (200, 201):
        sys.exit("ref update failed: %s" % r.get("message"))

    print("\npushed %s" % commit["sha"][:10])
    print("https://github.com/%s/%s" % (owner, name))
    print("CI: https://github.com/%s/%s/actions" % (owner, name))


if __name__ == "__main__":
    main()
