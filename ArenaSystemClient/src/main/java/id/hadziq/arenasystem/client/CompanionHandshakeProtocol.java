package id.hadziq.arenasystem.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Binary protocol shared with the ArenaSystem Bukkit CompanionHandshakeProtocol.
 */
final class CompanionHandshakeProtocol {
    static final int PROTOCOL_VERSION = 1;
    static final int CHALLENGE_BYTES = 16;
    static final int RESPONSE_BYTES = 20;

    private CompanionHandshakeProtocol() {
    }

    static byte[] createResponse(byte[] challenge) {
        if (challenge == null || challenge.length != CHALLENGE_BYTES) {
            throw new IllegalArgumentException(
                    "Challenge must contain exactly " + CHALLENGE_BYTES + " bytes.");
        }

        ByteBuffer challengeBuffer = ByteBuffer.wrap(challenge).order(ByteOrder.BIG_ENDIAN);
        UUID nonce = new UUID(challengeBuffer.getLong(), challengeBuffer.getLong());

        return ByteBuffer.allocate(RESPONSE_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(PROTOCOL_VERSION)
                .putLong(nonce.getMostSignificantBits())
                .putLong(nonce.getLeastSignificantBits())
                .array();
    }
}
