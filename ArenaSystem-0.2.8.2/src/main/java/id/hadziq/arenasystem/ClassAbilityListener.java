package id.hadziq.arenasystem;

import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Active class abilities and match-only durability protection. */
final class ClassAbilityListener implements Listener {
    /** Scoreboard tag used to exempt only the decorative Blast Mine marker from arena mob cleanup. */
    static final String BLAST_MINE_MARKER_TAG = "arenasystem_blast_mine_marker";
    private static final Set<EquipmentSlot> HIDDEN_SLOTS = EnumSet.of(
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET
    );

    private final ArenaSystemPlugin plugin;
    private final Set<UUID> vanishedPlayers = new HashSet<>();
    private final Map<UUID, BukkitTask> vanishTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> vanishExpiryTasks = new HashMap<>();
    private final Map<UUID, MarkInfo> markedTargets = new HashMap<>();
    private final Map<UUID, UUID> sniperTargetsByOwner = new HashMap<>();
    private final Set<UUID> sniperAimLocked = new HashSet<>();
    private final Map<UUID, Long> archerPinningReady = new HashMap<>();
    private final Map<UUID, Long> pinningProjectiles = new HashMap<>();
    private final Map<UUID, Long> assassinVenomReady = new HashMap<>();
    private final Map<UUID, Long> sniperFocusActive = new HashMap<>();
    private final Set<UUID> focusedProjectiles = new HashSet<>();
    private final Map<UUID, Long> ninjaSnowballNextRestock = new HashMap<>();
    private static final long NINJA_SNOWBALL_RESTOCK_MILLIS = 4_500L;
    private static final int NINJA_SNOWBALL_MAX = 7;

    private final Map<UUID, DemomanDynamite> demomanDynamite = new HashMap<>();
    private final Map<UUID, DemomanMine> demomanMines = new HashMap<>();
    private final Map<UUID, BukkitTask> demomanBurstTasks = new HashMap<>();
    private final Map<UUID, Long> boxerHeavyJabLastInteractionNanos = new HashMap<>();
    private final Set<UUID> boxerSkillDamage = new HashSet<>();

    private final BukkitTask ninjaSnowballRestockTask;
    private final BukkitTask demomanExplosivesTask;
    private final BukkitTask sniperAimLockTask;

    private record MarkInfo(UUID ownerId, String arenaName, long expiresAt, int arrowsHit) {}
    private enum DemomanBlastType { DYNAMITE_BURST, PRIMARY_BOMB, BLAST_MINE }
    private record DemomanDynamite(UUID ownerId, String arenaName, Snowball projectile,
                                   long fuseAtMillis, double radius, double maximumDamage,
                                   DemomanBlastType blastType) {}
    private record DemomanMine(UUID ownerId, String arenaName, Location location, Creeper marker, long expiresAtMillis) {}

