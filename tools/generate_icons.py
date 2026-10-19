#!/usr/bin/env python3
"""
Generate launcher icons for Task Tracker Pro.
Run: python3 tools/generate_icons.py
Requires: Pillow (pip install Pillow)
"""
from PIL import Image, ImageDraw
import os

BASE = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def main():
    for d, s in SIZES.items():
        os.makedirs(os.path.join(BASE, d), exist_ok=True)
        img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        r = s // 5
        draw.rounded_rectangle([0, 0, s - 1, s - 1], radius=r, fill=(0, 150, 136, 255))
        w = max(2, s // 12)
        p1 = (s * 0.28, s * 0.55)
        p2 = (s * 0.45, s * 0.70)
        p3 = (s * 0.74, s * 0.32)
        draw.line([p1, p2, p3], fill=(255, 255, 255, 255), width=w, joint="curve")
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            img.save(os.path.join(BASE, d, name))
        print(f"{d}: {s}x{s} done")

if __name__ == "__main__":
    main()
