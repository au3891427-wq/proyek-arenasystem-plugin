package id.hadziq.arenasystem.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
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

@Mixin(KeyBindsScreen.class)
abstract class KeyBindsScreenMixin {
    @Unique
    private KeyMapping arenasystem$pendingMapping;

    @Unique
    private int arenasystem$pendingModifiers;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void arenasystem$captureModifierKeys(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        KeyMapping selected = ((KeyBindsScreenAccessor) this).arenasystem$getSelectedKey();
        if (selected == null || !KeybindChordConfig.isManaged(selected.getName())) {
            return;
        }

        InputConstants.Key pressedKey = InputConstants.getKey(event);
        if (KeybindModifierMatcher.isModifierKey(pressedKey.getName())) {
            callback.setReturnValue(true);
            return;
        }

        arenasystem$pendingMapping = selected;
        arenasystem$pendingModifiers = event.isEscape()
            ? 0
            : KeybindModifierMatcher.modifiersFromInput(
                event.hasShiftDown(),
                event.hasAltDown(),
                event.hasControlDown()
            );
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void arenasystem$applyKeyboardModifiers(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        arenasystem$commitPendingModifiers();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void arenasystem$captureMouseModifiers(
        MouseButtonEvent event,
        boolean doubleClick,
        CallbackInfoReturnable<Boolean> callback
    ) {
        KeyMapping selected = ((KeyBindsScreenAccessor) this).arenasystem$getSelectedKey();
        if (selected == null || !KeybindChordConfig.isManaged(selected.getName())) {
            return;
        }

        arenasystem$pendingMapping = selected;
        arenasystem$pendingModifiers = KeybindModifierMatcher.modifiersFromInput(
            event.hasShiftDown(),
            event.hasAltDown(),
            event.hasControlDown()
        );
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void arenasystem$applyMouseModifiers(
        MouseButtonEvent event,
        boolean doubleClick,
        CallbackInfoReturnable<Boolean> callback
    ) {
        arenasystem$commitPendingModifiers();
    }

    @Unique
    private void arenasystem$commitPendingModifiers() {
        KeyMapping pending = arenasystem$pendingMapping;
        if (pending == null) {
            return;
        }

        // Vanilla has already processed the key or mouse input by this point.
        // Commit now instead of waiting for selectedKey to become null on a later click.
        KeybindChordConfig.setRequiredModifiers(pending.getName(), arenasystem$pendingModifiers);
        ((KeyBindsScreen) (Object) this).refreshKeybindLabels();

        arenasystem$pendingMapping = null;
        arenasystem$pendingModifiers = 0;
    }
}
