package id.hadziq.arenasystem.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// $VF: Compiled from SkillInputProtocol.java
final class SkillInputProtocol {
    static final int MESSAGE_BYTES = 6;
    static final byte ACTION_PRIMARY = 1;
    static final byte ACTION_SECONDARY = 2;
    static final byte ACTION_ASSASSIN_OFFHAND = 3;
    static final byte ACTION_SNIPER_AIM_LOCK = 4;
    private static final int MAGIC = 1095914313;
    private static final byte VERSION = 1;

    private SkillInputProtocol() {
    }

    static byte[] encode(byte action) {
        if (action != 1 && action != 2 && action != 3 && action != 4) {
            throw new IllegalArgumentException("Unknown ArenaSystem skill action: " + action);
        } else {
            return ByteBuffer.allocate(6)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(1095914313)
                .put((byte)1)
                .put(action)
                .array();
        }
    }
}
