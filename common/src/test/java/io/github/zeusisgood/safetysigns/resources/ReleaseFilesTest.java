package io.github.zeusisgood.safetysigns.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * 版を書く所（各版の build.gradle・SafetySignsMod.VERSION と CHANGELOG.md の最新の見出し）が揃っているか。
 * main に取り込まれると ci.yml の release ジョブが mc1.12.2/build.gradle の版でタグと GitHub Release を作り、本文に
 * CHANGELOG.md のその版の節を使うので、ずれは PR の CI で落とす。作業ディレクトリはリポジトリの直下。
 */
public class ReleaseFilesTest {

    private static final String VERSION = find(read("mc1.12.2/build.gradle"), "(?m)^version = '([^']+)'");

    @Test
    public void modVersionMatchesBuildGradle() {
        String source = read("mc1.12.2/src/main/java/io/github/zeusisgood/safetysigns/SafetySignsMod.java");
        assertEquals("1.12.2 の SafetySignsMod.VERSION", VERSION, find(source, "String VERSION = \"([^\"]+)\""));
    }

    /** 一番上の「## 」の見出しが今の版。 */
    @Test
    public void latestChangelogHeadingMatchesBuildGradle() {
        assertEquals("CHANGELOG.md の最新の見出し", VERSION, find(read("CHANGELOG.md"), "(?m)^## (.+?)\\s*$"));
    }

    private static String read(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String find(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) {
            fail("not found: " + regex);
        }
        return m.group(1);
    }
}
