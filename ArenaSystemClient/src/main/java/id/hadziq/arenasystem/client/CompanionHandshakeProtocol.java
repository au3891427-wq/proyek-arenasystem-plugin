package id.hadziq.arenasystem.client;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;

final class CompanionHandshakeProtocol {
    static final int PROTOCOL_VERSION = 2;
    static final int HEADER_BYTES = 25;
    static final int NONCE_BYTES = 32;
    static final int SECRET_BYTES = 32;
    static final int PROOF_BYTES = 32;
    static final int MAX_MESSAGE_BYTES = 89;

    private static final int MAGIC = 1_095_979_570;
    private static final byte[] HMAC_PREFIX = "ArenaSystem/Handshake".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private CompanionHandshakeProtocol() {
    }

    static byte[] randomBytes(int length) {
        if (length <= 0 || length > NONCE_BYTES) {
            throw new IllegalArgumentException("Invalid random byte count: " + length);
        }
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    static byte[] encodeServerHello(UUID sessionId, byte[] serverNonce) {
        return encode(Type.SERVER_HELLO, sessionId, serverNonce);
    }

    static byte[] encodeClientHello(UUID sessionId, byte[] serverNonce, byte[] clientNonce) {
        return encode(Type.CLIENT_HELLO, sessionId, serverNonce, clientNonce);
    }

    static byte[] encodeAuthenticate(UUID sessionId, byte[] proof) {
        return encode(Type.AUTHENTICATE, sessionId, proof);
    }

    static byte[] encodeConnectionCheck(UUID sessionId, byte[] proof) {
        return encode(Type.CONNECTION_CHECK, sessionId, proof);
    }

    static Message decode(byte[] payload) {
        if (payload == null || payload.length < HEADER_BYTES || payload.length > MAX_MESSAGE_BYTES) {
            return null;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
            if (buffer.getInt() != MAGIC) {
                return null;
            }
            Type type = Type.fromId(Byte.toUnsignedInt(buffer.get()));
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

    static byte[] createClientAuthProof(
            byte[] secret, UUID sessionId, byte[] serverNonce, byte[] clientNonce, byte[] authNonce) {
        return createProof("client-auth", secret, sessionId, serverNonce, clientNonce, authNonce);
    }

    static byte[] createServerChallengeProof(
            byte[] secret, UUID sessionId, byte[] serverNonce, byte[] clientNonce,
            byte[] authNonce, byte[] checkNonce) {
        return createProof("server-challenge", secret, sessionId,
                serverNonce, clientNonce, authNonce, checkNonce);
    }

    static byte[] createClientConnectionCheckProof(
            byte[] secret, UUID sessionId, byte[] serverNonce, byte[] clientNonce,
            byte[] authNonce, byte[] checkNonce) {
        return createProof("client-connection-check", secret, sessionId,
                serverNonce, clientNonce, authNonce, checkNonce);
    }

    static byte[] createServerAckProof(
            byte[] secret, UUID sessionId, byte[] serverNonce, byte[] clientNonce,
            byte[] authNonce, byte[] checkNonce, byte[] clientCheckProof) {
        return createProof("server-ack", secret, sessionId,
                serverNonce, clientNonce, authNonce, checkNonce, clientCheckProof);
    }

    static boolean constantTimeEquals(byte[] expected, byte[] actual) {
        return expected != null && actual != null && MessageDigest.isEqual(expected, actual);
    }

    private static byte[] encode(Type type, UUID sessionId, byte[]... parts) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        if (parts.length != type.partLengths.length) {
            throw new IllegalArgumentException("Invalid body part count for " + type);
        }
        int bodyLength = 0;
        for (int i = 0; i < parts.length; i++) {
            requireLength(parts[i], type.partLengths[i], "body part");
            bodyLength = Math.addExact(bodyLength, parts[i].length);
        }
        if (bodyLength != type.bodyLength) {
            throw new IllegalArgumentException("Invalid body size for " + type);
        }

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + bodyLength).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(MAGIC);
        buffer.put((byte) type.id);
        buffer.putInt(PROTOCOL_VERSION);
        buffer.putLong(sessionId.getMostSignificantBits());
        buffer.putLong(sessionId.getLeastSignificantBits());
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }

    private static byte[] createProof(String purpose, byte[] secret, UUID sessionId, byte[]... values) {
        requireLength(secret, SECRET_BYTES, "secret");
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(HMAC_PREFIX);
            mac.update((byte) 0);

            ByteBuffer header = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN);
            header.putInt(PROTOCOL_VERSION);
            header.putLong(sessionId.getMostSignificantBits());
            header.putLong(sessionId.getLeastSignificantBits());
            mac.update(header.array());

            updateLengthPrefixed(mac, purpose.getBytes(StandardCharsets.UTF_8));
            for (byte[] value : values) {
                requireLength(value, NONCE_BYTES, "proof input");
                updateLengthPrefixed(mac, value);
            }
            return mac.doFinal();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    private static void updateLengthPrefixed(Mac mac, byte[] value) {
        mac.update(ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN)
                .putInt(value.length).array());
        mac.update(value);
    }

    private static void requireLength(byte[] value, int expected, String name) {
        if (value == null || value.length != expected) {
            throw new IllegalArgumentException(name + " must be exactly " + expected + " bytes");
        }
    }

    record Message(Type type, int protocolVersion, UUID sessionId, byte[] body) {
        Message {
            if (type == null || sessionId == null || body == null || body.length != type.bodyLength) {
                throw new IllegalArgumentException("Invalid handshake message fields");
            }
            body = body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    enum Type {
        SERVER_HELLO(1, 32, 32),
        CLIENT_HELLO(2, 64, 32, 32),
        AUTH_CHALLENGE(3, 64, 32, 32),
        AUTHENTICATE(4, 32, 32),
        SERVER_CHALLENGE(5, 64, 32, 32),
        CONNECTION_CHECK(6, 32, 32),
        CONNECTION_ACK(7, 32, 32);

        private final int id;
        private final int bodyLength;
        private final int[] partLengths;

        Type(int id, int bodyLength, int... partLengths) {
            this.id = id;
            this.bodyLength = bodyLength;
            this.partLengths = partLengths;
        }

        static Type fromId(int id) {
            for (Type type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
        }
    }
}
