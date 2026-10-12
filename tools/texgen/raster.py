"""図形をドット絵として塗る仕組み。

図形（Shape）は「点が内側か」を答える関数で、座標は設計の単位（キャンバス全体が 0〜64、左上が (0, 0)）。
塗るときは、出力の解像度のマス目のピクセルごとに、そのピクセルの中心が図形の内側かで塗るかを決める。
アンチエイリアスはせず、大きく描いてから縮めることもしない（出力のマス目に直接描く）。
解像度が 64 なら、設計の単位の 1 がちょうど 1 ピクセル。32 なら 2 単位が 1 ピクセル。

図形は組み合わせられる: a | b（どちらか）、a & b（両方）、a - b（a から b を抜く）。
平行移動・拡大・回転・左右反転は shift / scale / rotate / mirror。
"""
import math

# 設計の単位でのキャンバスの大きさ
UNITS = 64


class Shape:
    def __init__(self, inside):
        self.inside = inside

    def __call__(self, x, y):
        return self.inside(x, y)

    def __or__(self, other):
        return Shape(lambda x, y: self.inside(x, y) or other.inside(x, y))

    def __and__(self, other):
        return Shape(lambda x, y: self.inside(x, y) and other.inside(x, y))

    def __sub__(self, other):
        return Shape(lambda x, y: self.inside(x, y) and not other.inside(x, y))

    def shift(self, dx, dy):
        return Shape(lambda x, y: self.inside(x - dx, y - dy))

    def scale(self, factor, cx=0.0, cy=0.0):
        """(cx, cy) を中心に factor 倍する。"""
        return Shape(lambda x, y: self.inside(cx + (x - cx) / factor, cy + (y - cy) / factor))

    def rotate(self, degrees, cx, cy):
        """(cx, cy) を中心に時計回りに回す（画面の y は下向きなので、数学の角度の向きと見た目が逆）。"""
        c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))

        def inside(x, y):
            dx, dy = x - cx, y - cy
            return self.inside(cx + dx * c + dy * s, cy - dx * s + dy * c)
        return Shape(inside)

    def mirror(self, cx):
        """列 cx を軸に左右反転する。"""
        return Shape(lambda x, y: self.inside(2 * cx - x, y))


NOTHING = Shape(lambda x, y: False)


def union(*shapes):
    shapes = [s for s in shapes if s is not None]
    return Shape(lambda x, y: any(s.inside(x, y) for s in shapes))


# ---- 基本の図形 ----

def circle(cx, cy, r):
    return Shape(lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r)


def ellipse(cx, cy, rx, ry):
    return Shape(lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1)


def rect(x0, y0, x1, y1):
    """x0 ≦ x < x1、y0 ≦ y < y1 の四角。"""
    return Shape(lambda x, y: x0 <= x < x1 and y0 <= y < y1)


def rounded_rect(x0, y0, x1, y1, r):
    """角を半径 r で丸めた四角。"""
    ix0, iy0, ix1, iy1 = x0 + r, y0 + r, x1 - r, y1 - r

    def inside(x, y):
        dx = max(ix0 - x, 0, x - ix1)
        dy = max(iy0 - y, 0, y - iy1)
        return dx * dx + dy * dy <= r * r
    return Shape(inside)


def segment_distance(x, y, x0, y0, x1, y1):
    """点 (x, y) から線分までの距離。"""
    vx, vy = x1 - x0, y1 - y0
    length2 = vx * vx + vy * vy
    t = 0.0 if length2 == 0 else max(0.0, min(1.0, ((x - x0) * vx + (y - y0) * vy) / length2))
    return math.hypot(x - (x0 + t * vx), y - (y0 + t * vy))


def capsule(p0, p1, r):
    """端の丸い太線（線分から距離 r 以内）。手足や棒に使う。"""
    (x0, y0), (x1, y1) = p0, p1
    return Shape(lambda x, y: segment_distance(x, y, x0, y0, x1, y1) <= r)


def tapered(p0, p1, r0, r1):
    """太さが端から端へ変わる太線（p0 で半径 r0、p1 で r1。端は丸い）。"""
    (x0, y0), (x1, y1) = p0, p1
    vx, vy = x1 - x0, y1 - y0
    length2 = vx * vx + vy * vy

    def inside(x, y):
        t = 0.0 if length2 == 0 else max(0.0, min(1.0, ((x - x0) * vx + (y - y0) * vy) / length2))
        r = r0 + (r1 - r0) * t
        return math.hypot(x - (x0 + t * vx), y - (y0 + t * vy)) <= r
    return Shape(inside)


