#!/usr/bin/env python3
"""Regenerate the alternative ("使用替代图标") launcher and splash icon assets.

The alternative artwork lives outside the resource tree (the untracked
``new_launcher_icon.jpg`` at the repository root). This script derives every
committed binary from it:

* ``res/mipmap-<density>/ic_launcher_alt_foreground.webp`` -- adaptive-icon
  foreground layers at 80% of the 108dp layer, so the drawing keeps its subject
  inside the launcher mask's safe zone.
* ``res/drawable/splash_logo_alt.png`` -- in-app logo used by the Firstrun splash
  page and the About header, rounded like ``splash_logo.png``.

The monochrome layer is deliberately NOT generated: the alternative artwork is a
full-colour sketch and is not suitable for themed (monochrome) icon rendering, so
both adaptive icons keep pointing at the original ``ic_launcher_monochrome``.

Usage:
    python tools/generate_alt_launcher_icons.py [--source PATH]
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SOURCE = REPO_ROOT / "new_launcher_icon.jpg"
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res"

# Adaptive-icon foreground occupies 80% of the 108dp layer.
FOREGROUND_SCALE = 0.80
DENSITY_LAYER_PX = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}
SPLASH_SIZE = 640
SPLASH_RADIUS = 72


def load_art(source: Path, size: int) -> Image.Image:
    return Image.open(source).convert("RGB").resize((size, size), Image.LANCZOS)


def rounded_mask(size: int, radius: int, supersample: int = 4) -> Image.Image:
    """Anti-aliased rounded-square alpha mask."""
    big = size * supersample
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, big - 1, big - 1), radius=radius * supersample, fill=255
    )
    return mask.resize((size, size), Image.LANCZOS)


def write_foregrounds(source: Path) -> None:
    for density, layer_px in DENSITY_LAYER_PX.items():
        side = round(layer_px * FOREGROUND_SCALE)
        canvas = Image.new("RGBA", (layer_px, layer_px), (0, 0, 0, 0))
        offset = (layer_px - side) // 2
        canvas.paste(load_art(source, side), (offset, offset))
        out = RES_DIR / f"mipmap-{density}" / "ic_launcher_alt_foreground.webp"
        canvas.save(out, format="WEBP", lossless=True, quality=100, method=6)
        print(f"wrote {out.relative_to(REPO_ROOT)} ({canvas.width}x{canvas.height})")


def write_splash(source: Path) -> None:
    logo = load_art(source, SPLASH_SIZE).convert("RGBA")
    logo.putalpha(rounded_mask(SPLASH_SIZE, SPLASH_RADIUS))
    out = RES_DIR / "drawable" / "splash_logo_alt.png"
    logo.save(out, format="PNG", optimize=True)
    print(f"wrote {out.relative_to(REPO_ROOT)} ({logo.width}x{logo.height})")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source",
        type=Path,
        default=DEFAULT_SOURCE,
        help=f"alternative artwork (default: {DEFAULT_SOURCE.name})",
    )
    args = parser.parse_args()
    if not args.source.is_file():
        parser.error(f"source artwork not found: {args.source}")
    write_foregrounds(args.source)
    write_splash(args.source)


if __name__ == "__main__":
    main()
