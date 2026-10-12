"""図柄の部品（標識の形と記号）。

部品は図形（raster.Shape）を返すだけで、塗るのは generate.py。座標は設計の単位（キャンバス全体が 0〜64、
左上が (0, 0)、中心は (32, 32)）で、出力の解像度に関係なく同じ値で書く（raster.py）。
色は ISO 3864 の安全色の近似（仕様書 3.3）と透明だけを使う。

記号の部品は「中心 (cx, cy) と高さ size（設計の単位）」を受け取る。中では原点を中心にした部品ごとの
決まった高さで形を書き、place で拡大・移動する。図案はすべてこのファイルで描いたもの（既存の画像や SVG は使わない）。
"""
import math

from PIL import Image

from raster import (NOTHING, UNITS, annulus, arc_band, capsule, circle, dilate, ellipse, layered, polygon,
                    polygon_distance, polyline, rect, rounded_rect, sine_band, tapered, union, Shape)

CENTER = UNITS / 2

# ISO 3864 の安全色の近似（仕様書 3.3）
YELLOW = (0xF9, 0xA8, 0x00, 255)
BLACK = (0x1A, 0x1A, 0x1A, 255)
RED = (0x9B, 0x24, 0x23, 255)
BLUE = (0x00, 0x53, 0x87, 255)
GREEN = (0x23, 0x7F, 0x52, 255)
# GHS 絵表示の枠と ISO 21482 の三角形の赤（仕様書 SPEC_2026-10-12-7.md）
HAZARD_RED = (0xD7, 0x19, 0x1F, 255)
WHITE = (0xFF, 0xFF, 0xFF, 255)
CLEAR = (0, 0, 0, 0)


def new_canvas(size):
    return Image.new("RGBA", (size, size), CLEAR)


def place(shape, height, cx, cy, size, mirror=False):
    """原点を中心に高さ height で書いた部品を、中心 (cx, cy)・高さ size に置く。mirror は左右反転（右向き → 左向き）。"""
    if mirror:
        shape = shape.mirror(0)
    return shape.scale(size / height).shift(cx, cy)


# ---- 標識の形 ----
# 外形は 1 単位の透明な余白を残して 62 単位に収める（板の縁がテクスチャの端に貼りつかないように）。

# 警告の三角形: 角の丸めの前の三角形（芯）の頂点。丸め（半径 TRIANGLE_ROUND）で外形は幅 62・高さ約 55 になる
TRIANGLE_ROUND = 3
TRIANGLE_CORE = [(CENTER, 8.5), (60.0, 8.5 + 56 * math.sqrt(3) / 2), (4.0, 8.5 + 56 * math.sqrt(3) / 2)]
TRIANGLE_BORDER = 5
# 三角形の中の黄色の重心（記号はこの辺りに置く）と、記号に使える高さの目安
TRIANGLE_SYMBOL_Y = 41
TRIANGLE_SYMBOL_SIZE = 26


def warning_triangle():
    """警告の形: (黒い外形, 黄の中)。角の丸い正三角形で、黒枠の太さは TRIANGLE_BORDER。"""
    distance = polygon_distance(TRIANGLE_CORE)
    outer = Shape(lambda x, y: distance(x, y) <= TRIANGLE_ROUND)
    inner = Shape(lambda x, y: distance(x, y) <= TRIANGLE_ROUND - TRIANGLE_BORDER)
    return outer, inner


DISC_RADIUS = 31
RING_INNER = 25


def disc(radius=DISC_RADIUS):
    """丸い標識の円（禁止の白地・指示の青）。既定は幅 62。"""
    return circle(CENTER, CENTER, radius)


def ring(outer=DISC_RADIUS, inner=RING_INNER):
    """禁止の赤い輪（太さ 6）。"""
    return annulus(CENTER, CENTER, outer, inner)


def slash(half=2.75, radius=RING_INNER + 0.5):
    """禁止の斜線: 左上から右下へ（45°）。輪の内側だけ。"""
    def inside(x, y):
        return abs((x - CENTER) - (y - CENTER)) <= half * math.sqrt(2) and \
            (x - CENTER) ** 2 + (y - CENTER) ** 2 <= radius * radius
    return Shape(inside)


def square():
    """安全状態・防火の四角（角を少し丸めた 62 角）。"""
    return rounded_rect(1, 1, UNITS - 1, UNITS - 1, 4)


