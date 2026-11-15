package com.igxxd.structentity.fabric;

import com.igxxd.structentity.Constants;
import com.igxxd.structentity.command.GridCommands;
import com.igxxd.structentity.generator.GridGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class StructEntityFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Constants.LOG.info("Initializing {} for Fabric...", Constants.MOD_NAME);

        // Register /grid commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            GridCommands.register(dispatcher);
            Constants.LOG.info("[StructEntity] Registered /grid commands.");
        });

        // Register incremental generation tick handler
        ServerTickEvents.END_SERVER_TICK.register(GridGenerator::onServerTick);
    }
}
