package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

// $VF: Compiled from AssassinOffhandCooldownHud.java
final class AssassinOffhandCooldownHud {
    private static final int INDICATOR_WIDTH = 16;
    private static final int INDICATOR_HEIGHT = 4;
    private static final int INDICATOR_BORDER = 1;
    private static final int INDICATOR_GAP_ABOVE_CROSSHAIR = 2;
    private static final int INDICATOR_OUTLINE = -1508353;
    private static final int INDICATOR_BACKGROUND = -16046541;
    private static final int INDICATOR_PROGRESS = -13445390;
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
        cooldownDurationNanos = safeTicks * 50000000L;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.level != null && !client.player.isSpectator()) {
            long duration = cooldownDurationNanos;
            if (duration > 0L) {
                long elapsed = System.nanoTime() - cooldownStartedAtNanos;
                if (elapsed >= duration) {
                    cooldownDurationNanos = 0L;
                } else {
                    float progress = Math.max(0.0F, Math.min(1.0F, (float)elapsed / (float)duration));
                    int progressWidth = Math.min(16, (int)(progress * 16.0F));
                    int totalWidth = 18;
                    int totalHeight = 6;
                    int centerX = client.getWindow().getGuiScaledWidth() / 2;
                    int centerY = client.getWindow().getGuiScaledHeight() / 2;
                    int left = centerX - totalWidth / 2;
                    int top = centerY - 7 - 2 - totalHeight;
                    int innerLeft = left + 1;
                    int innerTop = top + 1;
                    graphics.fill(left, top, left + totalWidth, top + totalHeight, -1508353);
                    graphics.fill(innerLeft, innerTop, innerLeft + 16, innerTop + 4, -16046541);
                    if (progressWidth > 0) {
                        graphics.fill(innerLeft, innerTop, innerLeft + progressWidth, innerTop + 4, -13445390);
                    }
                }
            }
        }
    }
}
