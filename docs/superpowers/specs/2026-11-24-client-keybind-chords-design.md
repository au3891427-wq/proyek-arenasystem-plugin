# Client Skill Keybind Chords — Design

## Intent
Give ArenaSystem skill inputs distinct modifier chords so they are less likely to collide with ordinary Minecraft controls.

## Accepted default chords
- Primary class skill: Shift + G
- Secondary class skill: Shift + H
- Held class-skill item: Shift + J
- Sniper Aim Lock: Alt + Q
- Assassin off-hand: Ctrl + Right Mouse Button

## Goals
- Detect the modifier and base-key/button press on the Fabric client.
- Keep the existing action IDs and packet format unchanged.
- Keep server-side class, cooldown, target, and action validation authoritative.
- Keep ordinary unmodified G/H/J/Q/RMB from sending an ArenaSystem action.
- Recognize the left/right variants of Shift, Alt, and Ctrl.
- Require the exact modifier set for each chord; extra modifier keys do not accidentally activate another action.

## Non-goals
- No Paper plugin changes or protocol changes.
- No custom keybind configuration UI or modifier remapping UI.
- No CapsLock/Tab bindings.
- No unrelated refactor of Assassin callbacks or vanilla controls.

## Approach
Considered: (1) read modifier state alongside the existing Fabric KeyMapping press events; (2) register standalone modifier KeyMappings; (3) replace the key handling with raw input callbacks/mixins. Use approach 1: it preserves the current configurable base KeyMappings and discrete press events, adds only a small pure modifier matcher, and avoids mixins. Modifier selection is fixed per action. The Controls screen describes the required modifier; the base key remains the configurable KeyMapping.

## Constraints and behavior
- Client target remains Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.2+26.3, Java 25, Gradle 9.8.0.
- Read modifier state through Minecraft 26.3's client helpers: hasShiftDown(), hasAltDown(), and hasControlDown(). This uses the current SDL input backend; do not add GLFW usage.
- Existing debounce, class-skill-item PDC check, Assassin dual-blade marker check, and existing vanilla-use suppression remain unchanged.
- Bump client mod version from 0.2.8 to 0.2.9. Server version remains unchanged.

## Verification
- Add a standalone Java test for exact modifier matching and mismatches, and run it with javac/java.
- Run the full client Gradle build and CI standalone tests when the target toolchain is available.
- Live gameplay still needs confirmation in a Minecraft client connected to the Paper server.
