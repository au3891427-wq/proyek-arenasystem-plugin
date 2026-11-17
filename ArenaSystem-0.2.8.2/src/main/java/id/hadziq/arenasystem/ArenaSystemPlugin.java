package id.hadziq.arenasystem;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.FluidCollisionMode;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

/**
 * Bootstrap plugin + "mesin" arena/match. Seluruh wiring Bukkit (command
 * executor, listener) didaftarkan di {@link #onEnable()} lalu didelegasikan
 * ke kelas terpisah ({@link ArenaCommandExecutor} dan beberapa {@code *Listener}).
 * Method-method di bawah ini tetap berada di sini (bukan di-private-kan
 * penuh lagi, melainkan package-private) karena saling terkait erat secara
 * state (arena, match, snapshot); lihat REFACTOR_NOTES.md untuk peta lengkap
 * dan rencana pemisahan lebih lanjut (ArenaEngine / MatchEngine).
 */
public final class ArenaSystemPlugin extends JavaPlugin implements Listener {
    static final int TDM_RADIUS = 40;
    /** Relic Rush lebih lebar dari TDM: menambah jarak tempuh sanctum-ke-altar dan ruang untuk cover tambahan. */
    static final int RELIC_RADIUS = 56;
    static final int SCAN_COLUMNS_PER_TICK = 256;
    static final int BUILD_BLOCKS_PER_TICK = 300;
    static final int RESTORE_BLOCKS_PER_TICK = 300;
    static final int DEFAULT_MATCH_SECONDS = 300;
    static final int TDM_TARGET_SCORE = 20;
    static final int RELIC_TARGET_SCORE = 3;
    static final int TDM_SPAWN_OFFSET = 27;

    final Map<String, ArenaData> arenas = new HashMap<>();
    final Map<String, ArenaData> incompleteArenas = new HashMap<>();
    final Map<String, MatchState> matches = new HashMap<>();
    final Map<UUID, PlayerSnapshot> pendingPlayerRestores = new HashMap<>();
    final Set<String> buildingArenas = new HashSet<>();
    SnapshotStore snapshots;
    NamespacedKey relicKey;
    NamespacedKey classSelectorKey;
    NamespacedKey classSkillKey;
    NamespacedKey randomStuffKey;
    NamespacedKey randomStuffDropExpiresKey;
    NamespacedKey randomStuffProjectileKey;
    PlayerArenaListener playerArenaListener;
    ClassAbilityListener classAbilityListener;
    ClassPassiveManager classPassiveManager;
    BukkitTask relicCarrierMovementTask;

    @Override
    public void onEnable() {
        getConfig().addDefault("matches.duration-seconds", DEFAULT_MATCH_SECONDS);
        getConfig().addDefault("matches.preparation-seconds", 30);
        getConfig().addDefault("matches.tdm-target-score", TDM_TARGET_SCORE);
        getConfig().addDefault("matches.relic-target-score", RELIC_TARGET_SCORE);
        getConfig().addDefault("matches.random-stuff-interval-seconds", 30);
        getConfig().addDefault("matches.random-stuff-max-recipients", 6);
        getConfig().addDefault("matches.random-stuff-expire-seconds", 20);
        getConfig().addDefault("matches.random-stuff-ground-expire-seconds", 5);
        // Migrate the old setting while keeping single-player matches enabled by default.
        if (getConfig().contains("matches.allow-solo-test", true)
                && !getConfig().contains("matches.allow-single-player", true)) {
            getConfig().set("matches.allow-single-player",
                    getConfig().getBoolean("matches.allow-solo-test", true));
        }
        getConfig().set("matches.allow-solo-test", null);
        getConfig().addDefault("matches.allow-single-player", true);
        getConfig().addDefault("matches.death-camera-seconds", 3);
        getConfig().addDefault("matches.keep-inventory.tdm", true);
        getConfig().addDefault("matches.keep-inventory.relic_rush", true);
        getConfig().addDefault("terrain.max-adjacent-step", 2);
        getConfig().addDefault("terrain.max-column-adjustment", 12);
        getConfig().addDefault("protection.allow-vegetation-break-by-default", true);
        getConfig().addDefault("class-abilities.knight_guard.cooldown-seconds", 26);
        getConfig().addDefault("class-abilities.knight_guard.duration-seconds", 3);
        getConfig().addDefault("class-abilities.tank_endurance.cooldown-seconds", 45);
        getConfig().addDefault("class-abilities.tank_endurance.duration-seconds", 5);
        getConfig().addDefault("class-abilities.assassin_burst.cooldown-seconds", 30);
        getConfig().addDefault("class-abilities.assassin_burst.duration-seconds", 3);
        getConfig().addDefault("class-abilities.ninja_vanish.cooldown-seconds", 34);
        getConfig().addDefault("class-abilities.ninja_vanish.duration-seconds", 6);
        getConfig().addDefault("class-abilities.ninja_shadow_step.cooldown-seconds", 20);
        getConfig().addDefault("class-abilities.ninja_shadow_step.distance-blocks", 8);
        getConfig().addDefault("class-abilities.archer_volley.cooldown-seconds", 20);
        getConfig().addDefault("class-abilities.sniper_mark.cooldown-seconds", 25);
        getConfig().addDefault("class-abilities.boxer_rapid_jab.cooldown-seconds", 20);
        getConfig().addDefault("class-abilities.boxer_barrage.cooldown-seconds", 28);
        getConfig().addDefault("class-abilities.demoman_dynamite.cooldown-seconds", 10);
        getConfig().addDefault("class-abilities.demoman_primary_bomb.cooldown-seconds", 2);
        getConfig().addDefault("class-abilities.demoman_blast_mine.cooldown-seconds", 20);
        getConfig().addDefault("class-abilities.knight_rally.cooldown-seconds", 32);
        getConfig().addDefault("class-abilities.tank_ground_slam.cooldown-seconds", 30);
        getConfig().addDefault("class-abilities.assassin_venom_edge.cooldown-seconds", 22);
        getConfig().addDefault("class-abilities.archer_pinning_shot.cooldown-seconds", 18);
        getConfig().addDefault("class-abilities.sniper_focus_shot.cooldown-seconds", 25);
        getConfig().options().copyDefaults(true);
        // Migrate only the previous built-in defaults, preserving any operator-customized values.
        migrateLegacyCooldownDefault("class-abilities.boxer_rapid_jab.cooldown-seconds", 16, 20);
        migrateLegacyCooldownDefault("class-abilities.demoman_dynamite.cooldown-seconds", 14, 10);
        migrateLegacyCooldownDefault("class-abilities.demoman_blast_mine.cooldown-seconds", 28, 20);
        saveConfig();

        snapshots = new SnapshotStore(getDataFolder());
        relicKey = new NamespacedKey(this, "arena_relic");
        classSelectorKey = new NamespacedKey(this, "class_selector");
        classSkillKey = new NamespacedKey(this, "class_skill");
        randomStuffKey = new NamespacedKey(this, "random_stuff");
        randomStuffDropExpiresKey = new NamespacedKey(this, "random_stuff_drop_expires_at");
        randomStuffProjectileKey = new NamespacedKey(this, "random_stuff_projectile");
        if (getCommand("arena") != null) getCommand("arena").setExecutor(new ArenaCommandExecutor(this));

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MobProtectionListener(this), this);
        pm.registerEvents(new BlockProtectionListener(this), this);
        playerArenaListener = new PlayerArenaListener(this);
        pm.registerEvents(playerArenaListener, this);
        pm.registerEvents(new RelicListener(this), this);
        pm.registerEvents(this, this);
        pm.registerEvents(new ClassMenuListener(this), this);
        classAbilityListener = new ClassAbilityListener(this);
        pm.registerEvents(classAbilityListener, this);
        classPassiveManager = new ClassPassiveManager(this);
        pm.registerEvents(classPassiveManager, this);

        // Enforce carrier movement server-side every tick: walk speed alone does not
        // remove sprint-jump momentum already present in the player velocity.
        relicCarrierMovementTask = Bukkit.getScheduler().runTaskTimer(
                this, this::enforceRelicCarrierMovement, 1L, 1L);

