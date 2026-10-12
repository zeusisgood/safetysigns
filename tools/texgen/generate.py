#!/usr/bin/env python3
"""看板のテクスチャを生成する（Pillow）。

いまは仮のテクスチャ（仕様書「9. 作業の順番」の 2）。カテゴリの形と色に、ISO のコードの番号を
3×5 ドットの数字で描くだけ。本番の図柄（放射線マーク・稲妻・炎など）は 9 の 7 でこのスクリプトを書き直す。

図柄のテクスチャは、標識の形の外側を透明にする。看板の板はテクスチャの透明でない所の形に切り抜かれる
（仕様書 SPEC_2026-10-12-2.md）。

使い方: python3 tools/texgen/generate.py
出力: src/main/resources/assets/safetysigns/textures/{signs,blocks}/*.png と doc/textures/preview.png
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
TEXTURES = ROOT / "src/main/resources/assets/safetysigns/textures"
PREVIEW = ROOT / "doc/textures/preview.png"

SIZE = 32

# ISO 3864 の安全色の近似（仕様書 3.3）
YELLOW = (0xF9, 0xA8, 0x00, 255)
BLACK = (0x1A, 0x1A, 0x1A, 255)
RED = (0x9B, 0x24, 0x23, 255)
BLUE = (0x00, 0x53, 0x87, 255)
GREEN = (0x23, 0x7F, 0x52, 255)
WHITE = (0xFF, 0xFF, 0xFF, 255)
CLEAR = (0, 0, 0, 0)
# 無地の金属板
PLATE = (0xE4, 0xE6, 0xE8, 255)
PLATE_EDGE = (0xA7, 0xAD, 0xB2, 255)
# 板の裏面と縁、ポール付きの固定金具（灰色の金属板。仕様書 SPEC_2026-10-12-2.md）
BACK = (0x8C, 0x91, 0x96, 255)
# ポールの支柱（仕様書 SPEC_2026-10-12-2.md）。基調の色と、丸く見せる明るい列・暗い列
POLE = (0xDD, 0xE0, 0xE2, 255)
POLE_LIGHT = (0xF0, 0xF2, 0xF3, 255)
POLE_DARK = (0xBA, 0xBF, 0xC3, 255)
# 一覧画像の背景（切り抜いた形が分かる暗い灰色）
PREVIEW_BG = (0x40, 0x40, 0x40, 255)

# 3×5 ドットの数字と「?」
GLYPHS = {
    "0": ["###", "#.#", "#.#", "#.#", "###"],
    "1": [".#.", "##.", ".#.", ".#.", "###"],
    "2": ["###", "..#", "###", "#..", "###"],
    "3": ["###", "..#", ".##", "..#", "###"],
    "4": ["#.#", "#.#", "###", "..#", "..#"],
    "5": ["###", "#..", "###", "..#", "###"],
    "6": ["###", "#..", "###", "#.#", "###"],
    "7": ["###", "..#", ".#.", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"],
    "9": ["###", "#.#", "###", "..#", "###"],
    "?": ["###", "..#", ".##", "...", ".#."],
}

# MVP の 8 種（仕様書 3.1）
SIGNS = ["w001", "w002", "w003", "w012", "w017", "m010", "p004", "p010"]


def draw_text(image, text, x, y, color, scale=1):
    for i, char in enumerate(text):
        for row, line in enumerate(GLYPHS[char]):
            for col, dot in enumerate(line):
                if dot == "#":
                    for dy in range(scale):
                        for dx in range(scale):
                            image.putpixel((x + (i * 4 + col) * scale + dx, y + row * scale + dy), color)


def text_width(text, scale=1):
    return (len(text) * 4 - 1) * scale


def warning(number):
    """警告: 黄の正三角形に黒枠。31 ピクセル幅で、頂点を 1 ピクセルにする。"""
    image = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    draw = ImageDraw.Draw(image)
    top, bottom = 4, 30
    draw.polygon([(15, top), (0, bottom), (30, bottom)], fill=BLACK)
    draw.polygon([(15, top + 4), (3, bottom - 2), (27, bottom - 2)], fill=YELLOW)
    draw_text(image, number, 15 - text_width(number) // 2, 20, BLACK)
    return image


def prohibition(number):
    """禁止: 白地に赤い円と斜線。"""
    image = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.ellipse([0, 0, 31, 31], fill=RED)
    draw.ellipse([4, 4, 27, 27], fill=WHITE)
    draw.line([(7, 7), (24, 24)], fill=RED, width=4)
    # 番号が斜線に埋もれないように、白い札の上に描く
    left = 16 - text_width(number) // 2
    draw.rectangle([left - 2, 12, left + text_width(number) + 1, 20], fill=WHITE, outline=BLACK)
    draw_text(image, number, left, 14, BLACK)
    return image


def mandatory(number):
    """指示: 青い円。"""
    image = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.ellipse([0, 0, 31, 31], fill=BLUE)
    draw_text(image, number, 16 - text_width(number, 2) // 2, 11, WHITE, scale=2)
    return image


SHAPES = {"w": warning, "p": prohibition, "m": mandatory}


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
    """ポールの支柱（16×16。モデルは左端の 4 列を 4 ピクセル角の支柱の各面に貼る）。仮のテクスチャ。"""
    image = Image.new("RGBA", (16, 16), POLE)
    draw = ImageDraw.Draw(image)
    for x in range(0, 16, 4):
        draw.line([(x, 0), (x, 15)], fill=POLE_LIGHT)
        draw.line([(x + 3, 0), (x + 3, 15)], fill=POLE_DARK)
    return image


def unknown():
    """一覧にない図柄 ID の「?」。"""
    image = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 29, 29], fill=BLACK)
    draw_text(image, "?", 16 - text_width("?", 4) // 2, 6, WHITE, scale=4)
    return image


def main():
    outputs = []
    for sign in SIGNS:
        image = SHAPES[sign[0]](sign[1:])
        path = TEXTURES / "signs" / f"{sign}.png"
        image.save(path)
        outputs.append((sign.upper(), image))
    for name, image in [("blank", blank()), ("unknown", unknown())]:
        image.save(TEXTURES / "blocks" / f"sign_{name}.png")
        outputs.append((name, image))
    # 支柱と裏面は図柄ではないので、一覧画像には入れない
    pole().save(TEXTURES / "blocks" / "sign_pole.png")
    back().save(TEXTURES / "blocks" / "sign_back.png")

    # 一覧画像: 8 倍に拡大して、暗い背景に並べる（透明な所は看板では板がない＝背景が見える）
    scale = 8
    cell = SIZE * scale
    gap = 8
    columns = 5
    rows = (len(outputs) + columns - 1) // columns
    preview = Image.new("RGBA", (columns * (cell + gap) + gap, rows * (cell + gap) + gap), PREVIEW_BG)
    for i, (_, image) in enumerate(outputs):
        composed = Image.alpha_composite(Image.new("RGBA", (SIZE, SIZE), PREVIEW_BG), image)
        x = gap + (i % columns) * (cell + gap)
        y = gap + (i // columns) * (cell + gap)
        preview.paste(composed.resize((cell, cell), Image.NEAREST), (x, y))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    preview.save(PREVIEW)
    print(f"wrote {len(outputs)} textures and {PREVIEW.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
