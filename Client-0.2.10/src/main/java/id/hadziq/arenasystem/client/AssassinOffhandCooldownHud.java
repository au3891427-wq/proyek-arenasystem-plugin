package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

final class AssassinOffhandCooldownHud {
    private static final int INDICATOR_WIDTH = 16;
    private static final int INDICATOR_HEIGHT = 4;
    private static final int INDICATOR_BORDER = 1;
    private static final int INDICATOR_GAP_ABOVE_CROSSHAIR = 2;

    // High-contrast colors remain readable against both bright daytime and dark nighttime scenes.
    private static final int INDICATOR_OUTLINE = 0xFFE8FBFF;
    private static final int INDICATOR_BACKGROUND = 0xFF0B2633;
    private static final int INDICATOR_PROGRESS = 0xFF32D6F2;

    private static volatile long cooldownStartedAtNanos;
    private static volatile long cooldownDurationNanos;

    private AssassinOffhandCooldownHud() {
    }

    static void initialize() {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.CROSSHAIR,
            Identifier.fromNamespaceAndPath("arstm_client0qb201", "assassin_offhand_cooldown"),
            AssassinOffhandCooldownHud::render
        );
    }

    static void startCooldown(int durationTicks) {
        int safeTicks = Math.max(1, Math.min(1200, durationTicks));
        long now = System.nanoTime();
        cooldownStartedAtNanos = now;
        cooldownDurationNanos = safeTicks * 50_000_000L;
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
        int progressWidth = Math.min(INDICATOR_WIDTH, (int) (progress * INDICATOR_WIDTH));
        int totalWidth = INDICATOR_WIDTH + INDICATOR_BORDER * 2;
        int totalHeight = INDICATOR_HEIGHT + INDICATOR_BORDER * 2;
        int centerX = client.getWindow().getGuiScaledWidth() / 2;
        int centerY = client.getWindow().getGuiScaledHeight() / 2;
        int left = centerX - totalWidth / 2;
        int top = centerY - 7 - INDICATOR_GAP_ABOVE_CROSSHAIR - totalHeight;
        int innerLeft = left + INDICATOR_BORDER;
        int innerTop = top + INDICATOR_BORDER;

        graphics.fill(left, top, left + totalWidth, top + totalHeight, INDICATOR_OUTLINE);
        graphics.fill(
            innerLeft,
            innerTop,
            innerLeft + INDICATOR_WIDTH,
            innerTop + INDICATOR_HEIGHT,
            INDICATOR_BACKGROUND
        );
        if (progressWidth > 0) {
            graphics.fill(
                innerLeft,
                innerTop,
                innerLeft + progressWidth,
                innerTop + INDICATOR_HEIGHT,
                INDICATOR_PROGRESS
            );
        }
    }
}
