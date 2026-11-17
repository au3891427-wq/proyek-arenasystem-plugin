package id.hadziq.arenasystem;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * Salinan lengkap kondisi pemain (inventory, lokasi, XP, HP, efek) sebelum
 * join arena, dipakai untuk mengembalikan pemain persis seperti semula saat
 * leave/quit/akhir match. Dipindahkan apa adanya dari ArenaSystemPlugin.
 */
final class PlayerSnapshot {
    final ItemStack[] contents;
    final ItemStack[] armor;
    final ItemStack offhand;
    final int heldSlot;
    final Location location;
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
        this.gameMode = player.getGameMode();
        this.level = player.getLevel();
        this.exp = player.getExp();
        this.totalExperience = player.getTotalExperience();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.health = player.getHealth();
        var maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        this.maxHealthBaseValue = maxHealthAttribute == null ? 20.0D : maxHealthAttribute.getBaseValue();
        var attackSpeedAttribute = player.getAttribute(Attribute.ATTACK_SPEED);
        this.attackSpeedBaseValue = attackSpeedAttribute == null ? 4.0D : attackSpeedAttribute.getBaseValue();
        this.potionEffects = new ArrayList<>(player.getActivePotionEffects());
    }

    static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(player);
    }

    void restore(Player player) {
        if (location.getWorld() != null) player.teleport(location);
        player.setGameMode(gameMode);
        player.getInventory().setContents(cloneItems(contents));
        player.getInventory().setArmorContents(cloneItems(armor));
        player.getInventory().setItemInOffHand(offhand == null ? null : offhand.clone());
        player.getInventory().setHeldItemSlot(heldSlot);
        player.setLevel(level);
        player.setExp(exp);
        player.setTotalExperience(totalExperience);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : potionEffects) player.addPotionEffect(effect);
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealthBaseValue);
            player.setHealth(Math.max(1.0D, Math.min(health, maxHealth.getValue())));
        }
        var attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) attackSpeed.setBaseValue(attackSpeedBaseValue);
    }
    private static ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
        return copy;
    }
}
