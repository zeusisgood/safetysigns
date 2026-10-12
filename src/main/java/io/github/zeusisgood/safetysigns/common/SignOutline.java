package io.github.zeusisgood.safetysigns.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * 図柄のテクスチャの形（不透明なピクセルの集まり）から作る、切り抜いた板の形。
 * 板の裏面を埋める四角と、板の縁（厚みの側面）の線分を、テクスチャのピクセル単位で持つ。
 * 座標は画像と同じ（x は左から、y は上から。0 〜 幅・高さ）。表の面は図柄のテクスチャ 1 枚を透明な所を描かずに貼るので、ここでは持たない。
 */
public final class SignOutline {

    /** 不透明とみなすアルファの下限より 1 小さい値（描画のアルファテスト GREATER 0.1 と同じ: 26 以上が不透明）。 */
    public static final int ALPHA_THRESHOLD = 25;

    /** 縁の向き（板の外側へ向く方向）。 */
    public enum Side { TOP, BOTTOM, LEFT, RIGHT }

    /** 裏面を埋める四角（x0 ≦ x < x1、y0 ≦ y < y1）。 */
    public static final class Rect {
        public final int x0;
        public final int y0;
        public final int x1;
        public final int y1;

        Rect(int x0, int y0, int x1, int y1) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        @Override
        public String toString() {
            return "Rect(" + x0 + ", " + y0 + ", " + x1 + ", " + y1 + ")";
        }
    }

    /**
     * 縁の線分。TOP・BOTTOM は y = at の横線（from ≦ x < to）、LEFT・RIGHT は x = at の縦線（from ≦ y < to）。
     */
    public static final class Edge {
        public final Side side;
        public final int at;
        public final int from;
        public final int to;

        Edge(Side side, int at, int from, int to) {
            this.side = side;
            this.at = at;
            this.from = from;
            this.to = to;
        }

        @Override
        public String toString() {
            return "Edge(" + side + ", " + at + ", " + from + ", " + to + ")";
        }
    }

    private final int width;
    private final int height;
    private final List<Rect> faces;
    private final List<Edge> edges;

    private SignOutline(int width, int height, List<Rect> faces, List<Edge> edges) {
        this.width = width;
        this.height = height;
        this.faces = Collections.unmodifiableList(faces);
        this.edges = Collections.unmodifiableList(edges);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public List<Rect> getFaces() {
        return faces;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    /** 全部が不透明な四角（テクスチャが読めなかったときなど）。 */
    public static SignOutline square(int size) {
        boolean[] solid = new boolean[size * size];
        Arrays.fill(solid, true);
        return of(size, size, solid);
    }

    /** ARGB のピクセル（行ごと、上から）から作る。アルファが {@link #ALPHA_THRESHOLD} より大きいピクセルを板にする。 */
    public static SignOutline fromArgb(int width, int height, int[] argb) {
        if (width < 1 || height < 1 || argb.length != width * height) {
            throw new IllegalArgumentException("bad size: " + width + "x" + height + ", " + argb.length + " pixels");
        }
        boolean[] solid = new boolean[width * height];
        for (int i = 0; i < solid.length; i++) {
            solid[i] = (argb[i] >>> 24) > ALPHA_THRESHOLD;
        }
        return of(width, height, solid);
    }

    /** 不透明かどうか（行ごと、上から）から作る。 */
    public static SignOutline of(int width, int height, boolean[] solid) {
        if (width < 1 || height < 1 || solid.length != width * height) {
            throw new IllegalArgumentException("bad size: " + width + "x" + height + ", " + solid.length + " pixels");
        }
        Mask mask = new Mask(width, height, solid);
        return new SignOutline(width, height, faces(mask), edges(mask));
    }

    // 行ごとの連続した区間を作り、すぐ上の行と同じ区間なら縦につなげる
    private static List<Rect> faces(Mask mask) {
        List<Rect> done = new ArrayList<>();
        List<int[]> open = new ArrayList<>(); // {x0, y0, x1}
        for (int y = 0; y < mask.height; y++) {
            List<int[]> next = new ArrayList<>();
            int x = 0;
            while (x < mask.width) {
                if (!mask.at(x, y)) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < mask.width && mask.at(x, y)) {
                    x++;
                }
                int[] continued = null;
                for (int[] rect : open) {
                    if (rect[0] == start && rect[2] == x) {
                        continued = rect;
                        break;
                    }
                }
                if (continued != null) {
                    open.remove(continued);
                    next.add(continued);
                } else {
                    next.add(new int[] {start, y, x});
                }
            }
            for (int[] rect : open) {
                done.add(new Rect(rect[0], rect[1], rect[2], y));
            }
            open = next;
        }
        for (int[] rect : open) {
            done.add(new Rect(rect[0], rect[1], rect[2], mask.height));
        }
        return done;
    }

    // 不透明なピクセルと、透明なピクセル（または画像の外）との境目。同じ線の上で続くものは 1 本にまとめる
    private static List<Edge> edges(Mask mask) {
        List<Edge> edges = new ArrayList<>();
        for (int y = 0; y < mask.height; y++) {
            int row = y;
            addRuns(edges, Side.TOP, y, mask.width, x -> mask.at(x, row) && !mask.at(x, row - 1));
            addRuns(edges, Side.BOTTOM, y + 1, mask.width, x -> mask.at(x, row) && !mask.at(x, row + 1));
        }
        for (int x = 0; x < mask.width; x++) {
            int column = x;
            addRuns(edges, Side.LEFT, x, mask.height, y -> mask.at(column, y) && !mask.at(column - 1, y));
            addRuns(edges, Side.RIGHT, x + 1, mask.height, y -> mask.at(column, y) && !mask.at(column + 1, y));
        }
        return edges;
    }

    private static void addRuns(List<Edge> edges, Side side, int at, int length, IntPredicate edge) {
        int i = 0;
        while (i < length) {
            if (!edge.test(i)) {
                i++;
                continue;
            }
            int start = i;
            while (i < length && edge.test(i)) {
                i++;
            }
            edges.add(new Edge(side, at, start, i));
        }
    }

    private static final class Mask {
        final int width;
        final int height;
        final boolean[] solid;

        Mask(int width, int height, boolean[] solid) {
            this.width = width;
            this.height = height;
            this.solid = solid;
        }

        // 画像の外は透明
        boolean at(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height && solid[y * width + x];
        }
    }
}
