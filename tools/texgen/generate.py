#!/usr/bin/env python3
"""看板のテクスチャを生成する（Pillow）。

図柄は「標識の形（警告・禁止・指示・安全状態・防火）」と「記号の部品（parts.py）」の組み合わせで定義する（SIGNS）。
部品の形は設計の単位（0〜64）で書き、出力の解像度のマス目に直接ドット絵として塗る（raster.py。縮小はしない）。
手でドットを打って仕上げた図柄は tools/texgen/manual/<名前>.png に置く。置いた図柄は生成せず、
そのファイルをそのまま出力にコピーする（スクリプトを流し直しても手の仕上げが消えない）。

図柄のテクスチャは、標識の形の外側を透明にする。看板の板はテクスチャの透明でない所の形に切り抜かれる
（仕様書 SPEC_2026-10-12-2.md）。

内蔵の図柄はすべて 128×128 で描く（--size の既定。仕様書 SPEC_2026-10-12-11.md）。設計の座標は 0〜64 なので、
128 では 1 単位が 2 ピクセルになる。図柄ごとに解像度を変えたいときは、SIGNS で hires(...) に包むと --size の倍数で描く
（今は使っていない）。

使い方: python3 tools/texgen/generate.py [--size 128]
  --size  図柄・無地・「?」の基本の解像度（16 以上 512 以下の 2 の累乗。既定 128）。hires の図柄はその倍数（512 まで）。
          裏面と支柱はいつも 16×16、アイテムのアイコンはいつも 32×32
出力: src/main/resources/assets/safetysigns/textures/{signs,blocks,items}/*.png、doc/textures/{preview,parts,items}.png、
      doc/store/icon.png（CurseForge のアイコン）、src/main/resources/logo.png（Forge の Mod 一覧のロゴ）
"""
import argparse
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import parts
import raster
from parts import BLACK, BLUE, CLEAR, GREEN, HAZARD_RED, RED, WHITE, YELLOW

ROOT = Path(__file__).resolve().parents[2]
TEXTURES = ROOT / "src/main/resources/assets/safetysigns/textures"
DEFINITIONS = ROOT / "src/main/resources/assets/safetysigns/safetysigns/signs.json"
MANUAL = Path(__file__).resolve().parent / "manual"
PREVIEW = ROOT / "doc/textures/preview.png"
PARTS_PREVIEW = ROOT / "doc/textures/parts.png"
ITEMS_PREVIEW = ROOT / "doc/textures/items.png"
STORE_ICON = ROOT / "doc/store/icon.png"
MOD_LOGO = ROOT / "src/main/resources/logo.png"
DEFAULT_SIZE = 128

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
# 形ごとに、使ってよい色と描く処理（形 → 記号 → 禁止の輪と斜線）を持つ。記号は部品の図形（parts.py）。
# 記号は、形の中（黄・白・青・緑・赤の地）から SYMBOL_MARGIN 単位より内側に収める（枠や輪にくっつかないように）。

SYMBOL_MARGIN = 1.5


def check_margin(symbol, field, grid=128):
    """記号が、地 field の縁から SYMBOL_MARGIN より内側にあるか確かめる（外れたら止まる）。

    出力の解像度によらず同じ結果になるよう、細かいマス目（grid × grid）の点で確かめる。
    """
    unit = raster.UNITS / grid
    keep_out = raster.dilate(raster.Shape(lambda x, y: not field(x, y)), SYMBOL_MARGIN)
    for gy in range(grid):
        for gx in range(grid):
            x, y = (gx + 0.5) * unit, (gy + 0.5) * unit
            if symbol(x, y) and keep_out(x, y):
                raise ValueError(f"記号が地の縁に近すぎる（設計の座標 {x:.1f}, {y:.1f}）")


def warning(*symbols):
    """警告（W）: 黄の正三角形に黒枠と黒の記号。"""
    def draw(image):
        outer, inner = parts.warning_triangle()
        raster.fill(image, outer, BLACK)
        raster.fill(image, inner, YELLOW)
        symbol = raster.union(*symbols)
        check_margin(symbol, inner)
        raster.fill(image, symbol, BLACK)
    return {"category": "W", "colors": {YELLOW, BLACK}, "draw": draw}


