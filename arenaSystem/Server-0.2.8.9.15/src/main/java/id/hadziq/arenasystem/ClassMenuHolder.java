package id.hadziq.arenasystem;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

// $VF: Compiled from ClassMenuHolder.java
final class ClassMenuHolder implements InventoryHolder {
    final String arenaName;
    final int page;
    final Map<Integer, String> classBySlot = new HashMap<>();
    Inventory inventory;

    ClassMenuHolder(String arenaName, int page) {
        this.arenaName = arenaName;
        this.page = page;
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}
