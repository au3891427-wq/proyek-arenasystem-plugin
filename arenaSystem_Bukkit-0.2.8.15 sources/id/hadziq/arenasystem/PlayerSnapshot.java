package id.hadziq.arenasystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

// $VF: Compiled from PlayerSnapshot.java
final class PlayerSnapshot {
    final ItemStack[] contents;
    final ItemStack[] armor;
    final ItemStack offhand;
    final int heldSlot;
    final Location location;
    final String locationWorldName;
    final GameMode gameMode;
    final int level;
    final float exp;
    final int totalExperience;
    final int foodLevel;
    final float saturation;
    final double health;
    final double maxHealthBaseValue;
    final double attackSpeedBaseValue;
    final List<PotionEffect> potionEffects;

    PlayerSnapshot(Player player) {
        this.contents = cloneItems(player.getInventory().getContents());
        this.armor = cloneItems(player.getInventory().getArmorContents());
        ItemStack hand = player.getInventory().getItemInOffHand();
        this.offhand = hand == null ? null : hand.clone();
        this.heldSlot = player.getInventory().getHeldItemSlot();
        this.location = player.getLocation().clone();
        this.locationWorldName = player.getWorld().getName();
        this.gameMode = player.getGameMode();
        this.level = player.getLevel();
        this.exp = player.getExp();
        this.totalExperience = player.getTotalExperience();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.health = player.getHealth();
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        this.maxHealthBaseValue = maxHealthAttribute == null ? 20.0 : maxHealthAttribute.getBaseValue();
        AttributeInstance attackSpeedAttribute = player.getAttribute(Attribute.ATTACK_SPEED);
        this.attackSpeedBaseValue = attackSpeedAttribute == null ? 4.0 : attackSpeedAttribute.getBaseValue();
        this.potionEffects = new ArrayList<>(player.getActivePotionEffects());
    }

    private PlayerSnapshot(
        ItemStack[] contents,
        ItemStack[] armor,
        ItemStack offhand,
        int heldSlot,
        Location location,
        String locationWorldName,
        GameMode gameMode,
        int level,
        float exp,
        int totalExperience,
        int foodLevel,
        float saturation,
        double health,
        double maxHealthBaseValue,
        double attackSpeedBaseValue,
        List<PotionEffect> potionEffects
    ) {
        this.contents = cloneItems(contents);
        this.armor = cloneItems(armor);
        this.offhand = offhand == null ? null : offhand.clone();
        this.heldSlot = heldSlot;
        this.location = location;
        this.locationWorldName = locationWorldName;
        this.gameMode = gameMode;
        this.level = level;
        this.exp = exp;
        this.totalExperience = totalExperience;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.health = health;
        this.maxHealthBaseValue = maxHealthBaseValue;
        this.attackSpeedBaseValue = attackSpeedBaseValue;
        this.potionEffects = new ArrayList<>(potionEffects);
    }

