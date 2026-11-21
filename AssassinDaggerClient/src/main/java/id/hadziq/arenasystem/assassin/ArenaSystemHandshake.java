package id.hadziq.arenasystem.assassin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Play-stage handshake with the ArenaSystem Paper plugin.
 *
 * The server sends a random 128-bit challenge. This client replies with the
 * challenge and the protocol version, allowing the server to verify that the
 * companion mod is active and understands the expected protocol.
 */
final class ArenaSystemHandshake {
    static final int PROTOCOL_VERSION = 1;

    private static final Identifier CHANNEL_ID =
            Identifier.fromNamespaceAndPath("arenasystem", "mod_handshake");

    private ArenaSystemHandshake() {
    }

    static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(
                ChallengePayload.TYPE,
                ChallengePayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
                ResponsePayload.TYPE,
                ResponsePayload.CODEC
        );

        ClientPlayNetworking.registerGlobalReceiver(
                ChallengePayload.TYPE,
                (payload, context) -> {
                    if (!ClientPlayNetworking.canSend(ResponsePayload.TYPE)) {
                        System.err.println("[ArenaSystem] Server handshake response channel is not available.");
                        return;
                    }

                    ClientPlayNetworking.send(new ResponsePayload(
                            PROTOCOL_VERSION,
                            payload.nonce()
                    ));
                }
        );
    }

    private record ChallengePayload(UUID nonce) implements CustomPacketPayload {
        private static final Type<ChallengePayload> TYPE = new Type<>(CHANNEL_ID);

        private static final StreamCodec<RegistryFriendlyByteBuf, ChallengePayload> CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeLong(payload.nonce().getMostSignificantBits());
                            buffer.writeLong(payload.nonce().getLeastSignificantBits());
                        },
                        buffer -> new ChallengePayload(new UUID(buffer.readLong(), buffer.readLong()))
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record ResponsePayload(int protocolVersion, UUID nonce) implements CustomPacketPayload {
        private static final Type<ResponsePayload> TYPE = new Type<>(CHANNEL_ID);

        private static final StreamCodec<RegistryFriendlyByteBuf, ResponsePayload> CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeInt(payload.protocolVersion());
                            buffer.writeLong(payload.nonce().getMostSignificantBits());
                            buffer.writeLong(payload.nonce().getLeastSignificantBits());
                        },
                        buffer -> new ResponsePayload(
                                buffer.readInt(),
                                new UUID(buffer.readLong(), buffer.readLong())
                        )
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
