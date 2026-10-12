"""図柄の部品（標識の形と記号）。

どの部品も 32×32 の画像にドットを打つだけで、アンチエイリアスはしない（形はピクセルの中心で内外を決め、
記号は「#」で書いたドット絵）。色は ISO 3864 の安全色の近似（仕様書 3.3）と透明だけを使う。

座標は左上が (0, 0)。形の中心は (15, 15) の列・行（31 ピクセル幅の形を左右対称に描くため）。
記号の部品は「中心の列 cx と上端の行 top」か「中心 (cx, cy)」を受け取り、色を引数で受ける
（警告は黒、指示は白、禁止は黒の記号を同じ部品で描けるように）。
"""
import math

from PIL import Image

SIZE = 32
CENTER = 15

# ISO 3864 の安全色の近似（仕様書 3.3）
YELLOW = (0xF9, 0xA8, 0x00, 255)
BLACK = (0x1A, 0x1A, 0x1A, 255)
RED = (0x9B, 0x24, 0x23, 255)
BLUE = (0x00, 0x53, 0x87, 255)
GREEN = (0x23, 0x7F, 0x52, 255)
WHITE = (0xFF, 0xFF, 0xFF, 255)
CLEAR = (0, 0, 0, 0)


def new_canvas():
    return Image.new("RGBA", (SIZE, SIZE), CLEAR)


def fill_where(image, inside, color):
    """inside(x, y) が真のピクセルを塗る（x, y はピクセルの番号）。"""
    for y in range(SIZE):
        for x in range(SIZE):
            if inside(x, y):
                image.putpixel((x, y), color)


def dots(image, rows, left, top, color):
    """ドット絵の部品。rows は文字列の行で、「#」の所を塗る（それ以外は触らない）。"""
    for row, line in enumerate(rows):
        for col, dot in enumerate(line):
            if dot == "#":
                image.putpixel((left + col, top + row), color)


