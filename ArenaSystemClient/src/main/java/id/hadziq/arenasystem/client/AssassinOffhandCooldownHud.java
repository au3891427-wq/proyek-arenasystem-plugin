package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Draws the Assassin off-hand recharge bar immediately above the vanilla crosshair. */
final class AssassinOffhandCooldownHud {
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final int INDICATOR_SIZE = 18;
    private static final int INDICATOR_GAP_ABOVE_CROSSHAIR = 2;
    private static final int PROGRESS_COLOR = 0xFFFFD54F;
    private static final Identifier INDICATOR_BACKGROUND =
            Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_background");
    private static final Identifier INDICATOR_PROGRESS =
            Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_progress");

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
        int progressHeight = Math.round(INDICATOR_SIZE * progress);

        int centerX = client.getWindow().getGuiScaledWidth() / 2;
        int centerY = client.getWindow().getGuiScaledHeight() / 2;
        int left = centerX - INDICATOR_SIZE / 2;
        int top = centerY - 7 - INDICATOR_GAP_ABOVE_CROSSHAIR - INDICATOR_SIZE;

        // Match the vanilla hotbar attack indicator's sword silhouette and bottom-up fill.
        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                INDICATOR_BACKGROUND,
                left,
                top,
                INDICATOR_SIZE,
                INDICATOR_SIZE);
        if (progressHeight > 0) {
            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    INDICATOR_PROGRESS,
                    INDICATOR_SIZE,
                    INDICATOR_SIZE,
                    0,
                    INDICATOR_SIZE - progressHeight,
                    left,
                    top + INDICATOR_SIZE - progressHeight,
                    INDICATOR_SIZE,
                    progressHeight,
                    PROGRESS_COLOR);
        }
    }
}
