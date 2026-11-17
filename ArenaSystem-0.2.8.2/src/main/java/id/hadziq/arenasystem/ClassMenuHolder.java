package id.hadziq.arenasystem;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** Penanda inventory pemilihan class TDM beserta halaman dan pemetaan slot. */
final class ClassMenuHolder implements InventoryHolder {
    final String arenaName;
    final int page;
    final Map<Integer, String> classBySlot = new HashMap<>();
    Inventory inventory;

    ClassMenuHolder(String arenaName, int page) {
        this.arenaName = arenaName;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
