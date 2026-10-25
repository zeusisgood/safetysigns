package io.github.zeusisgood.safetysigns.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RecentSignsTest {

    @Test
    public void addsNewestFirstAndMovesDuplicatesToTheFront() {
        RecentSigns recent = new RecentSigns();
        assertTrue(recent.add("safetysigns:w001"));
        assertTrue(recent.add("safetysigns:p002"));
        assertTrue(recent.add("safetysigns:w001"));
        assertEquals(Arrays.asList("safetysigns:w001", "safetysigns:p002"), recent.getIds());
    }

    @Test
    public void addingTheFirstIdAgainChangesNothing() {
        RecentSigns recent = new RecentSigns();
        recent.add("safetysigns:w001");
        assertFalse(recent.add("safetysigns:w001"));
    }

    @Test
    public void rejectsMalformedIds() {
        RecentSigns recent = new RecentSigns();
        assertFalse(recent.add(null));
        assertFalse(recent.add("w001"));
        assertEquals(Collections.emptyList(), recent.getIds());
    }

    @Test
    public void keepsAtMostMaxAndDropsTheOldest() {
        RecentSigns recent = new RecentSigns();
        for (int i = 0; i < RecentSigns.MAX + 5; i++) {
            recent.add("safetysigns:s" + i);
        }
        assertEquals(RecentSigns.MAX, recent.getIds().size());
        assertEquals("safetysigns:s" + (RecentSigns.MAX + 4), recent.getIds().get(0));
        assertEquals("safetysigns:s5", recent.getIds().get(RecentSigns.MAX - 1));
    }

    @Test
    public void writesAndReadsBack() {
        RecentSigns recent = new RecentSigns();
        recent.add("mypack:no_drone");
        recent.add("safetysigns:w001");
        StringWriter writer = new StringWriter();
        recent.write(writer);
        assertTrue(writer.toString().contains("\"version\": 1"));
        assertEquals(recent.getIds(), RecentSigns.read(new StringReader(writer.toString())).getIds());
    }

    @Test
    public void readingSkipsBadEntriesUnknownKeysAndTheOverflow() {
        StringBuilder json = new StringBuilder("{\"version\": 9, \"favorites\": [\"safetysigns:e001\"], \"recent\": [");
        json.append("\"safetysigns:w001\", 3, \"Bad Id\", \"safetysigns:w001\", null");
        List<String> expected = new ArrayList<>();
        expected.add("safetysigns:w001");
        for (int i = 0; i < RecentSigns.MAX + 3; i++) {
            json.append(", \"safetysigns:s").append(i).append('"');
            if (expected.size() < RecentSigns.MAX) {
                expected.add("safetysigns:s" + i);
            }
        }
        json.append("]}");
        assertEquals(expected, RecentSigns.read(new StringReader(json.toString())).getIds());
    }

    @Test
    public void readingWithoutRecentIsEmpty() {
        assertEquals(Collections.emptyList(), RecentSigns.read(new StringReader("{\"version\": 1}")).getIds());
    }

    // JsonParseException（1.21.1 のテストは Gson を実行時にしか持たないので、親の型で受ける）
    @Test(expected = RuntimeException.class)
    public void readingBrokenJsonThrows() {
        RecentSigns.read(new StringReader("{\"recent\": [\"safetysigns:w001\""));
    }

    // JsonParseException（1.21.1 のテストは Gson を実行時にしか持たないので、親の型で受ける）
    @Test(expected = RuntimeException.class)
    public void readingAWrongShapeThrows() {
        RecentSigns.read(new StringReader("{\"recent\": \"safetysigns:w001\"}"));
    }
}
