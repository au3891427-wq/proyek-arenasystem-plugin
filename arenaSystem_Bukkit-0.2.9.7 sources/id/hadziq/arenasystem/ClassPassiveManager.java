package id.hadziq.arenasystem;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

// $VF: Compiled from ClassPassiveManager.java
final class ClassPassiveManager implements Listener {
    private static final Map<String, ClassPassiveManager.PassiveDefinition> DEFINITIONS;
    private final ArenaSystemPlugin plugin;
    private final Map<UUID, ClassPassiveManager.PlayerPassiveState> playerStates = new HashMap<>();
    private static final long SHADOW_VEIL_REQUIRED_MILLIS = 5600L;
    private static final long SHADOW_VEIL_DURATION_MILLIS = 5300L;
    private static final int SHADOW_VEIL_DURATION_TICKS = 106;
    private static final double SHADOW_VEIL_BONUS_DAMAGE = 1.2;
    private static final long SHADOW_VEIL_MISSED_SWING_MILLIS = 450L;
    private static final int NINJA_FOLLOWUP_ATTACKS = 5;
    private static final double NINJA_FOLLOWUP_ATTACK_SPEED = 20.0;
    private static final long ARCHER_STEADY_AIM_REQUIRED_MILLIS = 1000L;
    private static final long TRACKED_PROJECTILE_LIFETIME_MILLIS = 30000L;
    private static final double ARCHER_STEADY_AIM_DAMAGE = 1.1;
    private static final double SNIPER_PRECISION_DAMAGE = 1.1;
    private static final double SNIPER_PRECISION_RANGE = 16.0;
    private static final double KNIGHT_GUARD_REDUCTION = 0.94;
    private static final double TANK_BULWARK_REDUCTION = 0.95;
    private static final double TANK_BULWARK_ABSORB_CHANCE = 0.15;
    private static final double TANK_BULWARK_ABSORB_REMAINING = 0.5;
    private static final long TANK_BULWARK_ABSORB_COOLDOWN_MILLIS = 3500L;
    private static final double ASSASSIN_EXECUTIONER_THRESHOLD = 0.35;
    private static final double ASSASSIN_EXECUTIONER_DAMAGE = 1.12;
    private static final int BOXER_RHYTHM_HITS_REQUIRED = 3;
    private static final long BOXER_RHYTHM_COOLDOWN_REDUCTION_MILLIS = 5000L;
    private static final double BOXER_RHYTHM_THIRD_JAB_DAMAGE = 1.2;
    private static final long DEMOMAN_OWN_BLAST_WINDOW_MILLIS = 500L;
    private final Map<UUID, ClassPassiveManager.TrackedShot> archerSteadyAimShots = new HashMap<>();
    private final Map<UUID, ClassPassiveManager.TrackedShot> sniperPrecisionShots = new HashMap<>();
    private final Map<UUID, ClassPassiveManager.NinjaFollowupBoost> ninjaFollowupBoosts = new HashMap<>();
    private final BukkitTask synchronizationTask;

    ClassPassiveManager(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
        this.synchronizationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        this.synchronizeActivePlayers();
        this.cleanupTrackedProjectiles();
        this.tickNinjaShadowVeil();
    }

