package id.hadziq.arenasystem.client;

// $VF: Compiled from KeybindModifierMatcher.java
final class KeybindModifierMatcher {
    static final int SHIFT = 1;
    static final int ALT = 2;
    static final int CONTROL = 4;

    private KeybindModifierMatcher() {
    }

    static boolean matchesExactly(int activeModifiers, int requiredModifiers) {
        return activeModifiers == requiredModifiers;
    }
}
