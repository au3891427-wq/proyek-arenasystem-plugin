package id.hadziq.arenasystem.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Versioned client-to-server input message for class skills and Assassin off-hand.
 */
final class SkillInputProtocol {
    static final int MESSAGE_BYTES = 6;
    static final byte ACTION_PRIMARY = 1;
    static final byte ACTION_SECONDARY = 2;
    static final byte ACTION_ASSASSIN_OFFHAND = 3;

    private static final int MAGIC = 0x41525349; // "ARSI"
    private static final byte VERSION = 1;

    private SkillInputProtocol() {
    }

    static byte[] encode(byte action) {
        if (action != ACTION_PRIMARY && action != ACTION_SECONDARY && action != ACTION_ASSASSIN_OFFHAND) {
            throw new IllegalArgumentException("Unknown ArenaSystem skill action: " + action);
        }

        return ByteBuffer.allocate(MESSAGE_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(MAGIC)
                .put(VERSION)
                .put(action)
                .array();
    }
}