    private void cleanupTrackedProjectiles() {
        long now = System.currentTimeMillis();
        this.archerSteadyAimShots.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
        this.sniperPrecisionShots.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    private void tickNinjaShadowVeil() {
        long now = System.currentTimeMillis();

        for (ClassPassiveManager.PlayerPassiveState state : this.playerStates.values()) {
            if (state.passiveId() == ClassPassiveManager.PassiveId.SHADOW_VEIL) {
                Player player = Bukkit.getPlayer(state.playerId());
                if (player != null
                    && player.isOnline()
                    && !player.isDead()
                    && player.getGameMode() == GameMode.SURVIVAL) {
                    if (state.flag("shadow_veil.active")) {
                        long expiresAt = state.deadline("shadow_veil.expires_at");
                        if (expiresAt > 0L && now >= expiresAt) {
                            this.endShadowVeil(player, state, now, "expired");
                        } else {
                            long pendingSwingUntil = state.deadline("shadow_veil.pending_swing_until");
                            if (state.flag("shadow_veil.pending_swing")
                                && pendingSwingUntil > 0L
                                && now >= pendingSwingUntil) {
                                this.endShadowVeil(player, state, now, "missed");
                            }
                        }
                    } else if (this.plugin.classAbilityListener != null
                        && this.plugin.classAbilityListener.isVanished(player)) {
                        state.setDeadline("shadow_veil.last_activity_at", now);
                        state.clearDeadline("shadow_veil.sneak_since");
                    } else if (!player.isSneaking()) {
                        state.clearDeadline("shadow_veil.sneak_since");
                        state.setFlag("shadow_veil.pending_swing", false);
                        state.clearDeadline("shadow_veil.pending_swing_until");
                    } else {
                        long sneakSince = state.deadline("shadow_veil.sneak_since");
                        if (sneakSince <= 0L) {
                            sneakSince = now;
                            state.setDeadline("shadow_veil.sneak_since", sneakSince);
                        }

                        long lastActivity = state.deadline("shadow_veil.last_activity_at");
                        if (lastActivity <= 0L) {
                            lastActivity = state.startedAtMillis();
                        }

                        long eligibleSince = Math.max(sneakSince, lastActivity);
                        if (now - eligibleSince >= 5600L) {
                            this.activateShadowVeil(player, state, now);
                        }
                    }
                }
            }
        }
    }

    private void activateShadowVeil(Player player, ClassPassiveManager.PlayerPassiveState state, long now) {
        if (this.plugin.classAbilityListener != null
            && this.plugin.classAbilityListener.startPassiveVanish(player, 106)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 106, 1, false, false, true), true);
            state.setFlag("shadow_veil.active", true);
            state.setFlag("shadow_veil.bonus_ready", true);
            state.setFlag("shadow_veil.pending_swing", false);
            state.setDeadline("shadow_veil.expires_at", now + 5300L);
            state.clearDeadline("shadow_veil.pending_swing_until");
            player.getWorld()
                .spawnParticle(Particle.SMOKE, player.getLocation().add(0.0, 1.0, 0.0), 24, 0.38, 0.55, 0.38, 0.025);
            player.getWorld()
                .spawnParticle(Particle.SOUL, player.getLocation().add(0.0, 0.9, 0.0), 8, 0.28, 0.42, 0.28, 0.015);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.65F, 1.7F);
            player.sendMessage(
                ChatColor.DARK_GREEN
                    + "Shadow Veil aktif! Speed II + menghilang selama 5,3 detik. Serangan pertama ke lawan mendapat +20% damage."
            );
            player.sendActionBar(ChatColor.DARK_GREEN + "SHADOW VEIL ACTIVE");
        } else {
            state.setDeadline("shadow_veil.last_activity_at", now);
        }
    }

    private void endShadowVeil(Player player, ClassPassiveManager.PlayerPassiveState state, long now, String reason) {
        boolean wasActive = state.flag("shadow_veil.active");
        state.setFlag("shadow_veil.active", false);
        state.setFlag("shadow_veil.bonus_ready", false);
        state.setFlag("shadow_veil.pending_swing", false);
        state.setFlag("shadow_veil.followup_trigger_pending", false);
        state.clearDeadline("shadow_veil.expires_at");
        state.clearDeadline("shadow_veil.pending_swing_until");
        state.setDeadline("shadow_veil.last_activity_at", now);
        if (wasActive) {
            if (this.plugin.classAbilityListener != null) {
                this.plugin.classAbilityListener.endPassiveVanish(player);
            }

            player.removePotionEffect(PotionEffectType.SPEED);
            if (player.isOnline() && !player.isDead() && player.getGameMode() == GameMode.SURVIVAL) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 72000, 0, false, false, true), true);
            }

            if (player.isOnline()) {
                if ("hit".equals(reason)) {
                    player.sendActionBar(ChatColor.YELLOW + "SHADOW VEIL BROKEN — kamu terkena serangan");
                } else if ("attack".equals(reason)) {
                    player.sendActionBar(ChatColor.GREEN + "AMBUSH! +20% damage");
                } else if ("missed".equals(reason)) {
                    player.sendActionBar(ChatColor.GRAY + "Shadow Veil berakhir — serangan tidak mengenai target");
                } else if ("expired".equals(reason)) {
                    player.sendActionBar(
                        ChatColor.GRAY + "Shadow Veil berakhir. Sneak lagi selama 5,6 detik untuk mengaktifkan ulang."
                    );
                }
            }
        }
    }

    private void recordShadowVeilActivity(
        Player player, ClassPassiveManager.PlayerPassiveState state, long now, String reason
    ) {
        if (state != null && state.passiveId() == ClassPassiveManager.PassiveId.SHADOW_VEIL) {
            if (state.flag("shadow_veil.active")) {
                this.endShadowVeil(player, state, now, reason);
            } else {
                state.setDeadline("shadow_veil.last_activity_at", now);
                state.clearDeadline("shadow_veil.sneak_since");
            }
        }
    }

    private static Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        } else {
            return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player
                ? player
                : null;
        }
    }

    private boolean areOpponentsInSameMatch(Player first, Player second) {
        ClassPassiveManager.PlayerPassiveState firstState = this.stateFor(first);
        ClassPassiveManager.PlayerPassiveState secondState = this.stateFor(second);
        if (firstState != null
            && secondState != null
            && firstState.arenaName().equalsIgnoreCase(secondState.arenaName())) {
            MatchState match = this.plugin.matches.get(firstState.arenaName().toLowerCase(Locale.ROOT));
            if (match != null && match.running && "tdm".equalsIgnoreCase(match.mode)) {
                String firstTeam = match.teams.get(first.getUniqueId());
                String secondTeam = match.teams.get(second.getUniqueId());
                return firstTeam != null && secondTeam != null && !firstTeam.equalsIgnoreCase(secondTeam);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPassiveMovement(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && from.getWorld().equals(to.getWorld()) && !(from.distanceSquared(to) <= 0.0025)) {
            ClassPassiveManager.PlayerPassiveState state = this.stateFor(event.getPlayer());
            if (state != null && state.passiveId() == ClassPassiveManager.PassiveId.STEADY_AIM) {
                state.setDeadline("steady_aim.still_since", System.currentTimeMillis());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPassiveBowShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && event.getProjectile() instanceof Arrow arrow) {
            ClassPassiveManager.PlayerPassiveState state = this.stateFor(player);
            if (state != null) {
                long now = System.currentTimeMillis();
                long expiresAt = now + 30000L;
                Location origin = player.getLocation().clone();
                ClassPassiveManager.TrackedShot shot = new ClassPassiveManager.TrackedShot(
                    player.getUniqueId(), state.arenaName(), expiresAt, origin
                );
                if (state.passiveId() == ClassPassiveManager.PassiveId.STEADY_AIM) {
                    long stillSince = state.deadline("steady_aim.still_since");
                    if (stillSince <= 0L) {
                        state.setDeadline("steady_aim.still_since", now);
                        return;
                    }

                    if (now - stillSince >= 1000L && !player.isSprinting()) {
                        this.archerSteadyAimShots.put(arrow.getUniqueId(), shot);
                        player.sendActionBar(ChatColor.GREEN + "STEADY AIM — panah mendapat +10% damage");
                        player.getWorld().spawnParticle(Particle.CRIT, arrow.getLocation(), 5, 0.08, 0.08, 0.08, 0.015);
                    }
                } else if (state.passiveId() == ClassPassiveManager.PassiveId.PRECISION) {
                    this.sniperPrecisionShots.put(arrow.getUniqueId(), shot);
                }
            }
        }
    }

    @EventHandler
    public void onPassiveProjectileHit(ProjectileHitEvent event) {
        UUID projectileId = event.getEntity().getUniqueId();
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.archerSteadyAimShots.remove(projectileId);
            this.sniperPrecisionShots.remove(projectileId);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClassPassiveDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player target) {
            Player attacker = resolvePlayerDamager(event.getDamager());
            if (attacker != null && !attacker.equals(target) && this.areOpponentsInSameMatch(attacker, target)) {
                long now = System.currentTimeMillis();
                ClassPassiveManager.PlayerPassiveState attackerState = this.stateFor(attacker);
                ClassPassiveManager.PlayerPassiveState targetState = this.stateFor(target);
                boolean directMelee = event.getDamager() == attacker && event.getCause() == DamageCause.ENTITY_ATTACK;
                if (attackerState != null) {
                    if (event.getDamager() instanceof Arrow arrow) {
                        ClassPassiveManager.TrackedShot archerShot = this.archerSteadyAimShots
                            .remove(arrow.getUniqueId());
                        if (attackerState.passiveId() == ClassPassiveManager.PassiveId.STEADY_AIM
                            && archerShot != null
                            && attacker.getUniqueId().equals(archerShot.shooterId())
                            && archerShot.expiresAtMillis() > now
                            && archerShot.arenaName().equalsIgnoreCase(attackerState.arenaName())) {
                            event.setDamage(event.getDamage() * 1.1);
                            attacker.sendActionBar(ChatColor.GREEN + "STEADY AIM: +10% damage");
                        }

                        ClassPassiveManager.TrackedShot sniperShot = this.sniperPrecisionShots
                            .remove(arrow.getUniqueId());
                        if (attackerState.passiveId() == ClassPassiveManager.PassiveId.PRECISION
                            && sniperShot != null
                            && attacker.getUniqueId().equals(sniperShot.shooterId())
                            && sniperShot.expiresAtMillis() > now
                            && sniperShot.arenaName().equalsIgnoreCase(attackerState.arenaName())
                            && sniperShot.origin() != null
                            && sniperShot.origin().getWorld() != null
                            && sniperShot.origin().getWorld().equals(target.getWorld())
                            && sniperShot.origin().distanceSquared(target.getLocation()) >= 256.0) {
                            event.setDamage(event.getDamage() * 1.1);
                            attacker.sendActionBar(ChatColor.AQUA + "PRECISION: +10% long-range damage");
                        }
                    }

                    if (attackerState.passiveId() == ClassPassiveManager.PassiveId.EXECUTIONER && directMelee) {
                        AttributeInstance maxHealthAttribute = target.getAttribute(Attribute.MAX_HEALTH);
                        double maxHealth = maxHealthAttribute == null ? 20.0 : maxHealthAttribute.getValue();
                        if (maxHealth > 0.0 && target.getHealth() <= maxHealth * 0.35) {
                            event.setDamage(event.getDamage() * 1.12);
                            attacker.sendActionBar(ChatColor.DARK_RED + "EXECUTIONER: +12% finishing damage");
                        }
                    }

                    if (attackerState.passiveId() == ClassPassiveManager.PassiveId.RHYTHM
                        && directMelee
                        && event.getDamage() > 0.0
                        && attacker.getInventory().getItemInMainHand().getType().isAir()
                        && (
                            this.plugin.classAbilityListener == null
                                || !this.plugin.classAbilityListener.isBoxerSkillDamage(attacker)
                        )) {
                        int nextHit = attackerState.addCounter("rhythm.landed_jabs", 1);
                        if (nextHit >= 3) {
                            attackerState.resetCounter("rhythm.landed_jabs");
                            event.setDamage(event.getDamage() * 1.2);
                            attacker.sendActionBar(ChatColor.LIGHT_PURPLE + "RHYTHM! JAB KETIGA +20% DAMAGE");
                            long reduced = this.plugin.classAbilityListener == null
                                ? 0L
                                : this.plugin
                                    .classAbilityListener
                                    .reduceSkillCooldown(
                                        attacker,
                                        this.plugin.matches.get(attackerState.arenaName().toLowerCase(Locale.ROOT)),
                                        "boxer_rapid_jab",
                                        5000L
                                    );
                            if (reduced > 0L) {
                                attacker.sendMessage(
                                    ChatColor.LIGHT_PURPLE + "Rhythm: cooldown Skill 1 berkurang 5 detik."
                                );
                            }

                            attacker.getWorld()
                                .spawnParticle(
                                    Particle.CRIT, attacker.getLocation().add(0.0, 1.0, 0.0), 10, 0.3, 0.4, 0.3, 0.03
                                );
                            attacker.getWorld()
                                .playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8F, 1.7F);
                        } else {
                            attacker.sendActionBar(ChatColor.LIGHT_PURPLE + "RHYTHM: " + nextHit + "/3 landed jabs");
                        }
                    }
                }

                if (targetState != null) {
                    if (targetState.passiveId() == ClassPassiveManager.PassiveId.GUARD) {
                        event.setDamage(event.getDamage() * 0.94);
                        if (now >= targetState.deadline("guard.feedback_ready_at")) {
                            targetState.setDeadline("guard.feedback_ready_at", now + 900L);
                            target.sendActionBar(ChatColor.AQUA + "GUARD: 6% damage reduced");
                        }
                    } else if (targetState.passiveId() == ClassPassiveManager.PassiveId.BULWARK) {
                        event.setDamage(event.getDamage() * 0.95);
                        long nextAbsorbAt = targetState.deadline("bulwark.next_absorb_at");
                        if (now >= nextAbsorbAt && ThreadLocalRandom.current().nextDouble() < 0.15) {
                            event.setDamage(event.getDamage() * 0.5);
                            targetState.setDeadline("bulwark.next_absorb_at", now + 3500L);
                            target.getWorld()
                                .spawnParticle(
                                    Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 14, 0.35, 0.45, 0.35, 0.035
                                );
                            target.getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8F, 1.35F);
                            target.sendActionBar(ChatColor.GOLD + "BULWARK: 50% damage absorbed!");
                        }
                    }
                }
            }
        }
    }

    void markDemomanOwnExplosion(Player player) {
        ClassPassiveManager.PlayerPassiveState state = this.stateFor(player);
        if (state != null && state.passiveId() == ClassPassiveManager.PassiveId.DEMOLITION_EXPERT) {
            state.setDeadline("demolition_expert.own_blast_until", System.currentTimeMillis() + 500L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDemomanOwnExplosionVelocity(PlayerVelocityEvent event) {
        ClassPassiveManager.PlayerPassiveState state = this.stateFor(event.getPlayer());
        if (state != null && state.passiveId() == ClassPassiveManager.PassiveId.DEMOLITION_EXPERT) {
            long markedUntil = state.deadline("demolition_expert.own_blast_until");
            if (markedUntil > System.currentTimeMillis()) {
                Vector velocity = event.getVelocity().clone();
                velocity.setX(velocity.getX() * 0.7);
                velocity.setZ(velocity.getZ() * 0.7);
                velocity.setY(velocity.getY() * 0.8);
                event.setVelocity(velocity);
                state.clearDeadline("demolition_expert.own_blast_until");
                event.getPlayer().sendActionBar(ChatColor.GOLD + "DEMOLITION EXPERT: blast recoil reduced");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNinjaCombat(EntityDamageByEntityEvent event) {
        long now = System.currentTimeMillis();
        Player attacker = resolvePlayerDamager(event.getDamager());
        Player target = event.getEntity() instanceof Player player ? player : null;
        if (attacker != null) {
            ClassPassiveManager.PlayerPassiveState attackerState = this.stateFor(attacker);
            if (attackerState != null && attackerState.passiveId() == ClassPassiveManager.PassiveId.SHADOW_VEIL) {
                boolean validTarget = target != null && this.areOpponentsInSameMatch(attacker, target);
                long expiresAt = attackerState.deadline("shadow_veil.expires_at");
                long pendingSwingUntil = attackerState.deadline("shadow_veil.pending_swing_until");
                boolean withinStealthWindow = attackerState.flag("shadow_veil.active")
                    && expiresAt > now
                    && (!attackerState.flag("shadow_veil.pending_swing") || pendingSwingUntil > now);
                boolean ambushTriggered = validTarget
                    && withinStealthWindow
                    && attackerState.flag("shadow_veil.bonus_ready");
                if (ambushTriggered) {
                    event.setDamage(event.getDamage() * 1.2);
                    attacker.sendMessage(ChatColor.GREEN + "Shadow Veil: Ambush berhasil! Damage serangan +20%.");
                }

                this.recordShadowVeilActivity(attacker, attackerState, now, "attack");
                if (ambushTriggered) {
                    attackerState.setFlag("shadow_veil.followup_trigger_pending", true);
                }
            }
        }

        if (target != null) {
            ClassPassiveManager.PlayerPassiveState targetState = this.stateFor(target);
            if (targetState != null && targetState.passiveId() == ClassPassiveManager.PassiveId.SHADOW_VEIL) {
                this.recordShadowVeilActivity(target, targetState, now, "hit");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNinjaFollowupHit(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker != null && event.getEntity() instanceof Player target) {
            ClassPassiveManager.PlayerPassiveState state = this.stateFor(attacker);
            if (state != null
                && state.passiveId() == ClassPassiveManager.PassiveId.SHADOW_VEIL
                && this.areOpponentsInSameMatch(attacker, target)) {
                if (state.flag("shadow_veil.followup_trigger_pending")) {
                    state.setFlag("shadow_veil.followup_trigger_pending", false);
                    if (!event.isCancelled() && event.getFinalDamage() > 0.0) {
                        this.activateNinjaFollowupBoost(attacker, state);
                    }
                } else if (!event.isCancelled() && !(event.getFinalDamage() <= 0.0)) {
                    this.consumeNinjaFollowupHit(attacker, state);
                }
            }
        }
    }

    private void activateNinjaFollowupBoost(Player player, ClassPassiveManager.PlayerPassiveState state) {
        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) {
            UUID playerId = player.getUniqueId();
            ClassPassiveManager.NinjaFollowupBoost current = this.ninjaFollowupBoosts.get(playerId);
            double originalBase = current == null ? attackSpeed.getBaseValue() : current.originalBaseAttackSpeed();
            this.ninjaFollowupBoosts
                .put(playerId, new ClassPassiveManager.NinjaFollowupBoost(state.arenaName(), originalBase, 5));
            attackSpeed.setBaseValue(20.0);
            player.sendActionBar(ChatColor.DARK_GREEN + "SHADOW VEIL FOLLOW-UP — 5 landed hits boosted");
        }
    }

    private void consumeNinjaFollowupHit(Player player, ClassPassiveManager.PlayerPassiveState state) {
        UUID playerId = player.getUniqueId();
        ClassPassiveManager.NinjaFollowupBoost boost = this.ninjaFollowupBoosts.get(playerId);
        if (boost != null) {
            if (!boost.arenaName().equalsIgnoreCase(state.arenaName())) {
                this.clearNinjaFollowupBoost(playerId, player);
            } else {
                int remaining = boost.attacksRemaining() - 1;
                if (remaining <= 0) {
                    this.clearNinjaFollowupBoost(playerId, player);
                    player.sendActionBar(ChatColor.GRAY + "FOLLOW-UP selesai — attack speed normal");
                } else {
                    this.ninjaFollowupBoosts
                        .put(
                            playerId,
                            new ClassPassiveManager.NinjaFollowupBoost(
                                boost.arenaName(), boost.originalBaseAttackSpeed(), remaining
                            )
                        );
                    player.sendActionBar(ChatColor.DARK_GREEN + "FOLLOW-UP: " + remaining + "/5 hit tersisa");
                }
            }
        }
    }

    private void clearNinjaFollowupBoost(UUID playerId, Player playerOverride) {
        ClassPassiveManager.NinjaFollowupBoost boost = this.ninjaFollowupBoosts.remove(playerId);
        if (boost != null) {
            Player player = playerOverride != null ? playerOverride : Bukkit.getPlayer(playerId);
            if (player != null) {
                AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
                if (attackSpeed != null) {
                    attackSpeed.setBaseValue(boost.originalBaseAttackSpeed());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNinjaProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            ClassPassiveManager.PlayerPassiveState state = this.stateFor(player);
            if (state != null && state.passiveId() == ClassPassiveManager.PassiveId.SHADOW_VEIL) {
                this.recordShadowVeilActivity(player, state, System.currentTimeMillis(), "attack");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNinjaProjectileHit(ProjectileHitEvent event) {
        if (event.getHitEntity() instanceof Player target) {
            ClassPassiveManager.PlayerPassiveState state = this.stateFor(target);
            if (state != null && state.passiveId() == ClassPassiveManager.PassiveId.SHADOW_VEIL) {
                this.recordShadowVeilActivity(target, state, System.currentTimeMillis(), "hit");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNinjaArmSwing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        ClassPassiveManager.PlayerPassiveState state = this.stateFor(player);
        if (state != null && state.passiveId() == ClassPassiveManager.PassiveId.SHADOW_VEIL) {
            if (state.flag("shadow_veil.active")) {
                state.setFlag("shadow_veil.pending_swing", true);
                state.setDeadline("shadow_veil.pending_swing_until", System.currentTimeMillis() + 450L);
            } else {
                this.recordShadowVeilActivity(player, state, System.currentTimeMillis(), "attack");
            }
        }
    }

    private static void register(
        Map<String, ClassPassiveManager.PassiveDefinition> target,
        String classId,
        ClassPassiveManager.PassiveId passiveId,
        String displayName,
        String concept
    ) {
        target.put(classId, new ClassPassiveManager.PassiveDefinition(classId, passiveId, displayName, concept));
    }

    static Map<String, ClassPassiveManager.PassiveDefinition> definitions() {
        return DEFINITIONS;
    }

    static ClassPassiveManager.PassiveDefinition definitionFor(String classId) {
        return classId == null ? null : DEFINITIONS.get(classId.trim().toLowerCase(Locale.ROOT));
    }

    ClassPassiveManager.PlayerPassiveState stateFor(UUID playerId) {
        return playerId == null ? null : this.playerStates.get(playerId);
    }

    ClassPassiveManager.PlayerPassiveState stateFor(Player player) {
        return player == null ? null : this.stateFor(player.getUniqueId());
    }

    boolean hasPassive(Player player, ClassPassiveManager.PassiveId passiveId) {
        ClassPassiveManager.PlayerPassiveState state = this.stateFor(player);
        return state != null && state.passiveId() == passiveId;
    }

    int trackedPlayerCount() {
        return this.playerStates.size();
    }

    private void synchronizeActivePlayers() {
        Set<UUID> activePlayers = new HashSet<>();

        for (MatchState match : this.plugin.matches.values()) {
            if (match.running && "tdm".equalsIgnoreCase(match.mode)) {
                for (UUID playerId : match.teams.keySet()) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null
                        && player.isOnline()
                        && !player.isDead()
                        && player.getGameMode() == GameMode.SURVIVAL) {
                        String classId = match.selectedClasses
                            .getOrDefault(playerId, "knight")
                            .trim()
                            .toLowerCase(Locale.ROOT);
                        ClassPassiveManager.PassiveDefinition definition = DEFINITIONS.get(classId);
                        if (definition != null) {
                            activePlayers.add(playerId);
                            ClassPassiveManager.PlayerPassiveState current = this.playerStates.get(playerId);
                            if (current == null
                                || !current.arenaName().equalsIgnoreCase(match.arenaName)
                                || !current.classId().equals(classId)) {
                                if (current != null) {
                                    this.clearPlayer(playerId);
                                }

                                ClassPassiveManager.PlayerPassiveState replacement = new ClassPassiveManager.PlayerPassiveState(
                                    playerId, match.arenaName, definition
                                );
                                if (definition.passiveId() == ClassPassiveManager.PassiveId.STEADY_AIM) {
                                    replacement.setDeadline("steady_aim.still_since", System.currentTimeMillis());
                                }

                                this.playerStates.put(playerId, replacement);
                            }
                        }
                    }
                }
            }
        }

        Set<UUID> stalePlayers = new HashSet<>();

        for (UUID playerId : this.playerStates.keySet()) {
            if (!activePlayers.contains(playerId)) {
                stalePlayers.add(playerId);
            }
        }

        for (UUID playerId : stalePlayers) {
            this.clearPlayer(playerId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        this.clearPlayer(event.getEntity().getUniqueId(), event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.clearPlayer(event.getPlayer().getUniqueId(), event.getPlayer());
    }

    void clearPlayer(UUID playerId) {
        this.clearPlayer(playerId, playerId == null ? null : Bukkit.getPlayer(playerId));
    }

    private void clearPlayer(UUID playerId, Player playerOverride) {
        if (playerId != null) {
            this.clearNinjaFollowupBoost(playerId, playerOverride);
            ClassPassiveManager.PlayerPassiveState removed = this.playerStates.remove(playerId);
            if (removed != null) {
                this.archerSteadyAimShots.entrySet().removeIf(entry -> entry.getValue().shooterId().equals(playerId));
                this.sniperPrecisionShots.entrySet().removeIf(entry -> entry.getValue().shooterId().equals(playerId));
                Player player = playerOverride != null ? playerOverride : Bukkit.getPlayer(playerId);
                if (player != null
                    && removed.passiveId() == ClassPassiveManager.PassiveId.SHADOW_VEIL
                    && removed.flag("shadow_veil.active")) {
                    if (this.plugin.classAbilityListener != null) {
                        this.plugin.classAbilityListener.endPassiveVanish(player);
                    }

                    player.removePotionEffect(PotionEffectType.SPEED);
                }

                removed.clear();
            }
        }
    }

    void clearMatch(MatchState match) {
        if (match != null) {
            Set<UUID> toClear = new HashSet<>();

            for (Entry<UUID, ClassPassiveManager.PlayerPassiveState> entry : this.playerStates.entrySet()) {
                if (entry.getValue().arenaName().equalsIgnoreCase(match.arenaName)) {
                    toClear.add(entry.getKey());
                }
            }

            for (UUID playerId : toClear) {
                this.clearPlayer(playerId);
            }

            Set<UUID> boostedPlayers = new HashSet<>();

            for (Entry<UUID, ClassPassiveManager.NinjaFollowupBoost> entry : this.ninjaFollowupBoosts.entrySet()) {
                if (entry.getValue().arenaName().equalsIgnoreCase(match.arenaName)) {
                    boostedPlayers.add(entry.getKey());
                }
            }

            for (UUID playerId : boostedPlayers) {
                this.clearPlayer(playerId);
            }
        }
    }

    void cleanupAll() {
        if (this.synchronizationTask != null) {
            this.synchronizationTask.cancel();
        }

        for (ClassPassiveManager.PlayerPassiveState state : this.playerStates.values()) {
            Player player = Bukkit.getPlayer(state.playerId());
            if (player != null
                && state.passiveId() == ClassPassiveManager.PassiveId.SHADOW_VEIL
                && state.flag("shadow_veil.active")) {
                if (this.plugin.classAbilityListener != null) {
                    this.plugin.classAbilityListener.endPassiveVanish(player);
                }

                player.removePotionEffect(PotionEffectType.SPEED);
            }

            state.clear();
        }

        for (UUID playerId : new HashSet<>(this.ninjaFollowupBoosts.keySet())) {
            this.clearNinjaFollowupBoost(playerId, Bukkit.getPlayer(playerId));
        }

        this.playerStates.clear();
        this.archerSteadyAimShots.clear();
        this.sniperPrecisionShots.clear();
        this.ninjaFollowupBoosts.clear();
    }

    static {
        Map<String, ClassPassiveManager.PassiveDefinition> definitions = new HashMap<>();
        register(
            definitions,
            "knight",
            ClassPassiveManager.PassiveId.GUARD,
            "Guard",
            "Ketahanan tambahan untuk petarung garis depan."
        );
        register(
            definitions,
            "archer",
            ClassPassiveManager.PassiveId.STEADY_AIM,
            "Steady Aim",
            "Keunggulan saat menjaga bidikan dan menembakkan panah."
        );
        register(
            definitions,
            "sniper",
            ClassPassiveManager.PassiveId.PRECISION,
            "Precision",
            "Keunggulan untuk serangan jarak jauh yang terarah."
        );
        register(
            definitions,
            "tank",
            ClassPassiveManager.PassiveId.BULWARK,
            "Bulwark",
            "Pertahanan berat dengan peluang menyerap sebagian damage."
        );
        register(
            definitions,
            "assassin",
            ClassPassiveManager.PassiveId.EXECUTIONER,
            "Executioner",
            "Tekanan tambahan terhadap lawan yang hampir kalah."
        );
        register(
            definitions,
            "ninja",
            ClassPassiveManager.PassiveId.SHADOW_VEIL,
            "Shadow Veil",
            "Stealth setelah berhasil menghindari pertarungan."
        );
        register(
            definitions,
            "boxer",
            ClassPassiveManager.PassiveId.RHYTHM,
            "Rhythm",
            "Setiap jab ketiga memberi +20% damage dan mengurangi cooldown Skill 1 sebesar 5 detik jika sedang cooldown."
        );
        register(
            definitions,
            "demoman",
            ClassPassiveManager.PassiveId.DEMOLITION_EXPERT,
            "Demolition Expert",
            "Ketahanan terhadap efek ledakan milik sendiri."
        );
        DEFINITIONS = Collections.unmodifiableMap(definitions);
    }

    // $VF: Compiled from ClassPassiveManager.java
    private record NinjaFollowupBoost(String arenaName, double originalBaseAttackSpeed, int attacksRemaining) {
    }

    // $VF: Compiled from ClassPassiveManager.java
    record PassiveDefinition(
        String classId, ClassPassiveManager.PassiveId passiveId, String displayName, String concept
    ) {
    }

    // $VF: Compiled from ClassPassiveManager.java
    enum PassiveId {
        GUARD,
        STEADY_AIM,
        PRECISION,
        BULWARK,
        EXECUTIONER,
        SHADOW_VEIL,
        RHYTHM,
        DEMOLITION_EXPERT;
    }

    // $VF: Compiled from ClassPassiveManager.java
    static final class PlayerPassiveState {
        private final UUID playerId;
        private final String arenaName;
        private final ClassPassiveManager.PassiveDefinition definition;
        private final long startedAtMillis;
        private final Map<String, Integer> counters = new HashMap<>();
        private final Map<String, Long> deadlines = new HashMap<>();
        private final Set<String> flags = new HashSet<>();

        private PlayerPassiveState(UUID playerId, String arenaName, ClassPassiveManager.PassiveDefinition definition) {
            this.playerId = playerId;
            this.arenaName = arenaName;
            this.definition = definition;
            this.startedAtMillis = System.currentTimeMillis();
        }

        UUID playerId() {
            return this.playerId;
        }

        String arenaName() {
            return this.arenaName;
        }

        String classId() {
            return this.definition.classId();
        }

        ClassPassiveManager.PassiveId passiveId() {
            return this.definition.passiveId();
        }

        ClassPassiveManager.PassiveDefinition definition() {
            return this.definition;
        }

        long startedAtMillis() {
            return this.startedAtMillis;
        }

        int counter(String key) {
            return this.counters.getOrDefault(normalizeKey(key), 0);
        }

        int addCounter(String key, int amount) {
            String safeKey = normalizeKey(key);
            return this.counters.merge(safeKey, amount, Integer::sum);
        }

        void setCounter(String key, int value) {
            this.counters.put(normalizeKey(key), Math.max(0, value));
        }

        void resetCounter(String key) {
            this.counters.remove(normalizeKey(key));
        }

        long deadline(String key) {
            return this.deadlines.getOrDefault(normalizeKey(key), 0L);
        }

        void setDeadline(String key, long epochMillis) {
            this.deadlines.put(normalizeKey(key), Math.max(0L, epochMillis));
        }

        void clearDeadline(String key) {
            this.deadlines.remove(normalizeKey(key));
        }

        boolean flag(String key) {
            return this.flags.contains(normalizeKey(key));
        }

        void setFlag(String key, boolean enabled) {
            String safeKey = normalizeKey(key);
            if (enabled) {
                this.flags.add(safeKey);
            } else {
                this.flags.remove(safeKey);
            }
        }

        private void clear() {
            this.counters.clear();
            this.deadlines.clear();
            this.flags.clear();
        }

        private static String normalizeKey(String key) {
            if (key != null && !key.isBlank()) {
                return key.trim().toLowerCase(Locale.ROOT);
            } else {
                throw new IllegalArgumentException("Passive state key must not be blank");
            }
        }
    }

    // $VF: Compiled from ClassPassiveManager.java
    private record TrackedShot(UUID shooterId, String arenaName, long expiresAtMillis, Location origin) {
    }
}