# GHS のひし形（正方形を 45° 回したもの）: 角の丸めの前のひし形（芯）の頂点。丸め（半径 DIAMOND_ROUND）で外形は 62 角になる
DIAMOND_ROUND = 2
DIAMOND_CORE = [(CENTER, 1.0 + DIAMOND_ROUND), (UNITS - 1.0 - DIAMOND_ROUND, CENTER),
                (CENTER, UNITS - 1.0 - DIAMOND_ROUND), (1.0 + DIAMOND_ROUND, CENTER)]
DIAMOND_BORDER = 4


def diamond():
    """GHS の形: (赤い外形, 白の中)。角を少し丸めたひし形で、赤枠の太さは DIAMOND_BORDER。"""
    distance = polygon_distance(DIAMOND_CORE)
    outer = Shape(lambda x, y: distance(x, y) <= DIAMOND_ROUND)
    inner = Shape(lambda x, y: distance(x, y) <= DIAMOND_ROUND - DIAMOND_BORDER)
    return outer, inner


# ---- 記号: 人物 ----
# 人物は関節の位置（骨組み）を決めて、頭は円、胴は太さの変わる線、手足は太線でつなぐ。
# 横向きの姿は右向き。手前の腕と脚の周りは細く抜いて、奥の手足・胴との境目が見えるようにする。
# 骨組みの単位は、立った姿の高さを 46 にしたもの。

PERSON_HEIGHT = 46
HEAD_R = 4.8
ARM_R = 2.6
LEG_R = 3.1
FOOT_R = 2.1


def _limb(points, r):
    return polyline(points, r)


def _figure(head, torso, far, near):
    """横向きの人物: 奥の手足 → 胴と頭 → 手前の手足の順に重ねる。

    奥の手足は胴の周りを抜いて離す。手前の腕は胴と重なる所で境目を付けると線が散らかるので、胴と同じ層にする。
    手前の脚（far / near の最後の 2 つ: 脚と足）は奥の脚との境目を抜く。
    """
    body = union(circle(*head, HEAD_R), torso, near[0])
    return layered(union(*far), body, union(*near[1:]), gap=1.0)


def person_standing():
    """立つ人（正面）。腕は体から少し離して下ろし、脚は少し開く。"""
    head = circle(0, -18.2, HEAD_R)
    torso = rounded_rect(-6.2, -11.6, 6.2, 5.5, 2.6)
    arms = union(*(capsule((side * 9.4, -9.2), (side * 10.8, 5.0), ARM_R) for side in (-1, 1)))
    legs = union(*(capsule((side * 3.6, 4.0), (side * 4.0, 20.4), 2.75) for side in (-1, 1)))
    return union(head, torso, arms, legs)


def person_walking():
    """歩く人（右向き）。"""
    shoulder, hip = (0.6, -9.6), (-0.4, 3.2)
    return _figure(
        head=(2.2, -18.2),
        torso=tapered(shoulder, hip, 4.4, 3.8),
        far=[_limb([shoulder, (-3.6, -2.6), (-6.4, 3.8)], ARM_R),
             _limb([hip, (-3.4, 11.6), (-7.6, 19.4)], LEG_R),
             capsule((-7.6, 19.4), (-5.4, 21.4), FOOT_R)],
        near=[_limb([shoulder, (4.4, -2.4), (8.4, 2.6)], ARM_R),
              _limb([hip, (3.8, 11.8), (5.0, 20.4)], LEG_R),
              capsule((5.0, 20.4), (8.6, 21.0), FOOT_R)],
    )


def person_running():
    """走る人（右向き）。体を前に倒し、腕を曲げて大きく振る。"""
    shoulder, hip = (3.4, -9.4), (-1.4, 2.8)
    return _figure(
        head=(6.2, -17.4),
        torso=tapered(shoulder, hip, 4.4, 3.8),
        far=[_limb([shoulder, (-2.6, -4.0), (-7.2, 0.4)], ARM_R),
             _limb([hip, (-6.0, 10.0), (-12.4, 12.4)], LEG_R),
             capsule((-12.4, 12.4), (-13.4, 15.6), FOOT_R)],
        near=[_limb([shoulder, (8.0, -2.8), (12.6, -7.0)], ARM_R),
              _limb([hip, (7.4, 7.8), (7.2, 18.6)], LEG_R),
              capsule((7.2, 18.6), (11.0, 19.4), FOOT_R)],
    )


