package com.blockplace.client.config;

import com.blockplace.Constants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;

public class BlockPlaceConfig {

    public enum AnimationStyle {
        SMOOTH,
        ROTATING,
        POP,
        DROP,
        SPIRAL,
        EXPAND,
        SPRING,
        NONE
    }

    private static final Path CONFIG_PATH = Path.of("config", "blockplace.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Currently active placement animation style.
     */
    public static AnimationStyle style = AnimationStyle.ROTATING;

    /**
     * Duration of the travel animation in ticks (20 ticks = 1 second).
     * 6 ticks = 300 milliseconds.
     */
    public static int durationTicks = 6;

    /**
     * Initial scale of the block when leaving the player's hand.
     */
    public static float startScale = 0.28f;

    /**
     * Height of the travel arc in blocks.
     */
    public static float arcHeight = 0.05f;

    /**
     * Initial rotation tilt from hand angle in degrees (used in SMOOTH style).
     */
    public static float initialTiltDegrees = 20.0f;

    /**
     * Subtle bounce/squash effect on block arrival.
     */
    public static boolean enableSettleBounce = true;

    /**
     * Subtle particle poof on block landing.
     */
    public static boolean enableArrivalParticles = true;

    /**
     * Whether to apply the animation only in first-person perspective.
     */
    public static boolean firstPersonOnly = true;

    /**
     * Whether cartoon tree punch shake animations are enabled.
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
                if (json.has("style")) {
                    try {
                        String savedStyle = json.get("style").getAsString();
                        setStyle(savedStyle);
                    } catch (Exception ignored) {}
                }
                if (json.has("durationTicks")) durationTicks = json.get("durationTicks").getAsInt();
                if (json.has("startScale")) startScale = json.get("startScale").getAsFloat();
                if (json.has("arcHeight")) arcHeight = json.get("arcHeight").getAsFloat();
                if (json.has("enableSettleBounce")) enableSettleBounce = json.get("enableSettleBounce").getAsBoolean();
                if (json.has("enableArrivalParticles")) enableArrivalParticles = json.get("enableArrivalParticles").getAsBoolean();
                if (json.has("enableTreeShake")) enableTreeShake = json.get("enableTreeShake").getAsBoolean();
                if (json.has("treeShakeIntensity")) treeShakeIntensity = json.get("treeShakeIntensity").getAsFloat();
                if (json.has("treeShakeDurationTicks")) treeShakeDurationTicks = json.get("treeShakeDurationTicks").getAsInt();
                if (json.has("enableTreeParticles")) enableTreeParticles = json.get("enableTreeParticles").getAsBoolean();
                if (json.has("enableTreeSound")) enableTreeSound = json.get("enableTreeSound").getAsBoolean();
                Constants.LOG.info("Loaded {} config from {}, style={}, treeShake={}", Constants.MOD_NAME, CONFIG_PATH, style, enableTreeShake);
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
            json.addProperty("style", style.name().toLowerCase());
            json.addProperty("durationTicks", durationTicks);
            json.addProperty("startScale", startScale);
            json.addProperty("arcHeight", arcHeight);
            json.addProperty("enableSettleBounce", enableSettleBounce);
            json.addProperty("enableArrivalParticles", enableArrivalParticles);
            json.addProperty("enableTreeShake", enableTreeShake);
            json.addProperty("treeShakeIntensity", treeShakeIntensity);
            json.addProperty("treeShakeDurationTicks", treeShakeDurationTicks);
            json.addProperty("enableTreeParticles", enableTreeParticles);
            json.addProperty("enableTreeSound", enableTreeSound);
            Files.writeString(CONFIG_PATH, GSON.toJson(json));
            Constants.LOG.info("Saved {} config to {}, style={}, treeShake={}", Constants.MOD_NAME, CONFIG_PATH, style, enableTreeShake);
        } catch (Exception e) {
            Constants.LOG.error("Failed to save config to {}", CONFIG_PATH, e);
        }
    }

    /**
     * Sets the active animation style and persists it immediately to file.
     * Supports "smooth", "rotating", "pop", "drop", "spiral", "expand", "spring", "none" (or "vanilla"/"off").
     *
     * @param styleName style name
     * @return true if valid style was set, false otherwise
     */
    public static boolean setStyle(String styleName) {
        if (styleName.equalsIgnoreCase("vanilla") || styleName.equalsIgnoreCase("off") || styleName.equalsIgnoreCase("disabled")) {
            style = AnimationStyle.NONE;
            save();
            return true;
        }
        for (AnimationStyle s : AnimationStyle.values()) {
            if (s.name().equalsIgnoreCase(styleName)) {
                style = s;
                save();
                return true;
            }
        }
        return false;
    }
}
