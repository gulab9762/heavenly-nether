package com.gulab.jrunner.fabric;

import com.gulab.jrunner.Constants;
import com.gulab.jrunner.JavaRunnerCommon;
import com.gulab.jrunner.command.JavaRunnerCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class JavaRunnerFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        JavaRunnerCommon.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            JavaRunnerCommands.register(dispatcher);
            Constants.LOG.info("[Java Runner Fabric] Registered live code execution commands (/jexec, /jfile, /jlist, /jhelp)");
        });
    }
}
