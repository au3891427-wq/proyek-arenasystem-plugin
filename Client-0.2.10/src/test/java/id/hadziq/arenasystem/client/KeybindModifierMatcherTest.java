package id.hadziq.arenasystem.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

public final class KeybindModifierMatcherTest {
    private static final String MIXIN_PACKAGE = "id.hadziq.arenasystem.client.mixin.";

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

        // Vanilla 26.3 returns early after processing a selected keyboard/mouse binding.
        // The mixin must run on every return path, not only at the final RETURN opcode.
        assertInjection(
            "KeyBindsScreenMixin",
            "arenasystem$applyKeyboardModifiers",
            "keyPressed",
            "RETURN"
        );
        assertInjection(
            "KeyBindsScreenMixin",
            "arenasystem$applyMouseModifiers",
            "mouseClicked",
            "RETURN"
        );

        // KeyMapping.resetMapping() also runs when a row is selected or rebound.
        // It must never reset ArenaSystem's modifier configuration.
        assertNoInjectionTarget("KeyMappingMixin", "resetMapping");

        // Modifier defaults may reset only when the actual footer Reset All button is pressed.
        assertInjection(
            "ButtonMixin",
            "arenasystem$resetModifiersForGlobalButton",
            "onPress",
            "HEAD"
        );
        assertAccessor("KeyBindsScreenAccessor", "arenasystem$getResetButton", "resetButton");
        assertResetButtonIdentityPolicy();

        System.out.println("PASS: 18 modifier assertions + 8 keybind UI regression assertions");
    }

    private static void assertInjection(
        String mixinName,
        String callbackName,
        String targetMethod,
        String expectedAt
    ) throws Exception {
        Class<?> mixin = loadMixin(mixinName);
        Method callback = findMethod(mixin, callbackName);
        Inject inject = callback.getAnnotation(Inject.class);
        assertTrue(inject != null, callbackName + " has @Inject");

        assertTrue(
            Arrays.asList(inject.method()).contains(targetMethod),
            callbackName + " targets " + targetMethod
        );

        boolean matchingPoint = Arrays.stream(inject.at()).anyMatch(point -> expectedAt.equals(point.value()));
        assertTrue(matchingPoint, callbackName + " injects at @" + expectedAt);
    }

    private static void assertNoInjectionTarget(String mixinName, String forbiddenTarget) throws Exception {
        Class<?> mixin = loadMixin(mixinName);
        for (Method method : mixin.getDeclaredMethods()) {
            Inject inject = method.getAnnotation(Inject.class);
            if (inject == null) continue;
            for (String target : inject.method()) {
                if (forbiddenTarget.equals(target)) {
                    throw new AssertionError(
                        mixinName + "." + method.getName() + " must not reset modifiers from " + forbiddenTarget
                    );
                }
            }
        }
    }

    private static void assertAccessor(String mixinName, String methodName, String expectedField) throws Exception {
        Method method = findMethod(loadMixin(mixinName), methodName);
        Accessor accessor = method.getAnnotation(Accessor.class);
        assertTrue(accessor != null, methodName + " has @Accessor");
        assertEquals(expectedField, accessor.value(), methodName + " targets " + expectedField);
    }

    private static void assertResetButtonIdentityPolicy() throws Exception {
        Class<?> mixin = loadMixin("ButtonMixin");
        Method policy = findMethod(mixin, "arenasystem$isGlobalResetButton");
        policy.setAccessible(true);

        Object resetButton = new Object();
        Object anotherButton = new Object();
        assertTrue((boolean) policy.invoke(null, resetButton, resetButton), "global Reset All button is accepted");
        assertFalse((boolean) policy.invoke(null, anotherButton, resetButton), "other buttons are ignored");
        assertFalse((boolean) policy.invoke(null, null, resetButton), "null button is ignored");
    }

    private static Class<?> loadMixin(String simpleName) {
        try {
            return Class.forName(MIXIN_PACKAGE + simpleName);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Missing required mixin " + simpleName, exception);
        }
    }

    private static Method findMethod(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
            .filter(method -> method.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing method " + type.getName() + "." + name));
    }

    private static int invokeMask(boolean shift, boolean alt, boolean control) throws Exception {
        return (int) invoke(
            "modifiersFromInput",
            new Class<?>[]{boolean.class, boolean.class, boolean.class},
            shift, alt, control
        );
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

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
