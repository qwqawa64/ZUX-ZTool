#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
classify_strings.py -- draft a rename map for every declared <string>.

Ownership is inferred from the files that reference each name:

  screens/home|features|ztoolsettings, ui/firstrun, uninstall/,
  MainActivity, navigation/            -> nav_ / page_<page>_
  viewmodel/<X>ViewModel               -> via name table
  screens|data /<scope-dir>/[<sub>/]   -> <scope>[_<sub>]_  (scope tokens follow
                                          the existing *_app_name family)
  ui/components|ui/theme, data/keys, utils/, service/, audit/, hook/,
  AndroidManifest                      -> SHARED / MANIFEST (review bucket)

Output: tools/string_rename_map.csv  (old,new,batch,evidence)
batch in {nav, page, common, scope, ztool, skip, REVIEW}.
New names are acronym-aware snake_cased. Same-scope multi-feature strings get
<scope>_common_; cross-scope or shared-only get common_ (review).
"""

from __future__ import annotations

import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

import importlib.util

REPO_ROOT = Path(__file__).resolve().parents[1]
OUT_CSV = Path(__file__).resolve().parent / "string_rename_map.csv"

_spec = importlib.util.spec_from_file_location(
    "checker", Path(__file__).resolve().parent / "check_unused_strings.py")
checker = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(checker)

NAV_PAT = re.compile(r"/navigation/|mainactivity\.kt", re.I)
PAGE_RULES = [
    (r"screens/home/", "page_home"),
    (r"screens/features/", "page_features"),
    (r"auditroute\.kt|screens/audit/", "page_audit"),
    (r"screens/ztoolsettings/", "page_settings"),
    (r"/ui/firstrun/", "page_firstrun"),
    (r"/uninstall/", "page_uninstall"),
    (r"viewmodel/firstrunagreement", "page_firstrun"),
    (r"viewmodel/settingsviewmodel|viewmodel/advancedsettings", "page_settings"),
    (r"viewmodel/homeviewmodel", "page_home"),
    (r"viewmodel/searchpage", "page_features"),
]

SCOPE_DIR_TOKENS = {
    "gametool": "game_tool",
    "launcher": "launcher",
    "mobiledesktop": "mobile_desktop",
    "ota": "system_update",
    "packageinstaller": "package_installer",
    "safecenter": "safe_center",
    "systemframework": "system_framework",
    "zuisetting": "settings",
    "settings": "settings",  # legacy dir name (data/settings repos)
}
SYSTEMUI_SUBS = {
    "statusbar": "status_bar",
    "lockscreen": "lock_screen",
    "controlcenter": "control_center",
    "animation": "animation",
    "misc": "misc",
    "qs": "qs",
}
VM_SCOPE_RULES = [
    (r"statusbarsettings", "system_ui_status_bar"),
    (r"lockscreen", "system_ui_lock_screen"),
    (r"controlcenter", "system_ui_control_center"),
    (r"systemuimisc", "system_ui_misc"),
    (r"animationwallpaper", "system_ui_animation"),
    (r"systemui", "system_ui"),
    (r"framework", "system_framework"),
    (r"gametool", "game_tool"),
    (r"launcher", "launcher"),
    (r"mobiledesktop", "mobile_desktop"),
    (r"ota", "system_update"),
    (r"packageinstaller", "package_installer"),
    (r"safecenter", "safe_center"),
    (r"settingsdetail|floatingwindow|zuisetting|magicwindow", "settings"),
]

SHARED_PATTERN = r"/ui/components/|/ui/theme|/data/|/utils/|/service/|/audit/|/hook/|/config/|/dexindex/"
KEY_REGISTRY_PAT = re.compile(r"/data/keys/", re.I)  # pref-key literals, not resource users

# app pages reached through data/ repos
DATA_PAGE_DIRS = {"home": "page_home"}

# rows the path heuristic cannot get right -- hand-decided (user-approved rules)
HAND_OVERRIDE = {
    "ram_formatter": ("ztool_ram_formatter", "ztool"),
    "ram_unavailable": ("ztool_ram_unavailable", "ztool"),
    "batch_uninstall_title": ("page_uninstall_title", "page"),
    "skipMultiWindowMode": ("settings_skip_multi_window_mode_title", "scope"),
    "skip_multi_window_mode": ("settings_skip_multi_window_mode_label", "scope"),
    "control_center_settings_title_suffix": ("system_ui_control_center_title_suffix", "scope"),
    "lock_screen_settings_title_suffix": ("system_ui_lock_screen_title_suffix", "scope"),
    "status_bar_settings_title_suffix": ("system_ui_status_bar_title_suffix", "scope"),
}

ALIAS_STRIP = {
    "system_ui": ("status_bar_", "lock_screen_", "lockscreen_", "control_center_",
                  "controlcenter_", "system_ui_", "systemui_", "qs_"),
    "system_update": ("ota_", "system_update_"),
    "game_tool": ("gametool_", "game_tool_"),
    "safe_center": ("safecenter_", "safe_center_"),
    "system_framework": ("framework_", "systemframework_", "system_framework_"),
    "mobile_desktop": ("mobiledesktop_", "mobile_desktop_"),
    "package_installer": ("packageinstaller_", "package_installer_"),
    "settings": ("zuisetting_", "zui_settings_", "settings_"),
    "page_firstrun": ("firstrun_",),
    "page_home": ("home_", "homefragment_"),
    "page_features": ("features_", "featuresfragment_"),
    "page_audit": ("audit_", "auditfragment_"),
    "page_settings": ("settings_", "ztoolsettings_"),
    "page_uninstall": ("batch_uninstall_",),
}

CONFORMING_RE = re.compile(
    r"^(settings|game_tool|system_update|package_installer|system_ui|launcher|"
    r"mobile_desktop|system_framework|safe_center)_app_(name|description)$")

# after stripping a domain alias the remainder must not start with a generic
# word, else different features would collapse onto the same generic name
GENERIC_FIRST = {"title", "summary", "setting", "settings", "text", "hint",
                 "label", "desc", "message", "error", "name", "value",
                 "enable", "disable", "on", "off", "info", "mode"}

NAV_SPECIAL = {
    "gotoHomePage": "nav_home",
    "gotoFeaturePage": "nav_features",
    "gotoSettingsPage": "nav_settings",
}


def snake(name: str) -> str:
    """Acronym-aware CamelCase -> snake_case, passes snake_case through."""
    if name.islower():
        return name
    tokens = re.findall(r"[A-Z]+(?![a-z])|[A-Z][a-z0-9]*|[a-z0-9]+", name)
    out = "_".join(t.lower() for t in tokens)
    return re.sub(r"_+", "_", out).strip("_")


SCOPE_FAMILIES = ["system_ui", "system_update", "game_tool", "safe_center",
                  "system_framework", "mobile_desktop", "package_installer",
                  "settings", "launcher"]


def scope_of_token(token: str) -> str:
    for fam in SCOPE_FAMILIES:
        if token == fam or token.startswith(fam + "_"):
            return fam
    return token


def strip_alias(token: str, body: str) -> str:
    aliases = ALIAS_STRIP.get(token, ())
    if not aliases:
        aliases = ALIAS_STRIP.get(scope_of_token(token), ())
    for alias in aliases:
        if body.startswith(alias):
            rest = body[len(alias):]
            first = rest.split("_", 1)[0]
            if rest and first not in GENERIC_FIRST:
                return rest
            break
    return body


def path_owner(rel: str) -> str | None:
    p = rel.replace("\\", "/").lower()
    if NAV_PAT.search(p):
        return "nav"
    for pat, token in PAGE_RULES:
        if re.search(pat, p):
            return token
    m = re.search(r"(?:screens|data)/systemui(?:/([a-z_]+))?", p)
    if m:
        sub = SYSTEMUI_SUBS.get(m.group(1) or "", None)
        return f"system_ui_{sub}" if sub else "system_ui"
    m = re.search(r"(?:screens|data)/([a-z_]+)/", p)
    if m and m.group(1) in DATA_PAGE_DIRS:
        return DATA_PAGE_DIRS[m.group(1)]
    if m and m.group(1) in SCOPE_DIR_TOKENS:
        return SCOPE_DIR_TOKENS[m.group(1)]
    if "androidmanifest.xml" in p:
        return "MANIFEST"
    vm = re.search(r"viewmodel/([a-z0-9_]+?)viewmodel\.kt", p)
    if vm:
        base = vm.group(1)
        for pat, token in VM_SCOPE_RULES:
            if re.search(pat, base):
                return token
        return None
    if re.search(SHARED_PATTERN, p):
        return "SHARED"
    return None


def top_scope(owner: str) -> str:
    return owner.split("_")[0] if not owner.startswith("page_") else owner


def main() -> int:
    default, _locales, _p, _a = checker.parse_declared()
    texts = [(path, checker.read_text(path)) for path in checker.find_scan_files()]

    refs = defaultdict(list)
    for path, text in texts:
        rel = str(path.relative_to(REPO_ROOT))
        is_xml = path.suffix.lower() == ".xml"
        pats = [re.compile(r"(?<![\w.])R\.string\.([A-Za-z0-9_]+)"),
                re.compile(r"@string/([A-Za-z0-9_]+)")]
        if not is_xml and not KEY_REGISTRY_PAT.search(rel):
            pats.append(re.compile(r'"([a-z][a-z0-9_]{2,60})"'))
        for pat in pats:
            for name in pat.findall(text):
                if name in default and rel not in refs[name]:
                    refs[name].append(rel)

    rows = []
    stats = defaultdict(int)
    for name in sorted(default):
        owners = []
        for rel in refs.get(name, []):
            owner = path_owner(rel)
            if owner and owner not in owners:
                owners.append(owner)
        ev = ";".join(r.split("/")[-1] for r in refs.get(name, [])[:3])
        sname = snake(name)

        if name in HAND_OVERRIDE:
            new, batch = HAND_OVERRIDE[name]
        elif CONFORMING_RE.match(name) or name.startswith("ztool_"):
            new, batch = name, "skip"
        elif name in NAV_SPECIAL:
            new, batch = NAV_SPECIAL[name], "nav"
        elif not owners:
            new, batch = sname, "REVIEW"
        else:
            page_owners = [o for o in owners if o.startswith("page_")]
            scope_owners = [o for o in owners if not o.startswith("page_")
                            and o not in ("nav", "SHARED", "MANIFEST")]
            aux = [o for o in owners if o in ("nav", "SHARED", "MANIFEST")]

            if scope_owners and not page_owners:
                scopes = {scope_of_token(o) for o in scope_owners}
                if len(scopes) == 1:
                    scope = scopes.pop()
                    if len(scope_owners) == 1 and scope_owners[0] != scope:
                        token = scope_owners[0]           # e.g. system_ui_status_bar
                    elif len(scope_owners) == 1:
                        token = scope                      # scope-level page
                    else:
                        token = f"{scope}_common"          # several features, one scope
                    body = strip_alias(token, sname)
                    new, batch = f"{token}_{body}", "scope"
                else:
                    new, batch = f"common_{sname}", "REVIEW"
            elif page_owners and not scope_owners:
                if len(set(page_owners)) == 1:
                    token = page_owners[0]
                    body = strip_alias(token, sname)
                    body = re.sub(r"^fragment_", "", body)
                    new, batch = f"{token}_{body}", "page"
                else:
                    new, batch = f"common_{sname}", "REVIEW"
            elif page_owners and scope_owners:
                # used by app pages AND feature pages alike -> shared business string
                new, batch = f"common_{sname}", "common"
            else:                                          # only nav/SHARED/MANIFEST
                new, batch = f"common_{sname}", "REVIEW"
        stats[batch] += 1
        rows.append({"old": name, "new": new, "batch": batch, "evidence": ev})

    # collision check
    new_names = defaultdict(list)
    for r in rows:
        new_names[r["new"]].append(r["old"])
    dupes = {k: v for k, v in new_names.items() if len(v) > 1}

    with OUT_CSV.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=["old", "new", "batch", "evidence"])
        writer.writeheader()
        writer.writerows(rows)

    print(f"rows: {len(rows)}")
    for batch, n in sorted(stats.items()):
        print(f"  {batch}: {n}")
    if dupes:
        print("COLLISIONS (multiple olds -> same new):")
        for k, v in sorted(dupes.items()):
            print(f"  {k} <- {', '.join(v)}")
    print(f"map: {OUT_CSV}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
