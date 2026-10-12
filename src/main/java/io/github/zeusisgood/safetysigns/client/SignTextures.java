package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.common.SignCatalog;
import io.github.zeusisgood.safetysigns.common.SignDefinition;
import io.github.zeusisgood.safetysigns.common.SignDefinitionParser;
import io.github.zeusisgood.safetysigns.common.SignOutline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 図柄の一覧（定義ファイル）とテクスチャ（クライアントだけ）。
 * ブロックのテクスチャを貼り直すたび（起動・F3+T・リソースパックの変更）に、全パックの定義ファイルを読み直して、
 * 図柄のテクスチャをブロックのテクスチャに登録し、テクスチャの透明でない所から切り抜いた板の形（{@link SignOutline}）を作る。
 */
@Mod.EventBusSubscriber(modid = SafetySignsMod.MODID, value = Side.CLIENT)
public final class SignTextures {

    /** 無地の看板の前面。 */
    public static final ResourceLocation BLANK = new ResourceLocation(SafetySignsMod.MODID, "blocks/sign_blank");
    /** 一覧にない図柄 ID の「?」。 */
    public static final ResourceLocation UNKNOWN = new ResourceLocation(SafetySignsMod.MODID, "blocks/sign_unknown");
    /** 看板の裏面と縁（灰色の金属板）。 */
    public static final ResourceLocation BACK = new ResourceLocation(SafetySignsMod.MODID, "blocks/sign_back");

    /** テクスチャが読めなかったときの板の形（四角。テクスチャはバニラの「テクスチャなし」の四角になる）。 */
    private static final SignOutline MISSING_OUTLINE = SignOutline.square(16);

    /** 各名前空間の定義ファイルの置き場所（assets/<namespace>/ からの相対）。 */
    private static final String DEFINITIONS_PATH = "safetysigns/signs.json";

    private static volatile SignCatalog catalog = SignCatalog.EMPTY;
    private static volatile Map<ResourceLocation, SignOutline> outlines = Collections.emptyMap();

    private SignTextures() {
    }

    public static SignCatalog catalog() {
        return catalog;
    }

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        TextureMap map = event.getMap();
        // ブロックのテクスチャ（textures/ 以下を貼る地図）だけ。起動直後でも確実に見分けられるよう、場所で見る
        if (!"textures".equals(map.getBasePath())) {
            return;
        }
        IResourceManager resourceManager = Minecraft.getMinecraft().getResourceManager();
        SignCatalog loaded = load(resourceManager);
        List<ResourceLocation> textures = new ArrayList<>();
        textures.add(BLANK);
        textures.add(UNKNOWN);
        for (SignDefinition definition : loaded.all()) {
            textures.add(new ResourceLocation(definition.getTexture()));
        }
        Map<ResourceLocation, SignOutline> shapes = new HashMap<>();
        for (ResourceLocation texture : textures) {
            map.registerSprite(texture);
            shapes.computeIfAbsent(texture, t -> readOutline(resourceManager, t));
        }
        map.registerSprite(BACK);
        outlines = shapes;
        catalog = loaded;
        SafetySignsMod.LOGGER.info("Loaded {} sign definitions", loaded.all().size());
    }

    private static SignCatalog load(IResourceManager resourceManager) {
        List<List<SignDefinition>> packs = new ArrayList<>();
        // 名前空間の順は ID に関係しない（ID の名前空間はファイルの名前空間と同じに限るため）。並べるのはログを揃えるためだけ
        for (String namespace : new TreeSet<>(resourceManager.getResourceDomains())) {
            List<IResource> resources;
            try {
                // 優先度の低いパックから順に返る
                resources = resourceManager.getAllResources(new ResourceLocation(namespace, DEFINITIONS_PATH));
            } catch (FileNotFoundException e) {
                continue;
            } catch (IOException e) {
                SafetySignsMod.LOGGER.warn("Failed to list sign definitions in {}", namespace, e);
                continue;
            }
            for (IResource resource : resources) {
                try (IResource r = resource;
                        Reader reader = new InputStreamReader(r.getInputStream(), StandardCharsets.UTF_8)) {
                    SignDefinitionParser.Result result = SignDefinitionParser.parse(namespace, reader);
                    for (String warning : result.getWarnings()) {
                        SafetySignsMod.LOGGER.warn("{} in {}: {}", r.getResourceLocation(), r.getResourcePackName(), warning);
                    }
                    packs.add(result.getDefinitions());
                } catch (IOException e) {
                    SafetySignsMod.LOGGER.warn("Failed to read {} in {}", resource.getResourceLocation(),
                            resource.getResourcePackName(), e);
                }
            }
        }
        return SignCatalog.merge(packs);
    }

    /**
     * テクスチャの PNG を読み、透明でない所から板の形を作る。アニメーションするテクスチャ（.mcmeta があり、縦に並んだコマ）は最初のコマだけ。
     * 読めなければ四角（ログに警告）。
     */
    private static SignOutline readOutline(IResourceManager resourceManager, ResourceLocation texture) {
        ResourceLocation file = new ResourceLocation(texture.getResourceDomain(), "textures/" + texture.getResourcePath() + ".png");
        try (IResource resource = resourceManager.getResource(file); InputStream in = resource.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IOException("not an image");
            }
            int width = image.getWidth();
            // コマは幅と同じ高さの正方形（バニラの既定）。.mcmeta のない縦長の静止画は全体を使う
            boolean animated = image.getHeight() > width && image.getHeight() % width == 0 && hasMetadata(resourceManager, file);
            int height = animated ? width : image.getHeight();
            return SignOutline.fromArgb(width, height, image.getRGB(0, 0, width, height, null, 0, width));
        } catch (IOException e) {
            SafetySignsMod.LOGGER.warn("Failed to read the shape of {}", file, e);
            return MISSING_OUTLINE;
        }
    }

    private static boolean hasMetadata(IResourceManager resourceManager, ResourceLocation file) {
        try (IResource resource = resourceManager.getResource(file)) {
            return resource.hasMetadata();
        } catch (IOException e) {
            return false;
        }
    }

    /** テクスチャ（{@link #textureOf} の結果）から切り抜いた板の形。 */
    public static SignOutline outlineOf(ResourceLocation texture) {
        return outlines.getOrDefault(texture, MISSING_OUTLINE);
    }

    /** 図柄 ID に対応するテクスチャ。null は無地、一覧にない ID は「?」。 */
    public static ResourceLocation textureOf(@Nullable String signId) {
        if (signId == null) {
            return BLANK;
        }
        SignDefinition definition = catalog.get(signId);
        return definition != null ? new ResourceLocation(definition.getTexture()) : UNKNOWN;
    }

    public static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture.toString());
    }

    public static TextureAtlasSprite sprite(@Nullable String signId) {
        return sprite(textureOf(signId));
    }

    /** 表示中の言語のコード（"ja_jp" など）。 */
    public static String language() {
        return Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
    }

    /** ツールチップなどの 1 行の説明: 「W003 放射性物質・電離放射線」「無地」「? 不明な図柄（<ID>）」。 */
    public static String describe(@Nullable String signId) {
        if (signId == null) {
            return I18n.format("gui.safetysigns.blank");
        }
        SignDefinition definition = catalog.get(signId);
        if (definition == null) {
            return I18n.format("gui.safetysigns.unknown", signId);
        }
        return definition.getCode() + " " + definition.getName(language());
    }
}
