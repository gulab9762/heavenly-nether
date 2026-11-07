package com.gulab.jrunner.fabric;

import com.gulab.jrunner.JavaRunnerCommon;
import net.fabricmc.api.ModInitializer;

public class JavaRunnerFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        JavaRunnerCommon.init();
    }
}
