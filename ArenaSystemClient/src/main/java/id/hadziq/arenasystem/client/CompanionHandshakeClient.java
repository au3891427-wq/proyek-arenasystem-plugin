package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.UUID;

final class CompanionHandshakeClient {
    private static final Identifier CHANNEL =
            Identifier.fromNamespaceAndPath("arenasystem", "mod_handshake");

    private static Session session;

    private CompanionHandshakeClient() {
    }

    static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> clearCurrentSession());

        PayloadTypeRegistry.clientboundPlay().register(HandshakePayload.TYPE, HandshakePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HandshakePayload.TYPE, HandshakePayload.CODEC);

        boolean registered = ClientPlayNetworking.registerGlobalReceiver(
                HandshakePayload.TYPE,
                (payload, context) -> handleMessage(payload.data())
        );
        if (!registered) {
            throw new IllegalStateException("ArenaSystem handshake channel is already registered.");
        }
        System.out.println("[ArenaSystem] Handshake Protocol v2 responder registered.");
    }

    private static synchronized void handleMessage(byte[] payload) {
        CompanionHandshakeProtocol.Message message = CompanionHandshakeProtocol.decode(payload);
        if (message == null) {
            System.err.println("[ArenaSystem] Ignored malformed handshake message.");
            return;
        }
        if (message.protocolVersion() != CompanionHandshakeProtocol.PROTOCOL_VERSION) {
            System.err.println("[ArenaSystem] Ignored unsupported handshake protocol version "
                    + message.protocolVersion() + ".");
            return;
        }

        if (message.type() == CompanionHandshakeProtocol.Type.SERVER_HELLO) {
            beginHandshake(message);
            return;
        }

        Session current = session;
        if (current == null || !current.sessionId.equals(message.sessionId())) {
            System.err.println("[ArenaSystem] Ignored handshake message for an inactive session.");
            return;
        }

        try {
            switch (message.type()) {
                case AUTH_CHALLENGE -> handleAuthChallenge(current, message.body());
                case SERVER_CHALLENGE -> handleServerChallenge(current, message.body());
                case CONNECTION_ACK -> handleConnectionAck(current, message.body());
                default -> System.err.println("[ArenaSystem] Ignored unexpected server message "
                        + message.type() + " during " + current.stage + ".");
            }
        } catch (IllegalArgumentException exception) {
            clearSession(current);
            System.err.println("[ArenaSystem] Handshake rejected: " + exception.getMessage());
        } catch (RuntimeException exception) {
            clearSession(current);
            System.err.println("[ArenaSystem] Handshake processing failed: " + exception.getMessage());
        }
    }

    private static void beginHandshake(CompanionHandshakeProtocol.Message message) {
        clearSession(session);
        Session next = new Session(message.sessionId(), message.body(),
                CompanionHandshakeProtocol.randomBytes(CompanionHandshakeProtocol.NONCE_BYTES));
        session = next;

        byte[] clientHello = CompanionHandshakeProtocol.encodeClientHello(
                next.sessionId, next.serverNonce, next.clientNonce);
        if (send(clientHello)) {
            next.stage = Stage.WAITING_AUTH_CHALLENGE;
            System.out.println("[ArenaSystem] Sent CLIENT_HELLO for handshake session " + next.sessionId + ".");
        } else {
            clearSession(next);
        }
    }

    private static void handleAuthChallenge(Session current, byte[] body) {
        requireStage(current, Stage.WAITING_AUTH_CHALLENGE, "AUTH_CHALLENGE");
        current.sessionSecret = Arrays.copyOfRange(body, 0, CompanionHandshakeProtocol.SECRET_BYTES);
        current.authNonce = Arrays.copyOfRange(body, CompanionHandshakeProtocol.SECRET_BYTES, body.length);

        byte[] proof = CompanionHandshakeProtocol.createClientAuthProof(
                current.sessionSecret, current.sessionId,
                current.serverNonce, current.clientNonce, current.authNonce);
        if (send(CompanionHandshakeProtocol.encodeAuthenticate(current.sessionId, proof))) {
            current.stage = Stage.WAITING_SERVER_CHALLENGE;
        } else {
            clearSession(current);
        }
    }

    private static void handleServerChallenge(Session current, byte[] body) {
        requireStage(current, Stage.WAITING_SERVER_CHALLENGE, "SERVER_CHALLENGE");
        byte[] checkNonce = Arrays.copyOfRange(body, 0, CompanionHandshakeProtocol.NONCE_BYTES);
        byte[] receivedServerProof = Arrays.copyOfRange(body, CompanionHandshakeProtocol.NONCE_BYTES, body.length);
        byte[] expectedServerProof = CompanionHandshakeProtocol.createServerChallengeProof(
                current.sessionSecret, current.sessionId,
                current.serverNonce, current.clientNonce, current.authNonce, checkNonce);

        if (!CompanionHandshakeProtocol.constantTimeEquals(expectedServerProof, receivedServerProof)) {
            throw new IllegalArgumentException("SERVER_CHALLENGE proof verification failed.");
        }

        byte[] clientCheckProof = CompanionHandshakeProtocol.createClientConnectionCheckProof(
                current.sessionSecret, current.sessionId,
                current.serverNonce, current.clientNonce, current.authNonce, checkNonce);
        current.checkNonce = checkNonce;
        current.clientCheckProof = clientCheckProof;

        if (send(CompanionHandshakeProtocol.encodeConnectionCheck(current.sessionId, clientCheckProof))) {
            current.stage = Stage.WAITING_CONNECTION_ACK;
        } else {
            clearSession(current);
        }
    }

    private static void handleConnectionAck(Session current, byte[] body) {
        requireStage(current, Stage.WAITING_CONNECTION_ACK, "CONNECTION_ACK");
        byte[] expectedAck = CompanionHandshakeProtocol.createServerAckProof(
                current.sessionSecret, current.sessionId,
                current.serverNonce, current.clientNonce, current.authNonce,
                current.checkNonce, current.clientCheckProof);

        if (!CompanionHandshakeProtocol.constantTimeEquals(expectedAck, body)) {
            throw new IllegalArgumentException("CONNECTION_ACK proof verification failed.");
        }

        UUID completedSession = current.sessionId;
        clearSession(current);
        System.out.println("[ArenaSystem] Handshake Protocol v2 completed successfully for session "
                + completedSession + ".");
    }

    private static void requireStage(Session current, Stage expected, String messageName) {
        if (current.stage != expected) {
            throw new IllegalArgumentException(messageName + " received during " + current.stage + ".");
        }
    }

    private static boolean send(byte[] data) {
        try {
            ClientPlayNetworking.send(new HandshakePayload(data));
            return true;
        } catch (RuntimeException exception) {
            System.err.println("[ArenaSystem] Could not send handshake message: " + exception.getMessage());
            return false;
        }
    }

    private static synchronized void clearCurrentSession() {
        clearSession(session);
    }

    private static void clearSession(Session target) {
        if (target == null) {
            return;
        }
        if (session == target) {
            session = null;
        }
        wipe(target.serverNonce);
        wipe(target.clientNonce);
        wipe(target.sessionSecret);
        wipe(target.authNonce);
        wipe(target.checkNonce);
        wipe(target.clientCheckProof);
    }

    private static void wipe(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    private enum Stage {
        WAITING_SERVER_HELLO,
        WAITING_AUTH_CHALLENGE,
        WAITING_SERVER_CHALLENGE,
        WAITING_CONNECTION_ACK
    }

    private static final class Session {
        private final UUID sessionId;
        private final byte[] serverNonce;
        private final byte[] clientNonce;
        private byte[] sessionSecret;
        private byte[] authNonce;
        private byte[] checkNonce;
        private byte[] clientCheckProof;
        private Stage stage = Stage.WAITING_SERVER_HELLO;

        private Session(UUID sessionId, byte[] serverNonce, byte[] clientNonce) {
            this.sessionId = sessionId;
            this.serverNonce = serverNonce.clone();
            this.clientNonce = clientNonce.clone();
        }
    }

    private record HandshakePayload(byte[] data) implements CustomPacketPayload {
        private static final Type<HandshakePayload> TYPE = new Type<>(CHANNEL);

        // Preserve the raw plugin-message bytes; never add a length prefix.
        private static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC =
                StreamCodec.of(
                        (buffer, payload) -> buffer.writeBytes(payload.data),
                        buffer -> {
                            int length = buffer.readableBytes();
                            byte[] bytes = new byte[length];
                            buffer.readBytes(bytes);
                            return new HandshakePayload(bytes);
                        });

        private HandshakePayload {
            if (data == null) {
                throw new IllegalArgumentException("Invalid ArenaSystem handshake payload.");
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