    static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(player);
    }

    boolean restore(Player player) {
        World world = this.location.getWorld();
        if (world == null && this.locationWorldName != null && !this.locationWorldName.isBlank()) {
            world = Bukkit.getWorld(this.locationWorldName);
        }

        if (world == null) {
            return false;
        }

        Location destination = this.location.clone();
        destination.setWorld(world);
        if (!player.teleport(destination)) {
            return false;
        }

        player.setGameMode(this.gameMode);
        player.getInventory().setContents(cloneItems(this.contents));
        player.getInventory().setArmorContents(cloneItems(this.armor));
        player.getInventory().setItemInOffHand(this.offhand == null ? null : this.offhand.clone());
        player.getInventory().setHeldItemSlot(Math.max(0, Math.min(this.heldSlot, 8)));
        player.setLevel(this.level);
        player.setExp(this.exp);
        player.setTotalExperience(this.totalExperience);
        player.setFoodLevel(this.foodLevel);
        player.setSaturation(this.saturation);

        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }

        for (PotionEffect effect : this.potionEffects) {
            player.addPotionEffect(effect);
        }

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(this.maxHealthBaseValue);
            player.setHealth(Math.max(1.0, Math.min(this.health, maxHealth.getValue())));
        }

        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) {
            attackSpeed.setBaseValue(this.attackSpeedBaseValue);
        }

        return true;
    }

    void writeTo(ConfigurationSection section) {
        section.set("format-version", 1);
        section.set("inventory-size", this.contents.length);

        for (int i = 0; i < this.contents.length; i++) {
            if (this.contents[i] != null) {
                section.set("inventory." + i, this.contents[i]);
            }
        }

        section.set("armor-size", this.armor.length);

        for (int i = 0; i < this.armor.length; i++) {
            if (this.armor[i] != null) {
                section.set("armor." + i, this.armor[i]);
            }
        }

        if (this.offhand != null) {
            section.set("offhand", this.offhand);
        }

        section.set("held-slot", this.heldSlot);
        section.set("location.world", this.locationWorldName);
        section.set("location.x", this.location.getX());
        section.set("location.y", this.location.getY());
        section.set("location.z", this.location.getZ());
        section.set("location.yaw", this.location.getYaw());
        section.set("location.pitch", this.location.getPitch());
        section.set("game-mode", this.gameMode.name());
        section.set("level", this.level);
        section.set("exp", this.exp);
        section.set("total-experience", this.totalExperience);
        section.set("food-level", this.foodLevel);
        section.set("saturation", this.saturation);
        section.set("health", this.health);
        section.set("max-health-base", this.maxHealthBaseValue);
        section.set("attack-speed-base", this.attackSpeedBaseValue);
        section.set("potion-effects", new ArrayList<>(this.potionEffects));
    }

    static PlayerSnapshot readFrom(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("Snapshot section tidak ditemukan.");
        }

        int formatVersion = section.getInt("format-version", 1);
        if (formatVersion != 1) {
            throw new IllegalArgumentException("Versi snapshot tidak didukung: " + formatVersion);
        }

        int inventorySize = boundedSize(section.getInt("inventory-size", 41), 100);
        ItemStack[] contents = new ItemStack[inventorySize];

        for (int i = 0; i < inventorySize; i++) {
            contents[i] = section.getItemStack("inventory." + i);
        }

        int armorSize = boundedSize(section.getInt("armor-size", 4), 16);
        ItemStack[] armor = new ItemStack[armorSize];

        for (int i = 0; i < armorSize; i++) {
            armor[i] = section.getItemStack("armor." + i);
        }

        String worldName = section.getString("location.world", "");
        World world = worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        Location location = new Location(
            world,
            section.getDouble("location.x"),
            section.getDouble("location.y"),
            section.getDouble("location.z"),
            (float)section.getDouble("location.yaw"),
            (float)section.getDouble("location.pitch")
        );
        List<PotionEffect> effects = new ArrayList<>();

        for (Object raw : section.getList("potion-effects", List.of())) {
            if (raw instanceof PotionEffect effect) {
                effects.add(effect);
            } else if (raw instanceof Map<?, ?> rawMap) {
                Map<String, Object> serialized = new LinkedHashMap<>();
                rawMap.forEach((key, value) -> {
                    if (key instanceof String stringKey) {
                        serialized.put(stringKey, value);
                    }
                });

                try {
                    if (ConfigurationSerialization.deserializeObject(serialized) instanceof PotionEffect effect) {
                        effects.add(effect);
                    }
                } catch (IllegalArgumentException var18) {
                }
            }
        }

        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(section.getString("game-mode", "SURVIVAL"));
        } catch (IllegalArgumentException ex) {
            gameMode = GameMode.SURVIVAL;
        }

        return new PlayerSnapshot(
            contents,
            armor,
            section.getItemStack("offhand"),
            section.getInt("held-slot", 0),
            location,
            worldName,
            gameMode,
            section.getInt("level"),
            (float)section.getDouble("exp"),
            section.getInt("total-experience"),
            section.getInt("food-level", 20),
            (float)section.getDouble("saturation", 5.0),
            section.getDouble("health", 20.0),
            section.getDouble("max-health-base", 20.0),
            section.getDouble("attack-speed-base", 4.0),
            effects
        );
    }

    private static int boundedSize(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];

        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }

        return copy;
    }
}
