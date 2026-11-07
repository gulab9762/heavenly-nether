package com.example.modtemplate.neoforge;

import com.example.modtemplate.Constants;
import com.example.modtemplate.ModTemplateCommon;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class ModTemplateNeoForge {
    public ModTemplateNeoForge(IEventBus modBus) {
        ModTemplateCommon.init();
    }
}
