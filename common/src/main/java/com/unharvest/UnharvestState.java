package com.unharvest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages persistent player progression, currencies, upgrade tree graph,
 * and field clearance statistics for Unharvest.
 */
public class UnharvestState {

    private static final Path SAVE_PATH = Path.of("config", "unharvest_save.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final UnharvestState INSTANCE = new UnharvestState();

    // Currency and Progress
    private long essence = 0;
    private int totalWithered = 0;
    private int fieldTotalCrops = 9761;

    // Graph Nodes
    public enum NodeCategory {
        ROOT,
        SCYTHE,
        SPORES,
        SWARM
    }

    public static class UpgradeNode {
        public final String id;
        public final String title;
        public final String description;
        public final String icon;
        public final long cost;
        public final NodeCategory category;
        public final double gridX;
        public final double gridY;
        public final List<String> parents;
        public final boolean requireAllParents;

        public UpgradeNode(String id, String title, String description, String icon, long cost,
                           NodeCategory category, double gridX, double gridY,
                           boolean requireAllParents, String... parents) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.icon = icon;
            this.cost = cost;
            this.category = category;
            this.gridX = gridX;
            this.gridY = gridY;
            this.requireAllParents = requireAllParents;
            this.parents = Arrays.asList(parents);
        }
    }

    public static final Map<String, UpgradeNode> NODES = new LinkedHashMap<>();

    private static void registerNode(UpgradeNode node) {
        NODES.put(node.id, node);
    }

