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
 * 版を書く所（各版の build.gradle・gradle.properties・SafetySignsMod.VERSION と CHANGELOG.md の最新の見出し）が揃っているか。
 * 接続できる範囲（1.12.2 の ACCEPTED_VERSIONS・新しい版の通信の PROTOCOL）が今のマイナーか。
 * main に取り込まれると ci.yml の release ジョブが mc1.12.2/build.gradle の版でタグと GitHub Release を作り、本文に
 * CHANGELOG.md のその版の節を使うので、ずれは PR の CI で落とす。作業ディレクトリはリポジトリの直下。
 */
public class ReleaseFilesTest {

    private static final String MOD_SOURCE = "/src/main/java/io/github/zeusisgood/safetysigns/SafetySignsMod.java";
    private static final String NETWORK_SOURCE = "/src/main/java/io/github/zeusisgood/safetysigns/network/SafetySignsNetwork.java";
    private static final String[] NEW_VERSIONS = {"mc1.20.1", "mc1.21.1"};

    private static final String VERSION = find(read("mc1.12.2/build.gradle"), "(?m)^version = '([^']+)'");

    @Test
    public void modVersionMatchesBuildGradle() {
        assertEquals("1.12.2 の SafetySignsMod.VERSION", VERSION, modVersion("mc1.12.2"));
    }

    @Test
    public void newVersionsMatchTheSameVersion() {
        for (String dir : NEW_VERSIONS) {
            assertEquals(dir + "/gradle.properties の mod_version", VERSION,
                    find(read(dir + "/gradle.properties"), "(?m)^mod_version=(\\S+)"));
            assertEquals(dir + " の SafetySignsMod.VERSION", VERSION, modVersion(dir));
        }
    }

    /** 同じマイナー同士だけ接続できる（1.12.2 は [1.1,1.2) の形、新しい版はマイナーの番号）。 */
    @Test
    public void connectableRangeIsTheCurrentMinor() {
        String minor = find(VERSION, "^(\\d+\\.\\d+)\\.");
        int next = Integer.parseInt(find(minor, "\\.(\\d+)$")) + 1;
        String range = "[" + minor + "," + find(minor, "^(\\d+)\\.") + "." + next + ")";
        assertEquals("1.12.2 の ACCEPTED_VERSIONS", range,
                find(read("mc1.12.2" + MOD_SOURCE), "String ACCEPTED_VERSIONS = \"([^\"]+)\""));
        for (String dir : NEW_VERSIONS) {
            assertEquals(dir + " の PROTOCOL", minor, find(read(dir + NETWORK_SOURCE), "String PROTOCOL = \"([^\"]+)\""));
        }
    }

    /** 一番上の「## 」の見出しが今の版。 */
    @Test
    public void latestChangelogHeadingMatchesBuildGradle() {
        assertEquals("CHANGELOG.md の最新の見出し", VERSION, find(read("CHANGELOG.md"), "(?m)^## (.+?)\\s*$"));
    }

    private static String modVersion(String dir) {
        return find(read(dir + MOD_SOURCE), "String VERSION = \"([^\"]+)\"");
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
