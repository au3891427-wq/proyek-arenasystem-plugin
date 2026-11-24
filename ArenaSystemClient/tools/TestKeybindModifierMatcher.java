package id.hadziq.arenasystem.client;

public final class TestKeybindModifierMatcher {
    private TestKeybindModifierMatcher() {
    }

    public static void main(String[] args) {
        assertMatch("Shift", KeybindModifierMatcher.SHIFT, KeybindModifierMatcher.SHIFT, true);
        assertMatch("Alt", KeybindModifierMatcher.ALT, KeybindModifierMatcher.ALT, true);
        assertMatch("Control", KeybindModifierMatcher.CONTROL, KeybindModifierMatcher.CONTROL, true);

        assertMatch("missing modifier", 0, KeybindModifierMatcher.SHIFT, false);
        assertMatch("wrong modifier", KeybindModifierMatcher.ALT, KeybindModifierMatcher.SHIFT, false);
        assertMatch("extra modifier", KeybindModifierMatcher.SHIFT | KeybindModifierMatcher.CONTROL,
                KeybindModifierMatcher.SHIFT, false);
        assertMatch("extra modifier on Control chord", KeybindModifierMatcher.ALT | KeybindModifierMatcher.CONTROL,
                KeybindModifierMatcher.CONTROL, false);

        System.out.println("Keybind modifier matcher tests passed.");
    }

    private static void assertMatch(String name, int active, int required, boolean expected) {
        boolean actual = KeybindModifierMatcher.matchesExactly(active, required);
        if (actual != expected) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
