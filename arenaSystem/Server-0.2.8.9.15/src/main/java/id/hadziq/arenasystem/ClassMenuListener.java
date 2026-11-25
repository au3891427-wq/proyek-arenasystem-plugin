package id.hadziq.arenasystem;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

// $VF: Compiled from ClassMenuListener.java
final class ClassMenuListener implements Listener {
    private final ArenaSystemPlugin plugin;

    ClassMenuListener(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClassMenuClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Inventory top = event.getView().getTopInventory();
            if (top.getHolder() instanceof ClassMenuHolder holder) {
                event.setCancelled(true);
                if (event.getClickedInventory() == top) {
                    int slot = event.getRawSlot();
                    if (slot == 18) {
                        if (holder.page > 0) {
                            this.plugin.openClassMenuPage(player, holder.arenaName, holder.page - 1);
                        }
                    } else if (slot == 26) {
                        if (holder.page < 1) {
                            this.plugin.openClassMenuPage(player, holder.arenaName, holder.page + 1);
                        }
                    } else {
                        String selected = holder.classBySlot.get(slot);
                        if (selected != null) {
                            this.plugin.selectClassByName(player, selected);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onClassMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ClassMenuHolder) {
            event.setCancelled(true);
        }
    }
}
