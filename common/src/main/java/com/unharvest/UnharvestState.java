package com.unharvest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.treeshake.Constants;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages persistent player progression, currencies, upgrade levels,
 * and field clearance statistics for Unharvest.
 */
public class UnharvestState {

    private static final Path SAVE_PATH = Path.of("config", "unharvest_save.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final UnharvestState INSTANCE = new UnharvestState();

    // Currency and Progress
    private long essence = 0;
    private int totalWithered = 0;
    private int fieldTotalCrops = 3500; // Updated dynamically from generated village field

    // Upgrades
    private int speedLevel = 0;     // Max 5: Scythe swing cooldown
    private int radiusLevel = 0;    // Max 4: Wither AoE spread
    private int yieldLevel = 0;     // Max 4: Essence multiplier per crop
    private int chainLevel = 0;     // Max 4: Contagion (Spores) chain-reaction chance
    private int swarmLevel = 0;     // Max 4: Locust Swarm idle auto-withering

    public static UnharvestState getInstance() {
        return INSTANCE;
    }

    public UnharvestState() {
        load();
    }

    public synchronized long getEssence() {
        return essence;
    }

    public synchronized void addEssence(long amount) {
        this.essence = Math.max(0, this.essence + amount);
        save();
    }

    public synchronized boolean spendEssence(long cost) {
        if (cost <= 0) return false;
        if (this.essence >= cost) {
            this.essence -= cost;
            save();
            return true;
        }
        return false;
    }

    public synchronized int getTotalWithered() {
        return totalWithered;
    }

    public synchronized void recordWithered(int count) {
        this.totalWithered = Math.min(fieldTotalCrops, this.totalWithered + count);
        save();
    }

    public synchronized int getFieldTotalCrops() {
        return fieldTotalCrops;
    }

    public synchronized void setFieldTotalCrops(int count) {
        if (count > 0) {
            this.fieldTotalCrops = count;
            save();
        }
    }

    public synchronized float getClearedPercentage() {
        if (fieldTotalCrops <= 0) return 0.0f;
        return Math.min(100.0f, ((float) totalWithered / (float) fieldTotalCrops) * 100.0f);
    }

    public String getFormattedEssence() {
        if (essence >= 1_000_000) {
            return String.format("%.1fM", essence / 1_000_000.0);
        } else if (essence >= 1_000) {
            return String.format("%.1fK", essence / 1_000.0);
        }
        return String.valueOf(essence);
    }

    // --- UPGRADE STATS & COSTS (Balanced as per original Unharvest game) ---

    // 1. Scythe Speed (Cooldown Ticks: 20 -> 15 -> 10 -> 6 -> 3 -> 1)
    public int getSpeedLevel() { return speedLevel; }
    public int getMaxSpeedLevel() { return 5; }
    public int getCooldownTicks() {
        return switch (speedLevel) {
            case 1 -> 15;
            case 2 -> 10;
            case 3 -> 6;
            case 4 -> 3;
            case 5 -> 1;
            default -> 20;
        };
    }
    public long getSpeedCost() {
        return switch (speedLevel) {
            case 0 -> 10;
            case 1 -> 50;
            case 2 -> 200;
            case 3 -> 750;
            case 4 -> 2500;
            default -> -1; // Maxed
        };
    }
    public boolean upgradeSpeed() {
        if (speedLevel < getMaxSpeedLevel() && spendEssence(getSpeedCost())) {
            speedLevel++;
            save();
            return true;
        }
        return false;
    }

    // 2. Wither Radius (AoE Box: 0 -> 1 (3x3) -> 2 (5x5) -> 3 (7x7) -> 4 (9x9))
    public int getRadiusLevel() { return radiusLevel; }
    public int getMaxRadiusLevel() { return 4; }
    public int getRadius() { return radiusLevel; }
    public long getRadiusCost() {
        return switch (radiusLevel) {
            case 0 -> 25;
            case 1 -> 125;
            case 2 -> 500;
            case 3 -> 1800;
            default -> -1;
        };
    }
    public boolean upgradeRadius() {
        if (radiusLevel < getMaxRadiusLevel() && spendEssence(getRadiusCost())) {
            radiusLevel++;
            save();
            return true;
        }
        return false;
    }

    // 3. Essence Multiplier (Yield: 1x -> 2x -> 3x -> 5x -> 10x)
    public int getYieldLevel() { return yieldLevel; }
    public int getMaxYieldLevel() { return 4; }
    public int getYieldMultiplier() {
        return switch (yieldLevel) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 5;
            case 4 -> 10;
            default -> 1;
        };
    }
    public long getYieldCost() {
        return switch (yieldLevel) {
            case 0 -> 15;
            case 1 -> 80;
            case 2 -> 350;
            case 3 -> 1200;
            default -> -1;
        };
    }
    public boolean upgradeYield() {
        if (yieldLevel < getMaxYieldLevel() && spendEssence(getYieldCost())) {
            yieldLevel++;
            save();
            return true;
        }
        return false;
    }

