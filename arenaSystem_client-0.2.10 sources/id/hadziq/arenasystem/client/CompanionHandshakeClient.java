package id.hadziq.arenasystem.client;

import java.util.Arrays;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

// $VF: Compiled from CompanionHandshakeClient.java
final class CompanionHandshakeClient {
    private static final Identifier CHANNEL = Identifier.fromNamespaceAndPath("arenasystem", "mod_handshake");
    private static CompanionHandshakeClient.Session session;

    private CompanionHandshakeClient() {
    }

    static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, minecraft) -> clearCurrentSession());
        PayloadTypeRegistry.clientboundPlay()
            .register(CompanionHandshakeClient.HandshakePayload.TYPE, CompanionHandshakeClient.HandshakePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay()
            .register(CompanionHandshakeClient.HandshakePayload.TYPE, CompanionHandshakeClient.HandshakePayload.CODEC);
        boolean registered = ClientPlayNetworking.registerGlobalReceiver(
            CompanionHandshakeClient.HandshakePayload.TYPE, (payload, context) -> handleMessage(payload.data())
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
        } else if (message.protocolVersion() != 2) {
            System.err
                .println(
                    "[ArenaSystem] Ignored unsupported handshake protocol version " + message.protocolVersion() + "."
                );
        } else if (message.type() == CompanionHandshakeProtocol.Type.SERVER_HELLO) {
            beginHandshake(message);
        } else {
            CompanionHandshakeClient.Session current = session;
            if (current != null && current.sessionId.equals(message.sessionId())) {
                try {
                    switch (message.type()) {
                        case AUTH_CHALLENGE:
                            handleAuthChallenge(current, message.body());
                            break;
                        case SERVER_CHALLENGE:
                            handleServerChallenge(current, message.body());
                            break;
                        case CONNECTION_ACK:
                            handleConnectionAck(current, message.body());
                            break;
                        default:
                            System.err
                                .println(
                                    "[ArenaSystem] Ignored unexpected server message "
                                        + message.type()
                                        + " during "
                                        + current.stage
                                        + "."
                                );
                    }
                } catch (IllegalArgumentException exception) {
                    clearSession(current);
                    System.err.println("[ArenaSystem] Handshake rejected: " + exception.getMessage());
                } catch (RuntimeException exception) {
                    clearSession(current);
                    System.err.println("[ArenaSystem] Handshake processing failed: " + exception.getMessage());
                }
            } else {
                System.err.println("[ArenaSystem] Ignored handshake message for an inactive session.");
            }
        }
    }

    private static void beginHandshake(CompanionHandshakeProtocol.Message message) {
        clearSession(session);
        CompanionHandshakeClient.Session next = new CompanionHandshakeClient.Session(
            message.sessionId(), message.body(), CompanionHandshakeProtocol.randomBytes(32)
        );
        session = next;
        byte[] clientHello = CompanionHandshakeProtocol.encodeClientHello(
            next.sessionId, next.serverNonce, next.clientNonce
        );
        if (send(clientHello)) {
            next.stage = CompanionHandshakeClient.Stage.WAITING_AUTH_CHALLENGE;
            System.out.println("[ArenaSystem] Sent CLIENT_HELLO for handshake session " + next.sessionId + ".");
        } else {
            clearSession(next);
        }
    }

    private static void handleAuthChallenge(CompanionHandshakeClient.Session current, byte[] body) {
        requireStage(current, CompanionHandshakeClient.Stage.WAITING_AUTH_CHALLENGE, "AUTH_CHALLENGE");
        current.sessionSecret = Arrays.copyOfRange(body, 0, 32);
        current.authNonce = Arrays.copyOfRange(body, 32, body.length);
        byte[] proof = CompanionHandshakeProtocol.createClientAuthProof(
            current.sessionSecret, current.sessionId, current.serverNonce, current.clientNonce, current.authNonce
        );
        if (send(CompanionHandshakeProtocol.encodeAuthenticate(current.sessionId, proof))) {
            current.stage = CompanionHandshakeClient.Stage.WAITING_SERVER_CHALLENGE;
        } else {
            clearSession(current);
        }
    }

    private static void handleServerChallenge(CompanionHandshakeClient.Session current, byte[] body) {
        requireStage(current, CompanionHandshakeClient.Stage.WAITING_SERVER_CHALLENGE, "SERVER_CHALLENGE");
        byte[] checkNonce = Arrays.copyOfRange(body, 0, 32);
        byte[] receivedServerProof = Arrays.copyOfRange(body, 32, body.length);
        byte[] expectedServerProof = CompanionHandshakeProtocol.createServerChallengeProof(
            current.sessionSecret,
            current.sessionId,
            current.serverNonce,
            current.clientNonce,
            current.authNonce,
            checkNonce
        );
        if (!CompanionHandshakeProtocol.constantTimeEquals(expectedServerProof, receivedServerProof)) {
            throw new IllegalArgumentException("SERVER_CHALLENGE proof verification failed.");
        }

        byte[] clientCheckProof = CompanionHandshakeProtocol.createClientConnectionCheckProof(
            current.sessionSecret,
            current.sessionId,
            current.serverNonce,
            current.clientNonce,
            current.authNonce,
            checkNonce
        );
        current.checkNonce = checkNonce;
        current.clientCheckProof = clientCheckProof;
        if (send(CompanionHandshakeProtocol.encodeConnectionCheck(current.sessionId, clientCheckProof))) {
            current.stage = CompanionHandshakeClient.Stage.WAITING_CONNECTION_ACK;
        } else {
            clearSession(current);
        }
    }

    private static void handleConnectionAck(CompanionHandshakeClient.Session current, byte[] body) {
        requireStage(current, CompanionHandshakeClient.Stage.WAITING_CONNECTION_ACK, "CONNECTION_ACK");
        byte[] expectedAck = CompanionHandshakeProtocol.createServerAckProof(
            current.sessionSecret,
            current.sessionId,
            current.serverNonce,
            current.clientNonce,
            current.authNonce,
            current.checkNonce,
            current.clientCheckProof
        );
        if (!CompanionHandshakeProtocol.constantTimeEquals(expectedAck, body)) {
            throw new IllegalArgumentException("CONNECTION_ACK proof verification failed.");
        }

        UUID completedSession = current.sessionId;
        clearSession(current);
        System.out
            .println("[ArenaSystem] Handshake Protocol v2 completed successfully for session " + completedSession + ".");
    }

    private static void requireStage(
        CompanionHandshakeClient.Session current, CompanionHandshakeClient.Stage expected, String messageName
    ) {
        if (current.stage != expected) {
            throw new IllegalArgumentException(messageName + " received during " + current.stage + ".");
        }
    }

    private static boolean send(byte[] data) {
        try {
            ClientPlayNetworking.send(new CompanionHandshakeClient.HandshakePayload(data));
            return true;
        } catch (RuntimeException exception) {
            System.err.println("[ArenaSystem] Could not send handshake message: " + exception.getMessage());
            return false;
        }
    }

    private static synchronized void clearCurrentSession() {
        clearSession(session);
    }

    private static void clearSession(CompanionHandshakeClient.Session target) {
        if (target != null) {
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
    }

    private static void wipe(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte)0);
        }
    }

    // $VF: Compiled from CompanionHandshakeClient.java
    private record HandshakePayload(byte[] data) implements CustomPacketPayload {
        private static final Type<CompanionHandshakeClient.HandshakePayload> TYPE = new Type<>(
            CompanionHandshakeClient.CHANNEL
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, CompanionHandshakeClient.HandshakePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBytes(payload.data), buffer -> {
                int length = buffer.readableBytes();
                if (length > 89) {
                    buffer.skipBytes(length);
                    return new CompanionHandshakeClient.HandshakePayload(new byte[0]);
                } else {
                    byte[] bytes = new byte[length];
                    buffer.readBytes(bytes);
                    return new CompanionHandshakeClient.HandshakePayload(bytes);
                }
            }
        );

        private HandshakePayload {
            if (data == null) {
                throw new IllegalArgumentException("Invalid ArenaSystem handshake payload.");
            }

            data = (byte[])data.clone();
        }

        public byte[] data() {
            return (byte[])this.data.clone();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // $VF: Compiled from CompanionHandshakeClient.java
    private static final class Session {
        private final UUID sessionId;
        private final byte[] serverNonce;
        private final byte[] clientNonce;
        private byte[] sessionSecret;
        private byte[] authNonce;
        private byte[] checkNonce;
        private byte[] clientCheckProof;
        private CompanionHandshakeClient.Stage stage = CompanionHandshakeClient.Stage.WAITING_SERVER_HELLO;

        private Session(UUID sessionId, byte[] serverNonce, byte[] clientNonce) {
            this.sessionId = sessionId;
            this.serverNonce = (byte[])serverNonce.clone();
            this.clientNonce = (byte[])clientNonce.clone();
        }
    }

    // $VF: Compiled from CompanionHandshakeClient.java
    private enum Stage {
        WAITING_SERVER_HELLO,
        WAITING_AUTH_CHALLENGE,
        WAITING_SERVER_CHALLENGE,
        WAITING_CONNECTION_ACK;
    }
}
