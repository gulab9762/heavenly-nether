package com.unharvest.mixin;

import com.unharvest.UnharvestManager;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {

    @Shadow
    public abstract WorldCreationUiState getUiState();

    @Inject(
            method = "onCreate",
            at = @At("HEAD")
    )
    private void unharvest$onCreate(CallbackInfo ci) {
        WorldCreationUiState uiState = this.getUiState();
        if (uiState != null && uiState.getWorldType() != null && uiState.getWorldType().preset() != null) {
            boolean isUnharvest = uiState.getWorldType().preset().is(Identifier.fromNamespaceAndPath("unharvest", "unharvest_field"));
            if (isUnharvest) {
                uiState.setDifficulty(Difficulty.PEACEFUL);
                UnharvestManager.markNextWorldAsUnharvest();
            }
        }
    }
}