    // 4. Contagion / Spores (Chain Reaction: 0% -> 25% -> 45% -> 65% -> 85%)
    // In Unharvest, Spores is unlocked for 100 points
    public int getChainLevel() { return chainLevel; }
    public int getMaxChainLevel() { return 4; }
    public float getContagionChance() {
        return switch (chainLevel) {
            case 1 -> 0.25f;
            case 2 -> 0.45f;
            case 3 -> 0.65f;
            case 4 -> 0.85f;
            default -> 0.0f;
        };
    }
    public long getChainCost() {
        return switch (chainLevel) {
            case 0 -> 100;
            case 1 -> 300;
            case 2 -> 900;
            case 3 -> 2500;
            default -> -1;
        };
    }
    public boolean upgradeChain() {
        if (chainLevel < getMaxChainLevel() && spendEssence(getChainCost())) {
            chainLevel++;
            save();
            return true;
        }
        return false;
    }

    // 5. Locust Swarm (Idle Automation: 0 -> 1/2s -> 2/1.5s -> 4/1s -> 8/0.5s)
    public int getSwarmLevel() { return swarmLevel; }
    public int getMaxSwarmLevel() { return 4; }
    public int getSwarmPeriodTicks() {
        return switch (swarmLevel) {
            case 1 -> 40; // 2.0s
            case 2 -> 30; // 1.5s
            case 3 -> 20; // 1.0s
            case 4 -> 10; // 0.5s
            default -> Integer.MAX_VALUE;
        };
    }
    public int getSwarmWitherCount() {
        return switch (swarmLevel) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 4;
            case 4 -> 8;
            default -> 0;
        };
    }
    public long getSwarmCost() {
        return switch (swarmLevel) {
            case 0 -> 250;
            case 1 -> 800;
            case 2 -> 2400;
            case 3 -> 6000;
            default -> -1;
        };
    }
    public boolean upgradeSwarm() {
        if (swarmLevel < getMaxSwarmLevel() && spendEssence(getSwarmCost())) {
            swarmLevel++;
            save();
            return true;
        }
        return false;
    }

    // --- RESET ---
    public synchronized void resetAll() {
        this.essence = 0;
        this.totalWithered = 0;
        this.speedLevel = 0;
        this.radiusLevel = 0;
        this.yieldLevel = 0;
        this.chainLevel = 0;
        this.swarmLevel = 0;
        save();
    }

    // --- PERSISTENCE ---
    public synchronized void load() {
        try {
            if (Files.exists(SAVE_PATH)) {
                String content = Files.readString(SAVE_PATH);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("essence")) essence = json.get("essence").getAsLong();
                if (json.has("totalWithered")) totalWithered = json.get("totalWithered").getAsInt();
                if (json.has("fieldTotalCrops")) fieldTotalCrops = json.get("fieldTotalCrops").getAsInt();
                if (json.has("speedLevel")) speedLevel = json.get("speedLevel").getAsInt();
                if (json.has("radiusLevel")) radiusLevel = json.get("radiusLevel").getAsInt();
                if (json.has("yieldLevel")) yieldLevel = json.get("yieldLevel").getAsInt();
                if (json.has("chainLevel")) chainLevel = json.get("chainLevel").getAsInt();
                if (json.has("swarmLevel")) swarmLevel = json.get("swarmLevel").getAsInt();
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to load Unharvest save data", e);
        }
    }

    public synchronized void save() {
        try {
            if (SAVE_PATH.getParent() != null) {
                Files.createDirectories(SAVE_PATH.getParent());
            }
            JsonObject json = new JsonObject();
            json.addProperty("essence", essence);
            json.addProperty("totalWithered", totalWithered);
            json.addProperty("fieldTotalCrops", fieldTotalCrops);
            json.addProperty("speedLevel", speedLevel);
            json.addProperty("radiusLevel", radiusLevel);
            json.addProperty("yieldLevel", yieldLevel);
            json.addProperty("chainLevel", chainLevel);
            json.addProperty("swarmLevel", swarmLevel);
            Files.writeString(SAVE_PATH, GSON.toJson(json));
        } catch (Exception e) {
            Constants.LOG.error("Failed to save Unharvest save data", e);
        }
    }
}