def person_crouching():
    """かがむ人（右向き）。腰を落として膝を前に出し、背を丸めて手を膝の先へ伸ばす。足の裏は立つ人と同じ高さ。"""
    shoulder, hip = (3.6, -2.4), (-5.4, 8.0)
    return _figure(
        head=(7.4, -9.6),
        torso=tapered(shoulder, hip, 4.4, 4.0),
        far=[_limb([hip, (2.4, 12.4), (-2.6, 20.0)], LEG_R),
             capsule((-2.6, 20.0), (0.8, 21.0), FOOT_R)],
        near=[_limb([shoulder, (8.8, 3.6), (12.6, 8.4)], ARM_R),
              _limb([hip, (5.6, 9.6), (3.4, 19.6)], LEG_R),
              capsule((3.4, 19.6), (7.2, 21.0), FOOT_R)],
    )


POSES = {"standing": person_standing, "walking": person_walking, "running": person_running,
         "crouching": person_crouching}


def person(cx, cy, size, pose="standing", mirror=False):
    """人物。pose は standing / walking / running / crouching。cy は立った姿の高さの中心（姿が変わっても足元は揃う）。"""
    return place(POSES[pose](), PERSON_HEIGHT, cx, cy, size, mirror)


def coverall_person(cx, cy, size):
    """防護服（つなぎ）を着た人（正面）。胴と脚を一続きの服にし、腕は体から離す。
    襟元・袖口・ベルト・長靴の上端の境目を細く抜く。"""
    suit = union(rounded_rect(-7.4, -11.8, 7.4, 8.0, 2.8),
                 rounded_rect(-7.4, 4.0, -0.9, 21.0, 1.4),
                 rounded_rect(0.9, 4.0, 7.4, 21.0, 1.4))
    head = circle(0, -18.2, HEAD_R)
    arms = union(*(capsule((side * 10.4, -9.4), (side * 12.2, 5.6), 2.6) for side in (-1, 1)))
    # 袖口・ベルト・長靴の上端の境目（服の色を細く抜く）
    seams = union(rect(-14.0, 2.6, -8.0, 3.6), rect(8.0, 2.6, 14.0, 3.6),
                  rect(-7.4, 0.4, 7.4, 1.4), rect(-7.4, 16.0, 7.4, 17.0))
    collar = polygon([(-3.4, -11.8), (3.4, -11.8), (0.0, -7.4)])
    shape = union(head, suit - collar, arms) - seams
    return place(shape, PERSON_HEIGHT, cx, cy, size)


# ---- 記号: 体の一部 ----

HAND_HEIGHT = 40


def hand_shape():
    """開いた手のひら（正面、指は上向き、親指は左）。原点の中心で高さ 40。"""
    palm = rounded_rect(-9.4, -3.0, 9.4, 13.0, 4.2)
    wrist = rect(-7.0, 10.0, 7.0, 20.0)
    fingers = union(capsule((-7.2, -2.0), (-7.4, -12.4), 2.0),
                    capsule((-2.4, -2.0), (-2.4, -17.0), 2.0),
                    capsule((2.4, -2.0), (2.6, -15.4), 2.0),
                    capsule((7.2, -2.0), (7.6, -10.0), 1.9))
    thumb = union(polyline([(-7.0, 10.0), (-12.0, 4.2), (-14.0, -1.6)], 2.6), circle(-7.4, 7.0, 3.6))
    return layered(union(palm, wrist, fingers), thumb, gap=1.0)


def hand(cx, cy, size, mirror=False):
    """開いた手のひら（触るな・手の保護など）。mirror で親指が右になる。"""
    return place(hand_shape(), HAND_HEIGHT, cx, cy, size, mirror)


