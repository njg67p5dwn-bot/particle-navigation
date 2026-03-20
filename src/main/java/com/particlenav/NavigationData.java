package com.particlenav;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists navigation data (marked position, target) per world as JSON.
 * Storage: <game_dir>/particle-navigation/<world_id>.json
 */
public class NavigationData {

    private static final Logger LOGGER = LoggerFactory.getLogger("particle-navigation");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path getDataDir() {
        return FabricLoader.getInstance().getGameDir().resolve("particle-navigation");
    }

    private static Path getDataFile(String worldId) {
        return getDataDir().resolve(worldId + ".json");
    }

    /**
     * Sanitize world name to a safe filename.
     * Keeps Unicode letters/digits (including Korean), replaces unsafe chars.
     */
    public static String sanitizeWorldId(String raw) {
        String sanitized = raw.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (sanitized.isBlank()) sanitized = "unknown";
        return sanitized;
    }

    public static void save(String worldId, BlockPos markedPos, BlockPos target) {
        try {
            Path dir = getDataDir();
            Files.createDirectories(dir);

            JsonObject json = new JsonObject();
            if (markedPos != null) {
                JsonObject mark = new JsonObject();
                mark.addProperty("x", markedPos.getX());
                mark.addProperty("y", markedPos.getY());
                mark.addProperty("z", markedPos.getZ());
                json.add("markedPos", mark);
            }
            if (target != null) {
                JsonObject tgt = new JsonObject();
                tgt.addProperty("x", target.getX());
                tgt.addProperty("y", target.getY());
                tgt.addProperty("z", target.getZ());
                json.add("target", tgt);
            }

            Files.writeString(getDataFile(worldId), GSON.toJson(json));
        } catch (IOException e) {
            LOGGER.error("[ParticleNav] Failed to save navigation data", e);
        }
    }

    public static LoadedData load(String worldId) {
        Path file = getDataFile(worldId);
        if (!Files.exists(file)) return new LoadedData(null, null);

        try {
            String content = Files.readString(file);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            BlockPos markedPos = parseBlockPos(json, "markedPos");
            BlockPos target = parseBlockPos(json, "target");
            return new LoadedData(markedPos, target);
        } catch (Exception e) {
            LOGGER.error("[ParticleNav] Failed to load navigation data", e);
            return new LoadedData(null, null);
        }
    }

    private static BlockPos parseBlockPos(JsonObject json, String key) {
        if (!json.has(key)) return null;
        JsonObject obj = json.getAsJsonObject(key);
        return new BlockPos(
                obj.get("x").getAsInt(),
                obj.get("y").getAsInt(),
                obj.get("z").getAsInt()
        );
    }

    public record LoadedData(BlockPos markedPos, BlockPos target) {}
}
