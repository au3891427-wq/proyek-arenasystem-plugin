package id.hadziq.arenasystem.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    private static KeyMapping daggerOffhandStrikeKey;

    private static boolean primaryWasDown;
    private static boolean secondaryWasDown;
    private static boolean classSkillItemWasDown;
    private static boolean daggerWasDown;

    private static long lastPrimaryInput;
    private static long lastSecondaryInput;
    private static long lastClassSkillItemInput;
    private static long lastDaggerInput;

    private static boolean initialized;

    private ArenaSystemKeybinds() {
    }

    static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        primarySkillKey = register(
                "key.arstm_client0qb201.primary_skill", InputConstants.KEY_G);
        secondarySkillKey = register(
                "key.arstm_client0qb201.secondary_skill", InputConstants.KEY_H);
        classSkillItemKey = register(
                "key.arstm_client0qb201.class_skill_item", InputConstants.KEY_J);
        daggerOffhandStrikeKey = register(
                "key.arstm_client0qb201.dagger_offhand_strike", InputConstants.KEY_K);

        ClientTickEvents.END_CLIENT_TICK.register(ArenaSystemKeybinds::onEndClientTick);
    }

    private static KeyMapping register(String translationKey, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey,
                InputConstants.Type.KEYBOARD,
                defaultKey,
                CATEGORY));
    }

    private static void onEndClientTick(Minecraft client) {
        boolean primaryDown = primarySkillKey.isDown();
        boolean secondaryDown = secondarySkillKey.isDown();
        boolean classSkillItemDown = classSkillItemKey.isDown();
        boolean daggerDown = daggerOffhandStrikeKey.isDown();

        boolean primaryPressed = primaryDown && !primaryWasDown;
        boolean secondaryPressed = secondaryDown && !secondaryWasDown;
        boolean classSkillItemPressed = classSkillItemDown && !classSkillItemWasDown;
        boolean daggerPressed = daggerDown && !daggerWasDown;

        primaryWasDown = primaryDown;
        secondaryWasDown = secondaryDown;
        classSkillItemWasDown = classSkillItemDown;
        daggerWasDown = daggerDown;

        if (client.screen != null) {
            return;
        }

        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null
                || !player.isAlive() || player.isSpectator()) {
            return;
        }

        int pressedCount = (primaryPressed ? 1 : 0)
                + (secondaryPressed ? 1 : 0)
                + (classSkillItemPressed ? 1 : 0)
                + (daggerPressed ? 1 : 0);
        if (pressedCount != 1) {
            return;
        }

        long now = System.nanoTime();
        if (primaryPressed && isDebounceReady(now, lastPrimaryInput)) {
            lastPrimaryInput = now;
            triggerPrimarySkill(client, player);
        } else if (secondaryPressed && isDebounceReady(now, lastSecondaryInput)) {
            lastSecondaryInput = now;
            triggerSecondarySkill(client, player);
        } else if (classSkillItemPressed && isDebounceReady(now, lastClassSkillItemInput)) {
            lastClassSkillItemInput = now;
            triggerClassSkillItem(client, player);
        } else if (daggerPressed && isDebounceReady(now, lastDaggerInput)) {
            lastDaggerInput = now;
            triggerDaggerStrike(client, player);
        }
    }

    private static boolean isDebounceReady(long now, long previous) {
        return previous == 0L || now - previous >= INPUT_DEBOUNCE_NANOS;
    }

    private static void triggerPrimarySkill(Minecraft client, LocalPlayer player) {
        if (isSecondaryModifierHeld(client, player)) {
            return;
        }
        sendSwapHands(client);
    }

    private static void triggerSecondarySkill(Minecraft client, LocalPlayer player) {
        if (!isSecondaryModifierHeld(client, player)) {
            return;
        }
        sendSwapHands(client);
    }

    private static boolean isSecondaryModifierHeld(Minecraft client, LocalPlayer player) {
        return client.options.keyShift.isDown()
                || client.options.keySprint.isDown()
                || player.isShiftKeyDown()
                || player.isSprinting();
    }

    private static void sendSwapHands(Minecraft client) {
        if (client.getConnection() == null) {
            return;
        }
        client.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO,
                Direction.DOWN));
    }

    private static void triggerClassSkillItem(Minecraft client, LocalPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (!hasPersistentMarker(mainHand, CLASS_SKILL_MARKER, Tag.TAG_STRING)) {
            return;
        }
        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
    }

    private static void triggerDaggerStrike(Minecraft client, LocalPlayer player) {
        if (!isAssassinBlade(player.getMainHandItem())
                || !isAssassinBlade(player.getOffhandItem())) {
            return;
        }

        if (client.crosshairPickEntity instanceof Player target && target != player) {
            client.gameMode.interact(player, target, InteractionHand.MAIN_HAND);
        } else {
            client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
        DaggerVisualEffects.playAttemptEffect(client, player);
    }

    private static boolean isAssassinBlade(ItemStack stack) {
        return !stack.isEmpty()
                && stack.is(Items.GOLDEN_SWORD)
                && hasPersistentMarker(stack, ASSASSIN_BLADE_MARKER, Tag.TAG_BYTE);
    }

    private static boolean hasPersistentMarker(ItemStack stack, String marker, int tagType) {
        if (stack.isEmpty()) {
            return false;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }

        CompoundTag root = customData.copyTag();
        Tag rawValues = root.get("PublicBukkitValues");
        if (!(rawValues instanceof CompoundTag bukkitValues)) {
            return false;
        }
        return bukkitValues.contains(marker, tagType);
    }
}