        loadArenas();
        getLogger().info("ArenaSystem 0.2.8.2 aktif. Arena siap: " + arenas.size());
    }

    private void migrateLegacyCooldownDefault(String path, int previousDefault, int newDefault) {
        if (getConfig().getInt(path, newDefault) == previousDefault) {
            getConfig().set(path, newDefault);
        }
    }

    @Override
    public void onDisable() {
        if (playerArenaListener != null) playerArenaListener.cleanupAll();
        if (relicCarrierMovementTask != null) {
            relicCarrierMovementTask.cancel();
            relicCarrierMovementTask = null;
        }
        if (classAbilityListener != null) classAbilityListener.cleanupAll();
        if (classPassiveManager != null) classPassiveManager.cleanupAll();
        for (MatchState match : new ArrayList<>(matches.values())) {
            cancelMatchTasks(match);
            match.ending = true;
            match.running = false;
            match.preparing = false;
            match.launching = false;
            clearMatchHud(match);
            restorePlayerSnapshots(match);
            ArenaData arena = arenas.get(match.arenaName);
            if (arena != null) {
                Map<BlockPos, String> changes = combinedMatchChanges(match);
                restoreMapImmediately(arena, changes);
                removeRelicItems(arena, match);
            }
        }
        // Snapshots deferred earlier for players who were dead; their scheduled tasks are
        // cancelled by Bukkit now, so finish them synchronously. Offline entries stay in memory
        // only and cannot outlive the plugin (pre-existing limitation).
        for (Map.Entry<UUID, PlayerSnapshot> entry : new ArrayList<>(pendingPlayerRestores.entrySet())) {
            Player online = Bukkit.getPlayer(entry.getKey());
            if (online == null || !online.isOnline()) continue;
            pendingPlayerRestores.remove(entry.getKey());
            restoreSnapshotWithoutScheduler(online, entry.getValue());
        }
        saveConfig();
    }

    /**
     * Restore path that must not use the scheduler (plugin is disabled). A dead player is
     * respawned synchronously first; the snapshot is applied only to a living player.
     * Our own listeners do not run while the plugin is disabled, so no extra respawn logic fires.
     */
    private void restoreSnapshotWithoutScheduler(Player player, PlayerSnapshot snapshot) {
        if (player.isDead()) player.spigot().respawn();
        if (player.isDead()) {
            getLogger().warning("Snapshot " + player.getName()
                    + " tidak dipulihkan: pemain masih mati setelah respawn paksa saat plugin dinonaktifkan.");
            return;
        }
        snapshot.restore(player);
    }

    void loadArenas() {
        var section = getConfig().getConfigurationSection("arenas");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String base = "arenas." + key + ".";
            String worldName = getConfig().getString(base + "world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().warning("World untuk arena '" + key + "' tidak termuat: " + worldName);
                continue;
            }

            int legacyY = getConfig().getInt(base + "floor-y", 64);
            int centerY = getConfig().getInt(base + "center-y", legacyY);
            String mode = getConfig().getString(base + "mode", "tdm");
            // Existing 0.2.3.1 arenas used ±27 even in Relic Rush. Keep that until regenerated.
            int spawnOffset = getConfig().getInt(base + "spawn-offset", TDM_SPAWN_OFFSET);
            // Arena legacy tanpa field radius selalu memakai footprint lama (40), termasuk Relic Rush.
            int radius = getConfig().getInt(base + "radius", TDM_RADIUS);
            ArenaData data = new ArenaData(
                    key.toLowerCase(Locale.ROOT), worldName,
                    getConfig().getLong(base + "seed", 0L),
                    getConfig().getInt(base + "center-x"),
                    getConfig().getInt(base + "center-z"),
                    centerY,
                    getConfig().getInt(base + "red-y", legacyY),
                    getConfig().getInt(base + "blue-y", legacyY),
                    getConfig().getInt(base + "objective-y", centerY + 4),
                    getConfig().getInt(base + "min-surface-y", legacyY),
                    getConfig().getInt(base + "max-surface-y", legacyY),
                    mode,
                    spawnOffset,
                    radius
            );

            if (getConfig().getBoolean(base + "complete", false)) {
                arenas.put(data.name, data);
                // v4 forces another pass because cleanup is now also part of the generation
                // plan and the legacy post-generation scan is expanded to twelve blocks.
                if (!getConfig().getBoolean(base + "combat-vegetation-cleaned-v4", false)) {
                    if (clearCombatVegetationSafely(data)) {
                        getConfig().set(base + "combat-vegetation-cleaned-v4", true);
                        saveConfig();
                    }
                }
                // Bersihkan mob yang sudah ada saat plugin dimuat; spawn baru diblokir
                // oleh CreatureSpawnEvent di seluruh kolom X/Z arena.
                clearMobsInArena(data);
            } else {
                incompleteArenas.put(data.name, data);
                getLogger().warning("Arena '" + key + "' berstatus belum selesai. Periksa snapshot dan gunakan /arena recover " + key + ".");
            }
        }
    }


    int teamCountFor(ArenaData arena) {
        if (arena == null || !"tdm".equalsIgnoreCase(arena.mode)) return 2;
        return Math.max(2, Math.min(4,
                getConfig().getInt("arenas." + arena.name + ".team-count", 2)));
    }

    List<String> teamNamesFor(ArenaData arena) {
        List<String> names = new ArrayList<>(List.of("red", "blue"));
        if (arena != null && "tdm".equalsIgnoreCase(arena.mode)) {
            int count = teamCountFor(arena);
            if (count >= 3) names.add("green");
            if (count >= 4) names.add("yellow");
        }
        return names;
    }

    ChatColor teamColor(String team) {
        return switch (team == null ? "" : team.toLowerCase(Locale.ROOT)) {
            case "red" -> ChatColor.RED;
            case "blue" -> ChatColor.BLUE;
            case "green" -> ChatColor.GREEN;
            case "yellow" -> ChatColor.YELLOW;
            default -> ChatColor.WHITE;
        };
    }

    String teamDisplayName(String team) {
        return team == null ? "?" : team.toUpperCase(Locale.ROOT);
    }

    void setArenaTeamCount(CommandSender sender, String arenaName, String rawCount) {
        ArenaData arena = arenas.get(arenaName.toLowerCase(Locale.ROOT));
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
            return;
        }
        if (!"tdm".equalsIgnoreCase(arena.mode)) {
            sender.sendMessage(ChatColor.RED + "Penambahan 3-4 tim saat ini hanya didukung mode TDM. Relic Rush tetap 2 tim.");
            return;
        }
        if (matches.containsKey(arena.name)) {
            sender.sendMessage(ChatColor.RED + "Hentikan/reset antrean atau pertandingan sebelum mengubah jumlah tim.");
            return;
        }
        int count;
        try { count = Integer.parseInt(rawCount); }
        catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Jumlah tim harus angka 2, 3, atau 4.");
            return;
        }
        if (count < 2 || count > 4) {
            sender.sendMessage(ChatColor.RED + "Jumlah tim yang didukung adalah 2-4.");
            return;
        }
        getConfig().set("arenas." + arena.name + ".team-count", count);
        saveConfig();
        sender.sendMessage(ChatColor.GREEN + "Arena " + arena.name + " sekarang memakai " + count
                + " tim: " + String.join(", ", teamNamesFor(arena)).toUpperCase(Locale.ROOT) + ".");
        sender.sendMessage(ChatColor.GRAY + "Jalankan /arena regenerate " + arena.name
                + " untuk menyesuaikan markas arena dengan jumlah tim baru.");
    }

    boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("arenasystem.admin")) return true;
        sender.sendMessage(ChatColor.RED + "Kamu tidak punya izin. Permission: arenasystem.admin");
        return false;
    }

    void createArena(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Jalankan perintah ini dari dalam game, di titik tengah arena.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena create <nama> [seed] [tdm|relic_rush]");
            sender.sendMessage(ChatColor.GRAY + "Contoh: /arena create relic relic_rush atau /arena create relic 12345 relic_rush");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,20}")) {
            sender.sendMessage(ChatColor.RED + "Nama arena hanya boleh huruf kecil, angka, _ dan - (maks. 20 karakter).");
            return;
        }
        if (arenas.containsKey(name) || incompleteArenas.containsKey(name) || buildingArenas.contains(name)
                || getConfig().contains("arenas." + name)) {
            sender.sendMessage(ChatColor.RED + "Nama arena sudah terdaftar atau masih memiliki data pemulihan.");
            return;
        }

        Player player = (Player) sender;
        World world = player.getWorld();
        int centerX = player.getLocation().getBlockX();
        int centerZ = player.getLocation().getBlockZ();

        long seed = System.currentTimeMillis();
        String mode = "tdm";
        if (args.length >= 3) {
            if (isSupportedMode(args[2])) {
                mode = args[2].toLowerCase(Locale.ROOT);
                if (args.length >= 4) {
                    player.sendMessage(ChatColor.RED + "Jika mode ditulis langsung setelah nama, seed tidak dapat ditambahkan setelahnya.");
                    return;
                }
            } else {
                try {
                    seed = Long.parseLong(args[2]);
                } catch (NumberFormatException ex) {
                    player.sendMessage(ChatColor.RED + "Argumen ketiga harus berupa seed angka atau mode tdm/relic_rush.");
                    return;
                }
                if (args.length >= 4) {
                    if (!isSupportedMode(args[3])) {
                        player.sendMessage(ChatColor.RED + "Mode tidak valid. Pilih tdm atau relic_rush.");
                        return;
                    }
                    mode = args[3].toLowerCase(Locale.ROOT);
                }
            }
        }
        int radius = radiusForMode(mode);
        if (overlapsExistingArena(world, centerX, centerZ, radius)) {
            player.sendMessage(ChatColor.RED + "Lokasi bertumpang tindih dengan arena yang sudah terdaftar.");
            return;
        }
        int span = radius * 2 + 1;
        player.sendMessage(ChatColor.GREEN + "Memindai kontur medan " + span + "x" + span + " untuk mode "
                + mode.toUpperCase(Locale.ROOT) + ". Bentuk arena akan dibuat khusus untuk mode ini...");
        scanAndBuild(player, name, world, centerX, centerZ, seed, mode);
    }

    void scanAndBuild(CommandSender sender, String name, World world, int centerX, int centerZ, long seed, String mode) {
        int radius = radiusForMode(mode);
        int size = radius * 2 + 1;
        int[][] heights = new int[size][size];
        int[] cursor = {-radius, -radius};
        int[] limits = {Integer.MAX_VALUE, Integer.MIN_VALUE};
        new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                while (cursor[0] <= radius && processed < SCAN_COLUMNS_PER_TICK) {
                    int y = TerrainScanner.findSurface(world, centerX + cursor[0], centerZ + cursor[1]);
                    if (y == Integer.MIN_VALUE) {
                        cancel();
                        sender.sendMessage(ChatColor.RED + "Tidak menemukan permukaan tanah yang cocok pada area ini.");
                        return;
                    }
                    heights[cursor[0] + radius][cursor[1] + radius] = y;
                    limits[0] = Math.min(limits[0], y);
                    limits[1] = Math.max(limits[1], y);
                    processed++;
                    cursor[1]++;
                    if (cursor[1] > radius) {
                        cursor[1] = -radius;
                        cursor[0]++;
                    }
                }
                if (cursor[0] > radius) {
                    cancel();
                    TerrainProfile originalProfile = new TerrainProfile(radius, heights, limits[0], limits[1]);
                    int maxAdjacentStep = Math.max(1, Math.min(4,
                            getConfig().getInt("terrain.max-adjacent-step", 2)));
                    // Relic Rush spawn offset tracks the sanctum position built by ArenaGenerator (radius - 8).
                    int spawnOffset = mode.equals("relic_rush") ? radius - 8 : TDM_SPAWN_OFFSET;
                    TerrainNormalizer.Result normalization;
                    try {
                        normalization = TerrainNormalizer.normalize(originalProfile, maxAdjacentStep, spawnOffset);
                    } catch (IllegalStateException | IllegalArgumentException ex) {
                        sender.sendMessage(ChatColor.RED + "Terrain tidak dapat dinormalkan dengan aman: " + ex.getMessage());
                        return;
                    }

                    TerrainProfile profile = normalization.profile();
                    int maxColumnAdjustment = Math.max(1,
                            getConfig().getInt("terrain.max-column-adjustment", 12));
                    sender.sendMessage(ChatColor.AQUA + "[TerrainNormalizer] " + normalization.changedColumns()
                            + " kolom disesuaikan; perubahan maksimum " + normalization.maxAdjustment()
                            + " blok, rata-rata " + String.format(Locale.ROOT, "%.2f", normalization.averageAdjustment())
                            + " blok; beda tinggi antarkolom dibatasi " + normalization.maxAdjacentStep()
                            + " blok; elevasi dan pad spawn RED/BLUE diseimbangkan.");
                    if (normalization.maxAdjustment() > maxColumnAdjustment) {
                        sender.sendMessage(ChatColor.RED + "Terrain terlalu ekstrem untuk dinormalkan dengan aman: "
                                + "perubahan per kolom perlu " + normalization.maxAdjustment()
                                + " blok (batas " + maxColumnAdjustment + ").");
                        sender.sendMessage(ChatColor.GRAY + "Atur terrain.max-column-adjustment di config.yml atau pilih area lain.");
                        return;
                    }

                    TerrainDetector.Report terrainReport = TerrainDetector.analyze(profile, radius, spawnOffset);
                    sender.sendMessage(ChatColor.GOLD + "[TerrainDetector] Setelah normalisasi: " + terrainReport.summary());
                    for (String warning : terrainReport.warnings()) {
                        sender.sendMessage(ChatColor.YELLOW + "[TerrainDetector] " + warning);
                    }
                    if (!terrainReport.accepted()) {
                        sender.sendMessage(ChatColor.RED + "Terrain belum memenuhi batas kelayakan arena: "
                                + terrainReport.rejectionReason());
                        sender.sendMessage(ChatColor.GRAY + "Pilih lokasi lain atau tinjau batas normalisasi dengan hati-hati.");
                        return;
                    }

                    int redY = profile.heightAt(-spawnOffset, 0);
                    int blueY = profile.heightAt(spawnOffset, 0);
                    if (!hasBuildClearanceAfterNormalization(world, centerX - spawnOffset, redY, centerZ,
                                originalProfile.heightAt(-spawnOffset, 0))
                            || !hasBuildClearanceAfterNormalization(world, centerX + spawnOffset, blueY, centerZ,
                                originalProfile.heightAt(spawnOffset, 0))
                            || !hasObjectiveClearanceAfterNormalization(world, centerX, profile.heightAt(0, 0),
                                centerZ, originalProfile.heightAt(0, 0))) {
                        sender.sendMessage(ChatColor.RED + "Ruang spawn/objektif terhalang block entity atau struktur yang tidak aman dipindahkan.");
                        return;
                    }

                    ArenaData data = makeArenaData(name, world.getName(), seed, centerX, centerZ, profile, mode, radius);
                    beginGeneration(sender, data, world, originalProfile, profile);
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    /**
     * Membersihkan rumput/semak pada arena lama satu kali. Pembersihan memindai
     * seluruh pita elevasi terrain, bukan hanya di atas blok tertinggi per kolom,
     * sehingga vegetasi di lereng/permukaan bertingkat tidak terlewat. Blok asli
     * ditambahkan ke snapshot sebelum dihapus agar pemulihan/delete arena tetap aman.
     */
    boolean clearCombatVegetationSafely(ArenaData arena) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) return false;
        if (!snapshots.exists(arena.name)) {
            getLogger().warning("Tidak dapat membersihkan vegetasi arena '" + arena.name
                    + "': snapshot asli tidak tersedia. Regenerate manual diperlukan.");
            return false;
        }

        Map<BlockPos, String> snapshot;
        try {
            snapshot = snapshots.load(arena.name);
        } catch (IOException ex) {
            getLogger().warning("Gagal membaca snapshot arena '" + arena.name
                    + "'; vegetasi tidak diubah: " + ex.getMessage());
            return false;
        }

        Map<BlockPos, String> toClear = new LinkedHashMap<>();
        // Use the arena's saved terrain band rather than the current highest block in
        // each column. Tall structures or cliffs can otherwise hide ground-level grass
        // from a surface-only scan.
        int scanMinY = Math.max(world.getMinHeight(), Math.min(arena.minSurfaceY, arena.maxSurfaceY) + 1);
        int scanMaxY = Math.min(world.getMaxHeight() - 1, Math.max(arena.minSurfaceY, arena.maxSurfaceY) + 12);
        for (int x = -arena.radius; x <= arena.radius; x++) {
            for (int z = -arena.radius; z <= arena.radius; z++) {
                for (int y = scanMinY; y <= scanMaxY; y++) {
                    Block block = world.getBlockAt(arena.centerX + x, y, arena.centerZ + z);
                    if (!ArenaGenerator.isCombatVegetation(block.getType())
                            || block.getState() instanceof TileState) continue;

                    BlockPos relative = new BlockPos(x, y - arena.centerY, z);
                    String originalData = block.getBlockData().getAsString();
                    snapshot.putIfAbsent(relative, originalData);
                    toClear.put(relative, originalData);
                }
            }
        }

        if (toClear.isEmpty()) return true;
        try {
            // Backup harus berhasil sebelum ada blok dunia yang diubah.
            snapshots.save(arena.name, snapshot);
        } catch (IOException ex) {
            getLogger().severe("Gagal memperbarui snapshot vegetasi arena '" + arena.name
                    + "'; tidak ada rumput yang dihapus: " + ex.getMessage());
            return false;
        }

        for (BlockPos pos : toClear.keySet()) {
            world.getBlockAt(arena.centerX + pos.x, arena.centerY + pos.y, arena.centerZ + pos.z)
                    .setType(Material.AIR, false);
        }
        getLogger().info("Menghapus " + toClear.size() + " blok vegetasi pengganggu dari arena '"
                + arena.name + "'. Snapshot pemulihan diperbarui.");
        return true;
    }

    void clearArenaVegetation(CommandSender sender, String arenaName) {
        ArenaData arena = arenas.get(arenaName.toLowerCase(Locale.ROOT));
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan atau belum selesai dibuat.");
            return;
        }
        if (matches.containsKey(arena.name)) {
            sender.sendMessage(ChatColor.RED + "Hentikan atau reset pertandingan sebelum membersihkan vegetasi.");
            return;
        }
        if (!clearCombatVegetationSafely(arena)) {
            sender.sendMessage(ChatColor.RED + "Vegetasi tidak dibersihkan. Pastikan snapshot arena tersedia dan periksa log.");
            return;
        }
        getConfig().set("arenas." + arena.name + ".combat-vegetation-cleaned-v4", true);
        saveConfig();
        sender.sendMessage(ChatColor.GREEN + "Rumput dan tanaman pengganggu di arena " + arena.name
                + " telah dibersihkan. Snapshot pemulihan tetap diperbarui.");
    }

    void clearMobsInArena(ArenaData arena) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) return;
        for (org.bukkit.entity.LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player || entity instanceof org.bukkit.entity.ArmorStand) continue;
            Location loc = entity.getLocation();
            if (!loc.getWorld().getName().equals(arena.worldName)) continue;
            
            int dx = Math.abs(loc.getBlockX() - arena.centerX);
            int dz = Math.abs(loc.getBlockZ() - arena.centerZ);
            if (dx <= arena.radius && dz <= arena.radius) {
                entity.remove();
            }
        }
    }






    boolean hasBuildClearanceAfterNormalization(World world, int x, int targetY, int z, int originalY) {
        for (int offset = 1; offset <= 3; offset++) {
            int y = targetY + offset;
            if (y <= originalY) continue; // this block is inside the planned cut and will become AIR
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) return false;
            Block block = world.getBlockAt(x, y, z);
            if (block.getState() instanceof TileState || !block.isPassable()) return false;
        }
        return true;
    }

    boolean hasObjectiveClearanceAfterNormalization(World world, int x, int targetY, int z, int originalY) {
        for (int offset = 1; offset <= 5; offset++) {
            int y = targetY + offset;
            if (y <= originalY) continue; // this block is inside the planned cut and will become AIR
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) return false;
            Block block = world.getBlockAt(x, y, z);
            if (block.getState() instanceof TileState || !block.isPassable()) return false;
        }
        return true;
    }

    ArenaData makeArenaData(String name, String worldName, long seed, int centerX, int centerZ,
                                    TerrainProfile terrain, String mode, int radius) {
        int centerY = terrain.heightAt(0, 0);
        // Relic Rush spawn offset tracks the sanctum position built by ArenaGenerator (radius - 8).
        int spawnOffset = "relic_rush".equalsIgnoreCase(mode) ? radius - 8 : TDM_SPAWN_OFFSET;
        int redY = terrain.heightAt(-spawnOffset, 0);
        int blueY = terrain.heightAt(spawnOffset, 0);
        return new ArenaData(name, worldName, seed, centerX, centerZ, centerY,
                redY, blueY, centerY + 5, terrain.minY, terrain.maxY, mode, spawnOffset, radius);
    }

    void beginGeneration(CommandSender sender, ArenaData data, World world,
                                 TerrainProfile originalProfile, TerrainProfile profile) {
        List<BlockEdit> edits;
        try {
            int configuredTeams = "tdm".equalsIgnoreCase(data.mode) ? teamCountFor(data) : 2;
            edits = ArenaGenerator.createPlan(world, data, originalProfile, profile,
                    data.seed, data.radius, configuredTeams);
        } catch (IllegalStateException ex) {
            sender.sendMessage(ChatColor.RED + "Pembuatan arena dibatalkan demi keamanan: " + ex.getMessage());
            getLogger().warning("Terrain normalization dibatalkan untuk arena " + data.name + ": " + ex.getMessage());
            return;
        }
        Map<BlockPos, String> original = captureOriginalBlocks(world, data, edits);
        if (original.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Tidak ada blok aman untuk dibangun. Tidak ada perubahan yang dilakukan.");
            return;
        }
        try {
            snapshots.save(data.name, original);
        } catch (IOException ex) {
            getLogger().severe("Tidak dapat menyimpan snapshot arena " + data.name + ": " + ex.getMessage());
            sender.sendMessage(ChatColor.RED + "Gagal menyimpan snapshot. Arena tidak dibuat demi keamanan world.");
            return;
        }

        buildingArenas.add(data.name);
        incompleteArenas.put(data.name, data);
        saveArenaData(data, false);
        sender.sendMessage(ChatColor.GREEN + "Pemindaian selesai. Membangun " + edits.size()
                + " perubahan blok secara bertahap...");

        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                try {
                    int end = Math.min(index + BUILD_BLOCKS_PER_TICK, edits.size());
                    for (; index < end; index++) {
                        BlockEdit edit = edits.get(index);
                        world.getBlockAt(data.centerX + edit.position.x,
                                        data.centerY + edit.position.y,
                                        data.centerZ + edit.position.z)
                                .setType(edit.material, false);
                    }
                    if (index >= edits.size()) {
                        cancel();
                        // Remove obstructive plants after generation. The cleanup updates the original
                        // snapshot before touching the world, so delete/regenerate can restore them.
                        if (clearCombatVegetationSafely(data)) {
                            getConfig().set("arenas." + data.name + ".combat-vegetation-cleaned-v4", true);
                            saveConfig();
                        } else {
                            getLogger().warning("Vegetation cleanup for arena '" + data.name
                                    + "' did not complete. It can be retried with /arena clearvegetation " + data.name + ".");
                        }

                        buildingArenas.remove(data.name);
                        incompleteArenas.remove(data.name);
                        arenas.put(data.name, data);
                        saveArenaData(data, true);

                        clearMobsInArena(data);

                        sender.sendMessage(ChatColor.GREEN + "Arena " + data.name + " berhasil dibuat dan dibersihkan dari mob!");
                        sender.sendMessage(ChatColor.GRAY + "Spawn: /arena tp " + data.name + " red|blue|green|yellow | Desain: " + data.mode.toUpperCase(Locale.ROOT));
                        sender.sendMessage(ChatColor.GRAY + (data.mode.equals("relic_rush")
                                ? "Tujuan: rebut Relic di tengah dan antar ke sanctum markas tim."
                                : "Tujuan: kalahkan lawan untuk mencapai target kill."));
                        getLogger().info("Arena '" + data.name + "' dibuat di " + data.worldName + " dan disterilkan dari mob.");
                    }
                } catch (RuntimeException ex) {
                    cancel();
                    getLogger().severe("Pembuatan arena " + data.name + " terhenti: " + ex.getMessage());
                    sender.sendMessage(ChatColor.RED + "Pembuatan terhenti. Snapshot masih disimpan; gunakan /arena recover " + data.name + " setelah memeriksa log.");
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    Map<BlockPos, String> captureOriginalBlocks(World world, ArenaData arena, List<BlockEdit> edits) {
        Map<BlockPos, String> original = new LinkedHashMap<>();
        for (BlockEdit edit : edits) {
            Block block = world.getBlockAt(arena.centerX + edit.position.x,
                    arena.centerY + edit.position.y, arena.centerZ + edit.position.z);
            if (block.getState() instanceof TileState) continue;
            original.put(edit.position, block.getBlockData().getAsString());
        }
        return original;
    }

    boolean overlapsExistingArena(World world, int centerX, int centerZ, int newRadius) {
        for (ArenaData arena : arenas.values()) {
            if (!arena.worldName.equals(world.getName())) continue;
            if (Math.abs(arena.centerX - centerX) < arena.radius + newRadius + 4
                    && Math.abs(arena.centerZ - centerZ) < arena.radius + newRadius + 4) return true;
        }
        for (ArenaData arena : incompleteArenas.values()) {
            if (!arena.worldName.equals(world.getName())) continue;
            if (Math.abs(arena.centerX - centerX) < arena.radius + newRadius + 4
                    && Math.abs(arena.centerZ - centerZ) < arena.radius + newRadius + 4) return true;
        }
        return false;
    }

    void saveArenaData(ArenaData data, boolean complete) {
        String base = "arenas." + data.name + ".";
        getConfig().set(base + "world", data.worldName);
        getConfig().set(base + "seed", data.seed);
        getConfig().set(base + "center-x", data.centerX);
        getConfig().set(base + "center-z", data.centerZ);
        getConfig().set(base + "center-y", data.centerY);
        getConfig().set(base + "floor-y", data.centerY); 
        getConfig().set(base + "red-y", data.redY);
        getConfig().set(base + "blue-y", data.blueY);
        getConfig().set(base + "objective-y", data.objectiveY);
        getConfig().set(base + "min-surface-y", data.minSurfaceY);
        getConfig().set(base + "max-surface-y", data.maxSurfaceY);
        getConfig().set(base + "mode", data.mode);
        getConfig().set(base + "spawn-offset", data.spawnOffset);
        getConfig().set(base + "radius", data.radius);
        getConfig().set(base + "complete", complete);
        saveConfig();
    }

    void listArenas(CommandSender sender) {
        if (arenas.isEmpty() && incompleteArenas.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Belum ada arena. Gunakan /arena create <nama> [seed] [tdm|relic_rush].");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Arena tersimpan:");
        for (ArenaData data : arenas.values()) {
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.AQUA + data.name + ChatColor.GRAY
                    + " | " + data.worldName + " | mode " + data.mode
                    + " | elevasi " + data.minSurfaceY + "–" + data.maxSurfaceY);
        }
        for (String name : incompleteArenas.keySet()) {
            sender.sendMessage(ChatColor.RED + "- " + name + " | belum selesai / perlu recovery");
        }
    }

    void showInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena info <nama>");
            return;
        }
        ArenaData data = arenas.get(args[1].toLowerCase(Locale.ROOT));
        if (data == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan atau belum selesai.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Arena " + data.name);
        sender.sendMessage(ChatColor.GRAY + "World: " + data.worldName + " | Seed: " + data.seed);
        sender.sendMessage(ChatColor.GRAY + "Tim aktif: "
                + String.join(", ", teamNamesFor(data)).toUpperCase(Locale.ROOT));
        sender.sendMessage(ChatColor.GRAY + "Mode: " + data.mode + " | Elevasi: " + data.minSurfaceY + "–" + data.maxSurfaceY
                + " | Jumlah tim: " + teamCountFor(data));
        sender.sendMessage(ChatColor.GRAY + "Desain: " + (data.mode.equals("relic_rush")
                ? "sanctum 13x13 + tiga jalur lebar + delapan outpost + altar Relic tengah" : "battlefield simetris + jalur rotasi + cover"));
        sender.sendMessage(ChatColor.GRAY + "Tujuan: " + (data.mode.equals("relic_rush")
                ? "rebut Relic dan antar ke altar markas" : "kalahkan lawan untuk mencapai target kill"));
        sender.sendMessage(ChatColor.GRAY + "Spawn merah: " + (data.centerX - data.spawnOffset) + ", " + (data.redY + 2) + ", " + data.centerZ);
        sender.sendMessage(ChatColor.GRAY + "Spawn biru: " + (data.centerX + data.spawnOffset) + ", " + (data.blueY + 2) + ", " + data.centerZ);
        sender.sendMessage(ChatColor.GRAY + "Objective: " + data.centerX + ", " + data.objectiveY + ", " + data.centerZ);
        sender.sendMessage(ChatColor.GRAY + "Snapshot pemulihan: " + (snapshots.exists(data.name) ? "tersedia" : "tidak ada (arena legacy)"));
    }

    void teleportToArena(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Perintah teleport hanya dapat digunakan pemain.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena tp <nama> <red|blue|green|yellow|center>");
            return;
        }
        Player player = (Player) sender;
        ArenaData arena = arenas.get(args[1].toLowerCase(Locale.ROOT));
        if (arena == null) {
            player.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
            return;
        }
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "World arena tidak termuat.");
            return;
        }
        String point = args[2].toLowerCase(Locale.ROOT);
        if (point.equals("tengah")) point = "center";
        Location target;
        int spawnOffset = arena.spawnOffset;
        if (!point.equals("center") && !teamNamesFor(arena).contains(
                point.equals("merah") ? "red" : point.equals("biru") ? "blue"
                        : point.equals("hijau") ? "green" : point.equals("kuning") ? "yellow" : point)) {
            player.sendMessage(ChatColor.RED + "Markas yang tersedia: "
                    + String.join(", ", teamNamesFor(arena)).toUpperCase(Locale.ROOT) + ".");
            return;
        }
        if (point.equals("red") || point.equals("merah")) {
            target = new Location(world, arena.centerX - spawnOffset + 0.5D, arena.redY + 2.0D, arena.centerZ + 0.5D, -90.0F, 0.0F);
        } else if (point.equals("blue") || point.equals("biru")) {
            target = new Location(world, arena.centerX + spawnOffset + 0.5D, arena.blueY + 2.0D, arena.centerZ + 0.5D, 90.0F, 0.0F);
        } else if (point.equals("green") || point.equals("hijau")) {
            target = new Location(world, arena.centerX + 0.5D,
                    world.getHighestBlockYAt(arena.centerX, arena.centerZ - spawnOffset) + 1.0D,
                    arena.centerZ - spawnOffset + 0.5D, 180.0F, 0.0F);
        } else if (point.equals("yellow") || point.equals("kuning")) {
            target = new Location(world, arena.centerX + 0.5D,
                    world.getHighestBlockYAt(arena.centerX, arena.centerZ + spawnOffset) + 1.0D,
                    arena.centerZ + spawnOffset + 0.5D, 0.0F, 0.0F);
        } else if (point.equals("center") || point.equals("tengah")) {
            target = new Location(world, arena.centerX + 0.5D, arena.objectiveY + 1.0D, arena.centerZ + 0.5D);
        } else {
            player.sendMessage(ChatColor.RED + "Pilih red, blue, green, yellow, atau center.");
            return;
        }
        player.teleport(target);
        player.sendMessage(ChatColor.GREEN + "Teleport ke arena " + arena.name + ".");
    }

    void deleteArena(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage(ChatColor.YELLOW + "Konfirmasi pemulihan world dengan: /arena delete <nama> confirm");
            return;
        }
        ArenaData arena = arenas.get(args[1].toLowerCase(Locale.ROOT));
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
            return;
        }
        if (!snapshots.exists(arena.name)) {
            sender.sendMessage(ChatColor.RED + "Snapshot asli tidak tersedia. Arena legacy 0.1.0 tidak bisa dihapus otomatis dengan aman.");
            sender.sendMessage(ChatColor.GRAY + "Pulihkan dari backup world atau buat ulang arena 0.2.0 di lokasi lain.");
            return;
        }
        stopAndRestoreMatch(arena.name, () -> restoreArenaSnapshot(sender, arena, true, () -> {
            arenas.remove(arena.name);
            getConfig().set("arenas." + arena.name, null);
            saveConfig();
            try {
                snapshots.delete(arena.name);
            } catch (IOException ex) {
                getLogger().warning("Snapshot arena " + arena.name + " belum terhapus: " + ex.getMessage());
            }
            sender.sendMessage(ChatColor.GREEN + "Arena " + arena.name + " dihapus dan blok yang dicatat telah dipulihkan.");
        }));
    }

    void regenerateArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena regenerate <nama> [seed]");
            return;
        }
        ArenaData old = arenas.get(args[1].toLowerCase(Locale.ROOT));
        if (old == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
            return;
        }
        if (!snapshots.exists(old.name)) {
            sender.sendMessage(ChatColor.RED + "Snapshot asli tidak tersedia. Regenerate ditolak agar tidak menimpa world tanpa pemulihan.");
            return;
        }
        long seed = old.seed;
        if (args.length >= 3 && !args[2].isBlank()) {
            try {
                seed = Long.parseLong(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Seed harus berupa angka bulat (long).");
                return;
            }
        }
        long finalSeed = seed;
        stopAndRestoreMatch(old.name, () -> restoreArenaSnapshot(sender, old, false, () -> {
            World world = Bukkit.getWorld(old.worldName);
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "World arena tidak termuat setelah pemulihan.");
                return;
            }
            arenas.remove(old.name);
            incompleteArenas.put(old.name, old);
            saveArenaData(old, false);
            sender.sendMessage(ChatColor.YELLOW + "Arena lama dipulihkan. Memindai medan untuk regenerate...");
            scanAndBuild(sender, old.name, world, old.centerX, old.centerZ, finalSeed, old.mode);
        }));
    }

    void recoverArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena recover <nama>");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        ArenaData data = incompleteArenas.get(name);
        if (data == null) {
            sender.sendMessage(ChatColor.RED + "Tidak ada arena tertunda dengan nama itu.");
            return;
        }
        if (!snapshots.exists(name)) {
            sender.sendMessage(ChatColor.RED + "Snapshot recovery tidak ditemukan. Pulihkan world dari backup manual.");
            return;
        }
        restoreArenaSnapshot(sender, data, true, () -> {
            incompleteArenas.remove(name);
            getConfig().set("arenas." + name, null);
            saveConfig();
            try {
                snapshots.delete(name);
            } catch (IOException ex) {
                getLogger().warning("Gagal menghapus snapshot recovery: " + ex.getMessage());
            }
            sender.sendMessage(ChatColor.GREEN + "Recovery " + name + " selesai. World dikembalikan ke keadaan sebelum generator dijalankan.");
        });
    }

    void restoreArenaSnapshot(CommandSender sender, ArenaData arena, boolean deleteSnapshot, Runnable after) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World " + arena.worldName + " tidak termuat; pemulihan dibatalkan.");
            return;
        }
        final Map<BlockPos, String> data;
        try {
            data = snapshots.load(arena.name);
        } catch (IOException ex) {
            sender.sendMessage(ChatColor.RED + "Gagal membaca snapshot: " + ex.getMessage());
            return;
        }
        sender.sendMessage(ChatColor.YELLOW + "Memulihkan " + data.size() + " posisi blok untuk arena " + arena.name + "...");
        restoreMapBatched(world, arena, data, () -> {
            if (deleteSnapshot) {
                try {
                    snapshots.delete(arena.name);
                } catch (IOException ex) {
                    getLogger().warning("Snapshot gagal dihapus: " + ex.getMessage());
                }
            }
            after.run();
        });
    }

    BukkitTask restoreMapBatched(World world, ArenaData arena, Map<BlockPos, String> changes, Runnable after) {
        List<Map.Entry<BlockPos, String>> entries = new ArrayList<>(changes.entrySet());
        return new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                int end = Math.min(index + RESTORE_BLOCKS_PER_TICK, entries.size());
                try {
                    for (; index < end; index++) {
                        Map.Entry<BlockPos, String> entry = entries.get(index);
                        BlockPos pos = entry.getKey();
                        world.getBlockAt(arena.centerX + pos.x, arena.centerY + pos.y, arena.centerZ + pos.z)
                                .setBlockData(Bukkit.createBlockData(entry.getValue()), false);
                    }
                    if (index >= entries.size()) {
                        cancel();
                        after.run();
                    }
                } catch (RuntimeException ex) {
                    cancel();
                    getLogger().severe("Pemulihan blok arena " + arena.name + " gagal: " + ex.getMessage());
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    void restoreMapImmediately(ArenaData arena, Map<BlockPos, String> changes) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) return;
        for (Map.Entry<BlockPos, String> entry : changes.entrySet()) {
            BlockPos pos = entry.getKey();
            try {
                world.getBlockAt(arena.centerX + pos.x, arena.centerY + pos.y, arena.centerZ + pos.z)
                        .setBlockData(Bukkit.createBlockData(entry.getValue()), false);
            } catch (RuntimeException ex) {
                getLogger().warning("Gagal mengembalikan blok match " + arena.name + ": " + ex.getMessage());
            }
        }
    }

    void resetMatch(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena reset <nama>");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        MatchState state = matches.get(name);
        if (state == null) {
            sender.sendMessage(ChatColor.YELLOW + "Tidak ada perubahan pertandingan yang perlu di-reset untuk arena itu.");
            return;
        }
        ArenaData arena = arenas.get(name);
        if (arena == null) {
            matches.remove(name);
            sender.sendMessage(ChatColor.RED + "Data arena hilang; state pertandingan dibersihkan.");
            return;
        }
        cancelMatchTasks(state);
        if (playerArenaListener != null) playerArenaListener.stopDeathCamerasForArena(name);
        if (classPassiveManager != null) classPassiveManager.clearMatch(state);
        state.running = false;
        state.preparing = false;
        state.launching = false;
        state.ending = true;
        clearMatchHud(state);
        restorePlayerSnapshots(state);
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World arena tidak termuat; reset dibatalkan.");
            return;
        }
        restoreMapBatched(world, arena, combinedMatchChanges(state), () -> {
            removeRelicItems(arena, state);
            matches.remove(name);
            sender.sendMessage(ChatColor.GREEN + "Perubahan blok selama persiapan/pertandingan pada arena " + name + " telah di-reset.");
        });
    }

    void stopAndRestoreMatch(String name, Runnable after) {
        MatchState state = matches.get(name);
        ArenaData arena = arenas.get(name);
        if (state == null || arena == null) {
            after.run();
            return;
        }
        cancelMatchTasks(state);
        if (playerArenaListener != null) playerArenaListener.stopDeathCamerasForArena(name);
        if (classPassiveManager != null) classPassiveManager.clearMatch(state);
        state.running = false;
        state.preparing = false;
        state.launching = false;
        state.ending = true;
        clearMatchHud(state);
        restorePlayerSnapshots(state);
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) {
            getLogger().warning("Tidak dapat memulihkan state match " + name + ": world tidak termuat.");
            matches.remove(name);
            after.run();
            return;
        }
        restoreMapBatched(world, arena, combinedMatchChanges(state), () -> {
            removeRelicItems(arena, state);
            matches.remove(name);
            after.run();
        });
    }

    void cancelMatchTasks(MatchState state) {
        if (state.timer != null) state.timer.cancel();
        if (state.preparationTimer != null) state.preparationTimer.cancel();
        if (state.lobbyBuildTask != null) state.lobbyBuildTask.cancel();
        if (state.lobbyRestoreTask != null) state.lobbyRestoreTask.cancel();
        state.timer = null;
        state.preparationTimer = null;
        state.lobbyBuildTask = null;
        state.lobbyRestoreTask = null;
    }

    Map<BlockPos, String> combinedMatchChanges(MatchState state) {
        Map<BlockPos, String> changes = new LinkedHashMap<>(state.originalBlocks);
        state.lobbyOriginalBlocks.forEach(changes::putIfAbsent);
        return changes;
    }

    void restorePlayerSnapshots(MatchState state) {
        for (Map.Entry<UUID, PlayerSnapshot> entry : new ArrayList<>(state.playerSnapshots.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                restoreRelicCarrierMovement(player, state);
                if (classAbilityListener != null) classAbilityListener.cancelFor(player);
                if (classPassiveManager != null) classPassiveManager.clearPlayer(entry.getKey());
                player.closeInventory();
                if (!player.isDead()) {
                    entry.getValue().restore(player);
                } else if (isEnabled() && playerArenaListener != null) {
                    // C3: never restore a dead player. Keep the snapshot until the player has
                    // respawned (see PlayerArenaListener#onPlayerRespawn) and request that respawn.
                    pendingPlayerRestores.put(entry.getKey(), entry.getValue());
                    playerArenaListener.requestRespawnAfterMatch(player);
                } else {
                    // Plugin is being disabled: no scheduler. Respawn synchronously, then restore.
                    restoreSnapshotWithoutScheduler(player, entry.getValue());
                }
            } else {
                pendingPlayerRestores.put(entry.getKey(), entry.getValue());
            }
        }
        state.playerSnapshots.clear();
        state.relicOriginalWalkSpeeds.clear();
    }

    void applyRelicCarrierMovement(Player player, MatchState state) {
        if (player == null || state == null) return;
        state.relicOriginalWalkSpeeds.putIfAbsent(player.getUniqueId(), player.getWalkSpeed());
        player.setSprinting(false);
        player.setWalkSpeed(0.10F); // Default is 0.20F; carrying the Relic halves walking speed.
    }

    /**
     * Prevent sprinting and sprint-jump momentum while the Relic is carried.
     * This runs synchronously once per tick so it also catches sprint states
     * that survive or race with the sprint-toggle event.
     */
    void enforceRelicCarrierMovement() {
        final double maxAirHorizontalSpeed = 0.055D;
        for (MatchState state : matches.values()) {
            if (!state.running || !"relic_rush".equals(state.mode) || state.relicCarrier == null) continue;

            Player carrier = Bukkit.getPlayer(state.relicCarrier);
            if (carrier == null || !carrier.isOnline() || !carrier.isValid()) continue;

            if (carrier.isSprinting()) carrier.setSprinting(false);
            if (Math.abs(carrier.getWalkSpeed() - 0.10F) > 0.001F) {
                carrier.setWalkSpeed(0.10F);
            }

            // Sprint-jumping can retain horizontal momentum even after sprint is disabled.
            // Cap only the X/Z component while airborne; preserve vertical motion and knockback direction.
            if (!carrier.isOnGround()) {
                Vector velocity = carrier.getVelocity();
                double horizontalSpeed = Math.hypot(velocity.getX(), velocity.getZ());
                if (horizontalSpeed > maxAirHorizontalSpeed) {
                    double scale = maxAirHorizontalSpeed / horizontalSpeed;
                    velocity.setX(velocity.getX() * scale);
                    velocity.setZ(velocity.getZ() * scale);
                    carrier.setVelocity(velocity);
                }
            }
        }
    }

    void restoreRelicCarrierMovement(Player player, MatchState state) {
        if (player == null || state == null) return;
        Float originalSpeed = state.relicOriginalWalkSpeeds.remove(player.getUniqueId());
        if (originalSpeed != null) player.setWalkSpeed(originalSpeed);
    }

    boolean isSupportedMode(String mode) {
        return mode != null && (mode.equalsIgnoreCase("tdm") || mode.equalsIgnoreCase("relic_rush"));
    }

    /** Relic Rush dibangun di radius yang lebih besar daripada TDM; lihat RELIC_RADIUS. */
    static int radiusForMode(String mode) {
        return "relic_rush".equalsIgnoreCase(mode) ? RELIC_RADIUS : TDM_RADIUS;
    }

    void setMode(CommandSender sender, String arenaName, String requestedMode) {
        ArenaData old = arenas.get(arenaName.toLowerCase(Locale.ROOT));
        if (old == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
            return;
        }
        if (matches.containsKey(old.name)) {
            sender.sendMessage(ChatColor.RED + "Hentikan/reset pertandingan dan kosongkan antrean sebelum mengubah mode.");
            return;
        }
        if (!isSupportedMode(requestedMode)) {
            sender.sendMessage(ChatColor.RED + "Mode tersedia: tdm atau relic_rush.");
            return;
        }
        if (!snapshots.exists(old.name)) {
            sender.sendMessage(ChatColor.RED + "Snapshot terrain arena tidak tersedia. Mode tidak diubah karena bentuk arena tidak dapat dibangun ulang dengan aman.");
            return;
        }
        String mode = requestedMode.toLowerCase(Locale.ROOT);
        int radius = radiusForMode(mode);
        int spawnOffset = mode.equals("relic_rush") ? radius - 8 : TDM_SPAWN_OFFSET;
        // This placeholder is superseded by a fresh makeArenaData() once regenerateArena()'s
        // rescan completes; its radius/spawnOffset only need to be sane, not final.
        ArenaData updated = new ArenaData(old.name, old.worldName, old.seed, old.centerX, old.centerZ,
                old.centerY, old.redY, old.blueY, old.objectiveY, old.minSurfaceY, old.maxSurfaceY, mode, spawnOffset, radius);
        arenas.put(updated.name, updated);
        incompleteArenas.put(updated.name, updated);
        // Mark incomplete before restoration starts so a crash can be recovered safely.
        saveArenaData(updated, false);
        sender.sendMessage(ChatColor.GREEN + "Mode arena " + updated.name + " dipilih: " + mode.toUpperCase(Locale.ROOT) + ".");
        sender.sendMessage(ChatColor.YELLOW + "Membangun ulang bentuk arena khusus mode " + mode.toUpperCase(Locale.ROOT) + "...");
        // Regenerate restores the pre-arena snapshot first, then runs the mode-specific generator.
        regenerateArena(sender, new String[]{"regenerate", updated.name});
    }

    void joinArena(Player player, String arenaName, String teamInput) {
        ArenaData arena = arenas.get(arenaName.toLowerCase(Locale.ROOT));
        if (arena == null) {
            player.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
            return;
        }
        String team = teamInput.toLowerCase(Locale.ROOT);
        if (!teamNamesFor(arena).contains(team)) {
            player.sendMessage(ChatColor.RED + "Tim yang tersedia: "
                    + String.join(", ", teamNamesFor(arena)) + ".");
            return;
        }
        for (MatchState current : matches.values()) {
            if (current.teams.containsKey(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Kamu sudah terdaftar di pertandingan arena " + current.arenaName + ".");
                return;
            }
        }
        MatchState state = matches.computeIfAbsent(arena.name, ignored -> new MatchState(arena.name, arena.mode));
        if (state.running || state.preparing || state.launching) {
            player.sendMessage(ChatColor.RED + "Lobby persiapan atau pertandingan sudah dimulai. Join ditutup.");
            return;
        }
        state.playerSnapshots.putIfAbsent(player.getUniqueId(), PlayerSnapshot.capture(player));
        state.selectedClasses.putIfAbsent(player.getUniqueId(), "knight");
        state.teams.put(player.getUniqueId(), team);
        player.teleport(spawnLocation(arena, team));
        player.sendMessage(ChatColor.GREEN + "Kamu bergabung dengan tim " + team.toUpperCase(Locale.ROOT)
                + " di arena " + arena.name + ".");
        showTeamJoinedTitle(player, arena, team);
        announce(state, ChatColor.YELLOW + player.getName() + " bergabung ke tim " + team + ".");
    }

    void leaveArena(Player player) {
        for (MatchState state : new ArrayList<>(matches.values())) {
            if (!state.teams.containsKey(player.getUniqueId())) continue;
            if (state.running || state.launching) {
                player.sendMessage(ChatColor.RED + "Kamu tidak dapat keluar dari tim saat pertandingan berlangsung.");
                return;
            }
            state.teams.remove(player.getUniqueId());
            state.selectedClasses.remove(player.getUniqueId());
            state.abilityCooldowns.remove(player.getUniqueId());
            if (classAbilityListener != null) classAbilityListener.cancelFor(player);
            if (classPassiveManager != null) classPassiveManager.clearPlayer(player.getUniqueId());
            player.closeInventory();
            Scoreboard previousBoard = state.previousScoreboards.remove(player.getUniqueId());
            if (previousBoard != null) player.setScoreboard(previousBoard);
            state.hudScoreboards.remove(player.getUniqueId());
            state.hudObjectives.remove(player.getUniqueId());
            PlayerSnapshot snapshot = state.playerSnapshots.remove(player.getUniqueId());
            if (snapshot != null) snapshot.restore(player);
            player.sendMessage(ChatColor.GREEN + "Kamu keluar dari antrean arena " + state.arenaName + ".");
            if (state.teams.isEmpty()) {
                if (state.preparing) {
                    stopAndRestoreMatch(state.arenaName, () -> getLogger().info("Lobby persiapan " + state.arenaName + " dibatalkan karena tidak ada pemain."));
                } else {
                    matches.remove(state.arenaName);
                }
            } else if (state.preparing) {
                adaptSinglePlayerPreparation(state);
                updateMatchHud(state);
            }
            return;
        }
        player.sendMessage(ChatColor.YELLOW + "Kamu tidak sedang bergabung ke arena mana pun.");
    }

    void startMatch(CommandSender sender, String arenaName) {
        ArenaData arena = arenas.get(arenaName.toLowerCase(Locale.ROOT));
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
            return;
        }
        MatchState state = matches.get(arena.name);
        if (state == null || state.running || state.preparing || state.launching) {
            sender.sendMessage(ChatColor.RED + "Belum ada antrean pemain atau arena sudah dalam proses pertandingan.");
            return;
        }

        state.teams.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        boolean singlePlayerMode = state.teams.size() == 1
                && getConfig().getBoolean("matches.allow-single-player", true);
        if (state.teams.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Tidak ada pemain online yang terdaftar di arena.");
            return;
        }
        List<String> requiredTeams = teamNamesFor(arena);
        List<String> missingTeams = requiredTeams.stream()
                .filter(team -> state.teams.values().stream().noneMatch(team::equals))
                .toList();
        if (!singlePlayerMode && !missingTeams.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Minimal satu pemain diperlukan di setiap tim: "
                    + String.join(", ", requiredTeams) + ". Tim kosong: " + String.join(", ", missingTeams) + ".");
            return;
        }

        state.singlePlayerMode = singlePlayerMode;
        state.redScore = 0;
        state.blueScore = 0;
        state.secondsElapsed = 0;
        state.teamScores.clear();
        for (String team : requiredTeams) state.teamScores.put(team, 0);
        state.preparationSecondsRemaining = Math.max(5,
                Math.min(180, getConfig().getInt("matches.preparation-seconds", 30)));
        state.preparing = true;
        clearMobsInArena(arena);

        boolean built = buildPreparationLobby(arena, state, () -> {
            if (!state.preparing || state.ending) return;
            for (Map.Entry<UUID, String> entry : state.teams.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null) continue;
                teleportToPreparationStation(player, arena, state, entry.getValue());
                if (state.mode.equals("tdm")) {
                    state.selectedClasses.putIfAbsent(entry.getKey(), "knight");
                    giveClassSelector(player);
                } else {
                    // Relic Rush is objective-first: all players use the same Relic Runner kit.
                    state.selectedClasses.remove(entry.getKey());
                    player.getInventory().clear();
                    player.getInventory().setArmorContents(new ItemStack[4]);
                }
            }
            updateMatchHud(state);
            showPreparationIntro(state);
            if (state.mode.equals("tdm")) {
                for (UUID uuid : state.teams.keySet()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) openClassMenu(player, arena, state);
                }
            }

            state.preparationTimer = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!state.preparing || state.ending) {
                        cancel();
                        return;
                    }
                    state.preparationSecondsRemaining--;
                    updateMatchHud(state);
                    if (state.preparationSecondsRemaining <= 0) {
                        cancel();
                        state.preparationTimer = null;
                        launchMatch(arena, state);
                        return;
                    }
                    // Final five-second cadence: note block ticks for 5..2, a clear start note at 1.
                    if (state.preparationSecondsRemaining <= 5) {
                        showPreparationCountdown(state, state.preparationSecondsRemaining);
                    }
                }
            }.runTaskTimer(this, 20L, 20L);
        });

        if (!built) {
            state.preparing = false;
            if (!state.originalBlocks.isEmpty()) {
                restoreMapImmediately(arena, state.originalBlocks);
                state.originalBlocks.clear();
            }
            sender.sendMessage(ChatColor.RED + "Lobby persiapan tidak dapat dibangun dengan aman. Pastikan area di atas arena kosong dan tidak berisi block entity.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Membangun lobby persiapan " + preparationLobbyName(state.mode)
                + " untuk arena " + arena.name + "...");
    }

    /** Build snapshot-backed GREEN/YELLOW TDM spawn pads without touching untracked world blocks. */
    boolean buildAdditionalTeamSpawnPads(ArenaData arena, MatchState state, List<String> teams) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) return false;
        Map<BlockPos, Material> edits = new LinkedHashMap<>();
        if (teams.contains("green") && !collectAdditionalSpawnPad(world, arena, edits, 0, -arena.spawnOffset, Material.GREEN_TERRACOTTA)) return false;
        if (teams.contains("yellow") && !collectAdditionalSpawnPad(world, arena, edits, 0, arena.spawnOffset, Material.YELLOW_TERRACOTTA)) return false;
        Map<BlockPos, String> originals = new LinkedHashMap<>();
        for (BlockPos pos : edits.keySet()) {
            Block block = world.getBlockAt(arena.centerX + pos.x, arena.centerY + pos.y, arena.centerZ + pos.z);
            if (block.getState() instanceof TileState) return false;
            originals.put(pos, block.getBlockData().getAsString());
        }
        state.originalBlocks.putAll(originals);
        for (Map.Entry<BlockPos, Material> entry : edits.entrySet()) {
            BlockPos pos = entry.getKey();
            world.getBlockAt(arena.centerX + pos.x, arena.centerY + pos.y, arena.centerZ + pos.z)
                    .setType(entry.getValue(), false);
        }
        return true;
    }

    boolean collectAdditionalSpawnPad(World world, ArenaData arena, Map<BlockPos, Material> edits,
                                       int centerX, int centerZ, Material teamMaterial) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int surfaceY = TerrainScanner.findSurface(world, arena.centerX + x, arena.centerZ + z);
                if (surfaceY == Integer.MIN_VALUE) return false;
                Block surface = world.getBlockAt(arena.centerX + x, surfaceY, arena.centerZ + z);
                if (dx == 0 && dz == 0 && surface.getType() == Material.SEA_LANTERN) {
                    surfaceY--;
                    if (surfaceY < world.getMinHeight()) return false;
                    surface = world.getBlockAt(arena.centerX + x, surfaceY, arena.centerZ + z);
                }
                if (surfaceY + 3 >= world.getMaxHeight()) return false;
                if (surface.getState() instanceof TileState) return false;
                Material pad = Math.max(Math.abs(dx), Math.abs(dz)) == 4
                        ? Material.WHITE_CONCRETE : teamMaterial;
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) pad = Material.SMOOTH_STONE;
                edits.put(new BlockPos(x, surfaceY - arena.centerY, z), pad);
                for (int offset = 1; offset <= 3; offset++) {
                    Block clear = world.getBlockAt(arena.centerX + x, surfaceY + offset, arena.centerZ + z);
                    if (clear.getState() instanceof TileState) return false;
                    edits.put(new BlockPos(x, surfaceY + offset - arena.centerY, z), Material.AIR);
                }
            }
        }
        return true;
    }

    /** Bangun lobby sementara sesuai mode, dengan snapshot agar dapat dipulihkan. */
    boolean buildPreparationLobby(ArenaData arena, MatchState state, Runnable after) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) return false;
        int lobbyY = arena.maxSurfaceY + 12;
        if (lobbyY < world.getMinHeight() + 2 || lobbyY + 6 >= world.getMaxHeight()) return false;

        Map<BlockPos, Material> plan = new LinkedHashMap<>();
        int floorRelativeY = lobbyY - arena.centerY;
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                for (int y = 1; y <= 5; y++) addLobbyBlock(plan, x, floorRelativeY + y, z, Material.AIR);
            }
        }

        if (state.mode.equals("relic_rush")) {
            buildRelicPreparationLayout(plan, floorRelativeY);
        } else {
            buildTdmPreparationLayout(plan, floorRelativeY, teamNamesFor(arena));
        }

        // Jangan mengubah chest, spawner, sign, atau block entity lain yang tidak tersimpan oleh snapshot BlockData.
        Map<BlockPos, String> originals = new LinkedHashMap<>();
        for (BlockPos pos : plan.keySet()) {
            int worldX = arena.centerX + pos.x;
            int worldY = arena.centerY + pos.y;
            int worldZ = arena.centerZ + pos.z;
            if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) return false;
            Block block = world.getBlockAt(worldX, worldY, worldZ);
            if (block.getState() instanceof TileState) return false;
            originals.put(pos, block.getBlockData().getAsString());
        }
        state.lobbyY = lobbyY;
        state.lobbyOriginalBlocks.clear();
        state.lobbyOriginalBlocks.putAll(originals);

        List<Map.Entry<BlockPos, Material>> edits = new ArrayList<>(plan.entrySet());
        state.lobbyBuildTask = new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                if (!state.preparing || state.ending) {
                    cancel();
                    state.lobbyBuildTask = null;
                    return;
                }
                try {
                    int end = Math.min(index + BUILD_BLOCKS_PER_TICK, edits.size());
                    for (; index < end; index++) {
                        Map.Entry<BlockPos, Material> edit = edits.get(index);
                        BlockPos pos = edit.getKey();
                        world.getBlockAt(arena.centerX + pos.x, arena.centerY + pos.y, arena.centerZ + pos.z)
                                .setType(edit.getValue(), false);
                    }
                    if (index >= edits.size()) {
                        cancel();
                        state.lobbyBuildTask = null;
                        after.run();
                    }
                } catch (RuntimeException ex) {
                    cancel();
                    state.lobbyBuildTask = null;
                    getLogger().severe("Gagal membangun lobby arena " + arena.name + ": " + ex.getMessage());
                    stopAndRestoreMatch(arena.name, () -> getLogger().warning("Lobby arena " + arena.name + " dibatalkan dan blok dipulihkan."));
                }
            }
        }.runTaskTimer(this, 1L, 1L);
        return true;
    }

    void buildTdmPreparationLayout(Map<BlockPos, Material> plan, int y, List<String> activeTeams) {
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                int edge = Math.max(Math.abs(x), Math.abs(z));
                Material floor = edge == 10 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_TILES;
                if (activeTeams.contains("red") && Math.abs(x + 7) <= 2 && Math.abs(z) <= 2) floor = Material.RED_CONCRETE;
                if (activeTeams.contains("blue") && Math.abs(x - 7) <= 2 && Math.abs(z) <= 2) floor = Material.BLUE_CONCRETE;
                if (activeTeams.contains("green") && Math.abs(x) <= 2 && Math.abs(z + 7) <= 2) floor = Material.GREEN_CONCRETE;
                if (activeTeams.contains("yellow") && Math.abs(x) <= 2 && Math.abs(z - 7) <= 2) floor = Material.YELLOW_CONCRETE;
                if (Math.abs(x) <= 2 && Math.abs(z) <= 2) floor = Material.POLISHED_ANDESITE;
                addLobbyBlock(plan, x, y, z, floor);

                boolean wall = edge == 10;
                boolean gate = (Math.abs(x) <= 2 && Math.abs(z) == 10)
                        || (Math.abs(z) <= 2 && Math.abs(x) == 10);
                if (wall && !gate) {
                    for (int h = 1; h <= 3; h++) {
                        addLobbyBlock(plan, x, y + h, z, Material.POLISHED_BLACKSTONE_BRICKS);
                    }
                }
                if (edge == 10 && Math.abs(x) == 10 && Math.abs(z) == 10) {
                    for (int h = 1; h <= 4; h++) addLobbyBlock(plan, x, y + h, z, Material.CHISELED_POLISHED_BLACKSTONE);
                }
            }
        }
        if (activeTeams.contains("red")) addLobbyBlock(plan, -7, y + 1, 0, Material.REDSTONE_BLOCK);
        if (activeTeams.contains("blue")) addLobbyBlock(plan, 7, y + 1, 0, Material.SEA_LANTERN);
        if (activeTeams.contains("green")) addLobbyBlock(plan, 0, y + 1, -7, Material.LIME_CONCRETE);
        if (activeTeams.contains("yellow")) addLobbyBlock(plan, 0, y + 1, 7, Material.YELLOW_CONCRETE);
        addLobbyBlock(plan, 0, y + 1, 0, Material.IRON_BLOCK);
        addLobbyBlock(plan, 0, y + 2, 0, Material.SEA_LANTERN);
    }

    void buildRelicPreparationLayout(Map<BlockPos, Material> plan, int y) {
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                int diamondDistance = Math.abs(x) + Math.abs(z);
                if (diamondDistance > 16) continue;
                Material floor = diamondDistance >= 15 ? Material.CUT_SANDSTONE : Material.SMOOTH_SANDSTONE;
                if (diamondDistance >= 10 && diamondDistance <= 12) floor = Material.CHISELED_SANDSTONE;
                if (Math.abs(x + 7) <= 2 && Math.abs(z) <= 2) floor = Material.RED_TERRACOTTA;
                if (Math.abs(x - 7) <= 2 && Math.abs(z) <= 2) floor = Material.BLUE_TERRACOTTA;
                if (Math.abs(x) <= 2 && Math.abs(z) <= 2) floor = Material.GOLD_BLOCK;
                addLobbyBlock(plan, x, y, z, floor);
            }
        }
        addLobbyBlock(plan, 0, y + 1, 0, Material.AMETHYST_BLOCK);
        addLobbyBlock(plan, 0, y + 2, 0, Material.SEA_LANTERN);
        addLobbyBlock(plan, 0, y + 3, 0, Material.AMETHYST_BLOCK);
        for (int sx : new int[]{-6, 6}) {
            for (int sz : new int[]{-6, 6}) {
                for (int h = 1; h <= 3; h++) {
                    addLobbyBlock(plan, sx, y + h, sz, h == 3 ? Material.AMETHYST_BLOCK : Material.PURPUR_BLOCK);
                }
            }
        }
        for (int h = 1; h <= 3; h++) {
            addLobbyBlock(plan, -7, y + h, 0, Material.RED_TERRACOTTA);
            addLobbyBlock(plan, 7, y + h, 0, Material.BLUE_TERRACOTTA);
        }
    }

    void addLobbyBlock(Map<BlockPos, Material> plan, int x, int relativeY, int z, Material material) {
        plan.put(new BlockPos(x, relativeY, z), material);
    }

    String preparationLobbyName(String mode) {
        return mode.equals("relic_rush") ? "Kuil Relik" : "Benteng Komando TDM";
    }

    Location preparationSpawnLocation(ArenaData arena, MatchState state, String team, boolean singlePlayer) {
        World world = Bukkit.getWorld(arena.worldName);
        // Mode solo hanya melonggarkan jumlah pemain. Lokasi selalu mengikuti stasiun tim.
        // Titik spawn berada di dalam pad berwarna, tidak tepat di atas blok penanda tim.
        String normalizedTeam = team == null ? "" : team.toLowerCase(Locale.ROOT);
        Location station;
        if ("red".equals(normalizedTeam)) {
            station = new Location(world, arena.centerX - 7.5D, state.lobbyY + 1.0D,
                    arena.centerZ + 1.5D, -90.0F, 0.0F);
        } else if ("blue".equals(normalizedTeam)) {
            station = new Location(world, arena.centerX + 7.5D, state.lobbyY + 1.0D,
                    arena.centerZ + 1.5D, 90.0F, 0.0F);
        } else if ("green".equals(normalizedTeam)) {
            station = new Location(world, arena.centerX + 1.5D, state.lobbyY + 1.0D,
                    arena.centerZ - 7.5D, 0.0F, 0.0F);
        } else if ("yellow".equals(normalizedTeam)) {
            station = new Location(world, arena.centerX + 1.5D, state.lobbyY + 1.0D,
                    arena.centerZ + 7.5D, 180.0F, 0.0F);
        } else {
            // Fallback aman untuk data tim lama/tidak valid; tidak memakai jalur khusus solo.
            station = new Location(world, arena.centerX + 0.5D, state.lobbyY + 1.0D,
                    arena.centerZ + 7.5D, 180.0F, 0.0F);
        }
        return faceArenaCenter(station, arena);
    }

    /** Atur arah pandang menuju titik tengah arena berdasarkan koordinat aktual. */
    Location faceArenaCenter(Location location, ArenaData arena) {
        if (location == null || arena == null) return location;
        Vector towardCenter = new Vector(
                arena.centerX + 0.5D - location.getX(),
                0.0D,
                arena.centerZ + 0.5D - location.getZ()
        );
        if (towardCenter.lengthSquared() > 1.0E-6D) {
            location.setDirection(towardCenter);
        }
        return location;
    }

    void teleportToPreparationStation(Player player, ArenaData arena, MatchState state, String team) {
        if (player == null || arena == null || state == null || team == null) return;
        Location station = preparationSpawnLocation(arena, state, team, state.singlePlayerMode);
        if (station.getWorld() == null) {
            getLogger().warning("World lobby tidak ditemukan untuk arena " + arena.name + ".");
            return;
        }
        player.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        player.setFallDistance(0.0F);
        boolean moved = player.teleport(station);
        if (!moved) {
            getLogger().warning("Teleport persiapan gagal untuk " + player.getName()
                    + " ke stasiun tim " + team.toUpperCase(Locale.ROOT) + " di arena " + arena.name + ".");
            return;
        }
        // Reassert one tick later so another same-tick lobby transition cannot leave
        // the player at the arena's generic center spawn.
        Bukkit.getScheduler().runTask(this, () -> {
            MatchState active = matches.get(arena.name);
            if (!player.isOnline() || active != state || !state.preparing
                    || !team.equals(state.teams.get(player.getUniqueId()))) return;
            Location current = player.getLocation();
            double yawDifference = Math.abs((((double) current.getYaw() - station.getYaw() + 540.0D)
                    % 360.0D) - 180.0D);
            boolean facingMismatch = yawDifference > 1.0D
                    || Math.abs(current.getPitch() - station.getPitch()) > 1.0F;
            if (current.getWorld() != station.getWorld()
                    || current.distanceSquared(station) > 1.0D || facingMismatch) {
                player.teleport(station);
                player.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
                player.setFallDistance(0.0F);
            }
        });
    }

    /** Tampilkan konfirmasi HUD kepada pemain yang baru berhasil bergabung ke tim. */
    void showTeamJoinedTitle(Player player, ArenaData arena, String team) {
        if (player == null || arena == null || team == null) return;
        ChatColor color = teamColor(team);
        String teamName = teamDisplayName(team);
        player.sendTitle(
                ChatColor.GOLD + "ARENA\n" + color + teamName + " TEAM",
                ChatColor.WHITE + "Kamu bergabung ke tim " + color + teamName
                        + ChatColor.WHITE + " • " + ChatColor.GRAY + arena.name,
                5, 55, 10
        );
    }

    /** Pesan pembuka preparation: satu title singkat, tanpa duplikasi ke chat. */
    void showPreparationIntro(MatchState state) {
        String title;
        String subtitle;
        if (state.mode.equals("tdm")) {
            title = ChatColor.GOLD + "TEAM DEATHMATCH";
            subtitle = ChatColor.WHITE + "Pilih class  •  Target "
                    + getConfig().getInt("matches.tdm-target-score", TDM_TARGET_SCORE) + " kill";
        } else {
            title = ChatColor.GOLD + "RELIC RUSH";
            subtitle = ChatColor.WHITE + "Rebut Relic  •  Antar ke markas";
        }
        for (UUID uuid : state.teams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.sendTitle(title, subtitle, 5, 55, 10);
        }
    }

    /** Final countdown title and note-block cues (5..2 prep, 1 start). */
    void showPreparationCountdown(MatchState state, int seconds) {
        for (UUID uuid : state.teams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            player.sendTitle(ChatColor.YELLOW + Integer.toString(seconds),
                    seconds == 1 ? ChatColor.GREEN + "MULAI!" : ChatColor.WHITE + "Bersiap!",
                    0, 20, 0);
            if (seconds == 1) {
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.85f);
            } else {
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f,
                        seconds == 5 ? 0.85f : seconds == 4 ? 0.95f : seconds == 3 ? 1.05f : 1.15f);
            }
        }
    }

    void showTdmGoTitle(MatchState state) {
        for (UUID uuid : state.teams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            player.sendTitle(ChatColor.GOLD + "ARENA\n" + ChatColor.YELLOW + "Teamdeathmatch",
                    ChatColor.GREEN + "GO!", 5, 35, 10);
        }
    }

    void launchMatch(ArenaData arena, MatchState state) {
        if (!state.preparing || state.ending) return;
        state.preparing = false;
        state.launching = true;
        updateMatchHud(state);
        // Hindari title/chat tambahan saat transisi; pengumuman match live muncul setelah arena siap.
        if (state.preparationTimer != null) state.preparationTimer.cancel();
        state.preparationTimer = null;

        for (Map.Entry<UUID, String> entry : state.teams.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            player.closeInventory();
            player.teleport(spawnLocation(arena, entry.getValue()));
        }

        // Lobby persiapan dihapus dan blok aslinya dipulihkan sebelum match live dimulai.
        state.lobbyRestoreTask = restoreMapBatched(Bukkit.getWorld(arena.worldName), arena, state.lobbyOriginalBlocks, () -> {
            state.lobbyRestoreTask = null;
            state.lobbyOriginalBlocks.clear();
            if (state.ending) return;
            beginLiveMatch(arena, state);
        });
    }

    void beginLiveMatch(ArenaData arena, MatchState state) {
        state.launching = false;
        state.running = true;
        state.secondsRemaining = getConfig().getInt("matches.duration-seconds", DEFAULT_MATCH_SECONDS);
        for (Map.Entry<UUID, String> entry : state.teams.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            if (state.mode.equals("tdm")) {
                giveClassLoadout(player, state.selectedClasses.getOrDefault(entry.getKey(), "knight"));
            } else {
                giveRelicRunnerLoadout(player, entry.getValue());
            }
        }
        updateMatchHud(state);
        if (state.mode.equals("tdm")) showTdmGoTitle(state);
        announce(state, ChatColor.GOLD + "Pertandingan " + state.mode.toUpperCase(Locale.ROOT) + " dimulai! Durasi "
                + formatTime(state.secondsRemaining) + ".");
        if (state.mode.equals("relic_rush")) {
            announce(state, ChatColor.YELLOW + "OBJEKTIF: dekati Relic di altar tengah untuk mengambilnya, lalu dekati sanctum markas RED/BLUE untuk mengamankan. "
                    + "Bunuh pembawa Relic untuk menjatuhkannya. Target: "
                    + getConfig().getInt("matches.relic-target-score", RELIC_TARGET_SCORE) + " capture.");
            state.relicToken = UUID.randomUUID().toString();
            state.relicCarrier = null;
            state.relicDropped = false;
            spawnRelic(arena, state);
        } else {
            announce(state, ChatColor.YELLOW + "OBJEKTIF: kalahkan pemain lawan. Setiap kill bernilai 1 poin; target "
                    + getConfig().getInt("matches.tdm-target-score", TDM_TARGET_SCORE) + " kill.");
        }

        state.timer = new BukkitRunnable() {
            @Override
            public void run() {
                state.secondsRemaining--;
                state.secondsElapsed++;
                if (state.secondsRemaining <= 0) {
                    finishMatch(state, arena, "Waktu habis.");
                } else {
                    int randomStuffInterval = Math.max(1, Math.min(600,
                            getConfig().getInt("matches.random-stuff-interval-seconds", 30)));
                    if (state.secondsElapsed % randomStuffInterval == 0) distributeRandomStuff(state);
                    updateMatchHud(state);
                    if (state.secondsRemaining % 60 == 0 || state.secondsRemaining <= 10) {
                        announce(state, ChatColor.GRAY + "Sisa waktu: " + formatTime(state.secondsRemaining));
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    void distributeRandomStuff(MatchState state) {
        List<Player> eligible = new ArrayList<>();
        for (UUID uuid : state.teams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !player.isDead()
                    && player.getGameMode() == GameMode.SURVIVAL) eligible.add(player);
        }
        if (eligible.isEmpty()) return;
        Collections.shuffle(eligible, ThreadLocalRandom.current());
        int maxRecipients = Math.max(1, Math.min(6,
                getConfig().getInt("matches.random-stuff-max-recipients", 6)));
        int recipients = ThreadLocalRandom.current().nextInt(1, Math.min(maxRecipients, eligible.size()) + 1);
        List<Material> pool = List.of(
                Material.GOLDEN_APPLE, Material.HONEY_BOTTLE, Material.ARROW,
                Material.SHIELD, Material.FIREWORK_ROCKET,
                Material.SNOWBALL, Material.FIRE_CHARGE, Material.ENDER_PEARL, Material.IRON_SWORD);
        for (int i = 0; i < recipients; i++) {
            Player recipient = eligible.get(i);
            // A new drop replaces any unused Random Stuff item owned by this player.
            removeRandomStuffItems(recipient, state);
            Material reward = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            String token = UUID.randomUUID().toString();
            ItemStack single = new ItemStack(reward, 1);
            ItemMeta meta = single.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "RANDOM STUFF: " + ChatColor.YELLOW
                        + randomStuffDisplayName(reward));
                meta.setLore(List.of(ChatColor.GRAY + randomStuffDescription(reward),
                        ChatColor.GRAY + "Klik kanan untuk menggunakan",
                        ChatColor.DARK_GRAY + "Hilang dalam 20 detik"));
                meta.getPersistentDataContainer().set(randomStuffKey, PersistentDataType.STRING, token);
                single.setItemMeta(meta);
            }
            Map<Integer, ItemStack> leftovers = recipient.getInventory().addItem(single);
            for (ItemStack leftover : leftovers.values()) {
                Item dropped = recipient.getWorld().dropItemNaturally(recipient.getLocation(), leftover);
                scheduleRandomStuffGroundRemoval(dropped);
            }
            state.randomStuffTokens.put(recipient.getUniqueId(), token);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                MatchState current = matches.get(state.arenaName);
                if (current == null || !token.equals(current.randomStuffTokens.get(recipient.getUniqueId()))) return;
                removeRandomStuffItems(recipient, current);
                current.randomStuffTokens.remove(recipient.getUniqueId());
                if (recipient.isOnline()) recipient.sendMessage(ChatColor.GRAY + "Random Stuff kedaluwarsa.");
            }, Math.max(1, getConfig().getInt("matches.random-stuff-expire-seconds", 20)) * 20L);
            recipient.sendMessage(ChatColor.GOLD + "RANDOM STUFF: " + ChatColor.YELLOW
                    + randomStuffDisplayName(reward) + ChatColor.GRAY + " (1 item, 20 detik)");
            recipient.playSound(recipient.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.25f);
        }
        announce(state, ChatColor.GOLD + "Random Stuff muncul untuk " + recipients + " pemain!");
    }

    private void removeRandomStuffItems(Player player, MatchState state) {
        String token = state.randomStuffTokens.get(player.getUniqueId());
        if (token == null) return;

        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !item.hasItemMeta()) continue;
            String found = item.getItemMeta().getPersistentDataContainer().get(randomStuffKey, PersistentDataType.STRING);
            if (token.equals(found)) inventory.setItem(slot, null);
        }

        // Also remove a matching item if it was dropped or ejected because the inventory was full.
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Item dropped) || !dropped.getItemStack().hasItemMeta()) continue;
                String found = dropped.getItemStack().getItemMeta().getPersistentDataContainer()
                        .get(randomStuffKey, PersistentDataType.STRING);
                if (token.equals(found)) dropped.remove();
            }
        }
    }

    private void scheduleRandomStuffGroundRemoval(Item dropped) {
        if (dropped == null || !dropped.isValid()) return;
        long lifetimeSeconds = Math.max(1, Math.min(30,
                getConfig().getInt("matches.random-stuff-ground-expire-seconds", 5)));
        long expiresAt = System.currentTimeMillis() + lifetimeSeconds * 1000L;
        dropped.getPersistentDataContainer().set(randomStuffDropExpiresKey, PersistentDataType.LONG, expiresAt);
        scheduleRandomStuffGroundRemoval(dropped.getUniqueId(), expiresAt);
    }

    private void scheduleRandomStuffGroundRemoval(UUID entityId, long expiresAt) {
        long remainingMillis = expiresAt - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof Item) entity.remove();
            return;
        }
        long delayTicks = Math.max(1L, (remainingMillis + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof Item item && item.isValid()) item.remove();
        }, delayTicks);
    }

    boolean isRandomStuffItem(ItemStack item) {
        return item != null && item.hasItemMeta() && randomStuffKey != null
                && item.getItemMeta().getPersistentDataContainer().has(randomStuffKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRandomStuffDrop(PlayerDropItemEvent event) {
        Item dropped = event.getItemDrop();
        ItemStack stack = dropped.getItemStack();
        if (!isRandomStuffItem(stack)) return;
        String token = stack.getItemMeta().getPersistentDataContainer()
                .get(randomStuffKey, PersistentDataType.STRING);
        UUID playerId = event.getPlayer().getUniqueId();
        boolean belongsToActiveMatch = matches.values().stream()
                .anyMatch(state -> token.equals(state.randomStuffTokens.get(playerId)));
        if (!belongsToActiveMatch) {
            event.setCancelled(true);
            return;
        }
        // Manual drops stay collectible, but only remain on the ground for five seconds.
        scheduleRandomStuffGroundRemoval(dropped);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRandomStuffChunkLoad(ChunkLoadEvent event) {
        // A dropped item may be unloaded before its timer fires. Its entity PDC
        // preserves the deadline so it is removed immediately (or re-scheduled) on reload.
        for (Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof Item item)) continue;
            Long expiresAt = item.getPersistentDataContainer().get(randomStuffDropExpiresKey, PersistentDataType.LONG);
            if (expiresAt == null) continue;
            scheduleRandomStuffGroundRemoval(item.getUniqueId(), expiresAt);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRandomStuffUse(PlayerInteractEvent event) {
        EquipmentSlot hand = event.getHand();
        Player player = event.getPlayer();
        ItemStack heldItem = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (!isRandomStuffItem(heldItem) && isRandomStuffItem(event.getItem())) {
            heldItem = event.getItem();
        }
        if (!isRandomStuffItem(heldItem)) return;

        // Semua input terhadap item Random Stuff dibatalkan agar item tidak melakukan
        // aksi vanilla. Hanya klik kanan dari main hand yang menjalankan efek custom.
        event.setCancelled(true);
        if (hand == EquipmentSlot.OFF_HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        activateRandomStuff(player, heldItem);
    }

    private void activateRandomStuff(Player player, ItemStack heldItem) {
        if (!isRandomStuffItem(heldItem)) return;
        String token = heldItem.getItemMeta().getPersistentDataContainer()
                .get(randomStuffKey, PersistentDataType.STRING);
        if (token == null) return;
        ArenaData arena = findArena(player.getLocation());
        MatchState state = arena == null ? null : matches.get(arena.name);
        if (state == null || !state.running || !state.teams.containsKey(player.getUniqueId())
                || !token.equals(state.randomStuffTokens.get(player.getUniqueId()))) return;

        Material reward = heldItem.getType();
        removeRandomStuffItems(player, state);
        state.randomStuffTokens.remove(player.getUniqueId());
        applyRandomStuffEffect(player, state, reward);
    }

    private String randomStuffDisplayName(Material reward) {
        return switch (reward) {
            case GOLDEN_APPLE -> "Field Ration";
            case HONEY_BOTTLE -> "Honey Guard";
            case ARROW -> "Fleet Arrow";
            case SHIELD -> "Aegis Shield";
            case FIREWORK_ROCKET -> "Power Signal";
            case SNOWBALL -> "Frost Orb";
            case FIRE_CHARGE -> "Ember Shot";
            case ENDER_PEARL -> "Rift Bomb";
            case IRON_SWORD -> "Duelist Blade";
            default -> reward.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private String randomStuffDescription(Material reward) {
        return switch (reward) {
            case GOLDEN_APPLE -> "Support: regenerasi singkat";
            case HONEY_BOTTLE -> "Support: absorption";
            case ARROW -> "Support: kecepatan meningkat";
            case SHIELD -> "Support: ketahanan meningkat";
            case FIREWORK_ROCKET -> "Support: kekuatan serangan meningkat";
            case SNOWBALL -> "Damage: proyektil frost yang memperlambat";
            case FIRE_CHARGE -> "Damage: fireball kecil dengan ledakan panas";
            case ENDER_PEARL -> "Damage: bom Rift dengan efek area";
            case IRON_SWORD -> "Damage: tebasan jarak dekat";
            default -> "Random Stuff";
        };
    }

    private void applyRandomStuffEffect(Player player, MatchState state, Material reward) {
        switch (reward) {
            case GOLDEN_APPLE -> player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 8 * 20, 1));
            case HONEY_BOTTLE -> player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 12 * 20, 0));
            case ARROW -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 8 * 20, 1));
            case SHIELD -> player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 6 * 20, 0));
            case FIREWORK_ROCKET -> player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 6 * 20, 0));
            case SNOWBALL -> launchRandomStuffProjectile(player, "frost", false);
            case FIRE_CHARGE -> launchRandomStuffProjectile(player, "ember", true);
            case ENDER_PEARL -> launchRandomStuffProjectile(player, "rift", false);
            case IRON_SWORD -> useRandomStuffDuelistBlade(player, state);
            default -> {
                player.sendMessage(ChatColor.RED + "Efek Random Stuff tidak dikenali.");
                return;
            }
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
        player.sendMessage(ChatColor.GREEN + randomStuffDisplayName(reward) + " digunakan!");
    }

    private void launchRandomStuffProjectile(Player player, String projectileType, boolean fireball) {
        Projectile projectile;
        if (fireball) {
            SmallFireball launched = player.launchProjectile(SmallFireball.class);
            launched.setIsIncendiary(false);
            launched.setYield(0.0F);
            launched.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(1.15D));
            projectile = launched;
        } else {
            Snowball launched = player.launchProjectile(Snowball.class);
            launched.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(1.45D));
            projectile = launched;
        }
        projectile.getPersistentDataContainer().set(randomStuffProjectileKey,
                PersistentDataType.STRING, projectileType);
        // A missed shot is short-lived; tagged projectiles cannot deal vanilla damage.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (projectile.isValid()) projectile.remove();
        }, 80L);
        player.getWorld().playSound(player.getLocation(), fireball
                ? org.bukkit.Sound.ENTITY_BLAZE_SHOOT : org.bukkit.Sound.ENTITY_SNOWBALL_THROW, 0.8f, 1.1f);
    }

    private void useRandomStuffDuelistBlade(Player player, MatchState state) {
        Vector direction = player.getEyeLocation().getDirection().normalize();
        RayTraceResult result = player.getWorld().rayTrace(
                player.getEyeLocation(), direction, 4.0D,
                FluidCollisionMode.NEVER, true, 0.75D,
                entity -> entity instanceof Player target && isRandomStuffEnemy(player, target, state));
        if (result == null || !(result.getHitEntity() instanceof Player target)) {
            player.sendMessage(ChatColor.GRAY + "Duelist Blade tidak mengenai lawan.");
            player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.2f);
            return;
        }
        target.damage(4.5D, player);
        target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1.0, 0),
                8, 0.25, 0.35, 0.25, 0.08);
        player.getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.9f, 1.0f);
    }

    private boolean isRandomStuffEnemy(Player shooter, Player target, MatchState expectedState) {
        if (shooter == null || target == null || !shooter.isOnline() || !target.isOnline()
                || shooter.isDead() || target.isDead() || shooter.getUniqueId().equals(target.getUniqueId())) return false;
        ArenaData shooterArena = findArena(shooter.getLocation());
        ArenaData targetArena = findArena(target.getLocation());
        if (shooterArena == null || targetArena == null || !shooterArena.name.equals(targetArena.name)) return false;
        MatchState state = matches.get(shooterArena.name);
        if (state == null || state != expectedState || !state.running) return false;
        String shooterTeam = state.teams.get(shooter.getUniqueId());
        String targetTeam = state.teams.get(target.getUniqueId());
        return shooterTeam != null && targetTeam != null && !shooterTeam.equals(targetTeam);
    }

    private void applyRandomStuffImpact(Player shooter, Location impact, String projectileType,
                                        Player directTarget, MatchState state) {
        if (shooter == null || state == null || impact == null || impact.getWorld() == null) return;
        switch (projectileType) {
            case "frost" -> {
                if (directTarget != null && isRandomStuffEnemy(shooter, directTarget, state)) {
                    directTarget.damage(3.0D, shooter);
                    directTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                    directTarget.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE,
                            directTarget.getLocation().add(0, 1.0, 0), 10, 0.25, 0.35, 0.25, 0.02);
                }
                impact.getWorld().playSound(impact, org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.65f, 1.7f);
            }
            case "ember" -> {
                damageRandomStuffRadius(shooter, state, impact, 1.65D, 4.75D, 0);
                impact.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
                        impact.clone().add(0, 0.15, 0), 22, 0.45, 0.35, 0.45, 0.025);
                impact.getWorld().playSound(impact, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.75f, 1.35f);
            }
            case "rift" -> {
                damageRandomStuffRadius(shooter, state, impact, 2.25D, 3.5D, 1);
                impact.getWorld().spawnParticle(org.bukkit.Particle.PORTAL,
                        impact.clone().add(0, 0.5, 0), 28, 0.65, 0.45, 0.65, 0.15);
                impact.getWorld().playSound(impact, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.25f);
            }
            default -> { }
        }
    }

    private void damageRandomStuffRadius(Player shooter, MatchState state, Location center,
                                         double radius, double damage, int knockbackLevel) {
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target) || !isRandomStuffEnemy(shooter, target, state)) continue;
            if (target.getLocation().distanceSquared(center) > radius * radius) continue;
            target.damage(damage, shooter);
            if (knockbackLevel > 0) {
                Vector push = target.getLocation().toVector().subtract(center.toVector());
                if (push.lengthSquared() > 1.0E-6D) {
                    push.normalize().multiply(0.28D).setY(0.12D);
                    target.setVelocity(target.getVelocity().add(push));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffProjectileDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getPersistentDataContainer().has(randomStuffProjectileKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRandomStuffProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String projectileType = projectile.getPersistentDataContainer()
                .get(randomStuffProjectileKey, PersistentDataType.STRING);
        if (projectileType == null) return;

        Player shooter = projectile.getShooter() instanceof Player player ? player : null;
        Location impact = projectile.getLocation().clone();
        projectile.remove();
        if (shooter == null || !shooter.isOnline()) return;
        ArenaData arena = findArena(shooter.getLocation());
        MatchState state = arena == null ? null : matches.get(arena.name);
        if (state == null || !state.running || !state.teams.containsKey(shooter.getUniqueId())) return;
        Player directTarget = event.getHitEntity() instanceof Player player ? player : null;
        applyRandomStuffImpact(shooter, impact, projectileType, directTarget, state);
    }

    private boolean playerUsesRandomStuffInMainHand(Player player) {
        return player != null && isRandomStuffItem(player.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffEntityUse(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack held = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (!isRandomStuffItem(held)) return;
        event.setCancelled(true);
        if (hand != EquipmentSlot.OFF_HAND) activateRandomStuff(player, held);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffBlockDamage(BlockDamageEvent event) {
        if (playerUsesRandomStuffInMainHand(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffBlockBreak(BlockBreakEvent event) {
        if (playerUsesRandomStuffInMainHand(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && playerUsesRandomStuffInMainHand(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffConsume(PlayerItemConsumeEvent event) {
        if (isRandomStuffItem(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffProjectile(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player
                && playerUsesRandomStuffInMainHand(player)) {
            event.setCancelled(true);
        }
    }

    void giveClassLoadout(Player player, String className) {
        player.setGameMode(GameMode.SURVIVAL);
        String selected = className == null ? "knight" : className.toLowerCase(Locale.ROOT);
        if (selected.equals("assasin")) selected = "assassin";

        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        // Each kit starts from a known health baseline. Tank receives +4 hearts;
        // PlayerSnapshot restores the player's previous base value after the match.
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(selected.equals("tank") ? 28.0D : 20.0D);
            player.setHealth(maxHealth.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);

        switch (selected) {
            case "archer" -> {
                player.getInventory().setItem(0, new ItemStack(Material.BOW));
                player.getInventory().setItem(1, new ItemStack(Material.ARROW, 48));
                player.getInventory().setItem(3, new ItemStack(Material.STONE_SWORD));
                player.getInventory().setArmorContents(new ItemStack[]{
                        new ItemStack(Material.LEATHER_BOOTS), new ItemStack(Material.LEATHER_LEGGINGS),
                        new ItemStack(Material.LEATHER_CHESTPLATE), new ItemStack(Material.LEATHER_HELMET)
                });
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory().setItem(2, classSkillItem(Material.FEATHER, "archer_volley",
                        ChatColor.GREEN + "Volley", "Lepaskan tiga panah sekaligus", "Cooldown: 20 detik | Membutuhkan 3 panah"));
                player.getInventory().setItem(7, classSkillItem(Material.SPECTRAL_ARROW, "archer_pinning_shot",
                        ChatColor.YELLOW + "Pinning Shot", "Panah berikutnya memperlambat lawan 2,5 detik", "Persiapan 10 detik | Cooldown 18 detik setelah panah dilepaskan"));
            }
            case "sniper" -> {
                player.getInventory().setItem(0, new ItemStack(Material.CROSSBOW));
                ItemStack harmingArrows = new ItemStack(Material.TIPPED_ARROW, 32);
                PotionMeta arrowMeta = (PotionMeta) harmingArrows.getItemMeta();
                arrowMeta.setBasePotionType(PotionType.HARMING); // Instant Damage I
                arrowMeta.setDisplayName(ChatColor.DARK_RED + "Arrow of Harming I");
                harmingArrows.setItemMeta(arrowMeta);
                player.getInventory().setItem(1, harmingArrows);
                player.getInventory().setItem(3, new ItemStack(Material.STONE_SWORD));
                player.getInventory().setItem(4, new ItemStack(Material.SPYGLASS));
                player.getInventory().setArmorContents(new ItemStack[]{
                        new ItemStack(Material.LEATHER_BOOTS), new ItemStack(Material.CHAINMAIL_LEGGINGS),
                        new ItemStack(Material.CHAINMAIL_CHESTPLATE), new ItemStack(Material.LEATHER_HELMET)
                });
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 6));
                player.getInventory().setItem(2, classSkillItem(Material.CLOCK, "sniper_mark",
                        ChatColor.AQUA + "Mark Target", "Tandai lawan yang kamu bidik", "Glowing + menerima 20% damage tambahan selama 8 detik", "Tekan Q untuk AIM LOCK | Hapus setelah 3 panah sniper mengenai target", "Jarak 32 blok | Cooldown: 25 detik"));
                player.getInventory().setItem(7, classSkillItem(Material.AMETHYST_SHARD, "sniper_focus_shot",
                        ChatColor.LIGHT_PURPLE + "Focus Mode", "Semua panah +15% damage dan lebih cepat", "Durasi: 8 detik | Cooldown: 25 detik"));
            }
            case "tank" -> {
                // High survivability, deliberately lower melee damage than Knight.
                player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                player.getInventory().addItem(new ItemStack(Material.SHIELD));
                player.getInventory().setArmorContents(new ItemStack[]{
                        new ItemStack(Material.DIAMOND_BOOTS), new ItemStack(Material.DIAMOND_LEGGINGS),
                        new ItemStack(Material.DIAMOND_CHESTPLATE), new ItemStack(Material.DIAMOND_HELMET)
                });
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory().setItem(2, classSkillItem(Material.IRON_BLOCK, "tank_endurance",
                        ChatColor.GOLD + "Endurance", "Resistance III selama 5 detik", "Gerak melambat | Cooldown: 45 detik"));
                player.getInventory().setItem(7, classSkillItem(Material.ANVIL, "tank_ground_slam",
                        ChatColor.GOLD + "Ground Slam", "Gelombang kejut area radius 5 blok", "Knockback + Slowness II selama 2 detik | Cooldown: 30 detik"));
            }
            case "assassin" -> {
                player.getInventory().addItem(new ItemStack(Material.GOLDEN_SWORD));
                player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory().setArmorContents(new ItemStack[]{
                        new ItemStack(Material.LEATHER_BOOTS), new ItemStack(Material.LEATHER_LEGGINGS),
                        new ItemStack(Material.LEATHER_CHESTPLATE), new ItemStack(Material.LEATHER_HELMET)
                });
                player.getInventory().setItem(2, classSkillItem(Material.SUGAR, "assassin_burst",
                        ChatColor.DARK_RED + "Burst", "Speed III + Haste II selama 3 detik", "Strength II selama 2 detik | Cooldown: 30 detik"));
                player.getInventory().setItem(7, classSkillItem(Material.FERMENTED_SPIDER_EYE, "assassin_venom_edge",
                        ChatColor.DARK_GREEN + "Venom Edge", "Serangan melee berikutnya memberi Poison II", "Berlaku 8 detik | Cooldown: 22 detik"));
            }
            case "ninja" -> {
                player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
                player.getInventory().addItem(new ItemStack(Material.SNOWBALL, 7));
                player.getInventory().setArmorContents(new ItemStack[]{
                        new ItemStack(Material.LEATHER_BOOTS), new ItemStack(Material.LEATHER_LEGGINGS),
                        new ItemStack(Material.LEATHER_CHESTPLATE), new ItemStack(Material.LEATHER_HELMET)
                });
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory().setItem(2, classSkillItem(Material.GRAY_DYE, "ninja_vanish",
                        ChatColor.DARK_GREEN + "Smoke Bomb", "Menghilang bersama armor dan item", "Durasi: 6 detik | Cooldown: 34 detik"));
                player.getInventory().setItem(3, classSkillItem(Material.FEATHER, "ninja_shadow_step",
                        ChatColor.GREEN + "Shadow Step", "Teleport aman ke arah pandangan", "Jarak hingga 8 blok | Cooldown: 20 detik"));
            }
            case "boxer" -> {
                // Shotgun-inspired close-range pressure: unarmed, quick chains, no shield.
                player.getInventory().setArmorContents(new ItemStack[]{
                        new ItemStack(Material.LEATHER_BOOTS), new ItemStack(Material.CHAINMAIL_LEGGINGS),
                        new ItemStack(Material.CHAINMAIL_CHESTPLATE), new ItemStack(Material.LEATHER_HELMET)
                });
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory().setItem(2, classSkillItem(Material.SLIME_BALL, "boxer_rapid_jab",
                        ChatColor.LIGHT_PURPLE + "Rapid Jab", "Tiga pukulan cepat ke arah depan", "Total dasar 3,45 damage | Jarak 3,4 blok | Cooldown: 20 detik"));
                player.getInventory().setItem(7, classSkillItem(Material.FIREWORK_STAR, "boxer_barrage",
                        ChatColor.LIGHT_PURPLE + "Barrage", "Enam pukulan cepat pada satu target", "Slow I | 8% peluang Weakness I | Cooldown: 28 detik"));
            }
            case "demoman" -> {
                // Explosives specialist. Skill effects are custom entity-only blasts;
                // they never create vanilla TNT or modify arena blocks.
                player.getInventory().setItem(0, classSkillItem(Material.TNT_MINECART, "demoman_primary_bomb",
                        ChatColor.RED + "Pocket Bomb", "Lempar satu bom ke arah pandangan", "Damage maksimum 3,4 | Radius 3 blok | Cooldown: 2 detik"));
                player.getInventory().setArmorContents(new ItemStack[]{
                        new ItemStack(Material.CHAINMAIL_BOOTS), new ItemStack(Material.LEATHER_LEGGINGS),
                        new ItemStack(Material.CHAINMAIL_CHESTPLATE), new ItemStack(Material.LEATHER_HELMET)
                });
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory().setItem(2, classSkillItem(Material.FIRE_CHARGE, "demoman_dynamite",
                        ChatColor.RED + "Dynamite Burst", "Luncurkan tiga bom menyebar satu per satu", "Maksimum 4,8 damage per bom | Knockback rendah | Jeda 0,2 detik | Cooldown: 10 detik"));
                player.getInventory().setItem(7, classSkillItem(Material.TNT, "demoman_blast_mine",
                        ChatColor.DARK_RED + "Blast Mine", "Pasang penanda Creeper yang memicu saat lawan mendekat", "Aktif 20 detik | Maksimal 1 ranjau | Cooldown: 20 detik"));
            }
            default -> {
                // Knight remains the balanced baseline: iron sword, shield, chainmail.
                player.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
                player.getInventory().addItem(new ItemStack(Material.SHIELD));
                player.getInventory().setArmorContents(new ItemStack[]{
                        new ItemStack(Material.CHAINMAIL_BOOTS), new ItemStack(Material.CHAINMAIL_LEGGINGS),
                        new ItemStack(Material.CHAINMAIL_CHESTPLATE), new ItemStack(Material.CHAINMAIL_HELMET)
                });
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory().setItem(2, classSkillItem(Material.IRON_INGOT, "knight_guard",
                        ChatColor.RED + "Power Strike", "Serangan kuat ke lawan di depan", "Damage tinggi | Cooldown: 26 detik"));
                player.getInventory().setItem(7, classSkillItem(Material.GOLDEN_APPLE, "knight_rally",
                        ChatColor.AQUA + "Rally", "Beri Resistance I kepada rekan tim dalam radius 8 blok", "Durasi 4 detik | Cooldown: 32 detik"));
            }
        }
        applyClassEffects(player, selected);
        markKitItemsUnbreakable(player);
    }

    /** Applies passive class traits and is also called after death when potion effects are cleared. */
    void applyClassEffects(Player player, String className) {
        String selected = className == null ? "knight" : className.toLowerCase(Locale.ROOT);
        if (selected.equals("assasin")) selected = "assassin";
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(selected.equals("tank") ? 28.0D : 20.0D);
            player.setHealth(maxHealth.getValue());
        }
        // Attack-speed traits use the attribute itself, not Haste's mining-speed effect.
        // Boxer gets a faster unarmed rhythm; every non-Boxer/non-Assassin class returns to 4.0.
        var attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) {
            double classAttackSpeed = selected.equals("assassin") ? 5.5D
                    : selected.equals("boxer") ? 6.0D : 4.0D;
            attackSpeed.setBaseValue(classAttackSpeed);
        }

        switch (selected) {
            case "sniper" -> player.addPotionEffect(
                    new PotionEffect(PotionEffectType.STRENGTH, 20 * 60 * 60, 0, false, false, true));
            case "assassin" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 60 * 60, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60 * 60, 1, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 60 * 60, 0, false, false, true));
            }
            case "ninja" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 60 * 60, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60 * 60, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 60 * 60, 1, false, false, true));
            }
            case "boxer" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 60 * 60, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60 * 60, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 60 * 60, 0, false, false, true));
            }
            default -> { }
        }
    }

    /** Relic Rush uses one equal objective kit; it does not inherit TDM class roles. */
    void giveRelicRunnerLoadout(Player player, String team) {
        player.setGameMode(GameMode.SURVIVAL);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0D);
            player.setHealth(maxHealth.getValue());
        }
        // Relic Rush has no class bonuses; clear the Assassin attack-speed trait.
        var attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) attackSpeed.setBaseValue(4.0D);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItem(0, new ItemStack(Material.STONE_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.SHIELD));
        player.getInventory().setItem(8, new ItemStack(Material.BREAD, 8));
        org.bukkit.Color tint = team.equals("red") ? org.bukkit.Color.fromRGB(220, 55, 55)
                : org.bukkit.Color.fromRGB(65, 105, 220);
        player.getInventory().setArmorContents(new ItemStack[]{
                dyedLeather(Material.LEATHER_BOOTS, tint),
                dyedLeather(Material.LEATHER_LEGGINGS, tint),
                dyedLeather(Material.LEATHER_CHESTPLATE, tint),
                dyedLeather(Material.LEATHER_HELMET, tint)
        });
        markKitItemsUnbreakable(player);
        player.sendMessage(ChatColor.GOLD + "Kit Relic Runner: pedang batu, shield, 8 roti, dan armor tim.");
    }

    ItemStack dyedLeather(Material material, org.bukkit.Color tint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(tint);
            item.setItemMeta(leatherMeta);
        }
        return item;
    }

    void giveClassSelector(Player player) {
        ItemStack selector = new ItemStack(Material.COMPASS);
        ItemMeta meta = selector.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Pilih Class");
            meta.setLore(List.of(ChatColor.GRAY + "Klik kanan untuk membuka menu class", ChatColor.DARK_GRAY + "Hanya aktif selama persiapan"));
            meta.getPersistentDataContainer().set(classSelectorKey, PersistentDataType.BYTE, (byte) 1);
            selector.setItemMeta(meta);
        }
        player.getInventory().setItem(4, selector);
    }

    boolean isClassSelector(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(classSelectorKey, PersistentDataType.BYTE);
    }

    void openClassMenuFor(Player player) {
        for (MatchState state : matches.values()) {
            if (state.preparing && state.teams.containsKey(player.getUniqueId())) {
                if (!state.mode.equals("tdm")) {
                    player.sendMessage(ChatColor.YELLOW + "Relic Rush tidak menggunakan class TDM. Semua pemain memakai Relic Runner kit.");
                    return;
                }
                ArenaData arena = arenas.get(state.arenaName);
                if (arena != null) openClassMenu(player, arena, state);
                return;
            }
        }
        player.sendMessage(ChatColor.RED + "Menu class hanya tersedia di lobby persiapan. Jalankan /arena join lalu tunggu admin menjalankan /arena start.");
    }

    void openClassMenu(Player player, ArenaData arena, MatchState state) {
        openClassMenu(player, arena, state, 0);
    }

    void openClassMenu(Player player, ArenaData arena, MatchState state, int requestedPage) {
        final int page = Math.max(0, Math.min(1, requestedPage));
        ClassMenuHolder holder = new ClassMenuHolder(arena.name, page);
        Inventory menu = Bukkit.createInventory(holder, 27,
                ChatColor.DARK_AQUA + "Pilih Class " + ChatColor.GRAY + "(" + (page + 1) + "/2)");
        holder.inventory = menu;

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(ChatColor.DARK_GRAY + " ");
            filler.setItemMeta(fillerMeta);
        }
        for (int slot = 0; slot < menu.getSize(); slot++) menu.setItem(slot, filler);

        // Four class cards per page, with one visual separator slot between cards.
        int[] classSlots = {10, 12, 14, 16};
        String[] classIds = page == 0
                ? new String[]{"knight", "tank", "archer", "sniper"}
                : new String[]{"assassin", "ninja", "boxer", "demoman"};
        for (int i = 0; i < classIds.length; i++) {
            String classId = classIds[i];
            int slot = classSlots[i];
            holder.classBySlot.put(slot, classId);
            menu.setItem(slot, classMenuCard(classId));
        }

        menu.setItem(18, page > 0
                ? classMenuNavigationItem(Material.ARROW, ChatColor.YELLOW + "Halaman Sebelumnya", "Kembali ke halaman class sebelumnya")
                : classMenuNavigationItem(Material.BARRIER, ChatColor.DARK_GRAY + "Tidak Ada Halaman Sebelumnya", "Kamu sedang di halaman pertama"));
        menu.setItem(22, classMenuNavigationItem(Material.BOOK, ChatColor.AQUA + "Halaman " + (page + 1) + " dari 2",
                page == 0 ? "Class dasar" : "Class spesialis"));
        menu.setItem(26, page < 1
                ? classMenuNavigationItem(Material.ARROW, ChatColor.GREEN + "Next — Class Lainnya", "Klik untuk melihat Assassin, Ninja, Boxer, dan Demoman")
                : classMenuNavigationItem(Material.BARRIER, ChatColor.DARK_GRAY + "Tidak Ada Halaman Berikutnya", "Kamu sedang di halaman terakhir"));
        player.openInventory(menu);
    }

    void openClassMenuPage(Player player, String arenaName, int requestedPage) {
        if (requestedPage < 0 || requestedPage > 1) return;
        for (MatchState state : matches.values()) {
            if (!state.preparing || !state.teams.containsKey(player.getUniqueId())
                    || !state.arenaName.equalsIgnoreCase(arenaName)) continue;
            if (!"tdm".equals(state.mode)) {
                player.sendMessage(ChatColor.YELLOW + "Menu class hanya tersedia pada Team Deathmatch.");
                return;
            }
            ArenaData arena = arenas.get(state.arenaName.toLowerCase(Locale.ROOT));
            if (arena != null) openClassMenu(player, arena, state, requestedPage);
            return;
        }
        player.sendMessage(ChatColor.RED + "Sesi pemilihan class sudah tidak tersedia.");
    }

    private ItemStack classMenuCard(String classId) {
        return switch (classId) {
            case "knight" -> classMenuItem(Material.IRON_SWORD, ChatColor.RED + "Knight",
                    "Frontliner seimbang", "Pedang besi, shield, armor chainmail", "20 HP | Damage dan pertahanan seimbang", "Skill: Power Strike + Rally");
            case "tank" -> classMenuItem(Material.DIAMOND_CHESTPLATE, ChatColor.GOLD + "Tank",
                    "Benteng berjalan", "Full diamond, shield, pedang kayu", "+4 hati | Serangan lebih lemah", "Skill: Endurance + Ground Slam");
            case "archer" -> classMenuItem(Material.BOW, ChatColor.GREEN + "Archer",
                    "Penyerang jarak menengah", "Bow, 48 panah, pedang batu", "Armor kulit | Memberi cover untuk tim", "Skill: Volley + Pinning Shot");
            case "sniper" -> classMenuItem(Material.CROSSBOW, ChatColor.AQUA + "Sniper",
                    "Penembak jarak jauh", "Crossbow, 32 panah, spyglass", "Spesialis serangan presisi jarak jauh", "Skill: Mark Target + Focus Mode");
            case "assassin" -> classMenuItem(Material.GOLDEN_SWORD, ChatColor.DARK_RED + "Assassin",
                    "Penyerang cepat", "Pedang emas dan armor kulit", "Haste I + Speed II + Jump Boost I", "Skill: Burst + Venom Edge");
            case "ninja" -> classMenuItem(Material.ENDER_PEARL, ChatColor.DARK_GREEN + "Ninja",
                    "Pengacau lincah", "Pedang batu, 7 snowball, armor kulit", "Mobilitas dan stealth", "Skill: Smoke Bomb + Shadow Step");
            case "boxer" -> classMenuItem(Material.LEATHER_CHESTPLATE, ChatColor.LIGHT_PURPLE + "Boxer",
                    "Petarung tangan kosong", "Rangkaian pukulan jarak dekat", "Strength I + Speed I + Haste I", "Skill: Rapid Jab + Barrage");
            case "demoman" -> classMenuItem(Material.TNT, ChatColor.RED + "Demoman",
                    "Spesialis pengendali area", "Pocket Bomb + armor campuran", "Ledakan khusus tanpa merusak terrain", "Skill: Dynamite Burst + Blast Mine");
            default -> classMenuItem(Material.BARRIER, ChatColor.RED + "Class tidak ditemukan", "Silakan laporkan masalah ini kepada admin");
        };
    }

    private ItemStack classMenuNavigationItem(Material material, String name, String... lore) {
        return classMenuItem(material, name, lore);
    }

    ItemStack classMenuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lines = new ArrayList<>();
            for (String line : lore) lines.add(ChatColor.GRAY + line);
            meta.setLore(lines);
            item.setItemMeta(meta);
        }
        return item;
    }

    ItemStack classSkillItem(Material material, String skillId, String displayName, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            List<String> lines = new ArrayList<>();
            lines.add(ChatColor.YELLOW + "Klik kanan untuk menggunakan skill");
            for (String line : lore) lines.add(ChatColor.GRAY + line);
            meta.setLore(lines);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(classSkillKey, PersistentDataType.STRING, skillId);
            item.setItemMeta(meta);
        }
        return item;
    }

    String readClassSkill(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(classSkillKey, PersistentDataType.STRING);
    }

    void markKitItemsUnbreakable(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) markItemUnbreakable(item);
        for (ItemStack item : inventory.getArmorContents()) markItemUnbreakable(item);
        markItemUnbreakable(inventory.getItemInOffHand());
    }

    private void markItemUnbreakable(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.isUnbreakable()) return;
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
    }

    void selectClassByName(Player player, String input) {
        String selected = input.toLowerCase(Locale.ROOT);
        if (selected.equals("assasin")) selected = "assassin";
        if (!Set.of("knight", "archer", "sniper", "tank", "assassin", "ninja", "boxer", "demoman").contains(selected)) {
            player.sendMessage(ChatColor.YELLOW + "Class tersedia: knight, tank, archer, sniper, assassin, ninja, boxer, demoman.");
            return;
        }
        for (MatchState state : matches.values()) {
            if (!state.preparing || !state.teams.containsKey(player.getUniqueId())) continue;
            if (!state.mode.equals("tdm")) {
                player.sendMessage(ChatColor.YELLOW + "Pemilihan class hanya tersedia pada Team Deathmatch.");
                return;
            }
            state.selectedClasses.put(player.getUniqueId(), selected);
            updateMatchHud(state);
            player.sendMessage(ChatColor.GREEN + "Class kamu dipilih: " + selected.toUpperCase(Locale.ROOT) + ".");
            player.closeInventory();
            return;
        }
        player.sendMessage(ChatColor.RED + "Kamu hanya dapat memilih class di lobby persiapan.");
    }


    void stopMatch(CommandSender sender, String arenaName, String reason) {
        ArenaData arena = arenas.get(arenaName.toLowerCase(Locale.ROOT));
        MatchState state = matches.get(arenaName.toLowerCase(Locale.ROOT));
        if (arena == null || state == null) {
            sender.sendMessage(ChatColor.RED + "Tidak ada pertandingan untuk arena itu.");
            return;
        }
        announce(state, ChatColor.YELLOW + reason);
        stopAndRestoreMatch(arena.name, () -> sender.sendMessage(ChatColor.GREEN + "Pertandingan dihentikan dan perubahan blok dipulihkan."));
    }

    void finishMatch(MatchState state, ArenaData arena, String reason) {
        if (!state.running) return;
        state.running = false;
        if (state.timer != null) state.timer.cancel();
        List<String> configuredTeams = teamNamesFor(arena);
        int highestScore = configuredTeams.stream().mapToInt(team -> state.teamScores.getOrDefault(team, 0)).max().orElse(0);
        List<String> winners = configuredTeams.stream()
                .filter(team -> state.teamScores.getOrDefault(team, 0) == highestScore).toList();
        String result = state.singlePlayerMode ? ""
                : winners.size() == 1 ? "Tim " + winners.get(0).toUpperCase(Locale.ROOT) + " menang!" : "Seri!";
        announce(state, ChatColor.GOLD + "Pertandingan selesai. " + reason
                + (result.isEmpty() ? "" : " " + result)
                + " Skor: " + scoreSummary(state, configuredTeams) + ".");
        if (state.mode.equals("tdm") && !state.singlePlayerMode) {
            for (UUID uuid : state.teams.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                String playerTeam = state.teams.get(uuid);
                if (winners.size() > 1) {
                    player.sendTitle(ChatColor.GOLD + "ARENA\n" + ChatColor.YELLOW + "DRAW",
                            ChatColor.WHITE + "Match tied!", 5, 75, 15);
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.0f);
                } else if (winners.contains(playerTeam)) {
                    player.sendTitle(ChatColor.GOLD + "ARENA\n" + ChatColor.GREEN + "STREAK!",
                            ChatColor.WHITE + "Your team won!", 5, 85, 20);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
                } else {
                    player.sendTitle(ChatColor.GOLD + "ARENA\n" + ChatColor.DARK_RED + "DOWN...",
                            ChatColor.WHITE + "Your team lost!", 5, 85, 20);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.85f);
                }
            }
        }
        stopAndRestoreMatch(arena.name, () -> {
            for (UUID uuid : new ArrayList<>(state.teams.keySet())) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) player.sendMessage(ChatColor.GRAY + "State arena telah dikembalikan ke awal pertandingan.");
            }
        });
    }

    String scoreSummary(MatchState state, List<String> teams) {
        List<String> parts = new ArrayList<>();
        for (String team : teams) {
            parts.add(teamColor(team) + team.toUpperCase(Locale.ROOT) + ChatColor.WHITE + " "
                    + state.teamScores.getOrDefault(team, 0));
        }
        return String.join(ChatColor.DARK_GRAY + " | ", parts);
    }

    void addScore(MatchState state, ArenaData arena, String team, int amount) {
        int updated = state.teamScores.merge(team, amount, Integer::sum);
        if (team.equals("red")) state.redScore = updated;
        if (team.equals("blue")) state.blueScore = updated;
        updateMatchHud(state);
        announce(state, ChatColor.YELLOW + "Skor: " + scoreSummary(state, teamNamesFor(arena)) + ".");
        int target = state.mode.equals("relic_rush")
                ? getConfig().getInt("matches.relic-target-score", RELIC_TARGET_SCORE)
                : getConfig().getInt("matches.tdm-target-score", TDM_TARGET_SCORE);
        if (updated >= target) finishMatch(state, arena, "Target skor tercapai.");
    }

    void spawnRelic(ArenaData arena, MatchState state) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null || state.relicToken == null) return;
        state.relicCarrier = null;
        state.relicDropped = false;
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Relic " + arena.name);
            meta.getPersistentDataContainer().set(relicKey, PersistentDataType.STRING, state.relicToken);
            stack.setItemMeta(meta);
        }
        // Spawn on the shrine floor beside the raised central pillar, not on top of its lantern.
        // The adjacent polished-blackstone floor is reachable from ground level.
        int relicFloorY = world.getHighestBlockYAt(arena.centerX, arena.centerZ + 2);
        Item relicItem = world.dropItem(
                new Location(world, arena.centerX + 0.5D, relicFloorY + 1.15D, arena.centerZ + 2.5D), stack);
        relicItem.setPickupDelay(Integer.MAX_VALUE);
    }

    boolean isRelic(ItemStack item, MatchState state) {
        if (item == null || state == null || state.relicToken == null
                || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String token = meta.getPersistentDataContainer().get(relicKey, PersistentDataType.STRING);
        return state.relicToken.equals(token);
    }

    void consumeHandItem(Player player, EquipmentSlot hand) {
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (item.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(null);
            else player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
        }
    }

    void removeRelicItems(ArenaData arena, MatchState state) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null || state == null) return;
        for (Item item : world.getEntitiesByClass(Item.class)) {
            if (isRelic(item.getItemStack(), state)) item.remove();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (isRelic(stack, state)) player.getInventory().setItem(slot, null);
            }
            if (isRelic(player.getInventory().getItemInOffHand(), state)) {
                player.getInventory().setItemInOffHand(null);
            }
        }
        state.relicCarrier = null;
        state.relicDropped = false;
    }

    /** Kirim pengumuman ke chat peserta; title hanya dipakai untuk transisi match. */
    void announce(MatchState state, String message) {
        for (UUID uuid : state.teams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            player.sendMessage(message);
        }
    }

    /** Membuat atau memperbarui sidebar yang menampilkan mode, tim, waktu, dan skor. */
    void updateMatchHud(MatchState state) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        boolean preparationView = state.preparing || state.launching;
        int targetScore = state.mode.equals("relic_rush")
                ? getConfig().getInt("matches.relic-target-score", RELIC_TARGET_SCORE)
                : getConfig().getInt("matches.tdm-target-score", TDM_TARGET_SCORE);

        for (UUID uuid : state.teams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            Scoreboard board = state.hudScoreboards.get(uuid);
            Objective objective = state.hudObjectives.get(uuid);
            if (board == null || objective == null) {
                board = manager.getNewScoreboard();
                String title = state.mode.equals("relic_rush") ? "RELIC RUSH" : "TEAM DEATHMATCH";
                objective = board.registerNewObjective("arena_hud", "dummy", ChatColor.GOLD + title);
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                objective.numberFormat(NumberFormat.blank());
                state.previousScoreboards.put(uuid, player.getScoreboard());
                state.hudScoreboards.put(uuid, board);
                state.hudObjectives.put(uuid, objective);
                player.setScoreboard(board);
            }

            refreshTeamMarkers(board, state);
            for (String entry : new HashSet<>(board.getEntries())) {
                board.resetScores(entry);
            }

            String team = state.teams.getOrDefault(uuid, "?").toUpperCase(Locale.ROOT);
            String className = state.selectedClasses.getOrDefault(uuid, "knight").toUpperCase(Locale.ROOT);
            String timerLine = preparationView
                    ? ChatColor.GRAY + "Mulai: " + ChatColor.YELLOW + formatTime(state.preparationSecondsRemaining)
                    : ChatColor.GRAY + "Waktu: " + ChatColor.YELLOW + formatTime(state.secondsRemaining);
            List<String> lines = new ArrayList<>();

            if (preparationView) {
                // Preparation hanya menampilkan hal yang perlu diketahui pemain.
                lines.add(timerLine);
                for (String listedTeam : teamNamesFor(arenas.get(state.arenaName))) {
                    lines.add(teamColor(listedTeam) + listedTeam.toUpperCase(Locale.ROOT) + ": "
                            + ChatColor.WHITE + state.teamScores.getOrDefault(listedTeam, 0) + "/" + targetScore);
                }
                lines.add(ChatColor.GRAY + "Tim: " + teamColor(team.toLowerCase(Locale.ROOT)) + team);
                if (state.mode.equals("tdm")) {
                    lines.add(ChatColor.GRAY + "Class: " + ChatColor.WHITE + className);
                } else {
                    lines.add(ChatColor.GOLD + "Relic: " + ChatColor.WHITE + "Belum dimulai");
                }
            } else {
                String mode = state.mode.replace('_', ' ').toUpperCase(Locale.ROOT);
                String statusLine = ChatColor.DARK_GRAY + "Arena Match";
                lines.add(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + mode);
                lines.add(timerLine);
                for (String listedTeam : teamNamesFor(arenas.get(state.arenaName))) {
                    lines.add(teamColor(listedTeam) + listedTeam.toUpperCase(Locale.ROOT) + ": "
                            + ChatColor.WHITE + state.teamScores.getOrDefault(listedTeam, 0) + "/" + targetScore);
                }
                lines.add(ChatColor.GRAY + "Tim: " + teamColor(team.toLowerCase(Locale.ROOT)) + team);
                if (state.mode.equals("relic_rush")) {
                    lines.add(ChatColor.GOLD + "Misi: " + ChatColor.WHITE + "Antar Relic");
                    String relicStatus;
                    if (state.relicCarrier != null) {
                        Player carrier = Bukkit.getPlayer(state.relicCarrier);
                        String carrierTeam = state.teams.getOrDefault(state.relicCarrier, "?").toUpperCase(Locale.ROOT);
                        relicStatus = carrier == null ? "Tidak diketahui" : carrier.getName() + " (" + carrierTeam + ")";
                    } else {
                        relicStatus = state.relicDropped ? "Terjatuh" : "Di altar tengah";
                    }
                    lines.add(ChatColor.GOLD + "Relic: " + ChatColor.WHITE + relicStatus);
                } else {
                    lines.add(ChatColor.GRAY + "Class: " + ChatColor.WHITE + className);
                    lines.add(ChatColor.GOLD + "Misi: " + ChatColor.WHITE + "Capai target kill");
                }
                lines.add(statusLine);
            }

            int score = lines.size();
            for (String line : lines) {
                objective.getScore(line).setScore(score--);
            }
        }
        refreshRelicGlow(state);
    }

    /** Efek glowing dibentuk per-viewer melalui team pada scoreboard HUD pribadi. */
    void refreshRelicGlow(MatchState state) {
        if (state == null || !state.mode.equals("relic_rush")) return;
        String carrierTeam = state.relicCarrier == null ? null : state.teams.get(state.relicCarrier);
        Player carrier = state.relicCarrier == null ? null : Bukkit.getPlayer(state.relicCarrier);

        for (UUID viewerId : state.teams.keySet()) {
            Player viewer = Bukkit.getPlayer(viewerId);
            Scoreboard board = state.hudScoreboards.get(viewerId);
            if (viewer == null || board == null) continue;

            Team glowTeam = board.getTeam("arena_relic_glow");
            if (glowTeam == null) glowTeam = board.registerNewTeam("arena_relic_glow");
            for (String entry : new HashSet<>(glowTeam.getEntries())) {
                glowTeam.removeEntry(entry);
            }

            if (carrier != null && carrier.isOnline()) {
                boolean ally = state.teams.get(viewerId) != null
                        && state.teams.get(viewerId).equals(carrierTeam);
                glowTeam.setColor(ally ? ChatColor.GREEN : ChatColor.RED);
                glowTeam.setPrefix("red".equals(carrierTeam) ? ChatColor.RED + "[RED] " : ChatColor.BLUE + "[BLUE] ");
                glowTeam.addEntry(carrier.getName());
            }
        }

        // Glow flag ada pada entity; warna aktualnya berasal dari team scoreboard tiap viewer.
        for (UUID memberId : state.teams.keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) member.setGlowing(carrier != null && memberId.equals(state.relicCarrier));
        }
    }

    /**
     * Memberi penanda tim lewat nametag dan daftar pemain (tab list).
     * Karena setiap peserta memakai scoreboard HUD pribadi, keempat nama tim
     * didaftarkan pada setiap scoreboard agar label tim selalu konsisten.
     */
    void refreshTeamMarkers(Scoreboard board, MatchState state) {
        for (String teamName : List.of("red", "blue", "green", "yellow")) {
            Team scoreboardTeam = board.getTeam("arena_" + teamName);
            if (scoreboardTeam == null) scoreboardTeam = board.registerNewTeam("arena_" + teamName);
            configureTeamMarker(scoreboardTeam, teamColor(teamName), "[" + teamName.toUpperCase(Locale.ROOT) + "] ");
            for (Map.Entry<UUID, String> entry : state.teams.entrySet()) {
                if (!teamName.equals(entry.getValue())) continue;
                Player member = Bukkit.getPlayer(entry.getKey());
                if (member != null) scoreboardTeam.addEntry(member.getName());
            }
        }
    }

    void configureTeamMarker(Team team, ChatColor color, String prefix) {
        team.setPrefix(color + prefix + ChatColor.RESET);
        team.setColor(color);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);

        // Hindari entri pemain lama jika roster pernah berubah sebelum match dimulai.
        for (String entry : new HashSet<>(team.getEntries())) {
            team.removeEntry(entry);
        }
    }

    /** Kembalikan scoreboard lama saat match berakhir atau dihentikan. */
    void clearMatchHud(MatchState state) {
        for (UUID uuid : state.teams.keySet()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.setGlowing(false);
        }
        for (Map.Entry<UUID, Scoreboard> entry : state.previousScoreboards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) player.setScoreboard(entry.getValue());
        }
        state.previousScoreboards.clear();
        state.hudScoreboards.clear();
        state.hudObjectives.clear();
    }

    String formatTime(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        return String.format(Locale.ROOT, "%02d:%02d", safeSeconds / 60, safeSeconds % 60);
    }

    Location spawnLocation(ArenaData arena, String team) {
        World world = Bukkit.getWorld(arena.worldName);
        int spawnOffset = arena.spawnOffset;
        Location spawn = switch (team.toLowerCase(Locale.ROOT)) {
            case "red" -> new Location(world, arena.centerX - spawnOffset + 0.5D,
                    arena.redY + 2.0D, arena.centerZ + 0.5D, -90.0F, 0.0F);
            case "blue" -> new Location(world, arena.centerX + spawnOffset + 0.5D,
                    arena.blueY + 2.0D, arena.centerZ + 0.5D, 90.0F, 0.0F);
            case "green" -> new Location(world, arena.centerX + 0.5D,
                    world.getHighestBlockYAt(arena.centerX, arena.centerZ - spawnOffset) + 1.0D,
                    arena.centerZ - spawnOffset + 0.5D, 180.0F, 0.0F);
            case "yellow" -> new Location(world, arena.centerX + 0.5D,
                    world.getHighestBlockYAt(arena.centerX, arena.centerZ + spawnOffset) + 1.0D,
                    arena.centerZ + spawnOffset + 0.5D, 0.0F, 0.0F);
            default -> new Location(world, arena.centerX + 0.5D,
                    arena.centerY + 2.0D, arena.centerZ + 0.5D);
        };
        return faceArenaCenter(spawn, arena);
    }

    void setProtectionFromCommand(CommandSender sender, String arenaName, String actionName,
                                          String[] args, int scopeIndex) {
        if (!requireAdmin(sender)) return;
        if (args.length <= scopeIndex) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena " + (arenaName == null ? "" : arenaName + " ")
                    + actionName + " <op|@s|@a|namaPemain>");
            return;
        }
        boolean allowed = actionName.equals("breakable") || actionName.equals("placeable");
        String action = (actionName.equals("breakable") || actionName.equals("disbreakable")) ? "break" : "place";
        String scopeInput = args[scopeIndex];
        String scope;
        if (scopeInput.equalsIgnoreCase("@a")) {
            scope = "all";
        } else if (scopeInput.equalsIgnoreCase("op")) {
            scope = "op";
        } else if (scopeInput.equalsIgnoreCase("@s")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "@s hanya dapat digunakan oleh pemain, bukan console.");
                return;
            }
            scope = "player." + ((Player) sender).getName().toLowerCase(Locale.ROOT);
        } else if (scopeInput.matches("[A-Za-z0-9_]{1,16}")) {
            scope = "player." + scopeInput.toLowerCase(Locale.ROOT);
        } else {
            sender.sendMessage(ChatColor.RED + "Target tidak valid. Gunakan op, @s, @a, atau nama pemain.");
            return;
        }
        String path = arenaName == null
                ? "protection.global." + action + "." + scope
                : "protection.arenas." + arenaName + "." + action + "." + scope;
        getConfig().set(path, allowed);
        saveConfig();
        String targetLabel = scope.startsWith("player.") ? scope.substring("player.".length()) : scope;
        sender.sendMessage(ChatColor.GREEN + (allowed ? "Diizinkan" : "Dilarang") + " melakukan " + action
                + " untuk " + targetLabel + (arenaName == null ? " di semua arena." : " di arena " + arenaName + "."));
    }

    boolean isProtectionAction(String action) {
        return action.equals("breakable") || action.equals("disbreakable")
                || action.equals("placeable") || action.equals("displaceable");
    }

    boolean isPreparing(ArenaData arena) {
        MatchState state = matches.get(arena.name);
        return state != null && (state.preparing || state.launching);
    }

    boolean isMatchRunning(ArenaData arena) {
        MatchState state = matches.get(arena.name);
        return state != null && (state.running || state.preparing || state.launching);
    }

    boolean isActionAllowed(String action, ArenaData arena, Player player) {
        if (player.hasPermission("arenasystem.bypass")) return true;
        String playerScope = "player." + player.getName().toLowerCase(Locale.ROOT);
        String[] paths = {
                "protection.arenas." + arena.name + "." + action + "." + playerScope,
                "protection.arenas." + arena.name + "." + action + ".op",
                "protection.arenas." + arena.name + "." + action + ".all",
                "protection.global." + action + "." + playerScope,
                "protection.global." + action + ".op",
                "protection.global." + action + ".all"
        };
        for (int i = 0; i < paths.length; i++) {
            if (i % 3 == 1 && !player.isOp()) continue;
            if (getConfig().contains(paths[i])) return getConfig().getBoolean(paths[i]);
        }
        return false;
    }

    boolean hasExplicitProtectionRule(String action, ArenaData arena, Player player) {
        String playerScope = "player." + player.getName().toLowerCase(Locale.ROOT);
        String[] paths = {
                "protection.arenas." + arena.name + "." + action + "." + playerScope,
                "protection.arenas." + arena.name + "." + action + ".op",
                "protection.arenas." + arena.name + "." + action + ".all",
                "protection.global." + action + "." + playerScope,
                "protection.global." + action + ".op",
                "protection.global." + action + ".all"
        };
        for (int i = 0; i < paths.length; i++) {
            if (i % 3 == 1 && !player.isOp()) continue;
            if (getConfig().contains(paths[i])) return true;
        }
        return false;
    }





    void recordMatchBlock(ArenaData arena, Block block, String oldData) {
        MatchState match = matches.get(arena.name);
        if (match == null || !match.running) return;
        BlockPos pos = new BlockPos(block.getX() - arena.centerX, block.getY() - arena.centerY, block.getZ() - arena.centerZ);
        match.originalBlocks.putIfAbsent(pos, oldData);
    }




















    /** Cari footprint arena berdasarkan world dan X/Z saja, tanpa batas Y. */
    ArenaData findArenaFootprint(Location location) {
        if (location == null || location.getWorld() == null) return null;
        String worldName = location.getWorld().getName();
        for (ArenaData arena : arenas.values()) {
            if (!arena.worldName.equals(worldName)) continue;
            if (Math.abs(location.getBlockX() - arena.centerX) > arena.radius) continue;
            if (Math.abs(location.getBlockZ() - arena.centerZ) > arena.radius) continue;
            return arena;
        }
        for (ArenaData arena : incompleteArenas.values()) {
            if (!arena.worldName.equals(worldName)) continue;
            if (Math.abs(location.getBlockX() - arena.centerX) > arena.radius) continue;
            if (Math.abs(location.getBlockZ() - arena.centerZ) > arena.radius) continue;
            return arena;
        }
        return null;
    }

    ArenaData findArena(Location location) {
        if (location == null || location.getWorld() == null) return null;
        for (ArenaData arena : arenas.values()) {
            if (!arena.worldName.equals(location.getWorld().getName())) continue;
            if (Math.abs(location.getBlockX() - arena.centerX) > arena.radius) continue;
            if (Math.abs(location.getBlockZ() - arena.centerZ) > arena.radius) continue;
            if (location.getBlockY() < arena.minSurfaceY - 20 || location.getBlockY() > arena.maxSurfaceY + 64) continue;
            return arena;
        }
        return null;
    }

    void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "ArenaSystem 0.2.8.2" + ChatColor.GRAY + " — mode-aware hybrid arena, terrain normalizer & match engine");
        sender.sendMessage(ChatColor.YELLOW + "/arena create <nama> [seed] [tdm|relic_rush] " + ChatColor.GRAY + "- buat bentuk arena sesuai mode");
        sender.sendMessage(ChatColor.YELLOW + "/arena list | info <nama> | tp <nama> <red|blue|green|yellow|center>");
        sender.sendMessage(ChatColor.YELLOW + "/arena delete <nama> confirm | regenerate <nama> [seed] | recover <nama>");
        sender.sendMessage(ChatColor.YELLOW + "/arena reset <nama> " + ChatColor.GRAY + "- pulihkan perubahan blok pertandingan");
        sender.sendMessage(ChatColor.YELLOW + "/arena mode <nama> <tdm|relic_rush> " + ChatColor.GRAY + "- ubah mode dan bangun ulang layout");
        sender.sendMessage(ChatColor.YELLOW + "/arena join <nama> <red|blue|green|yellow> [player] | leave | start <nama> | stop <nama>");
        sender.sendMessage(ChatColor.YELLOW + "/arena team <nama> [2|3|4] " + ChatColor.GRAY + "- atur jumlah tim TDM (default 2: RED, BLUE)");
        sender.sendMessage(ChatColor.YELLOW + "/arena class [knight|tank|archer|sniper|assassin|ninja|boxer|demoman] " + ChatColor.GRAY + "- pilih class di lobby persiapan");
        sender.sendMessage(ChatColor.GRAY + "Skill class: klik kanan item skill di hotbar. Boxer juga dapat klik kanan saat tangan kosong untuk Heavy Jab (2,1 detik cooldown).");
        sender.sendMessage(ChatColor.GRAY + "Cooldown dan durasi skill dapat diatur di config.yml.");
        sender.sendMessage(ChatColor.YELLOW + "/arena clearmobs <nama> " + ChatColor.GRAY + "- bersihkan mob di area arena");
        sender.sendMessage(ChatColor.YELLOW + "/arena clearvegetation <nama> " + ChatColor.GRAY + "- bersihkan rumput/tanaman pengganggu dengan snapshot aman");
        sender.sendMessage(ChatColor.YELLOW + "/arena <nama> breakable|disbreakable|placeable|displaceable <op|@s|@a|player>");
        sender.sendMessage(ChatColor.GRAY + "Default: blok arena dilindungi. arenasystem.bypass melewati aturan proteksi.");
    }

    void adaptSinglePlayerPreparation(MatchState state) {
        if (state.preparing && state.teams.size() == 1
                && getConfig().getBoolean("matches.allow-single-player", true) && !state.singlePlayerMode) {
            state.singlePlayerMode = true;
        }
    }

    MatchState findPreparingMatch(UUID playerId) {
        for (MatchState state : matches.values()) {
            if (state.preparing && state.teams.containsKey(playerId)) return state;
        }
        return null;
    }




}