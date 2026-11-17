# Patch Notes — Blast Mine Creeper Marker

- Replaced the TNT helmet ArmorStand indicator with a Creeper entity named `Blast Mine`.
- The Creeper is a visual-only marker: AI and awareness disabled, no gravity, invulnerable, silent, non-collidable, glowing, and configured with zero explosion radius.
- When an opposing player enters the existing Blast Mine trigger radius, the marker is removed immediately before the mine effect is processed.
- If the mine expires, its owner leaves/changes class, or match cleanup runs, the marker is also removed.
- The mine owner receives a chat notification for each opposing player who actually takes health or absorption damage from the mine. A cancelled/no-damage event does not produce a hit notification.
- Existing mine trigger radius, effect radius, damage values, lifetime, and cooldown are unchanged.
