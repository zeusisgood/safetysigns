package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.common.SignCatalog;
import io.github.zeusisgood.safetysigns.common.SignDefinition;
import io.github.zeusisgood.safetysigns.common.SignDefinitionParser;
import io.github.zeusisgood.safetysigns.common.SignOutline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.inventory.InventoryMenu;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 図柄の一覧（定義ファイル）とテクスチャ（クライアントだけ）。
 * リソースを読み直すたび（起動・F3+T・リソースパックの変更）に、全パックの定義ファイルを読み直して、
 * テクスチャの透明でない所から切り抜いた板の形（{@link SignOutline}）を作る。
 * 図柄のテクスチャそのものは、ブロックの atlas が {@code textures/signs/} の下を全部集める
 * （{@code assets/minecraft/atlases/blocks.json}）。なので定義ファイルの texture は {@code signs/} の下だけが使える。
 */
public final class SignTextures implements ResourceManagerReloadListener {

    /** 無地の看板の前面。 */
    public static final ResourceLocation BLANK = ResourceLocation.fromNamespaceAndPath(SafetySignsMod.MODID, "block/sign_blank");
    /** 一覧にない図柄 ID の「?」。 */
    public static final ResourceLocation UNKNOWN = ResourceLocation.fromNamespaceAndPath(SafetySignsMod.MODID, "block/sign_unknown");
    /** 看板の裏面と縁（灰色の金属板）。 */
    public static final ResourceLocation BACK = ResourceLocation.fromNamespaceAndPath(SafetySignsMod.MODID, "block/sign_back");

    /** atlas に入る図柄のテクスチャのディレクトリ（atlases/blocks.json の directory のソース）。 */
    private static final String SIGN_TEXTURE_DIRECTORY = "signs/";

    /** テクスチャが読めなかったときの板の形（四角。テクスチャはバニラの「テクスチャなし」の四角になる）。 */
    private static final SignOutline MISSING_OUTLINE = SignOutline.square(16);

    /** 各名前空間の定義ファイルの置き場所（assets/<namespace>/ からの相対）。 */
    private static final String DEFINITIONS_PATH = "safetysigns/signs.json";

    public static final SignTextures INSTANCE = new SignTextures();

    private static volatile SignCatalog catalog = SignCatalog.EMPTY;
    private static volatile Map<ResourceLocation, SignOutline> outlines = Collections.emptyMap();
    /** atlas に入らない所を texture に書いた図柄 ID（「?」で出す）。 */
    private static volatile Set<String> outsideAtlas = Collections.emptySet();
    /** リソースを読み直した回数。描画が取っておいた形（SignRenderer）を作り直す合図。 */
    private static volatile int generation;

    private SignTextures() {
    }

    public static SignCatalog catalog() {
        return catalog;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        SignCatalog loaded = load(resourceManager);
        Set<String> outside = new HashSet<>();
        List<ResourceLocation> textures = new ArrayList<>();
        textures.add(BLANK);
        textures.add(UNKNOWN);
        for (SignDefinition definition : loaded.all()) {
            ResourceLocation texture = ResourceLocation.parse(definition.getTexture());
            if (!texture.getPath().startsWith(SIGN_TEXTURE_DIRECTORY)) {
                SafetySignsMod.LOGGER.warn("The texture of {} ({}) is not under textures/{}; it is shown as \"?\" on this version",
                        definition.getId(), texture, SIGN_TEXTURE_DIRECTORY);
                outside.add(definition.getId());
                continue;
            }
            textures.add(texture);
        }
        Map<ResourceLocation, SignOutline> shapes = new HashMap<>();
        for (ResourceLocation texture : textures) {
            shapes.computeIfAbsent(texture, t -> readOutline(resourceManager, t));
        }
        outlines = shapes;
        outsideAtlas = outside;
        catalog = loaded;
        generation++;
        SafetySignsMod.LOGGER.info("Loaded {} sign definitions", loaded.all().size());
    }