HEAD_HEIGHT = 40
# 頭部の基準の位置（原点が中心、高さ 40）。保護具（ヘルメット・ゴーグル・耳覆い・マスク）はこの位置に合わせて描く
HEAD_ANCHORS = {
    "crown": (0.0, -19.0),   # 頭のてっぺん（ヘルメットの上端の目安）
    "brow": (0.0, -9.0),     # 眉の高さ（ヘルメットのつば・ゴーグルの上端）
    "eyes": (0.0, -5.0),     # 目の高さ（ゴーグル・保護めがね）
    "ears": (9.6, -3.0),     # 右の耳の中心（左は x を反転。耳覆い）
    "mouth": (0.0, 4.0),     # 口の高さ（マスク）
    "chin": (0.0, 9.0),      # あごの下
}


def head_shape():
    """頭部と肩（正面の胸像）。顔の中は描かず、保護具を重ねる前提の無地の形。原点の中心で高さ 40。"""
    face = ellipse(0, -5.0, 9.0, 14.0)
    ears = union(ellipse(-9.6, -3.0, 1.8, 3.4), ellipse(9.6, -3.0, 1.8, 3.4))
    neck = rect(-4.4, 6.0, 4.4, 13.0)
    shoulders = union(rounded_rect(-17.0, 12.4, 17.0, 26.0, 6.0)) & rect(-17.0, 12.4, 17.0, 21.0)
    return union(layered(union(neck, shoulders), face, gap=1.2), ears)


def head(cx, cy, size):
    """頭部（保護具を付ける前提）。保護具の位置は head_anchor で求める。"""
    return place(head_shape(), HEAD_HEIGHT, cx, cy, size)


def head_anchor(name, cx, cy, size, side=1):
    """頭部の基準の位置（HEAD_ANCHORS）を、head(cx, cy, size) と同じ置き方で設計の座標に直す。side=-1 で左。"""
    x, y = HEAD_ANCHORS[name]
    factor = size / HEAD_HEIGHT
    return cx + side * x * factor, cy + y * factor


def wear(base, gear, gap=1.2):
    """base（頭部・人物）に保護具 gear を重ねる。gear の周りを gap だけ抜いて、境目を見せる。"""
    return layered(base, gear, gap=gap)


# ---- 記号: そのほか ----

def arrow(cx, cy, length, angle=0, width=6.0, head_length=None, head_width=None):
    """矢印（中心 (cx, cy)、全長 length）。angle は向き（度。右が 0 で時計回り）。"""
    head_length = head_length or length * 0.42
    head_width = head_width or width * 2.6
    half = length / 2
    shaft = rect(-half, -width / 2, half - head_length + 0.5, width / 2)
    tip = polygon([(half - head_length, -head_width / 2), (half, 0), (half - head_length, head_width / 2)])
    return (shaft | tip).rotate(angle, 0, 0).shift(cx, cy)


FLAME_HEIGHT = 34


def flame_shape():
    """炎（原点の中心で高さ 34）。丸い根元から左上へなびく先と、右の小さな舌、中の抜き。"""
    outer = union(circle(0.0, 8.0, 9.0),
                  polygon([(-9.0, 8.0), (-8.4, 0.0), (-5.6, -6.0), (-2.4, -11.4), (-1.6, -17.0),
                           (2.6, -12.0), (4.6, -6.0), (6.4, -2.0), (9.0, 8.0)]),
                  polygon([(4.6, -3.0), (8.6, -9.4), (10.4, -3.0), (9.0, 6.0)]))
    inner = union(circle(0.4, 11.4, 4.4),
                  polygon([(-4.0, 11.4), (-3.2, 6.0), (-1.0, 1.0), (0.2, -4.2), (2.6, 2.0), (4.4, 6.4), (4.8, 11.4)]))
    return outer - inner


def flame(cx, cy, size):
    """炎（可燃物・火気厳禁など）。"""
    return place(flame_shape(), FLAME_HEIGHT, cx, cy, size)


LIGHTNING_HEIGHT = 40


def lightning_shape():
    """稲妻（原点の中心で高さ 40）。右上から左下へ 2 回折れ、先は左下を向いた矢じり。"""
    bolt = polygon([(2.0, -20.0), (9.6, -20.0), (3.4, -5.6), (9.4, -5.6), (-2.0, 9.2),
                    (-5.0, 7.0), (2.0, -1.6), (-4.4, -1.6)])
    tip = polygon([(-8.8, 3.8), (2.0, 11.6), (-9.6, 20.0)])
    return bolt | tip


def lightning(cx, cy, size, mirror=False):
    """稲妻（感電注意・高電圧など）。"""
    return place(lightning_shape(), LIGHTNING_HEIGHT, cx, cy, size, mirror)


