package id.hadziq.arenasystem.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class KeybindModifierMatcherTest {
    private KeybindModifierMatcherTest() {
    }

    public static void main(String[] args) throws Exception {
        assertEquals(1, invokeMask(true, false, false), "Shift mask");
        assertEquals(2, invokeMask(false, true, false), "Alt mask");
        assertEquals(4, invokeMask(false, false, true), "Control mask");
        assertEquals(3, invokeMask(true, true, false), "Shift + Alt mask");
        assertEquals(5, invokeMask(true, false, true), "Shift + Control mask");
        assertEquals(6, invokeMask(false, true, true), "Alt + Control mask");
        assertEquals(7, invokeMask(true, true, true), "combined mask");
        assertEquals(0, invokeMask(false, false, false), "empty mask");

        assertTrue(invokeIsModifier("key.keyboard.left.shift"), "left Shift is modifier");
        assertTrue(invokeIsModifier("key.keyboard.right.control"), "right Control is modifier");
        assertTrue(invokeIsModifier("key.keyboard.left.alt"), "left Alt is modifier");
        assertFalse(invokeIsModifier("key.keyboard.k"), "letter is not modifier");
        assertFalse(invokeIsModifier("key.keyboard.unknown"), "unknown key is not modifier");

        assertTrue(KeybindModifierMatcher.matchesExactly(5, 5), "exact modifier match");
        assertFalse(KeybindModifierMatcher.matchesExactly(7, 1), "extra modifier must not match");
        assertTrue(KeybindModifierMatcher.isValidMask(0), "zero mask is valid");
        assertTrue(KeybindModifierMatcher.isValidMask(7), "all supported modifiers are valid");
        assertFalse(KeybindModifierMatcher.isValidMask(8), "unsupported modifier bit is invalid");
        System.out.println("PASS: 18 modifier matcher assertions");
    }

    private static int invokeMask(boolean shift, boolean alt, boolean control) throws Exception {
        return (int) invoke("modifiersFromInput", new Class<?>[]{boolean.class, boolean.class, boolean.class}, shift, alt, control);
    }

    private static boolean invokeIsModifier(String keyName) throws Exception {
        return (boolean) invoke("isModifierKey", new Class<?>[]{String.class}, keyName);
    }

    private static Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = KeybindModifierMatcher.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) throw checked;
            if (cause instanceof Error error) throw error;
            throw exception;
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static void assertFalse(boolean value, String label) {
        assertTrue(!value, label);
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
