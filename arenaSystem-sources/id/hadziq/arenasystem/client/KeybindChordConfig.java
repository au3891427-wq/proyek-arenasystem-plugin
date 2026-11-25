package id.hadziq.arenasystem.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;

// $VF: Compiled from KeybindChordConfig.java
public final class KeybindChordConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "arenasystem-keybinds.json";
    private static final Map<String, Integer> DEFAULTS = createDefaults();
    private static final Map<String, Integer> REQUIRED_MODIFIERS = new LinkedHashMap<>(DEFAULTS);
    private static final Map<String, Integer> PRESSED_MODIFIERS = new HashMap<>();

    private KeybindChordConfig() {
    }

    static void load() {
        REQUIRED_MODIFIERS.clear();
        REQUIRED_MODIFIERS.putAll(DEFAULTS);
        PRESSED_MODIFIERS.clear();
        Path file = getConfigFile();
        if (Files.isRegularFile(file)) {
            try {
                JsonElement rootElement = (JsonElement)GSON.fromJson(
                    Files.readString(file, StandardCharsets.UTF_8), JsonElement.class
                );
                if (rootElement == null || !rootElement.isJsonObject()) {
                    return;
                }

                JsonElement bindingsElement = rootElement.getAsJsonObject().get("bindings");
                if (bindingsElement == null || !bindingsElement.isJsonObject()) {
                    return;
                }

                JsonObject bindings = bindingsElement.getAsJsonObject();

                for (Entry<String, JsonElement> entry : bindings.entrySet()) {
                    String key = entry.getKey();
                    JsonElement value = entry.getValue();
                    if (DEFAULTS.containsKey(key)
                        && value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isNumber()) {
                        double numericMask = value.getAsDouble();
                        if (numericMask == Math.rint(numericMask) && !(numericMask < 0.0) && !(numericMask > 7.0)) {
                            int mask = (int)numericMask;
                            if (KeybindModifierMatcher.isValidMask(mask)) {
                                REQUIRED_MODIFIERS.put(key, mask);
                            }
                        }
                    }
                }
            } catch (IOException | JsonParseException | IllegalStateException exception) {
                System.err
                    .println("[ArenaSystem] Could not read keybind config; using defaults: " + exception.getMessage());
            }
        }
    }

    public static int getRequiredModifiers(String translationKey) {
        return REQUIRED_MODIFIERS.getOrDefault(translationKey, 0);
    }

    public static boolean isManaged(String translationKey) {
        return DEFAULTS.containsKey(translationKey);
    }

    static void capturePressedModifiers(String translationKey, int modifiers) {
        if (DEFAULTS.containsKey(translationKey) && KeybindModifierMatcher.isValidMask(modifiers)) {
            PRESSED_MODIFIERS.put(translationKey, modifiers);
        }
    }

    static int consumePressedModifiers(String translationKey, int fallback) {
        Integer captured = PRESSED_MODIFIERS.remove(translationKey);
        return captured == null ? fallback : captured;
    }

    public static void setRequiredModifiers(String translationKey, int modifiers) {
        if (DEFAULTS.containsKey(translationKey) && KeybindModifierMatcher.isValidMask(modifiers)) {
            REQUIRED_MODIFIERS.put(translationKey, modifiers);
            save();
        }
    }

    public static void resetToDefaults() {
        REQUIRED_MODIFIERS.clear();
        REQUIRED_MODIFIERS.putAll(DEFAULTS);
        PRESSED_MODIFIERS.clear();
        save();
    }

    private static void save() {
        Path file = getConfigFile();
        Path temporaryFile = file.resolveSibling("arenasystem-keybinds.json.tmp");
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject bindings = new JsonObject();

        for (Entry<String, Integer> entry : REQUIRED_MODIFIERS.entrySet()) {
            bindings.addProperty(entry.getKey(), entry.getValue());
        }

        root.add("bindings", bindings);

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temporaryFile, GSON.toJson(root), StandardCharsets.UTF_8);

            try {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            System.err.println("[ArenaSystem] Could not save keybind config: " + exception.getMessage());

            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException var6) {
            }
        }
    }

    private static Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("arenasystem-keybinds.json");
    }

    private static Map<String, Integer> createDefaults() {
        Map<String, Integer> defaults = new LinkedHashMap<>();
        defaults.put("key.arstm_client0qb201.primary_skill", 0);
        defaults.put("key.arstm_client0qb201.secondary_skill", 0);
        defaults.put("key.arstm_client0qb201.class_skill_item", 0);
        defaults.put("key.arstm_client0qb201.assassin_offhand", 0);
        defaults.put("key.arstm_client0qb201.sniper_aim_lock", 0);
        return Map.copyOf(defaults);
    }
}
