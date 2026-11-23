package id.hadziq.arenasystem.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

final class DaggerVisualEffects {
    private static final long VISUAL_DEBOUNCE_NANOS = 160_000_000L;
    private static long lastVisualNanos;

    private DaggerVisualEffects() {
    }

    static void playAttemptEffect(Minecraft client, LocalPlayer player) {
        if (client.level == null) {
            return;
        }

        long now = System.nanoTime();
        if (now - lastVisualNanos < VISUAL_DEBOUNCE_NANOS) {
            return;
        }
        lastVisualNanos = now;

        Vec3 look = player.getLookAngle().normalize();
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        if (right.lengthSqr() < 1.0E-6) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }

        Vec3 origin = player.getEyePosition()
                .add(look.scale(1.1))
                .add(0.0, -0.18, 0.0);

        client.level.addParticle(
                ParticleTypes.SWEEP_ATTACK,
                origin.x, origin.y, origin.z,
                0.0, 0.0, 0.0);

        for (int i = 0; i < 3; i++) {
            double offset = (i - 1) * 0.22;
            Vec3 position = origin.add(right.scale(offset));
            client.level.addParticle(
                    ParticleTypes.CRIT,
                    position.x, position.y, position.z,
                    look.x * 0.035, 0.015, look.z * 0.035);
        }
    }
}
