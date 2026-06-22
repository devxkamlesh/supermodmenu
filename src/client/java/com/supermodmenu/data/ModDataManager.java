package com.supermodmenu.data;

import com.google.gson.*;
import com.supermodmenu.SuperModMenuClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Persistent per-mod user data: favorites and personal notes.
 *
 * Deliberately small — this is the only state Super Mod Menu stores on disk.
 */
public class ModDataManager {

    private static final Path DATA_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("supermodmenu").resolve("mod_data.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // modId -> ModUserData
    private static final Map<String, ModUserData> dataMap = new HashMap<>();

    public static void init() {
        try {
            Files.createDirectories(DATA_FILE.getParent());
        } catch (IOException e) {
            SuperModMenuClient.LOGGER.error("Failed to create config dir", e);
        }
        load();
    }

    // ── Favorites ────────────────────────────────────────────────────────────
    public static boolean isFavorite(String modId) {
        return getOrCreate(modId).favorite;
    }

    public static void setFavorite(String modId, boolean value) {
        getOrCreate(modId).favorite = value;
        save();
    }

    public static void toggleFavorite(String modId) {
        setFavorite(modId, !isFavorite(modId));
    }

    // ── Notes ──────────────────────────────────────────────────────────────--
    public static String getNote(String modId) {
        return getOrCreate(modId).note;
    }

    public static void setNote(String modId, String note) {
        getOrCreate(modId).note = note == null ? "" : note;
        save();
    }

    // ── Persistence ──────────────────────────────────────────────────────────
    private static ModUserData getOrCreate(String modId) {
        return dataMap.computeIfAbsent(modId, id -> new ModUserData());
    }

    public static void save() {
        try {
            Files.createDirectories(DATA_FILE.getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<String, ModUserData> entry : dataMap.entrySet()) {
                ModUserData d = entry.getValue();
                if (!d.favorite && d.note.isBlank()) continue; // don't persist empty records
                JsonObject obj = new JsonObject();
                obj.addProperty("favorite", d.favorite);
                obj.addProperty("note", d.note);
                root.add(entry.getKey(), obj);
            }
            Files.writeString(DATA_FILE, GSON.toJson(root));
        } catch (IOException e) {
            SuperModMenuClient.LOGGER.error("Failed to save mod data", e);
        }
    }

    private static void load() {
        if (!Files.exists(DATA_FILE)) return;
        try {
            String json = Files.readString(DATA_FILE);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject obj = entry.getValue().getAsJsonObject();
                ModUserData data = new ModUserData();
                if (obj.has("favorite")) data.favorite = obj.get("favorite").getAsBoolean();
                if (obj.has("note"))     data.note     = obj.get("note").getAsString();
                dataMap.put(entry.getKey(), data);
            }
        } catch (IOException | JsonParseException e) {
            SuperModMenuClient.LOGGER.error("Failed to load mod data", e);
        }
    }

    public static class ModUserData {
        public boolean favorite = false;
        public String  note     = "";
    }
}