def droplet(cx, cy, size):
    """しずく（液体・水など）。下が丸く、上がとがる。size は高さ。"""
    r = size * 0.32
    bottom = cy + size / 2
    ccy = bottom - r
    tip_y = cy - size / 2
    # とがった先から円に引いた接線の接点
    d = ccy - tip_y
    angle = math.asin(r / d)
    tx = r * math.cos(angle)
    ty = ccy - r * math.sin(angle)
    return circle(cx, ccy, r) | polygon([(cx, tip_y), (cx + tx, ty), (cx - tx, ty)])


def gear(cx, cy, size, teeth=8, hole=0.36):
    """歯車（機械・回転する部品など）。size は歯先の直径。hole は穴の直径の割合（0 で穴なし）。"""
    outer = size / 2
    root = outer * 0.76

    def inside(x, y):
        dx, dy = x - cx, y - cy
        r = math.hypot(dx, dy)
        if r <= root:
            return True
        if r > outer:
            return False
        # 歯は、歯の中心からの角度だけでなく弦の長さで決める（歯の側面が平行に近くなる）
        pitch = 2 * math.pi / teeth
        a = (math.atan2(dy, dx) + pitch / 2) % pitch - pitch / 2
        return abs(r * math.sin(a)) <= outer * 0.22
    body = Shape(inside)
    return body - circle(cx, cy, outer * hole) if hole else body


def waves(cx, cy, width, count=3, spacing=6.0, amplitude=1.6, wavelength=10.0, r=1.4):
    """横に流れる波を count 本、縦に並べる（水・液体など）。"""
    top = cy - spacing * (count - 1) / 2
    return union(*(sine_band(cx - width / 2, cx + width / 2, top + i * spacing, amplitude, wavelength, r)
                   for i in range(count)))


def rising_waves(cx, bottom, height, count=3, spacing=7.0, amplitude=1.8, wavelength=11.0, r=1.5):
    """縦に立ちのぼる波を count 本、横に並べる（熱・蒸気など）。"""
    left = cx - spacing * (count - 1) / 2
    return union(*(sine_band(bottom, bottom - height, left + i * spacing, amplitude, wavelength, r, vertical=True)
                   for i in range(count)))


# ---- 記号: 警告の図柄に使うもの ----

def exclamation(cx, cy, size):
    """感嘆符（一般警告）。縦棒は下へ向かって細くなり、上は丸い。"""
    s = size / 26
    bar = tapered((cx, cy - 10.4 * s), (cx, cy + 4.6 * s), 3.2 * s, 2.2 * s)
    dot = circle(cx, cy + 10.4 * s, 2.6 * s)
    return bar | dot


def trefoil(cx, cy, size):
    """放射線マーク: 中心の点と 3 枚の羽根（下・左上・右上、各 60°）。size は羽根の外の直径。"""
    outer = size / 2
    dot = circle(cx, cy, outer * 0.2)
    return dot | arc_band(cx, cy, outer, outer * 0.32, 150, 210) | arc_band(cx, cy, outer, outer * 0.32, 270, 330) \
        | arc_band(cx, cy, outer, outer * 0.32, 30, 90)


def explosion(cx, cy, size):
    """爆発: 弾けた玉（ぎざぎざの塊）と、周りへ飛ぶ破片。size は全体の高さ。"""
    s = size / 28
    spikes = []
    for i in range(16):
        angle = math.radians(i * 22.5 + 11.25)
        radius = (9.0 if i % 2 == 0 else 5.6) * s
        spikes.append((cx + radius * math.sin(angle), cy - radius * math.cos(angle)))
    burst = polygon(spikes)
    fragments = []
    for angle, distance, length in [(45, 10.2, 3.4), (100, 10.0, 3.2), (180, 10.4, 3.2),
                                    (260, 10.0, 3.2), (315, 10.2, 3.4)]:
        a = math.radians(angle)
        dx, dy = math.sin(a), -math.cos(a)
        start = (cx + dx * distance * s, cy + dy * distance * s)
        end = (cx + dx * (distance + length) * s, cy + dy * (distance + length) * s)
        fragments.append(tapered(start, end, 1.3 * s, 2.1 * s))
    return burst | union(*fragments)


