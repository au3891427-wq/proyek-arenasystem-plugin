package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Converts a real right-click press into a server-authoritative Assassin off-hand input.
 *
 * Block interactions are deliberately left to vanilla. The server still validates the
 * player's class, match, held items, target, range, team and cooldown.
 */
final class AssassinOffhandInputClient {
    private static boolean useWasDown;
    private static boolean initialized;

    private AssassinOffhandInputClient() {
    }

    static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(AssassinOffhandInputClient::onEndClientTick);
    }

    private static void onEndClientTick(Minecraft client) {
        boolean useDown = client.options.keyUse.isDown();
        boolean usePressed = useDown && !useWasDown;
        useWasDown = useDown;

        if (!usePressed || client.gui.screen() != null) {
            return;
        }

        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null
                || !player.isAlive() || player.isSpectator()) {
            return;
        }

        HitResult hitResult = client.hitResult;
        if (hitResult instanceof BlockHitResult) {
            return;
        }

        if (hitResult instanceof EntityHitResult entityHit
                && !(entityHit.getEntity() instanceof Player)) {
            return;
        }

        if (hitResult == null
                || (hitResult.getType() != HitResult.Type.MISS
                    && !(hitResult instanceof EntityHitResult))) {
            return;
        }

        SkillInputClient.send(SkillInputProtocol.ACTION_ASSASSIN_OFFHAND);
    }
}
