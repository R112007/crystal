from __future__ import annotations

import math
import random
import shutil
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
BLOCK_DIR = ROOT / "assets" / "sprites" / "blocks" / "tumor"
PREVIEW = ROOT / "build" / "tumor-art-preview.png"

OUTLINE = (20, 4, 10, 255)
DEEP = (48, 4, 16, 255)
BLOOD = (88, 7, 24, 255)
RED = (132, 13, 31, 255)
FLESH = (174, 28, 44, 255)
HOT = (216, 54, 66, 255)
PINK = (245, 101, 108, 255)
HIGHLIGHT = (255, 175, 167, 255)
BONE = (222, 201, 173, 255)
BONE_LIGHT = (255, 237, 210, 255)
MARROW = (239, 183, 100, 255)
MEMBRANE = (104, 21, 51, 255)
BLACK = (12, 2, 7, 255)


def hash_noise(x: int, y: int, seed: int) -> int:
    n = x * 374761393 + y * 668265263 + seed * 1442695041
    n = (n ^ (n >> 13)) * 1274126177
    return (n ^ (n >> 16)) & 0x7FFFFFFF


def pixel_shadow(canvas: Image.Image, mask: set[tuple[int, int]], radius: int = 2) -> None:
    pixels = canvas.load()
    for x, y in mask:
        for dx in range(-radius, radius + 1):
            for dy in range(-radius, radius + 1):
                px, py = x + dx, y + dy
                if 0 <= px < canvas.width and 0 <= py < canvas.height and (px, py) not in mask:
                    if abs(dx) + abs(dy) <= radius + 1:
                        pixels[px, py] = OUTLINE


