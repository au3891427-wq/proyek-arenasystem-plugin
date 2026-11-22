package id.hadziq.arenasystem.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

public final class TestCompanionHandshakeProtocol {
    private static final UUID SESSION_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");

    private TestCompanionHandshakeProtocol() {
    }

    public static void main(String[] args) {
        byte[] serverNonce = sequence(0);
        byte[] clientNonce = sequence(32);
        byte[] authNonce = sequence(64);
        byte[] checkNonce = sequence(96);
        byte[] secret = sequence(160);
        byte[] clientProof = filled(0x55);

        assertMessage(CompanionHandshakeProtocol.encodeServerHello(SESSION_ID, serverNonce),
                CompanionHandshakeProtocol.Type.SERVER_HELLO, 57);
        assertMessage(CompanionHandshakeProtocol.encodeClientHello(SESSION_ID, serverNonce, clientNonce),
                CompanionHandshakeProtocol.Type.CLIENT_HELLO, 89);
        assertMessage(CompanionHandshakeProtocol.encodeAuthenticate(SESSION_ID,
                CompanionHandshakeProtocol.createClientAuthProof(secret, SESSION_ID, serverNonce, clientNonce, authNonce)),
                CompanionHandshakeProtocol.Type.AUTHENTICATE, 57);
        assertMessage(CompanionHandshakeProtocol.encodeConnectionCheck(SESSION_ID,
                CompanionHandshakeProtocol.createClientConnectionCheckProof(
                        secret, SESSION_ID, serverNonce, clientNonce, authNonce, checkNonce)),
                CompanionHandshakeProtocol.Type.CONNECTION_CHECK, 57);
        int[] messageBodyLengths = {32, 64, 64, 32, 64, 32, 32};
        for (int i = 0; i < messageBodyLengths.length; i++) {
            assertDecodedType(i + 1, messageBodyLengths[i]);
        }

        assertHex("client-auth",
                CompanionHandshakeProtocol.createClientAuthProof(secret, SESSION_ID, serverNonce, clientNonce, authNonce),
                "0b1718343d9f815cf3cfe439d4b7ffef0c75d12937c703887837134a0ecb638b");
        assertHex("server-challenge",
                CompanionHandshakeProtocol.createServerChallengeProof(
                        secret, SESSION_ID, serverNonce, clientNonce, authNonce, checkNonce),
                "1fc7f06563c0b754d99b4e758e47b34ef838e6aceb4f3982db736a4961787133");
        assertHex("client-connection-check",
                CompanionHandshakeProtocol.createClientConnectionCheckProof(
                        secret, SESSION_ID, serverNonce, clientNonce, authNonce, checkNonce),
                "d2a9b979d2bf51e12efb60d72500e63028d332c40ac13b7cd864d0e261d5bb18");
        assertHex("server-ack",
                CompanionHandshakeProtocol.createServerAckProof(
                        secret, SESSION_ID, serverNonce, clientNonce, authNonce, checkNonce, clientProof),
                "4e22ec71a911eb79e63e4a3816b022983240679c89e8fafcdac15e409470c152");

        byte[] valid = CompanionHandshakeProtocol.encodeServerHello(SESSION_ID, serverNonce);
        byte[] badMagic = valid.clone();
        badMagic[0] ^= 1;
        assertRejected(badMagic);
        byte[] badType = valid.clone();
        badType[4] = (byte) 99;
        assertRejected(badType);
        byte[] badVersion = valid.clone();
        ByteBuffer.wrap(badVersion).order(ByteOrder.BIG_ENDIAN).putInt(5, 1);
        CompanionHandshakeProtocol.Message decodedBadVersion = CompanionHandshakeProtocol.decode(badVersion);
        if (decodedBadVersion == null || decodedBadVersion.protocolVersion() != 1) {
            throw new AssertionError("Decoder must preserve version for explicit compatibility handling.");
        }
        assertRejected(Arrays.copyOf(valid, valid.length - 1));
        assertRejected(new byte[CompanionHandshakeProtocol.MAX_MESSAGE_BYTES + 1]);
        assertRejected(null);

        if (CompanionHandshakeProtocol.constantTimeEquals(serverNonce, clientNonce)) {
            throw new AssertionError("Different nonce values unexpectedly compared equal.");
        }
        if (!CompanionHandshakeProtocol.constantTimeEquals(serverNonce, serverNonce.clone())) {
            throw new AssertionError("Equal nonce values did not compare equal.");
        }
        assertInvalid(() -> CompanionHandshakeProtocol.encodeServerHello(SESSION_ID, new byte[16]));
        assertInvalid(() -> CompanionHandshakeProtocol.createClientAuthProof(
                new byte[16], SESSION_ID, serverNonce, clientNonce, authNonce));

        System.out.println("ArenaSystem Handshake Protocol v2 tests passed.");
    }

    private static void assertDecodedType(int messageId, int bodyLength) {
        ByteBuffer packet = ByteBuffer.allocate(CompanionHandshakeProtocol.HEADER_BYTES + bodyLength)
                .order(ByteOrder.BIG_ENDIAN);
        packet.putInt(1_095_979_570);
        packet.put((byte) messageId);
        packet.putInt(CompanionHandshakeProtocol.PROTOCOL_VERSION);
        packet.putLong(SESSION_ID.getMostSignificantBits());
        packet.putLong(SESSION_ID.getLeastSignificantBits());
        packet.put(new byte[bodyLength]);
        CompanionHandshakeProtocol.Message decoded = CompanionHandshakeProtocol.decode(packet.array());
        if (decoded == null || decoded.type().ordinal() != messageId - 1) {
            throw new AssertionError("Could not decode protocol message type " + messageId);
        }
    }

    private static void assertMessage(byte[] encoded, CompanionHandshakeProtocol.Type type, int expectedLength) {
        if (encoded.length != expectedLength) {
            throw new AssertionError(type + " has incorrect encoded length: " + encoded.length);
        }
        CompanionHandshakeProtocol.Message decoded = CompanionHandshakeProtocol.decode(encoded);
        if (decoded == null || decoded.type() != type
                || decoded.protocolVersion() != CompanionHandshakeProtocol.PROTOCOL_VERSION
                || !decoded.sessionId().equals(SESSION_ID)) {
            throw new AssertionError(type + " did not round-trip correctly.");
        }
    }

    private static void assertHex(String purpose, byte[] actual, String expected) {
        StringBuilder hex = new StringBuilder(actual.length * 2);
        for (byte value : actual) {
            hex.append(String.format("%02x", value & 0xff));
        }
        if (!expected.contentEquals(hex)) {
            throw new AssertionError(purpose + " HMAC mismatch: " + hex);
        }
    }

    private static byte[] sequence(int start) {
        byte[] result = new byte[32];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (start + i);
        }
        return result;
    }

    private static byte[] filled(int value) {
        byte[] result = new byte[32];
        Arrays.fill(result, (byte) value);
        return result;
    }

    private static void assertRejected(byte[] payload) {
        if (CompanionHandshakeProtocol.decode(payload) != null) {
            throw new AssertionError("Malformed handshake payload was accepted.");
        }
    }

    private static void assertInvalid(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Invalid protocol input was accepted.");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
