# Upgrade ArenaSystem 0.2.3.7 → 0.2.3.8

1. Stop the server.
2. Back up `plugins/ArenaSystem`, the world, and all arena snapshots.
3. Replace the previous ArenaSystem JAR with `ArenaSystem-0.2.3.8.jar`; do not keep two ArenaSystem JARs installed.
4. Start the server and review the console for arena recovery or vegetation-cleanup warnings.
5. The cleanup migration will make one fresh pass over completed arenas using `combat-vegetation-cleaned-v4`. Original plant blocks are added to the existing arena snapshot before removal.
6. Existing default cooldowns migrate only when their values still match the previous built-in defaults: Rapid Jab 16→20 seconds, Dynamite 14→10 seconds, and Blast Mine 28→20 seconds. Other operator-customized cooldown values are preserved.
7. For TDM, set team count before players join the queue: `/arena team <arena> <2|3|4>`. The default is RED/BLUE; 3 teams adds GREEN; 4 teams adds YELLOW. Relic Rush stays two-team.
8. Test on a staging server using `FINAL-AUDIT-CHECKLIST.md` before updating the production server.

## Controls
- **F**: primary class skill.
- **Shift+F / configured modifier+F**: secondary class skill for Ninja as configured by the plugin.
- **Right-click**: use a class-skill item.
- **Boxer empty-hand right-click**: Heavy Jab (3-second cooldown).
- **Sniper Q while a target is marked**: toggle aim lock; the drop action is cancelled while the mark is active.
