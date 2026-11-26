package id.hadziq.arenasystem;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.AbstractArrow.PickupStatus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

// $VF: Compiled from ClassAbilityListener.java
final class ClassAbilityListener implements Listener {
    static final String BLAST_MINE_MARKER_TAG = "arenasystem_blast_mine_marker";
    private static final Set<EquipmentSlot> HIDDEN_SLOTS = EnumSet.of(
        EquipmentSlot.HAND,
        EquipmentSlot.OFF_HAND,
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    );
    private final ArenaSystemPlugin plugin;
    private final Set<UUID> vanishedPlayers = new HashSet<>();
    private final Map<UUID, BukkitTask> vanishTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> vanishExpiryTasks = new HashMap<>();
    private final Map<UUID, ClassAbilityListener.MarkInfo> markedTargets = new HashMap<>();
    private final Map<UUID, UUID> sniperTargetsByOwner = new HashMap<>();
    private final Set<UUID> sniperAimLocked = new HashSet<>();
    private final Map<UUID, Long> archerPinningReady = new HashMap<>();
    private final Map<UUID, Long> pinningProjectiles = new HashMap<>();
    private final Map<UUID, Long> assassinVenomReady = new HashMap<>();
    private final Map<UUID, Long> assassinOffhandReadyAtNanos = new HashMap<>();
    private final Map<UUID, Long> assassinOffhandInputLastReceivedNanos = new HashMap<>();
    private final Map<UUID, Long> skillInputLastReceivedNanos = new HashMap<>();
    private final Map<UUID, Long> sniperAimLockInputLastReceivedNanos = new HashMap<>();
    private static final String ASSASSIN_OFFHAND_COOLDOWN_CHANNEL = "arenasystem:assassin_offhand_cooldown";
    private static final double ASSASSIN_OFFHAND_DAMAGE = 2.0;
    private static final int ASSASSIN_OFFHAND_COOLDOWN_TICKS = 12;
    private static final long ASSASSIN_OFFHAND_COOLDOWN_NANOS = 600000000L;
    private final Map<UUID, Long> sniperFocusActive = new HashMap<>();
    private final Set<UUID> focusedProjectiles = new HashSet<>();
    private final Map<UUID, Long> ninjaSnowballNextRestock = new HashMap<>();
    private static final long NINJA_SNOWBALL_RESTOCK_MILLIS = 4500L;
    private static final int NINJA_SNOWBALL_MAX = 7;
    private final Map<UUID, ClassAbilityListener.DemomanDynamite> demomanDynamite = new HashMap<>();
    private final Map<UUID, ClassAbilityListener.DemomanMine> demomanMines = new HashMap<>();
    private final Map<UUID, BukkitTask> demomanBurstTasks = new HashMap<>();
    private final Map<UUID, Long> boxerHeavyJabLastInteractionNanos = new HashMap<>();
    private final Set<UUID> boxerSkillDamage = new HashSet<>();
    private final BukkitTask ninjaSnowballRestockTask;
    private final BukkitTask demomanExplosivesTask;
    private final BukkitTask sniperAimLockTask;

