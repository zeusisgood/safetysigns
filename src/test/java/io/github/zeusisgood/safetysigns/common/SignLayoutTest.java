package io.github.zeusisgood.safetysigns.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SignLayoutTest {

    private static final int SOUTH = 0;
    private static final int WEST = 1;
    private static final int NORTH = 2;
    private static final int EAST = 3;

    private static final GridPos ORIGIN = new GridPos(10, 64, -5);

    @Test
    public void rightIsTheViewersRight() {
        // 南を向く看板（見る人は北を向く）→ 右は東（+x）
        assertEquals(1, SignLayout.rightX(SOUTH));
        assertEquals(0, SignLayout.rightZ(SOUTH));
        // 西を向く看板（見る人は東を向く）→ 右は南（+z）
        assertEquals(0, SignLayout.rightX(WEST));
        assertEquals(1, SignLayout.rightZ(WEST));
        // 北を向く看板（見る人は南を向く）→ 右は西（-x）
        assertEquals(-1, SignLayout.rightX(NORTH));
        assertEquals(0, SignLayout.rightZ(NORTH));
        // 東を向く看板（見る人は西を向く）→ 右は北（-z）
        assertEquals(0, SignLayout.rightX(EAST));
        assertEquals(-1, SignLayout.rightZ(EAST));
    }

    @Test
    public void wall2x2CellsGoRightAndUp() {
        assertEquals(Arrays.asList(
                new GridPos(10, 64, -5),   // 0: 左下（代表）
                new GridPos(11, 64, -5),   // 1: 右下
                new GridPos(10, 65, -5),   // 2: 左上
                new GridPos(11, 65, -5)),  // 3: 右上
                SignLayout.WALL_2X2.cells(ORIGIN, SOUTH));
        assertEquals(Arrays.asList(
                new GridPos(10, 64, -5),
                new GridPos(10, 64, -6),
                new GridPos(10, 65, -5),
                new GridPos(10, 65, -6)),
                SignLayout.WALL_2X2.cells(ORIGIN, EAST));
    }

    @Test
    public void singleUsesOnlyTheOrigin() {
        for (int facing = 0; facing < 4; facing++) {
            assertEquals(Arrays.asList(ORIGIN), SignLayout.SINGLE.cells(ORIGIN, facing));
        }
    }

    @Test
    public void originOfFindsTheRepresentativeFromAnyPart() {
        for (int facing = 0; facing < 4; facing++) {
            for (int part = 0; part < 4; part++) {
                GridPos cell = SignLayout.WALL_2X2.cellOf(ORIGIN, facing, part);
                assertEquals(ORIGIN, SignLayout.WALL_2X2.originOf(cell, facing, part));
            }
        }
    }

    @Test
    public void cellsDoNotOverlap() {
        for (int facing = 0; facing < 4; facing++) {
            Set<GridPos> unique = new HashSet<>(SignLayout.WALL_2X2.cells(ORIGIN, facing));
            assertEquals(4, unique.size());
        }
    }

    @Test
    public void canPlaceNeedsEveryCell() {
        assertTrue(SignLayout.WALL_2X2.canPlace(ORIGIN, NORTH, cell -> true));
        GridPos blocked = SignLayout.WALL_2X2.cellOf(ORIGIN, NORTH, 3);
        assertFalse(SignLayout.WALL_2X2.canPlace(ORIGIN, NORTH, cell -> !cell.equals(blocked)));
        // ほかの向きなら、そのマスを使わないので置ける
        assertTrue(SignLayout.WALL_2X2.canPlace(ORIGIN, SOUTH, cell -> !cell.equals(blocked)));
    }

    @Test
    public void partNumbersAreRowTimesWidthPlusColumn() {
        SignLayout layout = SignLayout.WALL_2X2;
        assertEquals(4, layout.partCount());
        assertEquals(0, layout.column(2));
        assertEquals(1, layout.row(2));
        assertEquals(1, layout.column(3));
        assertEquals(1, layout.row(3));
    }

    @Test
    public void mirrorSwapsColumns() {
        assertEquals(1, SignLayout.WALL_2X2.mirrorPart(0));
        assertEquals(0, SignLayout.WALL_2X2.mirrorPart(1));
        assertEquals(3, SignLayout.WALL_2X2.mirrorPart(2));
        assertEquals(2, SignLayout.WALL_2X2.mirrorPart(3));
        assertEquals(0, SignLayout.SINGLE.mirrorPart(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptySize() {
        new SignLayout(0, 2);
    }
}
