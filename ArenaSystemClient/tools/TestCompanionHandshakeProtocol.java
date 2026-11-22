package id.hadziq.arenasystem.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class TestCompanionHandshakeProtocol {
    private TestCompanionHandshakeProtocol() {
    }

    public static void main(String[] args) {
        byte[] challenge = ByteBuffer.allocate(16)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(0x0123456789ABCDEFL)
                .putLong(0xFEDCBA9876543210L)
                .array();

        byte[] response = CompanionHandshakeProtocol.createResponse(challenge);
        if (response.length != CompanionHandshakeProtocol.RESPONSE_BYTES) {
            throw new AssertionError("Response must be exactly 20 bytes.");
        }

        ByteBuffer decoded = ByteBuffer.wrap(response).order(ByteOrder.BIG_ENDIAN);
        if (decoded.getInt() != CompanionHandshakeProtocol.PROTOCOL_VERSION) {
            throw new AssertionError("Wrong handshake protocol version.");
        }

        byte[] echoedNonce = new byte[CompanionHandshakeProtocol.CHALLENGE_BYTES];
        decoded.get(echoedNonce);
        if (!Arrays.equals(challenge, echoedNonce)) {
            throw new AssertionError("Response did not echo the challenge UUID bytes exactly.");
        }

        assertInvalid(null);
        assertInvalid(new byte[15]);
        assertInvalid(new byte[17]);

        System.out.println("ArenaSystem companion handshake protocol tests passed.");
    }

    private static void assertInvalid(byte[] challenge) {
        try {
            CompanionHandshakeProtocol.createResponse(challenge);
            throw new AssertionError("Invalid challenge was accepted.");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
