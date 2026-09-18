#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Migrate docs_archive -> docs/archive and active docs -> docs, with reference rewriting.

Usage:
  python tools/migrate_docs.py scan    # only print references to be rewritten (dry run of ref scan)
  python tools/migrate_docs.py apply   # perform git mv, rename, and rewrite references
  python tools/migrate_docs.py verify  # after apply, report any dangling old-path references
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# New live-doc locations (relative to project root), all lowercase snake_case.
LIVE_DOCS = {
    "docs_archive/hook_guide/libxposed_guide_ztool_ver.md": "docs/hook/libxposed_guide_ztool_ver.md",
    "docs_archive/hook_guide/add_frontend_item.md": "docs/hook/add_frontend_item.md",
    "docs_archive/hook_guide/add_new_hook_module.md": "docs/hook/add_new_hook_module.md",
    "docs_archive/dex_index/readme.md": "docs/dex_index/readme.md",
    "docs_archive/new_log_system/migrate_and_use_new_logging_system.md": "docs/logging/migrate_and_use_new_logging_system.md",
    "docs_archive/preference_key/add_new_preference_key_zh-cn.md": "docs/preference_key/add_new_preference_key_zh-cn.md",
}

def to_snake(name: str) -> str:
    stem, ext = os.path.splitext(name)
    s = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", stem)
    s = re.sub(r"[^A-Za-z0-9\-_.]+", "_", s)
    s = re.sub(r"_+", "_", s).strip("_")
    return s.lower() + ext.lower()

def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")

def list_repo_files():
    out = git("ls-files")
    files = [f.replace("\\", "/") for f in out.stdout.splitlines() if f.strip()]
    # include untracked-but-not-ignored (e.g. modified docs)
    out2 = git("ls-files", "--others", "--exclude-standard")
    files += [f.replace("\\", "/") for f in out2.stdout.splitlines() if f.strip()]
    return sorted(set(files))

def text_files(files):
    for f in files:
        if f.endswith((".md", ".MD", ".kt", ".java", ".kts", ".xml", ".json", ".txt", ".gradle")):
            yield f

# Build old->new map for every .md under docs_archive and root-level plan docs.
def build_move_map():
    moves = {}
    for f in list_repo_files():
        if f.startswith("docs_archive/") and f.endswith(".md"):
            new = f
            if f in LIVE_DOCS:
                new = LIVE_DOCS[f]
            else:
                parts = f.split("/")
                parts[-1] = to_snake(parts[-1])
                new = "docs/archive/" + "/".join(parts[1:])
            moves[f] = new
        elif f in ("PLAN_feature_search_phase1.md", "PLAN_feature_search_phase2.md",
                   "PLAN_feature_search_phase3.md", "PLAN_translate_cn_logs_comments.md"):
            moves[f] = "docs/archive/" + to_snake(f)
    return moves

MOVE_MAP = build_move_map()

# All alias forms that may appear in text for a given repo path.
def aliases(path: str):
    alts = {path, path.replace("/", "\\")}
    b = os.path.basename(path)
    alts |= {b, ".\\" + b, "./" + b}
    return sorted(alts, key=len, reverse=True)

BASENAME_MAP = {}
for old, new in MOVE_MAP.items():
    BASENAME_MAP.setdefault(os.path.basename(old), []).append((old, new))

def rewrite_text(text: str):
    hits = []
    # 1) full-path references (with optional markdown link target)
    def repl_full(m):
        p = m.group(2)
        norm = p.replace("\\", "/")
        if norm in MOVE_MAP:
            hits.append(p)
            return m.group(1) + MOVE_MAP[norm]
        return m.group(0)
    text = re.sub(r"(\]|`|\(|\s)(docs_archive/[A-Za-z0-9_\-./]+|[A-Za-z0-9_\-]+\.md)", repl_full, text)
    # 2) relative markdown links like ../preference_key/xxx.md or ./xxx.md (only inside link parens)
    def repl_rel(m):
        rel = m.group(2)
        # resolve relative to an unknown dir: match by basename path suffix
        tail = rel.lstrip("./").replace("\\", "/")
        for old, new in MOVE_MAP.items():
            if old.endswith(tail.split("/", 1)[-1]) and tail.split("/", 1)[-1] in old:
                # ensure full suffix match after stripping ../ segments
                parts = [p for p in tail.split("/") if p not in (".",)]
                while parts and parts[0] == "..":
                    parts.pop(0)
                if parts and old.endswith("/".join(parts)):
                    hits.append(rel)
                    return m.group(1) + new + m.group(3)
        return m.group(0)
    text = re.sub(r"(\]\()((?:\.\./|\./)?[A-Za-z0-9_\-./]+\.md)([\)\#])", repl_rel, text)
    # 3) bare basename mentions like `Add_Frontend_Item.md`
    for base, entries in BASENAME_MAP.items():
        if base.lower() != base:  # needs renaming
            def repl_base(m, entries=entries):
                for old, new in entries:
                    if old.endswith(base):
                        hits.append(base)
                        return m.group(1) + os.path.basename(new) + m.group(2)
                return m.group(0)
            text = re.sub(r"(\s|`|\[|\(|/)" + re.escape(base) + r"(\s|`|\]|\)|,|。|;)",
                          repl_base, text)
    return text, hits

SCAN_FILES = None

def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "verify"
    if mode == "scan":
        total = 0
        for f in text_files(list_repo_files()):
            if f.startswith("app/build") or f.startswith("app/src/main/assets"):
                continue
            p = os.path.join(ROOT, f)
            with open(p, encoding="utf-8", errors="replace") as fh:
                text = fh.read()
            _, hits = rewrite_text(text)
            if hits:
                print(f"{f}: {len(hits)} refs")
                total += len(hits)
        print(f"total refs: {total}")
    elif mode == "apply":
        # move files with git mv (handles modified files too)
        for old, new in sorted(MOVE_MAP.items()):
            dst = os.path.join(ROOT, new)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            r = git("mv", "-f", old, new)
            if r.returncode != 0:
                # fall back to plain move + git add
                os.replace(os.path.join(ROOT, old), dst)
                git("add", new)
            print(f"moved {old} -> {new}")
        # rewrite references in remaining text files
        for f in text_files(list_repo_files()):
            if f.startswith("app/build") or f.startswith("app/src/main/assets") or f == "tools/migrate_docs.py":
                continue
            p = os.path.join(ROOT, f)
            with open(p, encoding="utf-8", errors="replace") as fh:
                text = fh.read()
            new_text, hits = rewrite_text(text)
            if hits:
                with open(p, "w", encoding="utf-8", newline="") as fh:
                    fh.write(new_text)
                print(f"rewrote {len(hits)} refs in {f}")
        print("done")
    elif mode == "verify":
        bad = 0
        for f in text_files(list_repo_files()):
            if f.startswith("app/build") or f.startswith("app/src/main/assets"):
                continue
            p = os.path.join(ROOT, f)
            with open(p, encoding="utf-8", errors="replace") as fh:
                text = fh.read()
            for old in MOVE_MAP:
                for al in aliases(old):
                    if al in text and not al.startswith("docs/archive"):
                        print(f"{f}: still references {al}")
                        bad += 1
        print(f"dangling refs: {bad}")

if __name__ == "__main__":
    main()
