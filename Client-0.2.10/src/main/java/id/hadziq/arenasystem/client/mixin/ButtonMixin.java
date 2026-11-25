package id.hadziq.arenasystem.client.mixin;

import id.hadziq.arenasystem.client.KeybindChordConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.InputWithModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Button.class)
abstract class ButtonMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void arenasystem$resetModifiersForGlobalButton(
        InputWithModifiers input,
        CallbackInfo callback
    ) {
        if (!(Minecraft.getInstance().gui.screen() instanceof KeyBindsScreen screen)) {
            return;
        }

        Button resetButton = ((KeyBindsScreenAccessor) screen).arenasystem$getResetButton();
        if (arenasystem$isGlobalResetButton(this, resetButton)) {
            KeybindChordConfig.resetToDefaults();
        }
    }

    @Unique
    private static boolean arenasystem$isGlobalResetButton(Object pressedButton, Object resetButton) {
        return resetButton != null && pressedButton == resetButton;
    }
}
