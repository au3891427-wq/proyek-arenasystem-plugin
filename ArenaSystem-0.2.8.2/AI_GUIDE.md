# AI Coding Assistant Guide

## How to Navigate ArenaSystem
- **Finding Abilities:** Check `id.hadziq.arenasystem.classsystem.abilities` or `ClassAbilityListener.java`.
- **Finding Match Logic:** Check `id.hadziq.arenasystem.match`.
- **Finding Arena Generation:** Check `id.hadziq.arenasystem.terrain` and `ArenaGenerator.java`.
- **Modifying Commands:** Check `ArenaCommandExecutor.java`.

## Development Workflow
1. Run static audit against existing listeners.
2. Ensure Java 25 compatibility and Paper 26.3 API compliance.
3. Validate builds locally via `mvn clean package`.
