package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class AssassinOffhandCooldownHud {
    private static final int INDICATOR_WIDTH = 16;
    private static final int INDICATOR_HEIGHT = 4;
    private static final int INDICATOR_GAP_ABOVE_CROSSHAIR = 2;
    private static final Identifier INDICATOR_BACKGROUND = Identifier.withDefaultNamespace(
        "hud/crosshair_attack_indicator_background"
    );
    private static final Identifier INDICATOR_PROGRESS = Identifier.withDefaultNamespace(
        "hud/crosshair_attack_indicator_progress"
    );
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
        int progressWidth = Math.min(INDICATOR_WIDTH, (int) (progress * 17.0F));
        int centerX = client.getWindow().getGuiScaledWidth() / 2;
        int centerY = client.getWindow().getGuiScaledHeight() / 2;
        int left = centerX - INDICATOR_WIDTH / 2;
        int top = centerY - 7 - INDICATOR_GAP_ABOVE_CROSSHAIR - INDICATOR_HEIGHT;

        graphics.blitSprite(
            RenderPipelines.CROSSHAIR,
            INDICATOR_BACKGROUND,
            left,
            top,
            INDICATOR_WIDTH,
            INDICATOR_HEIGHT
        );
        if (progressWidth > 0) {
            graphics.blitSprite(
                RenderPipelines.CROSSHAIR,
                INDICATOR_PROGRESS,
                INDICATOR_WIDTH,
                INDICATOR_HEIGHT,
                0,
                0,
                left,
                top,
                progressWidth,
                INDICATOR_HEIGHT
            );
        }
    }
}
