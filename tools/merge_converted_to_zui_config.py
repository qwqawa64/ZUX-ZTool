# -*- coding: utf-8 -*-
"""
Merge the converted skeleton entries (onevision/output/embedding_config_converted.json,
a bare array) into a complete, module-consumable ZUI embedding config:
the wrapped {"EmbeddingConfigVersion", "skipEmbeddingDividerActivities", "packages"}
format that /data/adb/modules/zuxos_embedding/embedding_config.json uses and that
EmbeddingConfigManager.flashConfigs() expects.

The app UI has no array-JSON import entry, so the output of this script is meant
to be flashed to the module config path directly (root) instead.

Usage:
    python tools/merge_converted_to_zui_config.py [--drop-empty]

Options:
    --drop-empty  Skip entries that have no mainPage and no activityPairs
                  (useless skeletons).

Output: onevision/output/embedding_config_zui.json
"""
import argparse
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EXISTING_CONFIG = os.path.join(
    ROOT, "app", "src", "main", "assets", "embedding", "zuxos_embedding",
    "embedding_config.json")
CONVERTED = os.path.join(ROOT, "onevision", "output",
                         "embedding_config_converted.json")
OUT = os.path.join(ROOT, "onevision", "output", "embedding_config_zui.json")

ENTRY_FIELDS = [
    "mainPage", "activityPairs", "forceFullscreenPages", "transActivities",
    "leftTransActivities", "showEmbeddingDivider", "skipLetterboxDisplayInfo",
    "skipMultiWindowMode", "showSurfaceViewBackground",
    "shouldPausePrimaryActivity",
]


def bump_version(version):
    parts = (version or "0.0.0").split(".")
    parts[-1] = str(int(parts[-1]) + 1)
    return ".".join(parts)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--drop-empty", action="store_true",
                    help="skip entries without mainPage and activityPairs")
    ap.add_argument("--source", default=CONVERTED,
                    help="entry source: bare array or wrapped config "
                         "(default: %(default)s)")
    ap.add_argument("--out", default=OUT,
                    help="output path (default: %(default)s)")
    args = ap.parse_args()

    with open(EXISTING_CONFIG, encoding="utf-8") as f:
        config = json.load(f)
    required = ("EmbeddingConfigVersion", "skipEmbeddingDividerActivities",
                "packages")
    missing = [k for k in required if k not in config]
    if missing:
        raise SystemExit(f"{EXISTING_CONFIG} lacks {missing}; not a wrapped "
                         "ZUI embedding config, aborting")

    with open(args.source, encoding="utf-8") as f:
        converted = json.load(f)
    # Accept a bare entry array or a wrapped config with a packages array.
    if isinstance(converted, dict) and "packages" in converted:
        converted = converted["packages"]
    if not isinstance(converted, list):
        raise SystemExit(f"{args.source} is neither a bare entry array nor a "
                         "wrapped config")

    names = {p["name"] for p in config["packages"]}
    added, replaced, skipped_dup, dropped_empty = 0, 0, 0, 0
    for entry in converted:
        if not isinstance(entry, dict) or not entry.get("name"):
            raise SystemExit("converted array contains an entry without 'name'")
        pkg = entry["name"]
        empty = not entry.get("mainPage") and not entry.get("activityPairs")
        if args.drop_empty and empty:
            dropped_empty += 1
            continue
        if pkg in names:
            # Converted skeletons are lower fidelity than anything already
            # shipped, so never replace an existing entry.
            skipped_dup += 1
            continue
        config["packages"].append({k: entry[k] for k in
                                   ["name"] + ENTRY_FIELDS if k in entry})
        names.add(pkg)
        added += 1
        if empty:
            dropped_empty += 0  # counted, kept

    config["EmbeddingConfigVersion"] = bump_version(
        config["EmbeddingConfigVersion"])

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(config, f, ensure_ascii=False, indent=2)

    no_main = sum(1 for p in config["packages"] if not p.get("mainPage"))
    print(f"packages in output: {len(config['packages'])} "
          f"(added {added}, converted-duplicates skipped {skipped_dup}, "
          f"empty skeletons dropped {dropped_empty})")
    print(f"entries still missing mainPage (need manual fill): {no_main}")
    print(f"EmbeddingConfigVersion -> {config['EmbeddingConfigVersion']}")
    print("wrote:", args.out)


if __name__ == "__main__":
    main()
