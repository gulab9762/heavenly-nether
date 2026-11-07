package com.example.modtemplate.forge;

import com.example.modtemplate.Constants;
import com.example.modtemplate.ModTemplateCommon;
import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class ModTemplateForge {
    public ModTemplateForge() {
        ModTemplateCommon.init();
    }
}