def polyline(points, r):
    """点を順につないだ太線（つなぎ目も丸い）。"""
    return union(*(capsule(a, b, r) for a, b in zip(points, points[1:])))


def polygon(points):
    """多角形（偶奇規則）。"""
    def inside(x, y):
        result = False
        j = len(points) - 1
        for i in range(len(points)):
            xi, yi = points[i]
            xj, yj = points[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                result = not result
            j = i
        return result
    return Shape(inside)


def polygon_distance(points):
    """多角形の境界までの距離（内側は負）を返す関数。角の丸い形や、枠の内側を作るのに使う。"""
    inside = polygon(points)
    edges = list(zip(points, points[1:] + points[:1]))

    def distance(x, y):
        d = min(segment_distance(x, y, a[0], a[1], b[0], b[1]) for a, b in edges)
        return -d if inside(x, y) else d
    return distance


def offset_polygon(points, amount):
    """多角形を amount だけ太らせた形（負なら細らせる）。角は丸くなる。"""
    distance = polygon_distance(points)
    return Shape(lambda x, y: distance(x, y) <= amount)


def annulus(cx, cy, outer, inner):
    return circle(cx, cy, outer) - circle(cx, cy, inner)


def arc_band(cx, cy, outer, inner, start, end):
    """輪のうち、角度 start〜end（度。上が 0 で時計回り）の部分。"""
    def inside(x, y):
        d2 = (x - cx) ** 2 + (y - cy) ** 2
        if not inner * inner <= d2 <= outer * outer:
            return False
        angle = math.degrees(math.atan2(x - cx, cy - y)) % 360
        return (start <= angle <= end) if start <= end else (angle >= start or angle <= end)
    return Shape(inside)


def sine_band(x0, x1, y_center, amplitude, wavelength, r, phase=0.0, vertical=False):
    """波線（正弦波を太さ 2r で描く）。vertical なら縦に流れる波（x と y を入れ替える）。

    近い点を細かく取った折れ線にして太らせる（波の山でも太さが揃う）。
    """
    steps = max(8, int(abs(x1 - x0) * 2))
    points = []
    for i in range(steps + 1):
        t = x0 + (x1 - x0) * i / steps
        w = y_center + amplitude * math.sin(2 * math.pi * (t - x0) / wavelength + phase)
        points.append((w, t) if vertical else (t, w))
    return polyline(points, r)


# ---- 塗る ----

def fill(image, shape, color):
    """image（解像度は image.size）に shape を塗る。各ピクセルの中心で内外を決める。"""
    width, height = image.size
    unit = UNITS / width
    pixels = image.load()
    for py in range(height):
        y = (py + 0.5) * unit
        for px in range(width):
            if shape.inside((px + 0.5) * unit, y):
                pixels[px, py] = color


def dilate(shape, r, directions=16):
    """shape を r だけ太らせた形（中心と、半径 r の円の上の directions 点のどれかが内側なら内側）。

    手前の手足の周りに隙間を空けるなど、重なった部品を見分けるのに使う。r が小さい（数単位）前提の近似。
    """
    offsets = [(0.0, 0.0)] + [(r * math.cos(2 * math.pi * i / directions), r * math.sin(2 * math.pi * i / directions))
                              for i in range(directions)]
    offsets += [(dx / 2, dy / 2) for dx, dy in offsets[1:]]
    return Shape(lambda x, y: any(shape.inside(x + dx, y + dy) for dx, dy in offsets))


def layered(*shapes, gap=1.0):
    """奥から手前の順に重ね、手前の図形の周りを gap だけ抜いて、重なった所に境目の線を作る。"""
    result = NOTHING
    for shape in shapes:
        result = (result - dilate(shape, gap)) | shape
    return result


def erode(shape, r, directions=16):
    """shape を r だけ細らせた形（dilate の裏返し）。"""
    outside = dilate(Shape(lambda x, y: not shape.inside(x, y)), r, directions)
    return Shape(lambda x, y: not outside.inside(x, y))


def outline(shape, width):
    """shape の輪郭線（内側へ太さ width）。塗りつぶさずに線で描く部品（試験管・手など）に使う。"""
    return shape - erode(shape, width)