def hot_surface(cx, cy, size):
    """高温の面: 平らな面と、そこから立ちのぼる 3 本の波線。size は全体の高さ。"""
    s = size / 26
    surface = rect(cx - 11 * s, cy + 8.6 * s, cx + 11 * s, cy + 12.6 * s)
    return surface | rising_waves(cx, cy + 6.0 * s, 16.0 * s, spacing=7.6 * s, amplitude=1.9 * s,
                                  wavelength=12.0 * s, r=1.5 * s)


# ---- 記号: GHS 絵表示・ISO 21482 に使うもの（仕様書 SPEC_2026-10-12-7.md） ----
# どれも原点を中心にした高さ 40 で書き、place で置く。

GHS_HEIGHT = 40


def skull_shape():
    """ドクロと、その下で交差する 2 本の骨（原点の中心で高さ 40）。骨は奥、ドクロは手前。"""
    dome = circle(0.0, -7.0, 10.0)
    jaw = rounded_rect(-6.4, -2.0, 6.4, 8.6, 2.6)
    eyes = union(ellipse(-4.0, -6.4, 3.0, 3.4), ellipse(4.0, -6.4, 3.0, 3.4))
    nose = polygon([(0.0, -1.4), (1.8, 2.2), (-1.8, 2.2)])
    teeth = union(rect(-4.6, 5.0, 4.6, 5.9), rect(-2.6, 5.0, -1.7, 8.6), rect(1.7, 5.0, 2.6, 8.6))
    skull = union(dome, jaw) - eyes - nose - teeth

    def bone(p0, p1):
        # 端の膨らみ（関節）は、骨の向きと直角に 2 つ並べた円
        (x0, y0), (x1, y1) = p0, p1
        length = math.hypot(x1 - x0, y1 - y0)
        nx, ny = -(y1 - y0) / length * 1.9, (x1 - x0) / length * 1.9
        knobs = union(*(circle(x + side * nx, y + side * ny, 2.4) for x, y in (p0, p1) for side in (-1, 1)))
        return capsule(p0, p1, 1.9) | knobs
    bones = union(bone((-13.4, 6.4), (13.4, 16.4)), bone((13.4, 6.4), (-13.4, 16.4)))
    return layered(bones, skull, gap=1.2)


def skull_crossbones(cx, cy, size):
    """ドクロと交差した骨（急性毒性・ISO 21482）。size は骨の下の端までの高さ。"""
    return place(skull_shape(), GHS_HEIGHT, cx, cy, size)


def exploding_bomb_shape():
    """爆発する爆弾（原点の中心で高さ 40）。上がぎざぎざに割れた玉と、上と左右へ飛ぶ光の線・破片。"""
    bomb_c = (0.0, 9.0)
    # 割れ口: 玉の上をぎざぎざの線で切る
    teeth = [(-12.0, bomb_c[1] + 30.0), (-12.0, bomb_c[1] - 2.0)]
    for i in range(9):
        teeth.append((-10.8 + i * 2.7, bomb_c[1] - (7.4 if i % 2 else 3.4)))
    teeth += [(12.0, bomb_c[1] - 2.0), (12.0, bomb_c[1] + 30.0)]
    bomb = circle(*bomb_c, 10.4) & polygon(teeth)
    rays = []
    for angle in (-80, -52, -26, 0, 26, 52, 80):
        a = math.radians(angle)
        dx, dy = math.sin(a), -math.cos(a)
        rays.append(tapered((bomb_c[0] + dx * 13.0, bomb_c[1] + dy * 13.0),
                            (bomb_c[0] + dx * 20.0, bomb_c[1] + dy * 20.0), 1.3, 2.3))
    # 光の線のあいだを飛ぶ破片（小さな四角を少し回したもの）
    pieces = []
    for angle in (-66, -39, -13, 13, 39, 66):
        a = math.radians(angle)
        px, py = bomb_c[0] + math.sin(a) * 15.0, bomb_c[1] - math.cos(a) * 15.0
        pieces.append(rect(-1.4, -1.4, 1.4, 1.4).rotate(angle + 20, 0, 0).shift(px, py))
    return bomb | union(*rays) | union(*pieces)


def exploding_bomb(cx, cy, size):
    """爆発する爆弾（GHS01）。"""
    return place(exploding_bomb_shape(), GHS_HEIGHT, cx, cy, size)


