package com.unharvest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages persistent best harvest time records per player UUID.
 * Saved globally in config/unharvest_records.json.
 */
public class UnharvestRecords {

    private static final Path CONFIG_PATH = Path.of("config", "unharvest_records.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, PlayerRecord> RECORDS = new HashMap<>();
    private static boolean loaded = false;

    public static class PlayerRecord {
        public String uuid;
        public String playerName;
        public long bestTimeMs;
        public String formattedTime;
        public String completionDate;
        public int cropsWithered;

        public PlayerRecord(String uuid, String playerName, long bestTimeMs, String formattedTime, String completionDate, int cropsWithered) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.bestTimeMs = bestTimeMs;
            this.formattedTime = formattedTime;
            this.completionDate = completionDate;
            this.cropsWithered = cropsWithered;
        }
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        RECORDS.clear();

        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        try {
            String content = Files.readString(CONFIG_PATH);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (json.has("records")) {
                JsonObject recordsObj = json.getAsJsonObject("records");
                for (Map.Entry<String, JsonElement> entry : recordsObj.entrySet()) {
                    JsonObject rec = entry.getValue().getAsJsonObject();
                    String uuid = entry.getKey();
                    String name = rec.has("playerName") ? rec.get("playerName").getAsString() : "Player";
                    long timeMs = rec.has("bestTimeMs") ? rec.get("bestTimeMs").getAsLong() : -1;
                    String formatted = rec.has("formattedTime") ? rec.get("formattedTime").getAsString() : UnharvestTimer.formatTimer(timeMs);
                    String date = rec.has("completionDate") ? rec.get("completionDate").getAsString() : "";
                    int crops = rec.has("cropsWithered") ? rec.get("cropsWithered").getAsInt() : 0;
                    RECORDS.put(uuid, new PlayerRecord(uuid, name, timeMs, formatted, date, crops));
                }
            }
        } catch (Exception e) {
            Constants.LOG.error("[Unharvest] Failed to load personal best records", e);
        }
    }

    public static synchronized void save() {
        try {
            if (CONFIG_PATH.getParent() != null && !Files.exists(CONFIG_PATH.getParent())) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }

            JsonObject root = new JsonObject();
            JsonObject recordsObj = new JsonObject();
            for (Map.Entry<String, PlayerRecord> entry : RECORDS.entrySet()) {
                PlayerRecord r = entry.getValue();
                JsonObject rec = new JsonObject();
                rec.addProperty("uuid", r.uuid);
                rec.addProperty("playerName", r.playerName);
                rec.addProperty("bestTimeMs", r.bestTimeMs);
                rec.addProperty("formattedTime", r.formattedTime);
                rec.addProperty("completionDate", r.completionDate);
                rec.addProperty("cropsWithered", r.cropsWithered);
                recordsObj.add(entry.getKey(), rec);
            }
            root.add("records", recordsObj);

            Files.writeString(CONFIG_PATH, GSON.toJson(root));
        } catch (Exception e) {
            Constants.LOG.error("[Unharvest] Failed to save personal best records", e);
        }
    }

    public static synchronized Long getBestTimeMs(UUID uuid) {
        if (uuid == null) return null;
        load();
        PlayerRecord rec = RECORDS.get(uuid.toString());
        return (rec != null) ? rec.bestTimeMs : null;
    }

    public static synchronized String getFormattedBest(UUID uuid) {
        if (uuid == null) return "--:--:---";
        load();
        PlayerRecord rec = RECORDS.get(uuid.toString());
        if (rec != null && rec.bestTimeMs > 0) {
            return rec.formattedTime != null ? rec.formattedTime : UnharvestTimer.formatTimer(rec.bestTimeMs);
        }
        return "--:--:---";
    }

    public static synchronized boolean recordCompletion(UUID uuid, String playerName, long timeMs, int crops) {
        if (uuid == null || timeMs <= 0) return false;
        load();

        String uuidStr = uuid.toString();
        PlayerRecord current = RECORDS.get(uuidStr);

        boolean isNewBest = (current == null || timeMs < current.bestTimeMs);
        if (isNewBest) {
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String formatted = UnharvestTimer.formatTimer(timeMs);
            RECORDS.put(uuidStr, new PlayerRecord(uuidStr, playerName, timeMs, formatted, dateStr, crops));
            save();
            Constants.LOG.info("[Unharvest] New Personal Best for {} ({}): {}", playerName, uuidStr, formatted);
            return true;
        }
        return false;
    }
}