    static {
        // --- 1. ROOT ---
        registerNode(new UpgradeNode("skull", "Plague Nexus", "The origin of blight. Enables Withering Scythe reaping.",
                "💀", 0, NodeCategory.ROOT, 0, 0, false));

        // --- 2. SCYTHE TREE (Branching Upwards) ---
        // Tier 1
        registerNode(new UpgradeNode("scythe_speed_1", "Swift Sickle I", "Reduces scythe swing cooldown to 15 ticks.",
                "⌛", 10, NodeCategory.SCYTHE, -0.9, -1.0, false, "skull"));
        registerNode(new UpgradeNode("scythe_radius_1", "Wide Sweep I", "Expands withering radius to a 3x3 AoE.",
                "+", 25, NodeCategory.SCYTHE, 0.9, -1.0, false, "skull"));

        // Tier 2
        registerNode(new UpgradeNode("scythe_speed_2", "Swift Blade II", "Reduces scythe swing cooldown to 10 ticks.",
                "⌛", 50, NodeCategory.SCYTHE, -1.5, -2.0, false, "scythe_speed_1"));
        registerNode(new UpgradeNode("scythe_yield_1", "Essence Siphon I", "Grants 2x Blight Essence per withered crop.",
                "%", 80, NodeCategory.SCYTHE, 0.0, -2.0, false, "scythe_speed_1", "scythe_radius_1"));
        registerNode(new UpgradeNode("scythe_radius_2", "Wide Sweep II", "Expands withering radius to a 5x5 AoE.",
                "+", 125, NodeCategory.SCYTHE, 1.5, -2.0, false, "scythe_radius_1"));

        // Tier 3
        registerNode(new UpgradeNode("scythe_speed_3", "Frenzy Strike III", "Reduces scythe swing cooldown to 6 ticks.",
                "⌛", 200, NodeCategory.SCYTHE, -2.0, -3.0, false, "scythe_speed_2"));
        registerNode(new UpgradeNode("scythe_sweep_1", "Sickle Momentum I", "Increases harvest efficiency and swing speed.",
                "🗡️", 150, NodeCategory.SCYTHE, -0.9, -3.0, false, "scythe_speed_2", "scythe_yield_1"));
        registerNode(new UpgradeNode("scythe_yield_2", "Essence Siphon II", "Grants 3x Blight Essence per crop.",
                "%", 350, NodeCategory.SCYTHE, 0.0, -3.0, false, "scythe_yield_1"));
        registerNode(new UpgradeNode("scythe_sweep_2", "Sickle Momentum II", "Harvests crops in a broader arc.",
                "🗡️", 300, NodeCategory.SCYTHE, 0.9, -3.0, false, "scythe_radius_2", "scythe_yield_1"));
        registerNode(new UpgradeNode("scythe_radius_3", "Death Whirlwind III", "Expands withering radius to a 7x7 AoE.",
                "+", 500, NodeCategory.SCYTHE, 2.0, -3.0, false, "scythe_radius_2"));

        // Tier 4
        registerNode(new UpgradeNode("scythe_speed_4", "Flash Scythe IV", "Reduces scythe swing cooldown to 3 ticks.",
                "⌛", 750, NodeCategory.SCYTHE, -1.4, -4.0, false, "scythe_speed_3", "scythe_sweep_1"));
        registerNode(new UpgradeNode("scythe_yield_3", "Essence Siphon III", "Grants 5x Blight Essence per crop.",
                "%", 1200, NodeCategory.SCYTHE, 0.0, -4.0, false, "scythe_yield_2"));
        registerNode(new UpgradeNode("scythe_radius_4", "Cataclysmic Sweep IV", "Expands withering radius to a 9x9 AoE.",
                "+", 1800, NodeCategory.SCYTHE, 1.4, -4.0, false, "scythe_radius_3", "scythe_sweep_2"));

        // Apex Scythe
        registerNode(new UpgradeNode("scythe_mastery", "Grim Reaper Apex", "Mastery: 1-tick ultra speed & 10x Essence yield!",
                "👑", 4000, NodeCategory.SCYTHE, 0.0, -5.2, false, "scythe_speed_4", "scythe_yield_3", "scythe_radius_4"));

        // --- 3. SPORES / CONTAGION TREE (Branching Left - Crimson) ---
        registerNode(new UpgradeNode("spores_root", "Fungal Genesis", "Unlocks Spores: 25% chance for withering to cascade to adjacent crops.",
                "🍄", 100, NodeCategory.SPORES, -1.8, 0.0, false, "skull"));

        // Upper Spores Track (Chance)
        registerNode(new UpgradeNode("spores_chance_1", "Virulent Mold I", "Increases Contagion cascade chance to 45%.",
                "🍄", 300, NodeCategory.SPORES, -3.0, -0.7, false, "spores_root"));
        registerNode(new UpgradeNode("spores_chance_2", "Black Mold II", "Increases Contagion cascade chance to 65%.",
                "🍄", 900, NodeCategory.SPORES, -4.2, -0.7, false, "spores_chance_1"));

        // Lower Spores Track (Spread)
        registerNode(new UpgradeNode("spores_spread_1", "Spore Burst I", "Contagion cascade spreads 1 block further per wave.",
                "🍄", 250, NodeCategory.SPORES, -3.0, 0.7, false, "spores_root"));
        registerNode(new UpgradeNode("spores_spread_2", "Airborne Rot II", "Contagion cascade spreads 2 blocks further per wave.",
                "🍄", 750, NodeCategory.SPORES, -4.2, 0.7, false, "spores_spread_1"));

        // Converging Spores
        registerNode(new UpgradeNode("spores_pandemic", "Fungal Pandemic", "Increases Contagion chance to 85% with intense wave cascades.",
                "☣️", 2500, NodeCategory.SPORES, -5.4, 0.0, false, "spores_chance_2", "spores_spread_2"));
        // Apex Spores
        registerNode(new UpgradeNode("spores_master", "Primal Rot (Apex)", "Every withered crop detonates a secondary spore explosion.",
                "🍂", 5000, NodeCategory.SPORES, -6.6, 0.0, false, "spores_pandemic"));

        // --- 4. LOCUST SWARM TREE (Branching Right - Dark Moss) ---
        registerNode(new UpgradeNode("swarm_root", "Locust Brood", "Unlocks placeable Locust Swarm [R]! (20s cooldown, devours field for 7s)",
                "🦗", 150, NodeCategory.SWARM, 1.8, 0.0, false, "skull"));

        // Upper Track (Cooldown)
        registerNode(new UpgradeNode("swarm_cd_1", "Rapid Hatch I", "Reduces Locust Swarm cooldown from 20s to 16s.",
                "⏱️", 400, NodeCategory.SWARM, 3.0, -0.8, false, "swarm_root"));
        registerNode(new UpgradeNode("swarm_cd_2", "Constant Brood II", "Reduces Locust Swarm cooldown to 12s.",
                "⏱️", 1000, NodeCategory.SWARM, 4.2, -0.8, false, "swarm_cd_1"));
        registerNode(new UpgradeNode("swarm_cd_3", "Endless Swarm III", "Reduces Locust Swarm cooldown to 8s.",
                "⏱️", 2500, NodeCategory.SWARM, 5.4, -0.8, false, "swarm_cd_2"));

        // Middle Track (Devour Speed)
        registerNode(new UpgradeNode("swarm_speed_1", "Voracious Pests I", "Swarm devours crops 2x faster.",
                "🦗", 500, NodeCategory.SWARM, 3.6, 0.0, false, "swarm_root"));
        registerNode(new UpgradeNode("swarm_speed_2", "Devouring Swarm II", "Swarm devours crops 4x faster.",
                "🦗", 1500, NodeCategory.SWARM, 4.8, 0.0, false, "swarm_speed_1"));

        // Lower Track (Swarm Area)
        registerNode(new UpgradeNode("swarm_area_1", "Expanded Swarm I", "Increases Swarm devour radius to 7 blocks.",
                "+", 600, NodeCategory.SWARM, 3.0, 0.8, false, "swarm_root"));
        registerNode(new UpgradeNode("swarm_area_2", "Plague Cloud II", "Increases Swarm devour radius to 10 blocks.",
                "+", 1800, NodeCategory.SWARM, 4.2, 0.8, false, "swarm_area_1"));

        // Apex Swarm
        registerNode(new UpgradeNode("swarm_master", "Pestilence Horde (Apex)", "Summons a devastating rat & locust horde that consumes the field.",
                "🐀", 6000, NodeCategory.SWARM, 6.6, 0.0, false, "swarm_cd_3", "swarm_speed_2", "swarm_area_2"));
    }

