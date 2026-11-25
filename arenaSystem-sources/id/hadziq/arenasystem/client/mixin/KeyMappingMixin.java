package id.hadziq.arenasystem.client.mixin;

import com.mojang.blaze3d.platform.InputConstants.Key;
import id.hadziq.arenasystem.client.ArenaSystemKeybinds;
import id.hadziq.arenasystem.client.KeybindChordConfig;
import id.hadziq.arenasystem.client.KeybindModifierMatcher;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// $VF: Compiled from KeyMappingMixin.java
@Mixin(KeyMapping.class)
abstract class KeyMappingMixin {
    @Shadow
    public abstract String getName();

    @Inject(method = "click", at = @At("HEAD"))
    private static void arenasystem$captureRuntimeModifiers(Key key, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        int modifiers = KeybindModifierMatcher.modifiersFromInput(
            client.hasShiftDown(), client.hasAltDown(), client.hasControlDown()
        );
        ArenaSystemKeybinds.captureModifiersForKey(key, modifiers);
    }

    @Inject(method = "getTranslatedKeyMessage", at = @At("RETURN"), cancellable = true)
    private void arenasystem$appendModifierLabels(CallbackInfoReturnable<Component> callback) {
        int modifiers = KeybindChordConfig.getRequiredModifiers(this.getName());
        if (KeybindChordConfig.isManaged(this.getName()) && modifiers != 0) {
            MutableComponent display = Component.empty();
            boolean hasPrevious = false;
            if ((modifiers & 4) != 0) {
                display.append(Component.translatable("key.arstm_client0qb201.modifier.control"));
                hasPrevious = true;
            }

            if ((modifiers & 1) != 0) {
                if (hasPrevious) {
                    display.append(Component.literal(" + "));
                }

                display.append(Component.translatable("key.arstm_client0qb201.modifier.shift"));
                hasPrevious = true;
            }

            if ((modifiers & 2) != 0) {
                if (hasPrevious) {
                    display.append(Component.literal(" + "));
                }

                display.append(Component.translatable("key.arstm_client0qb201.modifier.alt"));
            }

            display.append(Component.literal(" + ")).append((Component)callback.getReturnValue());
            callback.setReturnValue(display);
        }
    }

    @Inject(method = "resetMapping", at = @At("HEAD"))
    private static void arenasystem$resetModifierDefaults(CallbackInfo callback) {
        if (Minecraft.getInstance().gui.screen() instanceof KeyBindsScreen) {
            KeybindChordConfig.resetToDefaults();
        }
    }
}
