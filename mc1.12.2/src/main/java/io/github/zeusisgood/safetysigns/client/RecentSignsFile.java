package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.common.RecentSigns;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 最近使った図柄（クライアントだけ）。設定のフォルダの {@code safetysigns_recent.json} に保存する（ワールド・サーバーをまたいで同じ）。
 * 起動してから初めて使うときに 1 回だけ読み、以後はメモリの履歴を使う。記録して変わるたびに書く。
 * 読めない・書けないときは警告を出すだけで、ゲームは止めない。
 */
public final class RecentSignsFile {

    private static final String FILE_NAME = "safetysigns_recent.json";

    @Nullable
    private static RecentSigns recent;
    /** 書けなかった警告を出したか（クリックのたびに出さない）。 */
    private static boolean warnedWrite;

    private RecentSignsFile() {
    }

    public static RecentSigns get() {
        if (recent == null) {
            recent = read();
        }
        return recent;
    }

    /** GUI で図柄を選んだときに呼ぶ。履歴が変わったらファイルに書く。 */
    public static void record(String signId) {
        if (get().add(signId)) {
            write();
        }
    }

    private static Path path() {
        return Loader.instance().getConfigDir().toPath().resolve(FILE_NAME);
    }

    private static RecentSigns read() {
        Path file = path();
        if (!Files.exists(file)) {
            return new RecentSigns();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return RecentSigns.read(reader);
        } catch (IOException | RuntimeException e) {
            // 壊れていたら空として始める。次に記録したときに正しい形で書き直す
            SafetySignsMod.LOGGER.warn("Failed to read {}; starting with no recent signs: {}", file, e.toString());
            return new RecentSigns();
        }
    }

    private static void write() {
        Path file = path();
        // 書いている途中で落ちても壊れないよう、別のファイルに書いてから置き換える
        Path temp = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                recent.write(writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            if (!warnedWrite) {
                warnedWrite = true;
                SafetySignsMod.LOGGER.warn("Failed to save {}; recent signs last only until the game closes: {}", file, e.toString());
            }
        }
    }
}
