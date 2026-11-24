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
 * Suppresses vanilla main-hand item/entity use while dual Assassin blades are equipped.
 *
 * The actual off-hand attack is sent once per right-mouse press by the client key mapping.
 * These callbacks must not send skill packets because vanilla use callbacks can be invoked
 * independently of the physical button's initial press.
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

            // The custom client input packet replaces vanilla main-hand item use.
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

            // Prevent a vanilla entity-interaction packet for the custom dagger click.
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