def flame_on_bar_shape():
    """横棒の上の炎（原点の中心で高さ 40）。"""
    return place(flame_shape(), FLAME_HEIGHT, 0.0, -3.0, 33.0) | rect(-12.0, 16.4, 12.0, 20.0)


def flame_on_bar(cx, cy, size):
    """横棒の上の炎（GHS02）。"""
    return place(flame_on_bar_shape(), GHS_HEIGHT, cx, cy, size)


def flame_over_circle_shape():
    """円の上の炎と横棒（原点の中心で高さ 40）。炎の根元は円に載る。"""
    ring_shape = annulus(0.0, 7.4, 9.0, 4.4)
    fire = place(flame_shape(), FLAME_HEIGHT, 0.0, -8.8, 22.4)
    return layered(fire, ring_shape, gap=1.0) | rect(-12.0, 16.4, 12.0, 20.0)


def flame_over_circle(cx, cy, size):
    """円の上の炎（GHS03）。"""
    return place(flame_over_circle_shape(), GHS_HEIGHT, cx, cy, size)


def gas_cylinder_shape():
    """ガスボンベ（原点の中心で高さ 40 の枠に、少し傾けて寝かせる）。左が丸い底、右が肩・首・バルブ。"""
    body = rounded_rect(-17.0, -6.4, 10.0, 6.4, 6.4) | rounded_rect(-8.0, -6.4, 9.0, 6.4, 2.4)
    neck = rect(9.0, -2.6, 12.6, 2.6)
    valve = rounded_rect(12.0, -4.6, 15.6, 4.6, 1.0)
    handle = rect(14.6, -1.2, 18.0, 1.2)
    cylinder = body | neck | valve | handle
    return cylinder.rotate(-24, 0, 0).shift(0.0, 1.0)


def gas_cylinder(cx, cy, size):
    """ガスボンベ（GHS04）。"""
    return place(gas_cylinder_shape(), GHS_HEIGHT, cx, cy, size)


def corrosion_shape():
    """腐食性物質（原点の中心で高さ 40）。上の 2 本の試験管からしずくが落ち、左の金属板と右の手を溶かす。"""
    tubes = union(capsule((-13.0, -15.0), (-6.0, -10.6), 2.9), capsule((13.0, -15.0), (6.0, -10.6), 2.9))
    drops = union(droplet(-5.6, -2.6, 8.4), droplet(5.6, -2.6, 8.4))
    # 金属板（左）と手（右、指は右向き、親指は上）。しずくの落ちる所が欠けて溶けている
    plate = rect(-15.0, 6.0, -1.4, 12.0) - circle(-5.6, 5.8, 3.0)
    palm = rounded_rect(1.4, 6.0, 10.6, 12.8, 2.0)
    fingers = union(*(capsule((9.0, y), (16.6, y), 1.0) for y in (7.2, 9.4, 11.6)))
    thumb = capsule((8.4, 6.0), (12.4, 2.8), 1.2)
    hand_shape_ = (palm | fingers | thumb) - circle(5.6, 5.8, 3.0)
    ground = rect(-14.0, 15.2, 14.0, 18.0)
    return tubes | drops | plate | hand_shape_ | ground


def corrosion(cx, cy, size):
    """腐食性物質（GHS05）。"""
    return place(corrosion_shape(), GHS_HEIGHT, cx, cy, size)


def health_hazard_shape():
    """健康有害性（原点の中心で高さ 40）。人の胸像の胸に、白く抜いた星形。"""
    head_ = circle(0.0, -12.0, 6.6)
    torso = rounded_rect(-13.0, -3.0, 13.0, 30.0, 10.0) & rect(-13.0, -3.0, 13.0, 18.0)
    points = []
    for i in range(16):
        a = math.radians(i * 22.5)
        r = 9.8 if i % 2 == 0 else 4.6
        points.append((r * math.sin(a), 8.0 - r * math.cos(a)))
    star = polygon(points)
    return layered(torso, head_, gap=1.2) - star


def health_hazard(cx, cy, size):
    """健康有害性（GHS08）。"""
    return place(health_hazard_shape(), GHS_HEIGHT, cx, cy, size)