def prohibition(*symbols):
    """禁止（P）: 白地に黒の記号、その上に赤い円と斜線。"""
    def draw(image):
        raster.fill(image, parts.disc(), WHITE)
        symbol = raster.union(*symbols)
        check_margin(symbol, parts.disc(parts.RING_INNER))
        raster.fill(image, symbol, BLACK)
        raster.fill(image, parts.ring() | parts.slash(), RED)
    return {"category": "P", "colors": {WHITE, RED, BLACK}, "draw": draw}


def mandatory(*symbols):
    """指示（M）: 青い円に白の記号。"""
    def draw(image):
        raster.fill(image, parts.disc(), BLUE)
        symbol = raster.union(*symbols)
        check_margin(symbol, parts.disc())
        raster.fill(image, symbol, WHITE)
    return {"category": "M", "colors": {BLUE, WHITE}, "draw": draw}


def safe_condition(*symbols):
    """安全状態（E）: 緑の四角に白の記号（仕様書 SPEC_2026-10-12-6.md）。"""
    def draw(image):
        raster.fill(image, parts.square(), GREEN)
        symbol = raster.union(*symbols)
        check_margin(symbol, parts.square())
        raster.fill(image, symbol, WHITE)
    return {"category": "E", "colors": {GREEN, WHITE}, "draw": draw}


def fire(*symbols):
    """防火（F）: 赤の四角に白の記号（仕様書 SPEC_2026-10-12-6.md）。"""
    def draw(image):
        raster.fill(image, parts.square(), RED)
        symbol = raster.union(*symbols)
        check_margin(symbol, parts.square())
        raster.fill(image, symbol, WHITE)
    return {"category": "F", "colors": {RED, WHITE}, "draw": draw}


def ghs(*symbols):
    """GHS 絵表示: 白地のひし形に赤枠と黒の記号（仕様書 SPEC_2026-10-12-7.md）。"""
    def draw(image):
        outer, inner = parts.diamond()
        raster.fill(image, outer, HAZARD_RED)
        raster.fill(image, inner, WHITE)
        symbol = raster.union(*symbols)
        check_margin(symbol, inner)
        raster.fill(image, symbol, BLACK)
    return {"category": "GHS", "colors": {WHITE, HAZARD_RED, BLACK}, "draw": draw}


def ionizing_radiation(*symbols):
    """ISO 21482（電離放射線）: 赤の正三角形に黒枠と黒の記号。三角形は警告と同じ形（仕様書 SPEC_2026-10-12-7.md）。"""
    def draw(image):
        outer, inner = parts.warning_triangle()
        raster.fill(image, outer, BLACK)
        raster.fill(image, inner, HAZARD_RED)
        symbol = raster.union(*symbols)
        check_margin(symbol, inner)
        raster.fill(image, symbol, BLACK)
    return {"category": "OTHER", "colors": {HAZARD_RED, BLACK}, "draw": draw}


def hires(sign, factor=2):
    """図柄を、基本の解像度（--size）の factor 倍で描く。図柄ごとに解像度を変える仕組み（今の内蔵の図柄では使っていない）。"""
    return dict(sign, scale=factor)


def sign_size(sign, size):
    """図柄を描く解像度。基本の解像度 size に図柄ごとの倍率を掛ける（512 まで）。"""
    return min(512, size * (sign or {}).get("scale", 1))


# ---- 図柄の定義 ----
# キーはテクスチャのファイル名（textures/signs/<キー>.png）。部品の位置と大きさは設計の単位（0〜64、中心 32）。
# 三角形の記号は、中の黄色の重心（parts.TRIANGLE_SYMBOL_Y）の辺りに置く。
# 大きすぎて地の縁に近づくと check_margin で止まるので、止まったら小さくするか位置をずらす。

C = parts.CENTER
TY = parts.TRIANGLE_SYMBOL_Y
# 保護具の図柄（M003・M004・M013・M014・M016・M017）の頭部の中心と高さ。図柄どうしで頭の大きさ・位置を揃える
HEAD_GEAR_PLACE = (C, C + 2.5, 40)

