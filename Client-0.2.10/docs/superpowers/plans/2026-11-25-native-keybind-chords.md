# Native Keybind Chords Implementation Plan

> **For agentic workers:** Use inline execution in this session. Steps use checkbox syntax.

**Goal:** Make ArenaSystem keybinds configurable as modifier + primary-key chords in Minecraft's native Controls screen.

**Architecture:** Keep vanilla KeyMapping as the primary-key registry. A client-only Mixin captures modifier-only key events while an ArenaSystem mapping is selected, stores the held modifier mask when the primary key is assigned, and decorates the displayed binding. A small Gson-backed config persists masks; gameplay reads the configured mask.

**Tech Stack:** Java 25, Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.2+26.3, Fabric Loom 1.19-SNAPSHOT, Mixin, existing Gson 2.13.2 include.

**Spec:** `docs/superpowers/specs/2026-11-25-native-keybind-chords-design.md`

## Global Constraints
- Keep Minecraft `26.3`, Fabric Loader `0.20.1`, Fabric API `0.166.2+26.3`, Java 25, and Loom `1.19-SNAPSHOT`.
- Do not add a runtime dependency or modify the server plugin.
- Preserve existing key defaults, action payloads, debounce behavior, and mod ID `arstm_client0qb201`.
- Use the release filename convention `arenaSystem_client-<version>.jar`.
- Only increment the version for the meaningful chord-binding feature, not for metadata/packaging cleanup alone.

## Review Focus
- Modifier-only input must not become the primary key.
- Modifier combinations must be sampled at primary-key assignment, including mouse assignment.
- No-modifier binding and vanilla Escape behavior must remain intact.
- Missing, malformed, or partial config must fall back safely to defaults.
- Resetting or unbinding a mapping must not leave stale modifier state.

---

### Task 1: Modifier masks and persisted configuration

**Files:**
- Create: `src/main/java/id/hadziq/arenasystem/client/KeybindChordConfig.java`
- Modify: `src/main/java/id/hadziq/arenasystem/client/KeybindModifierMatcher.java`
- Test: `src/test/java/id/hadziq/arenasystem/client/KeybindModifierMatcherTest.java` (standalone Java test harness)

**Interfaces:**
- Produces `KeybindChordConfig.getRequiredModifiers(String translationKey)`, `setRequiredModifiers(String translationKey, int mask)`, and `load()`.
- Modifier bits remain Shift=1, Alt=2, Control=4; valid mask is 0..7.

- [x] Write tests for exact-match masks, invalid bits, and zero mask; observed expected failure before helper implementation.
- [x] Implement the smallest pure mask helper and verify RED→GREEN.
- [x] Implement Gson-backed config with safe defaults and temp-file replacement; invalid entries fall back individually.
- [x] Run standalone Java tests and JSON/schema checks.

### Task 2: Native Controls capture and display

**Files:**
- Create: `src/main/java/id/hadziq/arenasystem/client/KeyBindsScreenMixin.java`
- Create: `src/main/java/id/hadziq/arenasystem/client/KeyBindsScreenAccessor.java`
- Create: `src/main/java/id/hadziq/arenasystem/client/KeyMappingMixin.java`
- Create: `src/main/java/id/hadziq/arenasystem/client/KeyMappingAccessor.java`
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `src/main/java/id/hadziq/arenasystem/client/ArenaSystemClient.java`

**Interfaces:**
- Consumes Task 1's `KeybindChordConfig` methods.
- Captures only mappings registered by ArenaSystem; leaves all other vanilla/mod key mappings untouched.

- [x] Add a focused test for modifier key classification and mask composition; observed RED before helper implementation.
- [x] Implement client-only mixin registration and modifier-only interception for selected ArenaSystem bindings.
- [x] Store the active modifier mask when vanilla assigns a keyboard or mouse primary key.
- [x] Decorate `KeyMapping.getTranslatedKeyMessage()` with the stored modifier labels.
- [x] Verify mixin JSON, Fabric metadata, and source-level target signatures against Minecraft 26.3 API references.

### Task 3: Runtime integration and release metadata

**Files:**
- Modify: `src/main/java/id/hadziq/arenasystem/client/ArenaSystemKeybinds.java`
- Modify: `src/main/resources/assets/arstm_client0qb201/lang/en_us.json`
- Modify: `src/main/resources/assets/arstm_client0qb201/lang/id_id.json`
- Modify: `gradle.properties`
- Modify: `build.gradle`
- Modify: `src/main/resources/fabric.mod.json`

- [x] Test modifier mask composition, exact matching, modifier classification, and invalid masks; observed RED before implementation.
- [x] Replace hard-coded required masks with config lookups without changing action payloads/debounce logic.
- [x] Load config during client initialization; update keybind labels to avoid implying immutable modifiers.
- [x] Align project and Fabric metadata version at `0.2.10` and use exact artifact name `arenaSystem_client-${project.version}.jar`.
- [x] Remove manually declared Gson `jars` entry because Loom's `include` generates the nested-JAR metadata.
- [x] Run available local checks; full Loom build is deferred to Windows JDK 25 / Gradle 9.9.2 because that toolchain is not available in this sandbox.

### Task 4: Final package and review

**Files:**
- Output: updated source ZIP containing client changes and unchanged server source.

- [x] Validate all JSON files and archive integrity.
- [x] Check that no server source changed and no unrelated client source changed.
- [x] Self-review input handling, persistence, metadata, and artifact naming.
- [x] Produce updated ZIP; full Loom build explicitly deferred because JDK 25 / Gradle 9.9.2 are unavailable in this sandbox.


**Final review:** self-review (no subagent tool). No Critical/Important source-level findings. The actual Fabric launch and Controls interaction remain unverified until the project is built and launched with Minecraft 26.3.
