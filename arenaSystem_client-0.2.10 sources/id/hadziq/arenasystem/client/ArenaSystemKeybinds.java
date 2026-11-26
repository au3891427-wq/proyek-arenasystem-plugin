package id.hadziq.arenasystem.client;

import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import id.hadziq.arenasystem.client.mixin.KeyMappingAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// $VF: Compiled from ArenaSystemKeybinds.java
public final class ArenaSystemKeybinds {
    private static final String MOD_ID = "arstm_client0qb201";
    private static final String CLASS_SKILL_MARKER = "arenasystem:class_skill";
    private static final String ASSASSIN_BLADE_MARKER = "arenasystem:assassin_blade";
    private static final long INPUT_DEBOUNCE_NANOS = 120000000L;
    private static final Category CATEGORY = Category.register(
        Identifier.fromNamespaceAndPath("arstm_client0qb201", "combat")
    );
    private static KeyMapping primarySkillKey;
    private static KeyMapping secondarySkillKey;
    private static KeyMapping classSkillItemKey;
    private static KeyMapping assassinOffhandClickKey;
    private static KeyMapping sniperAimLockKey;
    private static long lastPrimaryInput;
    private static long lastSecondaryInput;
    private static long lastClassSkillItemInput;
    private static long lastSniperAimLockInput;
    private static boolean initialized;

    private ArenaSystemKeybinds() {
    }

