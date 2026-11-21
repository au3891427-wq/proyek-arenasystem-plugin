package id.hadziq.arenasystem.assassin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

/**
 * Client side of the staged ArenaSystem companion handshake.
 *
 * Wire format and HMAC transcript must remain in sync with the Paper plugin's
 * CompanionHandshakeProtocol. This proves participation in the expected
 * protocol and prevents cross-session replay; it does not attest that the
 * client is an unmodified official JAR.
 */
final class ArenaSystemHandshake {
    static final int PROTOCOL_VERSION = 2;

    private static final int MAGIC = 0x41535232; // "ASR2"
    private static final int HEADER_BYTES = Integer.BYTES + 1 + Integer.BYTES + 16;
    private static final int NONCE_BYTES = 32;
    private static final int SECRET_BYTES = 32;
    private static final int PROOF_BYTES = 32;
    private static final int MAX_MESSAGE_BYTES = 89;

    private static final Identifier CHANNEL_ID =
            Identifier.fromNamespaceAndPath("arenasystem", "mod_handshake");
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Object SESSION_LOCK = new Object();
    private static ClientSession currentSession;

    private ArenaSystemHandshake() {
    }

    static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(
                HandshakePayload.TYPE,
                HandshakePayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
                HandshakePayload.TYPE,
                HandshakePayload.CODEC
        );

