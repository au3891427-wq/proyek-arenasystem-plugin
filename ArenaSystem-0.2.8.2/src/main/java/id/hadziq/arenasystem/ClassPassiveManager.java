package id.hadziq.arenasystem;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Owns the lifecycle and per-player runtime state for class passives.
 *
 * Owns per-player passive state and gameplay effects for active TDM participants.
 * Passive implementations are isolated here so their timers and lifecycle cleanup
 * cannot leak across classes, matches, deaths, or reconnects.
 */
final class ClassPassiveManager implements Listener {
    enum PassiveId {
        GUARD,
        STEADY_AIM,
        PRECISION,
        BULWARK,
        EXECUTIONER,
        SHADOW_VEIL,
        RHYTHM,
        DEMOLITION_EXPERT
    }

    /** Static class-to-passive metadata used by menus, HUD, and gameplay handlers. */
    record PassiveDefinition(
            String classId,
            PassiveId passiveId,
            String displayName,
            String concept
    ) {}

    /**
     * Mutable state scoped to one player, one class selection, and one match.
     * Keys are namespaced by passive implementation (for example,
     * "rhythm.landed_jabs" or "shadow_veil.last_activity_at").
     */
    static final class PlayerPassiveState {
        private final UUID playerId;
        private final String arenaName;
        private final PassiveDefinition definition;
        private final long startedAtMillis;
        private final Map<String, Integer> counters = new HashMap<>();
        private final Map<String, Long> deadlines = new HashMap<>();
        private final Set<String> flags = new HashSet<>();

        private PlayerPassiveState(UUID playerId, String arenaName, PassiveDefinition definition) {
            this.playerId = playerId;
            this.arenaName = arenaName;
            this.definition = definition;
            this.startedAtMillis = System.currentTimeMillis();
        }

        UUID playerId() {
            return playerId;
        }

        String arenaName() {
            return arenaName;
        }

        String classId() {
            return definition.classId();
        }

        PassiveId passiveId() {
            return definition.passiveId();
        }

        PassiveDefinition definition() {
            return definition;
        }

        long startedAtMillis() {
            return startedAtMillis;
        }

        int counter(String key) {
            return counters.getOrDefault(normalizeKey(key), 0);
        }

        int addCounter(String key, int amount) {
            String safeKey = normalizeKey(key);
            return counters.merge(safeKey, amount, Integer::sum);
        }

        void setCounter(String key, int value) {
            counters.put(normalizeKey(key), Math.max(0, value));
        }

        void resetCounter(String key) {
            counters.remove(normalizeKey(key));
        }

        long deadline(String key) {
            return deadlines.getOrDefault(normalizeKey(key), 0L);
        }

        void setDeadline(String key, long epochMillis) {
            deadlines.put(normalizeKey(key), Math.max(0L, epochMillis));
        }

        void clearDeadline(String key) {
            deadlines.remove(normalizeKey(key));
        }

        boolean flag(String key) {
            return flags.contains(normalizeKey(key));
        }

        void setFlag(String key, boolean enabled) {
            String safeKey = normalizeKey(key);
            if (enabled) flags.add(safeKey);
            else flags.remove(safeKey);
        }

        private void clear() {
            counters.clear();
            deadlines.clear();
            flags.clear();
        }

        private static String normalizeKey(String key) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Passive state key must not be blank");
            }
            return key.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static final Map<String, PassiveDefinition> DEFINITIONS;

    static {
        Map<String, PassiveDefinition> definitions = new HashMap<>();
        register(definitions, "knight", PassiveId.GUARD, "Guard", "Ketahanan tambahan untuk petarung garis depan.");
        register(definitions, "archer", PassiveId.STEADY_AIM, "Steady Aim", "Keunggulan saat menjaga bidikan dan menembakkan panah.");
        register(definitions, "sniper", PassiveId.PRECISION, "Precision", "Keunggulan untuk serangan jarak jauh yang terarah.");
        register(definitions, "tank", PassiveId.BULWARK, "Bulwark", "Pertahanan berat dengan peluang menyerap sebagian damage.");
        register(definitions, "assassin", PassiveId.EXECUTIONER, "Executioner", "Tekanan tambahan terhadap lawan yang hampir kalah.");
        register(definitions, "ninja", PassiveId.SHADOW_VEIL, "Shadow Veil", "Stealth setelah berhasil menghindari pertarungan.");
        register(definitions, "boxer", PassiveId.RHYTHM, "Rhythm", "Setiap jab ketiga memberi +20% damage dan mengurangi cooldown Skill 1 sebesar 5 detik jika sedang cooldown.");
        register(definitions, "demoman", PassiveId.DEMOLITION_EXPERT, "Demolition Expert", "Ketahanan terhadap efek ledakan milik sendiri.");
        DEFINITIONS = Collections.unmodifiableMap(definitions);
    }