def environment_shape():
    """水生環境有害性（原点の中心で高さ 40）。左に葉の落ちた木、右に腹を上にした魚、下に地面。"""
    trunk = tapered((-8.0, 15.0), (-8.6, -10.0), 2.8, 1.5)
    branches = union(polyline([(-8.4, -2.0), (-13.0, -7.4), (-15.4, -14.0)], 1.4),
                     polyline([(-8.4, -6.0), (-4.2, -11.6), (-3.0, -17.0)], 1.4),
                     polyline([(-8.2, 3.0), (-2.4, -1.6), (0.4, -6.2)], 1.4),
                     polyline([(-8.4, 6.6), (-14.0, 2.6), (-16.4, -1.0)], 1.4),
                     capsule((-13.0, -7.4), (-16.6, -6.6), 1.2))
    tree = trunk | branches
    body = ellipse(6.0, 10.4, 7.0, 4.2)
    tail = polygon([(11.4, 10.4), (16.0, 6.6), (16.0, 14.2)])
    fin = polygon([(3.4, 7.0), (8.4, 7.0), (6.0, 3.6)])
    eye = circle(1.8, 9.6, 1.3)
    fish = (body | tail | fin) - eye
    ground = rect(-15.0, 15.2, 15.0, 18.0)
    return tree | fish | ground


def environment(cx, cy, size):
    """水生環境有害性（GHS09）。"""
    return place(environment_shape(), GHS_HEIGHT, cx, cy, size)


def radiation_waves(cx, cy, angles=(-32, 0, 32), start=7.0, end=14.0, amplitude=0.9, wavelength=5.8, r=0.75):
    """放射線の波: 中心 (cx, cy) から外へ向かう波線を、angles（度。下が 0 で時計回り）の向きに 1 本ずつ描く。
    波線は中心から start〜end の距離に置く。"""
    wave = sine_band(start, end, 0.0, amplitude, wavelength, r, vertical=True)
    return union(*(wave.rotate(angle, 0, 0).shift(cx, cy) for angle in angles))


# 部品の一覧画像（doc/textures/parts.png）に並べる部品: (見出し, 図形)。設計の座標の中心に高さ 44 前後で置く
def catalog():
    c = CENTER
    return [
        ("person: standing", person(c, c, 48)),
        ("person: walking", person(c, c, 48, "walking")),
        ("person: running", person(c, c, 48, "running")),
        ("person: crouching", person(c, c, 48, "crouching")),
        ("coverall person", coverall_person(c, c + 2, 48)),
        ("hand", hand(c, c, 46)),
        ("head (for gear)", head(c, c, 44)),
        ("head + helmet (example)", wear(head(c, c, 44),
                                        (ellipse(c, head_anchor("brow", c, c, 44)[1] - 1, 11.6, 11.4)
                                         & rect(0, 0, UNITS, head_anchor("brow", c, c, 44)[1]))
                                        | rect(c - 14, head_anchor("brow", c, c, 44)[1] - 2.6,
                                               c + 14, head_anchor("brow", c, c, 44)[1]))),
        ("arrow", arrow(c, c, 46)),
        ("arrow (up)", arrow(c, c, 46, angle=-90)),
        ("flame", flame(c, c, 44)),
        ("lightning", lightning(c, c, 44)),
        ("droplet", droplet(c, c, 40)),
        ("gear", gear(c, c, 44)),
        ("waves", waves(c, c, 46)),
        ("rising waves", rising_waves(c, c + 16, 32)),
        ("exclamation", exclamation(c, c, 40)),
        ("trefoil", trefoil(c, c, 44)),
        ("explosion", explosion(c, c, 44)),
        ("hot surface", hot_surface(c, c, 40)),
        ("skull and crossbones", skull_crossbones(c, c, 44)),
        ("exploding bomb", exploding_bomb(c, c, 44)),
        ("flame on bar", flame_on_bar(c, c, 44)),
        ("flame over circle", flame_over_circle(c, c, 44)),
        ("gas cylinder", gas_cylinder(c, c, 44)),
        ("corrosion", corrosion(c, c, 44)),
        ("health hazard", health_hazard(c, c, 44)),
        ("environment", environment(c, c, 44)),
        ("radiation waves", trefoil(c, c - 10, 16) | radiation_waves(c, c - 10, start=11, end=25, amplitude=1.6, wavelength=11.0, r=1.4)),
    ]
