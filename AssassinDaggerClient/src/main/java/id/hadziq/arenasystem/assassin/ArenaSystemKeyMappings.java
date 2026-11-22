package id.hadziq.arenasystem.assassin;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public final class ArenaSystemKeyMappings {
    private static final String MOD_ID = "arenasystem_assassin_dagger";

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "controls"));

    private static final KeyMapping OFFHAND_STRIKE = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.arenasystem_assassin_dagger.offhand_strike",
                    InputConstants.Type.KEYSYM,
                    InputConstants.KEY_R,
                    CATEGORY
            )
    );

    private ArenaSystemKeyMappings() {
    }

    static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(ArenaSystemKeyMappings::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        while (OFFHAND_STRIKE.consumeClick()) {
            if (client.player == null
                    || client.gameMode == null
                    || client.screen != null
                    || !client.player.isAlive()
                    || !client.player.getMainHandItem().is(Items.GOLDEN_SWORD)
                    || !client.player.getOffhandItem().is(Items.GOLDEN_SWORD)) {
                continue;
            }

            // The Paper plugin already validates the Assassin class, item identity,
            // target, range, damage, and cooldown from this normal main-hand use packet.
            client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        }
    }
}
