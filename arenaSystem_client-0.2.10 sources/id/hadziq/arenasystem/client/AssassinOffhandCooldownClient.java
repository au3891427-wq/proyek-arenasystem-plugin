package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

// $VF: Compiled from AssassinOffhandCooldownClient.java
final class AssassinOffhandCooldownClient {
    private static final Identifier CHANNEL = Identifier.fromNamespaceAndPath(
        "arenasystem", "assassin_offhand_cooldown"
    );
    private static boolean initialized;

    private AssassinOffhandCooldownClient() {
    }

    static void initialize() {
        if (!initialized) {
            initialized = true;
            PayloadTypeRegistry.clientboundPlay()
                .register(
                    AssassinOffhandCooldownClient.CooldownPayload.TYPE,
                    AssassinOffhandCooldownClient.CooldownPayload.CODEC
                );
            ClientPlayNetworking.registerGlobalReceiver(
                AssassinOffhandCooldownClient.CooldownPayload.TYPE,
                (payload, context) -> AssassinOffhandCooldownHud.startCooldown(payload.durationTicks())
            );
            AssassinOffhandCooldownHud.initialize();
        }
    }

    // $VF: Compiled from AssassinOffhandCooldownClient.java
    private record CooldownPayload(int durationTicks) implements CustomPacketPayload {
        private static final Type<AssassinOffhandCooldownClient.CooldownPayload> TYPE = new Type<>(
            AssassinOffhandCooldownClient.CHANNEL
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, AssassinOffhandCooldownClient.CooldownPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeInt(payload.durationTicks),
            buffer -> new AssassinOffhandCooldownClient.CooldownPayload(buffer.readInt())
        );

        private CooldownPayload {
            durationTicks = Math.max(1, Math.min(1200, durationTicks));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
