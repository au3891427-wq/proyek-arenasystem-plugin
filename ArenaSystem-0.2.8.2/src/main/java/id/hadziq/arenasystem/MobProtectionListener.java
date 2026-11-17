package id.hadziq.arenasystem;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;

import io.papermc.paper.event.entity.EntityMoveEvent;

/**
 * Menjaga footprint arena tetap steril dari mob biasa: menolak spawn, pergerakan,
 * teleport masuk, mob yang ikut termuat lewat chunk, dan targeting ke/dari arena.
 * Satu-satunya pengecualian adalah Creeper dekoratif penanda Blast Mine.
 */
final class MobProtectionListener implements Listener {
    private final ArenaSystemPlugin plugin;

    MobProtectionListener(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Penanda Blast Mine adalah satu-satunya mob dekoratif yang diizinkan.
        // Consumer pada World.spawn memberi scoreboard tag sebelum event spawn diproses.
        if (isBlastMineMarker(event.getEntity())) {
            event.setCancelled(false);
            return;
        }

        // Semua alasan spawn mob biasa ditolak di footprint arena.
        if (plugin.findArenaFootprint(event.getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    /** Menahan mob biasa yang berjalan dari luar menuju footprint arena. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobEnterArena(EntityMoveEvent event) {
        org.bukkit.entity.LivingEntity mob = event.getEntity();
        if (mob instanceof Player || mob instanceof org.bukkit.entity.ArmorStand
                || isBlastMineMarker(mob)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        ArenaData destinationArena = plugin.findArenaFootprint(to);
        if (destinationArena == null) return;

        // Mob boleh tetap bergerak di dalam arena yang sama, tetapi tidak boleh masuk dari luar.
        if (plugin.findArenaFootprint(from) != destinationArena) {
            event.setCancelled(true);
        }
    }

    /** Mencegah mob biasa masuk melalui teleport, portal, atau command. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobTeleportIntoArena(EntityTeleportEvent event) {
        org.bukkit.entity.Entity entity = event.getEntity();
        if (isBlastMineMarker(entity)) return;
        if (!(entity instanceof org.bukkit.entity.LivingEntity)
                || entity instanceof Player
                || entity instanceof org.bukkit.entity.ArmorStand) return;

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
        // Bersihkan mob biasa yang tersimpan di chunk; marker Blast Mine dipertahankan.
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof org.bukkit.entity.LivingEntity)) continue;
            if (entity instanceof Player || entity instanceof org.bukkit.entity.ArmorStand
                    || isBlastMineMarker(entity)) continue;
            if (plugin.findArenaFootprint(entity.getLocation()) != null) entity.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Player || isBlastMineMarker(event.getEntity())) return;

        // Bersihkan mob biasa yang ternyata sudah berada di footprint arena.
        if (plugin.findArenaFootprint(event.getEntity().getLocation()) != null) {
            event.getEntity().remove();
            event.setCancelled(true);
            return;
        }

        // Mob di luar arena tidak boleh mengunci target pemain yang berada di dalamnya.
        org.bukkit.entity.LivingEntity target = event.getTarget();
        if (target != null && plugin.findArenaFootprint(target.getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    private boolean isBlastMineMarker(org.bukkit.entity.Entity entity) {
        return entity != null
                && entity.getScoreboardTags().contains(ClassAbilityListener.BLAST_MINE_MARKER_TAG);
    }
}
