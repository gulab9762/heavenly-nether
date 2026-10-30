package com.blockplace.client.config;

import com.blockplace.Constants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side configuration for cartoon tree shake animations.
 */
public class BlockPlaceConfig {

    private static final Path CONFIG_PATH = Path.of("config", "blockplace.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Whether cartoon tree punch/shake animations are enabled.
     */
    public static boolean enableTreeShake = true;

    /**
     * Multiplier for tree wobble displacement and tilt intensity.
     */
    public static float treeShakeIntensity = 1.0f;

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
                Constants.LOG.info("Loaded {} config from {}, treeShake={}", Constants.MOD_NAME, CONFIG_PATH, enableTreeShake);
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
            Files.writeString(CONFIG_PATH, GSON.toJson(json));
            Constants.LOG.info("Saved {} config to {}, treeShake={}", Constants.MOD_NAME, CONFIG_PATH, enableTreeShake);
        } catch (Exception e) {
            Constants.LOG.error("Failed to save config to {}", CONFIG_PATH, e);
        }
    }
}
