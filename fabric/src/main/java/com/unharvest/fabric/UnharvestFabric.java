package com.unharvest.fabric;

import com.unharvest.UnharvestCommon;
import net.fabricmc.api.ModInitializer;

public class UnharvestFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        UnharvestCommon.init();
    }
}
