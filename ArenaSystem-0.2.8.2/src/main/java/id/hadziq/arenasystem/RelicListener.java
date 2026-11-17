package id.hadziq.arenasystem;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Siklus hidup item Relic (Relic Rush): pickup, despawn-guard, drop manual,
 * dan delivery ke altar markas tim. Juga menangani klik item class-selector
 * di lobby persiapan karena sama-sama lewat PlayerInteractEvent.
 */
final class RelicListener implements Listener {
    private final ArenaSystemPlugin plugin;

    RelicListener(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
    }

@EventHandler(ignoreCancelled = true)
public void onRelicPickup(EntityPickupItemEvent event) {
    // Relic tidak pernah dipungut oleh mekanik vanilla; pickup dilakukan dari jarak.
    ItemStack stack = event.getItem().getItemStack();
    if (stack.getType() != Material.NETHER_STAR || !stack.hasItemMeta()) return;
    ArenaData arena = plugin.findArena(event.getItem().getLocation());
    if (arena == null) return;
    MatchState state = plugin.matches.get(arena.name);
    if (state != null && state.running && state.mode.equals("relic_rush")
            && plugin.isRelic(stack, state)) {
        event.setCancelled(true);
        event.getItem().setPickupDelay(Integer.MAX_VALUE);
    }
}

@EventHandler(ignoreCancelled = true)
public void onPlayerMove(PlayerMoveEvent event) {
    if (event.getTo() == null) return;
    if (event.getFrom().distanceSquared(event.getTo()) < 0.0001D) return;

    Player player = event.getPlayer();
    // A death-camera spectator follows the killer but must not pick up or
    // deliver the Relic while their respawn timer is running.
    if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL) return;
    ArenaData arena = plugin.findArena(event.getTo());
    if (arena == null) return;
    MatchState state = plugin.matches.get(arena.name);
    if (state == null || !state.running || !state.mode.equals("relic_rush")) return;
    String team = state.teams.get(player.getUniqueId());
    if (team == null) return;

    if (state.relicCarrier != null && state.relicCarrier.equals(player.getUniqueId())) {
        Location ownSanctum = plugin.spawnLocation(arena, team);
        if (event.getTo().distanceSquared(ownSanctum) <= 2.5D * 2.5D) {
            deliverRelic(player, arena, state, team);
        }
        return;
    }
    if (state.relicCarrier != null) return;

    // Pickup berbasis radius dari item Relic di altar (atau lokasi jatuhnya item).
    for (org.bukkit.entity.Entity nearby : event.getTo().getWorld().getNearbyEntities(
            event.getTo(), 2.5D, 2.5D, 2.5D)) {
        if (!(nearby instanceof Item item) || !item.isValid()) continue;
        ItemStack stack = item.getItemStack();
        if (!plugin.isRelic(stack, state)) continue;
        if (item.getLocation().distanceSquared(event.getTo()) > 2.5D * 2.5D) continue;
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.YELLOW + "Inventorimu penuh; kosongkan satu slot untuk mengambil Relic.");
            return;
        }
        item.remove();
        player.getInventory().addItem(stack.clone());
        state.relicCarrier = player.getUniqueId();
        state.relicDropped = false;
        plugin.applyRelicCarrierMovement(player, state);
        String teamName = team.toUpperCase(Locale.ROOT);
        plugin.announce(state, ChatColor.GOLD + player.getName() + " dari tim " + teamName
                + " membawa Relic! Antar ke sanctum markas timnya.");
        plugin.updateMatchHud(state);
        return;
    }
}

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onRelicCarrierSprint(PlayerToggleSprintEvent event) {
    if (!event.isSprinting()) return;
    for (MatchState state : plugin.matches.values()) {
        if (state.running && state.mode.equals("relic_rush")
                && event.getPlayer().getUniqueId().equals(state.relicCarrier)) {
            event.setCancelled(true);
            event.getPlayer().setSprinting(false);
            return;
        }
    }
}

private void deliverRelic(Player player, ArenaData arena, MatchState state, String team) {
    boolean hasRelic = false;
    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (plugin.isRelic(stack, state)) {
            player.getInventory().setItem(slot, null);
            hasRelic = true;
        }
    }
    if (plugin.isRelic(player.getInventory().getItemInOffHand(), state)) {
        player.getInventory().setItemInOffHand(null);
        hasRelic = true;
    }
    if (!hasRelic) return;

    plugin.restoreRelicCarrierMovement(player, state);
    state.relicCarrier = null;
    state.relicDropped = false;
    player.sendMessage(ChatColor.GOLD + "Relic berhasil diamankan di sanctum markas "
            + team.toUpperCase(Locale.ROOT) + "!");
    plugin.announce(state, ChatColor.YELLOW + "Tim " + team.toUpperCase(Locale.ROOT)
            + " berhasil mengamankan Relic. Satu capture bertambah!");
    plugin.updateMatchHud(state);
    plugin.addScore(state, arena, team, 1);
    if (state.running) plugin.spawnRelic(arena, state);
}

@EventHandler(ignoreCancelled = true)
public void onRelicDespawn(ItemDespawnEvent event) {
    ItemStack stack = event.getEntity().getItemStack();
    if (stack.getType() != Material.NETHER_STAR || !stack.hasItemMeta()) return;
    ArenaData arena = plugin.findArena(event.getEntity().getLocation());
    if (arena == null) return;
    MatchState state = plugin.matches.get(arena.name);
    if (state != null && state.running && state.mode.equals("relic_rush") && plugin.isRelic(stack, state)) {
        event.setCancelled(true);
    }
}

@EventHandler(ignoreCancelled = true)
public void onRelicDrop(PlayerDropItemEvent event) {
    ItemStack dropped = event.getItemDrop().getItemStack();
    if (dropped.getType() != Material.NETHER_STAR || !dropped.hasItemMeta()) return;
    ArenaData arena = plugin.findArena(event.getPlayer().getLocation());
    if (arena == null) return;
    MatchState state = plugin.matches.get(arena.name);
    if (state == null || !state.running || !state.mode.equals("relic_rush") || !plugin.isRelic(dropped, state)) return;
    plugin.restoreRelicCarrierMovement(event.getPlayer(), state);
    state.relicCarrier = null;
    state.relicDropped = true;
    event.getItemDrop().setPickupDelay(Integer.MAX_VALUE);
    plugin.announce(state, ChatColor.GOLD + "Relic dijatuhkan oleh " + event.getPlayer().getName() + ". Rebut kembali!");
    plugin.updateMatchHud(state);
}

@EventHandler(ignoreCancelled = true)
public void onPreparationClassSelector(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getHand() == null) return;
    Player player = event.getPlayer();
    MatchState preparingState = plugin.findPreparingMatch(player.getUniqueId());
    if (preparingState != null && plugin.isClassSelector(event.getItem())) {
        event.setCancelled(true);
        ArenaData preparingArena = plugin.arenas.get(preparingState.arenaName);
        if (preparingArena != null) plugin.openClassMenu(player, preparingArena, preparingState);
    }
}
}