        ClientPlayNetworking.registerGlobalReceiver(
                HandshakePayload.TYPE,
                (payload, context) -> handleServerMessage(payload.data())
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearSession());
    }

    private static void handleServerMessage(byte[] payload) {
        Message message = decode(payload);
        if (message == null) {
            System.err.println("[ArenaSystem] Ignoring malformed server handshake packet.");
            return;
        }

        if (message.protocolVersion() != PROTOCOL_VERSION) {
            clearSession();
            System.err.println(
                    "[ArenaSystem] Server handshake protocol " + message.protocolVersion()
                            + " is incompatible with client protocol " + PROTOCOL_VERSION + "."
            );
            return;
        }

        synchronized (SESSION_LOCK) {
            switch (message.type()) {
                case SERVER_HELLO -> handleServerHello(message);
                case AUTH_CHALLENGE -> handleAuthChallenge(message);
                case SERVER_CHALLENGE -> handleServerChallenge(message);
                case CONNECTION_ACK -> handleConnectionAck(message);
                default -> System.err.println(
                        "[ArenaSystem] Ignoring unexpected server handshake message: " + message.type()
                );
            }
        }
    }

    private static void handleServerHello(Message message) {
        byte[] body = message.body();
        if (body.length != NONCE_BYTES) {
            return;
        }

        byte[] serverNonce = body;
        byte[] clientNonce = randomBytes(NONCE_BYTES);
        ClientSession session = new ClientSession(message.sessionId(), serverNonce, clientNonce);
        currentSession = session;

        if (!send(encode(
                MessageType.CLIENT_HELLO,
                session.sessionId,
                concatenate(session.serverNonce, session.clientNonce)
        ))) {
            currentSession = null;
            System.err.println("[ArenaSystem] Could not send CLIENT_HELLO to the server.");
            return;
        }

        session.stage = Stage.AUTH_CHALLENGE_SENT;
    }

    private static void handleAuthChallenge(Message message) {
        ClientSession session = currentSession;
        if (!matches(session, message) || session.stage != Stage.AUTH_CHALLENGE_SENT) {
            return;
        }

        byte[] body = message.body();
        if (body.length != SECRET_BYTES + NONCE_BYTES) {
            return;
        }

        session.sessionSecret = Arrays.copyOfRange(body, 0, SECRET_BYTES);
        session.authNonce = Arrays.copyOfRange(body, SECRET_BYTES, body.length);

        byte[] proof = createClientAuthProof(
                session.sessionSecret,
                session.sessionId,
                session.serverNonce,
                session.clientNonce,
                session.authNonce
        );

        if (!send(encode(MessageType.AUTHENTICATE, session.sessionId, proof))) {
            currentSession = null;
            System.err.println("[ArenaSystem] Could not send AUTHENTICATE to the server.");
            return;
        }

        session.stage = Stage.SERVER_CHALLENGE_SENT;
    }

    private static void handleServerChallenge(Message message) {
        ClientSession session = currentSession;
        if (!matches(session, message) || session.stage != Stage.SERVER_CHALLENGE_SENT) {
            return;
        }

        byte[] body = message.body();
        if (body.length != NONCE_BYTES + PROOF_BYTES) {
            return;
        }

        byte[] checkNonce = Arrays.copyOfRange(body, 0, NONCE_BYTES);
        byte[] receivedServerProof = Arrays.copyOfRange(body, NONCE_BYTES, body.length);
        byte[] expectedServerProof = createServerChallengeProof(
                session.sessionSecret,
                session.sessionId,
                session.serverNonce,
                session.clientNonce,
                session.authNonce,
                checkNonce
        );

        if (!constantTimeEquals(expectedServerProof, receivedServerProof)) {
            currentSession = null;
            System.err.println("[ArenaSystem] Server challenge proof validation failed.");
            return;
        }

        session.checkNonce = checkNonce;
        session.clientCheckProof = createClientCheckProof(
                session.sessionSecret,
                session.sessionId,
                session.serverNonce,
                session.clientNonce,
                session.authNonce,
                session.checkNonce
        );

        if (!send(encode(MessageType.CONNECTION_CHECK, session.sessionId, session.clientCheckProof))) {
            currentSession = null;
            System.err.println("[ArenaSystem] Could not send CONNECTION_CHECK to the server.");
            return;
        }

        session.stage = Stage.CONNECTION_ACK_SENT;
    }

    private static void handleConnectionAck(Message message) {
        ClientSession session = currentSession;
        if (!matches(session, message) || session.stage != Stage.CONNECTION_ACK_SENT) {
            return;
        }

        byte[] receivedProof = message.body();
        if (receivedProof.length != PROOF_BYTES) {
            return;
        }

        byte[] expectedProof = createServerAckProof(
                session.sessionSecret,
                session.sessionId,
                session.serverNonce,
                session.clientNonce,
                session.authNonce,
                session.checkNonce,
                session.clientCheckProof
        );

        if (!constantTimeEquals(expectedProof, receivedProof)) {
            currentSession = null;
            System.err.println("[ArenaSystem] Connection acknowledgement validation failed.");
            return;
        }

        currentSession = null;
        System.out.println("[ArenaSystem] Companion handshake protocol v" + PROTOCOL_VERSION + " completed.");
    }

    private static boolean matches(ClientSession session, Message message) {
        return session != null && session.sessionId.equals(message.sessionId());
    }

    private static boolean send(byte[] bytes) {
        if (!ClientPlayNetworking.canSend(HandshakePayload.TYPE)) {
            return false;
        }
        ClientPlayNetworking.send(new HandshakePayload(bytes));
        return true;
    }

    private static void clearSession() {
        synchronized (SESSION_LOCK) {
            currentSession = null;
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static byte[] encode(MessageType type, UUID sessionId, byte[] body) {
        if (sessionId == null || body.length != type.bodyLength) {
            throw new IllegalArgumentException("Invalid ArenaSystem handshake packet");
        }

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + body.length).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(MAGIC);
        buffer.put((byte) type.id);
        buffer.putInt(PROTOCOL_VERSION);
        buffer.putLong(sessionId.getMostSignificantBits());
        buffer.putLong(sessionId.getLeastSignificantBits());
        buffer.put(body);
        return buffer.array();
    }

    private static Message decode(byte[] payload) {
        if (payload == null || payload.length < HEADER_BYTES || payload.length > MAX_MESSAGE_BYTES) {
            return null;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
            if (buffer.getInt() != MAGIC) {
                return null;
            }

            MessageType type = MessageType.fromId(Byte.toUnsignedInt(buffer.get()));
            if (type == null) {
                return null;
            }

            int protocolVersion = buffer.getInt();
            UUID sessionId = new UUID(buffer.getLong(), buffer.getLong());
            if (buffer.remaining() != type.bodyLength) {
                return null;
            }

            byte[] body = new byte[buffer.remaining()];
            buffer.get(body);
            return new Message(type, protocolVersion, sessionId, body);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static byte[] createClientAuthProof(
            byte[] secret,
            UUID sessionId,
            byte[] serverNonce,
            byte[] clientNonce,
            byte[] authNonce
    ) {
        return createProof("client-auth", secret, sessionId, serverNonce, clientNonce, authNonce);
    }

    private static byte[] createServerChallengeProof(
            byte[] secret,
            UUID sessionId,
            byte[] serverNonce,
            byte[] clientNonce,
            byte[] authNonce,
            byte[] checkNonce
    ) {
        return createProof("server-challenge", secret, sessionId, serverNonce, clientNonce, authNonce, checkNonce);
    }

    private static byte[] createClientCheckProof(
            byte[] secret,
            UUID sessionId,
            byte[] serverNonce,
            byte[] clientNonce,
            byte[] authNonce,
            byte[] checkNonce
    ) {
        return createProof("client-connection-check", secret, sessionId, serverNonce, clientNonce, authNonce, checkNonce);
    }

    private static byte[] createServerAckProof(
            byte[] secret,
            UUID sessionId,
            byte[] serverNonce,
            byte[] clientNonce,
            byte[] authNonce,
            byte[] checkNonce,
            byte[] clientCheckProof
    ) {
        return createProof("server-ack", secret, sessionId, serverNonce, clientNonce, authNonce, checkNonce, clientCheckProof);
    }

    private static byte[] createProof(String purpose, byte[] secret, UUID sessionId, byte[]... values) {
        if (secret == null || secret.length != SECRET_BYTES || sessionId == null) {
            throw new IllegalArgumentException("Invalid ArenaSystem proof input");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update("ArenaSystem/Handshake".getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);

            ByteBuffer header = ByteBuffer.allocate(Integer.BYTES + 16).order(ByteOrder.BIG_ENDIAN);
            header.putInt(PROTOCOL_VERSION);
            header.putLong(sessionId.getMostSignificantBits());
            header.putLong(sessionId.getLeastSignificantBits());
            mac.update(header.array());

            updateLengthPrefixed(mac, purpose.getBytes(StandardCharsets.UTF_8));
            for (byte[] value : values) {
                if (value == null || value.length != NONCE_BYTES) {
                    throw new IllegalArgumentException("Invalid ArenaSystem proof component");
                }
                updateLengthPrefixed(mac, value);
            }
            return mac.doFinal();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    private static void updateLengthPrefixed(Mac mac, byte[] value) {
        mac.update(ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN).putInt(value.length).array());
        mac.update(value);
    }

    private static boolean constantTimeEquals(byte[] expected, byte[] actual) {
        return expected != null && actual != null && MessageDigest.isEqual(expected, actual);
    }

    private static byte[] concatenate(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length = Math.addExact(length, part.length);
        }

        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private enum MessageType {
        SERVER_HELLO(1, NONCE_BYTES),
        CLIENT_HELLO(2, NONCE_BYTES * 2),
        AUTH_CHALLENGE(3, SECRET_BYTES + NONCE_BYTES),
        AUTHENTICATE(4, PROOF_BYTES),
        SERVER_CHALLENGE(5, NONCE_BYTES + PROOF_BYTES),
        CONNECTION_CHECK(6, PROOF_BYTES),
        CONNECTION_ACK(7, PROOF_BYTES);

        private final int id;
        private final int bodyLength;

        MessageType(int id, int bodyLength) {
            this.id = id;
            this.bodyLength = bodyLength;
        }

        private static MessageType fromId(int id) {
            for (MessageType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
        }
    }

    private record Message(MessageType type, int protocolVersion, UUID sessionId, byte[] body) {
        private Message {
            body = body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    private enum Stage {
        AUTH_CHALLENGE_SENT,
        SERVER_CHALLENGE_SENT,
        CONNECTION_ACK_SENT
    }

    private static final class ClientSession {
        private final UUID sessionId;
        private final byte[] serverNonce;
        private final byte[] clientNonce;
        private Stage stage;
        private byte[] sessionSecret;
        private byte[] authNonce;
        private byte[] checkNonce;
        private byte[] clientCheckProof;

        private ClientSession(UUID sessionId, byte[] serverNonce, byte[] clientNonce) {
            this.sessionId = sessionId;
            this.serverNonce = serverNonce.clone();
            this.clientNonce = clientNonce.clone();
        }
    }

    private record HandshakePayload(byte[] data) implements CustomPacketPayload {
        private static final Type<HandshakePayload> TYPE = new Type<>(CHANNEL_ID);

        private static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC =
                StreamCodec.of(
                        (buffer, payload) -> buffer.writeBytes(payload.data),
                        buffer -> {
                            byte[] data = new byte[buffer.readableBytes()];
                            buffer.readBytes(data);
                            return new HandshakePayload(data);
                        }
                );

        private HandshakePayload {
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