def draw_core(level: int, frame: int) -> Image.Image:
    image = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
    pix = image.load()
    cx, cy = 48.0, 49.0
    phase = frame * math.pi / 2
    base = 29.0 + level * 3.1 + math.sin(phase) * 1.6
    mask: set[tuple[int, int]] = set()
    for y in range(96):
        for x in range(96):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            angle = math.atan2(dy, dx)
            radius = math.hypot(dx, dy)
            lobes = (
                4.2
                + math.sin(angle * (5 + level) + phase) * (2.2 + level * 0.45)
                + math.sin(angle * (9 + level * 2) - phase * 0.6) * 1.25
                + math.cos(angle * 3) * 1.6
            )
            limit = base + lobes
            if level >= 3:
                limit += max(0.0, math.cos(angle * 4 + phase * 0.25)) * 6.0
            if level >= 4:
                limit += max(0.0, math.sin(angle * 7 - phase * 0.35)) * 4.0
            if radius < limit:
                mask.add((x, y))

    # Tendon-like stubs make the silhouette read as an organism rather than a circle.
    for index in range(4 + level):
        angle = index * math.tau / (4 + level) + 0.3
        length = base + 8 + (index % 2) * 3
        for step in range(int(base * 0.7), int(length)):
            x = int(cx + math.cos(angle) * step)
            y = int(cy + math.sin(angle) * step)
            for ox in range(-1, 2):
                for oy in range(-1, 2):
                    if abs(ox) + abs(oy) <= 1 and 0 <= x + ox < 96 and 0 <= y + oy < 96:
                        mask.add((x + ox, y + oy))

    pixel_shadow(image, mask, 2)
    for x, y in mask:
        dx, dy = x - cx, y - cy
        dist = math.hypot(dx, dy) / max(base, 1.0)
        noise = hash_noise(x, y, level * 41) % 17
        if dist < 0.25:
            color = RED if (x + y + level) % 5 else HOT
        elif dist < 0.58:
            color = FLESH if noise > 7 else HOT if noise < 2 else RED
        elif dist < 0.84:
            color = BLOOD if noise < 8 else RED
        else:
            color = DEEP if noise < 9 else BLOOD
        if noise == 0:
            color = PINK
        elif noise == 1:
            color = DEEP
        if dy > 8 + math.sin(dx * 0.28) * 4 and dy > abs(dx) * 0.25:
            color = BLOOD if color in (FLESH, RED, HOT) else color
        if dy < -4 and dist > 0.48:
            color = HOT if noise > 10 else FLESH
        pix[x, y] = color

    draw = ImageDraw.Draw(image)
    # Vein network: dark vessels, hot highlights, then tiny branch pixels.
    rng = random.Random(871 + level * 17 + frame)
    for branch in range(8 + level * 2):
        angle = rng.random() * math.tau
        for step in range(5, int(base * 0.9), 3):
            x = int(cx + math.cos(angle + math.sin(step * 0.12) * 0.08) * step)
            y = int(cy + math.sin(angle + math.sin(step * 0.12) * 0.08) * step)
            if 0 <= x < 96 and 0 <= y < 96:
                draw.rectangle((x - 1, y, x + 1, y), fill=OUTLINE)
                draw.point((x, y), fill=HOT if step % 7 else PINK)
                if step % 6 == 0:
                    draw.point((x + 1, y), fill=PINK)
                if step > 17 and step % 9 == 0:
                    draw.line((x, y, x + int(math.cos(angle + 1.2) * 5), y + int(math.sin(angle + 1.2) * 5)), fill=BLOOD)

    # Central maw and enamel growths.
    maw_w = 14 + level * 2
    maw_h = 9 + level
    draw.ellipse((cx - maw_w / 2, cy - maw_h / 2, cx + maw_w / 2, cy + maw_h / 2), fill=BLACK, outline=OUTLINE, width=2)
    draw.ellipse((cx - maw_w / 3, cy - maw_h / 3, cx + maw_w / 3, cy + maw_h / 3), fill=DEEP)
    for i in range(4 + level):
        ta = i * math.tau / (4 + level) + 0.25
        tx = cx + math.cos(ta) * (maw_w * 0.34)
        ty = cy + math.sin(ta) * (maw_h * 0.34)
        draw.polygon((tx, ty - 2, tx + 2, ty + 2, tx - 2, ty + 2), fill=BONE)

    # Calcium ribs grow with level.
    ribs = max(0, level - 1) * 3
    for i in range(ribs):
        a = i * math.tau / max(ribs, 1) + 0.2
        x1 = int(cx + math.cos(a) * (base * 0.45))
        y1 = int(cy + math.sin(a) * (base * 0.45))
        x2 = int(cx + math.cos(a + 0.15) * (base * 0.83))
        y2 = int(cy + math.sin(a + 0.15) * (base * 0.83))
        draw.line((x1, y1, x2, y2), fill=BONE, width=1)
        draw.point((x2, y2), fill=BONE_LIGHT)

    # Pustules pulse through the four-frame loop.
    for i in range(6 + level * 2):
        a = i * math.tau / (6 + level * 2) + 0.43
        r = base * (0.55 + (i % 3) * 0.09)
        px = int(cx + math.cos(a) * r)
        py = int(cy + math.sin(a) * r)
        size = 1 + ((i + frame) % 2)
        draw.ellipse((px - size, py - size, px + size, py + size), fill=HIGHLIGHT, outline=RED)

    # Small wet highlights break up the mottled flesh without making the sprite look glossy.
    for i in range(3 + level):
        a = -2.35 + i * 0.18
        px = int(cx + math.cos(a) * (base * 0.67))
        py = int(cy + math.sin(a) * (base * 0.67))
        draw.point((px, py), fill=PINK)
        draw.point((px + 1, py), fill=HIGHLIGHT)

    return image


def blob(size: int, seed: int, level: int = 1) -> Image.Image:
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pix = image.load()
    cx, cy = size / 2, size / 2
    mask: set[tuple[int, int]] = set()
    for y in range(size):
        for x in range(size):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            a = math.atan2(dy, dx)
            r = math.hypot(dx, dy)
            limit = size * 0.28 + math.sin(a * 5 + seed) * size * 0.045 + math.cos(a * 3) * size * 0.035
            if r < limit:
                mask.add((x, y))
    pixel_shadow(image, mask, 1)
    for x, y in mask:
        d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / (size * 0.32)
        n = hash_noise(x, y, seed) % 11
        pix[x, y] = RED if d < 0.42 else FLESH if n > 3 else BLOOD
    return image


