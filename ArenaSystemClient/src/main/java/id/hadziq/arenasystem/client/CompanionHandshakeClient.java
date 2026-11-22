package id.hadziq.arenasystem.client;

import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Responds to the ArenaSystem Paper plugin's nonce challenge.
 *
 * Server contract:
 * - Clientbound challenge: 16 raw bytes containing a UUID (big-endian).
 * - Serverbound response: protocol version (int 1) followed by the same UUID (20 raw bytes).
 */
final class CompanionHandshakeClient {
    private static final Identifier CHANNEL =
            Identifier.fromNamespaceAndPath("arenasystem", "mod_handshake");
    private static final int CHALLENGE_BYTES = 16;
    private static final int RESPONSE_BYTES = 20;

    private CompanionHandshakeClient() {
    }

    static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(HandshakePayload.TYPE, HandshakePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HandshakePayload.TYPE, HandshakePayload.CODEC);

        boolean registered = ClientPlayNetworking.registerGlobalReceiver(
                HandshakePayload.TYPE,
                (payload, context) -> respondToChallenge(payload.data())
        );
        if (!registered) {
            throw new IllegalStateException("ArenaSystem handshake channel is already registered.");
        }

        System.out.println("[ArenaSystem] Companion handshake responder registered.");
    }

    private static void respondToChallenge(byte[] challenge) {
        final byte[] response;
        try {
            response = CompanionHandshakeProtocol.createResponse(challenge);
        } catch (IllegalArgumentException exception) {
            System.err.println("[ArenaSystem] Ignored invalid handshake challenge: " + exception.getMessage());
            return;
        }

        try {
            ClientPlayNetworking.send(new HandshakePayload(response));
            System.out.println("[ArenaSystem] Sent companion handshake response to server.");
        } catch (RuntimeException exception) {
            System.err.println("[ArenaSystem] Could not send handshake response: " + exception.getMessage());
        }
    }

    private record HandshakePayload(byte[] data) implements CustomPacketPayload {
        private static final Type<HandshakePayload> TYPE = new Type<>(CHANNEL);

        /*
         * Do not add a length prefix: Bukkit's PluginMessageListener expects the exact
         * raw payload bytes from the Minecraft custom-payload packet.
         */
        private static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC =
                StreamCodec.of(
                        (buffer, payload) -> buffer.writeBytes(payload.data),
                        buffer -> {
                            int length = buffer.readableBytes();
                            if (length != CHALLENGE_BYTES) {
                                throw new DecoderException(
                                        "ArenaSystem challenge must be exactly " + CHALLENGE_BYTES + " bytes.");
                            }
                            byte[] bytes = new byte[length];
                            buffer.readBytes(bytes);
                            return new HandshakePayload(bytes);
                        });

        private HandshakePayload {
            if (data == null || (data.length != CHALLENGE_BYTES && data.length != RESPONSE_BYTES)) {
                throw new IllegalArgumentException("Invalid ArenaSystem handshake payload length.");
            }
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
