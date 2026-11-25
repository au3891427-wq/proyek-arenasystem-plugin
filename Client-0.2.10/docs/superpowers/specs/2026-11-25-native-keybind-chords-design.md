# ArenaSystem Client — Native Keybind Chords Design

## Intent
Allow ArenaSystem players to configure key + modifier combinations directly in Minecraft's built-in Controls screen, without installing another mod.

## Goals
- Record Shift/Ctrl/Alt combinations before a keyboard or mouse primary input.
- Display bindings as readable combinations in the Controls list.
- Persist each binding's modifier mask in the ArenaSystem config directory.
- Preserve current default combinations and gameplay actions.
- Keep the existing Minecraft/Fabric/Java target versions and avoid new runtime dependencies.

## Non-goals
- No server plugin changes, gameplay protocol changes, or custom settings screen.
- No left/right modifier distinction.
- No change to unrelated updater, HUD, resource-pack, or combat behavior.

## Approaches
- Mixin into vanilla Controls and KeyMapping (chosen): keeps the existing UI and dependencies minimal.
- Separate settings screen: more UI work and splits keybind configuration from vanilla Controls.
- External modifier-keybind mod: adds a dependency every player would need to install.

## Behavior and constraints
- While an ArenaSystem binding is selected, modifier-only key presses are captured, not assigned as the primary key.
- The next keyboard or mouse primary input is assigned by vanilla and receives the currently held modifier mask.
- Escape retains vanilla cancellation/unbind behavior.
- Defaults remain Shift for primary/secondary/class-skill-item, Ctrl for Assassin offhand, and Alt for sniper aim lock.
- Existing key mappings and action payloads remain unchanged; only required-modifier lookup becomes configurable.
- Current project baseline is Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.2+26.3, Java release/toolchain 25, Loom 1.19-SNAPSHOT.
- A meaningful new keybind capability may receive a patch version increment; metadata-only cleanup must not independently increment the version.
