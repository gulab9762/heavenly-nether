package com.gulab.jrunner.forge;

import com.gulab.jrunner.Constants;
import com.gulab.jrunner.JavaRunnerCommon;
import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class JavaRunnerForge {
    public JavaRunnerForge() {
        JavaRunnerCommon.init();
    }
}