def dots_centered(image, rows, cx, top, color):
    """dots を、行の幅の中心が列 cx に来るように置く（幅は奇数にしておく）。"""
    width = max(len(line) for line in rows)
    dots(image, rows, cx - width // 2, top, color)


# ---- 標識の形 ----

TRIANGLE_TOP = 3
TRIANGLE_ROWS = 27  # 31 ピクセル幅の正三角形の高さ（31 × √3 / 2 ≒ 26.8）


def triangle_half_width(row):
    """正三角形の row 行目（頂点が 0）の、中心の列から左右に広がるピクセル数。"""
    return round(row * CENTER / (TRIANGLE_ROWS - 1))


def warning_triangle(image, fill=YELLOW, border=BLACK, side=2, bottom=2):
    """警告の形: 黄の正三角形に黒枠。31 ピクセル幅で、頂点は 1 ピクセル。

    枠の太さは、斜めの辺で横に side ピクセル、底辺で bottom 行（斜めの辺と見た目の太さが揃う値）。
    """
    for row in range(TRIANGLE_ROWS):
        y = TRIANGLE_TOP + row
        half = triangle_half_width(row)
        for x in range(CENTER - half, CENTER + half + 1):
            inner = half - side
            in_fill = abs(x - CENTER) <= inner and row < TRIANGLE_ROWS - bottom
            image.putpixel((x, y), fill if in_fill else border)


def in_circle(dx, dy, radius):
    """中心からのずれ (dx, dy) のピクセルが、半径 radius（整数）の円の内側か。

    d² ≤ r² + r は、ドット絵の円が上下左右で 1 ピクセルだけ飛び出さない、丸く見える境目。
    """
    return dx * dx + dy * dy <= radius * radius + radius


def disc(image, color, radius=15, cx=CENTER, cy=CENTER):
    """円。既定は 31 ピクセル幅。"""
    fill_where(image, lambda x, y: in_circle(x - cx, y - cy, radius), color)


def ring(image, color, outer=15, inner=12, cx=CENTER, cy=CENTER):
    """輪（禁止の赤い円）。太さは outer - inner ピクセル。"""
    fill_where(image, lambda x, y: in_circle(x - cx, y - cy, outer) and not in_circle(x - cx, y - cy, inner), color)


def slash(image, color, half=1, radius=12, cx=CENTER, cy=CENTER):
    """禁止の斜線: 左上から右下へ。輪の内側だけに描く（1 行あたり 2 × half + 1 ピクセル）。"""
    fill_where(image, lambda x, y: abs((x - cx) - (y - cy)) <= half and in_circle(x - cx, y - cy, radius), color)


# ---- 記号 ----


def exclamation(image, cx, top, color):
    """感嘆符（一般警告）。縦棒は下へ向かって細くなる。"""
    dots_centered(image, [
        "###",
        "###",
        "###",
        "###",
        "###",
        "###",
        "###",
        "###",
        ".#.",
        ".#.",
        "...",
        "###",
        "###",
    ], cx, top, color)


def trefoil(image, cx, cy, color, dot=1.5, blade_in=2.5, blade_out=6.6):
    """放射線マーク: 中心の点と 3 枚の羽根（下・左上・右上、各 60°）。"""
    def inside(x, y):
        dx, dy = x - cx, y - cy
        r = math.hypot(dx, dy)
        if r <= dot:
            return True
        if not blade_in <= r <= blade_out:
            return False
        # 下向きを 0° にした角度。羽根は 0°・120°・240° を中心に ±30°
        angle = math.degrees(math.atan2(dx, dy)) % 120
        return angle < 30 or angle > 90
    fill_where(image, inside, color)


def explosion(image, cx, top, color):
    """爆発: 弾けた玉と、周りに飛ぶ破片。"""
    dots_centered(image, [
        "......#......",
        "..#.......#..",
        ".....###.....",
        "....#####....",
        "#..#######..#",
        "...#######...",
        "#..#######..#",
        "....#####....",
        ".....###.....",
        "..#.......#..",
        "......#......",
    ], cx, top, color)


def lightning(image, cx, top, color):
    """稲妻（感電注意）: 右上から左下へ折れ、先が左下を向いた矢じりになる。"""
    dots_centered(image, [
        ".....###.",
        "....###..",
        "...###...",
        "..###....",
        ".########",
        ".....###.",
        "....###..",
        "#..###...",
        "#.###....",
        "####.....",
        "###......",
        "####.....",
        "#####....",
    ], cx, top, color)


def heat(image, cx, top, color):
    """高温の面: 平らな面と、そこから立ちのぼる 3 本の波線。"""
    dots_centered(image, [
        ".##..##..##..",
        "##..##..##...",
        "##..##..##...",
        ".##..##..##..",
        "..##..##..##.",
        "..##..##..##.",
        ".##..##..##..",
        ".............",
        "#############",
        "#############",
    ], cx, top, color)


def flame(image, cx, top, color):
    """炎（可燃物など。MVP の 8 種では使わない）。"""
    dots_centered(image, [
        "....#....",
        "....##...",
        "...###...",
        "...####..",
        "..#####..",
        "..##.###.",
        ".###.###.",
        ".##...##.",
        "###...###",
        "##.....##",
        ".##...##.",
        "..#####..",
    ], cx, top, color)


def hand(image, cx, top, color):
    """開いた手のひら（触るな）。指は上向き、親指は左。"""
    dots_centered(image, [
        ".....#.......",
        "...#.##.#....",
        "...##.#.##...",
        "...##.#.##.#.",
        "...##.#.##.##",
        "...##.#.##.##",
        "...########..",
        "#..########.#",
        "##.#########.",
        ".###########.",
        "..##########.",
        "..#########..",
        "...#######...",
        "...#######...",
    ], cx, top, color)


def walking_person(image, cx, top, color):
    """歩く人（通行禁止）。右へ歩く向き。"""
    dots_centered(image, [
        "......###....",
        ".....#####...",
        ".....#####...",
        "......###....",
        ".....####....",
        "....######...",
        "...#######...",
        "..##.######..",
        ".##..####.##.",
        "##...####..##",
        ".....####....",
        ".....#####...",
        "....###.##...",
        "...###...##..",
        "..###.....##.",
        ".###......##.",
        "###.......###",
    ], cx, top, color)


def coverall_person(image, cx, top, color):
    """防護服（つなぎ）を着た人（防護服着用）。腕を少し開いて立つ正面の姿。"""
    dots_centered(image, [
        "....###....",
        "...#####...",
        "...#####...",
        "....###....",
        "...........",
        "..###.###..",
        ".####.####.",
        ".####.####.",
        ".##.#.#.##.",
        ".##.#.#.##.",
        ".##.###.##.",
        "##..###..##",
        "....###....",
        "...##.##...",
        "...##.##...",
        "...##.##...",
        "...##.##...",
        "..###.###..",
    ], cx, top, color)
