# ArenaSystem 0.2.3.9 — Final Audit Patch

## Terrain and vegetation
- Vegetation above the normalized surface is added to the same `BlockEdit` generation plan, so original grass/plant blocks are covered by the arena snapshot before edits are applied.
- Legacy arena cleanup now scans the entire saved terrain elevation band plus twelve blocks and uses the `combat-vegetation-cleaned-v4` migration flag, forcing one fresh cleanup pass for arenas previously marked by v3. This also catches ground vegetation hidden below tall structures or on stepped slopes.
- Tree leaves, logs, and deliberate arena structures remain excluded.

## TDM presentation
- Preparation countdown sounds run at 5, 4, 3, 2, and 1; the note-block start cue is played once at second 1.
- The live transition uses `ARENA / Teamdeathmatch / GO!`; it no longer plays a second duplicate start note.
- Match result titles remain per-player: winners receive `ARENA / STREAK! / Your team won!` with a firework blast; losers receive `ARENA / DOWN... / Your team lost!` with an Ender Dragon growl.

## Class and match changes
- Boxer: third landed unarmed jab gets +20% damage; if Rapid Jab is cooling down, that third jab removes 5 seconds. Rapid Jab cooldown is 20 seconds; empty-hand right-click performs Heavy Jab on a separate 3-second cooldown. Barrage applies Slowness I and has an 8% chance to apply Weakness I.
- Demoman: Dynamite Volley fires three custom bombs; cooldowns are 10 seconds (Dynamite) and 20 seconds (Blast Mine). Blast Mine remains entity-only and does not damage arena blocks.
- Assassin uses a Golden Sword. Knight Skill 1 is Power Strike damage instead of a defensive support skill. The three requested cooldown changes migrate older built-in defaults (16→20, 14→10, 28→20) while preserving operator-customized cooldown values.
- Tank Ground Slam deals damage, clear upward launch with horizontal push, and Slowness II; it also ends a Ninja's vanish before applying the hit.
- Sniper Mark lasts 8 seconds, provides the existing marked-target bonus, can toggle aim lock with Q, and ends after three arrows from the marking Sniper hit the marked target or the mark expires. Replacing a target's mark also clears the previous Sniper's reverse mapping/aim lock. Tooltip text now matches the behavior.
- TDM supports 2-4 teams: RED/BLUE by default, GREEN at 3 teams, and YELLOW at 4 teams. Configure with `/arena team <arena> <2|3|4>` before players join. Relic Rush remains two-team.
- Random Stuff is distributed every 30 seconds during live matches, to a random 1-6 eligible participants (capped by participants), one item per recipient.

## Verification status
- ZIP integrity and YAML syntax are checked before release.
- Full Paper API compilation and live server tests remain unverified in this environment because the required Paper API dependency / build toolchain is unavailable.


## Pemeriksaan satu pemain
- Pertandingan dapat dimulai oleh satu pemain melalui opsi `matches.allow-single-player` (default `true`).
- Label dan pengumuman mode solo yang bernuansa diagnostik dihapus dari gameplay. Scoreboard tetap memakai tampilan pertandingan biasa.
- Ketika hanya satu pemain, hasil akhir tidak mengklaim tim tersebut menang atau kalah; hasil menampilkan waktu dan skor saja.
