package id.hadziq.arenasystem.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import id.hadziq.arenasystem.client.KeybindChordConfig;
import id.hadziq.arenasystem.client.KeybindModifierMatcher;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// $VF: Compiled from KeyBindsScreenMixin.java
@Mixin(KeyBindsScreen.class)
abstract class KeyBindsScreenMixin {
    @Unique
    private KeyMapping arenasystem$pendingMapping;
    @Unique
    private int arenasystem$pendingModifiers;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void arenasystem$captureModifierKeys(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        KeyMapping selected = ((KeyBindsScreenAccessor)this).arenasystem$getSelectedKey();
        if (selected != null && KeybindChordConfig.isManaged(selected.getName())) {
            Key pressedKey = InputConstants.getKey(event);
            if (KeybindModifierMatcher.isModifierKey(pressedKey.getName())) {
                callback.setReturnValue(true);
            } else {
                this.arenasystem$pendingMapping = selected;
                this.arenasystem$pendingModifiers = event.isEscape()
                    ? 0
                    : KeybindModifierMatcher.modifiersFromInput(
                        event.hasShiftDown(), event.hasAltDown(), event.hasControlDown()
                    );
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("TAIL"))
    private void arenasystem$applyKeyboardModifiers(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        this.arenasystem$commitPendingModifiers();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void arenasystem$captureMouseModifiers(
        MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> callback
    ) {
        KeyMapping selected = ((KeyBindsScreenAccessor)this).arenasystem$getSelectedKey();
        if (selected != null && KeybindChordConfig.isManaged(selected.getName())) {
            this.arenasystem$pendingMapping = selected;
            this.arenasystem$pendingModifiers = KeybindModifierMatcher.modifiersFromInput(
                event.hasShiftDown(), event.hasAltDown(), event.hasControlDown()
            );
        }
    }

    @Inject(method = "mouseClicked", at = @At("TAIL"))
    private void arenasystem$applyMouseModifiers(
        MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> callback
    ) {
        this.arenasystem$commitPendingModifiers();
    }

    @Unique
    private void arenasystem$commitPendingModifiers() {
        KeyMapping pending = this.arenasystem$pendingMapping;
        if (pending != null) {
            KeyMapping selected = ((KeyBindsScreenAccessor)this).arenasystem$getSelectedKey();
            if (selected == null) {
                KeybindChordConfig.setRequiredModifiers(pending.getName(), this.arenasystem$pendingModifiers);
                ((KeyBindsScreen)this).refreshKeybindLabels();
            }

            this.arenasystem$pendingMapping = null;
            this.arenasystem$pendingModifiers = 0;
        }
    }
}
