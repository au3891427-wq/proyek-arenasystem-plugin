package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Draws a small off-hand recharge bar directly below the vanilla crosshair area. */
final class AssassinOffhandCooldownHud {
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final int BAR_WIDTH = 18;
    private static final int BAR_HEIGHT = 2;

    private static volatile long cooldownStartedAtNanos;
    private static volatile long cooldownDurationNanos;

    private AssassinOffhandCooldownHud() {
    }

    static void initialize() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("arstm_client0qb201", "assassin_offhand_cooldown"),
                AssassinOffhandCooldownHud::render);
    }

    static void startCooldown(int durationTicks) {
        int safeTicks = Math.max(1, Math.min(1_200, durationTicks));
        long now = System.nanoTime();
        cooldownStartedAtNanos = now;
        cooldownDurationNanos = safeTicks * NANOS_PER_TICK;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.player.isSpectator()) {
            return;
        }

        long duration = cooldownDurationNanos;
        if (duration <= 0L) {
            return;
        }

        long elapsed = System.nanoTime() - cooldownStartedAtNanos;
        if (elapsed >= duration) {
            cooldownDurationNanos = 0L;
            return;
        }

        float progress = Math.max(0.0F, Math.min(1.0F, (float) elapsed / (float) duration));
        int fillWidth = Math.round(BAR_WIDTH * progress);

        int centerX = client.getWindow().getGuiScaledWidth() / 2;
        int centerY = client.getWindow().getGuiScaledHeight() / 2;
        int left = centerX - BAR_WIDTH / 2;
        int top = centerY + 12;

        graphics.fill(left - 1, top - 1, left + BAR_WIDTH + 1, top + BAR_HEIGHT + 1, 0xB0000000);
        graphics.fill(left, top, left + BAR_WIDTH, top + BAR_HEIGHT, 0xFF4A4A4A);
        if (fillWidth > 0) {
            graphics.fill(left, top, left + fillWidth, top + BAR_HEIGHT, 0xFFE0B45A);
        }
    }
}
