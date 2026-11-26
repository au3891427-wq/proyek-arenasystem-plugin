package id.hadziq.arenasystem;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
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
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
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
import java.util.HexFormat;
import java.util.Collection;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.function.Consumer;

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
    private static final UUID ASSASSIN_DAGGER_RESOURCE_PACK_ID =
            UUID.fromString("2efb65ad-5f8a-4c26-a8b4-2f4d84c98d26");

    final Map<String, ArenaData> arenas = new HashMap<>();
    final Map<String, ArenaData> incompleteArenas = new HashMap<>();
    final Map<String, MatchState> matches = new HashMap<>();
    final Map<UUID, PlayerSnapshot> pendingPlayerRestores = new HashMap<>();
    final Set<String> buildingArenas = new HashSet<>();
    /** Footprints whose registration was administratively discarded; creation is blocked until released. */
    final Map<String, QuarantinedArea> quarantinedAreas = new HashMap<>();
    record QuarantinedArea(String name, String worldName, int centerX, int centerZ, int radius) { }
    final Map<String, Set<BukkitTask>> activeGenerationTasks = new HashMap<>();
    SnapshotStore snapshots;
    PendingPlayerSnapshotStore playerSnapshotStore;
    NamespacedKey relicKey;
    NamespacedKey classSelectorKey;
    NamespacedKey classSkillKey;
    NamespacedKey assassinBladeKey;
    NamespacedKey randomStuffKey;
    NamespacedKey randomStuffDropExpiresKey;
    NamespacedKey randomStuffProjectileKey;
    PlayerArenaListener playerArenaListener;
    ClassAbilityListener classAbilityListener;
    ClassPassiveManager classPassiveManager;
    PluginUpdateNotifier updateNotifier;
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
        getConfig().addDefault("resource-pack.assassin-dagger.url", "");
        getConfig().addDefault("resource-pack.assassin-dagger.sha1", "c4bea426f1b8f643dd74aa5bf56588c966b6b4d2");
        getConfig().addDefault("updates.enabled", true);
        getConfig().addDefault("updates.check-interval-hours", 6);
        getConfig().addDefault("updates.manifest-url", "https://raw.githubusercontent.com/au3891427-wq/proyek-arenasystem-plugin/main/arena-system-update-manifest.json");
        getConfig().addDefault("terrain.max-adjacent-step", 1);
        if (getConfig().getInt("terrain.max-adjacent-step", 1) != 1) {
            getLogger().warning("terrain.max-adjacent-step sekarang wajib 1; nilai konfigurasi lama diubah ke 1.");
            getConfig().set("terrain.max-adjacent-step", 1);
        }
        getConfig().addDefault("terrain.max-column-adjustment", 12);
        getConfig().addDefault("terrain.max-seed-delta", SeededTerrainBlender.DEFAULT_MAX_SEED_DELTA);
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
        playerSnapshotStore = new PendingPlayerSnapshotStore(getDataFolder());
        Map<UUID, PlayerSnapshot> recoveredPlayerSnapshots = playerSnapshotStore.loadAll(getLogger());
        pendingPlayerRestores.putAll(recoveredPlayerSnapshots);
        if (!recoveredPlayerSnapshots.isEmpty()) {
            getLogger().warning("Ditemukan " + recoveredPlayerSnapshots.size()
                    + " snapshot pemain yang belum dipulihkan dari sesi sebelumnya. "
                    + "Snapshot akan dipulihkan saat pemain login.");
        }
        relicKey = new NamespacedKey(this, "arena_relic");
        classSelectorKey = new NamespacedKey(this, "class_selector");
        classSkillKey = new NamespacedKey(this, "class_skill");
        assassinBladeKey = new NamespacedKey(this, "assassin_blade");
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
        getLogger().info("ArenaSystem 0.2.8.6 aktif. Arena siap: " + arenas.size());
        if (getConfig().getBoolean("updates.enabled", true)) {
            updateNotifier = new PluginUpdateNotifier(this);
            updateNotifier.start();
        }
    }

    private void migrateLegacyCooldownDefault(String path, int previousDefault, int newDefault) {
        if (getConfig().getInt(path, newDefault) == previousDefault) {
            getConfig().set(path, newDefault);
        }
    }

    // ---- Atomic, failure-aware config persistence (Stage 2B.1) -------------------------------
    // JavaPlugin#saveConfig swallows IOException, so lifecycle code must never depend on it.
    private File configFile() {
        return new File(getDataFolder(), "config.yml");
    }

    private void persistConfigOrThrow() throws IOException {
        AtomicFileWriter.write(configFile(), getConfig().saveToString().getBytes(StandardCharsets.UTF_8));
    }

    /** Non-lifecycle callers keep the old fire-and-forget contract, but now write atomically. */
    @Override
    public void saveConfig() {
        try {
            persistConfigOrThrow();
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Konfigurasi tidak dapat disimpan ke " + configFile(), ex);
        }
    }

    private final ConfigTransaction.ConfigAccess configAccess = new ConfigTransaction.ConfigAccess() {
        @Override public Object get(String path) { return getConfig().get(path); }
        @Override public void set(String path, Object value) { getConfig().set(path, value); }
        @Override public Map<String, Object> leafValues(String root) {
            org.bukkit.configuration.ConfigurationSection section = getConfig().getConfigurationSection(root);
            if (section == null) return null;
            Map<String, Object> leaves = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : section.getValues(true).entrySet()) {
                if (!(entry.getValue() instanceof org.bukkit.configuration.ConfigurationSection)) {
                    leaves.put(entry.getKey(), entry.getValue());
                }
            }
            return leaves;
        }
    };

    /** Mutates config subtrees and persists atomically; throws IllegalStateException and rolls back on failure. */
    private void commitConfig(Collection<String> roots, Runnable mutation) {
        ConfigTransaction.commit(configAccess, this::persistConfigOrThrow, roots, mutation);
    }

    /** Removes an arena record from config durably. On failure nothing changes in memory or on disk. */
    private void removeArenaRecord(String arenaName) {
        String root = "arenas." + arenaTaskKey(arenaName);
        commitConfig(List.of(root), () -> getConfig().set(root, null));
    }

    private String arenaTaskKey(String arenaName) {
        return arenaName.toLowerCase(Locale.ROOT);
    }

    private void trackGenerationTask(String arenaName, BukkitTask task) {
        if (task == null) return;
        activeGenerationTasks.computeIfAbsent(arenaTaskKey(arenaName), ignored -> new HashSet<>()).add(task);
    }

    private void untrackGenerationTask(String arenaName, BukkitTask task) {
        if (task == null) return;
        String key = arenaTaskKey(arenaName);
        Set<BukkitTask> tasks = activeGenerationTasks.get(key);
        if (tasks == null) return;
        tasks.remove(task);
        if (tasks.isEmpty()) activeGenerationTasks.remove(key);
    }

    private boolean hasActiveGenerationTasks(String arenaName) {
        Set<BukkitTask> tasks = activeGenerationTasks.get(arenaTaskKey(arenaName));
        return tasks != null && !tasks.isEmpty();
    }

    private void cancelAllGenerationTasks() {
        for (Set<BukkitTask> tasks : activeGenerationTasks.values()) {
            for (BukkitTask task : tasks) task.cancel();
        }
        activeGenerationTasks.clear();
        buildingArenas.clear();
    }

    boolean isIncompleteArena(ArenaData arena) {
        return arena != null && incompleteArenas.containsKey(arena.name);
    }

    @Override
    public void onDisable() {
        if (updateNotifier != null) {
            updateNotifier.stop();
            updateNotifier = null;
        }
        cancelAllGenerationTasks();
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
            if (arena == null) arena = incompleteArenas.get(match.arenaName);
            if (arena != null) {
                Map<BlockPos, String> changes = combinedMatchChanges(match);
                restoreMapImmediately(arena, changes);
                removeRelicItems(arena, match);
            }
        }
        saveConfig();
    }

    private void loadQuarantinedAreas() {
        quarantinedAreas.clear();
        var quarantine = getConfig().getConfigurationSection("quarantined-arenas");
        if (quarantine == null) return;
        for (String key : quarantine.getKeys(false)) {
            String base = "quarantined-arenas." + key + ".";
            String world = getConfig().getString(base + "world");
            if (world == null) continue;
            String name = key.toLowerCase(Locale.ROOT);
            quarantinedAreas.put(name, new QuarantinedArea(name, world,
                    getConfig().getInt(base + "center-x"), getConfig().getInt(base + "center-z"),
                    getConfig().getInt(base + "radius", RELIC_RADIUS)));
        }
        if (!quarantinedAreas.isEmpty()) {
            getLogger().warning(quarantinedAreas.size() + " area karantina aktif; pembuatan arena baru di area itu diblokir. "
                    + "Gunakan /arena quarantine list.");
        }
    }

    void loadArenas() {
        loadQuarantinedAreas();
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

            boolean complete = getConfig().getBoolean(base + "complete", false);
            GenerationStatus status = readGenerationStatus(base, complete);
            // The legacy complete flag remains the compatibility gate. A persisted
            // non-COMPLETE status always wins over a stale true flag for safety.
            if (status != GenerationStatus.COMPLETE) complete = false;
            if (complete) {
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
                getConfig().set(base + "complete", false);
                getConfig().set(base + "generation-state", status.name());
                getLogger().warning("Arena '" + key + "' berstatus " + status
                        + ". Periksa snapshot dan gunakan /arena recover " + key + ".");
            }
        }
    }


    private GenerationStatus readGenerationStatus(String base, boolean completeFlag) {
        String raw = getConfig().getString(base + "generation-state");
        if (raw == null || raw.isBlank()) {
            // Old configurations only stored the boolean flag.
            return completeFlag ? GenerationStatus.COMPLETE : GenerationStatus.RECOVERY_REQUIRED;
        }
        try {
            GenerationStatus parsed = GenerationStatus.valueOf(raw.toUpperCase(Locale.ROOT));
            return parsed == GenerationStatus.COMPLETE && !completeFlag
                    ? GenerationStatus.RECOVERY_REQUIRED : parsed;
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Status generation tidak dikenal pada " + base
                    + "; arena akan diperlakukan sebagai RECOVERY_REQUIRED.");
            return GenerationStatus.RECOVERY_REQUIRED;
        }
    }

    private void markRecoveryRequired(String arenaName) {
        ArenaData data = incompleteArenas.get(arenaTaskKey(arenaName));
        if (data == null) return;
        try {
            saveArenaData(data, GenerationStatus.RECOVERY_REQUIRED);
        } catch (RuntimeException ex) {
            getLogger().severe("Tidak dapat menyimpan RECOVERY_REQUIRED untuk arena "
                    + arenaName + ": " + ex.getMessage());
        }
    }

    /** Active team count for an arena name and target mode; the same config key teamCountFor() uses. */
    int activeTeamCount(String arenaName, String mode) {
        if (!"tdm".equalsIgnoreCase(mode)) return 2;
        return Math.max(2, Math.min(4, getConfig().getInt("arenas." + arenaName + ".team-count", 2)));
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
                || quarantinedAreas.containsKey(name) || getConfig().contains("arenas." + name)) {
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
            player.sendMessage(ChatColor.RED + "Lokasi bertumpang tindih dengan arena terdaftar atau area karantina (lihat /arena quarantine list).");
            return;
        }
        int span = radius * 2 + 1;
        player.sendMessage(ChatColor.GREEN + "Memindai kontur medan " + span + "x" + span + " untuk mode "
                + mode.toUpperCase(Locale.ROOT) + ". Bentuk arena akan dibuat khusus untuk mode ini...");
        player.sendMessage(ChatColor.GRAY + "Seed layout: " + seed
                + " | simpan angka ini untuk mereproduksi variasi struktur pada lokasi yang sama.");
        scanAndBuild(player, name, world, centerX, centerZ, seed, mode);
    }

    void scanAndBuild(CommandSender sender, String name, World world, int centerX, int centerZ, long seed, String mode) {
        if (!buildingArenas.add(arenaTaskKey(name))) {
            sender.sendMessage(ChatColor.RED + "Arena ini sedang diproses. Tunggu hingga operasi sebelumnya selesai.");
            return;
        }
        scanAndBuildLocked(sender, name, world, centerX, centerZ, seed, mode);
    }

    private void scanAndBuildLocked(CommandSender sender, String name, World world,
                                    int centerX, int centerZ, long seed, String mode) {
        int radius = radiusForMode(mode);
        int size = radius * 2 + 1;
        int[][] heights = new int[size][size];
        int[] cursor = {-radius, -radius};
        int[] limits = {Integer.MAX_VALUE, Integer.MIN_VALUE};
        // Tallied in the same per-column pass as heights[]; see TerrainScanner.hasGenericStructureAbove.
        int[] structureColumns = {0};
        BukkitTask[] scanTaskReference = new BukkitTask[1];
        BukkitRunnable scanTask = new BukkitRunnable() {
            private void finishScan(boolean releaseLock) {
                cancel();
                untrackGenerationTask(name, scanTaskReference[0]);
                if (releaseLock) buildingArenas.remove(arenaTaskKey(name));
            }

            @Override
            public void run() {
                try {
                    int processed = 0;
                while (cursor[0] <= radius && processed < SCAN_COLUMNS_PER_TICK) {
                    int y = TerrainScanner.findSurface(world, centerX + cursor[0], centerZ + cursor[1]);
                    if (y == Integer.MIN_VALUE) {
                        finishScan(true);
                        abortBeforeBuild(sender, name, "Tidak menemukan permukaan tanah yang cocok pada area ini.");
                        return;
                    }
                    heights[cursor[0] + radius][cursor[1] + radius] = y;
                    limits[0] = Math.min(limits[0], y);
                    limits[1] = Math.max(limits[1], y);
                    if (TerrainScanner.hasGenericStructureAbove(world, centerX + cursor[0], y, centerZ + cursor[1])) {
                        structureColumns[0]++;
                    }
                    processed++;
                    cursor[1]++;
                    if (cursor[1] > radius) {
                        cursor[1] = -radius;
                        cursor[0]++;
                    }
                }
                if (cursor[0] > radius) {
                    finishScan(false);
                    TerrainProfile originalProfile = new TerrainProfile(radius, heights, limits[0], limits[1]);

                    // Generic Structure: reject before doing any normalization/snapshot work if the
                    // plot already has too much pre-existing building/ruin material to make a
                    // proportional arena. Read-only check; no block is touched either way.
                    int totalColumns = size * size;
                    double structureCoverage = totalColumns == 0 ? 0.0 : (double) structureColumns[0] / totalColumns;
                    double maxStructureCoverage = Math.max(0.0, Math.min(1.0,
                            getConfig().getDouble("terrain.max-structure-coverage", 0.25)));
                    if (structureCoverage > maxStructureCoverage) {
                        sender.sendMessage(ChatColor.RED + "Area ini terlalu padat oleh rumah/bangunan/struktur dari medan asli: "
                                + Math.round(structureCoverage * 100) + "% kolom (batas "
                                + Math.round(maxStructureCoverage * 100) + "%).");
                        sender.sendMessage(ChatColor.GRAY + "Atur terrain.max-structure-coverage di config.yml atau pilih area yang tidak terlalu padat bangunan.");
                        abortBeforeBuild(sender, name, null);
                        return;
                    }

                    int maxAdjacentStep = 1;
                    // Relic Rush spawn offset tracks the sanctum position built by ArenaGenerator (radius - 8).
                    int spawnOffset = mode.equals("relic_rush") ? radius - 8 : TDM_SPAWN_OFFSET;
                    // Single source of truth: arenas.<name>.team-count (what /arena team stores and ArenaGenerator reads).
                    int activeTeams = activeTeamCount(name, mode);
                    int maxSeedDelta = Math.max(0, Math.min(SeededTerrainBlender.MAX_CONFIGURABLE_SEED_DELTA,
                            getConfig().getInt("terrain.max-seed-delta", SeededTerrainBlender.DEFAULT_MAX_SEED_DELTA)));
                    TerrainNormalizer.Result normalization;
                    try {
                        // Seed-dominant bounded blend first, then the 1-block slope normalizer on ALL active spawn anchors.
                        TerrainProfile blended = SeededTerrainBlender.blend(originalProfile, seed, spawnOffset, activeTeams,
                                maxSeedDelta, world.getMinHeight(), world.getMaxHeight());
                        normalization = TerrainNormalizer.normalize(blended, maxAdjacentStep,
                                SeededTerrainBlender.anchors(spawnOffset, activeTeams));
                    } catch (IllegalStateException | IllegalArgumentException ex) {
                        abortBeforeBuild(sender, name, "Terrain tidak dapat dinormalkan dengan aman: " + ex.getMessage());
                        return;
                    }

                    TerrainProfile profile = normalization.profile();
                    int maxColumnAdjustment = Math.max(1,
                            getConfig().getInt("terrain.max-column-adjustment", 12));
                    // The cut/fill bound is measured against the ORIGINAL scanned surface (seed delta + normalization).
                    int maxScannedAdjustment = 0;
                    for (int ax = -radius; ax <= radius; ax++) {
                        for (int az = -radius; az <= radius; az++) {
                            maxScannedAdjustment = Math.max(maxScannedAdjustment,
                                    Math.abs(profile.heightAt(ax, az) - originalProfile.heightAt(ax, az)));
                        }
                    }
                    sender.sendMessage(ChatColor.AQUA + "[TerrainNormalizer] " + normalization.changedColumns()
                            + " kolom disesuaikan; perubahan maksimum " + normalization.maxAdjustment()
                            + " blok, rata-rata " + String.format(Locale.ROOT, "%.2f", normalization.averageAdjustment())
                            + " blok; beda tinggi antarkolom dibatasi " + normalization.maxAdjacentStep()
                            + " blok; kontur seed (maks " + maxSeedDelta + " blok) dicampur; elevasi dan pad spawn seluruh tim aktif diseimbangkan.");
                    if (maxScannedAdjustment > maxColumnAdjustment) {
                        sender.sendMessage(ChatColor.RED + "Terrain terlalu ekstrem untuk dinormalkan dengan aman: "
                                + "perubahan per kolom perlu " + maxScannedAdjustment
                                + " blok (batas " + maxColumnAdjustment + ").");
                        sender.sendMessage(ChatColor.GRAY + "Atur terrain.max-column-adjustment di config.yml atau pilih area lain.");
                        abortBeforeBuild(sender, name, null);
                        return;
                    }

                    TerrainDetector.Report terrainReport = TerrainDetector.analyze(profile, radius, spawnOffset, activeTeams);
                    sender.sendMessage(ChatColor.GOLD + "[TerrainDetector] Setelah normalisasi: " + terrainReport.summary());
                    for (String warning : terrainReport.warnings()) {
                        sender.sendMessage(ChatColor.YELLOW + "[TerrainDetector] " + warning);
                    }
                    if (!terrainReport.accepted()) {
                        sender.sendMessage(ChatColor.RED + "Terrain belum memenuhi batas kelayakan arena: "
                                + terrainReport.rejectionReason());
                        sender.sendMessage(ChatColor.GRAY + "Pilih lokasi lain atau tinjau batas normalisasi dengan hati-hati.");
                        abortBeforeBuild(sender, name, null);
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
                        abortBeforeBuild(sender, name, "Ruang spawn/objektif terhalang block entity atau struktur yang tidak aman dipindahkan.");
                        return;
                    }

                    ArenaData data = makeArenaData(name, world.getName(), seed, centerX, centerZ, profile, mode, radius);
                    beginGeneration(sender, data, world, originalProfile, profile);
                }
                } catch (RuntimeException ex) {
                    finishScan(true);
                    getLogger().severe("Pemindaian arena " + name + " gagal: " + ex.getMessage());
                    abortBeforeBuild(sender, name, "Pemindaian arena gagal. Tidak ada perubahan terrain yang diterapkan: "
                            + ex.getMessage());
                }
            }
        };
        scanTaskReference[0] = scanTask.runTaskTimer(this, 1L, 1L);
        trackGenerationTask(name, scanTaskReference[0]);
    }

    /**
     * Ends a generation attempt that failed before any arena block was written.
     * A brand-new arena has no record, so nothing is left behind. For a regeneration
     * the baseline snapshot was already restored (state SCANNING), so the old arena no
     * longer exists in the world: its record is unregistered and the now-redundant
     * snapshot removed. If unregistering cannot be persisted the arena stays
     * RECOVERY_REQUIRED with its snapshot kept.
     */
    private void abortBeforeBuild(CommandSender sender, String name, String reason) {
        String key = arenaTaskKey(name);
        if (hasActiveGenerationTasks(key)) {
            getLogger().severe("abortBeforeBuild(" + key + ") diabaikan: masih ada task generasi aktif.");
            return;
        }
        buildingArenas.remove(key);
        if (reason != null) sender.sendMessage(ChatColor.RED + reason);
        if (!incompleteArenas.containsKey(key)) return;
        try {
            removeArenaRecord(key);
        } catch (RuntimeException ex) {
            markRecoveryRequired(key);
            sender.sendMessage(ChatColor.RED + "World sudah dikembalikan ke kondisi awal, tetapi status arena tidak dapat disimpan ("
                    + ex.getMessage() + "). Arena tetap RECOVERY_REQUIRED dan snapshot dipertahankan; jalankan /arena recover "
                    + key + " setelah masalah penyimpanan teratasi.");
            return;
        }
        incompleteArenas.remove(key);
        arenas.remove(key);
        try {
            snapshots.delete(key);
        } catch (IOException ex) {
            getLogger().warning("Snapshot arena " + key + " gagal dibersihkan setelah regenerasi dibatalkan: " + ex.getMessage());
        }
        sender.sendMessage(ChatColor.YELLOW + "Regenerasi dibatalkan. World arena sudah dikembalikan ke kondisi awal dan arena lama "
                + key + " TIDAK lagi tersedia (record dihapus). Buat ulang dengan /arena create " + key + ".");
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
            snapshot = snapshots.load(arena);
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
            snapshots.save(arena, snapshot);
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
            int configuredTeams = activeTeamCount(data.name, data.mode);
            edits = ArenaGenerator.createPlan(world, data, originalProfile, profile,
                    data.seed, data.radius, configuredTeams);
        } catch (IllegalStateException ex) {
            getLogger().warning("Terrain normalization dibatalkan untuk arena " + data.name + ": " + ex.getMessage());
            abortBeforeBuild(sender, data.name, "Pembuatan arena dibatalkan demi keamanan: " + ex.getMessage());
            return;
        }
        final Map<BlockPos, String> original;
        try {
            original = captureOriginalBlocks(world, data, edits);
        } catch (IllegalStateException ex) {
            getLogger().warning("Snapshot arena " + data.name + " ditolak: " + ex.getMessage());
            abortBeforeBuild(sender, data.name, "Pembuatan arena dibatalkan: " + ex.getMessage());
            return;
        }
        if (original.isEmpty()) {
            abortBeforeBuild(sender, data.name, "Tidak ada blok aman untuk dibangun. Tidak ada perubahan yang dilakukan.");
            return;
        }
        try {
            snapshots.save(data, original);
        } catch (IOException ex) {
            getLogger().severe("Tidak dapat menyimpan snapshot arena " + data.name + ": " + ex.getMessage());
            abortBeforeBuild(sender, data.name, "Gagal menyimpan snapshot. Arena tidak dibuat demi keamanan world.");
            return;
        }

        // BUILDING must be durable BEFORE the first block changes. If it cannot be written
        // the world is still untouched, so abort and restore the previous registration.
        ArenaData previousRecord = incompleteArenas.put(data.name, data);
        try {
            saveArenaData(data, GenerationStatus.BUILDING);
        } catch (RuntimeException ex) {
            if (previousRecord == null) {
                incompleteArenas.remove(data.name);
                try {
                    snapshots.delete(data.name);
                } catch (IOException cleanup) {
                    getLogger().warning("Snapshot yatim arena " + data.name + " gagal dibersihkan: " + cleanup.getMessage());
                }
            } else {
                incompleteArenas.put(data.name, previousRecord);
            }
            getLogger().severe("Status BUILDING arena " + data.name + " tidak dapat disimpan: " + ex.getMessage());
            abortBeforeBuild(sender, data.name, "Status BUILDING tidak dapat disimpan; world tidak diubah: " + ex.getMessage());
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Pemindaian selesai. Membangun " + edits.size()
                + " perubahan blok secara bertahap...");

        BukkitTask[] buildTaskReference = new BukkitTask[1];
        BukkitRunnable buildTask = new BukkitRunnable() {
            private int index;

            private void finishBuildTask() {
                untrackGenerationTask(data.name, buildTaskReference[0]);
            }

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
                        boolean vegetationCleanupComplete = clearCombatVegetationSafely(data);
                        getConfig().set("arenas." + data.name + ".combat-vegetation-cleaned-v4",
                                vegetationCleanupComplete);
                        if (!vegetationCleanupComplete) {
                            getLogger().warning("Vegetation cleanup for arena '" + data.name
                                    + "' did not complete. It can be retried with /arena clearvegetation " + data.name + ".");
                        }

                        // Persist completion before exposing the arena to commands or matches.
                        // If saving fails, the arena remains incomplete and its snapshot is retained.
                        saveArenaData(data, GenerationStatus.COMPLETE);
                        finishBuildTask();
                        incompleteArenas.remove(data.name);
                        arenas.put(data.name, data);
                        buildingArenas.remove(arenaTaskKey(data.name));

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
                    finishBuildTask();
                    buildingArenas.remove(arenaTaskKey(data.name));
                    markRecoveryRequired(data.name);
                    getLogger().severe("Pembuatan arena " + data.name + " terhenti: " + ex.getMessage());
                    sender.sendMessage(ChatColor.RED + "Pembuatan terhenti. Snapshot masih disimpan; gunakan /arena recover " + data.name + " setelah memeriksa log.");
                }
            }
        };
        buildTaskReference[0] = buildTask.runTaskTimer(this, 1L, 1L);
        trackGenerationTask(data.name, buildTaskReference[0]);
    }

    Map<BlockPos, String> captureOriginalBlocks(World world, ArenaData arena, List<BlockEdit> edits) {
        Map<BlockPos, String> original = new LinkedHashMap<>();
        for (BlockEdit edit : edits) {
            Block block = world.getBlockAt(arena.centerX + edit.position.x,
                    arena.centerY + edit.position.y, arena.centerZ + edit.position.z);
            if (block.getState() instanceof TileState) {
                throw new IllegalStateException("Rencana generator menyentuh block entity pada "
                        + block.getX() + "," + block.getY() + "," + block.getZ()
                        + ". Blok tersebut tidak dapat dipulihkan hanya dengan BlockData.");
            }
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
        for (QuarantinedArea area : quarantinedAreas.values()) {
            if (!area.worldName().equals(world.getName())) continue;
            if (Math.abs(area.centerX() - centerX) < area.radius() + newRadius + 4
                    && Math.abs(area.centerZ() - centerZ) < area.radius() + newRadius + 4) return true;
        }
        return false;
    }

    /**
     * Persists lifecycle state atomically. Throws IllegalStateException when the config
     * file cannot be written; in that case the previous state is still on disk AND in the
     * in-memory config, so callers must treat the transition as not having happened.
     */
    void saveArenaData(ArenaData data, GenerationStatus status) {
        if (data == null || status == null) throw new IllegalArgumentException("arena and status are required");
        String root = "arenas." + data.name;
        String base = root + ".";
        commitConfig(List.of(root), () -> {
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
            getConfig().set(base + "complete", status == GenerationStatus.COMPLETE);
            getConfig().set(base + "generation-state", status.name());
        });
    }

    void listArenas(CommandSender sender) {
        if (arenas.isEmpty() && incompleteArenas.isEmpty() && quarantinedAreas.isEmpty()) {
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
        if (!quarantinedAreas.isEmpty()) {
            sender.sendMessage(ChatColor.GOLD + "Area karantina: " + quarantinedAreas.size() + " (lihat /arena quarantine list)");
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
        String name = args[1].toLowerCase(Locale.ROOT);
        ArenaData arena = arenas.get(name);
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
            return;
        }
        if (buildingArenas.contains(name) || hasActiveGenerationTasks(name)) {
            sender.sendMessage(ChatColor.RED + "Arena sedang diproses atau menunggu pemulihan blok; penghapusan ditolak.");
            return;
        }
        if (!snapshots.exists(arena.name)) {
            sender.sendMessage(ChatColor.RED + "Snapshot asli tidak tersedia. Arena legacy 0.1.0 tidak bisa dihapus otomatis dengan aman.");
            sender.sendMessage(ChatColor.GRAY + "Pulihkan dari backup world atau buat ulang arena 0.2.0 di lokasi lain.");
            return;
        }

        buildingArenas.add(name);
        try {
            saveArenaData(arena, GenerationStatus.RESTORING);
        } catch (RuntimeException ex) {
            buildingArenas.remove(name);
            sender.sendMessage(ChatColor.RED + "Status penghapusan gagal disimpan; world tidak diubah: " + ex.getMessage());
            return;
        }
        incompleteArenas.put(name, arena);
        arenas.remove(name);
        stopAndRestoreMatch(name, () -> restoreArenaSnapshot(sender, arena, true, () -> {
            // Persist removal first. If this throws, restoreArenaSnapshot keeps the snapshot
            // and marks the arena RECOVERY_REQUIRED instead of deleting recovery data.
            removeArenaRecord(name);
            arenas.remove(name);
            incompleteArenas.remove(name);
            buildingArenas.remove(name);
            sender.sendMessage(ChatColor.GREEN + "Arena " + name + " dihapus dan blok yang dicatat telah dipulihkan.");
        }, ex -> {
            buildingArenas.remove(name);
            sender.sendMessage(ChatColor.RED + "Penghapusan gagal; arena tetap dikunci untuk recovery: " + ex.getMessage());
            getLogger().severe("Penghapusan arena " + name + " gagal: " + ex.getMessage());
        }), ex -> {
            markRecoveryRequired(name);
            buildingArenas.remove(name);
            sender.sendMessage(ChatColor.RED + "Pemulihan perubahan match gagal; arena tetap perlu recovery: " + ex.getMessage());
            getLogger().severe("Stop-match sebelum penghapusan " + name + " gagal: " + ex.getMessage());
        });
    }

    void regenerateArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena regenerate <nama> [seed]");
            return;
        }
        Long seedOverride = null;
        if (args.length >= 3 && !args[2].isBlank()) {
            try {
                seedOverride = Long.parseLong(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Seed harus berupa angka bulat (long).");
                return;
            }
        }
        regenerateInternal(sender, args[1].toLowerCase(Locale.ROOT), seedOverride, null);
    }

    /**
     * Single entry point for regenerate and mode change. All validation happens BEFORE any
     * map or config change; the arena lock is taken first and the RESTORING state is persisted
     * before the in-memory registration moves. Returns true only when the pipeline started.
     */
    private boolean regenerateInternal(CommandSender sender, String name, Long seedOverride, String modeOverride) {
        ArenaData old = arenas.get(name);
        if (old == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
            return false;
        }
        if (buildingArenas.contains(name) || hasActiveGenerationTasks(name)) {
            sender.sendMessage(ChatColor.RED + "Arena ini sedang diproses atau menunggu pemulihan blok. Tunggu hingga selesai.");
            return false;
        }
        if (!snapshots.exists(old.name)) {
            sender.sendMessage(ChatColor.RED + "Snapshot asli tidak tersedia. Regenerate ditolak agar tidak menimpa world tanpa pemulihan.");
            return false;
        }
        long finalSeed = seedOverride != null ? seedOverride : old.seed;
        String targetMode = modeOverride != null ? modeOverride : old.mode;

        buildingArenas.add(name);
        try {
            // Old record (old mode/radius) is persisted as RESTORING; the new mode only becomes
            // durable once BUILDING is saved by beginGeneration.
            saveArenaData(old, GenerationStatus.RESTORING);
        } catch (RuntimeException ex) {
            buildingArenas.remove(name);
            sender.sendMessage(ChatColor.RED + "Status regeneration gagal disimpan; world tidak diubah: " + ex.getMessage());
            return false;
        }
        incompleteArenas.put(name, old);
        arenas.remove(name);
        sender.sendMessage(ChatColor.GRAY + "Seed layout regenerate: " + finalSeed
                + " | gunakan seed lain untuk variasi struktur yang berbeda.");

        stopAndRestoreMatch(name, () -> restoreArenaSnapshot(sender, old, false, () -> {
            World world = Bukkit.getWorld(old.worldName);
            if (world == null) {
                markRecoveryRequired(name);
                buildingArenas.remove(name);
                sender.sendMessage(ChatColor.RED + "World arena tidak termuat setelah pemulihan. Arena tetap berstatus incomplete.");
                return;
            }
            try {
                saveArenaData(old, GenerationStatus.SCANNING);
            } catch (RuntimeException ex) {
                markRecoveryRequired(name);
                buildingArenas.remove(name);
                sender.sendMessage(ChatColor.RED + "Status scanning gagal disimpan; arena tetap memerlukan recovery: " + ex.getMessage());
                return;
            }
            arenas.remove(name);
            incompleteArenas.put(name, old);
            sender.sendMessage(ChatColor.YELLOW + "Arena lama dipulihkan. Memindai medan untuk regenerate...");
            scanAndBuildLocked(sender, name, world, old.centerX, old.centerZ, finalSeed, targetMode);
        }, ex -> {
            markRecoveryRequired(name);
            buildingArenas.remove(name);
            sender.sendMessage(ChatColor.RED + "Regeneration gagal; snapshot dipertahankan dan arena memerlukan recovery: " + ex.getMessage());
            getLogger().severe("Regeneration arena " + name + " gagal: " + ex.getMessage());
        }), ex -> {
            markRecoveryRequired(name);
            buildingArenas.remove(name);
            sender.sendMessage(ChatColor.RED + "Pemulihan state match gagal; regeneration dibatalkan: " + ex.getMessage());
            getLogger().severe("Stop-match sebelum regenerate " + name + " gagal: " + ex.getMessage());
        });
        return true;
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
        if (buildingArenas.contains(name) || hasActiveGenerationTasks(name)) {
            sender.sendMessage(ChatColor.RED + "Arena sedang diproses atau menunggu pemulihan blok. Tunggu hingga selesai.");
            return;
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("discard")) {
            discardArenaRecord(sender, data, args.length >= 4 && args[3].equalsIgnoreCase("confirm"));
            return;
        }
        if (!snapshots.exists(name)) {
            sender.sendMessage(ChatColor.RED + "Snapshot recovery tidak ditemukan. Pulihkan world dari backup manual, "
                    + "atau lepaskan registrasi dengan /arena recover " + name + " discard (world tidak diubah).");
            return;
        }

        buildingArenas.add(name);
        try {
            saveArenaData(data, GenerationStatus.RESTORING);
        } catch (RuntimeException ex) {
            buildingArenas.remove(name);
            sender.sendMessage(ChatColor.RED + "Tidak dapat menyimpan status recovery; world tidak diubah: " + ex.getMessage());
            return;
        }
        stopAndRestoreMatch(name, () -> restoreArenaSnapshot(sender, data, true, () -> {
            removeArenaRecord(name);
            incompleteArenas.remove(name);
            arenas.remove(name);
            buildingArenas.remove(name);
            sender.sendMessage(ChatColor.GREEN + "Recovery " + name + " selesai. World dikembalikan ke keadaan sebelum generator dijalankan.");
        }, ex -> {
            markRecoveryRequired(name);
            buildingArenas.remove(name);
            sender.sendMessage(ChatColor.RED + "Recovery gagal; arena tetap berstatus incomplete: " + ex.getMessage());
            getLogger().severe("Recovery arena " + name + " gagal: " + ex.getMessage());
        }), ex -> {
            markRecoveryRequired(name);
            buildingArenas.remove(name);
            sender.sendMessage(ChatColor.RED + "Pemulihan state pertandingan gagal; recovery arena dibatalkan: " + ex.getMessage());
            getLogger().severe("Restore match sebelum recovery " + name + " gagal: " + ex.getMessage());
        });
    }

    /**
     * Administrative escape hatch for an arena whose snapshot cannot be restored. It changes
     * NO world blocks and deletes NO snapshot evidence: the arena record is replaced by a
     * persisted quarantine record that keeps the footprint and name blocked for new arenas
     * until an administrator explicitly releases it. Snapshot files are renamed, not deleted.
     */
    private void discardArenaRecord(CommandSender sender, ArenaData data, boolean confirmed) {
        String name = data.name;
        if (!confirmed) {
            sender.sendMessage(ChatColor.RED + "PERINGATAN: ini hanya melepaskan registrasi arena " + name + ".");
            sender.sendMessage(ChatColor.RED + "Blok world TIDAK dipulihkan; area bisa tetap berada dalam kondisi parsial "
                    + "(sebagian terrain/struktur arena). Berkas snapshot/backup tidak dihapus, hanya diganti nama.");
            sender.sendMessage(ChatColor.YELLOW + "Area akan dikarantina dan tidak bisa dipakai untuk arena baru sampai Anda menjalankan "
                    + "/arena quarantine release " + name + " confirm.");
            sender.sendMessage(ChatColor.YELLOW + "Konfirmasi dengan: /arena recover " + name + " discard confirm");
            return;
        }
        if (matches.containsKey(name)) {
            sender.sendMessage(ChatColor.RED + "Masih ada state pertandingan untuk arena ini. Jalankan /arena recover " + name
                    + " lebih dulu agar perubahan match dipulihkan.");
            return;
        }
        if (buildingArenas.contains(name) || hasActiveGenerationTasks(name)) {
            sender.sendMessage(ChatColor.RED + "Arena sedang diproses; tidak dapat dilepaskan.");
            return;
        }
        String arenaRoot = "arenas." + name;
        String quarantineRoot = "quarantined-arenas." + name;
        String previousState = getConfig().getString(arenaRoot + ".generation-state", "UNKNOWN");
        String discardedAt = java.time.Instant.now().toString();
        try {
            // One atomic write: either the arena record is replaced by the quarantine record, or nothing changes.
            commitConfig(List.of(arenaRoot, quarantineRoot), () -> {
                getConfig().set(quarantineRoot + ".world", data.worldName);
                getConfig().set(quarantineRoot + ".center-x", data.centerX);
                getConfig().set(quarantineRoot + ".center-z", data.centerZ);
                getConfig().set(quarantineRoot + ".radius", data.radius);
                getConfig().set(quarantineRoot + ".previous-state", previousState);
                getConfig().set(quarantineRoot + ".discarded-at", discardedAt);
                getConfig().set(arenaRoot, null);
            });
        } catch (RuntimeException ex) {
            sender.sendMessage(ChatColor.RED + "Registrasi tidak dilepas karena penyimpanan gagal; tidak ada yang berubah: " + ex.getMessage());
            return;
        }
        incompleteArenas.remove(name);
        arenas.remove(name);
        quarantinedAreas.put(name, new QuarantinedArea(name, data.worldName, data.centerX, data.centerZ, data.radius));
        List<String> preserved = List.of();
        try {
            preserved = snapshots.quarantine(name, System.currentTimeMillis());
        } catch (IOException ex) {
            getLogger().warning("Berkas snapshot arena " + name + " tidak dapat diganti nama dan tetap berada di tempatnya: " + ex.getMessage());
        }
        getLogger().warning("Registrasi arena " + name + " dilepas secara administratif (state sebelumnya " + previousState
                + "). World tidak diubah dan mungkin parsial. Berkas yang dipertahankan: " + preserved);
        sender.sendMessage(ChatColor.YELLOW + "Registrasi arena " + name + " dilepas. World TIDAK diubah dan mungkin masih parsial.");
        sender.sendMessage(ChatColor.GRAY + (preserved.isEmpty()
                ? "Tidak ada berkas snapshot yang ditemukan/dipindahkan."
                : "Berkas snapshot dipertahankan sebagai: " + String.join(", ", preserved) + " (folder snapshots)."));
        sender.sendMessage(ChatColor.GRAY + "Area dikarantina untuk arena baru. Lepaskan dengan /arena quarantine release " + name + " confirm.");
    }

    void quarantineCommand(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            if (quarantinedAreas.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "Tidak ada area karantina.");
                return;
            }
            sender.sendMessage(ChatColor.GOLD + "Area karantina (world mungkin parsial):");
            for (QuarantinedArea area : quarantinedAreas.values()) {
                sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.AQUA + area.name() + ChatColor.GRAY + " | " + area.worldName()
                        + " | pusat " + area.centerX() + "," + area.centerZ() + " | radius " + area.radius());
            }
            return;
        }
        if (!args[1].equalsIgnoreCase("release") || args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena quarantine list | /arena quarantine release <nama> confirm");
            return;
        }
        String name = args[2].toLowerCase(Locale.ROOT);
        QuarantinedArea area = quarantinedAreas.get(name);
        if (area == null) {
            sender.sendMessage(ChatColor.RED + "Tidak ada area karantina dengan nama itu.");
            return;
        }
        if (args.length < 4 || !args[3].equalsIgnoreCase("confirm")) {
            sender.sendMessage(ChatColor.RED + "Melepas karantina menandai area " + name + " boleh dipakai arena baru, "
                    + "walaupun world di sana mungkin masih parsial.");
            sender.sendMessage(ChatColor.YELLOW + "Konfirmasi dengan: /arena quarantine release " + name + " confirm");
            return;
        }
        String root = "quarantined-arenas." + name;
        try {
            commitConfig(List.of(root), () -> getConfig().set(root, null));
        } catch (RuntimeException ex) {
            sender.sendMessage(ChatColor.RED + "Karantina tidak dilepas karena penyimpanan gagal: " + ex.getMessage());
            return;
        }
        quarantinedAreas.remove(name);
        getLogger().warning("Karantina area " + name + " dilepas oleh " + sender.getName() + ".");
        sender.sendMessage(ChatColor.GREEN + "Karantina " + name + " dilepas. Berkas snapshot berlabel .discarded- tidak dihapus.");
    }

    void restoreArenaSnapshot(CommandSender sender, ArenaData arena, boolean deleteSnapshot, Runnable after) {
        restoreArenaSnapshot(sender, arena, deleteSnapshot, after, ex -> {
            sender.sendMessage(ChatColor.RED + "Pemulihan snapshot arena gagal: " + ex.getMessage());
            getLogger().severe("Pemulihan snapshot arena " + arena.name + " gagal: " + ex.getMessage());
        });
    }

    void restoreArenaSnapshot(CommandSender sender, ArenaData arena, boolean deleteSnapshot,
                              Runnable after, Consumer<RuntimeException> onFailure) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) {
            markRecoveryRequired(arena.name);
            onFailure.accept(new IllegalStateException("World " + arena.worldName + " tidak termuat."));
            return;
        }

        final SnapshotStore.LoadedSnapshot snapshot;
        try {
            snapshot = snapshots.loadSnapshot(arena);
        } catch (IOException ex) {
            markRecoveryRequired(arena.name);
            onFailure.accept(new IllegalStateException("Gagal membaca snapshot: " + ex.getMessage(), ex));
            return;
        }

        Map<BlockPos, String> data = snapshot.blocks();
        if (snapshot.legacy()) {
            getLogger().warning("Snapshot ARN2 arena " + arena.name
                    + " tidak memiliki origin absolut; memakai centerY dari config untuk kompatibilitas.");
        }
        sender.sendMessage(ChatColor.YELLOW + "Memulihkan " + data.size() + " posisi blok untuk arena " + arena.name + "...");
        // Snapshots may legitimately extend to the widest supported footprint: an interrupted
        // mode change can leave a snapshot taken at the new radius beside the old arena record.
        int footprintBound = Math.max(arena.radius, Math.max(TDM_RADIUS, RELIC_RADIUS));
        restoreMapBatched(world, arena, snapshot.centerY(), footprintBound, data, () -> {
            // Persist the caller's final state before deleting recovery material.
            // A crash after this callback can leave an orphan snapshot, but never an
            // incomplete arena with no snapshot available to recover it.
            try {
                after.run();
            } catch (RuntimeException ex) {
                markRecoveryRequired(arena.name);
                onFailure.accept(ex);
                return;
            }
            if (deleteSnapshot) {
                try {
                    snapshots.delete(arena.name);
                } catch (IOException ex) {
                    getLogger().warning("World arena " + arena.name
                            + " sudah dipulihkan, tetapi berkas snapshot orphan gagal dibersihkan: " + ex.getMessage());
                }
            }
        }, onFailure);
    }

    BukkitTask restoreMapBatched(World world, ArenaData arena, Map<BlockPos, String> changes, Runnable after) {
        return restoreMapBatched(world, arena, changes, after, ex ->
                getLogger().severe("Pemulihan blok arena " + arena.name + " gagal: " + ex.getMessage()));
    }

    BukkitTask restoreMapBatched(World world, ArenaData arena, Map<BlockPos, String> changes,
                                 Runnable after, Consumer<RuntimeException> onFailure) {
        // In-memory match/lobby changes are validated for BlockData and world height only:
        // the preparation lobby legitimately sits above the terrain band.
        return restoreMapBatched(world, arena, arena.centerY, -1, changes, after, onFailure);
    }

    private BukkitTask restoreMapBatched(World world, ArenaData arena, int originY, int horizontalBound,
                                         Map<BlockPos, String> changes, Runnable after,
                                         Consumer<RuntimeException> onFailure) {
        if (world == null) {
            onFailure.accept(new IllegalStateException("World arena tidak tersedia."));
            return null;
        }
        // Validate EVERYTHING before the first block is written, so one bad entry can never
        // leave the world partially restored.
        final List<SnapshotRestoreValidator.Entry<org.bukkit.block.data.BlockData>> plan;
        try {
            plan = SnapshotRestoreValidator.<org.bukkit.block.data.BlockData>validate(changes,
                    arena.centerX, originY, arena.centerZ, horizontalBound,
                    world.getMinHeight(), world.getMaxHeight(), raw -> Bukkit.createBlockData(raw));
        } catch (RuntimeException ex) {
            if (isIncompleteArena(arena)) markRecoveryRequired(arena.name);
            onFailure.accept(ex);
            return null;
        }
        BukkitTask[] taskReference = new BukkitTask[1];
        BukkitRunnable restoreTask = new BukkitRunnable() {
            private int index;

            private void finishTask() {
                untrackGenerationTask(arena.name, taskReference[0]);
            }

            @Override
            public void run() {
                try {
                    int end = Math.min(index + RESTORE_BLOCKS_PER_TICK, plan.size());
                    for (; index < end; index++) {
                        SnapshotRestoreValidator.Entry<org.bukkit.block.data.BlockData> entry = plan.get(index);
                        world.getBlockAt(entry.worldX, entry.worldY, entry.worldZ).setBlockData(entry.data, false);
                    }
                } catch (RuntimeException ex) {
                    cancel();
                    finishTask();
                    if (isIncompleteArena(arena)) markRecoveryRequired(arena.name);
                    onFailure.accept(ex);
                    return;
                }

                if (index >= plan.size()) {
                    cancel();
                    finishTask();
                    try {
                        after.run();
                    } catch (RuntimeException ex) {
                        onFailure.accept(ex);
                    }
                }
            }
        };
        taskReference[0] = restoreTask.runTaskTimer(this, 1L, 1L);
        trackGenerationTask(arena.name, taskReference[0]);
        return taskReference[0];
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
        if (buildingArenas.contains(name) || incompleteArenas.containsKey(name)
                || hasActiveGenerationTasks(name)) {
            sender.sendMessage(ChatColor.RED + "Arena sedang diproses atau memerlukan recovery. Gunakan /arena recover " + name + ".");
            return;
        }
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
        stopAndRestoreMatch(name, after, ex ->
                getLogger().severe("Stop-match arena " + name + " gagal memulihkan blok: " + ex.getMessage()));
    }

    void stopAndRestoreMatch(String name, Runnable after, Consumer<RuntimeException> onFailure) {
        MatchState state = matches.get(name);
        ArenaData resolvedArena = arenas.get(name);
        if (resolvedArena == null) resolvedArena = incompleteArenas.get(name);
        if (state == null || resolvedArena == null) {
            try {
                after.run();
            } catch (RuntimeException ex) {
                onFailure.accept(ex);
            }
            return;
        }
        final ArenaData arena = resolvedArena;
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
            onFailure.accept(new IllegalStateException("World arena tidak termuat."));
            return;
        }
        restoreMapBatched(world, arena, combinedMatchChanges(state), () -> {
            removeRelicItems(arena, state);
            matches.remove(name);
            after.run();
        }, onFailure);
    }

    void cancelMatchTasks(MatchState state) {
        if (state.timer != null) state.timer.cancel();
        if (state.preparationTimer != null) state.preparationTimer.cancel();
        if (state.lobbyBuildTask != null) state.lobbyBuildTask.cancel();
        if (state.lobbyRestoreTask != null) {
            state.lobbyRestoreTask.cancel();
            untrackGenerationTask(state.arenaName, state.lobbyRestoreTask);
        }
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
                if (!restorePlayerSnapshot(player, entry.getValue())) {
                    queuePendingPlayerRestore(entry.getKey(), entry.getValue());
                }
            } else {
                queuePendingPlayerRestore(entry.getKey(), entry.getValue());
            }
        }
        state.playerSnapshots.clear();
        state.relicOriginalWalkSpeeds.clear();
    }

    /** Simpan snapshot sebelum inventory/lokasi pemain disentuh sistem arena. */
    boolean persistPlayerSnapshot(UUID playerId, PlayerSnapshot snapshot) {
        if (playerSnapshotStore == null) {
            getLogger().severe("PlayerSnapshotStore belum siap; pemain " + playerId + " tidak boleh masuk arena.");
            return false;
        }
        try {
            playerSnapshotStore.save(playerId, snapshot);
            return true;
        } catch (IOException ex) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "Gagal menyimpan snapshot pemain " + playerId + ". Join arena dibatalkan.", ex);
            return false;
        }
    }

    /** Simpan snapshot pending ke memori dan disk agar aman saat restart. */
    void queuePendingPlayerRestore(UUID playerId, PlayerSnapshot snapshot) {
        pendingPlayerRestores.put(playerId, snapshot);
        if (!persistPlayerSnapshot(playerId, snapshot)) {
            getLogger().severe("Snapshot pemain " + playerId
                    + " hanya aman di memori saat ini. Periksa storage plugin sebelum server dihentikan.");
        }
    }

    /** Coba pulihkan snapshot pemain yang pending setelah login/respawn. */
    void restorePendingPlayerRestore(Player player, PlayerSnapshot snapshot) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        if (!player.isOnline() || player.isDead()
                || pendingPlayerRestores.get(playerId) != snapshot) return;

        if (restorePlayerSnapshot(player, snapshot)) {
            pendingPlayerRestores.remove(playerId, snapshot);
        } else {
            getLogger().warning("Snapshot pemain " + playerId
                    + " belum dipulihkan (world asal belum tersedia atau teleport ditolak). Snapshot tetap pending.");
        }
    }

    /** Hapus file snapshot hanya setelah PlayerSnapshot.restore berhasil. */
    boolean restorePlayerSnapshot(Player player, PlayerSnapshot snapshot) {
        if (player == null || !player.isOnline() || player.isDead()) return false;
        if (!snapshot.restore(player)) return false;

        if (playerSnapshotStore != null) {
            try {
                playerSnapshotStore.delete(player.getUniqueId());
            } catch (IOException ex) {
                getLogger().log(java.util.logging.Level.SEVERE,
                        "Pemain " + player.getUniqueId()
                                + " berhasil dipulihkan, tetapi file snapshot gagal dihapus.", ex);
            }
        }
        return true;
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
        if (buildingArenas.contains(old.name) || hasActiveGenerationTasks(old.name)) {
            sender.sendMessage(ChatColor.RED + "Arena sedang diproses; mode tidak diubah.");
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
        // setMode no longer touches maps or config itself. The new mode is only passed to the
        // regeneration pipeline, which takes the lock first and persists state transactionally;
        // the arena keeps its old mode/radius record until BUILDING is durably saved.
        if (!regenerateInternal(sender, old.name, null, mode)) return;
        sender.sendMessage(ChatColor.GREEN + "Mode arena " + old.name + " dipilih: " + mode.toUpperCase(Locale.ROOT) + ".");
        sender.sendMessage(ChatColor.YELLOW + "Membangun ulang bentuk arena khusus mode " + mode.toUpperCase(Locale.ROOT) + "...");
    }

    @EventHandler
    public void onArenaSystemPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String url = getConfig().getString("resource-pack.assassin-dagger.url", "").trim();
        if (url.isEmpty()) return; // Manual local packs cannot be verified by the server.

        String sha1 = getConfig().getString("resource-pack.assassin-dagger.sha1", "").trim()
                .toLowerCase(Locale.ROOT);
        if (!sha1.matches("[0-9a-f]{40}")) {
            getLogger().warning("Resource pack Assassin Dagger tidak dikirim ke " + player.getName()
                    + ": resource-pack.assassin-dagger.sha1 harus berupa 40 karakter SHA-1 heksadesimal.");
            player.sendMessage(ChatColor.RED + "Resource pack ArenaSystem belum dikonfigurasi dengan benar. "
                    + "Tampilan dagger mungkin tidak tersedia.");
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            try {
                player.setResourcePack(ASSASSIN_DAGGER_RESOURCE_PACK_ID, url, HexFormat.of().parseHex(sha1),
                        Component.text("ArenaSystem Assassin Dagger — model dan tekstur senjata"), false);
            } catch (IllegalArgumentException ex) {
                getLogger().warning("URL/hash resource pack Assassin Dagger tidak valid: " + ex.getMessage());
                player.sendMessage(ChatColor.RED + "Resource pack ArenaSystem gagal diminta. Beri tahu admin.");
            }
        }, 20L);
    }

    @EventHandler
    public void onAssassinDaggerResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!ASSASSIN_DAGGER_RESOURCE_PACK_ID.equals(event.getID())) return;
        Player player = event.getPlayer();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        if (status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            player.sendMessage(ChatColor.GREEN + "Resource pack ArenaSystem berhasil dimuat. Model Assassin Dagger siap.");
            return;
        }

        if (status == PlayerResourcePackStatusEvent.Status.DECLINED
                || status == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || status == PlayerResourcePackStatusEvent.Status.FAILED_RELOAD
                || status == PlayerResourcePackStatusEvent.Status.INVALID_URL
                || status == PlayerResourcePackStatusEvent.Status.DISCARDED) {
            player.sendMessage(ChatColor.RED + "Resource pack ArenaSystem belum aktif. "
                    + ChatColor.YELLOW + "Model Assassin Dagger bisa tampil ungu/hitam.");
            player.sendMessage(ChatColor.GRAY + "Aktifkan server resource pack atau pasang pack AssassinDagger 26.3.");
            getLogger().info("Resource pack ArenaSystem belum aktif untuk " + player.getName()
                    + " (status: " + status + ").");
        }
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
        UUID playerId = player.getUniqueId();
        if (pendingPlayerRestores.containsKey(playerId)) {
            player.sendMessage(ChatColor.RED
                    + "Data pemainmu masih menunggu pemulihan. Relog atau hubungi admin sebelum masuk arena lagi.");
            return;
        }
        MatchState state = matches.computeIfAbsent(arena.name, ignored -> new MatchState(arena.name, arena.mode));
        if (state.running || state.preparing || state.launching) {
            player.sendMessage(ChatColor.RED + "Lobby persiapan atau pertandingan sudah dimulai. Join ditutup.");
            return;
        }
        PlayerSnapshot savedSnapshot = state.playerSnapshots.get(playerId);
        if (savedSnapshot == null) {
            savedSnapshot = PlayerSnapshot.capture(player);
            if (!persistPlayerSnapshot(playerId, savedSnapshot)) {
                player.sendMessage(ChatColor.RED
                        + "Join arena dibatalkan karena backup inventory gagal disimpan. Hubungi admin.");
                if (state.teams.isEmpty() && state.playerSnapshots.isEmpty()) {
                    matches.remove(arena.name, state);
                }
                return;
            }
            state.playerSnapshots.put(playerId, savedSnapshot);
        }
        state.selectedClasses.putIfAbsent(player.getUniqueId(), "knight");
        state.teams.put(player.getUniqueId(), team);
        player.teleport(spawnLocation(arena, team));
        player.sendMessage(ChatColor.GREEN + "Kamu bergabung dengan tim " + team.toUpperCase(Locale.ROOT)
                + " di arena " + arena.name + ".");
        showTeamJoinedTitle(player, arena, state, team);
        announce(state, ChatColor.YELLOW + player.getName() + " bergabung ke tim " + team + ".");
        // The queue roster is visible immediately, before /arena start creates preparation.
        updateMatchHud(state);
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
            if (snapshot != null && !restorePlayerSnapshot(player, snapshot)) {
                queuePendingPlayerRestore(player.getUniqueId(), snapshot);
            }
            player.sendMessage(ChatColor.GREEN + "Kamu keluar dari antrean arena " + state.arenaName + ".");
            if (state.teams.isEmpty()) {
                if (state.preparing) {
                    stopAndRestoreMatch(state.arenaName, () -> getLogger().info("Lobby persiapan " + state.arenaName + " dibatalkan karena tidak ada pemain."));
                } else {
                    matches.remove(state.arenaName);
                }
            } else {
                if (state.preparing) adaptSinglePlayerPreparation(state);
                // Queue and preparation rosters are live and must reflect departures too.
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

    /** Broadcast HUD konfirmasi join ke seluruh roster arena, bukan hanya pemain yang join. */
    void showTeamJoinedTitle(Player joinedPlayer, ArenaData arena, MatchState state, String team) {
        if (joinedPlayer == null || arena == null || state == null || team == null) return;
        ChatColor color = teamColor(team);
        String teamName = teamDisplayName(team);
        String subtitle = ChatColor.WHITE + joinedPlayer.getName() + " bergabung ke "
                + color + teamName + ChatColor.WHITE + " TEAM";
        for (UUID memberId : state.teams.keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) continue;
            member.sendTitle(ChatColor.GOLD + "ARENA", subtitle
                    + ChatColor.GRAY + " • " + arena.name, 5, 55, 10);
        }
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
        // A new class kit must never inherit an off-hand weapon from the previous class.
        player.getInventory().setItemInOffHand(null);

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
                // Dual Golden Blades are fixed to the main hand and off-hand.
                player.getInventory().setItem(0, createAssassinBlade());
                player.getInventory().setItemInOffHand(createAssassinBlade());
                player.getInventory().setItem(1, new ItemStack(Material.COOKED_BEEF, 8));
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
        if (selected.equals("assassin")) player.getInventory().setHeldItemSlot(0);
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

    private ItemStack createAssassinBlade() {
        ItemStack blade = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta meta = blade.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Assassin's Twin Blade");
            meta.setLore(List.of(
                    ChatColor.GRAY + "Main-hand: serangan normal",
                    ChatColor.GRAY + "Right-click: serangan off-hand",
                    ChatColor.DARK_GRAY + "Off-hand: 2.0 damage | cooldown 12 tick"
            ));
            meta.setUnbreakable(true);
            // 26.3 item_model points Java clients at the Assassin dagger assets
            // supplied by the companion Fabric client mod / resource pack.
            meta.setItemModel(new NamespacedKey(this, "assassin_dagger"));
            meta.getPersistentDataContainer().set(assassinBladeKey, PersistentDataType.BYTE, (byte) 1);
            blade.setItemMeta(meta);
        }
        return blade;
    }

    boolean isAssassinBlade(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_SWORD || !item.hasItemMeta()
                || assassinBladeKey == null) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(assassinBladeKey, PersistentDataType.BYTE);
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
        String name = arenaName.toLowerCase(Locale.ROOT);
        if (buildingArenas.contains(name) || hasActiveGenerationTasks(name)) {
            sender.sendMessage(ChatColor.RED + "Arena sedang diproses atau bloknya sedang dipulihkan.");
            return;
        }
        ArenaData arena = arenas.get(name);
        MatchState state = matches.get(name);
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

    /** Membuat atau memperbarui sidebar antrean, preparation, dan pertandingan. */
    void updateMatchHud(MatchState state) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        boolean preparationView = state.preparing || state.launching;
        boolean queueView = !state.running && !preparationView;
        int targetScore = state.mode.equals("relic_rush")
                ? getConfig().getInt("matches.relic-target-score", RELIC_TARGET_SCORE)
                : getConfig().getInt("matches.tdm-target-score", TDM_TARGET_SCORE);

        for (UUID uuid : state.teams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;

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
            List<String> lines = new ArrayList<>();

            if (queueView) {
                // This is the live roster shown as soon as the first player joins a team.
                // Empty teams are intentionally omitted until somebody joins them.
                lines.add(ChatColor.GOLD + "TEAM ROSTER");
                appendOccupiedTeamRoster(lines, state, arenas.get(state.arenaName));
                lines.add(ChatColor.DARK_GRAY + "Menunggu start...");
            } else if (preparationView) {
                lines.add(ChatColor.GRAY + "Mulai: "
                        + ChatColor.YELLOW + formatTime(state.preparationSecondsRemaining));
                lines.add(ChatColor.GOLD + "ROSTER");
                appendOccupiedTeamRoster(lines, state, arenas.get(state.arenaName));
                if (state.mode.equals("tdm")) {
                    lines.add(ChatColor.GRAY + "Class: " + ChatColor.WHITE + className);
                } else {
                    lines.add(ChatColor.GOLD + "Relic: " + ChatColor.WHITE + "Belum dimulai");
                }
            } else {
                String mode = state.mode.replace('_', ' ').toUpperCase(Locale.ROOT);
                lines.add(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + mode);
                lines.add(ChatColor.GRAY + "Waktu: "
                        + ChatColor.YELLOW + formatTime(state.secondsRemaining));
                for (String listedTeam : teamNamesFor(arenas.get(state.arenaName))) {
                    lines.add(teamColor(listedTeam) + listedTeam.toUpperCase(Locale.ROOT) + ": "
                            + ChatColor.WHITE + state.teamScores.getOrDefault(listedTeam, 0) + "/" + targetScore);
                }
                lines.add(ChatColor.GRAY + "Tim: "
                        + teamColor(team.toLowerCase(Locale.ROOT)) + team);
                if (state.mode.equals("relic_rush")) {
                    lines.add(ChatColor.GOLD + "Misi: " + ChatColor.WHITE + "Antar Relic");
                    String relicStatus;
                    if (state.relicCarrier != null) {
                        Player carrier = Bukkit.getPlayer(state.relicCarrier);
                        String carrierTeam = state.teams.getOrDefault(state.relicCarrier, "?")
                                .toUpperCase(Locale.ROOT);
                        relicStatus = carrier == null ? "Tidak diketahui"
                                : carrier.getName() + " (" + carrierTeam + ")";
                    } else {
                        relicStatus = state.relicDropped ? "Terjatuh" : "Di altar tengah";
                    }
                    lines.add(ChatColor.GOLD + "Relic: " + ChatColor.WHITE + relicStatus);
                } else {
                    lines.add(ChatColor.GRAY + "Class: " + ChatColor.WHITE + className);
                    lines.add(ChatColor.GOLD + "Misi: " + ChatColor.WHITE + "Capai target kill");
                }
                lines.add(ChatColor.DARK_GRAY + "Arena Match");
            }

            // Sidebar supports at most 15 visible score rows. Keep the roster compact
            // so larger teams do not accidentally push the important lines away.
            if (lines.size() > 15) {
                lines = new ArrayList<>(lines.subList(0, 15));
            }
            int score = lines.size();
            for (String line : lines) {
                objective.getScore(line).setScore(score--);
            }
        }
        refreshRelicGlow(state);
    }

    /** Menampilkan hanya tim yang memiliki anggota online, dengan nama pemain per tim. */
    private void appendOccupiedTeamRoster(List<String> lines, MatchState state, ArenaData arena) {
        for (String teamName : teamNamesFor(arena)) {
            ChatColor color = teamColor(teamName);
            List<String> names = new ArrayList<>();
            for (Map.Entry<UUID, String> entry : state.teams.entrySet()) {
                if (!teamName.equals(entry.getValue())) continue;
                Player member = Bukkit.getPlayer(entry.getKey());
                if (member != null && member.isOnline()) names.add(member.getName());
            }
            if (names.isEmpty()) continue;

            String teamLabel = teamName.toUpperCase(Locale.ROOT);
            int row = 0;
            StringBuilder rowNames = new StringBuilder();
            for (String name : names) {
                int separatorLength = rowNames.isEmpty() ? 0 : 2;
                String prefix = row == 0 ? teamLabel + ": " : "↳ ";
                if (!rowNames.isEmpty()
                        && prefix.length() + rowNames.length() + separatorLength + name.length() > 36) {
                    lines.add(color + prefix + ChatColor.WHITE + rowNames);
                    row++;
                    rowNames.setLength(0);
                    prefix = "↳ ";
                    separatorLength = 0;
                }
                if (!rowNames.isEmpty()) rowNames.append(", ");
                rowNames.append(name);
            }
            if (!rowNames.isEmpty()) {
                String prefix = row == 0 ? teamLabel + ": " : "↳ ";
                lines.add(color + prefix + ChatColor.WHITE + rowNames);
            }
        }
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




















    /** Cari footprint arena dalam volume vertikal yang sama dengan findArena(). */
    ArenaData findArenaFootprint(Location location) {
        if (location == null || location.getWorld() == null) return null;
        String worldName = location.getWorld().getName();
        int blockY = location.getBlockY();
        for (ArenaData arena : arenas.values()) {
            if (!arena.worldName.equals(worldName)) continue;
            if (Math.abs(location.getBlockX() - arena.centerX) > arena.radius) continue;
            if (Math.abs(location.getBlockZ() - arena.centerZ) > arena.radius) continue;
            if (blockY < arena.minSurfaceY - 20 || blockY > arena.maxSurfaceY + 64) continue;
            return arena;
        }
        for (ArenaData arena : incompleteArenas.values()) {
            if (!arena.worldName.equals(worldName)) continue;
            if (Math.abs(location.getBlockX() - arena.centerX) > arena.radius) continue;
            if (Math.abs(location.getBlockZ() - arena.centerZ) > arena.radius) continue;
            if (blockY < arena.minSurfaceY - 20 || blockY > arena.maxSurfaceY + 64) continue;
            return arena;
        }
        return null;
    }

    /** True when the block lies inside the protected volume of an arena that is not COMPLETE. */
    boolean touchesIncompleteArena(Location location) {
        if (incompleteArenas.isEmpty() || location == null || location.getWorld() == null) return false;
        return IncompleteArenaGuard.touches(incompleteArenas.values(), location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    boolean touchesIncompleteArenaMovement(Location location, int dx, int dy, int dz) {
        if (incompleteArenas.isEmpty() || location == null || location.getWorld() == null) return false;
        return IncompleteArenaGuard.touchesMovement(incompleteArenas.values(), location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), dx, dy, dz);
    }

    ArenaData findArena(Location location) {
        if (location == null || location.getWorld() == null) return null;
        for (ArenaData arena : arenas.values()) {
            if (incompleteArenas.containsKey(arena.name)) continue;
            if (!arena.worldName.equals(location.getWorld().getName())) continue;
            if (Math.abs(location.getBlockX() - arena.centerX) > arena.radius) continue;
            if (Math.abs(location.getBlockZ() - arena.centerZ) > arena.radius) continue;
            if (location.getBlockY() < arena.minSurfaceY - 20 || location.getBlockY() > arena.maxSurfaceY + 64) continue;
            return arena;
        }
        return null;
    }

    void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "ArenaSystem 0.2.8.6" + ChatColor.GRAY + " — mode-aware hybrid arena, terrain normalizer & match engine");
        sender.sendMessage(ChatColor.YELLOW + "/arena create <nama> [seed] [tdm|relic_rush] " + ChatColor.GRAY + "- seed menentukan variasi jalur dan cover");
        sender.sendMessage(ChatColor.YELLOW + "/arena list | info <nama> | tp <nama> <red|blue|green|yellow|center>");
        sender.sendMessage(ChatColor.YELLOW + "/arena delete <nama> confirm | regenerate <nama> [seed] | recover <nama> [discard confirm]");
        sender.sendMessage(ChatColor.YELLOW + "/arena quarantine list | release <nama> confirm " + ChatColor.GRAY + "- area dari recover discard");
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