def draw_meristem() -> Image.Image:
    image = blob(32, 3)
    draw = ImageDraw.Draw(image)
    draw.ellipse((12, 11, 20, 19), fill=HOT, outline=OUTLINE)
    draw.ellipse((14, 13, 17, 16), fill=HIGHLIGHT)
    draw.line((15, 20, 12, 27), fill=DEEP, width=2)
    draw.line((18, 20, 21, 28), fill=DEEP, width=2)
    draw.point((10, 8), fill=PINK)
    draw.point((23, 10), fill=PINK)
    return image


def draw_conveyor() -> Image.Image:
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 10, 30, 21), fill=OUTLINE)
    draw.rectangle((1, 12, 30, 19), fill=FLESH)
    draw.rectangle((2, 13, 29, 15), fill=HOT)
    draw.rectangle((2, 17, 29, 18), fill=BLOOD)
    for x in range(4, 29, 5):
        draw.line((x, 12, x + 2, 16, x, 20), fill=BONE, width=1)
    for x in (8, 17, 26):
        draw.polygon((x, 13, x + 3, 16, x, 19), fill=MEMBRANE)
    return image


def draw_drill() -> Image.Image:
    image = blob(32, 9)
    draw = ImageDraw.Draw(image)
    draw.ellipse((8, 7, 24, 23), fill=DEEP, outline=OUTLINE, width=2)
    draw.ellipse((11, 10, 21, 20), fill=HOT)
    draw.ellipse((14, 13, 18, 17), fill=BLACK)
    draw.polygon((16, 5, 21, 16, 16, 27, 11, 16), fill=BONE, outline=OUTLINE)
    draw.line((16, 8, 16, 24), fill=BONE_LIGHT)
    draw.point((13, 12), fill=MARROW)
    draw.point((19, 20), fill=MARROW)
    return image


def draw_turret_base() -> Image.Image:
    image = blob(32, 13)
    draw = ImageDraw.Draw(image)
    draw.ellipse((7, 7, 25, 25), fill=RED, outline=OUTLINE, width=2)
    draw.ellipse((11, 11, 21, 21), fill=HOT)
    draw.ellipse((14, 14, 18, 18), fill=BLACK)
    for i in range(6):
        a = i * math.tau / 6
        draw.point((16 + math.cos(a) * 10, 16 + math.sin(a) * 10), fill=BONE)
    return image


def draw_turret_barrel() -> Image.Image:
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse((10, 9, 22, 21), fill=FLESH, outline=OUTLINE)
    draw.rectangle((13, 1, 19, 16), fill=OUTLINE)
    draw.rectangle((14, 2, 18, 15), fill=BONE)
    draw.rectangle((15, 3, 17, 14), fill=BONE_LIGHT)
    draw.ellipse((12, 12, 20, 20), fill=HOT, outline=OUTLINE)
    draw.polygon((14, 1, 16, -2, 18, 1), fill=BONE)
    draw.point((16, 16), fill=BLACK)
    return image


def draw_wall() -> Image.Image:
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 30, 30), fill=OUTLINE)
    draw.rectangle((2, 2, 29, 29), fill=BLOOD)
    for i in range(5):
        inset = 3 + i * 2
        draw.arc((inset, inset, 31 - inset, 31 - inset), 15, 165, fill=FLESH, width=2)
        draw.arc((inset + 1, inset + 1, 30 - inset, 30 - inset), 195, 345, fill=RED, width=1)
    draw.line((6, 9, 25, 9), fill=BONE, width=2)
    draw.line((6, 22, 25, 22), fill=BONE, width=2)
    draw.polygon((16, 4, 21, 15, 16, 27, 11, 15), fill=HOT, outline=DEEP)
    draw.line((16, 7, 16, 24), fill=HIGHLIGHT)
    for y in (14, 18):
        draw.point((5, y), fill=PINK)
        draw.point((26, y), fill=PINK)
    return image


