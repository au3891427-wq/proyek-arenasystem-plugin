# Client Skill Keybind Chords Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Shift/Alt/Ctrl chord detection to the existing Fabric client keybinds without changing the server protocol.

**Architecture:** Preserve the existing KeyMapping press events and add a small pure modifier matcher. Read left/right modifier-key state from the Minecraft window with GLFW, then gate the existing action sends by an exact modifier match.

**Tech Stack:** Java 25, Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.2+26.3, Gradle 9.8.0.

**Spec:** docs/superpowers/specs/2026-11-24-client-keybind-chords-design.md

## Global Constraints

- Client target remains Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.2+26.3, Java 25, Gradle 9.8.0.
- Keep protocol action IDs 1–4 and the six-byte skill-input message unchanged.
- Server remains authoritative; no Paper source changes.
- Exact modifier matching: unmodified, mismatched, or extra-modifier chords must not send the action.
- Preserve existing debounce, PDC marker checks, and Assassin vanilla-use suppression.
- Set client mod version to 0.2.9; do not change server version.

## Review Focus

- Base key/button pressed without its modifier: ignored; covered by matcher tests and per-action gating.
- Left versus right modifier key: both must set the same logical modifier bit; verify both GLFW key codes in the implementation.
- Wrong or additional modifier held: ignored; covered by exact-match unit tests.
- Multiple ArenaSystem press events in one tick: retain the existing single-action guard after chord filtering.
- Ctrl+RMB with and without Assassin blade markers: retain the existing marker check and callback suppression path; inspect that no callback starts sending a second packet.

---

### Task 1: Test and implement pure modifier matching

**Files:**
- Create: `ArenaSystemClient/src/main/java/id/hadziq/arenasystem/client/KeybindModifierMatcher.java`
- Create: `ArenaSystemClient/tools/TestKeybindModifierMatcher.java`

**Interfaces:**
- Produces constants `SHIFT`, `ALT`, `CONTROL` and method `matchesExactly(int activeModifiers, int requiredModifiers)`.

- [ ] **Step 1: Write the failing test** in `TestKeybindModifierMatcher.java`. Assert that each required modifier matches itself; no modifier, a different modifier, and a combination containing extra modifier bits do not match.
- [ ] **Step 2: Run the test before adding the production class.** Run: `javac -d /tmp/arenasystem-modifier-test ArenaSystemClient/tools/TestKeybindModifierMatcher.java`. Expected: compilation fails because `KeybindModifierMatcher` does not exist.
- [ ] **Step 3: Implement the minimal matcher** with bit constants SHIFT=1, ALT=2, CONTROL=4 and exact integer equality.
- [ ] **Step 4: Run the test.** Compile both source files with javac, then run `id.hadziq.arenasystem.client.TestKeybindModifierMatcher`. Expected: pass.
- [ ] **Step 5: Commit** the matcher and its standalone test.

### Task 2: Apply chords to client input

**Files:**
- Modify: `ArenaSystemClient/src/main/java/id/hadziq/arenasystem/client/ArenaSystemKeybinds.java`
- Modify: `ArenaSystemClient/src/main/resources/assets/arstm_client0qb201/lang/en_us.json`
- Modify: `ArenaSystemClient/src/main/resources/assets/arstm_client0qb201/lang/id_id.json`

**Interfaces:**
- Consumes `KeybindModifierMatcher` constants and exact-match method from Task 1.
- Keeps `SkillInputClient.send(byte)` and existing action IDs unchanged.

- [ ] **Step 1: Read active modifier state** from `client.getWindow().getWindow()` using GLFW key-state checks for both left and right Shift, Alt, and Control.
- [ ] **Step 2: Gate each consumed press** with the accepted chord: Shift+G, Shift+H, Shift+J, Alt+Q, Ctrl+RMB. Filter to matching chord presses before the existing single-action guard. Sample the modifier mask once per client tick.
- [ ] **Step 3: Preserve existing guards** for player state, debounce, held class-skill PDC, Assassin dual-blade PDC, and existing off-hand suppression.
- [ ] **Step 4: Update key descriptions** to disclose the required modifier without hardcoding the base key, since the base KeyMapping remains remappable.
- [ ] **Step 5: Verify** source diff and ensure only the intended keybind gates and descriptions changed.

### Task 3: Update client version, documentation, and CI test

**Files:**
- Modify: `ArenaSystemClient/gradle.properties`
- Modify: `ArenaSystemClient/README.md`
- Modify: `.github/workflows/build-arenasystem-client.yml`

- [ ] **Step 1: Bump** `mod_version` from 0.2.8 to 0.2.9.
- [ ] **Step 2: Update README controls** to list all five default chords and clarify that each action's modifier is fixed while the base KeyMapping remains remappable.
- [ ] **Step 3: Add a CI standalone Java test step** for `KeybindModifierMatcher`, following the existing protocol-test pattern.
- [ ] **Step 4: Run local standalone test and attempt the full client build.** Full build command: `gradle --no-daemon clean build` from `ArenaSystemClient`, with Java 25 and Gradle 9.8.0.
- [ ] **Step 5: Verify** the final diff and CI result. If the environment lacks the required toolchain, report that limitation rather than claiming a full build passed.
- [ ] **Step 6: Commit** the remaining client implementation and documentation changes.
