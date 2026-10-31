package com.blockplace.client.config;

import com.blockplace.Constants;
import com.blockplace.client.animation.tree.TreeDetector;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side configuration for cartoon tree shake animations.
 */
public class BlockPlaceConfig {

    private static final Path CONFIG_PATH = Path.of("config", "blockplace.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final List<String> DEFAULT_WHITELIST = List.of(
            "#minecraft:logs",
            "minecraft:mushroom_stem",
            "minecraft:warped_stem",
            "minecraft:stripped_warped_stem",
            "minecraft:crimson_stem",
            "minecraft:stripped_crimson_stem",
            "minecraft:bamboo_block",
            "minecraft:stripped_bamboo_block",
            "minecraft:mangrove_roots"
    );

    /**
     * Whether cartoon tree punch/shake animations are enabled.
     */
    public static boolean enableTreeShake = true;

    /**
     * Multiplier for tree wobble displacement and tilt intensity.
     */
    public static float treeShakeIntensity = 0.25f;

    /**
     * Duration of the tree wobble animation in ticks (14 ticks ~ 700ms).
     */
    public static int treeShakeDurationTicks = 14;

    /**
     * Whether to spawn wood splinters and falling canopy leaves when punching trees.
     */
    public static boolean enableTreeParticles = true;

    /**
     * Whether to play wood impact and rustling foliage audio when punching trees.
     */
    public static boolean enableTreeSound = true;

    /**
     * Configurable whitelist of block IDs (e.g. "minecraft:mushroom_stem") or block tags
     * (e.g. "#minecraft:logs") that can be punched to trigger tree shaking animations.
     */
    public static List<String> shakableBlocksWhitelist = new ArrayList<>(DEFAULT_WHITELIST);

    /**
     * Resets whitelist to factory defaults.
     */
    public static void resetWhitelistToDefaults() {
        shakableBlocksWhitelist = new ArrayList<>(DEFAULT_WHITELIST);
        TreeDetector.rebuildWhitelistCache();
    }

    /**
     * Loads saved configuration from config/blockplace.json.
     */
    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String content = Files.readString(CONFIG_PATH);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("enableTreeShake")) enableTreeShake = json.get("enableTreeShake").getAsBoolean();
                if (json.has("treeShakeIntensity")) treeShakeIntensity = json.get("treeShakeIntensity").getAsFloat();
                if (json.has("treeShakeDurationTicks")) treeShakeDurationTicks = json.get("treeShakeDurationTicks").getAsInt();
                if (json.has("enableTreeParticles")) enableTreeParticles = json.get("enableTreeParticles").getAsBoolean();
                if (json.has("enableTreeSound")) enableTreeSound = json.get("enableTreeSound").getAsBoolean();

                if (json.has("shakableBlocksWhitelist") && json.get("shakableBlocksWhitelist").isJsonArray()) {
                    shakableBlocksWhitelist.clear();
                    for (JsonElement elem : json.getAsJsonArray("shakableBlocksWhitelist")) {
                        shakableBlocksWhitelist.add(elem.getAsString());
                    }
                } else {
                    shakableBlocksWhitelist = new ArrayList<>(DEFAULT_WHITELIST);
                }

                TreeDetector.rebuildWhitelistCache();
                Constants.LOG.info("Loaded {} config from {}, treeShake={}, intensity={}", Constants.MOD_NAME, CONFIG_PATH, enableTreeShake, treeShakeIntensity);
            } else {
                save();
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to load config from {}", CONFIG_PATH, e);
        }
    }

    /**
     * Saves configuration to config/blockplace.json.
     */
    public static void save() {
        try {
            if (CONFIG_PATH.getParent() != null) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            JsonObject json = new JsonObject();
            json.addProperty("enableTreeShake", enableTreeShake);
            json.addProperty("treeShakeIntensity", treeShakeIntensity);
            json.addProperty("treeShakeDurationTicks", treeShakeDurationTicks);
            json.addProperty("enableTreeParticles", enableTreeParticles);
            json.addProperty("enableTreeSound", enableTreeSound);

            JsonArray whitelistArray = new JsonArray();
            for (String item : shakableBlocksWhitelist) {
                whitelistArray.add(item);
            }
            json.add("shakableBlocksWhitelist", whitelistArray);

            Files.writeString(CONFIG_PATH, GSON.toJson(json));
            TreeDetector.rebuildWhitelistCache();
            Constants.LOG.info("Saved {} config to {}, treeShake={}, intensity={}", Constants.MOD_NAME, CONFIG_PATH, enableTreeShake, treeShakeIntensity);
        } catch (Exception e) {
            Constants.LOG.error("Failed to save config to {}", CONFIG_PATH, e);
        }
    }
}
