package id.hadziq.arenasystem.client;

import java.util.Arrays;

public final class TestSkillInputProtocol {
    private TestSkillInputProtocol() {
    }

    public static void main(String[] args) {
        byte[] primary = SkillInputProtocol.encode(SkillInputProtocol.ACTION_PRIMARY);
        byte[] secondary = SkillInputProtocol.encode(SkillInputProtocol.ACTION_SECONDARY);
        byte[] assassinOffhand = SkillInputProtocol.encode(SkillInputProtocol.ACTION_ASSASSIN_OFFHAND);

        assertEquals("primary", primary, new byte[] {
                0x41, 0x52, 0x53, 0x49, 0x01, 0x01
        });
        assertEquals("secondary", secondary, new byte[] {
                0x41, 0x52, 0x53, 0x49, 0x01, 0x02
        });
        assertEquals("Assassin off-hand", assassinOffhand, new byte[] {
                0x41, 0x52, 0x53, 0x49, 0x01, 0x03
        });

        assertInvalid((byte) 0);
        assertInvalid((byte) 4);
        System.out.println("ArenaSystem skill input protocol tests passed.");
    }

    private static void assertEquals(String name, byte[] actual, byte[] expected) {
        if (!Arrays.equals(actual, expected)) {
            throw new AssertionError(name + " message mismatch: " + Arrays.toString(actual));
        }
    }

    private static void assertInvalid(byte action) {
        try {
            SkillInputProtocol.encode(action);
            throw new AssertionError("Unknown action was accepted: " + action);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
