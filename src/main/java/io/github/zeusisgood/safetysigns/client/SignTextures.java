package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.common.SignCatalog;
import io.github.zeusisgood.safetysigns.common.SignDefinition;
import io.github.zeusisgood.safetysigns.common.SignDefinitionParser;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 図柄の一覧（定義ファイル）とテクスチャ（クライアントだけ）。
 * ブロックのテクスチャを貼り直すたび（起動・F3+T・リソースパックの変更）に、全パックの定義ファイルを読み直して、
 * 図柄のテクスチャをブロックのテクスチャに登録する。
 */
@Mod.EventBusSubscriber(modid = SafetySignsMod.MODID, value = Side.CLIENT)
public final class SignTextures {

    /** 無地の看板の前面。 */
    public static final ResourceLocation BLANK = new ResourceLocation(SafetySignsMod.MODID, "blocks/sign_blank");
    /** 一覧にない図柄 ID の「?」。 */
    public static final ResourceLocation UNKNOWN = new ResourceLocation(SafetySignsMod.MODID, "blocks/sign_unknown");

    /** 各名前空間の定義ファイルの置き場所（assets/<namespace>/ からの相対）。 */
    private static final String DEFINITIONS_PATH = "safetysigns/signs.json";

    private static volatile SignCatalog catalog = SignCatalog.EMPTY;

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
        SignCatalog loaded = load(Minecraft.getMinecraft().getResourceManager());
        catalog = loaded;
        map.registerSprite(BLANK);
        map.registerSprite(UNKNOWN);
        for (SignDefinition definition : loaded.all()) {
            map.registerSprite(new ResourceLocation(definition.getTexture()));
        }
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
