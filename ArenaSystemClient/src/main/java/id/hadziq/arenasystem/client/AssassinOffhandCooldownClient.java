package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Receives the server-authoritative Assassin off-hand cooldown signal. */
final class AssassinOffhandCooldownClient {
    private static final Identifier CHANNEL =
            Identifier.fromNamespaceAndPath("arenasystem", "assassin_offhand_cooldown");
    private static boolean initialized;

    private AssassinOffhandCooldownClient() {
    }

    static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        PayloadTypeRegistry.clientboundPlay().register(
                CooldownPayload.TYPE, CooldownPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(CooldownPayload.TYPE, (payload, context) ->
                AssassinOffhandCooldownHud.startCooldown(payload.durationTicks()));
        AssassinOffhandCooldownHud.initialize();
    }

    private record CooldownPayload(int durationTicks) implements CustomPacketPayload {
        private static final Type<CooldownPayload> TYPE = new Type<>(CHANNEL);
        private static final StreamCodec<RegistryFriendlyByteBuf, CooldownPayload> CODEC =
                StreamCodec.of(
                        (buffer, payload) -> buffer.writeInt(payload.durationTicks),
                        buffer -> new CooldownPayload(buffer.readInt()));

        private CooldownPayload {
            durationTicks = Math.max(1, Math.min(1_200, durationTicks));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
