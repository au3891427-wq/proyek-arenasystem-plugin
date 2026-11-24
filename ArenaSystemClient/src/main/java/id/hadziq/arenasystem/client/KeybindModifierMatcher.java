package id.hadziq.arenasystem.client;

/** Matches an exact set of modifier keys for an ArenaSystem keybind chord. */
final class KeybindModifierMatcher {
    static final int SHIFT = 1;
    static final int ALT = 1 << 1;
    static final int CONTROL = 1 << 2;

    private KeybindModifierMatcher() {
    }

    static boolean matchesExactly(int activeModifiers, int requiredModifiers) {
        return activeModifiers == requiredModifiers;
    }
}
