package com.gulab.jrunner.neoforge;

import com.gulab.jrunner.Constants;
import com.gulab.jrunner.JavaRunnerCommon;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class JavaRunnerNeoForge {
    public JavaRunnerNeoForge(IEventBus modBus) {
        JavaRunnerCommon.init();
    }
}
