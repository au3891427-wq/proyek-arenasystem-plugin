package id.hadziq.arenasystem;

import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;

// $VF: Compiled from RelicListener.java
final class RelicListener implements Listener {
    private final ArenaSystemPlugin plugin;

    RelicListener(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRelicPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (stack.getType() == Material.NETHER_STAR && stack.hasItemMeta()) {
            ArenaData arena = this.plugin.findArena(event.getItem().getLocation());
            if (arena != null) {
                MatchState state = this.plugin.matches.get(arena.name);
                if (state != null
                    && state.running
                    && state.mode.equals("relic_rush")
                    && this.plugin.isRelic(stack, state)) {
                    event.setCancelled(true);
                    event.getItem().setPickupDelay(Integer.MAX_VALUE);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() != null && !(event.getFrom().distanceSquared(event.getTo()) < 1.0E-4)) {
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.SURVIVAL) {
                ArenaData arena = this.plugin.findArena(event.getTo());
                if (arena != null) {
                    MatchState state = this.plugin.matches.get(arena.name);
                    if (state != null && state.running && state.mode.equals("relic_rush")) {
                        String team = state.teams.get(player.getUniqueId());
                        if (team != null) {
                            if (state.relicCarrier != null && state.relicCarrier.equals(player.getUniqueId())) {
                                Location ownSanctum = this.plugin.spawnLocation(arena, team);
                                if (event.getTo().distanceSquared(ownSanctum) <= 6.25) {
                                    this.deliverRelic(player, arena, state, team);
                                }
                            } else if (state.relicCarrier == null) {
                                for (Entity nearby : event.getTo()
                                    .getWorld()
                                    .getNearbyEntities(event.getTo(), 2.5, 2.5, 2.5)) {
                                    if (nearby instanceof Item item && item.isValid()) {
                                        ItemStack stack = item.getItemStack();
                                        if (this.plugin.isRelic(stack, state)
                                            && !(item.getLocation().distanceSquared(event.getTo()) > 6.25)) {
                                            if (player.getInventory().firstEmpty() == -1) {
                                                player.sendMessage(
                                                    ChatColor.YELLOW
                                                        + "Inventorimu penuh; kosongkan satu slot untuk mengambil Relic."
                                                );
                                                return;
                                            }

                                            item.remove();
                                            player.getInventory().addItem(new ItemStack[]{stack.clone()});
                                            state.relicCarrier = player.getUniqueId();
                                            state.relicDropped = false;
                                            this.plugin.applyRelicCarrierMovement(player, state);
                                            String teamName = team.toUpperCase(Locale.ROOT);
                                            this.plugin
                                                .announce(
                                                    state,
                                                    ChatColor.GOLD
                                                        + player.getName()
                                                        + " dari tim "
                                                        + teamName
                                                        + " membawa Relic! Antar ke sanctum markas timnya."
                                                );
                                            this.plugin.updateMatchHud(state);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRelicCarrierSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting()) {
            for (MatchState state : this.plugin.matches.values()) {
                if (state.running
                    && state.mode.equals("relic_rush")
                    && event.getPlayer().getUniqueId().equals(state.relicCarrier)) {
                    event.setCancelled(true);
                    event.getPlayer().setSprinting(false);
                    return;
                }
            }
        }
    }

    private void deliverRelic(Player player, ArenaData arena, MatchState state, String team) {
        boolean hasRelic = false;

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (this.plugin.isRelic(stack, state)) {
                player.getInventory().setItem(slot, null);
                hasRelic = true;
            }
        }

        if (this.plugin.isRelic(player.getInventory().getItemInOffHand(), state)) {
            player.getInventory().setItemInOffHand(null);
            hasRelic = true;
        }

        if (hasRelic) {
            this.plugin.restoreRelicCarrierMovement(player, state);
            state.relicCarrier = null;
            state.relicDropped = false;
            player.sendMessage(
                ChatColor.GOLD + "Relic berhasil diamankan di sanctum markas " + team.toUpperCase(Locale.ROOT) + "!"
            );
            this.plugin
                .announce(
                    state,
                    ChatColor.YELLOW
                        + "Tim "
                        + team.toUpperCase(Locale.ROOT)
                        + " berhasil mengamankan Relic. Satu capture bertambah!"
                );
            this.plugin.updateMatchHud(state);
            this.plugin.addScore(state, arena, team, 1);
            if (state.running) {
                this.plugin.spawnRelic(arena, state);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRelicDespawn(ItemDespawnEvent event) {
        ItemStack stack = event.getEntity().getItemStack();
        if (stack.getType() == Material.NETHER_STAR && stack.hasItemMeta()) {
            ArenaData arena = this.plugin.findArena(event.getEntity().getLocation());
            if (arena != null) {
                MatchState state = this.plugin.matches.get(arena.name);
                if (state != null
                    && state.running
                    && state.mode.equals("relic_rush")
                    && this.plugin.isRelic(stack, state)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRelicDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (dropped.getType() == Material.NETHER_STAR && dropped.hasItemMeta()) {
            ArenaData arena = this.plugin.findArena(event.getPlayer().getLocation());
            if (arena != null) {
                MatchState state = this.plugin.matches.get(arena.name);
                if (state != null
                    && state.running
                    && state.mode.equals("relic_rush")
                    && this.plugin.isRelic(dropped, state)) {
                    this.plugin.restoreRelicCarrierMovement(event.getPlayer(), state);
                    state.relicCarrier = null;
                    state.relicDropped = true;
                    event.getItemDrop().setPickupDelay(Integer.MAX_VALUE);
                    this.plugin
                        .announce(
                            state,
                            ChatColor.GOLD
                                + "Relic dijatuhkan oleh "
                                + event.getPlayer().getName()
                                + ". Rebut kembali!"
                        );
                    this.plugin.updateMatchHud(state);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreparationClassSelector(PlayerInteractEvent event) {
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
            && event.getHand() != null) {
            Player player = event.getPlayer();
            MatchState preparingState = this.plugin.findPreparingMatch(player.getUniqueId());
            if (preparingState != null && this.plugin.isClassSelector(event.getItem())) {
                event.setCancelled(true);
                ArenaData preparingArena = this.plugin.arenas.get(preparingState.arenaName);
                if (preparingArena != null) {
                    this.plugin.openClassMenu(player, preparingArena, preparingState);
                }
            }
        }
    }
}