    ClassAbilityListener(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
        this.plugin
            .getServer()
            .getMessenger()
            .registerOutgoingPluginChannel(plugin, "arenasystem:assassin_offhand_cooldown");
        this.ninjaSnowballRestockTask = Bukkit.getScheduler()
            .runTaskTimer(plugin, this::tickNinjaSnowballRestock, 5L, 5L);
        this.demomanExplosivesTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickDemomanExplosives, 2L, 2L);
        this.sniperAimLockTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSniperAimLock, 1L, 1L);
    }

    private void tickNinjaSnowballRestock() {
        long now = System.currentTimeMillis();
        Set<UUID> activeNinjas = new HashSet<>();

        for (MatchState state : this.plugin.matches.values()) {
            if (state.running && "tdm".equals(state.mode)) {
                for (UUID playerId : state.teams.keySet()) {
                    if ("ninja".equalsIgnoreCase(state.selectedClasses.getOrDefault(playerId, "knight"))) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null
                            && player.isOnline()
                            && !player.isDead()
                            && player.getGameMode() == GameMode.SURVIVAL) {
                            activeNinjas.add(playerId);
                            int snowballCount = this.countNinjaSnowballs(player);
                            if (snowballCount >= 7) {
                                this.ninjaSnowballNextRestock.put(playerId, now + 4500L);
                            } else {
                                long nextRestock = this.ninjaSnowballNextRestock
                                    .computeIfAbsent(playerId, ignored -> now + 4500L);
                                if (now >= nextRestock && this.addOneNinjaSnowball(player)) {
                                    this.ninjaSnowballNextRestock.put(playerId, now + 4500L);
                                }
                            }
                        }
                    }
                }
            }
        }

        this.ninjaSnowballNextRestock.keySet().removeIf(playerIdx -> !activeNinjas.contains(playerIdx));
    }

    private int countNinjaSnowballs(Player player) {
        int total = 0;

        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == Material.SNOWBALL) {
                total += item.getAmount();
            }
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() == Material.SNOWBALL) {
            total += offHand.getAmount();
        }

        return total;
    }

    private boolean addOneNinjaSnowball(Player player) {
        if (this.countNinjaSnowballs(player) >= 7) {
            return false;
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() == Material.SNOWBALL && offHand.getAmount() < 7) {
            offHand.setAmount(offHand.getAmount() + 1);
            player.getInventory().setItemInOffHand(offHand);
            return true;
        }

        ItemStack[] contents = player.getInventory().getStorageContents();

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null
                && item.getType() != Material.AIR
                && item.getType() == Material.SNOWBALL
                && item.getAmount() < 7) {
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

        for (Entry<UUID, ClassAbilityListener.DemomanDynamite> entry : new ArrayList<>(this.demomanDynamite.entrySet())) {
            ClassAbilityListener.DemomanDynamite explosive = entry.getValue();
            Snowball projectile = explosive.projectile();
            if (!projectile.isValid()) {
                this.demomanDynamite.remove(entry.getKey());
            } else if (now >= explosive.fuseAtMillis()) {
                this.demomanDynamite.remove(entry.getKey());
                this.detonateDemomanProjectile(explosive);
                projectile.remove();
            }
        }

        for (Entry<UUID, ClassAbilityListener.DemomanMine> entry : new ArrayList<>(this.demomanMines.entrySet())) {
            ClassAbilityListener.DemomanMine mine = entry.getValue();
            Player owner = Bukkit.getPlayer(mine.ownerId());
            MatchState state = owner == null ? null : this.activeTdmMatch(owner);
            if (owner == null
                || !owner.isOnline()
                || owner.isDead()
                || state == null
                || !state.arenaName.equalsIgnoreCase(mine.arenaName())
                || !"demoman".equalsIgnoreCase(state.selectedClasses.getOrDefault(owner.getUniqueId(), "knight"))) {
                this.removeDemomanMine(mine.ownerId());
            } else if (now >= mine.expiresAtMillis()) {
                this.removeDemomanMine(mine.ownerId());
                owner.sendActionBar(ChatColor.GRAY + "Blast Mine kedaluwarsa.");
            } else {
                double triggerRadiusSquared = 5.0625;

                for (Player target : mine.location().getWorld().getPlayers()) {
                    if (this.isEnemy(owner, target, state)
                        && !target.isDead()
                        && target.getGameMode() == GameMode.SURVIVAL
                        && !(target.getLocation().distanceSquared(mine.location()) > triggerRadiusSquared)) {
                        this.removeDemomanMine(mine.ownerId());
                        this.detonateDemomanBlast(
                            owner, state, mine.location(), 3.5, 4.2, ClassAbilityListener.DemomanBlastType.BLAST_MINE
                        );
                        owner.sendActionBar(ChatColor.RED + "BLAST MINE TRIGGERED!");
                        break;
                    }
                }
            }
        }
    }

    private void detonateDemomanProjectile(ClassAbilityListener.DemomanDynamite explosive) {
        Player owner = Bukkit.getPlayer(explosive.ownerId());
        if (owner != null && owner.isOnline() && !owner.isDead()) {
            MatchState state = this.activeTdmMatch(owner);
            if (state != null
                && state.arenaName.equalsIgnoreCase(explosive.arenaName())
                && "demoman".equalsIgnoreCase(state.selectedClasses.getOrDefault(owner.getUniqueId(), "knight"))) {
                Location blastCenter = explosive.projectile().getLocation().clone();
                ArenaData arena = this.plugin.findArenaFootprint(blastCenter);
                if (arena != null && arena.name.equalsIgnoreCase(state.arenaName)) {
                    this.detonateDemomanBlast(
                        owner, state, blastCenter, explosive.radius(), explosive.maximumDamage(), explosive.blastType()
                    );
                }
            }
        }
    }

    private void detonateDemomanBlast(
        Player owner,
        MatchState state,
        Location center,
        double radius,
        double maximumDamage,
        ClassAbilityListener.DemomanBlastType blastType
    ) {
        if (owner != null && state != null && center != null && center.getWorld() != null) {
            World world = center.getWorld();
            world.spawnParticle(Particle.EXPLOSION, center, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticle(Particle.FLAME, center, 24, 0.55, 0.35, 0.55, 0.035);
            world.spawnParticle(Particle.SMOKE, center, 18, 0.65, 0.45, 0.65, 0.025);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.85F, 1.15F);
            double radiusSquared = radius * radius;

            for (Player target : world.getPlayers()) {
                if (this.isEnemy(owner, target, state) && !target.isDead() && target.getGameMode() == GameMode.SURVIVAL
                    )
                 {
                    double distanceSquared = target.getLocation().distanceSquared(center);
                    if (!(distanceSquared > radiusSquared)) {
                        double distance = Math.sqrt(distanceSquared);
                        double falloff = Math.max(0.18, 1.0 - distance / radius);
                        double damage = maximumDamage * falloff;
                        double healthBefore = target.getHealth();
                        double absorptionBefore = target.getAbsorptionAmount();
                        target.damage(damage, owner);
                        boolean damageApplied = target.getHealth() < healthBefore
                            || target.getAbsorptionAmount() < absorptionBefore;
                        if (damageApplied && blastType == ClassAbilityListener.DemomanBlastType.BLAST_MINE) {
                            owner.sendMessage(ChatColor.GREEN + "Blast Mine mengenai " + target.getName() + "!");
                        }

                        if (target.isValid() && !target.isDead()) {
                            Vector push = target.getLocation().toVector().subtract(center.toVector());
                            if (push.lengthSquared() < 1.0E-4) {
                                push = owner.getLocation().getDirection().clone();
                            }

                            push.setY(0.0);
                            if (push.lengthSquared() > 1.0E-4) {
                                if (blastType != ClassAbilityListener.DemomanBlastType.DYNAMITE_BURST
                                    && blastType != ClassAbilityListener.DemomanBlastType.PRIMARY_BOMB) {
                                    push.normalize().multiply(0.35 + 0.55 * falloff).setY(0.18 + 0.24 * falloff);
                                } else {
                                    push.normalize().multiply(0.16 + 0.18 * falloff).setY(0.045 + 0.075 * falloff);
                                }

                                target.setVelocity(target.getVelocity().add(push));
                            }

                            target.getWorld()
                                .spawnParticle(
                                    Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 8, 0.25, 0.3, 0.25, 0.025
                                );
                        }

                        target.sendMessage(ChatColor.RED + "Kamu terkena ledakan Demoman " + owner.getName() + "!");
                    }
                }
            }

            if (blastType != ClassAbilityListener.DemomanBlastType.BLAST_MINE) {
                double selfRadiusSquared = (radius + 1.0) * (radius + 1.0);
                double selfDistanceSquared = owner.getLocation().distanceSquared(center);
                if (selfDistanceSquared <= selfRadiusSquared) {
                    Vector recoil = owner.getLocation().toVector().subtract(center.toVector());
                    if (recoil.lengthSquared() < 1.0E-4) {
                        recoil = owner.getLocation().getDirection().multiply(-1.0);
                    }

                    recoil.setY(0.0);
                    if (recoil.lengthSquared() > 1.0E-4) {
                        recoil.normalize().multiply(0.58).setY(0.3);
                        if (this.plugin.classPassiveManager != null) {
                            this.plugin.classPassiveManager.markDemomanOwnExplosion(owner);
                        }

                        owner.setVelocity(owner.getVelocity().add(recoil));
                    }
                }
            }
        }
    }

    private boolean placeDemomanMine(Player player, MatchState state) {
        if (this.demomanMines.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Kamu sudah memiliki satu Blast Mine aktif.");
            return false;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock != null && targetBlock.getType().isSolid()) {
            Block feetBlock = targetBlock.getRelative(0, 1, 0);
            Block headBlock = targetBlock.getRelative(0, 2, 0);
            if (feetBlock.isPassable() && headBlock.isPassable() && !feetBlock.isLiquid() && !headBlock.isLiquid()) {
                Location mineLocation = targetBlock.getLocation().add(0.5, 1.05, 0.5);
                ArenaData arena = this.plugin.findArenaFootprint(mineLocation);
                if (arena != null && arena.name.equalsIgnoreCase(state.arenaName)) {
                    if (player.getLocation().distanceSquared(mineLocation) > 25.0) {
                        return false;
                    }

                    Creeper marker = mineLocation.getWorld().spawn(mineLocation, Creeper.class, creeper -> {
                        creeper.addScoreboardTag("arenasystem_blast_mine_marker");
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

                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                            viewer.hideEntity(this.plugin, marker);
                        }
                    }

                    marker.getWorld()
                        .spawnParticle(
                            Particle.SMOKE, mineLocation.clone().add(0.0, 0.1, 0.0), 8, 0.18, 0.06, 0.18, 0.01
                        );
                    marker.getWorld().playSound(mineLocation, Sound.BLOCK_STONE_PLACE, 0.7F, 0.8F);
                    this.demomanMines
                        .put(
                            player.getUniqueId(),
                            new ClassAbilityListener.DemomanMine(
                                player.getUniqueId(),
                                state.arenaName,
                                mineLocation.clone(),
                                marker,
                                System.currentTimeMillis() + 20000L
                            )
                        );
                    player.sendMessage(
                        ChatColor.RED
                            + "Blast Mine terpasang! Akan meledak ketika lawan mendekat. Aktif selama 20 detik."
                    );
                    return true;
                } else {
                    player.sendMessage(
                        ChatColor.YELLOW + "Blast Mine hanya dapat dipasang di dalam arena pertandinganmu."
                    );
                    return false;
                }
            } else {
                player.sendMessage(ChatColor.YELLOW + "Tidak ada ruang aman untuk memasang Blast Mine di sana.");
                return false;
            }
        } else {
            player.sendMessage(
                ChatColor.YELLOW + "Arahkan pandangan ke permukaan padat dalam jarak 5 blok untuk memasang Blast Mine."
            );
            return false;
        }
    }

    private void removeDemomanMine(UUID ownerId) {
        ClassAbilityListener.DemomanMine mine = this.demomanMines.remove(ownerId);
        if (mine != null) {
            Creeper marker = mine.marker();
            if (marker != null && marker.isValid()) {
                marker.remove();
            }
        }
    }

    private void cleanupDemomanFor(UUID ownerId) {
        if (ownerId != null) {
            this.removeDemomanMine(ownerId);
            BukkitTask burstTask = this.demomanBurstTasks.remove(ownerId);
            if (burstTask != null && !burstTask.isCancelled()) {
                burstTask.cancel();
            }

            this.demomanDynamite.entrySet().removeIf(entry -> {
                ClassAbilityListener.DemomanDynamite explosive = entry.getValue();
                if (!ownerId.equals(explosive.ownerId())) {
                    return false;
                }

                if (explosive.projectile().isValid()) {
                    explosive.projectile().remove();
                }

                return true;
            });
        }
    }

    boolean isBoxerSkillDamage(Player player) {
        return player != null && this.boxerSkillDamage.contains(player.getUniqueId());
    }

    private void applyBoxerSkillDamage(Player attacker, Player target, double damage) {
        UUID id = attacker.getUniqueId();
        this.boxerSkillDamage.add(id);

        try {
            target.setNoDamageTicks(0);
            target.damage(damage, attacker);
        } finally {
            this.boxerSkillDamage.remove(id);
        }
    }

    private Player rayTraceBoxerTarget(Player player, MatchState state) {
        RayTraceResult result = player.getWorld()
            .rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                3.4,
                0.72,
                entity -> entity instanceof Player targetx && this.isEnemy(player, targetx, state)
            );
        return result != null && result.getHitEntity() instanceof Player target && player.hasLineOfSight(target)
            ? target
            : null;
    }

    private boolean useBoxerHeavyJab(Player player, MatchState state) {
        long now = System.currentTimeMillis();
        Map<String, Long> cooldowns = state.abilityCooldowns
            .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long readyAt = cooldowns.getOrDefault("boxer_heavy_jab", 0L);
        if (readyAt > now) {
            player.sendActionBar(ChatColor.YELLOW + "HEAVY JAB: " + Math.max(1L, (readyAt - now + 999L) / 1000L) + "s");
            return false;
        }

        Player target = this.rayTraceBoxerTarget(player, state);
        if (target == null) {
            player.sendActionBar(ChatColor.GRAY + "HEAVY JAB — arahkan ke lawan dalam jarak 3,4 blok");
            return false;
        }

        cooldowns.put("boxer_heavy_jab", now + 2100L);
        this.applyBoxerSkillDamage(player, target, 2.6);
        Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
        if (push.lengthSquared() > 0.001) {
            target.setVelocity(target.getVelocity().add(push.normalize().multiply(0.42).setY(0.16)));
        }

        target.getWorld()
            .spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 12, 0.22, 0.3, 0.22, 0.035);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8F, 0.85F);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8F, 0.75F);
        player.sendActionBar(ChatColor.LIGHT_PURPLE + "HEAVY JAB!");
        return true;
    }

    private boolean useBoxerRapidJab(Player player, MatchState state) {
        if (this.rayTraceBoxerTarget(player, state) == null) {
            player.sendMessage(ChatColor.YELLOW + "Rapid Jab harus diarahkan ke lawan dalam jarak 3,4 blok.");
            return false;
        } else {
            this.performBoxerJab(player, state, 0, 3, 1.15, false);
            Bukkit.getScheduler()
                .runTaskLater(this.plugin, () -> this.performBoxerJab(player, state, 1, 3, 1.15, false), 3L);
            Bukkit.getScheduler()
                .runTaskLater(this.plugin, () -> this.performBoxerJab(player, state, 2, 3, 1.15, true), 6L);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.75F, 1.55F);
            player.sendActionBar(ChatColor.LIGHT_PURPLE + "RAPID JAB — 3-hit combo");
            return true;
        }
    }

    private void performBoxerJab(
        Player player, MatchState state, int index, int total, double damage, boolean finishingHit
    ) {
        if (player.isOnline()
            && !player.isDead()
            && this.activeTdmMatch(player) == state
            && "boxer".equalsIgnoreCase(state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) {
            Player target = this.rayTraceBoxerTarget(player, state);
            if (target == null) {
                player.getWorld()
                    .spawnParticle(
                        Particle.SWEEP_ATTACK,
                        player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.8)),
                        1,
                        0.05,
                        0.05,
                        0.05,
                        0.0
                    );
            } else {
                this.applyBoxerSkillDamage(player, target, damage);
                target.getWorld()
                    .spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 7, 0.18, 0.25, 0.18, 0.025);
                target.getWorld()
                    .playSound(
                        target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.65F, finishingHit ? 1.35F : 1.65F
                    );
                if (finishingHit && target.isValid() && !target.isDead()) {
                    Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
                    if (push.lengthSquared() > 0.001) {
                        target.setVelocity(target.getVelocity().add(push.normalize().multiply(0.24).setY(0.12)));
                    }
                }
            }
        }
    }

    private boolean useBoxerBarrage(Player player, MatchState state) {
        Player target = this.rayTraceBoxerTarget(player, state);
        if (target == null) {
            player.sendMessage(ChatColor.YELLOW + "Barrage harus diarahkan ke lawan dalam jarak 3,4 blok.");
            return false;
        }

        UUID targetId = target.getUniqueId();
        player.sendMessage(ChatColor.LIGHT_PURPLE + "BARRAGE! Jangan lepaskan tekanan selama rangkaian pukulan.");

        for (int hit = 0; hit < 6; hit++) {
            int hitIndex = hit;
            Bukkit.getScheduler()
                .runTaskLater(
                    this.plugin, () -> this.performBoxerBarrageHit(player, state, targetId, hitIndex), hit * 3L
                );
        }

        return true;
    }

    private void performBoxerBarrageHit(Player player, MatchState state, UUID targetId, int hitIndex) {
        Player target = Bukkit.getPlayer(targetId);
        if (player.isOnline()
            && !player.isDead()
            && target != null
            && target.isOnline()
            && !target.isDead()
            && this.activeTdmMatch(player) == state
            && this.isEnemy(player, target, state)
            && player.getWorld().equals(target.getWorld())
            && !(player.getLocation().distanceSquared(target.getLocation()) > 11.559999999999999)
            && player.hasLineOfSight(target)
            && "boxer".equalsIgnoreCase(state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) {
            this.applyBoxerSkillDamage(player, target, 0.9);
            if (hitIndex == 0 && target.isValid() && !target.isDead()) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0, false, true, true), true);
                if (ThreadLocalRandom.current().nextDouble() < 0.08) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, true, true), true);
                    target.sendMessage(ChatColor.DARK_GRAY + "Barrage memberi Weakness I!");
                }

                target.sendMessage(ChatColor.GRAY + "Barrage membuat gerakanmu melambat (Slowness I).");
            }

            target.getWorld()
                .spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 6, 0.16, 0.22, 0.16, 0.02);
            target.getWorld()
                .playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.65F, 1.45F + hitIndex * 0.04F);
            if (hitIndex == 5 && target.isValid() && !target.isDead()) {
                Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
                if (push.lengthSquared() > 0.001) {
                    target.setVelocity(target.getVelocity().add(push.normalize().multiply(0.52).setY(0.24)));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSkillUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND) {
            Action action = event.getAction();
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                Player player = event.getPlayer();
                ItemStack heldItem = player.getInventory().getItemInMainHand();
                if (heldItem == null || heldItem.getType() == Material.AIR) {
                    heldItem = event.getItem();
                }

                String skill = this.plugin.readClassSkill(heldItem);
                if (skill != null) {
                    event.setCancelled(true);
                    MatchState state = this.activeTdmMatch(player);
                    if (state == null) {
                        player.sendMessage(
                            ChatColor.RED + "Skill class hanya dapat digunakan saat pertandingan TDM berlangsung."
                        );
                    } else {
                        this.activate(player, state, skill, heldItem.getType());
                    }
                } else {
                    MatchState boxerState = this.activeTdmMatch(player);
                    if (boxerState != null
                        && "boxer"
                            .equalsIgnoreCase(boxerState.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))
                        && (heldItem == null || heldItem.getType().isAir())) {
                        event.setCancelled(true);
                        if (!this.isDuplicateHeavyJabInteraction(player)) {
                            this.useBoxerHeavyJab(player, boxerState);
                        }
                    }
                }
            }
        }
    }

    private boolean isAssassinDualBladeReady(Player player, MatchState state) {
        return state != null
            && player.getGameMode() == GameMode.SURVIVAL
            && "assassin".equalsIgnoreCase(state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))
            && this.plugin.isAssassinBlade(player.getInventory().getItemInMainHand())
            && this.plugin.isAssassinBlade(player.getInventory().getItemInOffHand());
    }

    private void useAssassinOffhand(Player attacker, MatchState state, Player target) {
        if (this.isAssassinDualBladeReady(attacker, state)) {
            UUID attackerId = attacker.getUniqueId();
            long nowNanos = System.nanoTime();
            long readyAtNanos = this.assassinOffhandReadyAtNanos.getOrDefault(attackerId, 0L);
            if (nowNanos < readyAtNanos) {
                long remainingMillis = Math.max(1L, (readyAtNanos - nowNanos + 999999L) / 1000000L);
                attacker.sendActionBar(
                    ChatColor.YELLOW + "OFF-HAND: " + String.format(Locale.ROOT, "%.1fs", remainingMillis / 1000.0)
                );
            } else {
                this.assassinOffhandReadyAtNanos.put(attackerId, nowNanos + 600000000L);
                attacker.swingOffHand();
                this.sendAssassinOffhandCooldown(attacker);
                if (!this.isValidAssassinOffhandTarget(attacker, target, state)) {
                    attacker.sendActionBar(ChatColor.GOLD + "TWIN BLADE — OFF-HAND");
                } else if (target.getNoDamageTicks() > target.getMaximumNoDamageTicks() / 2) {
                    attacker.sendActionBar(ChatColor.GRAY + "OFF-HAND — target masih dalam hit cooldown");
                } else {
                    double healthBefore = target.getHealth();
                    double absorptionBefore = target.getAbsorptionAmount();
                    target.damage(2.0, attacker);
                    boolean damageApplied = target.getHealth() < healthBefore
                        || target.getAbsorptionAmount() < absorptionBefore;
                    if (damageApplied) {
                        target.getWorld()
                            .spawnParticle(
                                Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 5, 0.18, 0.22, 0.18, 0.02
                            );
                        target.getWorld()
                            .playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK, 0.75F, 1.55F);
                        attacker.sendActionBar(ChatColor.GOLD + "TWIN BLADE — OFF-HAND HIT");
                    } else {
                        attacker.sendActionBar(ChatColor.GOLD + "TWIN BLADE — OFF-HAND");
                    }
                }
            }
        }
    }

    private Player rayTraceAssassinOffhandTarget(Player player, MatchState state) {
        RayTraceResult result = player.getWorld()
            .rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                3.2,
                0.6,
                entity -> entity instanceof Player targetx && this.isEnemy(player, targetx, state)
            );
        return result != null
                && result.getHitEntity() instanceof Player target
                && this.isValidAssassinOffhandTarget(player, target, state)
            ? target
            : null;
    }

    private boolean isValidAssassinOffhandTarget(Player attacker, Player target, MatchState state) {
        return target != null
            && this.isEnemy(attacker, target, state)
            && !target.isDead()
            && target.getGameMode() == GameMode.SURVIVAL
            && attacker.getWorld().equals(target.getWorld())
            && attacker.getEyeLocation().distanceSquared(target.getLocation().add(0.0, 1.0, 0.0)) <= 10.24
            && attacker.hasLineOfSight(target);
    }

    private void sendAssassinOffhandCooldown(Player player) {
        byte[] payload = ByteBuffer.allocate(4).putInt(12).array();
        player.sendPluginMessage(this.plugin, "arenasystem:assassin_offhand_cooldown", payload);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBoxerEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND) {
            Player player = event.getPlayer();
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            if (heldItem == null || heldItem.getType().isAir()) {
                MatchState state = this.activeTdmMatch(player);
                if (state != null
                    && "boxer".equalsIgnoreCase(state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) {
                    event.setCancelled(true);
                    if (!this.isDuplicateHeavyJabInteraction(player)) {
                        this.useBoxerHeavyJab(player, state);
                    }
                }
            }
        }
    }

    private boolean isDuplicateHeavyJabInteraction(Player player) {
        long now = System.nanoTime();
        UUID playerId = player.getUniqueId();
        Long previous = this.boxerHeavyJabLastInteractionNanos.put(playerId, now);
        return previous != null && now - previous < 100000000L;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSkillItemDrop(PlayerDropItemEvent event) {
        if (!this.plugin.isRandomStuffItem(event.getItemDrop().getItemStack())) {
            Player player = event.getPlayer();
            MatchState state = this.activeTdmMatch(player);
            if (state != null
                && "assassin".equalsIgnoreCase(state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))
                && this.plugin.isAssassinBlade(event.getItemDrop().getItemStack())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.YELLOW + "Twin Blade Assassin tidak dapat dijatuhkan selama pertandingan.");
            } else if (state != null
                && "sniper".equalsIgnoreCase(state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))
                && this.sniperTargetsByOwner.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
            } else if (this.plugin.readClassSkill(event.getItemDrop().getItemStack()) != null) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.YELLOW + "Item skill class tidak dapat dijatuhkan.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSniperShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && event.getProjectile() instanceof Arrow arrow) {
            MatchState state = this.activeTdmMatch(player);
            if (state != null
                && "sniper".equalsIgnoreCase(state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) {
                long now = System.currentTimeMillis();
                Long focusUntil = this.sniperFocusActive.get(player.getUniqueId());
                boolean focused = focusUntil != null && focusUntil > now;
                if (focusUntil != null && !focused) {
                    this.sniperFocusActive.remove(player.getUniqueId(), focusUntil);
                }

                Vector direction = player.getEyeLocation().getDirection().normalize();
                arrow.setGravity(false);
                arrow.setVelocity(direction.multiply(focused ? 6.8 : 6.2));
                arrow.setCritical(true);
                arrow.setPickupStatus(PickupStatus.DISALLOWED);
                if (focused) {
                    this.focusedProjectiles.add(arrow.getUniqueId());
                    arrow.getWorld()
                        .spawnParticle(Particle.ELECTRIC_SPARK, arrow.getLocation(), 10, 0.08, 0.08, 0.08, 0.02);
                    player.sendMessage(ChatColor.AQUA + "Focus Mode: tembakan mendapat bonus damage 15%!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArcherPinningShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && event.getProjectile() instanceof Arrow arrow) {
            MatchState state = this.activeTdmMatch(player);
            if (state != null
                && "archer".equalsIgnoreCase(state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) {
                UUID playerId = player.getUniqueId();
                Long readyUntil = this.archerPinningReady.get(playerId);
                long now = System.currentTimeMillis();
                if (readyUntil != null && readyUntil > now) {
                    long cooldownUntil = state.abilityCooldowns
                        .getOrDefault(playerId, Map.of())
                        .getOrDefault("archer_pinning_shot", 0L);
                    if (cooldownUntil > now) {
                        this.archerPinningReady.remove(playerId, readyUntil);
                    } else if (this.archerPinningReady.remove(playerId, readyUntil)) {
                        this.pinningProjectiles.put(arrow.getUniqueId(), now + 60000L);
                        arrow.setPickupStatus(PickupStatus.ALLOWED);
                        arrow.getWorld().spawnParticle(Particle.CRIT, arrow.getLocation(), 8, 0.08, 0.08, 0.08, 0.02);
                        player.sendMessage(ChatColor.GREEN + "Panah Pinning Shot dilepaskan! Cooldown dimulai.");
                        this.startSkillCooldown(player, state, "archer_pinning_shot", Material.SPECTRAL_ARROW);
                    }
                } else if (readyUntil != null) {
                    this.archerPinningReady.remove(playerId, readyUntil);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKitItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        MatchState state = this.activeMatch(player);
        if (state != null && (state.running || state.preparing || state.launching)) {
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

        if (attacker != null && this.vanishedPlayers.contains(attacker.getUniqueId())) {
            this.endVanish(attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClassCombatEffects(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player target) {
            Player var11 = null;
            Projectile projectile = null;
            if (event.getDamager() instanceof Player player) {
                var11 = player;
            } else if (event.getDamager() instanceof Projectile shot && shot.getShooter() instanceof Player player) {
                var11 = player;
                projectile = shot;
            }

            if (var11 != null) {
                MatchState state = this.activeTdmMatch(target);
                if (state != null && this.activeTdmMatch(var11) == state && this.isEnemy(var11, target, state)) {
                    long now = System.currentTimeMillis();
                    ClassAbilityListener.MarkInfo mark = this.markedTargets.get(target.getUniqueId());
                    if (mark != null) {
                        if (mark.expiresAt() <= now) {
                            this.clearSniperMark(target.getUniqueId(), mark);
                        } else if (mark.arenaName().equalsIgnoreCase(state.arenaName)) {
                            event.setDamage(event.getDamage() * 1.2);
                            if (projectile instanceof Arrow && var11.getUniqueId().equals(mark.ownerId())) {
                                int landed = mark.arrowsHit() + 1;
                                if (landed >= 3) {
                                    this.clearSniperMark(target.getUniqueId(), mark);
                                    var11.sendMessage(
                                        ChatColor.GREEN
                                            + "MARK selesai: 3 panah berhasil mengenai "
                                            + target.getName()
                                            + "."
                                    );
                                    var11.sendActionBar(ChatColor.GREEN + "MARK COMPLETE — 3/3 ARROWS");
                                } else {
                                    this.markedTargets
                                        .put(
                                            target.getUniqueId(),
                                            new ClassAbilityListener.MarkInfo(
                                                mark.ownerId(), mark.arenaName(), mark.expiresAt(), landed
                                            )
                                        );
                                    var11.sendActionBar(ChatColor.AQUA + "MARK: " + landed + "/3 panah mengenai target");
                                }
                            }
                        }
                    }

                    if (projectile instanceof Arrow arrow) {
                        if (this.focusedProjectiles.remove(arrow.getUniqueId())) {
                            event.setDamage(event.getDamage() * 1.15);
                        }

                        Long pinUntil = this.pinningProjectiles.remove(arrow.getUniqueId());
                        if (pinUntil != null && pinUntil > now) {
                            target.addPotionEffect(
                                new PotionEffect(PotionEffectType.SLOWNESS, 50, 3, false, true, true), true
                            );
                            target.getWorld()
                                .spawnParticle(
                                    Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 12, 0.3, 0.45, 0.3, 0.05
                                );
                            target.getWorld()
                                .spawnParticle(
                                    Particle.SNOWFLAKE,
                                    target.getLocation().add(0.0, 1.0, 0.0),
                                    8,
                                    0.28,
                                    0.4,
                                    0.28,
                                    0.01
                                );
                            target.sendMessage(
                                ChatColor.RED + "Kamu terkena Pinning Shot dari Archer " + var11.getName() + "!"
                            );
                            var11.sendMessage(ChatColor.GREEN + "Pinning Shot mengenai " + target.getName() + ".");
                        }
                    }

                    Long venomUntil = this.assassinVenomReady.get(var11.getUniqueId());
                    if (venomUntil != null
                        && event.getDamager() instanceof Player
                        && "assassin"
                            .equalsIgnoreCase(state.selectedClasses.getOrDefault(var11.getUniqueId(), "knight"))) {
                        this.assassinVenomReady.remove(var11.getUniqueId());
                        if (venomUntil > now) {
                            target.addPotionEffect(
                                new PotionEffect(PotionEffectType.POISON, 60, 1, false, true, true), true
                            );
                            target.getWorld()
                                .spawnParticle(
                                    Particle.WITCH, target.getLocation().add(0.0, 1.0, 0.0), 18, 0.32, 0.5, 0.32, 0.02
                                );
                            target.sendMessage(ChatColor.DARK_GREEN + "Venom Edge Assassin mengenai kamu!");
                            var11.sendMessage(ChatColor.GREEN + "Venom Edge aktif pada " + target.getName() + ".");
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNinjaSnowballHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Snowball snowball
            && snowball.getShooter() instanceof Player attacker
            && event.getHitEntity() instanceof LivingEntity target) {
            MatchState state = this.activeTdmMatch(attacker);
            if (state != null
                && "ninja".equalsIgnoreCase(state.selectedClasses.getOrDefault(attacker.getUniqueId(), "knight"))
                && target instanceof Player targetPlayer
                && this.activeTdmMatch(targetPlayer) == state
                && this.isEnemy(attacker, targetPlayer, state)) {
                Vector push = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
                push.setY(0);
                if (push.lengthSquared() < 1.0E-4) {
                    push = snowball.getVelocity().clone();
                    push.setY(0);
                }

                if (!(push.lengthSquared() < 1.0E-4)) {
                    push.normalize().multiply(0.65);
                    push.setY(0.22);
                    target.setVelocity(target.getVelocity().add(push));
                    target.getWorld()
                        .spawnParticle(
                            Particle.SNOWFLAKE, target.getLocation().add(0.0, 1.0, 0.0), 10, 0.28, 0.32, 0.28, 0.015
                        );
                    target.getWorld()
                        .spawnParticle(
                            Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 5, 0.22, 0.25, 0.22, 0.02
                        );
                    target.getWorld().playSound(target.getLocation(), Sound.BLOCK_SNOW_BREAK, 0.65F, 1.25F);
                    targetPlayer.sendMessage(
                        ChatColor.YELLOW + "Kamu terdorong oleh Snowball Ninja dari " + attacker.getName() + "!"
                    );
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDemomanDynamiteHit(ProjectileHitEvent event) {
        ClassAbilityListener.DemomanDynamite explosive = this.demomanDynamite.remove(event.getEntity().getUniqueId());
        if (explosive != null) {
            this.detonateDemomanProjectile(explosive);
            event.getEntity().remove();
        }
    }

    @EventHandler
    public void onFocusedProjectileHit(ProjectileHitEvent event) {
        UUID projectileId = event.getEntity().getUniqueId();
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.focusedProjectiles.remove(projectileId);
            this.pinningProjectiles.remove(projectileId);
        }, 1L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        this.sniperAimLockInputLastReceivedNanos.remove(event.getEntity().getUniqueId());
        this.cancelFor(event.getEntity());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.skillInputLastReceivedNanos.remove(player.getUniqueId());
        this.sniperAimLockInputLastReceivedNanos.remove(player.getUniqueId());
        this.cancelFor(player);
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
        if (this.vanishedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHiddenPlayerInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && this.vanishedPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHiddenPlayerInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && this.vanishedPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player viewer = event.getPlayer();

        for (UUID id : new HashSet<>(this.vanishedPlayers)) {
            Player hidden = Bukkit.getPlayer(id);
            if (hidden != null && hidden.isOnline() && !hidden.equals(viewer)) {
                viewer.hidePlayer(this.plugin, hidden);
            }
        }

        for (ClassAbilityListener.DemomanMine mine : this.demomanMines.values()) {
            if (mine.marker() != null && mine.marker().isValid() && !mine.ownerId().equals(viewer.getUniqueId())) {
                viewer.hideEntity(this.plugin, mine.marker());
            }
        }
    }

    private MatchState activeTdmMatch(Player player) {
        for (MatchState state : this.plugin.matches.values()) {
            if (state.running && "tdm".equals(state.mode) && state.teams.containsKey(player.getUniqueId())) {
                return state;
            }
        }

        return null;
    }

    private void toggleSniperAimLock(Player player) {
        UUID playerId = player.getUniqueId();
        MatchState state = this.activeTdmMatch(player);
        if (state != null
            && "sniper".equalsIgnoreCase(state.selectedClasses.getOrDefault(playerId, "knight"))
            && this.sniperTargetsByOwner.containsKey(playerId)) {
            if (!this.hasValidSniperMark(player)) {
                this.clearSniperMarkOwnedBy(playerId);
                player.sendActionBar(ChatColor.GRAY + "MARK tidak lagi valid");
            } else if (this.sniperAimLocked.remove(playerId)) {
                player.sendActionBar(ChatColor.GRAY + "AIM LOCK OFF");
            } else {
                this.sniperAimLocked.add(playerId);
                player.sendActionBar(ChatColor.AQUA + "AIM LOCK ON — bidikan mengikuti target MARK");
            }
        }
    }

    void handleSkillInput(Player player, byte action) {
        if (player != null
            && player.isOnline()
            && !player.isDead()
            && player.getGameMode() == GameMode.SURVIVAL
            && !this.vanishedPlayers.contains(player.getUniqueId())) {
            if (action == 4) {
                UUID playerId = player.getUniqueId();
                long now = System.nanoTime();
                long previous = this.sniperAimLockInputLastReceivedNanos.getOrDefault(playerId, 0L);
                if (previous == 0L || now - previous >= 75000000L) {
                    this.sniperAimLockInputLastReceivedNanos.put(playerId, now);
                    this.toggleSniperAimLock(player);
                }
            } else if (action == 3) {
                MatchState assassinState = this.activeTdmMatch(player);
                if (this.isAssassinDualBladeReady(player, assassinState)) {
                    UUID playerId = player.getUniqueId();
                    long now = System.nanoTime();
                    long previous = this.assassinOffhandInputLastReceivedNanos.getOrDefault(playerId, 0L);
                    if (previous == 0L || now - previous >= 100000000L) {
                        this.assassinOffhandInputLastReceivedNanos.put(playerId, now);
                        this.useAssassinOffhand(
                            player, assassinState, this.rayTraceAssassinOffhandTarget(player, assassinState)
                        );
                    }
                }
            } else if (action == 1 || action == 2) {
                MatchState state = this.activeTdmMatch(player);
                if (state != null) {
                    long now = System.nanoTime();
                    UUID playerId = player.getUniqueId();
                    long previous = this.skillInputLastReceivedNanos.getOrDefault(playerId, 0L);
                    if (previous == 0L || now - previous >= 75000000L) {
                        this.skillInputLastReceivedNanos.put(playerId, now);
                        String className = state.selectedClasses
                            .getOrDefault(playerId, "knight")
                            .toLowerCase(Locale.ROOT);
                        boolean secondary = action == 2;
                        String skill = secondary ? this.secondarySkill(className) : this.primarySkill(className);
                        if (skill != null) {
                            Material trigger = secondary
                                ? this.secondarySkillMaterial(className)
                                : this.primarySkillMaterial(className);
                            this.activate(player, state, skill, trigger);
                        }
                    }
                }
            }
        }
    }

    private MatchState activeMatch(Player player) {
        ArenaData arena = this.plugin.findArenaFootprint(player.getLocation());
        if (arena == null) {
            return null;
        }

        MatchState state = this.plugin.matches.get(arena.name);
        return state != null && state.teams.containsKey(player.getUniqueId()) ? state : null;
    }

    private void activate(Player player, MatchState state, String skill, Material triggerMaterial) {
        String className = state.selectedClasses.getOrDefault(player.getUniqueId(), "knight");
        if (!this.isSkillAllowed(className, skill)) {
            player.sendMessage(ChatColor.RED + "Skill ini tidak cocok dengan class yang sedang kamu gunakan.");
        } else {
            long now = System.currentTimeMillis();
            long readyAt = state.abilityCooldowns.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(skill, 0L);
            if (readyAt > now) {
                long remaining = Math.max(1L, (readyAt - now + 999L) / 1000L);
                player.sendMessage(ChatColor.YELLOW + "Skill belum siap. Cooldown tersisa " + remaining + " detik.");
            } else {
                boolean used = switch (skill) {
                    case "knight_guard" -> this.useKnightGuard(player, this.durationSeconds(skill));
                    case "tank_endurance" -> this.useTankEndurance(player, this.durationSeconds(skill));
                    case "assassin_burst" -> this.useAssassinBurst(player, this.durationSeconds(skill));
                    case "ninja_vanish" -> this.startVanish(player, this.durationSeconds(skill));
                    case "ninja_shadow_step" -> this.useShadowStep(player, this.distanceBlocks(skill));
                    case "archer_volley" -> this.useArcherVolley(player);
                    case "sniper_mark" -> this.useSniperMark(player, state);
                    case "boxer_rapid_jab" -> this.useBoxerRapidJab(player, state);
                    case "knight_rally" -> this.useKnightRally(player, state);
                    case "tank_ground_slam" -> this.useTankGroundSlam(player, state);
                    case "assassin_venom_edge" -> this.armAssassinVenom(player);
                    case "archer_pinning_shot" -> this.armArcherPinningShot(player);
                    case "sniper_focus_shot" -> this.activateSniperFocusMode(player);
                    case "boxer_barrage" -> this.useBoxerBarrage(player, state);
                    case "demoman_dynamite" -> this.useDemomanDynamite(player, state);
                    case "demoman_primary_bomb" -> this.useDemomanPrimaryBomb(player, state);
                    case "demoman_blast_mine" -> this.placeDemomanMine(player, state);
                    default -> false;
                };
                if (used && !skill.equals("archer_pinning_shot")) {
                    this.startSkillCooldown(player, state, skill, triggerMaterial);
                }
            }
        }
    }

    private void startSkillCooldown(Player player, MatchState state, String skill, Material triggerMaterial) {
        int cooldown = this.cooldownSeconds(skill);
        long readyAt = System.currentTimeMillis() + cooldown * 1000L;
        state.abilityCooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(skill, readyAt);
        player.setCooldown(triggerMaterial, cooldown * 20);
    }

    long reduceSkillCooldown(Player player, MatchState state, String skill, long reductionMillis) {
        if (player != null && state != null && skill != null && reductionMillis > 0L) {
            UUID playerId = player.getUniqueId();
            Map<String, Long> cooldowns = state.abilityCooldowns.get(playerId);
            if (cooldowns == null) {
                return 0L;
            }

            long now = System.currentTimeMillis();
            Long readyAtValue = cooldowns.get(skill);

            Material skillMaterial = switch (skill) {
                case "boxer_rapid_jab" -> Material.SLIME_BALL;
                case "demoman_dynamite" -> Material.FIRE_CHARGE;
                case "demoman_primary_bomb" -> Material.TNT_MINECART;
                case "demoman_blast_mine" -> Material.TNT;
                default -> Material.STICK;
            };
            if (readyAtValue != null && readyAtValue > now) {
                long oldRemaining = readyAtValue - now;
                long appliedReduction = Math.min(reductionMillis, oldRemaining);
                long newReadyAt = readyAtValue - appliedReduction;
                if (newReadyAt <= now) {
                    cooldowns.remove(skill);
                    player.setCooldown(skillMaterial, 0);
                } else {
                    cooldowns.put(skill, newReadyAt);
                    int remainingTicks = (int)Math.min(2147483647L, Math.max(1L, (newReadyAt - now + 49L) / 50L));
                    player.setCooldown(skillMaterial, remainingTicks);
                }

                if (cooldowns.isEmpty()) {
                    state.abilityCooldowns.remove(playerId);
                }

                return appliedReduction;
            } else {
                cooldowns.remove(skill);
                if (cooldowns.isEmpty()) {
                    state.abilityCooldowns.remove(playerId);
                }

                return 0L;
            }
        } else {
            return 0L;
        }
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
        MatchState state = this.activeTdmMatch(player);
        if (state == null) {
            return false;
        }

        RayTraceResult result = player.getWorld()
            .rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                4.2,
                0.85,
                entity -> entity instanceof Player targetx && this.isEnemy(player, targetx, state)
            );
        if (result != null && result.getHitEntity() instanceof Player target && player.hasLineOfSight(target)) {
            target.damage(4.8, player);
            if (target.isValid() && !target.isDead()) {
                Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
                if (push.lengthSquared() > 0.001) {
                    target.setVelocity(target.getVelocity().add(push.normalize().multiply(0.38).setY(0.14)));
                }
            }

            target.getWorld()
                .spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0.0, 1.0, 0.0), 4, 0.25, 0.3, 0.25, 0.0);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9F, 1.15F);
            player.sendActionBar(ChatColor.RED + "POWER STRIKE!");
            return true;
        } else {
            player.sendMessage(ChatColor.YELLOW + "Power Strike harus diarahkan ke lawan dalam jarak 4,2 blok.");
            return false;
        }
    }

    private boolean useTankEndurance(Player player, int duration) {
        player.addPotionEffect(
            new PotionEffect(PotionEffectType.RESISTANCE, duration * 20, 2, false, false, true), true
        );
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration * 20, 0, false, false, true), true);
        player.getWorld()
            .spawnParticle(
                Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0, 1.0, 0.0), 24, 0.55, 0.8, 0.55, 0.08
            );
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.8F, 0.75F);
        player.sendMessage(ChatColor.GOLD + "Endurance aktif: tubuhmu sangat tahan damage, tetapi gerak melambat.");
        return true;
    }

    private boolean useAssassinBurst(Player player, int duration) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration * 20, 2, false, false, true), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration * 20, 1, false, false, true), true);
        player.addPotionEffect(
            new PotionEffect(PotionEffectType.STRENGTH, Math.min(duration, 2) * 20, 1, false, false, true), true
        );
        Bukkit.getScheduler()
            .runTaskLater(
                this.plugin,
                () -> {
                    if (player.isOnline()) {
                        MatchState active = this.activeTdmMatch(player);
                        if (active != null
                            && active.running
                            && "assassin"
                                .equalsIgnoreCase(active.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) {
                            player.addPotionEffect(
                                new PotionEffect(PotionEffectType.SPEED, 72000, 1, false, false, true), true
                            );
                            player.addPotionEffect(
                                new PotionEffect(PotionEffectType.HASTE, 72000, 0, false, false, true), true
                            );
                        }
                    }
                },
                duration * 20L + 1L
            );
        player.getWorld()
            .spawnParticle(Particle.FLAME, player.getLocation().add(0.0, 1.0, 0.0), 16, 0.42, 0.5, 0.42, 0.025);
        player.getWorld()
            .spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0.0, 1.0, 0.0), 3, 0.45, 0.35, 0.45, 0.0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 1.6F);
        player.sendMessage(ChatColor.DARK_RED + "BURST! Serangan dan gerakanmu meningkat untuk sesaat.");
        return true;
    }

    private boolean startVanish(Player player, int duration) {
        if (this.vanishedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Kamu sedang menghilang.");
            return false;
        } else {
            return this.startVanishTicks(
                player,
                Math.max(1, duration) * 20,
                ChatColor.DARK_GREEN
                    + "Smoke Bomb: kamu dan perlengkapan yang terlihat menghilang selama "
                    + duration
                    + " detik."
            );
        }
    }

    boolean startPassiveVanish(Player player, int durationTicks) {
        return player != null
            && player.isOnline()
            && durationTicks > 0
            && !this.vanishedPlayers.contains(player.getUniqueId())
            && this.startVanishTicks(player, durationTicks, null);
    }

    boolean isVanished(Player player) {
        return player != null && this.vanishedPlayers.contains(player.getUniqueId());
    }

    void endPassiveVanish(Player player) {
        if (player != null) {
            this.endVanish(player);
        }
    }

    private boolean startVanishTicks(Player player, int durationTicks, String message) {
        UUID id = player.getUniqueId();
        if (this.vanishedPlayers.contains(id)) {
            return false;
        }

        this.vanishedPlayers.add(id);
        player.addPotionEffect(
            new PotionEffect(PotionEffectType.INVISIBILITY, durationTicks, 0, false, false, false), true
        );

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                viewer.hidePlayer(this.plugin, player);
            }
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7F, 1.5F);
        player.getWorld()
            .spawnParticle(Particle.SMOKE, player.getLocation().add(0.0, 1.0, 0.0), 24, 0.45, 0.65, 0.45, 0.02);
        if (message != null && !message.isBlank()) {
            player.sendMessage(message);
        }

        BukkitTask oldRefresh = this.vanishTasks.remove(id);
        if (oldRefresh != null) {
            oldRefresh.cancel();
        }

        BukkitTask oldExpiry = this.vanishExpiryTasks.remove(id);
        if (oldExpiry != null) {
            oldExpiry.cancel();
        }

        BukkitTask refreshTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (player.isOnline() && this.vanishedPlayers.contains(id)) {
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (!viewer.equals(player)) {
                        viewer.hidePlayer(this.plugin, player);
                    }
                }
            } else {
                this.endVanish(player);
            }
        }, 10L, 10L);
        this.vanishTasks.put(id, refreshTask);
        BukkitTask expiryTask = Bukkit.getScheduler()
            .runTaskLater(this.plugin, () -> this.endVanish(player), durationTicks);
        this.vanishExpiryTasks.put(id, expiryTask);
        return true;
    }

    private void endVanish(Player player) {
        UUID id = player.getUniqueId();
        BukkitTask refreshTask = this.vanishTasks.remove(id);
        if (refreshTask != null && !refreshTask.isCancelled()) {
            refreshTask.cancel();
        }

        BukkitTask expiryTask = this.vanishExpiryTasks.remove(id);
        if (expiryTask != null && !expiryTask.isCancelled()) {
            expiryTask.cancel();
        }

        if (this.vanishedPlayers.remove(id)) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            if (player.isOnline()) {
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (!viewer.equals(player)) {
                        viewer.showPlayer(this.plugin, player);
                    }
                }

                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5F, 0.8F);
            }
        }
    }

    void resetCooldownsForRespawn(Player player, MatchState state) {
        if (player != null && state != null) {
            UUID id = player.getUniqueId();
            state.abilityCooldowns.remove(id);
            String className = state.selectedClasses.getOrDefault(id, "knight").toLowerCase();
            player.setCooldown(this.primarySkillMaterial(className), 0);
            player.setCooldown(this.secondarySkillMaterial(className), 0);
            if ("demoman".equals(className)) {
                player.setCooldown(Material.TNT_MINECART, 0);
                player.setCooldown(Material.TNT, 0);
            }

            this.archerPinningReady.remove(id);
            this.assassinVenomReady.remove(id);
            this.sniperFocusActive.remove(id);
            ClassAbilityListener.MarkInfo markOnPlayer = this.markedTargets.get(id);
            if (markOnPlayer != null) {
                this.clearSniperMark(id, markOnPlayer, player);
            }

            this.clearSniperMarkOwnedBy(id);
        }
    }

    void cancelFor(Player player) {
        if (player != null) {
            UUID id = player.getUniqueId();
            this.endVanish(player);
            ClassAbilityListener.MarkInfo markOnPlayer = this.markedTargets.get(id);
            if (markOnPlayer != null) {
                this.clearSniperMark(id, markOnPlayer, player);
            }

            this.clearSniperMarkOwnedBy(id);
            this.sniperTargetsByOwner.entrySet().removeIf(entry -> {
                if (!entry.getValue().equals(id)) {
                    return false;
                }

                this.sniperAimLocked.remove(entry.getKey());
                return true;
            });
            this.archerPinningReady.remove(id);
            this.assassinVenomReady.remove(id);
            this.assassinOffhandReadyAtNanos.remove(id);
            this.assassinOffhandInputLastReceivedNanos.remove(id);
            this.sniperFocusActive.remove(id);
            this.ninjaSnowballNextRestock.remove(id);
            this.boxerHeavyJabLastInteractionNanos.remove(id);
            this.cleanupDemomanFor(id);
            this.boxerSkillDamage.remove(id);
        }
    }

    private void clearSniperMarkOwnedBy(UUID ownerId) {
        UUID targetId = this.sniperTargetsByOwner.remove(ownerId);
        this.sniperAimLocked.remove(ownerId);
        if (targetId != null) {
            ClassAbilityListener.MarkInfo mark = this.markedTargets.get(targetId);
            if (mark != null && ownerId.equals(mark.ownerId())) {
                this.clearSniperMark(targetId, mark);
            }
        }
    }

    private void clearSniperMark(UUID targetId, ClassAbilityListener.MarkInfo mark) {
        this.clearSniperMark(targetId, mark, null);
    }

    private void clearSniperMark(UUID targetId, ClassAbilityListener.MarkInfo mark, Player targetOverride) {
        if (targetId != null && mark != null) {
            boolean removedMark = this.markedTargets.remove(targetId, mark);
            boolean removedOwnerMapping = this.sniperTargetsByOwner.remove(mark.ownerId(), targetId);
            if (removedOwnerMapping) {
                this.sniperAimLocked.remove(mark.ownerId());
            }

            if (removedMark && !this.markedTargets.containsKey(targetId)) {
                Player target = targetOverride != null ? targetOverride : Bukkit.getPlayer(targetId);
                if (target != null) {
                    target.removePotionEffect(PotionEffectType.GLOWING);
                }
            }
        }
    }

    void cleanupAll() {
        this.skillInputLastReceivedNanos.clear();
        this.sniperAimLockInputLastReceivedNanos.clear();
        if (this.ninjaSnowballRestockTask != null && !this.ninjaSnowballRestockTask.isCancelled()) {
            this.ninjaSnowballRestockTask.cancel();
        }

        if (this.demomanExplosivesTask != null && !this.demomanExplosivesTask.isCancelled()) {
            this.demomanExplosivesTask.cancel();
        }

        if (this.sniperAimLockTask != null && !this.sniperAimLockTask.isCancelled()) {
            this.sniperAimLockTask.cancel();
        }

        for (BukkitTask task : new ArrayList<>(this.demomanBurstTasks.values())) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }

        this.demomanBurstTasks.clear();

        for (Entry<UUID, ClassAbilityListener.MarkInfo> entry : new ArrayList<>(this.markedTargets.entrySet())) {
            this.clearSniperMark(entry.getKey(), entry.getValue());
        }

        for (UUID id : new HashSet<>(this.vanishedPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                this.endVanish(player);
            } else {
                BukkitTask task = this.vanishTasks.remove(id);
                if (task != null) {
                    task.cancel();
                }

                this.vanishedPlayers.remove(id);
            }
        }

        for (BukkitTask task : new ArrayList<>(this.vanishTasks.values())) {
            task.cancel();
        }

        for (BukkitTask task : new ArrayList<>(this.vanishExpiryTasks.values())) {
            task.cancel();
        }

        this.vanishTasks.clear();
        this.vanishExpiryTasks.clear();
        this.vanishedPlayers.clear();
        this.sniperFocusActive.clear();
        this.markedTargets.clear();
        this.sniperTargetsByOwner.clear();
        this.sniperAimLocked.clear();
        this.boxerHeavyJabLastInteractionNanos.clear();
        this.assassinOffhandReadyAtNanos.clear();
        this.assassinOffhandInputLastReceivedNanos.clear();
        this.assassinVenomReady.clear();
        this.ninjaSnowballNextRestock.clear();
        this.focusedProjectiles.clear();
        this.pinningProjectiles.clear();

        for (UUID ownerId : new HashSet<>(this.demomanMines.keySet())) {
            this.removeDemomanMine(ownerId);
        }

        for (ClassAbilityListener.DemomanDynamite explosive : this.demomanDynamite.values()) {
            if (explosive.projectile().isValid()) {
                explosive.projectile().remove();
            }
        }

        this.demomanDynamite.clear();
        this.boxerSkillDamage.clear();
        this.plugin
            .getServer()
            .getMessenger()
            .unregisterOutgoingPluginChannel(this.plugin, "arenasystem:assassin_offhand_cooldown");
    }

    private boolean useDemomanDynamite(Player player, final MatchState state) {
        final UUID ownerId = player.getUniqueId();
        if (this.demomanBurstTasks.containsKey(ownerId)) {
            return false;
        }

        final Vector forward = player.getEyeLocation().getDirection().normalize();
        final Vector side = new Vector<>(-forward.getZ(), 0.0, forward.getX());
        if (side.lengthSquared() > 1.0E-4) {
            side.normalize();
        } else {
            side.setX(1.0);
        }

        BukkitTask task = (new BukkitRunnable()        // $VF: Compiled from ClassAbilityListener.java
     {
                private int launched;

                @Override
                public void run() {
                    Player currentOwner = Bukkit.getPlayer(ownerId);
                    if (currentOwner != null
                        && currentOwner.isOnline()
                        && !currentOwner.isDead()
                        && ClassAbilityListener.this.activeTdmMatch(currentOwner) == state
                        && "demoman".equalsIgnoreCase(state.selectedClasses.getOrDefault(ownerId, "knight"))) {
                        ClassAbilityListener.this.launchDemomanDynamiteProjectile(
                            currentOwner,
                            state,
                            forward,
                            side,
                            this.launched,
                            3.5,
                            4.8,
                            ClassAbilityListener.DemomanBlastType.DYNAMITE_BURST
                        );
                        this.launched++;
                        if (this.launched >= 3) {
                            ClassAbilityListener.this.demomanBurstTasks.remove(ownerId);
                            this.cancel();
                        }
                    } else {
                        ClassAbilityListener.this.cleanupDemomanFor(ownerId);
                        this.cancel();
                    }
                }
            })
            .runTaskTimer(this.plugin, 1L, 4L);
        this.demomanBurstTasks.put(ownerId, task);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0F, 1.1F);
        player.getWorld()
            .spawnParticle(
                Particle.FLAME, player.getEyeLocation().add(forward.clone().multiply(0.6)), 18, 0.18, 0.12, 0.18, 0.035
            );
        player.sendMessage(ChatColor.RED + "Dynamite Burst! Tiga bom diluncurkan satu per satu dengan jeda singkat.");
        return true;
    }

    private boolean useDemomanPrimaryBomb(Player player, MatchState state) {
        if (state != null
            && this.activeTdmMatch(player) == state
            && "demoman".equalsIgnoreCase(state.selectedClasses.getOrDefault(player.getUniqueId(), "knight"))) {
            Vector forward = player.getEyeLocation().getDirection().normalize();
            Vector side = new Vector<>(-forward.getZ(), 0.0, forward.getX());
            if (side.lengthSquared() > 1.0E-4) {
                side.normalize();
            } else {
                side.setX(1.0);
            }

            this.launchDemomanDynamiteProjectile(
                player, state, forward, side, 1, 3.0, 3.4, ClassAbilityListener.DemomanBlastType.PRIMARY_BOMB
            );
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.8F, 1.35F);
            player.getWorld()
                .spawnParticle(
                    Particle.FLAME,
                    player.getEyeLocation().add(forward.clone().multiply(0.55)),
                    7,
                    0.1,
                    0.08,
                    0.1,
                    0.015
                );
            player.sendActionBar(ChatColor.RED + "POCKET BOMB!");
            return true;
        } else {
            return false;
        }
    }

    private void launchDemomanDynamiteProjectile(
        Player player,
        MatchState state,
        Vector forward,
        Vector side,
        int shotIndex,
        double radius,
        double maximumDamage,
        ClassAbilityListener.DemomanBlastType blastType
    ) {
        int spreadIndex = shotIndex - 1;
        Snowball projectile = player.launchProjectile(Snowball.class);
        projectile.setItem(
            new ItemStack(
                blastType == ClassAbilityListener.DemomanBlastType.PRIMARY_BOMB ? Material.TNT : Material.FIRE_CHARGE
            )
        );
        Vector velocity = forward.clone()
            .multiply(1.15)
            .add(side.clone().multiply(spreadIndex * 0.2))
            .add(new Vector<>(0.0, spreadIndex == 0 ? 0.04 : 0.1, 0.0));
        projectile.setVelocity(velocity);
        projectile.setGravity(true);
        this.demomanDynamite
            .put(
                projectile.getUniqueId(),
                new ClassAbilityListener.DemomanDynamite(
                    player.getUniqueId(),
                    state.arenaName,
                    projectile,
                    System.currentTimeMillis() + 1350L,
                    radius,
                    maximumDamage,
                    blastType
                )
            );
        projectile.getWorld().spawnParticle(Particle.FLAME, projectile.getLocation(), 5, 0.06, 0.06, 0.06, 0.01);
    }

    private boolean useShadowStep(Player player, int maxDistance) {
        ArenaData arena = this.plugin.findArena(player.getLocation());
        if (arena == null) {
            return false;
        }

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
                if (feet.isPassable()
                    && head.isPassable()
                    && !feet.isLiquid()
                    && !head.isLiquid()
                    && floor.getType().isSolid()
                    && !floor.isLiquid()
                    && !this.isHazardousFloor(floor.getType())
                    && Math.abs(blockX - arena.centerX) <= arena.radius
                    && Math.abs(blockZ - arena.centerZ) <= arena.radius) {
                    destination = new Location(
                        world, blockX + 0.5, feetY, blockZ + 0.5, origin.getYaw(), origin.getPitch()
                    );
                    break;
                }
            }
        }

        if (destination == null) {
            player.sendMessage(ChatColor.YELLOW + "Shadow Step gagal: tidak ada tempat mendarat yang aman di depanmu.");
            return false;
        }

        if (!player.teleport(destination)) {
            return false;
        }

        player.setFallDistance(0.0F);
        player.setVelocity(new Vector<>(0, 0, 0));
        player.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9F, 1.25F);
        player.getWorld()
            .spawnParticle(Particle.PORTAL, destination.clone().add(0.0, 1.0, 0.0), 28, 0.35, 0.55, 0.35, 0.1);
        player.sendMessage(ChatColor.DARK_GREEN + "Shadow Step berhasil: kamu berpindah tanpa Ender Pearl.");
        return true;
    }

    private boolean isHazardousFloor(Material material) {
        String name = material.name();
        return name.contains("MAGMA")
            || name.contains("CACTUS")
            || name.contains("CAMPFIRE")
            || name.contains("SWEET_BERRY")
            || material == Material.FIRE
            || material == Material.SOUL_FIRE;
    }

    private boolean useArcherVolley(Player player) {
        int arrows = player.getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum();
        if (arrows < 3) {
            player.sendMessage(ChatColor.YELLOW + "Volley membutuhkan minimal 3 panah.");
            return false;
        }

        player.getInventory().removeItem(new ItemStack(Material.ARROW, 3));
        Vector base = player.getEyeLocation().getDirection().normalize();
        double[] angles = new double[]{-0.1, 0.0, 0.1};

        for (double angle : angles) {
            Arrow arrow = player.launchProjectile(Arrow.class);
            arrow.setVelocity(base.clone().rotateAroundY(angle).multiply(2.6));
            arrow.getWorld().spawnParticle(Particle.CRIT, arrow.getLocation(), 5, 0.05, 0.05, 0.05, 0.015);
            arrow.setCritical(true);
            arrow.setPickupStatus(PickupStatus.DISALLOWED);
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (arrow.isValid()) {
                    arrow.remove();
                }
            }, 100L);
        }

        player.getWorld().spawnParticle(Particle.CRIT, player.getEyeLocation(), 14, 0.18, 0.18, 0.18, 0.04);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0F, 1.2F);
        player.sendMessage(ChatColor.GREEN + "Volley! Tiga panah dilepaskan sekaligus.");
        return true;
    }

    private void tickSniperAimLock() {
        long now = System.currentTimeMillis();

        for (Entry<UUID, ClassAbilityListener.MarkInfo> entry : new ArrayList<>(this.markedTargets.entrySet())) {
            UUID targetId = entry.getKey();
            ClassAbilityListener.MarkInfo mark = entry.getValue();
            Player target = Bukkit.getPlayer(targetId);
            Player owner = Bukkit.getPlayer(mark.ownerId());
            if (!this.isValidSniperMark(owner, target, mark, now)) {
                this.clearSniperMark(targetId, mark);
                if (owner != null && owner.isOnline()) {
                    owner.sendActionBar(
                        mark.expiresAt() <= now
                            ? ChatColor.GRAY + "MARK EXPIRED"
                            : ChatColor.GRAY + "MARK DIBERSIHKAN — target tidak lagi valid"
                    );
                }
            }
        }

        for (UUID ownerId : new HashSet<>(this.sniperAimLocked)) {
            Player owner = Bukkit.getPlayer(ownerId);
            UUID targetId = this.sniperTargetsByOwner.get(ownerId);
            Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
            ClassAbilityListener.MarkInfo mark = targetId == null ? null : this.markedTargets.get(targetId);
            if (!this.isValidSniperMark(owner, target, mark, now)) {
                if (mark != null && targetId != null) {
                    this.clearSniperMark(targetId, mark);
                } else {
                    this.sniperAimLocked.remove(ownerId);
                }
            } else {
                Vector direction = target.getEyeLocation().toVector().subtract(owner.getEyeLocation().toVector());
                if (direction.lengthSquared() > 1.0E-4) {
                    Location view = owner.getLocation().clone();
                    view.setDirection(direction);
                    owner.setRotation(view.getYaw(), view.getPitch());
                }
            }
        }
    }

    private boolean hasValidSniperMark(Player owner) {
        if (owner == null) {
            return false;
        }

        UUID targetId = this.sniperTargetsByOwner.get(owner.getUniqueId());
        if (targetId == null) {
            return false;
        }

        ClassAbilityListener.MarkInfo mark = this.markedTargets.get(targetId);
        return this.isValidSniperMark(owner, Bukkit.getPlayer(targetId), mark, System.currentTimeMillis());
    }

    private boolean isValidSniperMark(Player owner, Player target, ClassAbilityListener.MarkInfo mark, long now) {
        if (owner != null
            && target != null
            && mark != null
            && owner.isOnline()
            && target.isOnline()
            && !owner.isDead()
            && !target.isDead()
            && owner.isValid()
            && target.isValid()
            && owner.getGameMode() == GameMode.SURVIVAL
            && target.getGameMode() == GameMode.SURVIVAL
            && mark.expiresAt() > now
            && owner.getUniqueId().equals(mark.ownerId())
            && target.getUniqueId().equals(this.sniperTargetsByOwner.get(owner.getUniqueId()))
            && owner.getWorld().equals(target.getWorld())) {
            MatchState ownerState = this.activeTdmMatch(owner);
            MatchState targetState = this.activeTdmMatch(target);
            if (ownerState != null
                && ownerState == targetState
                && mark.arenaName().equalsIgnoreCase(ownerState.arenaName)
                && "sniper".equalsIgnoreCase(ownerState.selectedClasses.getOrDefault(owner.getUniqueId(), "knight"))
                && this.isEnemy(owner, target, ownerState)) {
                ArenaData ownerArena = this.plugin.findArenaFootprint(owner.getLocation());
                ArenaData targetArena = this.plugin.findArenaFootprint(target.getLocation());
                return ownerArena != null
                    && targetArena != null
                    && ownerArena.name.equalsIgnoreCase(mark.arenaName())
                    && targetArena.name.equalsIgnoreCase(mark.arenaName());
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean useSniperMark(Player player, MatchState state) {
        RayTraceResult result = player.getWorld()
            .rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                32.0,
                0.65,
                entity -> entity instanceof Player targetx
                    && !targetx.isDead()
                    && targetx.getGameMode() == GameMode.SURVIVAL
                    && this.isEnemy(player, targetx, state)
            );
        if (result != null && result.getHitEntity() instanceof Player target && player.hasLineOfSight(target)) {
            long expiresAt = System.currentTimeMillis() + 8000L;
            UUID ownerId = player.getUniqueId();
            UUID targetId = target.getUniqueId();
            UUID previousTarget = this.sniperTargetsByOwner.get(ownerId);
            if (previousTarget != null && !previousTarget.equals(targetId)) {
                ClassAbilityListener.MarkInfo oldOwnedMark = this.markedTargets.get(previousTarget);
                if (oldOwnedMark != null && ownerId.equals(oldOwnedMark.ownerId())) {
                    this.clearSniperMark(previousTarget, oldOwnedMark);
                } else {
                    this.sniperTargetsByOwner.remove(ownerId, previousTarget);
                    this.sniperAimLocked.remove(ownerId);
                }
            }

            ClassAbilityListener.MarkInfo replacedMark = this.markedTargets.get(targetId);
            if (replacedMark != null && !ownerId.equals(replacedMark.ownerId())) {
                this.clearSniperMark(targetId, replacedMark);
                Player previousOwner = Bukkit.getPlayer(replacedMark.ownerId());
                if (previousOwner != null) {
                    previousOwner.sendActionBar(ChatColor.GRAY + "MARK DIGANTI OLEH SNIPER LAIN");
                }
            }

            this.markedTargets.put(targetId, new ClassAbilityListener.MarkInfo(ownerId, state.arenaName, expiresAt, 0));
            this.sniperTargetsByOwner.put(ownerId, targetId);
            this.sniperAimLocked.remove(ownerId);
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 160, 0, false, false, true), true);
            target.getWorld()
                .spawnParticle(
                    Particle.ELECTRIC_SPARK, target.getLocation().add(0.0, 1.0, 0.0), 28, 0.42, 0.7, 0.42, 0.04
                );
            target.getWorld()
                .spawnParticle(Particle.END_ROD, target.getLocation().add(0.0, 1.0, 0.0), 10, 0.3, 0.6, 0.3, 0.015);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7F, 1.4F);
            player.sendMessage(
                ChatColor.AQUA
                    + "Target MARK: "
                    + target.getName()
                    + " selama 8 detik atau sampai 3 panah Sniper mengenainya. Tekan Q untuk mengaktifkan AIM LOCK."
            );
            target.sendMessage(
                ChatColor.RED
                    + "Kamu terkena MARK dari Sniper "
                    + player.getName()
                    + "! Mark berakhir setelah 3 panah mengenaimu atau 8 detik."
            );
            return true;
        } else {
            player.sendMessage(ChatColor.YELLOW + "Tidak ada lawan yang terlihat dalam jarak 32 blok.");
            return false;
        }
    }

    private boolean useKnightRally(Player player, MatchState state) {
        int affected = 0;
        String ownTeam = state.teams.get(player.getUniqueId());
        if (ownTeam == null) {
            return false;
        }

        for (Player teammate : player.getWorld().getPlayers()) {
            String teammateTeam = state.teams.get(teammate.getUniqueId());
            if (teammateTeam != null
                && ownTeam.equals(teammateTeam)
                && !(teammate.getLocation().distanceSquared(player.getLocation()) > 64.0)) {
                teammate.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 0, false, true, true), true);
                teammate.getWorld()
                    .spawnParticle(
                        Particle.HAPPY_VILLAGER, teammate.getLocation().add(0.0, 1.0, 0.0), 12, 0.4, 0.55, 0.4, 0.02
                    );
                if (!teammate.equals(player)) {
                    teammate.sendMessage(
                        ChatColor.AQUA + "Knight " + player.getName() + " mengaktifkan Rally: Resistance I!"
                    );
                }

                affected++;
            }
        }

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.9F, 1.2F);
        player.sendMessage(
            ChatColor.AQUA + "Rally aktif: " + affected + " anggota tim mendapat Resistance I selama 4 detik."
        );
        return true;
    }

    private boolean useTankGroundSlam(Player player, MatchState state) {
        int hit = 0;

        for (Player target : player.getWorld().getPlayers()) {
            if (this.isEnemy(player, target, state)
                && !(target.getLocation().distanceSquared(player.getLocation()) > 25.0)) {
                if (this.vanishedPlayers.contains(target.getUniqueId())) {
                    this.endVanish(target);
                }

                target.damage(3.2, player);
                Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
                if (push.lengthSquared() > 0.001) {
                    push.normalize().multiply(0.68).setY(0.78);
                } else {
                    push.setY(0.28);
                }

                target.setVelocity(push);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true, true), true);
                target.sendMessage(ChatColor.GOLD + "Kamu terkena Ground Slam milik Tank " + player.getName() + "!");
                hit++;
            }
        }

        player.getWorld()
            .spawnParticle(
                Particle.BLOCK, player.getLocation(), 35, 1.5, 0.15, 1.5, 0.15, Material.STONE.createBlockData()
            );
        player.getWorld()
            .spawnParticle(Particle.CLOUD, player.getLocation().add(0.0, 0.15, 0.0), 18, 1.2, 0.12, 1.2, 0.06);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 0.65F);
        player.sendMessage(ChatColor.GOLD + "Ground Slam! " + hit + " lawan terkena gelombang kejut.");
        return true;
    }

    private boolean armAssassinVenom(Player player) {
        this.assassinVenomReady.put(player.getUniqueId(), System.currentTimeMillis() + 8000L);
        player.getWorld()
            .spawnParticle(Particle.WITCH, player.getLocation().add(0.0, 1.0, 0.0), 18, 0.3, 0.45, 0.3, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 0.8F, 1.5F);
        player.sendMessage(
            ChatColor.DARK_GREEN + "Venom Edge siap: serangan melee berikutnya memberi Poison II. Berlaku 8 detik."
        );
        return true;
    }

    private boolean armArcherPinningShot(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long pendingUntil = this.archerPinningReady.get(playerId);
        if (pendingUntil != null) {
            if (pendingUntil > now) {
                player.sendMessage(
                    ChatColor.YELLOW + "Pinning Shot sudah siap. Lepaskan satu panah dengan bow terlebih dahulu."
                );
                return false;
            }

            if (this.archerPinningReady.remove(playerId, pendingUntil)) {
                MatchState state = this.activeTdmMatch(player);
                if (state != null) {
                    this.startSkillCooldown(player, state, "archer_pinning_shot", Material.SPECTRAL_ARROW);
                }

                player.sendMessage(ChatColor.YELLOW + "Waktu persiapan Pinning Shot habis; cooldown dimulai.");
                return false;
            }
        }

        int arrows = player.getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum();
        if (arrows < 1) {
            player.sendMessage(ChatColor.YELLOW + "Pinning Shot membutuhkan minimal 1 panah.");
            return false;
        } else {
            long armedUntil = now + 10000L;
            this.archerPinningReady.put(playerId, armedUntil);
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getEyeLocation(), 12, 0.18, 0.18, 0.18, 0.015);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.8F, 1.3F);
            int cooldown = this.cooldownSeconds("archer_pinning_shot");
            player.sendMessage(
                ChatColor.GREEN
                    + "Pinning Shot siap: lepaskan satu panah dalam 10 detik. Cooldown "
                    + cooldown
                    + " detik dimulai setelah panah dilepaskan."
            );
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (this.archerPinningReady.remove(playerId, armedUntil)) {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != null) {
                        MatchState active = this.activeTdmMatch(online);
                        if (active != null) {
                            this.startSkillCooldown(online, active, "archer_pinning_shot", Material.SPECTRAL_ARROW);
                            online.sendMessage(ChatColor.YELLOW + "Pinning Shot kedaluwarsa; cooldown dimulai.");
                        }
                    }
                }
            }, 200L);
            return true;
        }
    }

    private boolean activateSniperFocusMode(Player player) {
        long expiresAt = System.currentTimeMillis() + 8000L;
        this.sniperFocusActive.put(player.getUniqueId(), expiresAt);
        player.getWorld()
            .spawnParticle(
                Particle.END_ROD,
                player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.45)),
                18,
                0.18,
                0.2,
                0.18,
                0.025
            );
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.7F);
        player.sendMessage(
            ChatColor.AQUA + "Focus Mode aktif selama 8 detik: semua panah mendapat +15% damage dan kecepatan 6.8!"
        );
        return true;
    }

    private boolean isEnemy(Player attacker, Player target, MatchState state) {
        if (!target.equals(attacker) && target.isOnline()) {
            String attackerTeam = state.teams.get(attacker.getUniqueId());
            String targetTeam = state.teams.get(target.getUniqueId());
            return attackerTeam != null && targetTeam != null && !attackerTeam.equals(targetTeam);
        } else {
            return false;
        }
    }

    private int cooldownSeconds(String skill) {
        return Math.max(
            1,
            Math.min(
                3600,
                this.plugin
                    .getConfig()
                    .getInt("class-abilities." + skill + ".cooldown-seconds", this.defaultCooldown(skill))
            )
        );
    }

    private int durationSeconds(String skill) {
        return Math.max(
            1,
            Math.min(
                30,
                this.plugin
                    .getConfig()
                    .getInt("class-abilities." + skill + ".duration-seconds", this.defaultDuration(skill))
            )
        );
    }

    private int distanceBlocks(String skill) {
        return Math.max(
            2, Math.min(12, this.plugin.getConfig().getInt("class-abilities." + skill + ".distance-blocks", 8))
        );
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

    // $VF: Compiled from ClassAbilityListener.java
    private enum DemomanBlastType {
        DYNAMITE_BURST,
        PRIMARY_BOMB,
        BLAST_MINE;
    }

    // $VF: Compiled from ClassAbilityListener.java
    private record DemomanDynamite(
        UUID ownerId,
        String arenaName,
        Snowball projectile,
        long fuseAtMillis,
        double radius,
        double maximumDamage,
        ClassAbilityListener.DemomanBlastType blastType
    ) {
    }

    // $VF: Compiled from ClassAbilityListener.java
    private record DemomanMine(UUID ownerId, String arenaName, Location location, Creeper marker, long expiresAtMillis) {
    }

    // $VF: Compiled from ClassAbilityListener.java
    private record MarkInfo(UUID ownerId, String arenaName, long expiresAt, int arrowsHit) {
    }
}