    static void initialize() {
        if (!initialized) {
            initialized = true;
            primarySkillKey = registerKeyboard("key.arstm_client0qb201.primary_skill", 10);
            secondarySkillKey = registerKeyboard("key.arstm_client0qb201.secondary_skill", 11);
            classSkillItemKey = registerKeyboard("key.arstm_client0qb201.class_skill_item", 13);
            sniperAimLockKey = registerKeyboard("key.arstm_client0qb201.sniper_aim_lock", 20);
            assassinOffhandClickKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.arstm_client0qb201.assassin_offhand", Type.MOUSE, 3, CATEGORY)
            );
            ClientTickEvents.END_CLIENT_TICK.register(ArenaSystemKeybinds::onEndClientTick);
        }
    }

    private static KeyMapping registerKeyboard(String translationKey, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(translationKey, Type.KEYBOARD, defaultKey, CATEGORY));
    }

    private static void onEndClientTick(Minecraft client) {
        boolean primaryPressed = primarySkillKey.consumeClick();
        boolean secondaryPressed = secondarySkillKey.consumeClick();
        boolean classSkillItemPressed = classSkillItemKey.consumeClick();
        boolean assassinOffhandClicked = assassinOffhandClickKey.consumeClick();
        boolean sniperAimLockPressed = sniperAimLockKey.consumeClick();
        if (client.gui.screen() == null) {
            LocalPlayer player = client.player;
            if (player != null
                && client.level != null
                && client.gameMode != null
                && player.isAlive()
                && !player.isSpectator()
                && (
                    primaryPressed
                        || secondaryPressed
                        || classSkillItemPressed
                        || assassinOffhandClicked
                        || sniperAimLockPressed
                )) {
                int fallbackModifiers = readActiveModifiers(client);
                int assassinModifiers = assassinOffhandClicked
                    ? KeybindChordConfig.consumePressedModifiers(assassinOffhandClickKey.getName(), fallbackModifiers)
                    : fallbackModifiers;
                int sniperModifiers = sniperAimLockPressed
                    ? KeybindChordConfig.consumePressedModifiers(sniperAimLockKey.getName(), fallbackModifiers)
                    : fallbackModifiers;
                int primaryModifiers = primaryPressed
                    ? KeybindChordConfig.consumePressedModifiers(primarySkillKey.getName(), fallbackModifiers)
                    : fallbackModifiers;
                int secondaryModifiers = secondaryPressed
                    ? KeybindChordConfig.consumePressedModifiers(secondarySkillKey.getName(), fallbackModifiers)
                    : fallbackModifiers;
                int classSkillModifiers = classSkillItemPressed
                    ? KeybindChordConfig.consumePressedModifiers(classSkillItemKey.getName(), fallbackModifiers)
                    : fallbackModifiers;
                if (assassinOffhandClicked
                    && KeybindModifierMatcher.matchesConfiguredChord(
                        assassinModifiers, KeybindChordConfig.getRequiredModifiers(assassinOffhandClickKey.getName())
                    )
                    && hasAssassinBlades(player)) {
                    SkillInputClient.send((byte)3);
                }

                long now = System.nanoTime();
                if (sniperAimLockPressed
                    && KeybindModifierMatcher.matchesExactly(
                        sniperModifiers, KeybindChordConfig.getRequiredModifiers(sniperAimLockKey.getName())
                    )
                    && isDebounceReady(now, lastSniperAimLockInput)) {
                    lastSniperAimLockInput = now;
                    SkillInputClient.send((byte)4);
                }

                boolean primaryChordPressed = primaryPressed
                    && KeybindModifierMatcher.matchesExactly(
                        primaryModifiers, KeybindChordConfig.getRequiredModifiers(primarySkillKey.getName())
                    );
                boolean secondaryChordPressed = secondaryPressed
                    && KeybindModifierMatcher.matchesExactly(
                        secondaryModifiers, KeybindChordConfig.getRequiredModifiers(secondarySkillKey.getName())
                    );
                boolean classSkillItemChordPressed = classSkillItemPressed
                    && KeybindModifierMatcher.matchesExactly(
                        classSkillModifiers, KeybindChordConfig.getRequiredModifiers(classSkillItemKey.getName())
                    );
                int pressedCount = (primaryChordPressed ? 1 : 0)
                    + (secondaryChordPressed ? 1 : 0)
                    + (classSkillItemChordPressed ? 1 : 0);
                if (pressedCount == 1) {
                    if (primaryChordPressed && isDebounceReady(now, lastPrimaryInput)) {
                        lastPrimaryInput = now;
                        triggerPrimarySkill();
                    } else if (secondaryChordPressed && isDebounceReady(now, lastSecondaryInput)) {
                        lastSecondaryInput = now;
                        triggerSecondarySkill();
                    } else if (classSkillItemChordPressed && isDebounceReady(now, lastClassSkillItemInput)) {
                        lastClassSkillItemInput = now;
                        triggerClassSkillItem(client, player);
                    }
                }
            }
        }
    }

    public static void captureModifiersForKey(Key key, int modifiers) {
        captureIfMatching(primarySkillKey, key, modifiers);
        captureIfMatching(secondarySkillKey, key, modifiers);
        captureIfMatching(classSkillItemKey, key, modifiers);
        captureIfMatching(assassinOffhandClickKey, key, modifiers);
        captureIfMatching(sniperAimLockKey, key, modifiers);
    }

    private static void captureIfMatching(KeyMapping mapping, Key key, int modifiers) {
        if (mapping != null && ((KeyMappingAccessor)mapping).arenasystem$getBoundKey().equals(key)) {
            KeybindChordConfig.capturePressedModifiers(mapping.getName(), modifiers);
        }
    }

    private static int readActiveModifiers(Minecraft client) {
        int modifiers = 0;
        if (client.hasShiftDown()) {
            modifiers |= 1;
        }

        if (client.hasAltDown()) {
            modifiers |= 2;
        }

        if (client.hasControlDown()) {
            modifiers |= 4;
        }

        return modifiers;
    }

    private static boolean isDebounceReady(long now, long previous) {
        return previous == 0L || now - previous >= 120000000L;
    }

    private static void triggerPrimarySkill() {
        SkillInputClient.send((byte)1);
    }

    private static void triggerSecondarySkill() {
        SkillInputClient.send((byte)2);
    }

    private static void triggerClassSkillItem(Minecraft client, LocalPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (hasPersistentMarker(mainHand, "arenasystem:class_skill")) {
            client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
    }

    private static boolean hasAssassinBlades(LocalPlayer player) {
        return hasPersistentMarker(player.getMainHandItem(), "arenasystem:assassin_blade")
            && hasPersistentMarker(player.getOffhandItem(), "arenasystem:assassin_blade");
    }

    private static boolean hasPersistentMarker(ItemStack stack, String marker) {
        if (stack.isEmpty()) {
            return false;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }

        CompoundTag root = customData.copyTag();
        return root.get("PublicBukkitValues") instanceof CompoundTag bukkitValues && bukkitValues.contains(marker);
    }
}
