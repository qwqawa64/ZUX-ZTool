#!/usr/bin/env python3
"""Composes the frames sampled by webframes.mjs into one labelled filmstrip.

Usage:
    python tools/web_frames/strip.py --dir frames --out strip.png [--columns 8]
"""

import argparse
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

FONT_CANDIDATES = [
    "C:/Windows/Fonts/segoeui.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]


def load_font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                pass
    return ImageFont.load_default()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", required=True, help="output directory of webframes.mjs")
    parser.add_argument("--out", default=None, help="filmstrip path (default <dir>/strip.png)")
    parser.add_argument("--columns", type=int, default=0, help="wrap after N frames")
    parser.add_argument("--padding", type=int, default=16)
    parser.add_argument("--label-size", type=int, default=22)
    args = parser.parse_args()

    manifest_path = os.path.join(args.dir, "manifest.json")
    with open(manifest_path, encoding="utf-8") as handle:
        manifest = json.load(handle)

    frames = manifest["frames"]
    images = [Image.open(os.path.join(args.dir, frame["file"])).convert("RGB") for frame in frames]
    if not images:
        sys.exit("no frames in manifest")
    width, height = images[0].size
    columns = args.columns or len(images)
    rows = (len(images) + columns - 1) // columns

    font = load_font(args.label_size)
    label_height = args.label_size + 8
    pad = args.padding
    strip_width = pad + columns * (width + pad)
    strip_height = pad + rows * (label_height + height + pad)
    canvas = Image.new("RGB", (strip_width, strip_height), (255, 255, 255))
    draw = ImageDraw.Draw(canvas)

    for index, (frame, image) in enumerate(zip(frames, images)):
        column = index % columns
        row = index // columns
        x = pad + column * (width + pad)
        y = pad + row * (label_height + height + pad)
        draw.text((x, y), "%d ms" % frame["timeMs"], font=font, fill=(70, 70, 70))
        canvas.paste(image, (x, y + label_height))

    out = args.out or os.path.join(args.dir, "strip.png")
    canvas.save(out)
    print("%s (%dx%d, %d frames)" % (out, canvas.width, canvas.height, len(frames)))


if __name__ == "__main__":
    main()
