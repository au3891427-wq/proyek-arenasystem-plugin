package id.hadziq.arenasystem;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;

import io.papermc.paper.event.entity.EntityKnockbackEvent;

/**
 * Proteksi blok dan ledakan di dalam arena: aturan breakable/placeable per
 * arena atau global, pencatatan perubahan blok selama match (untuk restore),
 * dan isolasi arena dari ledakan/api yang berasal dari luar footprint.
 */
final class BlockProtectionListener implements Listener {
    private final ArenaSystemPlugin plugin;

    BlockProtectionListener(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
    }

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onBlockBreak(BlockBreakEvent event) {
    ArenaData arena = plugin.findArena(event.getBlock().getLocation());
    if (arena == null) return;

    boolean vegetation = ArenaGenerator.isCombatVegetation(event.getBlock().getType());
    boolean vegetationDefaultAllowed = vegetation
            && plugin.getConfig().getBoolean("protection.allow-vegetation-break-by-default", true)
            && !plugin.hasExplicitProtectionRule("break", arena, event.getPlayer());
    boolean breakAllowed = plugin.isActionAllowed("break", arena, event.getPlayer()) || vegetationDefaultAllowed;

    if (plugin.isPreparing(arena) || event.getBlock().getState() instanceof TileState || !breakAllowed) {
        event.setCancelled(true);
        // Saat match aktif, proteksi tetap bekerja tetapi tidak membanjiri chat pemain.
        if (!plugin.isMatchRunning(arena)) {
            event.getPlayer().sendMessage(ChatColor.RED + "Kamu tidak diizinkan menghancurkan blok di arena " + arena.name + ".");
        }
    }
}

@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void rememberBrokenBlock(BlockBreakEvent event) {
    ArenaData arena = plugin.findArena(event.getBlock().getLocation());
    if (arena == null) return;
    plugin.recordMatchBlock(arena, event.getBlock(), event.getBlock().getBlockData().getAsString());
}

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onBlockPlace(BlockPlaceEvent event) {
    ArenaData arena = plugin.findArena(event.getBlock().getLocation());
    if (arena == null) return;
    if (plugin.isPreparing(arena) || event.getBlockReplacedState() instanceof TileState
            || !plugin.isActionAllowed("place", arena, event.getPlayer())) {
        event.setCancelled(true);
        // Samakan perilaku place dengan break: senyap selama match aktif.
        if (!plugin.isMatchRunning(arena)) {
            event.getPlayer().sendMessage(ChatColor.RED + "Kamu tidak diizinkan memasang blok di arena " + arena.name + ".");
        }
    }
}

@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void rememberPlacedBlock(BlockPlaceEvent event) {
    ArenaData arena = plugin.findArena(event.getBlock().getLocation());
    if (arena == null) return;
    plugin.recordMatchBlock(arena, event.getBlock(), event.getBlockReplacedState().getBlockData().getAsString());
}

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onEntityExplode(EntityExplodeEvent event) {
    // Ledakan yang berasal dari dalam footprint arena dibatalkan seluruhnya.
    if (plugin.findArenaFootprint(event.getLocation()) != null) {
        event.setCancelled(true);
        return;
    }

    // Ledakan dari luar tetap berjalan di luar, tetapi tidak boleh menghapus
    // satu blok pun yang berada di dalam footprint arena.
    event.blockList().removeIf(block -> plugin.findArenaFootprint(block.getLocation()) != null);
}

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onBlockExplode(BlockExplodeEvent event) {
    if (plugin.findArenaFootprint(event.getBlock().getLocation()) != null) {
        event.setCancelled(true);
        return;
    }

    event.blockList().removeIf(block -> plugin.findArenaFootprint(block.getLocation()) != null);
}

/**
 * Lindungi pemain, mob, item, dan entity lain di dalam arena dari damage ledakan
 * yang pusat ledakannya berada di luar arena.
 */
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onArenaExplosionDamage(EntityDamageEvent event) {
    EntityDamageEvent.DamageCause cause = event.getCause();
    if (cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
            && cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;

    if (plugin.findArenaFootprint(event.getEntity().getLocation()) != null) {
        event.setCancelled(true);
    }
}

/**
 * Batalkan dorongan ledakan agar pemain/entity di arena tidak terpental
 * oleh TNT atau ledakan lain yang berasal dari luar.
 */
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onArenaExplosionKnockback(EntityKnockbackEvent event) {
    if (event.getCause() != EntityKnockbackEvent.Cause.EXPLOSION) return;

    if (plugin.findArenaFootprint(event.getEntity().getLocation()) != null) {
        event.setCancelled(true);
    }
}

/**
 * Ledakan dari luar tidak boleh menyalakan api di arena, termasuk api
 * yang kemudian menyebar dari area luar ke dalam arena.
 */
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onArenaExplosionIgnite(BlockIgniteEvent event) {
    BlockIgniteEvent.IgniteCause cause = event.getCause();
    if (cause != BlockIgniteEvent.IgniteCause.EXPLOSION
            && cause != BlockIgniteEvent.IgniteCause.SPREAD) return;

    if (plugin.findArenaFootprint(event.getBlock().getLocation()) != null) {
        event.setCancelled(true);
    }
}

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onArenaBlockBurn(BlockBurnEvent event) {
    if (plugin.findArenaFootprint(event.getBlock().getLocation()) != null) {
        event.setCancelled(true);
    }
}

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onArenaHangingExplosion(HangingBreakEvent event) {
    if (event.getCause() != HangingBreakEvent.RemoveCause.EXPLOSION) return;

    // Lindungi item frame, painting, dan hanging entity lain dari ledakan luar.
    if (plugin.findArenaFootprint(event.getEntity().getLocation()) != null) {
        event.setCancelled(true);
    }
}
}
