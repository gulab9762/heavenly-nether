package com.example.modtemplate.fabric;

import com.example.modtemplate.ModTemplateCommon;
import net.fabricmc.api.ModInitializer;

public class ModTemplateFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ModTemplateCommon.init();
    }
}
