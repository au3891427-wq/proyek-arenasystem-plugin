package id.hadziq.arenasystem.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

final class ArenaSystemKeybinds {
    private static final String MOD_ID = "arstm_client0qb201";
    private static final String CLASS_SKILL_MARKER = "arenasystem:class_skill";
    private static final String ASSASSIN_BLADE_MARKER = "arenasystem:assassin_blade";
    private static final long INPUT_DEBOUNCE_NANOS = 120_000_000L;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "combat"));

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
        if (initialized) {
            return;
        }
        initialized = true;

        primarySkillKey = registerKeyboard(
                "key.arstm_client0qb201.primary_skill", InputConstants.KEY_G);
        secondarySkillKey = registerKeyboard(
                "key.arstm_client0qb201.secondary_skill", InputConstants.KEY_H);
        classSkillItemKey = registerKeyboard(
                "key.arstm_client0qb201.class_skill_item", InputConstants.KEY_J);
        sniperAimLockKey = registerKeyboard(
                "key.arstm_client0qb201.sniper_aim_lock", InputConstants.KEY_Q);
        assassinOffhandClickKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.arstm_client0qb201.assassin_offhand",
                InputConstants.Type.MOUSE,
                InputConstants.MOUSE_BUTTON_RIGHT,
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(ArenaSystemKeybinds::onEndClientTick);
    }

    private static KeyMapping registerKeyboard(String translationKey, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey,
                InputConstants.Type.KEYBOARD,
                defaultKey,
                CATEGORY));
    }

    private static void onEndClientTick(Minecraft client) {
        // consumeClick() consumes discrete press events, not the held state.
        boolean primaryPressed = primarySkillKey.consumeClick();
        boolean secondaryPressed = secondarySkillKey.consumeClick();
        boolean classSkillItemPressed = classSkillItemKey.consumeClick();
        boolean assassinOffhandClicked = assassinOffhandClickKey.consumeClick();
        boolean sniperAimLockPressed = sniperAimLockKey.consumeClick();

        if (client.gui.screen() != null) {
            return;
        }

        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null
                || !player.isAlive() || player.isSpectator()) {
            return;
        }

        if (assassinOffhandClicked && hasAssassinBlades(player)) {
            SkillInputClient.send(SkillInputProtocol.ACTION_ASSASSIN_OFFHAND);
        }

        long now = System.nanoTime();
        if (sniperAimLockPressed && isDebounceReady(now, lastSniperAimLockInput)) {
            lastSniperAimLockInput = now;
            SkillInputClient.send(SkillInputProtocol.ACTION_SNIPER_AIM_LOCK);
        }

        int pressedCount = (primaryPressed ? 1 : 0)
                + (secondaryPressed ? 1 : 0)
                + (classSkillItemPressed ? 1 : 0);
        if (pressedCount != 1) {
            return;
        }

        if (primaryPressed && isDebounceReady(now, lastPrimaryInput)) {
            lastPrimaryInput = now;
            triggerPrimarySkill();
        } else if (secondaryPressed && isDebounceReady(now, lastSecondaryInput)) {
            lastSecondaryInput = now;
            triggerSecondarySkill();
        } else if (classSkillItemPressed && isDebounceReady(now, lastClassSkillItemInput)) {
            lastClassSkillItemInput = now;
            triggerClassSkillItem(client, player);
        }
    }

    private static boolean isDebounceReady(long now, long previous) {
        return previous == 0L || now - previous >= INPUT_DEBOUNCE_NANOS;
    }

    private static void triggerPrimarySkill() {
        SkillInputClient.send(SkillInputProtocol.ACTION_PRIMARY);
    }

    private static void triggerSecondarySkill() {
        SkillInputClient.send(SkillInputProtocol.ACTION_SECONDARY);
    }

    private static void triggerClassSkillItem(Minecraft client, LocalPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (!hasPersistentMarker(mainHand, CLASS_SKILL_MARKER)) {
            return;
        }
        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
    }

    private static boolean hasAssassinBlades(LocalPlayer player) {
        return hasPersistentMarker(player.getMainHandItem(), ASSASSIN_BLADE_MARKER)
                && hasPersistentMarker(player.getOffhandItem(), ASSASSIN_BLADE_MARKER);
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
        Tag rawValues = root.get("PublicBukkitValues");
        return rawValues instanceof CompoundTag bukkitValues && bukkitValues.contains(marker);
    }
}