    private static SignCatalog load(ResourceManager resourceManager) {
        List<List<SignDefinition>> packs = new ArrayList<>();
        // 名前空間の順は ID に関係しない（ID の名前空間はファイルの名前空間と同じに限るため）。並べるのはログを揃えるためだけ
        for (String namespace : new TreeSet<>(resourceManager.getNamespaces())) {
            // 優先度の低いパックから順に返る
            for (Resource resource : resourceManager.getResourceStack(ResourceLocation.fromNamespaceAndPath(namespace, DEFINITIONS_PATH))) {
                try (Reader reader = resource.openAsReader()) {
                    SignDefinitionParser.Result result = SignDefinitionParser.parse(namespace, reader);
                    for (String warning : result.getWarnings()) {
                        SafetySignsMod.LOGGER.warn("{}:{} in {}: {}", namespace, DEFINITIONS_PATH, resource.sourcePackId(), warning);
                    }
                    packs.add(result.getDefinitions());
                } catch (IOException e) {
                    SafetySignsMod.LOGGER.warn("Failed to read {}:{} in {}", namespace, DEFINITIONS_PATH, resource.sourcePackId(), e);
                }
            }
        }
        return SignCatalog.merge(packs);
    }

    /**
     * テクスチャの PNG を読み、透明でない所から板の形を作る。アニメーションするテクスチャ（.mcmeta があり、縦に並んだコマ）は最初のコマだけ。
     * 読めなければ四角（ログに警告）。
     */
    private static SignOutline readOutline(ResourceManager resourceManager, ResourceLocation texture) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), "textures/" + texture.getPath() + ".png");
        Optional<Resource> found = resourceManager.getResource(file);
        if (found.isEmpty()) {
            SafetySignsMod.LOGGER.warn("Failed to read the shape of {}: not found", file);
            return MISSING_OUTLINE;
        }
        Resource resource = found.get();
        try (InputStream in = resource.open()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IOException("not an image");
            }
            int width = image.getWidth();
            // コマは幅と同じ高さの正方形（バニラの既定）。.mcmeta のない縦長の静止画は全体を使う
            boolean animated = image.getHeight() > width && image.getHeight() % width == 0 && hasAnimation(resource);
            int height = animated ? width : image.getHeight();
            // 解像度は問わない（板の形はテクスチャの幅・高さに対する割合で描く）。2 の累乗の正方形でないと、
            // 板が引き伸ばされたり、ブロックのテクスチャ全体のミップマップが下がったりするので、知らせるだけにする
            if (width != height || Integer.bitCount(width) != 1) {
                SafetySignsMod.LOGGER.warn("{} is {}x{}; sign textures should be a power-of-two square (16x16, 32x32, 64x64, ...)",
                        file, width, height);
            }
            return SignOutline.fromArgb(width, height, image.getRGB(0, 0, width, height, null, 0, width));
        } catch (IOException e) {
            SafetySignsMod.LOGGER.warn("Failed to read the shape of {}", file, e);
            return MISSING_OUTLINE;
        }
    }

    private static boolean hasAnimation(Resource resource) {
        try {
            return resource.metadata().getSection(AnimationMetadataSection.SERIALIZER).isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    /** リソースを読み直した回数（読み直すと図柄のテクスチャと板の形が変わる）。 */
    public static int generation() {
        return generation;
    }

    /** テクスチャ（{@link #textureOf} の結果）から切り抜いた板の形。 */
    public static SignOutline outlineOf(ResourceLocation texture) {
        return outlines.getOrDefault(texture, MISSING_OUTLINE);
    }

    /** 図柄 ID に対応するテクスチャ。null は無地、一覧にない ID と atlas に入らない図柄は「?」。 */
    public static ResourceLocation textureOf(@Nullable String signId) {
        if (signId == null) {
            return BLANK;
        }
        SignDefinition definition = catalog.get(signId);
        return definition != null && !outsideAtlas.contains(signId) ? ResourceLocation.parse(definition.getTexture()) : UNKNOWN;
    }

    public static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
    }

    /** 表示中の言語のコード（"ja_jp" など）。 */
    public static String language() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }

    /** ツールチップなどの 1 行の説明: 「W003 放射性物質・電離放射線」「無地」「? 不明な図柄（<ID>）」。 */
    public static Component describe(@Nullable String signId) {
        if (signId == null) {
            return Component.translatable("gui.safetysigns.blank");
        }
        SignDefinition definition = catalog.get(signId);
        if (definition == null) {
            return Component.translatable("gui.safetysigns.unknown", signId);
        }
        return Component.literal(definition.getCode() + " " + definition.getName(language()));
    }
}