    ClassAbilityListener(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
        // Keep explicit handles so plugin shutdown can cancel every repeating task.
        this.ninjaSnowballRestockTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::tickNinjaSnowballRestock, 5L, 5L);
        this.demomanExplosivesTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::tickDemomanExplosives, 2L, 2L);
        this.sniperAimLockTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::tickSniperAimLock, 1L, 1L);
    }

    /** Maintains a seven-snowball supply for Ninja players in active TDM matches. */
    private void tickNinjaSnowballRestock() {
        long now = System.currentTimeMillis();
        Set<UUID> activeNinjas = new HashSet<>();

        for (MatchState state : plugin.matches.values()) {
            if (!state.running || !"tdm".equals(state.mode)) continue;

            for (UUID playerId : state.teams.keySet()) {
                if (!"ninja".equalsIgnoreCase(
                        state.selectedClasses.getOrDefault(playerId, "knight"))) continue;

                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline() || player.isDead()
                        || player.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;

                activeNinjas.add(playerId);
                int snowballCount = countNinjaSnowballs(player);

                // A full supply resets the timer continuously. If a snowball is
                // thrown afterward, the player must wait a fresh 4.5 seconds.
                if (snowballCount >= NINJA_SNOWBALL_MAX) {
                    ninjaSnowballNextRestock.put(playerId, now + NINJA_SNOWBALL_RESTOCK_MILLIS);
                    continue;
                }

                long nextRestock = ninjaSnowballNextRestock.computeIfAbsent(
                        playerId, ignored -> now + NINJA_SNOWBALL_RESTOCK_MILLIS);
                if (now < nextRestock) continue;

                if (addOneNinjaSnowball(player)) {
                    ninjaSnowballNextRestock.put(playerId, now + NINJA_SNOWBALL_RESTOCK_MILLIS);
                }
                // If there is no snowball stack and the inventory has no free
                // slot, leave the timer ready and retry as soon as space opens.
            }
        }

        // Also clears stale timers after class changes, match ends, or disconnects.
        ninjaSnowballNextRestock.keySet().removeIf(playerId -> !activeNinjas.contains(playerId));
    }

    private int countNinjaSnowballs(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == Material.SNOWBALL) total += item.getAmount();
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() == Material.SNOWBALL) total += offHand.getAmount();
        return total;
    }

    /** Adds exactly one snowball without exceeding the seven-item supply cap. */
    private boolean addOneNinjaSnowball(Player player) {
        if (countNinjaSnowballs(player) >= NINJA_SNOWBALL_MAX) return false;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() == Material.SNOWBALL
                && offHand.getAmount() < NINJA_SNOWBALL_MAX) {
            offHand.setAmount(offHand.getAmount() + 1);
            player.getInventory().setItemInOffHand(offHand);
            return true;
        }

        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getType() == Material.SNOWBALL && item.getAmount() < NINJA_SNOWBALL_MAX) {
                item.setAmount(item.getAmount() + 1);
                contents[slot] = item;
                player.getInventory().setStorageContents(contents);
                return true;
            }
        }

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() == Material.AIR) {
                contents[slot] = new ItemStack(Material.SNOWBALL, 1);
                player.getInventory().setStorageContents(contents);
                return true;
            }
        }
        return false;
    }

    private void tickDemomanExplosives() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, DemomanDynamite> entry : new ArrayList<>(demomanDynamite.entrySet())) {
            DemomanDynamite explosive = entry.getValue();
            Snowball projectile = explosive.projectile();
            if (!projectile.isValid()) {
                demomanDynamite.remove(entry.getKey());
                continue;
            }
            if (now >= explosive.fuseAtMillis()) {
                demomanDynamite.remove(entry.getKey());
                detonateDemomanProjectile(explosive);
                projectile.remove();
            }
        }

        for (Map.Entry<UUID, DemomanMine> entry : new ArrayList<>(demomanMines.entrySet())) {
            DemomanMine mine = entry.getValue();
            Player owner = Bukkit.getPlayer(mine.ownerId());
            MatchState state = owner == null ? null : activeTdmMatch(owner);
            if (owner == null || !owner.isOnline() || owner.isDead() || state == null
                    || !state.arenaName.equalsIgnoreCase(mine.arenaName())
                    || !"demoman".equalsIgnoreCase(state.selectedClasses.getOrDefault(owner.getUniqueId(), "knight"))) {
                removeDemomanMine(mine.ownerId());
                continue;
            }
            if (now >= mine.expiresAtMillis()) {
                removeDemomanMine(mine.ownerId());
                owner.sendActionBar(ChatColor.GRAY + "Blast Mine kedaluwarsa.");
                continue;
            }

            double triggerRadiusSquared = 2.25D * 2.25D;
            for (Player target : mine.location().getWorld().getPlayers()) {
                if (!isEnemy(owner, target, state) || target.isDead()
                        || target.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;
                if (target.getLocation().distanceSquared(mine.location()) > triggerRadiusSquared) continue;

                removeDemomanMine(mine.ownerId());
                detonateDemomanBlast(owner, state, mine.location(), 3.5D, 4.2D,
                        DemomanBlastType.BLAST_MINE);
                owner.sendActionBar(ChatColor.RED + "BLAST MINE TRIGGERED!");
                break;
            }
        }
    }

    private void detonateDemomanProjectile(DemomanDynamite explosive) {
        Player owner = Bukkit.getPlayer(explosive.ownerId());
        if (owner == null || !owner.isOnline() || owner.isDead()) return;
        MatchState state = activeTdmMatch(owner);
        if (state == null || !state.arenaName.equalsIgnoreCase(explosive.arenaName())
                || !"demoman".equalsIgnoreCase(state.selectedClasses.getOrDefault(owner.getUniqueId(), "knight"))) return;
        Location blastCenter = explosive.projectile().getLocation().clone();
        ArenaData arena = plugin.findArenaFootprint(blastCenter);
        if (arena == null || !arena.name.equalsIgnoreCase(state.arenaName)) return;
        detonateDemomanBlast(owner, state, blastCenter, explosive.radius(),
                explosive.maximumDamage(), explosive.blastType());
    }

    /** A custom entity-only blast. It deliberately never creates a vanilla explosion. */
    private void detonateDemomanBlast(Player owner, MatchState state, Location center,
                                      double radius, double maximumDamage, DemomanBlastType blastType) {
        if (owner == null || state == null || center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        world.spawnParticle(org.bukkit.Particle.EXPLOSION, center, 1, 0, 0, 0, 0);
        world.spawnParticle(org.bukkit.Particle.FLAME, center, 24, 0.55, 0.35, 0.55, 0.035);
        world.spawnParticle(org.bukkit.Particle.SMOKE, center, 18, 0.65, 0.45, 0.65, 0.025);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.85f, 1.15f);

        double radiusSquared = radius * radius;
        for (Player target : world.getPlayers()) {
            if (!isEnemy(owner, target, state) || target.isDead()
                    || target.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;
            double distanceSquared = target.getLocation().distanceSquared(center);
            if (distanceSquared > radiusSquared) continue;

            double distance = Math.sqrt(distanceSquared);
            double falloff = Math.max(0.18D, 1.0D - (distance / radius));
            double damage = maximumDamage * falloff;
            double healthBefore = target.getHealth();
            double absorptionBefore = target.getAbsorptionAmount();
            target.damage(damage, owner);
            boolean damageApplied = target.getHealth() < healthBefore
                    || target.getAbsorptionAmount() < absorptionBefore;
            if (damageApplied && blastType == DemomanBlastType.BLAST_MINE) {
                owner.sendMessage(ChatColor.GREEN + "Blast Mine mengenai " + target.getName() + "!");
            }

            if (target.isValid() && !target.isDead()) {
                Vector push = target.getLocation().toVector().subtract(center.toVector());
                if (push.lengthSquared() < 0.0001D) push = owner.getLocation().getDirection().clone();
                push.setY(0.0D);
                if (push.lengthSquared() > 0.0001D) {
                    if (blastType == DemomanBlastType.DYNAMITE_BURST
                            || blastType == DemomanBlastType.PRIMARY_BOMB) {
                        // Dynamite should stagger opponents, not launch them upward.
                        push.normalize().multiply(0.16D + 0.18D * falloff)
                                .setY(0.045D + 0.075D * falloff);
                    } else {
                        // Preserve the established Blast Mine knockback behavior.
                        push.normalize().multiply(0.35D + 0.55D * falloff)
                                .setY(0.18D + 0.24D * falloff);
                    }
                    target.setVelocity(target.getVelocity().add(push));
                }
                target.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                        target.getLocation().add(0, 1, 0), 8, 0.25, 0.3, 0.25, 0.025);
            }
            target.sendMessage(ChatColor.RED + "Kamu terkena ledakan Demoman " + owner.getName() + "!");
        }

        if (blastType != DemomanBlastType.BLAST_MINE) {
            double selfRadiusSquared = (radius + 1.0D) * (radius + 1.0D);
            double selfDistanceSquared = owner.getLocation().distanceSquared(center);
            if (selfDistanceSquared <= selfRadiusSquared) {
                Vector recoil = owner.getLocation().toVector().subtract(center.toVector());
                if (recoil.lengthSquared() < 0.0001D) recoil = owner.getLocation().getDirection().multiply(-1.0D);
                recoil.setY(0.0D);
                if (recoil.lengthSquared() > 0.0001D) {
                    recoil.normalize().multiply(0.58D).setY(0.30D);
                    if (plugin.classPassiveManager != null) plugin.classPassiveManager.markDemomanOwnExplosion(owner);
                    owner.setVelocity(owner.getVelocity().add(recoil));
                }
            }
        }
    }

    private boolean placeDemomanMine(Player player, MatchState state) {
        if (demomanMines.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Kamu sudah memiliki satu Blast Mine aktif.");
            return false;
        }
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !targetBlock.getType().isSolid()) {
            player.sendMessage(ChatColor.YELLOW + "Arahkan pandangan ke permukaan padat dalam jarak 5 blok untuk memasang Blast Mine.");
            return false;
        }
        Block feetBlock = targetBlock.getRelative(0, 1, 0);
        Block headBlock = targetBlock.getRelative(0, 2, 0);
        if (!feetBlock.isPassable() || !headBlock.isPassable() || feetBlock.isLiquid() || headBlock.isLiquid()) {
            player.sendMessage(ChatColor.YELLOW + "Tidak ada ruang aman untuk memasang Blast Mine di sana.");
            return false;
        }

        Location mineLocation = targetBlock.getLocation().add(0.5D, 1.05D, 0.5D);
        ArenaData arena = plugin.findArenaFootprint(mineLocation);
        if (arena == null || !arena.name.equalsIgnoreCase(state.arenaName)) {
            player.sendMessage(ChatColor.YELLOW + "Blast Mine hanya dapat dipasang di dalam arena pertandinganmu.");
            return false;
        }
        if (player.getLocation().distanceSquared(mineLocation) > 25.0D) return false;

        Creeper marker = mineLocation.getWorld().spawn(mineLocation, Creeper.class, creeper -> {
            // This Creeper is a visual marker only: no AI, movement, targeting, or explosion.
            // The tag lets MobProtectionListener distinguish it from ordinary arena mobs.
            creeper.addScoreboardTag(BLAST_MINE_MARKER_TAG);
            creeper.setAI(false);
            creeper.setAware(false);
            creeper.setGravity(false);
            creeper.setInvulnerable(true);
            creeper.setSilent(true);
            creeper.setPersistent(false);
            creeper.setRemoveWhenFarAway(false);
            creeper.setTarget(null);
            creeper.setCollidable(false);
            creeper.setGlowing(true);
            creeper.setCustomName(ChatColor.RED + "Blast Mine");
            creeper.setCustomNameVisible(true);
            creeper.setExplosionRadius(0);
            creeper.setFuseTicks(0);
            creeper.setIgnited(false);
            creeper.setCanPickupItems(false);
        });
        // The marker remains a real entity for the mine lifecycle, but only its owner sees it.
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                viewer.hideEntity(plugin, marker);
            }
        }
        marker.getWorld().spawnParticle(org.bukkit.Particle.SMOKE,
                mineLocation.clone().add(0, 0.1D, 0), 8, 0.18, 0.06, 0.18, 0.01);
        marker.getWorld().playSound(mineLocation, Sound.BLOCK_STONE_PLACE, 0.7f, 0.8f);
        demomanMines.put(player.getUniqueId(), new DemomanMine(
                player.getUniqueId(), state.arenaName, mineLocation.clone(), marker,
                System.currentTimeMillis() + 20_000L));
        player.sendMessage(ChatColor.RED + "Blast Mine terpasang! Akan meledak ketika lawan mendekat. Aktif selama 20 detik.");
        return true;
    }

    private void removeDemomanMine(UUID ownerId) {
        DemomanMine mine = demomanMines.remove(ownerId);
        if (mine == null) return;
        Creeper marker = mine.marker();
        if (marker != null && marker.isValid()) marker.remove();
    }

    private void cleanupDemomanFor(UUID ownerId) {
        if (ownerId == null) return;
        removeDemomanMine(ownerId);
        BukkitTask burstTask = demomanBurstTasks.remove(ownerId);
        if (burstTask != null && !burstTask.isCancelled()) burstTask.cancel();
        demomanDynamite.entrySet().removeIf(entry -> {
            DemomanDynamite explosive = entry.getValue();
            if (!ownerId.equals(explosive.ownerId())) return false;
            if (explosive.projectile().isValid()) explosive.projectile().remove();
            return true;
        });
    }

    boolean isBoxerSkillDamage(Player player) {
        return player != null && boxerSkillDamage.contains(player.getUniqueId());
    }

    private void applyBoxerSkillDamage(Player attacker, Player target, double damage) {
        UUID id = attacker.getUniqueId();
        boxerSkillDamage.add(id);
        try {
            // Rapid multi-hit abilities intentionally bypass vanilla hit immunity
            // between their scripted punches; each individual hit stays low.
            target.setNoDamageTicks(0);
            target.damage(damage, attacker);
        } finally {
            boxerSkillDamage.remove(id);
        }
    }

    private Player rayTraceBoxerTarget(Player player, MatchState state) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), 3.4D, 0.72D,
                entity -> entity instanceof Player target && isEnemy(player, target, state));
        if (result == null || !(result.getHitEntity() instanceof Player target)
                || !player.hasLineOfSight(target)) return null;
        return target;
    }

    private boolean useBoxerHeavyJab(Player player, MatchState state) {
        long now = System.currentTimeMillis();
        Map<String, Long> cooldowns = state.abilityCooldowns.computeIfAbsent(
                player.getUniqueId(), ignored -> new HashMap<>());
        long readyAt = cooldowns.getOrDefault("boxer_heavy_jab", 0L);
        if (readyAt > now) {
            player.sendActionBar(ChatColor.YELLOW + "HEAVY JAB: " + Math.max(1L, (readyAt - now + 999L) / 1000L) + "s");
            return false;
        }
        Player target = rayTraceBoxerTarget(player, state);
        if (target == null) {
            player.sendActionBar(ChatColor.GRAY + "HEAVY JAB — arahkan ke lawan dalam jarak 3,4 blok");
            return false;
        }
        cooldowns.put("boxer_heavy_jab", now + 2_100L);
        applyBoxerSkillDamage(player, target, 2.6D);
        Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
        if (push.lengthSquared() > 0.001D) target.setVelocity(target.getVelocity().add(
                push.normalize().multiply(0.42D).setY(0.16D)));
        target.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                target.getLocation().add(0, 1, 0), 12, 0.22, 0.3, 0.22, 0.035);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 0.85f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 0.75f);
        player.sendActionBar(ChatColor.LIGHT_PURPLE + "HEAVY JAB!");
        return true;
    }

    private boolean useBoxerRapidJab(Player player, MatchState state) {
        if (rayTraceBoxerTarget(player, state) == null) {
            player.sendMessage(ChatColor.YELLOW + "Rapid Jab harus diarahkan ke lawan dalam jarak 3,4 blok.");
            return false;
        }
        performBoxerJab(player, state, 0, 3, 1.15D, false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> performBoxerJab(player, state, 1, 3, 1.15D, false), 3L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> performBoxerJab(player, state, 2, 3, 1.15D, true), 6L);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.75f, 1.55f);
        player.sendActionBar(ChatColor.LIGHT_PURPLE + "RAPID JAB — 3-hit combo");
        return true;
    }

    private void performBoxerJab(Player player, MatchState state, int index, int total,
                                 double damage, boolean finishingHit) {
        if (!player.isOnline() || player.isDead() || activeTdmMatch(player) != state
                || !"boxer".equalsIgnoreCase(state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) return;
        Player target = rayTraceBoxerTarget(player, state);
        if (target == null) {
            player.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
                    player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.8D)),
                    1, 0.05, 0.05, 0.05, 0.0);
            return;
        }
        applyBoxerSkillDamage(player, target, damage);
        target.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                target.getLocation().add(0, 1, 0), 7, 0.18, 0.25, 0.18, 0.025);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.65f,
                finishingHit ? 1.35f : 1.65f);
        if (finishingHit && target.isValid() && !target.isDead()) {
            Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (push.lengthSquared() > 0.001D) target.setVelocity(target.getVelocity().add(push.normalize().multiply(0.24D).setY(0.12D)));
        }
    }

    private boolean useBoxerBarrage(Player player, MatchState state) {
        Player target = rayTraceBoxerTarget(player, state);
        if (target == null) {
            player.sendMessage(ChatColor.YELLOW + "Barrage harus diarahkan ke lawan dalam jarak 3,4 blok.");
            return false;
        }
        UUID targetId = target.getUniqueId();
        player.sendMessage(ChatColor.LIGHT_PURPLE + "BARRAGE! Jangan lepaskan tekanan selama rangkaian pukulan.");
        for (int hit = 0; hit < 6; hit++) {
            final int hitIndex = hit;
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> performBoxerBarrageHit(player, state, targetId, hitIndex), hit * 3L);
        }
        return true;
    }

    private void performBoxerBarrageHit(Player player, MatchState state, UUID targetId, int hitIndex) {
        Player target = Bukkit.getPlayer(targetId);
        if (!player.isOnline() || player.isDead() || target == null || !target.isOnline() || target.isDead()
                || activeTdmMatch(player) != state || !isEnemy(player, target, state)
                || !player.getWorld().equals(target.getWorld())
                || player.getLocation().distanceSquared(target.getLocation()) > 3.4D * 3.4D
                || !player.hasLineOfSight(target)
                || !"boxer".equalsIgnoreCase(state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) return;

        applyBoxerSkillDamage(player, target, 0.90D);
        if (hitIndex == 0 && target.isValid() && !target.isDead()) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0,
                    false, true, true), true);
            if (ThreadLocalRandom.current().nextDouble() < 0.08D) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0,
                        false, true, true), true);
                target.sendMessage(ChatColor.DARK_GRAY + "Barrage memberi Weakness I!");
            }
            target.sendMessage(ChatColor.GRAY + "Barrage membuat gerakanmu melambat (Slowness I).");
        }
        target.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                target.getLocation().add(0, 1, 0), 6, 0.16, 0.22, 0.16, 0.02);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG,
                0.65f, 1.45f + (hitIndex * 0.04f));
        if (hitIndex == 5 && target.isValid() && !target.isDead()) {
            Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (push.lengthSquared() > 0.001D) target.setVelocity(target.getVelocity().add(push.normalize().multiply(0.52D).setY(0.24D)));
        }
    }

    /**
     * Skill items activate from a right-click input alone. A player may click
     * empty air or a block; aiming at/interacting with a block is never required.
     * Read the live main-hand slot instead of relying only on event.getItem(),
     * which can be null on some right-click-air interactions or API integrations.
     *
     * Run even when another listener has cancelled the interaction so region or
     * item-use handlers cannot accidentally make air activation feel block-only.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSkillUse(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            heldItem = event.getItem();
        }
        String skill = plugin.readClassSkill(heldItem);
        if (skill == null) {
            MatchState boxerState = activeTdmMatch(player);
            if (boxerState != null && "boxer".equalsIgnoreCase(
                    boxerState.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))
                    && (heldItem == null || heldItem.getType().isAir())) {
                event.setCancelled(true);
                if (!isDuplicateHeavyJabInteraction(player)) {
                    useBoxerHeavyJab(player, boxerState);
                }
            }
            return;
        }

        // Suppress the underlying item/block interaction; the skill is the action.
        event.setCancelled(true);

        MatchState state = activeTdmMatch(player);
        if (state == null) {
            player.sendMessage(ChatColor.RED + "Skill class hanya dapat digunakan saat pertandingan TDM berlangsung.");
            return;
        }
        activate(player, state, skill, heldItem.getType());
    }

    /**
     * Entity right-clicks are delivered through PlayerInteractEntityEvent rather than
     * PlayerInteractEvent. Both paths share a short packet-level guard so a client
     * interaction cannot activate Heavy Jab twice in the same input burst.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBoxerEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem != null && !heldItem.getType().isAir()) return;

        MatchState state = activeTdmMatch(player);
        if (state == null || !"boxer".equalsIgnoreCase(
                state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) return;

        event.setCancelled(true);
        if (!isDuplicateHeavyJabInteraction(player)) {
            useBoxerHeavyJab(player, state);
        }
    }

    private boolean isDuplicateHeavyJabInteraction(Player player) {
        long now = System.nanoTime();
        UUID playerId = player.getUniqueId();
        Long previous = boxerHeavyJabLastInteractionNanos.put(playerId, now);
        // A single client input can arrive through the air/block and entity event paths.
        return previous != null && now - previous < 100_000_000L;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSkillItemDrop(PlayerDropItemEvent event) {
        // Random Stuff is intentionally droppable and has its own five-second ground cleanup.
        if (plugin.isRandomStuffItem(event.getItemDrop().getItemStack())) return;
        Player player = event.getPlayer();
        MatchState state = activeTdmMatch(player);
        if (state != null && "sniper".equalsIgnoreCase(
                state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))
                && sniperTargetsByOwner.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            UUID id = player.getUniqueId();
            if (!hasValidSniperMark(player)) {
                clearSniperMarkOwnedBy(id);
                player.sendActionBar(ChatColor.GRAY + "MARK tidak lagi valid");
                return;
            }
            if (sniperAimLocked.remove(id)) {
                player.sendActionBar(ChatColor.GRAY + "AIM LOCK OFF");
            } else {
                sniperAimLocked.add(id);
                player.sendActionBar(ChatColor.AQUA + "AIM LOCK ON — bidikan mengikuti target MARK");
            }
            return;
        }
        if (plugin.readClassSkill(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.YELLOW + "Item skill class tidak dapat dijatuhkan.");
        }
    }

    /** Sniper arrows travel straight; other projectile classes keep their own gravity settings. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSniperShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof Arrow arrow)) return;
        MatchState state = activeTdmMatch(player);
        if (state == null || !"sniper".equalsIgnoreCase(
                state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) return;

        long now = System.currentTimeMillis();
        Long focusUntil = sniperFocusActive.get(player.getUniqueId());
        boolean focused = focusUntil != null && focusUntil > now;
        if (focusUntil != null && !focused) sniperFocusActive.remove(player.getUniqueId(), focusUntil);

        Vector direction = player.getEyeLocation().getDirection().normalize();
        arrow.setGravity(false);
        arrow.setVelocity(direction.multiply(focused ? 6.8D : 6.2D));
        arrow.setCritical(true);
        arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);

        if (focused) {
            // Store the payload on the projectile itself, so it survives Focus Mode expiry
            // and still applies if the arrow travels for a long time before hitting.
            focusedProjectiles.add(arrow.getUniqueId());
            arrow.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK,
                    arrow.getLocation(), 10, 0.08, 0.08, 0.08, 0.02);
            player.sendMessage(ChatColor.AQUA + "Focus Mode: tembakan mendapat bonus damage 15%!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArcherPinningShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof Arrow arrow)) return;
        MatchState state = activeTdmMatch(player);
        if (state == null || !"archer".equalsIgnoreCase(
                state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) return;
        UUID playerId = player.getUniqueId();
        Long readyUntil = archerPinningReady.get(playerId);
        long now = System.currentTimeMillis();
        if (readyUntil == null || readyUntil <= now) {
            if (readyUntil != null) archerPinningReady.remove(playerId, readyUntil);
            return;
        }

        // A pending Pinning Shot is the active ability window, not a way to
        // bypass a cooldown from a previous use. The cooldown starts only when
        // this charged arrow is actually fired.
        long cooldownUntil = state.abilityCooldowns
                .getOrDefault(playerId, Map.of())
                .getOrDefault("archer_pinning_shot", 0L);
        if (cooldownUntil > now) {
            archerPinningReady.remove(playerId, readyUntil);
            return;
        }
        if (!archerPinningReady.remove(playerId, readyUntil)) return;

        // The 10-second timer is the time allowed to fire the charged arrow.
        // Once fired, give the projectile its own lifetime so a slow arrow does
        // not lose its effect merely because the arming window has ended.
        pinningProjectiles.put(arrow.getUniqueId(), now + 60_000L);
        arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.ALLOWED);
        arrow.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                arrow.getLocation(), 8, 0.08, 0.08, 0.08, 0.02);
        player.sendMessage(ChatColor.GREEN + "Panah Pinning Shot dilepaskan! Cooldown dimulai.");
        startSkillCooldown(player, state, "archer_pinning_shot", Material.SPECTRAL_ARROW);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKitItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        MatchState state = activeMatch(player);
        if (state != null && (state.running || state.preparing || state.launching)) {
            // Kit items are also flagged Unbreakable; this is a second guard for all match gear.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNinjaAttack(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }
        if (attacker != null && vanishedPlayers.contains(attacker.getUniqueId())) {
            endVanish(attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClassCombatEffects(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;

        Player attacker = null;
        Projectile projectile = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile shot
                && shot.getShooter() instanceof Player player) {
            attacker = player;
            projectile = shot;
        }
        if (attacker == null) return;
        MatchState state = activeTdmMatch(target);
        if (state == null || activeTdmMatch(attacker) != state || !isEnemy(attacker, target, state)) return;

        long now = System.currentTimeMillis();
        MarkInfo mark = markedTargets.get(target.getUniqueId());
        if (mark != null) {
            if (mark.expiresAt() <= now) {
                clearSniperMark(target.getUniqueId(), mark);
            } else if (mark.arenaName().equalsIgnoreCase(state.arenaName)) {
                event.setDamage(event.getDamage() * 1.20D);
                if (projectile instanceof Arrow && attacker.getUniqueId().equals(mark.ownerId())) {
                    int landed = mark.arrowsHit() + 1;
                    if (landed >= 3) {
                        clearSniperMark(target.getUniqueId(), mark);
                        attacker.sendMessage(ChatColor.GREEN + "MARK selesai: 3 panah berhasil mengenai " + target.getName() + ".");
                        attacker.sendActionBar(ChatColor.GREEN + "MARK COMPLETE — 3/3 ARROWS");
                    } else {
                        markedTargets.put(target.getUniqueId(), new MarkInfo(mark.ownerId(), mark.arenaName(),
                                mark.expiresAt(), landed));
                        attacker.sendActionBar(ChatColor.AQUA + "MARK: " + landed + "/3 panah mengenai target");
                    }
                }
            }
        }

        if (projectile instanceof Arrow arrow) {
            if (focusedProjectiles.remove(arrow.getUniqueId())) {
                event.setDamage(event.getDamage() * 1.15D);
            }

            Long pinUntil = pinningProjectiles.remove(arrow.getUniqueId());
            if (pinUntil != null && pinUntil > now) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 3,
                        false, true, true), true);
                target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0),
                        12, 0.3, 0.45, 0.3, 0.05);
                target.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0),
                        8, 0.28, 0.4, 0.28, 0.01);
                target.sendMessage(ChatColor.RED + "Kamu terkena Pinning Shot dari Archer " + attacker.getName() + "!");
                attacker.sendMessage(ChatColor.GREEN + "Pinning Shot mengenai " + target.getName() + ".");
            }
        }

        Long venomUntil = assassinVenomReady.get(attacker.getUniqueId());
        if (venomUntil != null && event.getDamager() instanceof Player
                && "assassin".equalsIgnoreCase(state.selectedClasses.getOrDefault(attacker.getUniqueId(), "knight"))) {
            assassinVenomReady.remove(attacker.getUniqueId());
            if (venomUntil > now) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1,
                        false, true, true), true);
                target.getWorld().spawnParticle(org.bukkit.Particle.WITCH,
                        target.getLocation().add(0, 1, 0), 18, 0.32, 0.5, 0.32, 0.02);
                target.sendMessage(ChatColor.DARK_GREEN + "Venom Edge Assassin mengenai kamu!");
                attacker.sendMessage(ChatColor.GREEN + "Venom Edge aktif pada " + target.getName() + ".");
            }
        }
    }

    /** Ninja snowballs apply a light Knockback-I-style push to opposing players. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNinjaSnowballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)
                || !(snowball.getShooter() instanceof Player attacker)
                || !(event.getHitEntity() instanceof LivingEntity target)) return;

        MatchState state = activeTdmMatch(attacker);
        if (state == null || !"ninja".equalsIgnoreCase(
                state.selectedClasses.getOrDefault(attacker.getUniqueId(), "knight"))) return;

        // Knockback is only applied to opposing players in the same TDM match.
        if (!(target instanceof Player targetPlayer)
                || activeTdmMatch(targetPlayer) != state
                || !isEnemy(attacker, targetPlayer, state)) return;

        Vector push = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        push.setY(0);
        if (push.lengthSquared() < 0.0001) {
            push = snowball.getVelocity().clone();
            push.setY(0);
        }
        if (push.lengthSquared() < 0.0001) return;

        push.normalize().multiply(0.65D);
        push.setY(0.22D);
        target.setVelocity(target.getVelocity().add(push));

        target.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE,
                target.getLocation().add(0, 1, 0), 10, 0.28, 0.32, 0.28, 0.015);
        target.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                target.getLocation().add(0, 1, 0), 5, 0.22, 0.25, 0.22, 0.02);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_SNOW_BREAK, 0.65f, 1.25f);
        targetPlayer.sendMessage(ChatColor.YELLOW + "Kamu terdorong oleh Snowball Ninja dari "
                + attacker.getName() + "!");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDemomanDynamiteHit(ProjectileHitEvent event) {
        DemomanDynamite explosive = demomanDynamite.remove(event.getEntity().getUniqueId());
        if (explosive == null) return;
        detonateDemomanProjectile(explosive);
        event.getEntity().remove();
    }

    @EventHandler
    public void onFocusedProjectileHit(ProjectileHitEvent event) {
        UUID projectileId = event.getEntity().getUniqueId();
        // Defer cleanup one tick so an entity-hit damage event from this same
        // projectile can consume its Focus/Pinning payload first.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            focusedProjectiles.remove(projectileId);
            pinningProjectiles.remove(projectileId);
        }, 1L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        cancelFor(event.getEntity());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelFor(event.getPlayer());
    }

    /**
     * F is Minecraft's native swap-hands key. During TDM it activates the
     * primary class skill; for Ninja, holding the configured sneak or sprint
     * input while pressing F activates the secondary Shadow Step skill.
     * The hand swap is cancelled.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPrimarySkillKey(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            event.setCancelled(true);
            return;
        }
        if (vanishedPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        MatchState state = activeTdmMatch(player);
        if (state == null) return;
        String className = state.selectedClasses.getOrDefault(player.getUniqueId(), "knight").toLowerCase();
        // Read the client's current key input rather than only the sneak pose.
        // This also supports users who bind Shift to Sprint instead of Sneak.
        Input input = player.getCurrentInput();
        boolean modifierHeld = input.isSneak() || input.isSprint();
        // Plain F remains Smoke Bomb; Sneak+F or Sprint+F activates Shadow Step.
        String secondarySkill = modifierHeld ? secondarySkill(className) : null;
        String skill = secondarySkill != null ? secondarySkill : primarySkill(className);
        if (skill == null) return;

        event.setCancelled(true);
        Material trigger = secondarySkill != null
                ? secondarySkillMaterial(className) : primarySkillMaterial(className);
        activate(player, state, skill, trigger);
    }

    private String primarySkill(String className) {
        return switch (className) {
            case "knight" -> "knight_guard";
            case "tank" -> "tank_endurance";
            case "assassin" -> "assassin_burst";
            case "ninja" -> "ninja_vanish";
            case "archer" -> "archer_volley";
            case "sniper" -> "sniper_mark";
            case "boxer" -> "boxer_rapid_jab";
            case "demoman" -> "demoman_dynamite";
            default -> null;
        };
    }

    private Material primarySkillMaterial(String className) {
        return switch (className) {
            case "knight" -> Material.IRON_INGOT;
            case "tank" -> Material.IRON_BLOCK;
            case "assassin" -> Material.SUGAR;
            case "ninja" -> Material.GRAY_DYE;
            case "archer" -> Material.FEATHER;
            case "sniper" -> Material.CLOCK;
            case "boxer" -> Material.SLIME_BALL;
            case "demoman" -> Material.FIRE_CHARGE;
            default -> Material.STICK;
        };
    }

    private String secondarySkill(String className) {
        return switch (className) {
            case "knight" -> "knight_rally";
            case "tank" -> "tank_ground_slam";
            case "assassin" -> "assassin_venom_edge";
            case "ninja" -> "ninja_shadow_step";
            case "archer" -> "archer_pinning_shot";
            case "sniper" -> "sniper_focus_shot";
            case "boxer" -> "boxer_barrage";
            case "demoman" -> "demoman_blast_mine";
            default -> null;
        };
    }

    private Material secondarySkillMaterial(String className) {
        return switch (className) {
            case "knight" -> Material.GOLDEN_APPLE;
            case "tank" -> Material.ANVIL;
            case "assassin" -> Material.FERMENTED_SPIDER_EYE;
            case "ninja" -> Material.FEATHER;
            case "archer" -> Material.SPECTRAL_ARROW;
            case "sniper" -> Material.AMETHYST_SHARD;
            case "boxer" -> Material.FIREWORK_STAR;
            case "demoman" -> Material.TNT;
            default -> Material.STICK;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHiddenPlayerSwapHands(PlayerSwapHandItemsEvent event) {
        if (vanishedPlayers.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHiddenPlayerInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && vanishedPlayers.contains(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHiddenPlayerInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && vanishedPlayers.contains(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player viewer = event.getPlayer();
        for (UUID id : new HashSet<>(vanishedPlayers)) {
            Player hidden = Bukkit.getPlayer(id);
            if (hidden != null && hidden.isOnline() && !hidden.equals(viewer)) {
                viewer.hidePlayer(plugin, hidden);
            }
        }
        for (DemomanMine mine : demomanMines.values()) {
            if (mine.marker() != null && mine.marker().isValid()
                    && !mine.ownerId().equals(viewer.getUniqueId())) {
                viewer.hideEntity(plugin, mine.marker());
            }
        }
    }

    private MatchState activeTdmMatch(Player player) {
        for (MatchState state : plugin.matches.values()) {
            if (state.running && "tdm".equals(state.mode)
                    && state.teams.containsKey(player.getUniqueId())) return state;
        }
        return null;
    }

    private MatchState activeMatch(Player player) {
        ArenaData arena = plugin.findArenaFootprint(player.getLocation());
        if (arena == null) return null;
        MatchState state = plugin.matches.get(arena.name);
        if (state == null || !state.teams.containsKey(player.getUniqueId())) return null;
        return state;
    }

    private void activate(Player player, MatchState state, String skill, Material triggerMaterial) {
        String className = state.selectedClasses.getOrDefault(player.getUniqueId(), "knight");
        if (!isSkillAllowed(className, skill)) {
            player.sendMessage(ChatColor.RED + "Skill ini tidak cocok dengan class yang sedang kamu gunakan.");
            return;
        }
        long now = System.currentTimeMillis();
        long readyAt = state.abilityCooldowns
                .getOrDefault(player.getUniqueId(), Map.of())
                .getOrDefault(skill, 0L);
        if (readyAt > now) {
            long remaining = Math.max(1L, (readyAt - now + 999L) / 1000L);
            player.sendMessage(ChatColor.YELLOW + "Skill belum siap. Cooldown tersisa " + remaining + " detik.");
            return;
        }

        boolean used = switch (skill) {
            case "knight_guard" -> useKnightGuard(player, durationSeconds(skill));
            case "tank_endurance" -> useTankEndurance(player, durationSeconds(skill));
            case "assassin_burst" -> useAssassinBurst(player, durationSeconds(skill));
            case "ninja_vanish" -> startVanish(player, durationSeconds(skill));
            case "ninja_shadow_step" -> useShadowStep(player, distanceBlocks(skill));
            case "archer_volley" -> useArcherVolley(player);
            case "sniper_mark" -> useSniperMark(player, state);
            case "boxer_rapid_jab" -> useBoxerRapidJab(player, state);
            case "knight_rally" -> useKnightRally(player, state);
            case "tank_ground_slam" -> useTankGroundSlam(player, state);
            case "assassin_venom_edge" -> armAssassinVenom(player);
            case "archer_pinning_shot" -> armArcherPinningShot(player);
            case "sniper_focus_shot" -> activateSniperFocusMode(player);
            case "boxer_barrage" -> useBoxerBarrage(player, state);
            case "demoman_dynamite" -> useDemomanDynamite(player, state);
            case "demoman_primary_bomb" -> useDemomanPrimaryBomb(player, state);
            case "demoman_blast_mine" -> placeDemomanMine(player, state);
            default -> false;
        };

        if (used && !skill.equals("archer_pinning_shot")) {
            startSkillCooldown(player, state, skill, triggerMaterial);
        }
    }

    private void startSkillCooldown(Player player, MatchState state, String skill, Material triggerMaterial) {
        int cooldown = cooldownSeconds(skill);
        long readyAt = System.currentTimeMillis() + cooldown * 1000L;
        state.abilityCooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(skill, readyAt);
        // Native item cooldown displays the radial/grey overlay on the skill icon.
        // The per-skill timestamp remains authoritative for validation.
        player.setCooldown(triggerMaterial, cooldown * 20);
    }

    /** Reduces an active skill cooldown and keeps the native item cooldown display in sync. */
    long reduceSkillCooldown(Player player, MatchState state, String skill, long reductionMillis) {
        if (player == null || state == null || skill == null || reductionMillis <= 0L) return 0L;
        UUID playerId = player.getUniqueId();
        Map<String, Long> cooldowns = state.abilityCooldowns.get(playerId);
        if (cooldowns == null) return 0L;

        long now = System.currentTimeMillis();
        Long readyAtValue = cooldowns.get(skill);
        Material skillMaterial = switch (skill) {
            case "boxer_rapid_jab" -> Material.SLIME_BALL;
            case "demoman_dynamite" -> Material.FIRE_CHARGE;
            case "demoman_primary_bomb" -> Material.TNT_MINECART;
            case "demoman_blast_mine" -> Material.TNT;
            default -> Material.STICK;
        };
        if (readyAtValue == null || readyAtValue <= now) {
            cooldowns.remove(skill);
            if (cooldowns.isEmpty()) state.abilityCooldowns.remove(playerId);
            return 0L;
        }

        long oldRemaining = readyAtValue - now;
        long appliedReduction = Math.min(reductionMillis, oldRemaining);
        long newReadyAt = readyAtValue - appliedReduction;

        if (newReadyAt <= now) {
            cooldowns.remove(skill);
            player.setCooldown(skillMaterial, 0);
        } else {
            cooldowns.put(skill, newReadyAt);
            int remainingTicks = (int) Math.min(Integer.MAX_VALUE,
                    Math.max(1L, (newReadyAt - now + 49L) / 50L));
            player.setCooldown(skillMaterial, remainingTicks);
        }

        if (cooldowns.isEmpty()) state.abilityCooldowns.remove(playerId);
        return appliedReduction;
    }

    private boolean isSkillAllowed(String className, String skill) {
        String selected = className == null ? "knight" : className.toLowerCase();
        return switch (skill) {
            case "knight_guard", "knight_rally" -> selected.equals("knight");
            case "tank_endurance", "tank_ground_slam" -> selected.equals("tank");
            case "assassin_burst", "assassin_venom_edge" -> selected.equals("assassin");
            case "ninja_vanish", "ninja_shadow_step" -> selected.equals("ninja");
            case "archer_volley", "archer_pinning_shot" -> selected.equals("archer");
            case "sniper_mark", "sniper_focus_shot" -> selected.equals("sniper");
            case "boxer_rapid_jab", "boxer_barrage" -> selected.equals("boxer");
            case "demoman_dynamite", "demoman_primary_bomb", "demoman_blast_mine" -> selected.equals("demoman");
            default -> false;
        };
    }

    private boolean useKnightGuard(Player player, int duration) {
        MatchState state = activeTdmMatch(player);
        if (state == null) return false;
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), 4.2D, 0.85D,
                entity -> entity instanceof Player target && isEnemy(player, target, state));
        if (result == null || !(result.getHitEntity() instanceof Player target)
                || !player.hasLineOfSight(target)) {
            player.sendMessage(ChatColor.YELLOW + "Power Strike harus diarahkan ke lawan dalam jarak 4,2 blok.");
            return false;
        }
        target.damage(4.8D, player);
        if (target.isValid() && !target.isDead()) {
            Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (push.lengthSquared() > 0.001D) target.setVelocity(target.getVelocity().add(
                    push.normalize().multiply(0.38D).setY(0.14D)));
        }
        target.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
                target.getLocation().add(0, 1, 0), 4, 0.25, 0.3, 0.25, 0.0);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.15f);
        player.sendActionBar(ChatColor.RED + "POWER STRIKE!");
        return true;
    }

    private boolean useTankEndurance(Player player, int duration) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                duration * 20, 2, false, false, true), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                duration * 20, 0, false, false, true), true);
        player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0), 24, 0.55, 0.8, 0.55, 0.08);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 0.75f);
        player.sendMessage(ChatColor.GOLD + "Endurance aktif: tubuhmu sangat tahan damage, tetapi gerak melambat.");
        return true;
    }

    private boolean useAssassinBurst(Player player, int duration) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                duration * 20, 2, false, false, true), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,
                duration * 20, 1, false, false, true), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                Math.min(duration, 2) * 20, 1, false, false, true), true);
        // Stronger short effects replace the passive effects; restore Assassin's
        // normal Speed II and Haste I after the burst expires if the TDM match continues.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            MatchState active = activeTdmMatch(player);
            if (active == null || !active.running
                    || !"assassin".equalsIgnoreCase(active.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) return;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    20 * 60 * 60, 1, false, false, true), true);
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,
                    20 * 60 * 60, 0, false, false, true), true);
        }, duration * 20L + 1L);
        player.getWorld().spawnParticle(org.bukkit.Particle.FLAME, player.getLocation().add(0, 1, 0),
                16, 0.42, 0.5, 0.42, 0.025);
        player.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
                player.getLocation().add(0, 1, 0), 3, 0.45, 0.35, 0.45, 0.0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.6f);
        player.sendMessage(ChatColor.DARK_RED + "BURST! Serangan dan gerakanmu meningkat untuk sesaat.");
        return true;
    }

    private boolean startVanish(Player player, int duration) {
        if (vanishedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Kamu sedang menghilang.");
            return false;
        }
        return startVanishTicks(player, Math.max(1, duration) * 20,
                ChatColor.DARK_GREEN + "Smoke Bomb: kamu dan perlengkapan yang terlihat menghilang selama "
                        + duration + " detik.");
    }

    /** Full-entity vanish entry point used by the Shadow Veil passive. */
    boolean startPassiveVanish(Player player, int durationTicks) {
        if (player == null || !player.isOnline() || durationTicks <= 0
                || vanishedPlayers.contains(player.getUniqueId())) return false;
        return startVanishTicks(player, durationTicks, null);
    }

    boolean isVanished(Player player) {
        return player != null && vanishedPlayers.contains(player.getUniqueId());
    }

    /** Ends the shared vanish channel when Shadow Veil expires or is broken. */
    void endPassiveVanish(Player player) {
        if (player != null) endVanish(player);
    }

    private boolean startVanishTicks(Player player, int durationTicks, String message) {
        UUID id = player.getUniqueId();
        if (vanishedPlayers.contains(id)) return false;
        vanishedPlayers.add(id);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                durationTicks, 0, false, false, false), true);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) viewer.hidePlayer(plugin, player);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.5f);
        player.getWorld().spawnParticle(org.bukkit.Particle.SMOKE,
                player.getLocation().add(0, 1, 0), 24, 0.45, 0.65, 0.45, 0.02);
        if (message != null && !message.isBlank()) player.sendMessage(message);

        BukkitTask oldRefresh = vanishTasks.remove(id);
        if (oldRefresh != null) oldRefresh.cancel();
        BukkitTask oldExpiry = vanishExpiryTasks.remove(id);
        if (oldExpiry != null) oldExpiry.cancel();

        // Refresh viewer hiding so players who begin tracking the Ninja mid-vanish
        // also receive the hidden state. A separate one-shot task preserves the
        // exact requested duration rather than rounding to refresh intervals.
        BukkitTask refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !vanishedPlayers.contains(id)) {
                endVanish(player);
                return;
            }
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(player)) viewer.hidePlayer(plugin, player);
            }
        }, 10L, 10L);
        vanishTasks.put(id, refreshTask);
        BukkitTask expiryTask = Bukkit.getScheduler().runTaskLater(
                plugin, () -> endVanish(player), durationTicks);
        vanishExpiryTasks.put(id, expiryTask);
        return true;
    }

    private void endVanish(Player player) {
        UUID id = player.getUniqueId();
        BukkitTask refreshTask = vanishTasks.remove(id);
        if (refreshTask != null && !refreshTask.isCancelled()) refreshTask.cancel();
        BukkitTask expiryTask = vanishExpiryTasks.remove(id);
        if (expiryTask != null && !expiryTask.isCancelled()) expiryTask.cancel();
        if (!vanishedPlayers.remove(id)) return;

        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        if (!player.isOnline()) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) viewer.showPlayer(plugin, player);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.8f);
    }

    /** Clears every class ability cooldown when a participant respawns. */
    void resetCooldownsForRespawn(Player player, MatchState state) {
        if (player == null || state == null) return;
        UUID id = player.getUniqueId();
        state.abilityCooldowns.remove(id);

        String className = state.selectedClasses.getOrDefault(id, "knight").toLowerCase();
        player.setCooldown(primarySkillMaterial(className), 0);
        player.setCooldown(secondarySkillMaterial(className), 0);
        if ("demoman".equals(className)) {
            player.setCooldown(Material.TNT_MINECART, 0);
            player.setCooldown(Material.TNT, 0);
        }

        // Also clear any temporary charge/stance state that could otherwise
        // survive an unusual respawn sequence.
        archerPinningReady.remove(id);
        assassinVenomReady.remove(id);
        sniperFocusActive.remove(id);
        MarkInfo markOnPlayer = markedTargets.get(id);
        if (markOnPlayer != null) clearSniperMark(id, markOnPlayer, player);
        clearSniperMarkOwnedBy(id);
    }

    void cancelFor(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        endVanish(player);
        MarkInfo markOnPlayer = markedTargets.get(id);
        if (markOnPlayer != null) clearSniperMark(id, markOnPlayer, player);
        clearSniperMarkOwnedBy(id);
        // Remove any unexpected reverse mapping that still points at this player.
        sniperTargetsByOwner.entrySet().removeIf(entry -> {
            if (!entry.getValue().equals(id)) return false;
            sniperAimLocked.remove(entry.getKey());
            return true;
        });
        archerPinningReady.remove(id);
        assassinVenomReady.remove(id);
        sniperFocusActive.remove(id);
        ninjaSnowballNextRestock.remove(id);
        boxerHeavyJabLastInteractionNanos.remove(id);
        cleanupDemomanFor(id);
        boxerSkillDamage.remove(id);
    }

    private void clearSniperMarkOwnedBy(UUID ownerId) {
        UUID targetId = sniperTargetsByOwner.remove(ownerId);
        sniperAimLocked.remove(ownerId);
        if (targetId == null) return;
        MarkInfo mark = markedTargets.get(targetId);
        if (mark != null && ownerId.equals(mark.ownerId())) clearSniperMark(targetId, mark);
    }

    private void clearSniperMark(UUID targetId, MarkInfo mark) {
        clearSniperMark(targetId, mark, null);
    }

    private void clearSniperMark(UUID targetId, MarkInfo mark, Player targetOverride) {
        if (targetId == null || mark == null) return;
        boolean removedMark = markedTargets.remove(targetId, mark);
        boolean removedOwnerMapping = sniperTargetsByOwner.remove(mark.ownerId(), targetId);
        if (removedOwnerMapping) sniperAimLocked.remove(mark.ownerId());

        // Do not erase a newer mark's Glowing effect if this was a stale cleanup.
        if (removedMark && !markedTargets.containsKey(targetId)) {
            // A quit/death event may already make Bukkit.getPlayer(UUID) return null;
            // use the event's Player object so the persisted Glowing effect is removed too.
            Player target = targetOverride != null ? targetOverride : Bukkit.getPlayer(targetId);
            if (target != null) target.removePotionEffect(PotionEffectType.GLOWING);
        }
    }

    void cleanupAll() {
        if (ninjaSnowballRestockTask != null && !ninjaSnowballRestockTask.isCancelled()) {
            ninjaSnowballRestockTask.cancel();
        }
        if (demomanExplosivesTask != null && !demomanExplosivesTask.isCancelled()) {
            demomanExplosivesTask.cancel();
        }
        if (sniperAimLockTask != null && !sniperAimLockTask.isCancelled()) {
            sniperAimLockTask.cancel();
        }
        for (BukkitTask task : new ArrayList<>(demomanBurstTasks.values())) {
            if (task != null && !task.isCancelled()) task.cancel();
        }
        demomanBurstTasks.clear();
        for (Map.Entry<UUID, MarkInfo> entry : new ArrayList<>(markedTargets.entrySet())) {
            clearSniperMark(entry.getKey(), entry.getValue());
        }
        for (UUID id : new HashSet<>(vanishedPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) endVanish(player);
            else {
                BukkitTask task = vanishTasks.remove(id);
                if (task != null) task.cancel();
                vanishedPlayers.remove(id);
            }
        }
        for (BukkitTask task : new ArrayList<>(vanishTasks.values())) task.cancel();
        for (BukkitTask task : new ArrayList<>(vanishExpiryTasks.values())) task.cancel();
        vanishTasks.clear();
        vanishExpiryTasks.clear();
        vanishedPlayers.clear();
        sniperFocusActive.clear();
        markedTargets.clear();
        sniperTargetsByOwner.clear();
        sniperAimLocked.clear();
        boxerHeavyJabLastInteractionNanos.clear();
        ninjaSnowballNextRestock.clear();
        focusedProjectiles.clear();
        pinningProjectiles.clear();
        for (UUID ownerId : new HashSet<>(demomanMines.keySet())) removeDemomanMine(ownerId);
        for (DemomanDynamite explosive : demomanDynamite.values()) {
            if (explosive.projectile().isValid()) explosive.projectile().remove();
        }
        demomanDynamite.clear();
        boxerSkillDamage.clear();
    }

    private boolean useDemomanDynamite(Player player, MatchState state) {
        UUID ownerId = player.getUniqueId();
        if (demomanBurstTasks.containsKey(ownerId)) return false;

        Vector forward = player.getEyeLocation().getDirection().normalize();
        Vector side = new Vector(-forward.getZ(), 0.0D, forward.getX());
        if (side.lengthSquared() > 0.0001D) side.normalize();
        else side.setX(1.0D);

        // A single repeating task emits one projectile every four ticks (0.2 s).
        // The owner/match/class checks also stop a burst if state changes mid-sequence.
        BukkitTask task = new BukkitRunnable() {
            private int launched;

            @Override
            public void run() {
                Player currentOwner = Bukkit.getPlayer(ownerId);
                if (currentOwner == null || !currentOwner.isOnline() || currentOwner.isDead()
                        || activeTdmMatch(currentOwner) != state
                        || !"demoman".equalsIgnoreCase(
                                state.selectedClasses.getOrDefault(ownerId, "knight"))) {
                    cleanupDemomanFor(ownerId);
                    cancel();
                    return;
                }

                launchDemomanDynamiteProjectile(currentOwner, state, forward, side, launched,
                        3.5D, 4.8D, DemomanBlastType.DYNAMITE_BURST);
                launched++;
                if (launched >= 3) {
                    demomanBurstTasks.remove(ownerId);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 4L);
        demomanBurstTasks.put(ownerId, task);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.1f);
        player.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
                player.getEyeLocation().add(forward.clone().multiply(0.6D)),
                18, 0.18, 0.12, 0.18, 0.035);
        player.sendMessage(ChatColor.RED + "Dynamite Burst! Tiga bom diluncurkan satu per satu dengan jeda singkat.");
        return true;
    }

    /** Throws one smaller custom bomb from Demoman's primary TNT weapon. */
    private boolean useDemomanPrimaryBomb(Player player, MatchState state) {
        if (state == null || activeTdmMatch(player) != state
                || !"demoman".equalsIgnoreCase(
                        state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) return false;

        Vector forward = player.getEyeLocation().getDirection().normalize();
        Vector side = new Vector(-forward.getZ(), 0.0D, forward.getX());
        if (side.lengthSquared() > 0.0001D) side.normalize();
        else side.setX(1.0D);

        launchDemomanDynamiteProjectile(player, state, forward, side, 1,
                3.0D, 3.4D, DemomanBlastType.PRIMARY_BOMB);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.8f, 1.35f);
        player.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
                player.getEyeLocation().add(forward.clone().multiply(0.55D)),
                7, 0.10D, 0.08D, 0.10D, 0.015D);
        player.sendActionBar(ChatColor.RED + "POCKET BOMB!");
        return true;
    }

    private void launchDemomanDynamiteProjectile(Player player, MatchState state,
                                                 Vector forward, Vector side, int shotIndex,
                                                 double radius, double maximumDamage,
                                                 DemomanBlastType blastType) {
        int spreadIndex = shotIndex - 1; // left, center, right
        Snowball projectile = player.launchProjectile(Snowball.class);
        projectile.setItem(new ItemStack(blastType == DemomanBlastType.PRIMARY_BOMB
                ? Material.TNT : Material.FIRE_CHARGE));
        Vector velocity = forward.clone().multiply(1.15D)
                .add(side.clone().multiply(spreadIndex * 0.20D))
                .add(new Vector(0.0D, spreadIndex == 0 ? 0.04D : 0.10D, 0.0D));
        projectile.setVelocity(velocity);
        projectile.setGravity(true);
        demomanDynamite.put(projectile.getUniqueId(), new DemomanDynamite(
                player.getUniqueId(), state.arenaName, projectile, System.currentTimeMillis() + 1_350L,
                radius, maximumDamage, blastType));
        projectile.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
                projectile.getLocation(), 5, 0.06D, 0.06D, 0.06D, 0.01D);
    }

    private boolean useShadowStep(Player player, int maxDistance) {
        ArenaData arena = plugin.findArena(player.getLocation());
        if (arena == null) return false;
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().setY(0);
        if (direction.lengthSquared() < 0.001) {
            player.sendMessage(ChatColor.YELLOW + "Arah teleport tidak valid.");
            return false;
        }
        direction.normalize();
        Location destination = null;
        World world = origin.getWorld();

        for (int distance = maxDistance; distance >= 2 && destination == null; distance--) {
            double x = origin.getX() + direction.getX() * distance;
            double z = origin.getZ() + direction.getZ() * distance;
            int blockX = Location.locToBlock(x);
            int blockZ = Location.locToBlock(z);
            for (int dy : new int[]{0, 1, -1, 2, -2, -3}) {
                int feetY = origin.getBlockY() + dy;
                Block feet = world.getBlockAt(blockX, feetY, blockZ);
                Block head = world.getBlockAt(blockX, feetY + 1, blockZ);
                Block floor = world.getBlockAt(blockX, feetY - 1, blockZ);
                if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()) continue;
                if (!floor.getType().isSolid() || floor.isLiquid() || isHazardousFloor(floor.getType())) continue;
                if (Math.abs(blockX - arena.centerX) > arena.radius
                        || Math.abs(blockZ - arena.centerZ) > arena.radius) continue;
                destination = new Location(world, blockX + 0.5, feetY, blockZ + 0.5,
                        origin.getYaw(), origin.getPitch());
                break;
            }
        }

        if (destination == null) {
            player.sendMessage(ChatColor.YELLOW + "Shadow Step gagal: tidak ada tempat mendarat yang aman di depanmu.");
            return false;
        }
        if (!player.teleport(destination)) return false;
        player.setFallDistance(0.0f);
        player.setVelocity(new Vector(0, 0, 0));
        player.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 1.25f);
        player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, destination.clone().add(0, 1, 0), 28, 0.35, 0.55, 0.35, 0.1);
        player.sendMessage(ChatColor.DARK_GREEN + "Shadow Step berhasil: kamu berpindah tanpa Ender Pearl.");
        return true;
    }

    private boolean isHazardousFloor(Material material) {
        String name = material.name();
        return name.contains("MAGMA") || name.contains("CACTUS") || name.contains("CAMPFIRE")
                || name.contains("SWEET_BERRY") || material == Material.FIRE || material == Material.SOUL_FIRE;
    }

    private boolean useArcherVolley(Player player) {
        int arrows = player.getInventory().all(Material.ARROW).values().stream()
                .mapToInt(ItemStack::getAmount).sum();
        if (arrows < 3) {
            player.sendMessage(ChatColor.YELLOW + "Volley membutuhkan minimal 3 panah.");
            return false;
        }
        player.getInventory().removeItem(new ItemStack(Material.ARROW, 3));
        Vector base = player.getEyeLocation().getDirection().normalize();
        double[] angles = {-0.10, 0.0, 0.10};
        for (double angle : angles) {
            Arrow arrow = player.launchProjectile(Arrow.class);
            arrow.setVelocity(base.clone().rotateAroundY(angle).multiply(2.6));
            arrow.getWorld().spawnParticle(org.bukkit.Particle.CRIT, arrow.getLocation(),
                    5, 0.05, 0.05, 0.05, 0.015);
            arrow.setCritical(true);
            arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
            // Volley projectiles are temporary skill effects, not recoverable ammo.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (arrow.isValid()) arrow.remove();
            }, 100L);
        }
        player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, player.getEyeLocation(),
                14, 0.18, 0.18, 0.18, 0.04);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.2f);
        player.sendMessage(ChatColor.GREEN + "Volley! Tiga panah dilepaskan sekaligus.");
        return true;
    }

    private void tickSniperAimLock() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, MarkInfo> entry : new ArrayList<>(markedTargets.entrySet())) {
            UUID targetId = entry.getKey();
            MarkInfo mark = entry.getValue();
            Player target = Bukkit.getPlayer(targetId);
            Player owner = Bukkit.getPlayer(mark.ownerId());
            if (!isValidSniperMark(owner, target, mark, now)) {
                clearSniperMark(targetId, mark);
                if (owner != null && owner.isOnline()) {
                    owner.sendActionBar(mark.expiresAt() <= now
                            ? ChatColor.GRAY + "MARK EXPIRED"
                            : ChatColor.GRAY + "MARK DIBERSIHKAN — target tidak lagi valid");
                }
            }
        }

        for (UUID ownerId : new HashSet<>(sniperAimLocked)) {
            Player owner = Bukkit.getPlayer(ownerId);
            UUID targetId = sniperTargetsByOwner.get(ownerId);
            Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
            MarkInfo mark = targetId == null ? null : markedTargets.get(targetId);
            if (!isValidSniperMark(owner, target, mark, now)) {
                if (mark != null && targetId != null) clearSniperMark(targetId, mark);
                else sniperAimLocked.remove(ownerId);
                continue;
            }

            Vector direction = target.getEyeLocation().toVector()
                    .subtract(owner.getEyeLocation().toVector());
            if (direction.lengthSquared() > 0.0001D) {
                Location view = owner.getLocation().clone();
                view.setDirection(direction);
                owner.setRotation(view.getYaw(), view.getPitch());
            }
        }
    }

    private boolean hasValidSniperMark(Player owner) {
        if (owner == null) return false;
        UUID targetId = sniperTargetsByOwner.get(owner.getUniqueId());
        if (targetId == null) return false;
        MarkInfo mark = markedTargets.get(targetId);
        return isValidSniperMark(owner, Bukkit.getPlayer(targetId), mark, System.currentTimeMillis());
    }

    private boolean isValidSniperMark(Player owner, Player target, MarkInfo mark, long now) {
        if (owner == null || target == null || mark == null
                || !owner.isOnline() || !target.isOnline()
                || owner.isDead() || target.isDead()
                || !owner.isValid() || !target.isValid()
                || owner.getGameMode() != org.bukkit.GameMode.SURVIVAL
                || target.getGameMode() != org.bukkit.GameMode.SURVIVAL
                || mark.expiresAt() <= now
                || !owner.getUniqueId().equals(mark.ownerId())
                || !target.getUniqueId().equals(
                        sniperTargetsByOwner.get(owner.getUniqueId()))
                || !owner.getWorld().equals(target.getWorld())) return false;

        MatchState ownerState = activeTdmMatch(owner);
        MatchState targetState = activeTdmMatch(target);
        if (ownerState == null || ownerState != targetState
                || !mark.arenaName().equalsIgnoreCase(ownerState.arenaName)
                || !"sniper".equalsIgnoreCase(
                        ownerState.selectedClasses.getOrDefault(owner.getUniqueId(), "knight"))
                || !isEnemy(owner, target, ownerState)) return false;

        ArenaData ownerArena = plugin.findArenaFootprint(owner.getLocation());
        ArenaData targetArena = plugin.findArenaFootprint(target.getLocation());
        return ownerArena != null && targetArena != null
                && ownerArena.name.equalsIgnoreCase(mark.arenaName())
                && targetArena.name.equalsIgnoreCase(mark.arenaName());
    }

    private boolean useSniperMark(Player player, MatchState state) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), 32.0, 0.65,
                entity -> entity instanceof Player target
                        && !target.isDead()
                        && target.getGameMode() == org.bukkit.GameMode.SURVIVAL
                        && isEnemy(player, target, state));
        if (result == null || !(result.getHitEntity() instanceof Player target)
                || !player.hasLineOfSight(target)) {
            player.sendMessage(ChatColor.YELLOW + "Tidak ada lawan yang terlihat dalam jarak 32 blok.");
            return false;
        }
        long expiresAt = System.currentTimeMillis() + 8_000L;
        UUID ownerId = player.getUniqueId();
        UUID targetId = target.getUniqueId();
        UUID previousTarget = sniperTargetsByOwner.get(ownerId);
        if (previousTarget != null && !previousTarget.equals(targetId)) {
            MarkInfo oldOwnedMark = markedTargets.get(previousTarget);
            if (oldOwnedMark != null && ownerId.equals(oldOwnedMark.ownerId())) {
                clearSniperMark(previousTarget, oldOwnedMark);
            } else {
                sniperTargetsByOwner.remove(ownerId, previousTarget);
                sniperAimLocked.remove(ownerId);
            }
        }

        // A target can have only one active mark. Replacing it also clears the old owner's lock.
        MarkInfo replacedMark = markedTargets.get(targetId);
        if (replacedMark != null && !ownerId.equals(replacedMark.ownerId())) {
            clearSniperMark(targetId, replacedMark);
            Player previousOwner = Bukkit.getPlayer(replacedMark.ownerId());
            if (previousOwner != null) {
                previousOwner.sendActionBar(ChatColor.GRAY + "MARK DIGANTI OLEH SNIPER LAIN");
            }
        }
        markedTargets.put(targetId, new MarkInfo(ownerId, state.arenaName, expiresAt, 0));
        sniperTargetsByOwner.put(ownerId, targetId);
        sniperAimLocked.remove(ownerId);
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 8 * 20, 0, false, false, true), true);
        target.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK,
                target.getLocation().add(0, 1, 0), 28, 0.42, 0.7, 0.42, 0.04);
        target.getWorld().spawnParticle(org.bukkit.Particle.END_ROD,
                target.getLocation().add(0, 1, 0), 10, 0.3, 0.6, 0.3, 0.015);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.4f);
        player.sendMessage(ChatColor.AQUA + "Target MARK: " + target.getName()
                + " selama 8 detik atau sampai 3 panah Sniper mengenainya. Tekan Q untuk mengaktifkan AIM LOCK.");
        target.sendMessage(ChatColor.RED + "Kamu terkena MARK dari Sniper " + player.getName()
                + "! Mark berakhir setelah 3 panah mengenaimu atau 8 detik.");
        return true;
    }

    private boolean useKnightRally(Player player, MatchState state) {
        int affected = 0;
        String ownTeam = state.teams.get(player.getUniqueId());
        if (ownTeam == null) return false;
        for (Player teammate : player.getWorld().getPlayers()) {
            String teammateTeam = state.teams.get(teammate.getUniqueId());
            if (teammateTeam == null || !ownTeam.equals(teammateTeam)
                    || teammate.getLocation().distanceSquared(player.getLocation()) > 64.0) continue;
            teammate.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 4 * 20, 0,
                    false, true, true), true);
            teammate.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                    teammate.getLocation().add(0, 1, 0), 12, 0.4, 0.55, 0.4, 0.02);
            if (!teammate.equals(player)) teammate.sendMessage(ChatColor.AQUA + "Knight " + player.getName() + " mengaktifkan Rally: Resistance I!");
            affected++;
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.9f, 1.2f);
        player.sendMessage(ChatColor.AQUA + "Rally aktif: " + affected + " anggota tim mendapat Resistance I selama 4 detik.");
        return true;
    }

    private boolean useTankGroundSlam(Player player, MatchState state) {
        int hit = 0;
        for (Player target : player.getWorld().getPlayers()) {
            if (!isEnemy(player, target, state) || target.getLocation().distanceSquared(player.getLocation()) > 25.0) continue;
            if (vanishedPlayers.contains(target.getUniqueId())) endVanish(target);
            target.damage(3.2D, player);
            Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (push.lengthSquared() > 0.001) push.normalize().multiply(0.68D).setY(0.78D);
            else push.setY(0.28D);
            target.setVelocity(push);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1,
                    false, true, true), true);
            target.sendMessage(ChatColor.GOLD + "Kamu terkena Ground Slam milik Tank " + player.getName() + "!");
            hit++;
        }
        player.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, player.getLocation(), 35,
                1.5, 0.15, 1.5, 0.15, Material.STONE.createBlockData());
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD,
                player.getLocation().add(0, 0.15, 0), 18, 1.2, 0.12, 1.2, 0.06);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.65f);
        player.sendMessage(ChatColor.GOLD + "Ground Slam! " + hit + " lawan terkena gelombang kejut.");
        return true;
    }

    private boolean armAssassinVenom(Player player) {
        assassinVenomReady.put(player.getUniqueId(), System.currentTimeMillis() + 8_000L);
        player.getWorld().spawnParticle(org.bukkit.Particle.WITCH,
                player.getLocation().add(0, 1, 0), 18, 0.3, 0.45, 0.3, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 0.8f, 1.5f);
        player.sendMessage(ChatColor.DARK_GREEN + "Venom Edge siap: serangan melee berikutnya memberi Poison II. Berlaku 8 detik.");
        return true;
    }

    private boolean armArcherPinningShot(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long pendingUntil = archerPinningReady.get(playerId);
        if (pendingUntil != null) {
            if (pendingUntil > now) {
                player.sendMessage(ChatColor.YELLOW + "Pinning Shot sudah siap. Lepaskan satu panah dengan bow terlebih dahulu.");
                return false;
            }
            if (archerPinningReady.remove(playerId, pendingUntil)) {
                MatchState state = activeTdmMatch(player);
                if (state != null) {
                    startSkillCooldown(player, state, "archer_pinning_shot", Material.SPECTRAL_ARROW);
                }
                player.sendMessage(ChatColor.YELLOW + "Waktu persiapan Pinning Shot habis; cooldown dimulai.");
                return false;
            }
        }

        int arrows = player.getInventory().all(Material.ARROW).values().stream()
                .mapToInt(ItemStack::getAmount).sum();
        if (arrows < 1) {
            player.sendMessage(ChatColor.YELLOW + "Pinning Shot membutuhkan minimal 1 panah.");
            return false;
        }

        long armedUntil = now + 10_000L;
        archerPinningReady.put(playerId, armedUntil);
        player.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE,
                player.getEyeLocation(), 12, 0.18, 0.18, 0.18, 0.015);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.8f, 1.3f);
        int cooldown = cooldownSeconds("archer_pinning_shot");
        player.sendMessage(ChatColor.GREEN + "Pinning Shot siap: lepaskan satu panah dalam 10 detik. Cooldown "
                + cooldown + " detik dimulai setelah panah dilepaskan.");

        // If the player never fires, expire the charge and start the cooldown
        // after the preparation window so the skill cannot be refreshed freely.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!archerPinningReady.remove(playerId, armedUntil)) return;
            Player online = Bukkit.getPlayer(playerId);
            if (online == null) return;
            MatchState active = activeTdmMatch(online);
            if (active != null) {
                startSkillCooldown(online, active, "archer_pinning_shot", Material.SPECTRAL_ARROW);
                online.sendMessage(ChatColor.YELLOW + "Pinning Shot kedaluwarsa; cooldown dimulai.");
            }
        }, 200L);
        return true;
    }

    private boolean activateSniperFocusMode(Player player) {
        long expiresAt = System.currentTimeMillis() + 8_000L;
        sniperFocusActive.put(player.getUniqueId(), expiresAt);
        player.getWorld().spawnParticle(org.bukkit.Particle.END_ROD,
                player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.45)),
                18, 0.18, 0.2, 0.18, 0.025);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.7f);
        player.sendMessage(ChatColor.AQUA + "Focus Mode aktif selama 8 detik: semua panah mendapat +15% damage dan kecepatan 6.8!");
        return true;
    }

    private boolean isEnemy(Player attacker, Player target, MatchState state) {
        if (target.equals(attacker) || !target.isOnline()) return false;
        String attackerTeam = state.teams.get(attacker.getUniqueId());
        String targetTeam = state.teams.get(target.getUniqueId());
        return attackerTeam != null && targetTeam != null && !attackerTeam.equals(targetTeam);
    }

    private int cooldownSeconds(String skill) {
        return Math.max(1, Math.min(3600, plugin.getConfig().getInt(
                "class-abilities." + skill + ".cooldown-seconds", defaultCooldown(skill))));
    }

    private int durationSeconds(String skill) {
        return Math.max(1, Math.min(30, plugin.getConfig().getInt(
                "class-abilities." + skill + ".duration-seconds", defaultDuration(skill))));
    }

    private int distanceBlocks(String skill) {
        return Math.max(2, Math.min(12,
                plugin.getConfig().getInt("class-abilities." + skill + ".distance-blocks", 8)));
    }

    private int defaultCooldown(String skill) {
        return switch (skill) {
            case "knight_guard" -> 26;
            case "tank_endurance" -> 45;
            case "assassin_burst" -> 30;
            case "ninja_vanish" -> 34;
            case "ninja_shadow_step" -> 20;
            case "archer_volley" -> 20;
            case "sniper_mark" -> 25;
            case "boxer_rapid_jab" -> 20;
            case "boxer_barrage" -> 28;
            case "demoman_dynamite" -> 10;
            case "demoman_primary_bomb" -> 2;
            case "demoman_blast_mine" -> 20;
            case "knight_rally" -> 32;
            case "tank_ground_slam" -> 30;
            case "assassin_venom_edge" -> 22;
            case "archer_pinning_shot" -> 18;
            case "sniper_focus_shot" -> 25;
            default -> 30;
        };
    }

    private int defaultDuration(String skill) {
        return switch (skill) {
            case "knight_guard" -> 3;
            case "tank_endurance" -> 5;
            case "assassin_burst" -> 3;
            case "ninja_vanish" -> 6;
            default -> 4;
        };
    }
}
