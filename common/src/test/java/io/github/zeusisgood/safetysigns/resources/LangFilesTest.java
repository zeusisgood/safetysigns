package io.github.zeusisgood.safetysigns.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * 翻訳の正本（lang/*.json）の日英のキーが一致し、同じキーが 2 回ないか。
 * json は 1 行に 1 つの {@code "キー": "値"} の形で書く約束なので、行ごとに読む（JSON のライブラリに頼らない。
 * 同じキーの 2 回目は JSON のライブラリでは後勝ちで黙って消えるため）。作業ディレクトリはリポジトリの直下。
 */
public class LangFilesTest {

    private static final Pattern ENTRY = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:\\s*\".*\",?\\s*$");

    @Test
    public void englishAndJapaneseHaveTheSameKeys() {
        assertEquals(keys("lang/en_us.json"), keys("lang/ja_jp.json"));
    }

    @Test
    public void blockNamesUseTheNewKeyForm() {
        for (String key : keys("lang/en_us.json")) {
            assertTrue("block.safetysigns.<名前> にする（1.12.2 の tile.….name はビルドで作る）: " + key,
                    !key.startsWith("tile."));
        }
    }

    private static Set<String> keys(String path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Set<String> keys = new LinkedHashSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("{") || trimmed.equals("}") || trimmed.isEmpty()) {
                continue;
            }
            Matcher m = ENTRY.matcher(line);
            if (!m.matches()) {
                fail(path + ": 1 行に 1 つの \"キー\": \"値\" にする: " + line);
            }
            if (!keys.add(m.group(1))) {
                fail(path + ": 同じキーが 2 回ある: " + m.group(1));
            }
        }
        return keys;
    }
}
