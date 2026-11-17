# GEMINI.md — AI Developer Guidelines for ArenaSystem

## Project Identity
- **Project Name:** ArenaSystem
- **Baseline Version:** 0.2.8.2
- **Platform:** Minecraft Paper API 26.3
- **Language Level:** Java 25
- **Build Tool:** Maven (`mvn clean package`)
- **Main Package:** `id.hadziq.arenasystem`

## Source of Truth & Rules
1. **Absolute Consistency:** Never modify gameplay balance, damage values, cooldowns, knockback vectors, or skill durations unless explicitly requested.
2. **No Gameplay Alteration:** Preserve all 8 combat classes (Knight, Tank, Archer, Sniper, Assassin, Ninja, Boxer, Demoman), Random Stuff items, Death Camera, TDM, and Relic Rush mechanics.
3. **Audit First:** Always inspect actual source implementations before suggesting edits or writing patches.
4. **Lifecycle Safety:** Any temporary state (projectiles, active abilities, attribute modifiers, death camera states) must be registered in `STATE_REGISTRY.md` and cleaned up on player quit, death, match end, or plugin disable.