def draw_nursery() -> Image.Image:
    image = blob(64, 27, 2)
    draw = ImageDraw.Draw(image)
    draw.ellipse((14, 12, 50, 54), fill=RED, outline=OUTLINE, width=2)
    draw.ellipse((19, 17, 45, 49), fill=HOT)
    draw.ellipse((23, 21, 41, 45), fill=MEMBRANE, outline=DEEP)
    for i, (x, y) in enumerate(((27, 27), (34, 31), (29, 36), (37, 40))):
        draw.ellipse((x - 3, y - 3, x + 3, y + 3), fill=BLACK)
        draw.point((x, y), fill=PINK if i % 2 else MARROW)
    for angle in (-2.4, -1.2, 0.3, 2.5):
        x1 = 32 + math.cos(angle) * 20
        y1 = 33 + math.sin(angle) * 20
        x2 = 32 + math.cos(angle) * 30
        y2 = 33 + math.sin(angle) * 30
        draw.line((x1, y1, x2, y2), fill=OUTLINE, width=4)
        draw.line((x1, y1, x2, y2), fill=FLESH, width=2)
    return image


def shrink_inside(image: Image.Image, factor: float) -> Image.Image:
    """Keeps the texture dimensions/collision footprint, but adds transparent visual margin."""
    canvas = Image.new("RGBA", image.size, (0, 0, 0, 0))
    width = max(1, round(image.width * factor))
    height = max(1, round(image.height * factor))
    resized = image.resize((width, height), Image.Resampling.NEAREST)
    canvas.alpha_composite(resized, ((image.width - width) // 2, (image.height - height) // 2))
    return canvas


def main() -> None:
    BLOCK_DIR.mkdir(parents=True, exist_ok=True)
    core_images: list[Image.Image] = []
    for level in range(1, 5):
        for frame in range(4):
            image = shrink_inside(draw_core(level, frame), 0.72)
            image.save(BLOCK_DIR / f"tumor-core-{level}-{frame}.png")
            if level == 1 and frame == 0:
                image.save(BLOCK_DIR / "tumor-core.png")
            core_images.append(image)

    shrink_inside(draw_meristem(), 0.62).save(BLOCK_DIR / "tumor-meristem.png")
    shrink_inside(draw_conveyor(), 0.86).save(BLOCK_DIR / "tumor-conveyor.png")
    shrink_inside(draw_drill(), 0.68).save(BLOCK_DIR / "tumor-drill.png")
    shrink_inside(draw_turret_base(), 0.70).save(BLOCK_DIR / "tumor-turret.png")
    shrink_inside(draw_turret_barrel(), 0.70).save(BLOCK_DIR / "tumor-turret-preview.png")
    shrink_inside(draw_turret_barrel(), 0.70).save(BLOCK_DIR / "tumor-turret-heat.png")
    shrink_inside(draw_wall(), 0.78).save(BLOCK_DIR / "tumor-wall.png")
    shrink_inside(draw_nursery(), 0.75).save(BLOCK_DIR / "tumor-nursery.png")

    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    preview = Image.new("RGBA", (6 * 128, 3 * 144), (18, 12, 18, 255))
    draw = ImageDraw.Draw(preview)
    thumbs = [
        core_images[0],
        core_images[4],
        core_images[8],
        core_images[12],
        shrink_inside(draw_meristem(), 0.62),
        shrink_inside(draw_drill(), 0.68),
        shrink_inside(draw_conveyor(), 0.86),
        shrink_inside(draw_turret_base(), 0.70),
        shrink_inside(draw_turret_barrel(), 0.70),
        shrink_inside(draw_wall(), 0.78),
        shrink_inside(draw_nursery(), 0.75),
        core_images[15],
    ]
    labels = ["L1", "L2", "L3", "L4", "Meristem", "Drill", "Vein", "Turret", "Barrel", "Wall", "Nursery", "Pulse"]
    for index, (thumb, label) in enumerate(zip(thumbs, labels)):
        cell_x = (index % 6) * 128
        cell_y = (index // 6) * 144
        fitted = thumb.copy()
        fitted.thumbnail((112, 112), Image.Resampling.NEAREST)
        preview.alpha_composite(fitted, (cell_x + (128 - fitted.width) // 2, cell_y + 8))
        draw.text((cell_x + 6, cell_y + 124), label, fill=(255, 220, 215, 255))
    preview.resize((preview.width * 2, preview.height * 2), Image.Resampling.NEAREST).save(PREVIEW)


if __name__ == "__main__":
    main()
