package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

// $VF: Compiled from AssassinOffhandCooldownHud.java
final class AssassinOffhandCooldownHud {
    private static final long NANOS_PER_TICK = 50000000L;
    private static final int INDICATOR_SIZE = 18;
    private static final int INDICATOR_GAP_ABOVE_CROSSHAIR = 2;
    private static final int PROGRESS_COLOR = -10929;
    private static final Identifier INDICATOR_BACKGROUND = Identifier.withDefaultNamespace(
        "hud/hotbar_attack_indicator_background"
    );
    private static final Identifier INDICATOR_PROGRESS = Identifier.withDefaultNamespace(
        "hud/hotbar_attack_indicator_progress"
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
                    int progressHeight = Math.round(18.0F * progress);
                    int centerX = client.getWindow().getGuiScaledWidth() / 2;
                    int centerY = client.getWindow().getGuiScaledHeight() / 2;
                    int left = centerX - 9;
                    int top = centerY - 7 - 2 - 18;
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, INDICATOR_BACKGROUND, left, top, 18, 18);
                    if (progressHeight > 0) {
                        graphics.blitSprite(
                            RenderPipelines.GUI_TEXTURED,
                            INDICATOR_PROGRESS,
                            18,
                            18,
                            0,
                            18 - progressHeight,
                            left,
                            top + 18 - progressHeight,
                            18,
                            progressHeight,
                            -10929
                        );
                    }
                }
            }
        }
    }
}
