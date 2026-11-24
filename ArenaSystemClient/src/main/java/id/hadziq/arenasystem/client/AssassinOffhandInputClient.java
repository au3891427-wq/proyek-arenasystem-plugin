package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Converts genuine air/entity right-click interactions into server-authoritative
 * Assassin off-hand input. Block interactions are not intercepted.
 */
final class AssassinOffhandInputClient {
    private static final String ASSASSIN_BLADE_MARKER = "arenasystem:assassin_blade";
    private static boolean initialized;

    private AssassinOffhandInputClient() {
    }

    static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != InteractionHand.MAIN_HAND
                    || !world.isClientSide()
                    || !(player instanceof LocalPlayer)
                    || !player.isAlive()
                    || player.isSpectator()
                    || !hasAssassinBlades(player)) {
                return InteractionResult.PASS;
            }

            SkillInputClient.send(SkillInputProtocol.ACTION_ASSASSIN_OFFHAND);
            // The custom plugin message replaces vanilla item-use processing for this dagger action.
            return InteractionResult.FAIL;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND
                    || !world.isClientSide()
                    || !(player instanceof LocalPlayer)
                    || !(entity instanceof Player)
                    || !player.isAlive()
                    || player.isSpectator()
                    || !hasAssassinBlades(player)) {
                return InteractionResult.PASS;
            }

            SkillInputClient.send(SkillInputProtocol.ACTION_ASSASSIN_OFFHAND);
            // Prevent a second vanilla entity-interaction packet for the same click.
            return InteractionResult.FAIL;
        });
    }

    private static boolean hasAssassinBlades(Player player) {
        return hasAssassinBladeMarker(player.getMainHandItem())
                && hasAssassinBladeMarker(player.getOffhandItem());
    }

    private static boolean hasAssassinBladeMarker(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }

        CompoundTag root = customData.copyTag();
        Tag rawValues = root.get("PublicBukkitValues");
        return rawValues instanceof CompoundTag bukkitValues
                && bukkitValues.contains(ASSASSIN_BLADE_MARKER);
    }
}
