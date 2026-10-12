package io.github.zeusisgood.safetysigns.common;

/**
 * マスの座標（Minecraft の BlockPos を持ち込まないための、同じ意味の値）。
 */
public final class GridPos {

    public final int x;
    public final int y;
    public final int z;

    public GridPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public GridPos add(int dx, int dy, int dz) {
        return new GridPos(x + dx, y + dy, z + dz);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GridPos)) {
            return false;
        }
        GridPos pos = (GridPos) other;
        return x == pos.x && y == pos.y && z == pos.z;
    }

    @Override
    public int hashCode() {
        return (x * 31 + y) * 31 + z;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