SIGNS = {
    "w001": warning(parts.exclamation(C, TY - 2, 29)),
    "w002": warning(parts.explosion(C, TY - 0.5, 23)),
    "w003": warning(parts.trefoil(C, TY - 0.5, 25)),
    "w004": warning(parts.laser(C + 1.5, 45, 15.5)),
    "w005": warning(parts.antenna(C, 40, 27)),
    "w006": warning(parts.magnet(C, 40, 28)),
    "w009": warning(parts.biohazard(C, 40.5, 25)),
    "w010": warning(parts.snowflake(C, 41, 23.5)),
    "w012": warning(parts.lightning(C, TY - 1, 27)),
    "w016": warning(parts.skull_crossbones(C, TY - 0.5, 23.5)),
    "w017": warning(parts.hot_surface(C, TY + 0.5, 23)),
    "w018": warning(parts.automatic_start(C, 41.5, 24)),
    "w019": warning(parts.crushing(C, 36, 40)),
    "w021": warning(parts.flame(C, TY - 0.5, 24)),
    "w023": warning(parts.corrosive_triangle(C, 40, 40)),
    "w026": warning(parts.battery(C, 43, 26)),
    "w029": warning(parts.upright_cylinder(C, 38.5, 30)),
    "w042": warning(parts.arc_flash(C, 39.5, 27)),
    "w079": warning(parts.hot_content(C, 41, 25)),
    "w080": warning(parts.hot_steam(C, 36, 40)),
    "m001": mandatory(parts.exclamation(C, C, 40)),
    "m002": mandatory(parts.open_book(C, C + 0.5, 40)),
    "m003": mandatory(parts.protected_head(*HEAD_GEAR_PLACE, "ear_muffs")),
    "m004": mandatory(parts.protected_head(*HEAD_GEAR_PLACE, "goggles")),
    "m008": mandatory(parts.safety_boot(C, C, 40)),
    "m009": mandatory(parts.glove(C, C, 44)),
    "m010": mandatory(parts.coverall_person(C, C + 0.5, 48)),
    "m013": mandatory(parts.protected_head(*HEAD_GEAR_PLACE, "face_shield")),
    "m014": mandatory(parts.protected_head(*HEAD_GEAR_PLACE, "helmet")),
    "m016": mandatory(parts.protected_head(*HEAD_GEAR_PLACE, "dust_mask")),
    "m017": mandatory(parts.protected_head(*HEAD_GEAR_PLACE, "respirator")),
    "p001": prohibition(),
    "p002": prohibition(parts.cigarette(C, C, 40)),
    "p003": prohibition(parts.burning_match(C, C, 40)),
    "p004": prohibition(parts.person(C - 0.5, C, 44, "walking")),
    "p010": prohibition(parts.hand(C + 1, C + 0.5, 42)),
    "p011": prohibition(parts.bucket_on_fire(C, C, 40)),
    "p013": prohibition(parts.mobile_phone(C, C, 40)),
    "e001": safe_condition(parts.exit_door(C, C, 52, mirror=True)),
    "e002": safe_condition(parts.exit_door(C, C, 52)),
    "e003": safe_condition(parts.first_aid_cross(C, C, 44)),
    "e011": safe_condition(parts.eyewash(C, C, 50)),
    "e012": safe_condition(parts.safety_shower(C, C, 52)),
    "e020": safe_condition(parts.stop_button(C, C + 1, 44)),
    "f001": fire(parts.extinguisher(C, C, 50)),
    "f005": fire(parts.call_point(C, C - 1, 44)),
    "ghs01": ghs(parts.exploding_bomb(C, C, 34)),
    "ghs02": ghs(parts.flame_on_bar(C, C, 30)),
    "ghs03": ghs(parts.flame_over_circle(C, C, 30)),
    "ghs04": ghs(parts.gas_cylinder(C, C, 36)),
    "ghs05": ghs(parts.corrosion(C, C, 40)),
    "ghs06": ghs(parts.skull_crossbones(C, C - 2.5, 30)),
    "ghs07": ghs(parts.exclamation(C, C, 30)),
    "ghs08": ghs(parts.health_hazard(C, C, 30)),
    "ghs09": ghs(parts.environment(C, C - 1, 29)),
    "iso21482": ionizing_radiation(parts.trefoil(C, 28.6, 12.6),
                                   parts.radiation_waves(C, 28.6, angles=(-28, 0, 28), start=8.2, end=11.4,
                                                         amplitude=0.7, wavelength=7.6, r=0.75),
                                   parts.skull_crossbones(21.6, 45.0, 17),
                                   parts.person(40.6, 45.6, 17.2, "running")),
}


