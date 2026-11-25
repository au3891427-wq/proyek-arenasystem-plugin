package id.hadziq.arenasystem.client;

// $VF: Compiled from KeybindModifierMatcher.java
public final class KeybindModifierMatcher {
    public static final int SHIFT = 1;
    public static final int ALT = 2;
    public static final int CONTROL = 4;
    static final int VALID_MASK = SHIFT | ALT | CONTROL;

    private KeybindModifierMatcher() {
    }

    static boolean matchesExactly(int activeModifiers, int requiredModifiers) {
        return activeModifiers == requiredModifiers;
    }

    public static int modifiersFromInput(boolean shift, boolean alt, boolean control) {
        int modifiers = 0;
        if (shift) modifiers |= SHIFT;
        if (alt) modifiers |= ALT;
        if (control) modifiers |= CONTROL;
        return modifiers;
    }

    public static boolean isModifierKey(String keyName) {
        if (keyName == null) return false;
        return keyName.endsWith(".shift") || keyName.endsWith(".control") || keyName.endsWith(".alt");
    }

    static boolean isValidMask(int modifiers) {
        return (modifiers & ~VALID_MASK) == 0;
    }
}
