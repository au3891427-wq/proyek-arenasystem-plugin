package id.hadziq.arenasystem;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent.Cause;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

// $VF: Compiled from BlockProtectionListener.java
final class BlockProtectionListener implements Listener {
    private final ArenaSystemPlugin plugin;

    BlockProtectionListener(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ArenaData arena = this.plugin.findArenaFootprint(event.getBlock().getLocation());
        if (arena != null) {
            if (this.plugin.isIncompleteArena(arena)) {
                event.setCancelled(true);
                event.getPlayer()
                    .sendMessage(ChatColor.RED + "Arena sedang dibangun atau dipulihkan; blok tidak dapat diubah.");
            } else {
                boolean vegetation = ArenaGenerator.isCombatVegetation(event.getBlock().getType());
                boolean vegetationDefaultAllowed = vegetation
                    && this.plugin.getConfig().getBoolean("protection.allow-vegetation-break-by-default", true)
                    && !this.plugin.hasExplicitProtectionRule("break", arena, event.getPlayer());
                boolean breakAllowed = this.plugin.isActionAllowed("break", arena, event.getPlayer())
                    || vegetationDefaultAllowed;
                if (this.plugin.isPreparing(arena) || event.getBlock().getState() instanceof TileState || !breakAllowed
                    )
                 {
                    event.setCancelled(true);
                    if (!this.plugin.isMatchRunning(arena)) {
                        event.getPlayer()
                            .sendMessage(
                                ChatColor.RED + "Kamu tidak diizinkan menghancurkan blok di arena " + arena.name + "."
                            );
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberBrokenBlock(BlockBreakEvent event) {
        ArenaData arena = this.plugin.findArena(event.getBlock().getLocation());
        if (arena != null) {
            this.plugin.recordMatchBlock(arena, event.getBlock(), event.getBlock().getBlockData().getAsString());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ArenaData arena = this.plugin.findArenaFootprint(event.getBlock().getLocation());
        if (arena != null) {
            if (this.plugin.isIncompleteArena(arena)) {
                event.setCancelled(true);
                event.getPlayer()
                    .sendMessage(ChatColor.RED + "Arena sedang dibangun atau dipulihkan; blok tidak dapat diubah.");
            } else if (this.plugin.isPreparing(arena)
                || event.getBlockReplacedState() instanceof TileState
                || !this.plugin.isActionAllowed("place", arena, event.getPlayer())) {
                event.setCancelled(true);
                if (!this.plugin.isMatchRunning(arena)) {
                    event.getPlayer()
                        .sendMessage(ChatColor.RED + "Kamu tidak diizinkan memasang blok di arena " + arena.name + ".");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberPlacedBlock(BlockPlaceEvent event) {
        ArenaData arena = this.plugin.findArena(event.getBlock().getLocation());
        if (arena != null) {
            this.plugin
                .recordMatchBlock(arena, event.getBlock(), event.getBlockReplacedState().getBlockData().getAsString());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (this.plugin.findArenaFootprint(event.getLocation()) != null) {
            event.setCancelled(true);
        } else {
            event.blockList().removeIf(block -> this.plugin.findArenaFootprint(block.getLocation()) != null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (this.plugin.findArenaFootprint(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        } else {
            event.blockList().removeIf(block -> this.plugin.findArenaFootprint(block.getLocation()) != null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaExplosionDamage(EntityDamageEvent event) {
        DamageCause cause = event.getCause();
        if ((cause == DamageCause.BLOCK_EXPLOSION || cause == DamageCause.ENTITY_EXPLOSION)
            && this.plugin.findArenaFootprint(event.getEntity().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaExplosionKnockback(EntityKnockbackEvent event) {
        if (event.getCause() == Cause.EXPLOSION
            && this.plugin.findArenaFootprint(event.getEntity().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaExplosionIgnite(BlockIgniteEvent event) {
        IgniteCause cause = event.getCause();
        if ((cause == IgniteCause.EXPLOSION || cause == IgniteCause.SPREAD)
            && this.plugin.findArenaFootprint(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockBurn(BlockBurnEvent event) {
        if (this.plugin.findArenaFootprint(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaHangingExplosion(HangingBreakEvent event) {
        if (event.getCause() == RemoveCause.EXPLOSION
            && this.plugin.findArenaFootprint(event.getEntity().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    private boolean touchesIncomplete(Block block) {
        return block != null && this.plugin.touchesIncompleteArena(block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIncompleteBucketEmpty(PlayerBucketEmptyEvent event) {
        if (this.touchesIncomplete(event.getBlock()) || this.touchesIncomplete(event.getBlockClicked())) {
            event.setCancelled(true);
            event.getPlayer()
                .sendMessage(ChatColor.RED + "Arena sedang dibangun atau dipulihkan; blok tidak dapat diubah.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIncompleteBucketFill(PlayerBucketFillEvent event) {
        if (this.touchesIncomplete(event.getBlock()) || this.touchesIncomplete(event.getBlockClicked())) {
            event.setCancelled(true);
            event.getPlayer()
                .sendMessage(ChatColor.RED + "Arena sedang dibangun atau dipulihkan; blok tidak dapat diubah.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIncompleteBlockFromTo(BlockFromToEvent event) {
        if (this.touchesIncomplete(event.getToBlock()) || this.touchesIncomplete(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIncompletePistonExtend(BlockPistonExtendEvent event) {
        if (this.pistonTouchesIncomplete(event.getBlock(), event.getDirection(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIncompletePistonRetract(BlockPistonRetractEvent event) {
        if (this.pistonTouchesIncomplete(event.getBlock(), event.getDirection(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonTouchesIncomplete(Block piston, BlockFace direction, List<Block> moved) {
        int dx = direction.getModX();
        int dy = direction.getModY();
        int dz = direction.getModZ();
        if (this.plugin.touchesIncompleteArenaMovement(piston.getLocation(), dx, dy, dz)) {
            return true;
        }

        for (Block block : moved) {
            if (this.plugin.touchesIncompleteArenaMovement(block.getLocation(), dx, dy, dz)) {
                return true;
            }
        }

        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIncompleteEntityChangeBlock(EntityChangeBlockEvent event) {
        if (this.touchesIncomplete(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