    private final ArenaSystemPlugin plugin;
    private final Map<UUID, PlayerPassiveState> playerStates = new HashMap<>();
    private static final long SHADOW_VEIL_REQUIRED_MILLIS = 5_600L;
    private static final long SHADOW_VEIL_DURATION_MILLIS = 5_300L;
    private static final int SHADOW_VEIL_DURATION_TICKS = 106;
    private static final double SHADOW_VEIL_BONUS_DAMAGE = 1.20D;
    private static final long SHADOW_VEIL_MISSED_SWING_MILLIS = 450L;
    private static final int NINJA_FOLLOWUP_ATTACKS = 5;
    private static final double NINJA_FOLLOWUP_ATTACK_SPEED = 20.0D;

    private static final long ARCHER_STEADY_AIM_REQUIRED_MILLIS = 1_000L;
    private static final long TRACKED_PROJECTILE_LIFETIME_MILLIS = 30_000L;
    private static final double ARCHER_STEADY_AIM_DAMAGE = 1.10D;
    private static final double SNIPER_PRECISION_DAMAGE = 1.10D;
    private static final double SNIPER_PRECISION_RANGE = 16.0D;
    private static final double KNIGHT_GUARD_REDUCTION = 0.94D;
    private static final double TANK_BULWARK_REDUCTION = 0.95D;
    private static final double TANK_BULWARK_ABSORB_CHANCE = 0.15D;
    private static final double TANK_BULWARK_ABSORB_REMAINING = 0.50D;
    private static final long TANK_BULWARK_ABSORB_COOLDOWN_MILLIS = 3_500L;
    private static final double ASSASSIN_EXECUTIONER_THRESHOLD = 0.35D;
    private static final double ASSASSIN_EXECUTIONER_DAMAGE = 1.12D;
    private static final int BOXER_RHYTHM_HITS_REQUIRED = 3;
    private static final long BOXER_RHYTHM_COOLDOWN_REDUCTION_MILLIS = 5_000L;
    private static final double BOXER_RHYTHM_THIRD_JAB_DAMAGE = 1.20D;
    private static final long DEMOMAN_OWN_BLAST_WINDOW_MILLIS = 500L;

    private record TrackedShot(UUID shooterId, String arenaName, long expiresAtMillis, Location origin) {}

    private final Map<UUID, TrackedShot> archerSteadyAimShots = new HashMap<>();
    private final Map<UUID, TrackedShot> sniperPrecisionShots = new HashMap<>();
    private final Map<UUID, NinjaFollowupBoost> ninjaFollowupBoosts = new HashMap<>();
    private final BukkitTask synchronizationTask;

    private record NinjaFollowupBoost(String arenaName, double originalBaseAttackSpeed, int attacksRemaining) {}

    ClassPassiveManager(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
        // One-tick updates keep the 5.3-second stealth window and swing grace precise.
        this.synchronizationTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        synchronizeActivePlayers();
        cleanupTrackedProjectiles();
        tickNinjaShadowVeil();
    }

