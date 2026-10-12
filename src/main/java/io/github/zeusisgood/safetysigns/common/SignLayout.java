package io.github.zeusisgood.safetysigns.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * 看板が使うマスの並び（幅 × 高さ）。壁に貼る看板を正面から見て、代表のマス（部品 0）を左下に置き、
 * そこから右・上へ広がる。部品の番号は {@code 行 × 幅 + 列}（列は左から、行は下から）。
 * <p>
 * 向きは Minecraft の EnumFacing の horizontal index（0 南・1 西・2 北・3 東）で、看板の面が向いている方向。
 * 図: doc/diagrams/multiblock.md
 */
public final class SignLayout {

    public static final SignLayout SINGLE = new SignLayout(1, 1);
    public static final SignLayout WALL_2X2 = new SignLayout(2, 2);

    // 正面から見た「右」の向き（x, z）。添字は horizontal index。
    // 南を向く看板は北を向いて見るので右は東、西を向く看板は東を向いて見るので右は南、…
    private static final int[] RIGHT_X = {1, 0, -1, 0};
    private static final int[] RIGHT_Z = {0, 1, 0, -1};

    private final int width;
    private final int height;

    public SignLayout(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("size must be positive: " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int partCount() {
        return width * height;
    }

    public int column(int part) {
        return part % width;
    }

    public int row(int part) {
        return part / width;
    }

    /** 正面から見た右の向きの x。 */
    public static int rightX(int facing) {
        return RIGHT_X[facing & 3];
    }

    /** 正面から見た右の向きの z。 */
    public static int rightZ(int facing) {
        return RIGHT_Z[facing & 3];
    }

    /** 代表のマスが origin のとき、部品 part のマス。 */
    public GridPos cellOf(GridPos origin, int facing, int part) {
        int column = column(part);
        return origin.add(rightX(facing) * column, row(part), rightZ(facing) * column);
    }

    /** 部品 part が cell にあるときの、代表のマス。 */
    public GridPos originOf(GridPos cell, int facing, int part) {
        int column = column(part);
        return cell.add(-rightX(facing) * column, -row(part), -rightZ(facing) * column);
    }

    /** 代表のマスが origin のときの全部のマス（部品の番号の順）。 */
    public List<GridPos> cells(GridPos origin, int facing) {
        List<GridPos> cells = new ArrayList<>(partCount());
        for (int part = 0; part < partCount(); part++) {
            cells.add(cellOf(origin, facing, part));
        }
        return Collections.unmodifiableList(cells);
    }

    /** 全部のマスに置けるときだけ true（1 マスでも置けなければ設置に失敗する）。 */
    public boolean canPlace(GridPos origin, int facing, Predicate<GridPos> placeable) {
        for (GridPos cell : cells(origin, facing)) {
            if (!placeable.test(cell)) {
                return false;
            }
        }
        return true;
    }

    /** 鏡に映したときの部品の番号（左右が入れ替わるので列を反転する）。 */
    public int mirrorPart(int part) {
        return row(part) * width + (width - 1 - column(part));
    }
}
