package com.particlenav;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point for the Particle Navigation mod.
 */
public class ParticleNavClient implements ClientModInitializer {

    public static final String MOD_ID = "particle-navigation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ParticleNav] Initializing Particle Navigation mod");

        NavCommand.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            NavigationManager.getInstance().tick();
        });

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            NavigationManager.getInstance().onChunkLoad(
                    chunk.getPos().x, chunk.getPos().z);
        });

        LOGGER.info("[ParticleNav] Mod initialized. Use /nav set <x> <y> <z> to navigate.");
    }
}
