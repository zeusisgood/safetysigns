#!/usr/bin/env python3
"""看板のテクスチャを生成する（Pillow）。

図柄は「標識の形（警告・禁止・指示）」と「記号の部品（parts.py）」の組み合わせで定義する（SIGNS）。
手でドットを打って仕上げた図柄は tools/texgen/manual/<名前>.png に置く。置いた図柄は生成せず、
そのファイルをそのまま出力にコピーする（スクリプトを流し直しても手の仕上げが消えない）。

図柄のテクスチャは、標識の形の外側を透明にする。看板の板はテクスチャの透明でない所の形に切り抜かれる
（仕様書 SPEC_2026-10-12-2.md）。

使い方: python3 tools/texgen/generate.py
出力: src/main/resources/assets/safetysigns/textures/{signs,blocks}/*.png と doc/textures/preview.png
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import parts
from parts import BLACK, BLUE, CENTER, CLEAR, GREEN, RED, SIZE, WHITE, YELLOW

ROOT = Path(__file__).resolve().parents[2]
TEXTURES = ROOT / "src/main/resources/assets/safetysigns/textures"
DEFINITIONS = ROOT / "src/main/resources/assets/safetysigns/safetysigns/signs.json"
MANUAL = Path(__file__).resolve().parent / "manual"
PREVIEW = ROOT / "doc/textures/preview.png"

# 無地の金属板
PLATE = (0xE4, 0xE6, 0xE8, 255)
PLATE_EDGE = (0xA7, 0xAD, 0xB2, 255)
# 板の裏面と縁、ポール付きの固定金具（灰色の金属板。仕様書 SPEC_2026-10-12-2.md）
BACK = (0x8C, 0x91, 0x96, 255)
# ポールの支柱（仕様書 SPEC_2026-10-12-2.md）。基調の色と、丸く見せる明るい列・暗い列
POLE = (0xDD, 0xE0, 0xE2, 255)
POLE_LIGHT = (0xF0, 0xF2, 0xF3, 255)
POLE_DARK = (0xBA, 0xBF, 0xC3, 255)
# 一覧画像の背景（切り抜いた形が分かる暗い灰色）と文字
PREVIEW_BG = (0x40, 0x40, 0x40, 255)
PREVIEW_TEXT = (0xF0, 0xF0, 0xF0, 255)
PREVIEW_NOTE = (0xA0, 0xA0, 0xA0, 255)


# ---- 標識の形 ----
# 形ごとに、使ってよい色と描く処理（形 → 記号 → 禁止の輪と斜線）を持つ。記号は (部品, 位置の引数) の並び。

def warning(*symbols):
    """警告（W）: 黄の正三角形に黒枠と黒の記号。"""
    def draw(image):
        parts.warning_triangle(image)
        for part, args in symbols:
            part(image, *args, BLACK)
    return {"category": "W", "colors": {YELLOW, BLACK}, "draw": draw}


def prohibition(*symbols):
    """禁止（P）: 白地に黒の記号、その上に赤い円と斜線。"""
    def draw(image):
        parts.disc(image, WHITE)
        for part, args in symbols:
            part(image, *args, BLACK)
        parts.ring(image, RED)
        parts.slash(image, RED)
    return {"category": "P", "colors": {WHITE, RED, BLACK}, "draw": draw}


def mandatory(*symbols):
    """指示（M）: 青い円に白の記号。"""
    def draw(image):
        parts.disc(image, BLUE)
        for part, args in symbols:
            part(image, *args, WHITE)
    return {"category": "M", "colors": {BLUE, WHITE}, "draw": draw}


# ---- 図柄の定義 ----
# キーはテクスチャのファイル名（textures/signs/<キー>.png）。位置の引数は部品ごと（parts.py）。
# 三角形の記号は、中の黄色の重心より少し下（三角形の上は細いので）に置く。

SIGNS = {
    "w001": warning((parts.exclamation, (CENTER, 13))),
    "w002": warning((parts.explosion, (CENTER, 15))),
    "w003": warning((parts.trefoil, (CENTER, 20))),
    "w012": warning((parts.lightning, (CENTER, 14))),
    "w017": warning((parts.heat, (CENTER, 17))),
    "m010": mandatory((parts.coverall_person, (CENTER, 6))),
    "p004": prohibition((parts.walking_person, (CENTER, 6))),
    "p010": prohibition((parts.hand, (CENTER, 8))),
}


def check_colors(name, image, allowed):
    """決めた色と透明以外のピクセル（中間色・半透明）がないか確かめる。"""
    allowed = allowed | {CLEAR}
    stray = {color for _, color in image.getcolors(SIZE * SIZE) if color not in allowed}
    if stray:
        raise SystemExit(f"{name}: 決めた色以外のピクセルがある: {sorted(stray)[:5]}")


def load_manual(name, allowed):
    """手で仕上げた図柄を読む。大きさが違えば止め、色が外れていれば警告だけ出して使う。"""
    image = Image.open(MANUAL / f"{name}.png").convert("RGBA")
    if image.size != (SIZE, SIZE):
        raise SystemExit(f"manual/{name}.png: 大きさが {image.size}（{SIZE}×{SIZE} にする）")
    try:
        check_colors(f"manual/{name}.png", image, allowed)
    except SystemExit as error:
        print(f"warning: {error}", file=sys.stderr)
    return image


def blank():
    """無地の前面（看板の板そのもの）。"""
    image = Image.new("RGBA", (SIZE, SIZE), PLATE)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=PLATE_EDGE)
    return image


def back():
    """板の裏面と縁・固定金具（16×16 の無地。描画はどこを取っても同じ色になる前提）。"""
    return Image.new("RGBA", (16, 16), BACK)


def pole():
    """ポールの支柱（16×16。モデルは左端の 4 列を 4 ピクセル角の支柱の各面に貼る）。"""
    image = Image.new("RGBA", (16, 16), POLE)
    draw = ImageDraw.Draw(image)
    for x in range(0, 16, 4):
        draw.line([(x, 0), (x, 15)], fill=POLE_LIGHT)
        draw.line([(x + 3, 0), (x + 3, 15)], fill=POLE_DARK)
    return image


# 「?」（一覧にない図柄 ID）の 3×5 ドットを 4 倍で描く
QUESTION = ["###", "..#", ".##", "...", ".#."]


def unknown():
    """一覧にない図柄 ID の「?」。"""
    image = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 29, 29], fill=BLACK)
    for row, line in enumerate(QUESTION):
        for col, dot in enumerate(line):
            if dot == "#":
                draw.rectangle([10 + col * 4, 6 + row * 4, 13 + col * 4, 9 + row * 4], fill=WHITE)
    return image


def english_names():
    """定義ファイルから、テクスチャのファイル名 → (コード, 英語の名前) を作る（一覧画像の見出し）。"""
    with open(DEFINITIONS, encoding="utf-8") as file:
        signs = json.load(file)["signs"]
    names = {}
    for sign in signs:
        texture = sign.get("texture", sign["id"].replace(":", ":signs/", 1))
        names[texture.split("/")[-1]] = (sign.get("code", ""), sign.get("names", {}).get("en_us", ""))
    return names


def wrap(draw, text, font, width):
    """見出しを width ピクセルに収まるように単語で折り返す。"""
    lines, line = [], ""
    for word in text.split():
        candidate = f"{line} {word}".strip()
        if line and draw.textlength(candidate, font=font) > width:
            lines.append(line)
            line = word
        else:
            line = candidate
    return lines + [line] if line else lines


def write_preview(entries):
    """一覧画像: 8 倍に拡大して暗い背景に並べ、下にコードと名前を書く（透明な所は看板では板がない）。"""
    scale = 8
    cell = SIZE * scale
    gap = 16
    label = 64
    columns = 5
    rows = (len(entries) + columns - 1) // columns
    preview = Image.new("RGBA", (columns * (cell + gap) + gap, rows * (cell + label + gap) + gap), PREVIEW_BG)
    draw = ImageDraw.Draw(preview)
    # Pillow 内蔵のフォント（環境によって一覧画像が変わらないように、システムのフォントは使わない）
    title_font = ImageFont.load_default(size=18)
    name_font = ImageFont.load_default(size=13)
    for i, (code, name, image) in enumerate(entries):
        x = gap + (i % columns) * (cell + gap)
        y = gap + (i // columns) * (cell + label + gap)
        composed = Image.alpha_composite(Image.new("RGBA", (SIZE, SIZE), PREVIEW_BG), image)
        preview.paste(composed.resize((cell, cell), Image.NEAREST), (x, y))
        draw.text((x, y + cell + 6), code, font=title_font, fill=PREVIEW_TEXT)
        for j, line in enumerate(wrap(draw, name, name_font, cell)[:2]):
            draw.text((x, y + cell + 28 + j * 16), line, font=name_font, fill=PREVIEW_NOTE)
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    preview.save(PREVIEW)


# 手で描いただけの図柄（SIGNS にない名前）を確かめる色。名前の先頭の文字で分類を決める
CATEGORY_COLORS = {"w": {YELLOW, BLACK}, "p": {WHITE, RED, BLACK}, "m": {BLUE, WHITE}}
ALL_COLORS = {YELLOW, BLACK, RED, BLUE, GREEN, WHITE}


def main():
    names = english_names()
    entries = []
    manual_only = sorted(path.stem for path in MANUAL.glob("*.png") if path.stem not in SIGNS)
    for name in list(SIGNS) + manual_only:
        sign = SIGNS.get(name)
        if (MANUAL / f"{name}.png").exists():
            allowed = sign["colors"] if sign else CATEGORY_COLORS.get(name[0], ALL_COLORS)
            image = load_manual(name, allowed)
            source = "manual"
        else:
            image = parts.new_canvas()
            sign["draw"](image)
            check_colors(name, image, sign["colors"])
            source = "generated"
        image.save(TEXTURES / "signs" / f"{name}.png")
        code, english = names.get(name, (name.upper(), ""))
        if source == "manual":
            english += " (manual)"
        entries.append((code, english, image))
        print(f"{name}: {source}")

    for name, image, title in [("blank", blank(), "Blank"), ("unknown", unknown(), "Unknown pattern ID")]:
        image.save(TEXTURES / "blocks" / f"sign_{name}.png")
        entries.append((name, title, image))
    # 支柱と裏面は図柄ではないので、一覧画像には入れない
    pole().save(TEXTURES / "blocks" / "sign_pole.png")
    back().save(TEXTURES / "blocks" / "sign_back.png")

    write_preview(entries)
    print(f"wrote {len(entries)} textures and {PREVIEW.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
