package com.treeshake.fabric;

import com.treeshake.TreeShakeCommon;
import net.fabricmc.api.ModInitializer;

public class TreeShakeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        TreeShakeCommon.init();
    }
}
