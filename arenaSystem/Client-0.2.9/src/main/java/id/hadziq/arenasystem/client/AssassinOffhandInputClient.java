package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// $VF: Compiled from AssassinOffhandInputClient.java
final class AssassinOffhandInputClient {
    private static final String ASSASSIN_BLADE_MARKER = "arenasystem:assassin_blade";
    private static boolean initialized;

    private AssassinOffhandInputClient() {
    }

    static void initialize() {
        if (!initialized) {
            initialized = true;
            UseItemCallback.EVENT
                .register(
                    (UseItemCallback)(player, world, hand) -> (InteractionResult)(hand == InteractionHand.MAIN_HAND
                            && world.isClientSide()
                            && player instanceof LocalPlayer
                            && player.isAlive()
                            && !player.isSpectator()
                            && hasAssassinBlades(player)
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS)
                );
            UseEntityCallback.EVENT
                .register(
                    (UseEntityCallback)(player, world, hand, entity, hitResult) -> (InteractionResult)(hand
                                == InteractionHand.MAIN_HAND
                            && world.isClientSide()
                            && player instanceof LocalPlayer
                            && entity instanceof Player
                            && player.isAlive()
                            && !player.isSpectator()
                            && hasAssassinBlades(player)
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS)
                );
        }
    }

    private static boolean hasAssassinBlades(Player player) {
        return hasAssassinBladeMarker(player.getMainHandItem()) && hasAssassinBladeMarker(player.getOffhandItem());
    }

    private static boolean hasAssassinBladeMarker(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }

        CompoundTag root = customData.copyTag();
        return root.get("PublicBukkitValues") instanceof CompoundTag bukkitValues
            && bukkitValues.contains("arenasystem:assassin_blade");
    }
}
