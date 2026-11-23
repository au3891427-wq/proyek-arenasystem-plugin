#!/usr/bin/env python3
"""Create the four-frame Assassin Dagger shimmer texture.

Usage:
  python generate_dagger_animation.py input_32x32.png output_32x128.png

Pillow is needed only when regenerating the artwork. It is not a mod runtime
or Gradle dependency.
"""
from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Original square 32x32 dagger PNG")
    parser.add_argument("output", type=Path, help="Destination vertical sprite sheet")
    args = parser.parse_args()

    try:
        source = Image.open(args.source).convert("RGBA")
    except (OSError, ValueError) as exc:
        parser.error(f"cannot read source PNG: {exc}")

    if source.size != (32, 32):
        parser.error(f"expected a 32x32 texture, received {source.width}x{source.height}")

    sheet = Image.new("RGBA", (32, 128), (0, 0, 0, 0))
    for frame_index in range(4):
        frame = source.copy()
        x = 26 - (2 * frame_index)
        y = 5 + (2 * frame_index)
        frame.putpixel((x, y), (238, 247, 255, 255))
        frame.putpixel((x + 1, y), (150, 187, 218, 255))
        frame.putpixel((x, y + 1), (184, 211, 232, 255))
        sheet.alpha_composite(frame, (0, frame_index * 32))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    try:
        sheet.save(args.output, format="PNG", optimize=True)
    except OSError as exc:
        parser.error(f"cannot write output PNG: {exc}")

    print(f"Wrote {args.output} ({sheet.width}x{sheet.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
