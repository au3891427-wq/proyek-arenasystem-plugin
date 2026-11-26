package id.hadziq.arenasystem.client;

// $VF: Compiled from KeybindModifierMatcher.java
public final class KeybindModifierMatcher {
    public static final int SHIFT = 1;
    public static final int ALT = 2;
    public static final int CONTROL = 4;
    static final int VALID_MASK = 7;

    private KeybindModifierMatcher() {
    }

    static boolean matchesExactly(int activeModifiers, int requiredModifiers) {
        return activeModifiers == requiredModifiers;
    }

    static boolean matchesConfiguredChord(int activeModifiers, int requiredModifiers) {
        return requiredModifiers == 0 || matchesExactly(activeModifiers, requiredModifiers);
    }

    public static boolean bindingsConflict(boolean sameKey, int firstModifiers, int secondModifiers) {
        return sameKey && firstModifiers == secondModifiers;
    }

    public static int modifiersFromInput(boolean shift, boolean alt, boolean control) {
        int modifiers = 0;
        if (shift) {
            modifiers |= 1;
        }

        if (alt) {
            modifiers |= 2;
        }

        if (control) {
            modifiers |= 4;
        }

        return modifiers;
    }

    public static boolean isModifierKey(String keyName) {
        return keyName != null
            && (keyName.endsWith(".shift") || keyName.endsWith(".control") || keyName.endsWith(".alt"));
    }

    static boolean isValidMask(int modifiers) {
        return (modifiers & -8) == 0;
    }
}
