"""図柄の部品（標識の形と記号）。

部品は図形（raster.Shape）を返すだけで、塗るのは generate.py。座標は設計の単位（キャンバス全体が 0〜64、
左上が (0, 0)、中心は (32, 32)）で、出力の解像度に関係なく同じ値で書く（raster.py）。
色は ISO 3864 の安全色の近似（仕様書 3.3）と透明だけを使う。

記号の部品は「中心 (cx, cy) と高さ size（設計の単位）」を受け取る。中では原点を中心にした部品ごとの
決まった高さで形を書き、place で拡大・移動する。図案はすべてこのファイルで描いたもの（既存の画像や SVG は使わない）。
"""
import math

from PIL import Image

from raster import (NOTHING, UNITS, annulus, arc_band, capsule, circle, dilate, ellipse, layered, outline, polygon,
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
    """爆発する爆弾（原点の中心で高さ 40）。上がぎざぎざに割れた大きな玉と、上と左右へ飛ぶ太い破片 4 つ。"""
    bomb_c = (0.0, 7.0)
    bomb_r = 12.0
    # 割れ口: 玉の上をぎざぎざの線で切る
    teeth = [(-14.0, bomb_c[1] + 30.0), (-14.0, bomb_c[1] - 4.0)]
    for i in range(7):
        teeth.append((-10.2 + i * 3.4, bomb_c[1] - (8.6 if i % 2 else 4.2)))
    teeth += [(14.0, bomb_c[1] - 4.0), (14.0, bomb_c[1] + 30.0)]
    bomb = circle(*bomb_c, bomb_r) & polygon(teeth)
    # 破片: 玉から外へ向かって太くなる楔
    fragments = []
    for angle in (-62, -21, 21, 62):
        a = math.radians(angle)
        dx, dy = math.sin(a), -math.cos(a)
        fragments.append(tapered((bomb_c[0] + dx * 14.6, bomb_c[1] + dy * 14.6),
                                 (bomb_c[0] + dx * 21.0, bomb_c[1] + dy * 21.0), 1.4, 2.8))
    return bomb | union(*fragments)


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
    """ガスボンベ（原点の中心で高さ 40 の枠に、横倒しで置く）。

    胴は両端が丸い太い円筒（上側に白い光沢の筋）。右端の上に、短い首・口金・小さなハンドルを縦に積む。
    魚雷に見えないように、端から横へ突き出す部品（羽に見える十字の形）は使わない。傾けると縁がぎざぎざになるので水平に置く。
    """
    body = rounded_rect(-17.0, -1.0, 17.0, 13.0, 7.0)
    shine = rounded_rect(-11.0, 1.8, 4.0, 3.2, 0.7)
    # 肩の手前の白い帯（ボンベの首輪）
    collar = rect(7.4, -1.0, 8.6, 13.0)
    body = body - collar
    neck = rect(11.4, -4.4, 14.6, 0.5)
    cap = rounded_rect(10.2, -7.6, 15.8, -4.0, 1.0)
    # ハンドルは口金の上の小さな輪（バルブの回し手）
    handle = annulus(13.0, -10.2, 2.6, 1.2)
    return (body - shine) | neck | cap | handle


def gas_cylinder(cx, cy, size):
    """ガスボンベ（GHS04）。"""
    return place(gas_cylinder_shape(), GHS_HEIGHT, cx, cy, size)


# 白抜きの輪郭線の太さ（設計の単位。128×128 で 1.5 ピクセル前後）
CORROSION_LINE = 0.6


def test_tube(bottom, mouth, line=None):
    """試験管: 底 bottom は丸く、口 mouth は平らに切って縁を少し広げる。line を渡すと輪郭線だけ（太さ line）、
    渡さなければ塗りつぶし。"""
    (x0, y0), (x1, y1) = bottom, mouth
    dx, dy = x1 - x0, y1 - y0
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    body = capsule(bottom, mouth, 1.9) - Shape(lambda x, y: (x - x1) * ux + (y - y1) * uy > 0)
    lip = capsule((x1 - uy * 2.3, y1 + ux * 2.3), (x1 + uy * 2.3, y1 - ux * 2.3), (line or 1.2) / 2)
    return (outline(body, line) if line else body) | lip


def corroded_hand(line=CORROSION_LINE):
    """液がかかって欠けた手（GHS05 の右下と同じ位置・大きさ。x 3.6〜18.6、y 1〜6.9）。

    手のひらを下にした手で、指は左へ向く。甲・指・親指をそれぞれ輪郭線で描いて重ね（指の境目が見える）、
    液のかかった甲の上（x 13 の辺り）が欠けている。
    """
    bite = circle(13.0, 1.6, 2.2) | circle(15.2, 2.0, 1.0)
    back = rounded_rect(8.4, 1.6, 18.6, 6.6, 2.0) - bite
    fingers = [capsule((9.4, 2.5), (4.4, 2.3), 0.9), capsule((9.4, 4.0), (3.6, 4.0), 0.9),
               capsule((9.4, 5.4), (4.2, 5.6), 0.85), capsule((9.4, 6.4), (5.6, 6.9), 0.75)]
    thumb = capsule((10.6, 2.2), (6.6, 1.0), 0.8)
    hand_ = outline(back, line)
    for part in fingers + [thumb]:
        hand_ = hand_ | (outline(part, line) - back)
    return hand_


def corrosion_shape():
    """腐食性物質（原点の中心で高さ 40。128×128 で描く前提の細かさ）。

    すべてをひし形の中央の横長の帯に収める（上下に積まない）。
    左半分: 左下へ少し傾いた試験管から液がこぼれ、下の横長の平たい板（黒）の上面が、液のかかった所で欠けている。
    右半分: 右下へ少し傾いた試験管から液がこぼれ、下の手のひらを下にした手が、液のかかった所で欠けている。
    2 本の試験管はほぼ水平で、丸い底が中央で少し離れて向かい合う。試験管と手は白抜きの輪郭線、板としずくは塗りつぶし。
    液のかかった所から、波線の煙を 3 本ずつ立てる。
    """
    line = CORROSION_LINE

    def tube(bottom, mouth):
        return test_tube(bottom, mouth, line)

    def smoke(x, bottom, top):
        # 縦に立ちのぼる細い波線
        return sine_band(bottom, top, x, 0.55, 2.6, 0.42, vertical=True)

    def side(sign):
        """sign=-1 で左半分、+1 で右半分の、試験管・しずく・煙（板と手は別）。"""
        bottom, mouth = (sign * 3.0, -7.8), (sign * 14.4, -5.6)
        drop_x = sign * 13.0
        drops = droplet(drop_x, -1.8, 3.4)
        smokes = union(*(smoke(drop_x + dx, 0.6, -3.2) for dx in (-2.6, 2.6, sign * 4.8)))
        return tube(bottom, mouth) | drops | smokes

    # 左: 横長の平たい板（塗りつぶし）。液のかかった上面が欠けている
    bite_left = circle(-13.0, 2.2, 2.2) | circle(-10.8, 2.5, 1.0) | circle(-15.2, 2.6, 1.0)
    plate = rect(-18.0, 2.4, -3.6, 5.6) - bite_left
    return side(-1) | side(1) | plate | corroded_hand(line)


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



# ---- 記号: ISO 7010 の警告・禁止（優先度 A の前半） ----
# どれも原点を中心にした部品ごとの決まった高さで書き、place で置く。

LASER_HEIGHT = 26


def laser_shape():
    """レーザー光線（原点の中心で高さ 26）。左の光の点（中心の円と、長短交互の 12 本の光線）から、右へ長い光線が伸びる。"""
    bx = -13.0
    rays = []
    for i in range(12):
        a = math.radians(i * 30 + 15)
        length = 13.0 if i % 2 == 0 else 9.4
        dx, dy = math.sin(a), -math.cos(a)
        rays.append(tapered((bx + dx * 5.2, dy * 5.2), (bx + dx * length, dy * length), 1.5, 1.0))
    beam = rect(bx, -1.8, 25.0, 1.8)
    return circle(bx, 0.0, 4.2) | union(*rays) | beam


def laser(cx, cy, size):
    """レーザー光線（W004）。size は光の点の高さ。光線は右へ size の 1.5 倍ほど伸びる。"""
    return place(laser_shape(), LASER_HEIGHT, cx, cy, size)


SYMBOL_HEIGHT = 40


def antenna_shape():
    """非電離放射線（原点の中心で高さ 40）。アンテナの塔と、先端の点から左右へ広がる電波の弧 2 本ずつ。"""
    tip = (0.0, -6.0)
    dot = circle(*tip, 2.6)
    waves = union(*(arc_band(*tip, outer, outer - 2.0, start, end)
                    for outer in (7.6, 13.0) for start, end in ((52, 128), (232, 308))))
    mast = tapered(tip, (0.0, 17.0), 1.0, 1.8)
    legs = union(capsule((0.0, 8.0), (-7.0, 18.0), 1.4), capsule((0.0, 8.0), (7.0, 18.0), 1.4),
                 capsule((-3.6, 13.0), (3.6, 13.0), 1.0))
    ground = rect(-10.0, 17.6, 10.0, 20.0)
    return dot | waves | mast | legs | ground


def antenna(cx, cy, size):
    """非電離放射線（W005）。"""
    return place(antenna_shape(), SYMBOL_HEIGHT, cx, cy, size)


def magnet_shape():
    """磁界（原点の中心で高さ 40。128×128 で描く前提の細かさ）。

    上に、開いた側を下に向けた U 字の磁石（極の先を白い線で区切る）。下に、両極の間を下へふくらんでつなぐ磁力線を
    入れ子で 3 本。外の線ほど極の外寄りから出て、大きくふくらむ。磁力線は極から少し離して、磁石と見分けられるようにする。
    """
    top = -12.0
    leg_bottom = -1.0
    body = (arc_band(0.0, top, 11.4, 5.4, 270, 90)
            | rect(-11.4, top, -5.4, leg_bottom) | rect(5.4, top, 11.4, leg_bottom))
    poles = rect(-12.0, -6.0, 12.0, -5.0)
    start = leg_bottom + 2.0
    lines = union(*(_half_ellipse_line(0.0, start, rx, ry, 0.85)
                    for rx, ry in ((6.6, 4.4), (9.6, 10.0), (13.4, 16.0))))
    return (body - poles) | (lines - rect(-20.0, -20.0, 20.0, start))


def _half_ellipse_line(cx, cy, rx, ry, r):
    """下半分の楕円の線（中心 (cx, cy)、横の半径 rx・縦の半径 ry、太さ 2r）。"""
    points = []
    for i in range(33):
        a = math.pi * i / 32
        points.append((cx + rx * math.cos(a), cy + ry * math.sin(a)))
    return polyline(points, r)


def magnet(cx, cy, size):
    """磁界（W006）。"""
    return place(magnet_shape(), SYMBOL_HEIGHT, cx, cy, size)


BIOHAZARD_HEIGHT = 46


def biohazard_shape():
    """生物災害（原点の中心で高さ 46）。上・左下・右下の 3 つの三日月と、中心の穴、三日月の口を横切る輪。

    三日月は大きな円（半径 15）から、外へずらした小さな円（半径 10.5）を抜いた形。輪は三日月の口の中にだけ見せ、
    三日月との間を細く抜く。
    """
    crescents = []
    mouths = []
    for angle in (0, 120, 240):
        a = math.radians(angle)
        dx, dy = math.sin(a), -math.cos(a)
        mouth = circle(dx * 15.0, dy * 15.0, 10.5)
        crescents.append(circle(dx * 11.0, dy * 11.0, 15.0) - mouth)
        mouths.append(mouth)
    body = union(*crescents) - circle(0.0, 0.0, 3.2)
    ring_part = annulus(0.0, 0.0, 17.4, 14.8) & union(*mouths)
    # 上下の真ん中が原点に来るようにずらす（上の端は -26、下の端は約 20.5）
    return (body | (ring_part - dilate(body, 1.1))).shift(0.0, 2.75)


def biohazard(cx, cy, size):
    """生物災害（W009）。"""
    return place(biohazard_shape(), BIOHAZARD_HEIGHT, cx, cy, size)


def snowflake(cx, cy, size):
    """雪の結晶（低温）。6 本の腕と、腕ごとに 2 本ずつの枝。size は直径。"""
    s = size / 40
    arms = []
    for i in range(6):
        a = math.radians(i * 60)
        dx, dy = math.sin(a), -math.cos(a)
        arms.append(capsule((0.0, 0.0), (dx * 19.0, dy * 19.0), 1.7))
        for side in (-1, 1):
            b = a + side * math.radians(48)
            base = (dx * 10.0, dy * 10.0)
            arms.append(capsule(base, (base[0] + math.sin(b) * 6.6, base[1] - math.cos(b) * 6.6), 1.5))
    return union(*arms).scale(s).shift(cx, cy)


def automatic_start_shape():
    """自動起動（原点の中心で高さ 40）。歯車と、その周りを時計回りに回る矢印（上で切れた輪の先に矢じり）。"""
    ring_shape = arc_band(0.0, 0.0, 19.0, 15.4, 30, 320)
    # 矢じりは輪の終わり（左上）で、時計回りの向き（上へ）を指す: 輪の外から内まで広い三角形
    end, tip_angle = math.radians(320), math.radians(346)
    tip = (math.sin(tip_angle) * 17.2, -math.cos(tip_angle) * 17.2)
    head = polygon([tip, (math.sin(end) * 23.0, -math.cos(end) * 23.0), (math.sin(end) * 11.4, -math.cos(end) * 11.4)])
    return ring_shape | head | gear(0.0, 0.0, 22.0, teeth=8, hole=0.34)


def automatic_start(cx, cy, size):
    """自動起動（W018）。"""
    return place(automatic_start_shape(), SYMBOL_HEIGHT, cx, cy, size)


def crushing_shape():
    """挟まれ（原点の中心で高さ 40。高さ 40 で置くと設計の単位のまま）。上から下りる押し板と下向きの矢印、
    その下でかがむ人、床。押し板は人の背のすぐ上に置く。"""
    plate = rect(-6.6, -9.4, 6.6, -6.4)
    push = arrow(0.0, -14.2, 8.0, angle=90, width=2.4, head_length=3.8, head_width=7.6)
    floor = rect(-16.0, 15.6, 16.0, 17.6)
    worker = person(-2.0, 1.8, 26.0, "crouching")
    return plate | push | floor | worker


def crushing(cx, cy, size):
    """挟まれ（W019）。"""
    return place(crushing_shape(), SYMBOL_HEIGHT, cx, cy, size)


def corrosive_triangle_shape():
    """腐食性物質の警告（原点の中心で高さ 40。高さ 40 で置くと設計の単位のまま。128×128 で描く前提の細かさ）。

    三角形に合わせて上下に積む: 上の中央に、底を寄せて「ハ」の字に傾けた試験管 2 本（塗りつぶし）、
    口から落ちるしずく、下の左に上面の欠けた板、下の右に甲の欠けた手（GHS05 と同じ手）。
    """
    tubes = test_tube((-2.6, -12.6), (-8.0, -4.0)) | test_tube((2.6, -12.6), (8.0, -4.0))
    drops = droplet(-10.0, 0.6, 4.0) | droplet(10.0, 0.6, 4.0)
    bite = circle(-10.0, 4.6, 2.4) | circle(-7.6, 5.0, 1.1) | circle(-12.4, 5.0, 1.1)
    plate = rect(-17.0, 4.8, -3.6, 9.4) - bite
    hand_ = corroded_hand().shift(-3.0, 3.6)
    return tubes | drops | plate | hand_


def corrosive_triangle(cx, cy, size):
    """腐食性物質の警告（W023）。"""
    return place(corrosive_triangle_shape(), SYMBOL_HEIGHT, cx, cy, size)


def battery_shape():
    """電池の充電（原点の中心で高さ 40）。横長の電池（上に端子 2 つ）の胴に、白く抜いた稲妻。"""
    body = rounded_rect(-17.0, -10.0, 17.0, 14.0, 2.4)
    terminals = rect(-13.0, -14.0, -6.6, -9.0) | rect(6.6, -14.0, 13.0, -9.0)
    return (body - lightning(0.0, 2.0, 20.0)) | terminals


def battery(cx, cy, size):
    """電池の充電（W026）。"""
    return place(battery_shape(), SYMBOL_HEIGHT, cx, cy, size)


def upright_cylinder_shape():
    """高圧ガス容器（原点の中心で高さ 40）。立てたボンベ: 丸い肩の胴（左に白い光沢の筋、肩の下に白い帯）、首・弁・ハンドル。"""
    body = rounded_rect(-7.4, -10.0, 7.4, 20.0, 5.0) | rect(-7.4, 0.0, 7.4, 20.0)
    shine = rect(-4.8, -2.4, -3.4, 16.4)
    collar = rect(-7.4, -4.6, 7.4, -3.4)
    neck = rect(-2.4, -14.6, 2.4, -9.0)
    valve = rounded_rect(-3.6, -17.4, 3.6, -13.6, 0.8)
    handle = rect(-6.4, -20.0, 6.4, -18.4) | rect(-0.9, -19.0, 0.9, -17.0)
    return (body - shine - collar) | neck | valve | handle


def upright_cylinder(cx, cy, size):
    """高圧ガス容器（W029）。GHS04 と違い、立てて描く。"""
    return place(upright_cylinder_shape(), SYMBOL_HEIGHT, cx, cy, size)


def arc_flash_shape():
    """アークフラッシュ（原点の中心で高さ 40）。左右から向き合う 2 本の電極、その間を走るぎざぎざの放電、上へ広がる閃光。"""
    electrodes = (rect(-20.0, 7.0, -9.0, 12.4) | polygon([(-9.0, 7.0), (-5.6, 9.7), (-9.0, 12.4)])
                  | rect(9.0, 7.0, 20.0, 12.4) | polygon([(9.0, 7.0), (5.6, 9.7), (9.0, 12.4)]))
    spark = polyline([(-5.6, 9.7), (-2.6, 5.4), (-0.6, 12.8), (1.8, 5.2), (3.4, 11.4), (5.6, 9.7)], 1.0)
    rays = []
    for angle in (-50, -25, 0, 25, 50):
        a = math.radians(angle)
        dx, dy = math.sin(a), -math.cos(a)
        rays.append(tapered((dx * 7.0, 6.0 + dy * 7.0), (dx * 18.0, 6.0 + dy * 18.0), 0.9, 2.2))
    return electrodes | spark | union(*rays)


def arc_flash(cx, cy, size):
    """アークフラッシュ（W042）。"""
    return place(arc_flash_shape(), SYMBOL_HEIGHT, cx, cy, size)


def hot_content_shape():
    """高温の内容物（原点の中心で高さ 40）。両側に取っ手の付いた鍋（上の縁は空け、中に黒い内容物）と、そこから立ちのぼる 3 本の波線。"""
    outer = polygon([(-13.0, 0.0), (13.0, 0.0), (11.0, 20.0), (-11.0, 20.0)])
    inner = polygon([(-10.4, -1.0), (10.4, -1.0), (8.6, 17.4), (-8.6, 17.4)])
    pot = outer - inner
    content = inner & rect(-20.0, 5.0, 20.0, 20.0)
    handles = rect(-17.0, 2.4, -12.0, 5.0) | rect(12.0, 2.4, 17.0, 5.0)
    steam = rising_waves(0.0, -2.6, 17.0, spacing=7.0, amplitude=1.8, wavelength=10.0, r=1.5)
    return pot | content | handles | steam


def hot_content(cx, cy, size):
    """高温の内容物（W079）。"""
    return place(hot_content_shape(), SYMBOL_HEIGHT, cx, cy, size)


def hot_steam_shape():
    """高温の蒸気（原点の中心で高さ 40。高さ 40 で置くと設計の単位のまま）。左から来る配管とフランジ、
    その口から右へ噴き出して広がる蒸気の雲（輪郭線）。"""
    pipe = rect(-16.0, 8.6, -5.0, 13.6) | rect(-6.6, 6.2, -4.0, 16.0)
    cloud = union(circle(0.2, 11.0, 3.4), circle(5.6, 8.0, 4.4), circle(6.4, 14.0, 3.2), circle(11.2, 11.4, 3.8),
                  circle(10.4, 5.8, 2.8))
    return pipe | outline(cloud, 1.5)


def hot_steam(cx, cy, size):
    """高温の蒸気（W080）。"""
    return place(hot_steam_shape(), SYMBOL_HEIGHT, cx, cy, size)


def cigarette_shape():
    """火のついたたばこ（原点の中心で高さ 40。高さ 40 で置くと設計の単位のまま）。横に寝かせたたばこ
    （左にフィルターの境目、右の先に灰）と、先から立ちのぼる煙 2 本。"""
    body = rect(-19.0, 6.0, 10.6, 11.6)
    filter_line = rect(-11.0, 6.0, -9.8, 11.6)
    ash = rect(11.8, 6.0, 14.6, 11.6)
    smoke = union(sine_band(3.6, -16.0, 13.0, 1.5, 10.0, 1.2, vertical=True),
                  sine_band(3.6, -11.0, 7.6, 1.5, 10.0, 1.2, vertical=True))
    return (body - filter_line) | ash | smoke


def cigarette(cx, cy, size):
    """火のついたたばこ（P002）。"""
    return place(cigarette_shape(), SYMBOL_HEIGHT, cx, cy, size)


def burning_match_shape():
    """火のついたマッチ（原点の中心で高さ 40。高さ 40 で置くと設計の単位のまま）。立てたマッチの軸と頭、その上の炎。"""
    stick = rect(-1.5, 1.0, 1.5, 20.0)
    match_head = ellipse(0.0, 0.4, 2.6, 3.4)
    fire = place(flame_shape(), FLAME_HEIGHT, 0.0, -10.4, 19.0)
    return layered(stick | match_head, fire, gap=1.0)


def burning_match(cx, cy, size):
    """火のついたマッチ（P003）。"""
    return place(burning_match_shape(), SYMBOL_HEIGHT, cx, cy, size)


def bucket_on_fire_shape():
    """水で消火するな（原点の中心で高さ 40。高さ 40 で置くと設計の単位のまま）。右上で傾けたバケツ（底の側に
    細い取っ手）から、左下の炎へ水のしずくが 2 つ落ちる。"""
    body = polygon([(-6.4, -5.6), (6.4, -5.6), (4.6, 6.4), (-4.6, 6.4)])
    handle = arc_band(0.0, -1.0, 9.2, 8.0, 90, 270) - rect(-10.0, -10.0, 10.0, -1.0)
    bucket = (body | handle).rotate(-125, 0.0, 0.0).shift(9.6, -9.6)
    drops = droplet(5.0, -1.4, 8.0) | droplet(-5.2, 1.8, 7.0)
    fire = place(flame_shape(), FLAME_HEIGHT, -10.0, 12.6, 16.0)
    return bucket | drops | fire


def bucket_on_fire(cx, cy, size):
    """水で消火するな（P011）。"""
    return place(bucket_on_fire_shape(), SYMBOL_HEIGHT, cx, cy, size)


def mobile_phone_shape():
    """携帯電話（原点の中心で高さ 40。高さ 40 で置くと設計の単位のまま）。縦長の電話機（白い画面とボタン）と、
    右上へ広がる電波の弧 2 本。"""
    body = rounded_rect(-10.0, -14.0, 3.0, 16.0, 2.2)
    screen = rect(-7.6, -11.0, 0.6, -2.6)
    keys = union(*(rect(x, y, x + 1.8, y + 1.5) for x in (-7.6, -4.4, -1.2) for y in (0.4, 3.8, 7.2, 10.6)))
    waves = union(*(arc_band(4.0, -9.0, outer, outer - 1.8, 25, 95) for outer in (6.6, 11.0)))
    return (body - screen - keys) | waves


def mobile_phone(cx, cy, size):
    """携帯電話（P013）。"""
    return place(mobile_phone_shape(), SYMBOL_HEIGHT, cx, cy, size)

# ---- 記号: ISO 7010 の指示・安全状態・防火（優先度 A の後半。仕様書 SPEC_2026-10-12-12.md） ----
# 保護具の図柄（M003・M004・M013・M014・M016・M017）は、同じ頭部（head_shape）に保護具を重ねる。
# 保護具は HEAD_ANCHORS の位置に合わせて、原点を中心にした高さ 40 の頭部の座標で書く（HEAD_GEAR）。

def _head_with(gear, gap=1.2):
    """頭部（原点の中心で高さ 40）に保護具 gear を重ね、周りを gap だけ抜いて境目を見せる。"""
    return wear(head_shape(), gear, gap=gap)


def helmet_gear():
    """保護帽（ヘルメット）: 頭を覆う半球と、左右へ張り出したつば。中央に縦の筋（白抜き）。"""
    dome = ellipse(0.0, -9.6, 12.0, 12.8) & rect(-13.0, -24.0, 13.0, -9.6)
    brim = rounded_rect(-15.6, -11.4, 15.6, -7.6, 1.6)
    ridge = rect(-1.0, -20.0, 1.0, -11.4)
    return (dome | brim) - ridge


def ear_muffs_gear():
    """耳の保護具（イヤーマフ）: 頭の上を通るバンドと、両耳を覆う大きな楕円のカップ。"""
    band = arc_band(0.0, -6.0, 15.6, 12.8, 270, 90) & rect(-16.0, -24.0, 16.0, -4.0)
    cups = union(*(rounded_rect(side * 13.6 - 4.0, -10.4, side * 13.6 + 4.0, 4.4, 3.6) for side in (-1, 1)))
    return band | cups


def goggles_gear():
    """目の保護具（ゴーグル）: 目の高さで顔の幅より広い枠と、地の色で抜いた 2 つのレンズ、耳へ回るバンド。"""
    frame = rounded_rect(-12.4, -11.4, 12.4, 0.6, 4.0)
    lenses = union(*(rounded_rect(side * 6.0 - 4.6, -9.0, side * 6.0 + 4.6, -1.8, 2.4) for side in (-1, 1)))
    strap = rect(-15.6, -8.0, 15.6, -3.6)
    return (frame | strap) - lenses


def face_shield_gear():
    """顔面保護具（防災面）: 額のバンドと、顔の前を覆う透明な面（太い輪郭線で描き、中の顔が見える）。"""
    band = rounded_rect(-12.6, -18.0, 12.6, -12.0, 2.0)
    visor = rounded_rect(-13.6, -12.0, 13.6, 12.4, 5.0)
    return band | outline(visor, 2.2)


def dust_mask_gear():
    """マスク（防じんマスク）: 鼻と口を覆う、下へ少しとがった形と、耳へ伸びるひも。中に横の折り目（白抜き）。"""
    cup = polygon([(-8.8, -3.4), (-3.0, -6.6), (3.0, -6.6), (8.8, -3.4), (8.0, 4.6), (0.0, 9.4), (-8.0, 4.6)])
    straps = union(capsule((-8.4, -2.0), (-11.0, -3.0), 1.0), capsule((8.4, -2.0), (11.0, -3.0), 1.0))
    folds = union(rect(-6.4, -1.8, 6.4, -1.0), rect(-6.0, 1.8, 6.0, 2.6))
    return (cup - folds) | straps


def respirator_gear():
    """呼吸用保護具（防毒マスク）: 鼻と口を覆う面体と、両の頬の前に付けた大きな丸い吸収缶、中央の排気弁。"""
    piece = rounded_rect(-7.4, -6.0, 7.4, 8.6, 4.4)
    valve = circle(0.0, 4.0, 2.6) - circle(0.0, 4.0, 1.2)
    canisters = union(*(circle(side * 9.6, 3.0, 5.0) - annulus(side * 9.6, 3.0, 3.4, 2.2) for side in (-1, 1)))
    straps = union(capsule((-6.4, -4.0), (-12.0, -6.8), 1.0), capsule((6.4, -4.0), (12.0, -6.8), 1.0))
    return layered(piece - valve | straps, canisters, gap=1.0)


HEAD_GEAR = {"helmet": helmet_gear, "ear_muffs": ear_muffs_gear, "goggles": goggles_gear,
             "face_shield": face_shield_gear, "dust_mask": dust_mask_gear, "respirator": respirator_gear}


def protected_head(cx, cy, size, gear):
    """保護具を付けた頭部（M003・M004・M013・M014・M016・M017）。gear は HEAD_GEAR の名前。
    頭部の位置と大きさは、図柄どうしで揃える（generate.py の HEAD_GEAR_PLACE）。"""
    return place(_head_with(HEAD_GEAR[gear]()), HEAD_HEIGHT, cx, cy, size)


def open_book_shape():
    """取扱説明書（原点の中心で高さ 40）: 手前に開いた本（左右のページに文字の行）と、上に情報の「i」。"""
    pages = union(polygon([(-1.0, -2.0), (-8.0, -4.6), (-16.0, -3.4), (-16.0, 15.0), (-8.0, 13.8), (-1.0, 16.4)]),
                  polygon([(1.0, -2.0), (8.0, -4.6), (16.0, -3.4), (16.0, 15.0), (8.0, 13.8), (1.0, 16.4)]))
    cover = polygon([(-18.4, -1.0), (-18.4, 17.6), (-8.0, 16.4), (0.0, 19.4), (8.0, 16.4), (18.4, 17.6),
                     (18.4, -1.0), (16.0, -1.0), (16.0, 15.0), (8.0, 13.8), (1.0, 16.4), (-1.0, 16.4),
                     (-8.0, 13.8), (-16.0, 15.0), (-16.0, -1.0)])
    lines = union(*(rect(x0, y, x0 + 9.4, y + 1.2) for x0 in (-13.4, 3.6) for y in (-0.6, 3.0, 6.6, 10.2)))
    # 文字の行は、ページの傾きに合わせず水平に置く（細かい斜めの線はつぶれる）
    info = circle(0.0, -16.4, 2.4) | rounded_rect(-2.1, -12.2, 2.1, -5.8, 0.8)
    return (pages - lines) | cover | info


def open_book(cx, cy, size):
    """取扱説明書を読め（M002）。"""
    return place(open_book_shape(), SYMBOL_HEIGHT, cx, cy, size)


def safety_boot_shape():
    """安全靴（原点の中心で高さ 40）: 右向きの編み上げの半長靴。つま先の先しん（白抜きの線で区切る）と、厚い靴底。"""
    upper = polygon([(-9.0, -18.0), (1.4, -18.0), (2.4, -4.0), (8.0, 0.4), (15.0, 3.2), (17.0, 8.0),
                     (17.0, 12.6), (-11.0, 12.6), (-11.0, -6.0)])
    sole = rounded_rect(-12.0, 13.6, 18.0, 18.6, 1.6)
    heel_gap = rect(-3.0, 16.6, 5.0, 19.0)
    toe_cap = arc_band(14.6, 12.6, 9.0, 7.8, 290, 360)
    collar = rect(-10.0, -15.4, 2.0, -14.4)
    laces = union(*(rect(-1.4 + i * 0.3, -12.0 + i * 3.2, 2.6 + i * 1.0, -11.0 + i * 3.2) for i in range(4)))
    return (upper - toe_cap - collar - laces) | (sole - heel_gap)


def safety_boot(cx, cy, size):
    """安全靴を着用（M008）。"""
    return place(safety_boot_shape(), SYMBOL_HEIGHT, cx, cy, size)


def glove_shape():
    """保護手袋（原点の中心で高さ 40）: 手のひらの部品（hand_shape）を手袋にし、手首に広がる長い袖口を付ける。"""
    glove = hand_shape().scale(0.86, 0.0, 0.0).shift(0.0, -3.6)
    cuff = polygon([(-8.4, 11.6), (8.4, 11.6), (11.4, 20.0), (-11.4, 20.0)])
    return layered(glove - rect(-20.0, 11.0, 20.0, 30.0), cuff, gap=1.2)


def glove(cx, cy, size):
    """保護手袋を着用（M009）。"""
    return place(glove_shape(), SYMBOL_HEIGHT, cx, cy, size)


# 安全状態・防火の記号は、四角（62 角）の中で原点を中心にした高さ 40 で書く

def exit_door_shape():
    """非常口（原点の中心で高さ 40。右向き = E002）: 右の扉の枠へ駆け込む人と、下の右向きの矢印。

    扉は太い白の枠で、開口部は地の色のまま。人は扉の左で、手前の足が開口部へ入る。左向き（E001）は左右反転する。
    """
    frame = rect(7.0, -20.0, 20.0, 9.0) - rect(9.8, -17.2, 17.2, 9.0)
    runner = person_running().scale(31.0 / PERSON_HEIGHT, 0.0, 0.0).shift(-4.0, -6.4)
    arrow_shape = arrow(0.0, 16.0, 30.0, width=4.4, head_length=9.0, head_width=11.0)
    return layered(frame, runner, gap=1.2) | arrow_shape


def exit_door(cx, cy, size, mirror=False):
    """非常口。mirror=False が右（E002）、True が左（E001）。人・扉・矢印をまとめて左右反転する。"""
    return place(exit_door_shape(), SYMBOL_HEIGHT, cx, cy, size, mirror)


def first_aid_cross(cx, cy, size):
    """救急用具（E003）: 太い十字。size は十字の幅。"""
    half, arm = size / 2, size * 0.17
    return rect(cx - half, cy - arm, cx + half, cy + arm) | rect(cx - arm, cy - half, cx + arm, cy + half)


def eyewash_shape():
    """洗眼器（原点の中心で高さ 40）: 前へかがんだ横向き（右向き）の頭と、前の台のノズルから弧を描いて目へ届く水。

    正面の頭部の部品だと、水の線が顔を横切って目が分からなくなるので、この図柄だけ横向きの頭を描く。
    """
    tilt, offset = 20, (-4.0, -4.0)
    skull = ellipse(-3.0, -6.0, 9.6, 10.4)
    face = polygon([(1.0, -14.0), (6.4, -9.0), (7.6, -4.6), (10.4, -1.2), (7.8, 0.0), (8.2, 3.0),
                    (6.4, 5.6), (1.0, 6.4)])
    neck = polygon([(-9.0, -1.0), (0.0, 2.0), (-1.0, 16.0), (-12.0, 16.0)])
    eye_x, eye_y = 4.0, -6.0
    profile = (union(skull, face, neck) - ellipse(eye_x, eye_y, 2.2, 1.4)).rotate(tilt, 0.0, 0.0).shift(*offset)
    # 目の位置（頭と同じく回して動かす）
    c, s = math.cos(math.radians(tilt)), math.sin(math.radians(tilt))
    ex, ey = eye_x * c - eye_y * s + offset[0], eye_x * s + eye_y * c + offset[1]
    # ノズル (13, 10) から上へ噴き上がり、目の前へ右上から下りる線（2 次のベジェ曲線。顔に食い込むと目が潰れる）
    start, control, end = (13.0, 10.0), (15.0, -16.0), (ex + 4.6, ey - 0.4)
    points = [((1 - t) ** 2 * start[0] + 2 * (1 - t) * t * control[0] + t * t * end[0],
               (1 - t) ** 2 * start[1] + 2 * (1 - t) * t * control[1] + t * t * end[1])
              for t in (i / 24 for i in range(25))]
    stream = polyline(points, 0.9)
    nozzle = rect(11.0, 9.6, 15.0, 15.0)
    basin = (ellipse(4.0, 15.0, 15.0, 4.0) & rect(-12.0, 15.0, 20.0, 20.0)) | rect(-11.0, 14.0, 19.0, 15.0)
    return layered(profile, stream, gap=1.0) | nozzle | basin


def eyewash(cx, cy, size):
    """洗眼器（E011）。"""
    return place(eyewash_shape(), SYMBOL_HEIGHT, cx, cy, size)


def safety_shower_shape():
    """緊急シャワー（原点の中心で高さ 40）: 上の配管から下がるシャワーの傘と、降りそそぐ水、その下に立つ人（正面）。"""
    pipe = rect(-19.0, -19.0, 1.2, -16.4) | rect(-1.2, -19.0, 1.2, -13.0)
    head = polygon([(-1.6, -13.4), (1.6, -13.4), (7.4, -9.4), (-7.4, -9.4)])
    drops = union(*(capsule((x, y), (x, y + 2.0), 0.7)
                    for x, y0 in ((-6.8, -7.4), (-2.3, -6.4), (2.3, -6.4), (6.8, -7.4))
                    for y in (y0, y0 + 4.2)))
    figure = person_standing().scale(28.0 / PERSON_HEIGHT, 0.0, 0.0).shift(0.0, 6.0)
    return layered(pipe | head | drops, figure, gap=1.0)


def safety_shower(cx, cy, size):
    """緊急シャワー（E012）。"""
    return place(safety_shower_shape(), SYMBOL_HEIGHT, cx, cy, size)


def stop_button_shape():
    """非常停止ボタン（原点の中心で高さ 40）: 横から見たきのこ形の押しボタン（大きな丸い頭・首・台座）と、
    上から押す手のひら。"""
    cap = (ellipse(0.0, 4.0, 15.0, 8.0) & rect(-16.0, -5.0, 16.0, 4.0)) | rounded_rect(-15.0, 3.2, 15.0, 6.0, 1.0)
    neck = rect(-5.4, 6.0, 5.4, 10.4)
    base = rounded_rect(-13.0, 10.4, 13.0, 19.4, 1.6)
    press = place(hand_shape(), HAND_HEIGHT, 0.0, -14.0, 22.0).rotate(180, 0.0, -14.0)
    return layered(cap | neck | base, press, gap=1.2) - rect(-14.0, 13.2, 14.0, 14.2)


def stop_button(cx, cy, size):
    """非常停止ボタン（E020）。"""
    return place(stop_button_shape(), SYMBOL_HEIGHT, cx, cy, size)


def extinguisher_shape():
    """消火器（原点の中心で高さ 40）: 左寄りに立てた本体（丸い肩）、上のレバーと取っ手、右へ垂れるホースとノズル、
    右上に小さな炎。"""
    body = rounded_rect(-11.0, -8.0, 1.0, 19.0, 4.6)
    valve = rect(-7.4, -12.0, -2.6, -7.0)
    lever = polygon([(-10.6, -13.6), (-1.4, -16.8), (-0.4, -14.6), (-9.6, -11.8)])
    grip = polygon([(-8.8, -12.0), (-1.6, -12.0), (-1.0, -10.2), (-8.8, -10.2)])
    hose = polyline([(-2.0, -10.0), (4.6, -10.2), (7.4, -4.0), (7.4, 8.0)], 1.3)
    nozzle = polygon([(5.6, 7.0), (9.2, 7.0), (10.2, 13.6), (4.6, 13.6)])
    label = rect(-9.0, 0.0, -1.0, 1.2)
    fire_ = place(flame_shape(), FLAME_HEIGHT, 13.0, -10.0, 13.0)
    return layered((body - label) | valve | lever | grip, hose | nozzle, gap=1.0) | fire_


def extinguisher(cx, cy, size):
    """消火器（F001）。"""
    return place(extinguisher_shape(), SYMBOL_HEIGHT, cx, cy, size)


def pointing_hand_shape():
    """指さす手（原点の中心で高さ 40。指先は上）: 握った指と、上へ伸ばした人さし指、手首。"""
    fist = rounded_rect(-7.4, -3.0, 7.0, 10.0, 3.2)
    index = capsule((-3.6, -2.0), (-3.6, -17.6), 2.3)
    knuckles = union(*(rect(-7.4, y, 2.6, y + 0.9) for y in (0.8, 4.2)))
    thumb = capsule((5.4, 5.0), (0.4, 1.0), 2.4)
    wrist = rect(-5.8, 9.0, 5.4, 20.0)
    return layered(union(fist - knuckles, index, wrist), thumb, gap=0.9)


def call_point_shape():
    """火災報知機（原点の中心で高さ 40）: 右寄りの発信機の箱（中に押しボタンの円）と、左下から押す指、左上に小さな炎、
    箱から出る音の弧。"""
    box = rounded_rect(-1.0, -8.0, 18.0, 11.0, 2.0) - rounded_rect(2.0, -5.0, 15.0, 8.0, 1.0)
    button = circle(8.5, 1.5, 4.0)
    finger = pointing_hand_shape().rotate(45, 0.0, 0.0).scale(0.7, 0.0, 0.0).shift(-2.0, 12.0)
    fire_ = place(flame_shape(), FLAME_HEIGHT, -12.0, -11.0, 14.0)
    sound = union(*(arc_band(8.5, -8.0, r, r - 1.6, 325, 35) for r in (6.0, 10.0)))
    return layered(box | button | sound, finger, gap=1.2) | fire_


def call_point(cx, cy, size):
    """火災報知機（F005）。"""
    return place(call_point_shape(), SYMBOL_HEIGHT, cx, cy, size)


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
        ("laser", laser(c - 4, c, 26)),
        ("antenna", antenna(c, c, 44)),
        ("magnet", magnet(c, c, 44)),
        ("biohazard", biohazard(c, c, 44)),
        ("snowflake", snowflake(c, c, 44)),
        ("automatic start-up", automatic_start(c, c, 44)),
        ("crushing", crushing(c, c, 44)),
        ("corrosive (triangle)", corrosive_triangle(c, c, 44)),
        ("battery", battery(c, c, 44)),
        ("upright cylinder", upright_cylinder(c, c, 44)),
        ("arc flash", arc_flash(c, c, 44)),
        ("hot content", hot_content(c, c, 44)),
        ("hot steam", hot_steam(c, c, 44)),
        ("cigarette", cigarette(c, c, 44)),
        ("burning match", burning_match(c, c, 44)),
        ("bucket on fire", bucket_on_fire(c, c, 44)),
        ("mobile phone", mobile_phone(c, c, 44)),
        ("head + ear muffs", protected_head(c, c, 44, "ear_muffs")),
        ("head + goggles", protected_head(c, c, 44, "goggles")),
        ("head + face shield", protected_head(c, c, 44, "face_shield")),
        ("head + helmet", protected_head(c, c, 44, "helmet")),
        ("head + dust mask", protected_head(c, c, 44, "dust_mask")),
        ("head + respirator", protected_head(c, c, 44, "respirator")),
        ("open book", open_book(c, c, 44)),
        ("safety boot", safety_boot(c, c, 44)),
        ("glove", glove(c, c, 44)),
        ("emergency exit (right)", exit_door(c, c, 44)),
        ("first aid cross", first_aid_cross(c, c, 40)),
        ("eyewash", eyewash(c, c, 44)),
        ("safety shower", safety_shower(c, c, 44)),
        ("emergency stop button", stop_button(c, c, 44)),
        ("fire extinguisher", extinguisher(c, c, 44)),
        ("pointing hand", place(pointing_hand_shape(), SYMBOL_HEIGHT, c, c, 44)),
        ("fire alarm call point", call_point(c, c, 44)),
    ]
