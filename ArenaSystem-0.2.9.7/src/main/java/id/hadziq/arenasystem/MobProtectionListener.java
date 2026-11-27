package id.hadziq.arenasystem;

import io.papermc.paper.event.entity.EntityEffectTickEvent;
import io.papermc.paper.event.entity.EntityMoveEvent;
import io.papermc.paper.event.entity.WardenAngerChangeEvent;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.NPC;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPotionEffectEvent.Cause;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

// $VF: Compiled from MobProtectionListener.java
final class MobProtectionListener implements Listener {
    private final ArenaSystemPlugin plugin;

    MobProtectionListener(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isProtectedEnemy(Entity entity) {
        return entity instanceof Enemy
            && !(entity instanceof Player)
            && !(entity instanceof NPC)
            && !this.isBlastMineMarker(entity)
            && !entity.hasMetadata("NPC")
            && !(entity instanceof Tameable tameable && tameable.isTamed());
    }

    private boolean isArenaBlockedMob(Entity entity) {
        if (entity != null
            && !(entity instanceof Player)
            && !(entity instanceof NPC)
            && !entity.hasMetadata("NPC")
            && !this.isBlastMineMarker(entity)) {
            EntityType type = entity.getType();
            return this.isProtectedEnemy(entity)
                || type == EntityType.WANDERING_TRADER
                || type == EntityType.TRADER_LLAMA;
        } else {
            return false;
        }
    }

    private boolean isMatchParticipant(Player player) {
        if (player == null) {
            return false;
        }

        for (MatchState state : this.plugin.matches.values()) {
            boolean activeLifecycle = state.running || state.preparing || state.launching || state.ending;
            if (activeLifecycle && state.teams.containsKey(player.getUniqueId())) {
                ArenaData arena = this.plugin.arenas.get(state.arenaName);
                if (arena != null && arena.worldName.equals(player.getWorld().getName())) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isInsideArenaHorizontalBounds(Player player) {
        if (player == null) {
            return false;
        }

        Location location = player.getLocation();
        String worldName = location.getWorld().getName();
        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();

        for (ArenaData arena : this.plugin.arenas.values()) {
            if (arena.worldName.equals(worldName)
                && Math.abs(blockX - arena.centerX) <= arena.radius
                && Math.abs(blockZ - arena.centerZ) <= arena.radius) {
                return true;
            }
        }

        for (ArenaData arena : this.plugin.incompleteArenas.values()) {
            if (arena.worldName.equals(worldName)
                && Math.abs(blockX - arena.centerX) <= arena.radius
                && Math.abs(blockZ - arena.centerZ) <= arena.radius) {
                return true;
            }
        }

        return false;
    }

    private boolean isProtectedPlayer(Player player) {
        return player != null && (this.isInsideArenaHorizontalBounds(player) || this.isMatchParticipant(player));
    }

    private boolean isHostileDamageSource(Entity source) {
        return source != null
            && (
                this.isProtectedEnemy(source)
                    || source instanceof Projectile projectile
                        && projectile.getShooter() instanceof Entity shooter
                        && this.isProtectedEnemy(shooter)
                    || source instanceof EvokerFangs fangs && this.isProtectedEnemy(fangs.getOwner())
                    || source instanceof AreaEffectCloud cloud
                        && cloud.getSource() instanceof Entity shooter
                        && this.isProtectedEnemy(shooter)
            );
    }

    private boolean isHostilePotionSource(ProjectileSource source) {
        return source instanceof Entity entity && this.isProtectedEnemy(entity);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (this.isBlastMineMarker(entity)) {
            event.setCancelled(false);
        } else if (this.isArenaBlockedMob(entity)) {
            if (this.plugin.findArenaFootprint(event.getLocation()) != null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobEnterArena(EntityMoveEvent event) {
        Entity entity = event.getEntity();
        if (this.isArenaBlockedMob(entity)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null) {
                ArenaData destinationArena = this.plugin.findArenaFootprint(to);
                if (destinationArena != null) {
                    if (this.plugin.findArenaFootprint(from) != destinationArena) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobTeleportIntoArena(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (this.isArenaBlockedMob(entity)) {
            Location to = event.getTo();
            if (to != null) {
                ArenaData destinationArena = this.plugin.findArenaFootprint(to);
                if (destinationArena != null) {
                    if (this.plugin.findArenaFootprint(event.getFrom()) != destinationArena) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArenaChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (this.isArenaBlockedMob(entity) && this.plugin.findArenaFootprint(entity.getLocation()) != null) {
                entity.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        Entity attacker = event.getEntity();
        if (this.isProtectedEnemy(attacker)) {
            if (this.plugin.findArenaFootprint(attacker.getLocation()) != null) {
                attacker.remove();
                event.setTarget(null);
                event.setCancelled(false);
            } else {
                LivingEntity target = event.getTarget();
                if (target instanceof Player player && this.isProtectedPlayer(player)
                    || target != null && this.plugin.findArenaFootprint(target.getLocation()) != null) {
                    event.setTarget(null);
                    event.setCancelled(false);
                    if (attacker instanceof Warden warden && target != null) {
                        warden.clearAnger(target);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProtectedPlayerWardenAnger(WardenAngerChangeEvent event) {
        if (event.getTarget() instanceof Player player && this.isProtectedPlayer(player)) {
            event.setNewAnger(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHostileEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player && this.isProtectedPlayer(player)) {
            if (this.isHostileDamageSource(event.getDamager())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHostileDamageToMatchParticipant(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && this.isProtectedPlayer(player)) {
            if (event.getCause() == DamageCause.SONIC_BOOM) {
                event.setCancelled(true);
            } else {
                DamageSource damageSource = event.getDamageSource();
                if (this.isHostileDamageSource(damageSource.getCausingEntity())
                    || this.isHostileDamageSource(damageSource.getDirectEntity())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHostileAttackPotionEffect(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player && this.isProtectedPlayer(player)) {
            Cause cause = event.getCause();
            boolean darkness = PotionEffectType.DARKNESS.equals(event.getModifiedType());
            boolean wardenBlindness = cause == Cause.WARDEN
                && PotionEffectType.BLINDNESS.equals(event.getModifiedType());
            boolean hostileSource = this.isHostileDamageSource(event.getSource());
            if (darkness || wardenBlindness || hostileSource || cause == Cause.ATTACK || cause == Cause.WARDEN) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProtectedPlayerDarknessTick(EntityEffectTickEvent event) {
        if (event.getEntity() instanceof Player player && this.isProtectedPlayer(player)) {
            if (PotionEffectType.DARKNESS.equals(event.getType())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHostilePotionSplash(PotionSplashEvent event) {
        if (this.isHostilePotionSource(event.getPotion().getShooter())) {
            for (LivingEntity affected : event.getAffectedEntities()) {
                if (affected instanceof Player player && this.isProtectedPlayer(player)) {
                    event.setIntensity(affected, 0.0);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHostileAreaEffectCloud(AreaEffectCloudApplyEvent event) {
        if (this.isHostilePotionSource(event.getEntity().getSource())) {
            event.getAffectedEntities()
                .removeIf(entity -> entity instanceof Player player && this.isProtectedPlayer(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHostileCombustParticipant(EntityCombustByEntityEvent event) {
        if (event.getEntity() instanceof Player player && this.isProtectedPlayer(player)) {
            if (this.isHostileDamageSource(event.getCombuster())) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isBlastMineMarker(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains("arenasystem_blast_mine_marker");
    }
}
