package id.hadziq.arenasystem;

import org.bukkit.Location;
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
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.event.entity.EntityEffectTickEvent;
import io.papermc.paper.event.entity.WardenAngerChangeEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PotionSplashEvent;

import io.papermc.paper.event.entity.EntityMoveEvent;

/**
 * Menjaga area arena steril dari entity hostile.
 *
 * Passive mob, hewan tame, marker Blast Mine, dan NPC tidak dihapus/diblokir.
 */
final class MobProtectionListener implements Listener {
    private final ArenaSystemPlugin plugin;

    MobProtectionListener(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isProtectedEnemy(Entity entity) {
        if (!(entity instanceof Enemy)) return false;
        if (entity instanceof Player || entity instanceof NPC || isBlastMineMarker(entity)) return false;
        if (entity.hasMetadata("NPC")) return false; // kompatibilitas NPC plugin lama seperti Citizens
        if (entity instanceof Tameable tameable && tameable.isTamed()) return false;
        return true;
    }

    /**
     * Entitas yang tidak boleh spawn atau masuk ke arena. Selain hostile mob,
     * Wandering Trader dan Trader Llama juga dikecualikan agar event trader
     * alami tidak meninggalkan NPC/llama di medan pertandingan.
     */
    private boolean isArenaBlockedMob(Entity entity) {
        if (entity == null || entity instanceof Player || entity instanceof NPC
                || entity.hasMetadata("NPC") || isBlastMineMarker(entity)) {
            return false;
        }
        EntityType type = entity.getType();
        return isProtectedEnemy(entity)
                || type == EntityType.WANDERING_TRADER
                || type == EntityType.TRADER_LLAMA;
    }

    /**
     * Keanggotaan match tetap menjadi acuan ketika knockback atau perpindahan paksa
     * menempatkan peserta sedikit di luar footprint. Perlindungan dibatasi ke dunia
     * arena dan lifecycle pertandingan agar antrean lama tidak mendapat imunitas.
     */
    private boolean isMatchParticipant(Player player) {
        if (player == null) return false;

        for (MatchState state : plugin.matches.values()) {
            boolean activeLifecycle = state.running || state.preparing || state.launching || state.ending;
            if (!activeLifecycle || !state.teams.containsKey(player.getUniqueId())) continue;

            ArenaData arena = plugin.arenas.get(state.arenaName);
            if (arena != null && arena.worldName.equals(player.getWorld().getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Horizontal arena bounds are used for player protection. Do not use the
     * footprint's vertical range here: knockback or forced movement can place a
     * participant far above/below the scanned surface while they are still over
     * the arena.
     */
    private boolean isInsideArenaHorizontalBounds(Player player) {
        if (player == null) return false;

        Location location = player.getLocation();
        String worldName = location.getWorld().getName();
        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();

        for (ArenaData arena : plugin.arenas.values()) {
            if (!arena.worldName.equals(worldName)) continue;
            if (Math.abs(blockX - arena.centerX) <= arena.radius
                    && Math.abs(blockZ - arena.centerZ) <= arena.radius) {
                return true;
            }
        }
        for (ArenaData arena : plugin.incompleteArenas.values()) {
            if (!arena.worldName.equals(worldName)) continue;
            if (Math.abs(blockX - arena.centerX) <= arena.radius
                    && Math.abs(blockZ - arena.centerZ) <= arena.radius) {
                return true;
            }
        }
        return false;
    }

    /** Pemain di kolom horizontal arena terlindungi; peserta aktif juga terlindungi saat keluar batas. */
    private boolean isProtectedPlayer(Player player) {
        return player != null
                && (isInsideArenaHorizontalBounds(player) || isMatchParticipant(player));
    }

    /**
     * Periksa entitas penyebab dan entitas yang langsung memberikan damage.
     * DamageSource.getCausingEntity() mencakup proyektil yang ditembakkan mob;
     * pemeriksaan entitas langsung menjadi fallback untuk projectile/fang/cloud.
     */
    private boolean isHostileDamageSource(Entity source) {
        if (source == null) return false;
        if (isProtectedEnemy(source)) return true;

        if (source instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter
                && isProtectedEnemy(shooter)) {
            return true;
        }
        if (source instanceof EvokerFangs fangs && isProtectedEnemy(fangs.getOwner())) {
            return true;
        }
        return source instanceof AreaEffectCloud cloud
                && cloud.getSource() instanceof Entity shooter
                && isProtectedEnemy(shooter);
    }

    private boolean isHostilePotionSource(org.bukkit.projectiles.ProjectileSource source) {
        return source instanceof Entity entity && isProtectedEnemy(entity);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        // Marker Blast Mine harus tetap dapat dibuat di dalam arena, sama seperti
        // perilaku listener sebelum filter hostile diperketat.
        if (isBlastMineMarker(entity)) {
            event.setCancelled(false);
            return;
        }
        if (!isArenaBlockedMob(entity)) return;

        if (plugin.findArenaFootprint(event.getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    /** Mencegah hostile mob berjalan dari luar menuju arena. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobEnterArena(EntityMoveEvent event) {
        Entity entity = event.getEntity();
        if (!isArenaBlockedMob(entity)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        ArenaData destinationArena = plugin.findArenaFootprint(to);
        if (destinationArena == null) return;

        if (plugin.findArenaFootprint(from) != destinationArena) {
            event.setCancelled(true);
        }
    }

    /** Mencegah hostile mob masuk lewat teleport/portal/command. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobTeleportIntoArena(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (!isArenaBlockedMob(entity)) return;

        Location to = event.getTo();
        if (to == null) return;

        ArenaData destinationArena = plugin.findArenaFootprint(to);
        if (destinationArena == null) return;

        if (plugin.findArenaFootprint(event.getFrom()) != destinationArena) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArenaChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (!isArenaBlockedMob(entity)) continue;
            if (plugin.findArenaFootprint(entity.getLocation()) != null) {
                entity.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        Entity attacker = event.getEntity();
        if (!isProtectedEnemy(attacker)) return;

        if (plugin.findArenaFootprint(attacker.getLocation()) != null) {
            attacker.remove();
            event.setTarget(null);
            event.setCancelled(false);
            return;
        }

        // Jangan cancel event: Paper mendokumentasikan bahwa cancellation mempertahankan
        // target lama. setTarget(null) benar-benar mengosongkan target mob.
        LivingEntity target = event.getTarget();
        if ((target instanceof Player player && isProtectedPlayer(player))
                || (target != null && plugin.findArenaFootprint(target.getLocation()) != null)) {
            event.setTarget(null);
            event.setCancelled(false);

            // Warden juga menyimpan anger per-target, terpisah dari Mob#getTarget().
            if (attacker instanceof Warden warden && target != null) {
                warden.clearAnger(target);
            }
        }
    }

    /**
     * Warden dapat memperoleh anger dari getaran (termasuk langkah kaki), tanpa
     * bergantung sepenuhnya pada target biasa. Jangan biarkan anger terhadap pemain
     * yang dilindungi bertambah sehingga Warden tidak masuk ke perilaku pursuit/sonic boom.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProtectedPlayerWardenAnger(WardenAngerChangeEvent event) {
        if (!(event.getTarget() instanceof Player player) || !isProtectedPlayer(player)) return;
        event.setNewAnger(0);
    }

    /**
     * Primary entity-damage path. Read the direct damager so melee hits and
     * projectiles are not dependent solely on DamageSource attribution.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHostileEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isProtectedPlayer(player)) return;
        if (isHostileDamageSource(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    /**
     * Lapisan terakhir untuk damage hostile. Ini juga menangkap Sonic Boom Warden
     * yang tidak harus melewati proses pemilihan target biasa.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHostileDamageToMatchParticipant(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isProtectedPlayer(player)) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.SONIC_BOOM) {
            event.setCancelled(true);
            return;
        }

        var damageSource = event.getDamageSource();
        if (isHostileDamageSource(damageSource.getCausingEntity())
                || isHostileDamageSource(damageSource.getDirectEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Warden memberi efek DARKNESS ketika mendeteksi pemain (misalnya melalui
     * getaran langkah kaki). Batalkan berdasarkan tipe efek, bukan hanya cause,
     * karena efek darkness juga dapat berasal dari sculk shrieker.
     * Efek ATTACK mencakup debuff langsung seperti poison cave spider/shulker.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHostileAttackPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isProtectedPlayer(player)) return;

        EntityPotionEffectEvent.Cause cause = event.getCause();
        boolean darkness = PotionEffectType.DARKNESS.equals(event.getModifiedType());
        boolean wardenBlindness = cause == EntityPotionEffectEvent.Cause.WARDEN
                && PotionEffectType.BLINDNESS.equals(event.getModifiedType());

        // Gunakan atribusi sumber juga: efek dari tipped arrow, splash potion,
        // atau lingering cloud tidak selalu memakai Cause.ATTACK/WARDEN.
        boolean hostileSource = isHostileDamageSource(event.getSource());

        if (darkness || wardenBlindness || hostileSource
                || cause == EntityPotionEffectEvent.Cause.ATTACK
                || cause == EntityPotionEffectEvent.Cause.WARDEN) {
            event.setCancelled(true);
        }
    }

    /**
     * Fallback for effect-tick paths. Only suppress Darkness on protected arena
     * players; do not affect any other potion effect or player ability.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProtectedPlayerDarknessTick(EntityEffectTickEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isProtectedPlayer(player)) return;
        if (PotionEffectType.DARKNESS.equals(event.getType())) {
            event.setCancelled(true);
        }
    }

    /**
     * Cegah splash potion dari mob hostile memberikan efek kepada pemain yang dilindungi,
     * tanpa mengganggu efek potion terhadap entitas lain di sekitarnya.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHostilePotionSplash(PotionSplashEvent event) {
        if (!isHostilePotionSource(event.getPotion().getShooter())) return;

        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player player && isProtectedPlayer(player)) {
                event.setIntensity(affected, 0.0D);
            }
        }
    }

    /**
     * Daftar target lingering cloud dapat diubah. Hapus hanya pemain yang dilindungi
     * dari daftar penerima efek; entitas lain di sekitarnya tetap diproses.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHostileAreaEffectCloud(AreaEffectCloudApplyEvent event) {
        if (!isHostilePotionSource(event.getEntity().getSource())) return;

        event.getAffectedEntities().removeIf(entity ->
                entity instanceof Player player && isProtectedPlayer(player));
    }

    /** Cegah sumber api dari mob hostile membakar peserta pertandingan. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHostileCombustParticipant(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isProtectedPlayer(player)) return;
        if (isHostileDamageSource(event.getCombuster())) {
            event.setCancelled(true);
        }
    }

    private boolean isBlastMineMarker(Entity entity) {
        return entity != null
                && entity.getScoreboardTags().contains(ClassAbilityListener.BLAST_MINE_MARKER_TAG);
    }
}
