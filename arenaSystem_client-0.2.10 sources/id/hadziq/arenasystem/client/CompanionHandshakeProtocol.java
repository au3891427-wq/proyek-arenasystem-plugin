package id.hadziq.arenasystem.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// $VF: Compiled from CompanionHandshakeProtocol.java
final class CompanionHandshakeProtocol {
    static final int PROTOCOL_VERSION = 2;
    static final int HEADER_BYTES = 25;
    static final int NONCE_BYTES = 32;
    static final int SECRET_BYTES = 32;
    static final int PROOF_BYTES = 32;
    static final int MAX_MESSAGE_BYTES = 89;
    private static final int MAGIC = 1095979570;
    private static final byte[] HMAC_PREFIX = "ArenaSystem/Handshake".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private CompanionHandshakeProtocol() {
    }

    static byte[] randomBytes(int length) {
        if (length > 0 && length <= 32) {
            byte[] bytes = new byte[length];
            RANDOM.nextBytes(bytes);
            return bytes;
        } else {
            throw new IllegalArgumentException("Invalid random byte count: " + length);
        }
    }

    static byte[] encodeServerHello(UUID sessionId, byte[] serverNonce) {
        return encode(CompanionHandshakeProtocol.Type.SERVER_HELLO, sessionId, serverNonce);
    }

    static byte[] encodeClientHello(UUID sessionId, byte[] serverNonce, byte[] clientNonce) {
        return encode(CompanionHandshakeProtocol.Type.CLIENT_HELLO, sessionId, serverNonce, clientNonce);
    }

    static byte[] encodeAuthenticate(UUID sessionId, byte[] proof) {
        return encode(CompanionHandshakeProtocol.Type.AUTHENTICATE, sessionId, proof);
    }

    static byte[] encodeConnectionCheck(UUID sessionId, byte[] proof) {
        return encode(CompanionHandshakeProtocol.Type.CONNECTION_CHECK, sessionId, proof);
    }

    static CompanionHandshakeProtocol.Message decode(byte[] payload) {
        if (payload != null && payload.length >= 25 && payload.length <= 89) {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
                if (buffer.getInt() != 1095979570) {
                    return null;
                }

                CompanionHandshakeProtocol.Type type = CompanionHandshakeProtocol.Type.fromId(
                    Byte.toUnsignedInt(buffer.get())
                );
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
                return new CompanionHandshakeProtocol.Message(type, protocolVersion, sessionId, body);
            } catch (RuntimeException exception) {
                return null;
            }
        } else {
            return null;
        }
    }

    static byte[] createClientAuthProof(
        byte[] secret, UUID sessionId, byte[] serverNonce, byte[] clientNonce, byte[] authNonce
    ) {
        return createProof("client-auth", secret, sessionId, serverNonce, clientNonce, authNonce);
    }

    static byte[] createServerChallengeProof(
        byte[] secret, UUID sessionId, byte[] serverNonce, byte[] clientNonce, byte[] authNonce, byte[] checkNonce
    ) {
        return createProof("server-challenge", secret, sessionId, serverNonce, clientNonce, authNonce, checkNonce);
    }

    static byte[] createClientConnectionCheckProof(
        byte[] secret, UUID sessionId, byte[] serverNonce, byte[] clientNonce, byte[] authNonce, byte[] checkNonce
    ) {
        return createProof(
            "client-connection-check", secret, sessionId, serverNonce, clientNonce, authNonce, checkNonce
        );
    }

    static byte[] createServerAckProof(
        byte[] secret,
        UUID sessionId,
        byte[] serverNonce,
        byte[] clientNonce,
        byte[] authNonce,
        byte[] checkNonce,
        byte[] clientCheckProof
    ) {
        return createProof(
            "server-ack", secret, sessionId, serverNonce, clientNonce, authNonce, checkNonce, clientCheckProof
        );
    }

    static boolean constantTimeEquals(byte[] expected, byte[] actual) {
        return expected != null && actual != null && MessageDigest.isEqual(expected, actual);
    }

    private static byte[] encode(CompanionHandshakeProtocol.Type type, UUID sessionId, byte[]... parts) {
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

        ByteBuffer buffer = ByteBuffer.allocate(25 + bodyLength).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(1095979570);
        buffer.put((byte)type.id);
        buffer.putInt(2);
        buffer.putLong(sessionId.getMostSignificantBits());
        buffer.putLong(sessionId.getLeastSignificantBits());

        for (byte[] part : parts) {
            buffer.put(part);
        }

        return buffer.array();
    }

    private static byte[] createProof(String purpose, byte[] secret, UUID sessionId, byte[]... values) {
        requireLength(secret, 32, "secret");
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(HMAC_PREFIX);
            mac.update((byte)0);
            ByteBuffer header = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN);
            header.putInt(2);
            header.putLong(sessionId.getMostSignificantBits());
            header.putLong(sessionId.getLeastSignificantBits());
            mac.update(header.array());
            updateLengthPrefixed(mac, purpose.getBytes(StandardCharsets.UTF_8));

            for (byte[] value : values) {
                requireLength(value, 32, "proof input");
                updateLengthPrefixed(mac, value);
            }

            return mac.doFinal();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    private static void updateLengthPrefixed(Mac mac, byte[] value) {
        mac.update(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value.length).array());
        mac.update(value);
    }

    private static void requireLength(byte[] value, int expected, String name) {
        if (value == null || value.length != expected) {
            throw new IllegalArgumentException(name + " must be exactly " + expected + " bytes");
        }
    }

    // $VF: Compiled from CompanionHandshakeProtocol.java
    record Message(CompanionHandshakeProtocol.Type type, int protocolVersion, UUID sessionId, byte[] body) {
        Message(CompanionHandshakeProtocol.Type type, int protocolVersion, UUID sessionId, byte[] body) {
            if (type != null && sessionId != null && body != null && body.length == type.bodyLength) {
                body = (byte[])body.clone();
                this.type = type;
                this.protocolVersion = protocolVersion;
                this.sessionId = sessionId;
                this.body = body;
            } else {
                throw new IllegalArgumentException("Invalid handshake message fields");
            }
        }

        public byte[] body() {
            return (byte[])this.body.clone();
        }
    }

    // $VF: Compiled from CompanionHandshakeProtocol.java
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

        static CompanionHandshakeProtocol.Type fromId(int id) {
            for (CompanionHandshakeProtocol.Type type : values()) {
                if (type.id == id) {
                    return type;
                }
            }

            return null;
        }
    }
}