    private void cleanupTrackedProjectiles() {
        long now = System.currentTimeMillis();
        archerSteadyAimShots.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
        sniperPrecisionShots.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    /** Activates Shadow Veil after 5.6 seconds of uninterrupted sneaking without combat. */
    private void tickNinjaShadowVeil() {
        long now = System.currentTimeMillis();
        for (PlayerPassiveState state : playerStates.values()) {
            if (state.passiveId() != PassiveId.SHADOW_VEIL) continue;

            Player player = Bukkit.getPlayer(state.playerId());
            if (player == null || !player.isOnline() || player.isDead()
                    || player.getGameMode() != GameMode.SURVIVAL) continue;

            if (state.flag("shadow_veil.active")) {
                long expiresAt = state.deadline("shadow_veil.expires_at");
                if (expiresAt > 0L && now >= expiresAt) {
                    endShadowVeil(player, state, now, "expired");
                    continue;
                }

                long pendingSwingUntil = state.deadline("shadow_veil.pending_swing_until");
                if (state.flag("shadow_veil.pending_swing")
                        && pendingSwingUntil > 0L && now >= pendingSwingUntil) {
                    endShadowVeil(player, state, now, "missed");
                }
                continue;
            }

            // The active Smoke Bomb skill owns the same full-vanish channel.
            // Do not overlap it; require a fresh quiet/sneak window after it ends.
            if (plugin.classAbilityListener != null && plugin.classAbilityListener.isVanished(player)) {
                state.setDeadline("shadow_veil.last_activity_at", now);
                state.clearDeadline("shadow_veil.sneak_since");
                continue;
            }

            if (!player.isSneaking()) {
                state.clearDeadline("shadow_veil.sneak_since");
                state.setFlag("shadow_veil.pending_swing", false);
                state.clearDeadline("shadow_veil.pending_swing_until");
                continue;
            }

            long sneakSince = state.deadline("shadow_veil.sneak_since");
            if (sneakSince <= 0L) {
                sneakSince = now;
                state.setDeadline("shadow_veil.sneak_since", sneakSince);
            }

            long lastActivity = state.deadline("shadow_veil.last_activity_at");
            if (lastActivity <= 0L) lastActivity = state.startedAtMillis();
            long eligibleSince = Math.max(sneakSince, lastActivity);
            if (now - eligibleSince >= SHADOW_VEIL_REQUIRED_MILLIS) {
                activateShadowVeil(player, state, now);
            }
        }
    }

    private void activateShadowVeil(Player player, PlayerPassiveState state, long now) {
        if (plugin.classAbilityListener == null
                || !plugin.classAbilityListener.startPassiveVanish(player, SHADOW_VEIL_DURATION_TICKS)) {
            state.setDeadline("shadow_veil.last_activity_at", now);
            return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                SHADOW_VEIL_DURATION_TICKS, 1, false, false, true), true);
        state.setFlag("shadow_veil.active", true);
        state.setFlag("shadow_veil.bonus_ready", true);
        state.setFlag("shadow_veil.pending_swing", false);
        state.setDeadline("shadow_veil.expires_at", now + SHADOW_VEIL_DURATION_MILLIS);
        state.clearDeadline("shadow_veil.pending_swing_until");

        player.getWorld().spawnParticle(Particle.SMOKE,
                player.getLocation().add(0, 1, 0), 24, 0.38, 0.55, 0.38, 0.025);
        player.getWorld().spawnParticle(Particle.SOUL,
                player.getLocation().add(0, 0.9, 0), 8, 0.28, 0.42, 0.28, 0.015);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.65f, 1.7f);
        player.sendMessage(ChatColor.DARK_GREEN + "Shadow Veil aktif! Speed II + menghilang selama 5,3 detik. Serangan pertama ke lawan mendapat +20% damage.");
        player.sendActionBar(ChatColor.DARK_GREEN + "SHADOW VEIL ACTIVE");
    }

    /** Ends stealth, restores Ninja's normal Speed I, and starts a fresh quiet timer. */
    private void endShadowVeil(Player player, PlayerPassiveState state, long now, String reason) {
        boolean wasActive = state.flag("shadow_veil.active");
        state.setFlag("shadow_veil.active", false);
        state.setFlag("shadow_veil.bonus_ready", false);
        state.setFlag("shadow_veil.pending_swing", false);
        state.setFlag("shadow_veil.followup_trigger_pending", false);
        state.clearDeadline("shadow_veil.expires_at");
        state.clearDeadline("shadow_veil.pending_swing_until");
        state.setDeadline("shadow_veil.last_activity_at", now);

        if (!wasActive) return;
        if (plugin.classAbilityListener != null) {
            plugin.classAbilityListener.endPassiveVanish(player);
        }
        player.removePotionEffect(PotionEffectType.SPEED);
        // Ninja's kit normally grants Speed I; restore it after the temporary Speed II.
        if (player.isOnline() && !player.isDead()
                && player.getGameMode() == GameMode.SURVIVAL) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    20 * 60 * 60, 0, false, false, true), true);
        }
        if (player.isOnline()) {
            if ("hit".equals(reason)) {
                player.sendActionBar(ChatColor.YELLOW + "SHADOW VEIL BROKEN — kamu terkena serangan");
            } else if ("attack".equals(reason)) {
                player.sendActionBar(ChatColor.GREEN + "AMBUSH! +20% damage");
            } else if ("missed".equals(reason)) {
                player.sendActionBar(ChatColor.GRAY + "Shadow Veil berakhir — serangan tidak mengenai target");
            } else if ("expired".equals(reason)) {
                player.sendActionBar(ChatColor.GRAY + "Shadow Veil berakhir. Sneak lagi selama 5,6 detik untuk mengaktifkan ulang.");
            }
        }
    }

    private void recordShadowVeilActivity(Player player, PlayerPassiveState state, long now, String reason) {
        if (state == null || state.passiveId() != PassiveId.SHADOW_VEIL) return;
        if (state.flag("shadow_veil.active")) {
            endShadowVeil(player, state, now, reason);
        } else {
            state.setDeadline("shadow_veil.last_activity_at", now);
            state.clearDeadline("shadow_veil.sneak_since");
        }
    }

    private static Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean areOpponentsInSameMatch(Player first, Player second) {
        PlayerPassiveState firstState = stateFor(first);
        PlayerPassiveState secondState = stateFor(second);
        if (firstState == null || secondState == null
                || !firstState.arenaName().equalsIgnoreCase(secondState.arenaName())) return false;
        MatchState match = plugin.matches.get(firstState.arenaName().toLowerCase(Locale.ROOT));
        if (match == null || !match.running || !"tdm".equalsIgnoreCase(match.mode)) return false;
        String firstTeam = match.teams.get(first.getUniqueId());
        String secondTeam = match.teams.get(second.getUniqueId());
        return firstTeam != null && secondTeam != null && !firstTeam.equalsIgnoreCase(secondTeam);
    }

    /** Tracks movement so Archer earns a steady-aim bonus while holding position. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPassiveMovement(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || !from.getWorld().equals(to.getWorld())) return;
        if (from.distanceSquared(to) <= 0.0025D) return; // Ignore looking around; require meaningful position change.

        PlayerPassiveState state = stateFor(event.getPlayer());
        if (state != null && state.passiveId() == PassiveId.STEADY_AIM) {
            state.setDeadline("steady_aim.still_since", System.currentTimeMillis());
        }
    }

    /** Stores launch-time data for Archer and Sniper arrows. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPassiveBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !(event.getProjectile() instanceof Arrow arrow)) return;
        PlayerPassiveState state = stateFor(player);
        if (state == null) return;

        long now = System.currentTimeMillis();
        long expiresAt = now + TRACKED_PROJECTILE_LIFETIME_MILLIS;
        Location origin = player.getLocation().clone();
        TrackedShot shot = new TrackedShot(player.getUniqueId(), state.arenaName(), expiresAt, origin);

        if (state.passiveId() == PassiveId.STEADY_AIM) {
            long stillSince = state.deadline("steady_aim.still_since");
            if (stillSince <= 0L) {
                state.setDeadline("steady_aim.still_since", now);
                return;
            }
            if (now - stillSince >= ARCHER_STEADY_AIM_REQUIRED_MILLIS && !player.isSprinting()) {
                archerSteadyAimShots.put(arrow.getUniqueId(), shot);
                player.sendActionBar(ChatColor.GREEN + "STEADY AIM — panah mendapat +10% damage");
                player.getWorld().spawnParticle(Particle.CRIT,
                        arrow.getLocation(), 5, 0.08, 0.08, 0.08, 0.015);
            }
        } else if (state.passiveId() == PassiveId.PRECISION) {
            sniperPrecisionShots.put(arrow.getUniqueId(), shot);
        }
    }

    /** Removes launch markers after a projectile has hit anything. */
    @EventHandler
    public void onPassiveProjectileHit(ProjectileHitEvent event) {
        UUID projectileId = event.getEntity().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            archerSteadyAimShots.remove(projectileId);
            sniperPrecisionShots.remove(projectileId);
        }, 1L);
    }

    /** Applies the combat passives after ability-specific damage modifiers. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClassPassiveDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker == null || attacker.equals(target)
                || !areOpponentsInSameMatch(attacker, target)) return;

        long now = System.currentTimeMillis();
        PlayerPassiveState attackerState = stateFor(attacker);
        PlayerPassiveState targetState = stateFor(target);
        boolean directMelee = event.getDamager() == attacker
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK;

        if (attackerState != null) {
            if (event.getDamager() instanceof Arrow arrow) {
                TrackedShot archerShot = archerSteadyAimShots.remove(arrow.getUniqueId());
                if (attackerState.passiveId() == PassiveId.STEADY_AIM
                        && archerShot != null
                        && attacker.getUniqueId().equals(archerShot.shooterId())
                        && archerShot.expiresAtMillis() > now
                        && archerShot.arenaName().equalsIgnoreCase(attackerState.arenaName())) {
                    event.setDamage(event.getDamage() * ARCHER_STEADY_AIM_DAMAGE);
                    attacker.sendActionBar(ChatColor.GREEN + "STEADY AIM: +10% damage");
                }

                TrackedShot sniperShot = sniperPrecisionShots.remove(arrow.getUniqueId());
                if (attackerState.passiveId() == PassiveId.PRECISION
                        && sniperShot != null
                        && attacker.getUniqueId().equals(sniperShot.shooterId())
                        && sniperShot.expiresAtMillis() > now
                        && sniperShot.arenaName().equalsIgnoreCase(attackerState.arenaName())
                        && sniperShot.origin() != null
                        && sniperShot.origin().getWorld() != null
                        && sniperShot.origin().getWorld().equals(target.getWorld())
                        && sniperShot.origin().distanceSquared(target.getLocation())
                        >= SNIPER_PRECISION_RANGE * SNIPER_PRECISION_RANGE) {
                    event.setDamage(event.getDamage() * SNIPER_PRECISION_DAMAGE);
                    attacker.sendActionBar(ChatColor.AQUA + "PRECISION: +10% long-range damage");
                }
            }

            if (attackerState.passiveId() == PassiveId.EXECUTIONER && directMelee) {
                var maxHealthAttribute = target.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = maxHealthAttribute == null ? 20.0D : maxHealthAttribute.getValue();
                if (maxHealth > 0.0D && target.getHealth() <= maxHealth * ASSASSIN_EXECUTIONER_THRESHOLD) {
                    event.setDamage(event.getDamage() * ASSASSIN_EXECUTIONER_DAMAGE);
                    attacker.sendActionBar(ChatColor.DARK_RED + "EXECUTIONER: +12% finishing damage");
                }
            }

            if (attackerState.passiveId() == PassiveId.RHYTHM && directMelee
                    && event.getDamage() > 0.0D
                    && attacker.getInventory().getItemInMainHand().getType().isAir()
                    && (plugin.classAbilityListener == null
                    || !plugin.classAbilityListener.isBoxerSkillDamage(attacker))) {
                int nextHit = attackerState.addCounter("rhythm.landed_jabs", 1);
                if (nextHit >= BOXER_RHYTHM_HITS_REQUIRED) {
                    attackerState.resetCounter("rhythm.landed_jabs");
                    event.setDamage(event.getDamage() * BOXER_RHYTHM_THIRD_JAB_DAMAGE);
                    attacker.sendActionBar(ChatColor.LIGHT_PURPLE + "RHYTHM! JAB KETIGA +20% DAMAGE");
                    long reduced = plugin.classAbilityListener == null ? 0L
                            : plugin.classAbilityListener.reduceSkillCooldown(
                                    attacker, plugin.matches.get(attackerState.arenaName().toLowerCase(Locale.ROOT)),
                                    "boxer_rapid_jab", BOXER_RHYTHM_COOLDOWN_REDUCTION_MILLIS);
                    if (reduced > 0L) {
                        attacker.sendMessage(ChatColor.LIGHT_PURPLE + "Rhythm: cooldown Skill 1 berkurang 5 detik.");
                    }
                    attacker.getWorld().spawnParticle(Particle.CRIT,
                            attacker.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.03);
                    attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.7f);
                } else {
                    attacker.sendActionBar(ChatColor.LIGHT_PURPLE + "RHYTHM: " + nextHit + "/3 landed jabs");
                }
            }
        }

        if (targetState != null) {
            if (targetState.passiveId() == PassiveId.GUARD) {
                event.setDamage(event.getDamage() * KNIGHT_GUARD_REDUCTION);
                if (now >= targetState.deadline("guard.feedback_ready_at")) {
                    targetState.setDeadline("guard.feedback_ready_at", now + 900L);
                    target.sendActionBar(ChatColor.AQUA + "GUARD: 6% damage reduced");
                }
            } else if (targetState.passiveId() == PassiveId.BULWARK) {
                event.setDamage(event.getDamage() * TANK_BULWARK_REDUCTION);
                long nextAbsorbAt = targetState.deadline("bulwark.next_absorb_at");
                if (now >= nextAbsorbAt
                        && ThreadLocalRandom.current().nextDouble() < TANK_BULWARK_ABSORB_CHANCE) {
                    event.setDamage(event.getDamage() * TANK_BULWARK_ABSORB_REMAINING);
                    targetState.setDeadline("bulwark.next_absorb_at",
                            now + TANK_BULWARK_ABSORB_COOLDOWN_MILLIS);
                    target.getWorld().spawnParticle(Particle.CRIT,
                            target.getLocation().add(0, 1, 0), 14, 0.35, 0.45, 0.35, 0.035);
                    target.getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.35f);
                    target.sendActionBar(ChatColor.GOLD + "BULWARK: 50% damage absorbed!");
                }
            }
        }
    }

    /** Marks the short velocity window for Demoman's own custom dynamite recoil. */
    void markDemomanOwnExplosion(Player player) {
        PlayerPassiveState state = stateFor(player);
        if (state != null && state.passiveId() == PassiveId.DEMOLITION_EXPERT) {
            state.setDeadline("demolition_expert.own_blast_until",
                    System.currentTimeMillis() + DEMOMAN_OWN_BLAST_WINDOW_MILLIS);
        }
    }

    /** The hook is ready for Demoman's custom explosives; ordinary explosions are untouched. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDemomanOwnExplosionVelocity(PlayerVelocityEvent event) {
        PlayerPassiveState state = stateFor(event.getPlayer());
        if (state == null || state.passiveId() != PassiveId.DEMOLITION_EXPERT) return;
        long markedUntil = state.deadline("demolition_expert.own_blast_until");
        if (markedUntil <= System.currentTimeMillis()) return;

        Vector velocity = event.getVelocity().clone();
        velocity.setX(velocity.getX() * 0.70D);
        velocity.setZ(velocity.getZ() * 0.70D);
        velocity.setY(velocity.getY() * 0.80D);
        event.setVelocity(velocity);
        state.clearDeadline("demolition_expert.own_blast_until");
        event.getPlayer().sendActionBar(ChatColor.GOLD + "DEMOLITION EXPERT: blast recoil reduced");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNinjaCombat(EntityDamageByEntityEvent event) {
        long now = System.currentTimeMillis();
        Player attacker = resolvePlayerDamager(event.getDamager());
        Player target = event.getEntity() instanceof Player player ? player : null;

        if (attacker != null) {
            PlayerPassiveState attackerState = stateFor(attacker);
            if (attackerState != null && attackerState.passiveId() == PassiveId.SHADOW_VEIL) {
                boolean validTarget = target != null && areOpponentsInSameMatch(attacker, target);
                long expiresAt = attackerState.deadline("shadow_veil.expires_at");
                long pendingSwingUntil = attackerState.deadline("shadow_veil.pending_swing_until");
                boolean withinStealthWindow = attackerState.flag("shadow_veil.active")
                        && expiresAt > now
                        && (!attackerState.flag("shadow_veil.pending_swing") || pendingSwingUntil > now);
                boolean ambushTriggered = validTarget && withinStealthWindow
                        && attackerState.flag("shadow_veil.bonus_ready");
                if (ambushTriggered) {
                    event.setDamage(event.getDamage() * SHADOW_VEIL_BONUS_DAMAGE);
                    attacker.sendMessage(ChatColor.GREEN + "Shadow Veil: Ambush berhasil! Damage serangan +20%.");
                }
                recordShadowVeilActivity(attacker, attackerState, now, "attack");
                // recordShadowVeilActivity ends the stealth state, so arm this flag afterward.
                if (ambushTriggered) {
                    attackerState.setFlag("shadow_veil.followup_trigger_pending", true);
                }
            }
        }

        if (target != null) {
            PlayerPassiveState targetState = stateFor(target);
            if (targetState != null && targetState.passiveId() == PassiveId.SHADOW_VEIL) {
                recordShadowVeilActivity(target, targetState, now, "hit");
            }
        }
    }

    /**
     * Counts only finalized, non-cancelled damage events. The Shadow Veil ambush
     * event arms the boost but is deliberately excluded from the five follow-up hits.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onNinjaFollowupHit(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker == null || !(event.getEntity() instanceof Player target)) return;
        PlayerPassiveState state = stateFor(attacker);
        if (state == null || state.passiveId() != PassiveId.SHADOW_VEIL
                || !areOpponentsInSameMatch(attacker, target)) return;

        if (state.flag("shadow_veil.followup_trigger_pending")) {
            state.setFlag("shadow_veil.followup_trigger_pending", false);
            if (!event.isCancelled() && event.getFinalDamage() > 0.0D) {
                activateNinjaFollowupBoost(attacker, state);
            }
            return;
        }

        if (event.isCancelled() || event.getFinalDamage() <= 0.0D) return;
        consumeNinjaFollowupHit(attacker, state);
    }

    private void activateNinjaFollowupBoost(Player player, PlayerPassiveState state) {
        var attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed == null) return;

        UUID playerId = player.getUniqueId();
        NinjaFollowupBoost current = ninjaFollowupBoosts.get(playerId);
        double originalBase = current == null
                ? attackSpeed.getBaseValue()
                : current.originalBaseAttackSpeed();
        // Re-activation refreshes the counter without adding another attribute modifier.
        ninjaFollowupBoosts.put(playerId,
                new NinjaFollowupBoost(state.arenaName(), originalBase, NINJA_FOLLOWUP_ATTACKS));
        attackSpeed.setBaseValue(NINJA_FOLLOWUP_ATTACK_SPEED);
        player.sendActionBar(ChatColor.DARK_GREEN + "SHADOW VEIL FOLLOW-UP — 5 landed hits boosted");
    }

    private void consumeNinjaFollowupHit(Player player, PlayerPassiveState state) {
        UUID playerId = player.getUniqueId();
        NinjaFollowupBoost boost = ninjaFollowupBoosts.get(playerId);
        if (boost == null) return;
        if (!boost.arenaName().equalsIgnoreCase(state.arenaName())) {
            clearNinjaFollowupBoost(playerId, player);
            return;
        }

        int remaining = boost.attacksRemaining() - 1;
        if (remaining <= 0) {
            clearNinjaFollowupBoost(playerId, player);
            player.sendActionBar(ChatColor.GRAY + "FOLLOW-UP selesai — attack speed normal");
        } else {
            ninjaFollowupBoosts.put(playerId,
                    new NinjaFollowupBoost(boost.arenaName(), boost.originalBaseAttackSpeed(), remaining));
            player.sendActionBar(ChatColor.DARK_GREEN + "FOLLOW-UP: " + remaining + "/5 hit tersisa");
        }
    }

    private void clearNinjaFollowupBoost(UUID playerId, Player playerOverride) {
        NinjaFollowupBoost boost = ninjaFollowupBoosts.remove(playerId);
        if (boost == null) return;
        Player player = playerOverride != null ? playerOverride : Bukkit.getPlayer(playerId);
        if (player == null) return;
        var attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) attackSpeed.setBaseValue(boost.originalBaseAttackSpeed());
    }

    /** A Ninja projectile throw is an attack attempt and breaks the quiet window. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNinjaProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        PlayerPassiveState state = stateFor(player);
        if (state != null && state.passiveId() == PassiveId.SHADOW_VEIL) {
            recordShadowVeilActivity(player, state, System.currentTimeMillis(), "attack");
        }
    }

    /** Counts an incoming snowball or other non-damaging projectile hit as being attacked. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNinjaProjectileHit(ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof Player target)) return;
        PlayerPassiveState state = stateFor(target);
        if (state != null && state.passiveId() == PassiveId.SHADOW_VEIL) {
            recordShadowVeilActivity(target, state, System.currentTimeMillis(), "hit");
        }
    }

    /** A missed melee swing also breaks stealth, but grants a short window for the hit event to arrive. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onNinjaArmSwing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        PlayerPassiveState state = stateFor(player);
        if (state == null || state.passiveId() != PassiveId.SHADOW_VEIL) return;
        if (state.flag("shadow_veil.active")) {
            state.setFlag("shadow_veil.pending_swing", true);
            state.setDeadline("shadow_veil.pending_swing_until",
                    System.currentTimeMillis() + SHADOW_VEIL_MISSED_SWING_MILLIS);
        } else {
            recordShadowVeilActivity(player, state, System.currentTimeMillis(), "attack");
        }
    }

    private static void register(
            Map<String, PassiveDefinition> target,
            String classId,
            PassiveId passiveId,
            String displayName,
            String concept
    ) {
        target.put(classId, new PassiveDefinition(classId, passiveId, displayName, concept));
    }

    static Map<String, PassiveDefinition> definitions() {
        return DEFINITIONS;
    }

    static PassiveDefinition definitionFor(String classId) {
        if (classId == null) return null;
        return DEFINITIONS.get(classId.trim().toLowerCase(Locale.ROOT));
    }

    PlayerPassiveState stateFor(UUID playerId) {
        return playerId == null ? null : playerStates.get(playerId);
    }

    PlayerPassiveState stateFor(Player player) {
        return player == null ? null : stateFor(player.getUniqueId());
    }

    boolean hasPassive(Player player, PassiveId passiveId) {
        PlayerPassiveState state = stateFor(player);
        return state != null && state.passiveId() == passiveId;
    }

    int trackedPlayerCount() {
        return playerStates.size();
    }

    /**
     * Synchronizes runtime state with active TDM participants. Preparation,
     * Relic Rush, spectators, dead players, and offline players never own an
     * active passive state.
     */
    private void synchronizeActivePlayers() {
        Set<UUID> activePlayers = new HashSet<>();

        for (MatchState match : plugin.matches.values()) {
            if (!match.running || !"tdm".equalsIgnoreCase(match.mode)) continue;

            for (UUID playerId : match.teams.keySet()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline() || player.isDead()
                        || player.getGameMode() != GameMode.SURVIVAL) continue;

                String classId = match.selectedClasses.getOrDefault(playerId, "knight")
                        .trim().toLowerCase(Locale.ROOT);
                PassiveDefinition definition = DEFINITIONS.get(classId);
                if (definition == null) continue;

                activePlayers.add(playerId);
                PlayerPassiveState current = playerStates.get(playerId);
                if (current == null || !current.arenaName().equalsIgnoreCase(match.arenaName)
                        || !current.classId().equals(classId)) {
                    if (current != null) clearPlayer(playerId);
                    PlayerPassiveState replacement =
                            new PlayerPassiveState(playerId, match.arenaName, definition);
                    if (definition.passiveId() == PassiveId.STEADY_AIM) {
                        replacement.setDeadline("steady_aim.still_since", System.currentTimeMillis());
                    }
                    playerStates.put(playerId, replacement);
                }
            }
        }

        Set<UUID> stalePlayers = new HashSet<>();
        for (UUID playerId : playerStates.keySet()) {
            if (!activePlayers.contains(playerId)) stalePlayers.add(playerId);
        }
        for (UUID playerId : stalePlayers) clearPlayer(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        clearPlayer(event.getEntity().getUniqueId(), event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayer(event.getPlayer().getUniqueId(), event.getPlayer());
    }

    void clearPlayer(UUID playerId) {
        clearPlayer(playerId, playerId == null ? null : Bukkit.getPlayer(playerId));
    }

    private void clearPlayer(UUID playerId, Player playerOverride) {
        if (playerId == null) return;
        clearNinjaFollowupBoost(playerId, playerOverride);
        PlayerPassiveState removed = playerStates.remove(playerId);
        if (removed == null) return;
        archerSteadyAimShots.entrySet().removeIf(entry -> entry.getValue().shooterId().equals(playerId));
        sniperPrecisionShots.entrySet().removeIf(entry -> entry.getValue().shooterId().equals(playerId));
        Player player = playerOverride != null ? playerOverride : Bukkit.getPlayer(playerId);
        if (player != null && removed.passiveId() == PassiveId.SHADOW_VEIL
                && removed.flag("shadow_veil.active")) {
            if (plugin.classAbilityListener != null) plugin.classAbilityListener.endPassiveVanish(player);
            player.removePotionEffect(PotionEffectType.SPEED);
        }
        removed.clear();
    }

    void clearMatch(MatchState match) {
        if (match == null) return;
        Set<UUID> toClear = new HashSet<>();
        for (Map.Entry<UUID, PlayerPassiveState> entry : playerStates.entrySet()) {
            if (entry.getValue().arenaName().equalsIgnoreCase(match.arenaName)) {
                toClear.add(entry.getKey());
            }
        }
        for (UUID playerId : toClear) clearPlayer(playerId);
        Set<UUID> boostedPlayers = new HashSet<>();
        for (Map.Entry<UUID, NinjaFollowupBoost> entry : ninjaFollowupBoosts.entrySet()) {
            if (entry.getValue().arenaName().equalsIgnoreCase(match.arenaName)) {
                boostedPlayers.add(entry.getKey());
            }
        }
        for (UUID playerId : boostedPlayers) clearPlayer(playerId);
    }

    void cleanupAll() {
        if (synchronizationTask != null) synchronizationTask.cancel();
        for (PlayerPassiveState state : playerStates.values()) {
            Player player = Bukkit.getPlayer(state.playerId());
            if (player != null && state.passiveId() == PassiveId.SHADOW_VEIL
                    && state.flag("shadow_veil.active")) {
                if (plugin.classAbilityListener != null) plugin.classAbilityListener.endPassiveVanish(player);
                player.removePotionEffect(PotionEffectType.SPEED);
            }
            state.clear();
        }
        for (UUID playerId : new HashSet<>(ninjaFollowupBoosts.keySet())) {
            clearNinjaFollowupBoost(playerId, Bukkit.getPlayer(playerId));
        }
        playerStates.clear();
        archerSteadyAimShots.clear();
        sniperPrecisionShots.clear();
        ninjaFollowupBoosts.clear();
    }
}
