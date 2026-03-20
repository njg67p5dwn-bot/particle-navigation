package com.particlenav;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point for the Particle Navigation mod.
 */
public class ParticleNavClient implements ClientModInitializer {

    public static final String MOD_ID = "particle-navigation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static boolean pendingWorldJoin = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ParticleNav] Initializing Particle Navigation mod");

        NavCommand.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingWorldJoin && client.level != null) {
                pendingWorldJoin = false;
                String worldId = getWorldId(client);
                NavigationManager.getInstance().onWorldJoin(worldId);
                LOGGER.info("[ParticleNav] World joined, loaded data for: {}", worldId);
            }
            NavigationManager.getInstance().tick();
        });

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            NavigationManager.getInstance().onChunkLoad(
                    chunk.getPos().x, chunk.getPos().z);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Delay by 1 tick to ensure IntegratedServer is fully available
            pendingWorldJoin = true;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pendingWorldJoin = false;
            NavigationManager.getInstance().onWorldLeave();
            LOGGER.info("[ParticleNav] World left, data saved.");
        });

        LOGGER.info("[ParticleNav] Mod initialized. Use /nav set <x> <y> <z> to navigate.");
    }

    private static String getWorldId(Minecraft client) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server != null) {
            return NavigationData.sanitizeWorldId(server.getWorldData().getLevelName());
        }
        if (client.getCurrentServer() != null) {
            return NavigationData.sanitizeWorldId(client.getCurrentServer().ip);
        }
        return "unknown";
    }
}