def check_colors(name, image, allowed):
    """決めた色と透明以外のピクセル（中間色・半透明）がないか確かめる。"""
    allowed = allowed | {CLEAR}
    stray = {color for _, color in image.getcolors(image.width * image.height) if color not in allowed}
    if stray:
        raise SystemExit(f"{name}: 決めた色以外のピクセルがある: {sorted(stray)[:5]}")


def is_power_of_two(n):
    return n > 0 and n & (n - 1) == 0


def load_manual(name, allowed, size):
    """手で仕上げた図柄を読む。2 の累乗の正方形でなければ止め、解像度が size と違えば・色が外れていれば警告だけ出して使う。"""
    image = Image.open(MANUAL / f"{name}.png").convert("RGBA")
    width, height = image.size
    if width != height or not is_power_of_two(width):
        raise SystemExit(f"manual/{name}.png: 大きさが {image.size}（2 の累乗の正方形にする）")
    if width != size:
        print(f"warning: manual/{name}.png: 解像度が {width}（生成は {size}）", file=sys.stderr)
    try:
        check_colors(f"manual/{name}.png", image, allowed)
    except SystemExit as error:
        print(f"warning: {error}", file=sys.stderr)
    return image


def blank(size):
    """無地の前面（看板の板そのもの）。縁の線は 32 ピクセルあたり 1 ピクセル。"""
    image = Image.new("RGBA", (size, size), PLATE)
    draw = ImageDraw.Draw(image)
    for i in range(max(1, size // 32)):
        draw.rectangle([i, i, size - 1 - i, size - 1 - i], outline=PLATE_EDGE)
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


# ---- アイテムのアイコン（仕様書 SPEC_2026-10-25.md） ----
# 32×32 で固定（--size に従わない）。座標はピクセルで書き、三角は設計の単位（1 ピクセル = 2 単位）に直して塗る。

ITEM_SIZE = 32
ITEM_COLORS = {PLATE, PLATE_EDGE, YELLOW, BLACK, POLE, POLE_LIGHT, POLE_DARK}
# CurseForge のアイコン・Forge の Mod 一覧のロゴの背景（透明な所を塗る）
LOGO_BG = (0x2B, 0x2F, 0x33, 255)


def item_plate(image, x0, y0, x1, y1):
    """板（両端を含むピクセルの範囲）と 1 ピクセルの縁。"""
    draw = ImageDraw.Draw(image)
    draw.rectangle([x0, y0, x1, y1], fill=PLATE, outline=PLATE_EDGE)


def item_triangle(image, cx, base, width, border):
    """板に描く警告の三角（記号なし）。cx は中心の列、base は底辺の下端、width は底辺の幅（ピクセル）。"""
    unit = raster.UNITS / ITEM_SIZE
    height = width * math.sqrt(3) / 2
    left, right = cx + 0.5 - width / 2, cx + 0.5 + width / 2
    bottom = base + 1
    points = [((cx + 0.5) * unit, (bottom - height) * unit), (right * unit, bottom * unit), (left * unit, bottom * unit)]
    raster.fill(image, raster.polygon(points), BLACK)
    raster.fill(image, raster.offset_polygon(points, -border * unit), YELLOW)


def item_wall_1x1():
    """壁掛け 1×1: 中央に 21×21 の板。"""
    image = parts.new_canvas(ITEM_SIZE)
    item_plate(image, 5, 5, 25, 25)
    item_triangle(image, 15, 22, 17, 1)
    return image


def item_wall_2x2():
    """壁掛け 2×2: 枠いっぱいの板に、4 枚分を示す十字の分割線と大きい三角。"""
    image = parts.new_canvas(ITEM_SIZE)
    item_plate(image, 1, 1, 29, 29)
    draw = ImageDraw.Draw(image)
    draw.line([(15, 1), (15, 29)], fill=PLATE_EDGE)
    draw.line([(1, 15), (29, 15)], fill=PLATE_EDGE)
    item_triangle(image, 15, 25, 25, 2)
    return image


def item_pole():
    """ポール付き: 上に 20×20 の板、その下に 4 ピクセル幅の支柱（ブロックの支柱と同じ明暗の列）。"""
    image = parts.new_canvas(ITEM_SIZE)
    draw = ImageDraw.Draw(image)
    draw.rectangle([13, 19, 16, 31], fill=POLE)
    draw.line([(13, 19), (13, 31)], fill=POLE_LIGHT)
    draw.line([(16, 19), (16, 31)], fill=POLE_DARK)
    item_plate(image, 5, 0, 24, 19)
    item_triangle(image, 14.5, 16, 15, 1)
    return image


ITEMS = {"wall_sign_1x1": (item_wall_1x1, "Wall sign 1x1"), "wall_sign_2x2": (item_wall_2x2, "Wall sign 2x2"),
         "pole_sign": (item_pole, "Pole sign")}


def logo(icon, factor):
    """アイテムのアイコンを最近傍で factor 倍にし、透明な所を LOGO_BG で塗る。"""
    large = icon.resize((icon.width * factor, icon.height * factor), Image.NEAREST)
    return Image.alpha_composite(Image.new("RGBA", large.size, LOGO_BG), large)


def write_items():
    """アイテムのアイコン 3 枚・一覧画像・CurseForge のアイコン・Mod 一覧のロゴを出す。"""
    entries = []
    (TEXTURES / "items").mkdir(exist_ok=True)
    for name, (make, title) in ITEMS.items():
        image = make()
        check_colors(f"items/{name}", image, ITEM_COLORS)
        image.save(TEXTURES / "items" / f"{name}.png")
        entries.append((name, title, image))
    write_sheet(ITEMS_PREVIEW, entries)
    icon = entries[0][2]
    logo(icon, 16).save(STORE_ICON)
    logo(icon, 4).save(MOD_LOGO)


# 「?」の形（設計の単位）: 黒い四角に、白い鉤と点
QUESTION = (raster.polyline([(23.0, 23.0), (24.0, 18.0), (27.6, 14.4), (32.0, 13.4), (36.4, 14.4), (40.0, 18.0),
                             (40.8, 22.6), (39.0, 27.0), (34.6, 30.4), (32.0, 33.6), (32.0, 39.0)], 3.0)
            | raster.circle(32.0, 48.0, 3.6))


def unknown(size):
    """一覧にない図柄 ID の「?」。"""
    image = Image.new("RGBA", (size, size), CLEAR)
    raster.fill(image, raster.rect(4, 4, 60, 60), BLACK)
    raster.fill(image, QUESTION, WHITE)
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


# 一覧画像の 1 マスの大きさ（ピクセル）。テクスチャは整数倍で拡大する（解像度 128 なら 2 倍）
PREVIEW_CELL = 256


def write_sheet(path, entries, cell_bg=None):
    """一覧画像: 拡大して暗い背景に並べ、下に見出しと説明を書く。entries は (見出し, 説明, 画像)。

    解像度の違う図柄も同じマスの大きさに整数倍で拡大する（128 なら 2 倍、64 なら 4 倍）。
    cell_bg を渡すと、透明な所をその色で塗る（部品の一覧で、黒い部品を見やすくする）。
    """
    cell = max([PREVIEW_CELL] + [entry[2].width for entry in entries])
    gap = 16
    label = 64
    columns = 5
    rows = (len(entries) + columns - 1) // columns
    sheet = Image.new("RGBA", (columns * (cell + gap) + gap, rows * (cell + label + gap) + gap), PREVIEW_BG)
    draw = ImageDraw.Draw(sheet)
    # Pillow 内蔵のフォント（環境によって一覧画像が変わらないように、システムのフォントは使わない）
    title_font = ImageFont.load_default(size=18)
    name_font = ImageFont.load_default(size=13)
    for i, (title, note, image) in enumerate(entries):
        x = gap + (i % columns) * (cell + gap)
        y = gap + (i // columns) * (cell + label + gap)
        background = Image.new("RGBA", image.size, cell_bg or PREVIEW_BG)
        composed = Image.alpha_composite(background, image)
        sheet.paste(composed.resize((cell, cell), Image.NEAREST), (x, y))
        draw.text((x, y + cell + 6), title, font=title_font, fill=PREVIEW_TEXT)
        for j, line in enumerate(wrap(draw, note, name_font, cell)[:2]):
            draw.text((x, y + cell + 28 + j * 16), line, font=name_font, fill=PREVIEW_NOTE)
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)


def write_parts(size):
    """部品の一覧画像: 部品を 1 つずつ白地に黒で塗って並べる（parts.catalog）。"""
    entries = []
    for i, (title, shape) in enumerate(parts.catalog()):
        image = parts.new_canvas(size)
        raster.fill(image, shape, BLACK)
        entries.append((f"{i + 1:02d}", title, image))
    write_sheet(PARTS_PREVIEW, entries, cell_bg=WHITE)


# 手で描いただけの図柄（SIGNS にない名前）を確かめる色。名前の先頭で分類を決める（長い接頭辞から順に見る）
CATEGORY_COLORS = {"ghs": {WHITE, HAZARD_RED, BLACK}, "iso21482": {HAZARD_RED, BLACK},
                   "w": {YELLOW, BLACK}, "p": {WHITE, RED, BLACK}, "m": {BLUE, WHITE}, "e": {GREEN, WHITE},
                   "f": {RED, WHITE}}
ALL_COLORS = {YELLOW, BLACK, RED, BLUE, GREEN, WHITE, HAZARD_RED}


def manual_colors(name):
    """手で描いただけの図柄の、使ってよい色（名前の接頭辞で決める。知らなければ全部の色）。"""
    for prefix in sorted(CATEGORY_COLORS, key=len, reverse=True):
        if name.startswith(prefix):
            return CATEGORY_COLORS[prefix]
    return ALL_COLORS


def parse_size():
    parser = argparse.ArgumentParser(description="看板のテクスチャと一覧画像を生成する")
    parser.add_argument("--size", type=int, default=DEFAULT_SIZE,
                        help=f"図柄・無地・「?」の解像度（16〜512 の 2 の累乗。既定 {DEFAULT_SIZE}）")
    size = parser.parse_args().size
    if not is_power_of_two(size) or not 16 <= size <= 512:
        parser.error(f"--size は 16〜512 の 2 の累乗にする（{size}）")
    return size


def main():
    size = parse_size()
    names = english_names()
    entries = []
    manual_only = sorted(path.stem for path in MANUAL.glob("*.png") if path.stem not in SIGNS)
    for name in list(SIGNS) + manual_only:
        sign = SIGNS.get(name)
        target = sign_size(sign, size)
        if (MANUAL / f"{name}.png").exists():
            allowed = sign["colors"] if sign else manual_colors(name)
            image = load_manual(name, allowed, target)
            source = "manual"
        else:
            image = parts.new_canvas(target)
            try:
                sign["draw"](image)
            except ValueError as error:
                raise SystemExit(f"{name}: {error}")
            check_colors(name, image, sign["colors"])
            source = "generated"
        image.save(TEXTURES / "signs" / f"{name}.png")
        code, english = names.get(name, (name.upper(), ""))
        if source == "manual":
            english = f"{english} (manual)".strip()
        entries.append((code, english, image))
        print(f"{name}: {source} ({image.width}x{image.height})")

    for name, image, title in [("blank", blank(size), "Blank"), ("unknown", unknown(size), "Unknown pattern ID")]:
        image.save(TEXTURES / "blocks" / f"sign_{name}.png")
        entries.append((name, title, image))
    # 支柱と裏面は図柄ではないので、一覧画像には入れない
    pole().save(TEXTURES / "blocks" / "sign_pole.png")
    back().save(TEXTURES / "blocks" / "sign_back.png")

    write_sheet(PREVIEW, entries)
    write_parts(size)
    write_items()
    print(f"wrote {len(entries)} textures ({size}x{size}), {PREVIEW.relative_to(ROOT)} and "
          f"{PARTS_PREVIEW.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