    // Set of unlocked node IDs
    private final Set<String> unlockedNodes = new HashSet<>();
    private boolean victoryShown = false;

    public static UnharvestState getInstance() {
        return INSTANCE;
    }

    public UnharvestState() {
        unlockedNodes.add("skull");
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

    public synchronized void setTotalWithered(int count) {
        this.totalWithered = Math.min(fieldTotalCrops, Math.max(0, count));
        if (count == 0) {
            this.victoryShown = false;
        }
        save();
    }

    public synchronized boolean isVictoryShown() {
        return victoryShown;
    }

    public synchronized void setVictoryShown(boolean victoryShown) {
        this.victoryShown = victoryShown;
        save();
    }

    public synchronized boolean isFieldCompleted() {
        return fieldTotalCrops > 0 && totalWithered >= fieldTotalCrops;
    }

    public synchronized void setFieldStats(int totalCrops, int totalWithered) {
        this.fieldTotalCrops = Math.max(1, totalCrops);
        this.totalWithered = Math.min(this.fieldTotalCrops, Math.max(0, totalWithered));
        save();
    }

    public synchronized float getClearedPercentage() {
        if (fieldTotalCrops <= 0) return 0.0f;
        return Math.min(100.0f, ((float) totalWithered / (float) fieldTotalCrops) * 100.0f);
    }

    public String getFormattedEssence() {
        return String.format(Locale.US, "%,d", essence);
    }

    // --- GRAPH NODE LOGIC ---

    public synchronized boolean isNodeUnlocked(String id) {
        if ("skull".equals(id)) return true;
        return unlockedNodes.contains(id);
    }

    public synchronized boolean isNodeRevealed(String id) {
        if ("skull".equals(id)) return true;
        if (isNodeUnlocked(id)) return true;

        UpgradeNode node = NODES.get(id);
        if (node == null) return false;

        // Revealed only if at least one parent in the chain has been unlocked
        for (String parent : node.parents) {
            if (isNodeUnlocked(parent)) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean canUnlockNode(String id) {
        UpgradeNode node = NODES.get(id);
        if (node == null) return false;
        if (isNodeUnlocked(id)) return false;

        // Check prerequisites
        if (node.parents.isEmpty()) return true;

        if (node.requireAllParents) {
            for (String parent : node.parents) {
                if (!isNodeUnlocked(parent)) return false;
            }
        } else {
            boolean anyParentUnlocked = false;
            for (String parent : node.parents) {
                if (isNodeUnlocked(parent)) {
                    anyParentUnlocked = true;
                    break;
                }
            }
            if (!anyParentUnlocked) return false;
        }

        return this.essence >= node.cost;
    }

    public synchronized boolean hasAvailableUpgrade() {
        for (String id : NODES.keySet()) {
            if (canUnlockNode(id)) return true;
        }
        return false;
    }

    public synchronized boolean unlockNode(String id) {
        UpgradeNode node = NODES.get(id);
        if (node == null) return false;

        if (canUnlockNode(id)) {
            if (node.cost <= 0 || spendEssence(node.cost)) {
                unlockedNodes.add(id);
                save();
                return true;
            }
        }
        return false;
    }

    // --- GAMEPLAY STATS DERIVED FROM GRAPH ---

    public synchronized int getCooldownTicks() {
        if (isNodeUnlocked("scythe_mastery")) return 1;
        if (isNodeUnlocked("scythe_speed_4")) return 3;
        if (isNodeUnlocked("scythe_speed_3")) return 6;
        if (isNodeUnlocked("scythe_speed_2")) return 10;
        if (isNodeUnlocked("scythe_speed_1")) return 15;
        return 20;
    }

    public synchronized int getRadius() {
        if (isNodeUnlocked("scythe_radius_4")) return 4; // 9x9
        if (isNodeUnlocked("scythe_radius_3")) return 3; // 7x7
        if (isNodeUnlocked("scythe_radius_2")) return 2; // 5x5
        if (isNodeUnlocked("scythe_radius_1")) return 1; // 3x3
        return 0;
    }

    public synchronized int getYieldMultiplier() {
        int mult = 1;
        if (isNodeUnlocked("scythe_yield_1")) mult = 2;
        if (isNodeUnlocked("scythe_yield_2")) mult = 3;
        if (isNodeUnlocked("scythe_yield_3")) mult = 5;
        if (isNodeUnlocked("scythe_mastery")) mult = 10;
        return mult;
    }

    public synchronized float getContagionChance() {
        if (isNodeUnlocked("spores_master") || isNodeUnlocked("spores_pandemic")) return 0.85f;
        if (isNodeUnlocked("spores_chance_2")) return 0.65f;
        if (isNodeUnlocked("spores_chance_1")) return 0.45f;
        if (isNodeUnlocked("spores_root")) return 0.25f;
        return 0.0f;
    }

    public synchronized int getContagionCascadeSteps() {
        int steps = 3;
        if (isNodeUnlocked("spores_spread_1")) steps += 1;
        if (isNodeUnlocked("spores_spread_2")) steps += 2;
        if (isNodeUnlocked("spores_master")) steps += 3;
        return steps;
    }

    public synchronized boolean isSwarmUnlocked() {
        return isNodeUnlocked("swarm_root");
    }

    public synchronized int getSwarmCooldownTicks() {
        int cd = 400; // 20.0s
        if (isNodeUnlocked("swarm_cd_1")) cd -= 80;  // 16.0s
        if (isNodeUnlocked("swarm_cd_2")) cd -= 80;  // 12.0s
        if (isNodeUnlocked("swarm_cd_3")) cd -= 80;  // 8.0s
        if (isNodeUnlocked("swarm_master")) cd = 120; // 6.0s
        return Math.max(80, cd);
    }

    public synchronized int getSwarmRadius() {
        if (isNodeUnlocked("swarm_master")) return 12;
        if (isNodeUnlocked("swarm_area_2")) return 10;
        if (isNodeUnlocked("swarm_area_1")) return 7;
        return 5;
    }

    public synchronized int getSwarmDevourRate() {
        if (isNodeUnlocked("swarm_master")) return 8;
        if (isNodeUnlocked("swarm_speed_2")) return 5;
        if (isNodeUnlocked("swarm_speed_1")) return 3;
        return 2;
    }

    // --- RESET ---
    public synchronized void resetAll() {
        this.essence = 0;
        this.totalWithered = 0;
        this.victoryShown = false;
        this.unlockedNodes.clear();
        this.unlockedNodes.add("skull");
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
                if (json.has("victoryShown")) victoryShown = json.get("victoryShown").getAsBoolean();

                if (json.has("unlockedNodes")) {
                    JsonArray arr = json.getAsJsonArray("unlockedNodes");
                    unlockedNodes.clear();
                    unlockedNodes.add("skull");
                    for (JsonElement el : arr) {
                        unlockedNodes.add(el.getAsString());
                    }
                } else {
                    // Legacy migration
                    unlockedNodes.clear();
                    unlockedNodes.add("skull");
                    if (json.has("speedLevel")) {
                        int spd = json.get("speedLevel").getAsInt();
                        if (spd >= 1) unlockedNodes.add("scythe_speed_1");
                        if (spd >= 2) unlockedNodes.add("scythe_speed_2");
                        if (spd >= 3) unlockedNodes.add("scythe_speed_3");
                        if (spd >= 4) unlockedNodes.add("scythe_speed_4");
                        if (spd >= 5) unlockedNodes.add("scythe_mastery");
                    }
                    if (json.has("radiusLevel")) {
                        int rad = json.get("radiusLevel").getAsInt();
                        if (rad >= 1) unlockedNodes.add("scythe_radius_1");
                        if (rad >= 2) unlockedNodes.add("scythe_radius_2");
                        if (rad >= 3) unlockedNodes.add("scythe_radius_3");
                        if (rad >= 4) unlockedNodes.add("scythe_radius_4");
                    }
                    if (json.has("yieldLevel")) {
                        int yld = json.get("yieldLevel").getAsInt();
                        if (yld >= 1) unlockedNodes.add("scythe_yield_1");
                        if (yld >= 2) unlockedNodes.add("scythe_yield_2");
                        if (yld >= 3) unlockedNodes.add("scythe_yield_3");
                    }
                    if (json.has("chainLevel") && json.get("chainLevel").getAsInt() >= 1) {
                        unlockedNodes.add("spores_root");
                    }
                    if (json.has("swarmLevel") && json.get("swarmLevel").getAsInt() >= 1) {
                        unlockedNodes.add("swarm_root");
                    }
                }
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
            json.addProperty("victoryShown", victoryShown);

            JsonArray arr = new JsonArray();
            for (String node : unlockedNodes) {
                arr.add(node);
            }
            json.add("unlockedNodes", arr);

            Files.writeString(SAVE_PATH, GSON.toJson(json));
        } catch (Exception e) {
            Constants.LOG.error("Failed to save Unharvest save data", e);
        }
    }
}
