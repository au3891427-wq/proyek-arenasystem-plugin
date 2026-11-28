package id.hadziq.arenasystem;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

// $VF: Compiled from ArenaSystemPlugin.java
public final class ArenaSystemPlugin extends JavaPlugin implements Listener {
    static final int TDM_RADIUS = 40;
    static final int RELIC_RADIUS = 56;
    static final int SCAN_COLUMNS_PER_TICK = 256;
    static final int BUILD_BLOCKS_PER_TICK = 300;
    static final int RESTORE_BLOCKS_PER_TICK = 300;
    static final int DEFAULT_MATCH_SECONDS = 300;
    static final int TDM_TARGET_SCORE = 20;
    static final int RELIC_TARGET_SCORE = 3;
    static final int TDM_SPAWN_OFFSET = 27;
    private static final UUID ASSASSIN_DAGGER_RESOURCE_PACK_ID = UUID.fromString("2efb65ad-5f8a-4c26-a8b4-2f4d84c98d26");
    final Map<String, ArenaData> arenas = new HashMap<>();
    final Map<String, ArenaData> incompleteArenas = new HashMap<>();
    final Map<String, MatchState> matches = new HashMap<>();
    final Map<UUID, PlayerSnapshot> pendingPlayerRestores = new HashMap<>();
    final Set<String> buildingArenas = new HashSet<>();
    final Map<String, ArenaSystemPlugin.QuarantinedArea> quarantinedAreas = new HashMap<>();
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
    private final ConfigTransaction.ConfigAccess configAccess = new ConfigTransaction.ConfigAccess()    // $VF: Compiled from ArenaSystemPlugin.java
 {
        @Override
        public Object get(String path) {
            return ArenaSystemPlugin.this.getConfig().get(path);
        }

        @Override
        public void set(String path, Object value) {
            ArenaSystemPlugin.this.getConfig().set(path, value);
        }

        @Override
        public Map<String, Object> leafValues(String root) {
            ConfigurationSection section = ArenaSystemPlugin.this.getConfig().getConfigurationSection(root);
            if (section == null) {
                return null;
            }

            Map<String, Object> leaves = new LinkedHashMap<>();

            for (Entry<String, Object> entry : section.getValues(true).entrySet()) {
                if (!(entry.getValue() instanceof ConfigurationSection)) {
                    leaves.put(entry.getKey(), entry.getValue());
                }
            }

            return leaves;
        }
    };

    @Override
    public void onEnable() {
        this.getConfig().addDefault("matches.duration-seconds", 300);
        this.getConfig().addDefault("matches.preparation-seconds", 30);
        this.getConfig().addDefault("matches.tdm-target-score", 20);
        this.getConfig().addDefault("matches.relic-target-score", 3);
        this.getConfig().addDefault("matches.random-stuff-interval-seconds", 30);
        this.getConfig().addDefault("matches.random-stuff-max-recipients", 6);
        this.getConfig().addDefault("matches.random-stuff-expire-seconds", 20);
        this.getConfig().addDefault("matches.random-stuff-ground-expire-seconds", 5);
        if (this.getConfig().contains("matches.allow-solo-test", true)
            && !this.getConfig().contains("matches.allow-single-player", true)) {
            this.getConfig()
                .set("matches.allow-single-player", this.getConfig().getBoolean("matches.allow-solo-test", true));
        }

        this.getConfig().set("matches.allow-solo-test", null);
        this.getConfig().addDefault("matches.allow-single-player", true);
        this.getConfig().addDefault("matches.death-camera-seconds", 3);
        this.getConfig().addDefault("matches.keep-inventory.tdm", true);
        this.getConfig().addDefault("matches.keep-inventory.relic_rush", true);
        this.getConfig().addDefault("resource-pack.assassin-dagger.url", "");
        this.getConfig().addDefault("resource-pack.assassin-dagger.sha1", "c4bea426f1b8f643dd74aa5bf56588c966b6b4d2");
        this.getConfig().addDefault("updates.enabled", true);
        this.getConfig().addDefault("updates.check-interval-hours", 6);
        this.getConfig()
            .addDefault(
                "updates.manifest-url",
                "https://raw.githubusercontent.com/au3891427-wq/proyek-arenasystem-plugin/main/arena-system-update-manifest.json"
            );
        this.getConfig().addDefault("terrain.max-adjacent-step", 1);
        if (this.getConfig().getInt("terrain.max-adjacent-step", 1) != 1) {
            this.getLogger().warning("terrain.max-adjacent-step sekarang wajib 1; nilai konfigurasi lama diubah ke 1.");
            this.getConfig().set("terrain.max-adjacent-step", 1);
        }

        this.getConfig().addDefault("terrain.max-column-adjustment", 12);
        this.getConfig().addDefault("terrain.max-seed-delta", 6);
        this.getConfig().addDefault("protection.allow-vegetation-break-by-default", true);
        this.getConfig().addDefault("class-abilities.knight_guard.cooldown-seconds", 26);
        this.getConfig().addDefault("class-abilities.knight_guard.duration-seconds", 3);
        this.getConfig().addDefault("class-abilities.tank_endurance.cooldown-seconds", 45);
        this.getConfig().addDefault("class-abilities.tank_endurance.duration-seconds", 5);
        this.getConfig().addDefault("class-abilities.assassin_burst.cooldown-seconds", 30);
        this.getConfig().addDefault("class-abilities.assassin_burst.duration-seconds", 3);
        this.getConfig().addDefault("class-abilities.ninja_vanish.cooldown-seconds", 34);
        this.getConfig().addDefault("class-abilities.ninja_vanish.duration-seconds", 6);
        this.getConfig().addDefault("class-abilities.ninja_shadow_step.cooldown-seconds", 20);
        this.getConfig().addDefault("class-abilities.ninja_shadow_step.distance-blocks", 8);
        this.getConfig().addDefault("class-abilities.archer_volley.cooldown-seconds", 20);
        this.getConfig().addDefault("class-abilities.sniper_mark.cooldown-seconds", 25);
        this.getConfig().addDefault("class-abilities.boxer_rapid_jab.cooldown-seconds", 20);
        this.getConfig().addDefault("class-abilities.boxer_barrage.cooldown-seconds", 28);
        this.getConfig().addDefault("class-abilities.demoman_dynamite.cooldown-seconds", 10);
        this.getConfig().addDefault("class-abilities.demoman_primary_bomb.cooldown-seconds", 2);
        this.getConfig().addDefault("class-abilities.demoman_blast_mine.cooldown-seconds", 20);
        this.getConfig().addDefault("class-abilities.knight_rally.cooldown-seconds", 32);
        this.getConfig().addDefault("class-abilities.tank_ground_slam.cooldown-seconds", 30);
        this.getConfig().addDefault("class-abilities.assassin_venom_edge.cooldown-seconds", 22);
        this.getConfig().addDefault("class-abilities.archer_pinning_shot.cooldown-seconds", 18);
        this.getConfig().addDefault("class-abilities.sniper_focus_shot.cooldown-seconds", 25);
        this.getConfig().options().copyDefaults(true);
        this.migrateLegacyCooldownDefault("class-abilities.boxer_rapid_jab.cooldown-seconds", 16, 20);
        this.migrateLegacyCooldownDefault("class-abilities.demoman_dynamite.cooldown-seconds", 14, 10);
        this.migrateLegacyCooldownDefault("class-abilities.demoman_blast_mine.cooldown-seconds", 28, 20);
        this.saveConfig();
        this.snapshots = new SnapshotStore(this.getDataFolder());
        this.playerSnapshotStore = new PendingPlayerSnapshotStore(this.getDataFolder());
        Map<UUID, PlayerSnapshot> recoveredPlayerSnapshots = this.playerSnapshotStore.loadAll(this.getLogger());
        this.pendingPlayerRestores.putAll(recoveredPlayerSnapshots);
        if (!recoveredPlayerSnapshots.isEmpty()) {
            this.getLogger()
                .warning(
                    "Ditemukan "
                        + recoveredPlayerSnapshots.size()
                        + " snapshot pemain yang belum dipulihkan dari sesi sebelumnya. Snapshot akan dipulihkan saat pemain login."
                );
        }

        this.relicKey = new NamespacedKey(this, "arena_relic");
        this.classSelectorKey = new NamespacedKey(this, "class_selector");
        this.classSkillKey = new NamespacedKey(this, "class_skill");
        this.assassinBladeKey = new NamespacedKey(this, "assassin_blade");
        this.randomStuffKey = new NamespacedKey(this, "random_stuff");
        this.randomStuffDropExpiresKey = new NamespacedKey(this, "random_stuff_drop_expires_at");
        this.randomStuffProjectileKey = new NamespacedKey(this, "random_stuff_projectile");
        if (this.getCommand("arena") != null) {
            this.getCommand("arena").setExecutor(new ArenaCommandExecutor(this));
        }

        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(new MobProtectionListener(this), this);
        pm.registerEvents(new BlockProtectionListener(this), this);
        this.playerArenaListener = new PlayerArenaListener(this);
        pm.registerEvents(this.playerArenaListener, this);
        pm.registerEvents(new RelicListener(this), this);
        pm.registerEvents(this, this);
        pm.registerEvents(new ClassMenuListener(this), this);
        this.classAbilityListener = new ClassAbilityListener(this);
        pm.registerEvents(this.classAbilityListener, this);
        this.classPassiveManager = new ClassPassiveManager(this);
        pm.registerEvents(this.classPassiveManager, this);
        this.relicCarrierMovementTask = Bukkit.getScheduler()
            .runTaskTimer(this, this::enforceRelicCarrierMovement, 1L, 1L);
        this.loadArenas();
        this.getLogger().info("ArenaSystem 0.2.8.6 aktif. Arena siap: " + this.arenas.size());
        if (this.getConfig().getBoolean("updates.enabled", true)) {
            this.updateNotifier = new PluginUpdateNotifier(this);
            this.updateNotifier.start();
        }
    }

    private void migrateLegacyCooldownDefault(String path, int previousDefault, int newDefault) {
        if (this.getConfig().getInt(path, newDefault) == previousDefault) {
            this.getConfig().set(path, newDefault);
        }
    }

    private File configFile() {
        return new File(this.getDataFolder(), "config.yml");
    }

    private void persistConfigOrThrow() throws IOException {
        AtomicFileWriter.write(this.configFile(), this.getConfig().saveToString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void saveConfig() {
        try {
            this.persistConfigOrThrow();
        } catch (IOException ex) {
            this.getLogger().log(Level.SEVERE, "Konfigurasi tidak dapat disimpan ke " + this.configFile(), ex);
        }
    }

    private void commitConfig(Collection<String> roots, Runnable mutation) {
        ConfigTransaction.commit(this.configAccess, this::persistConfigOrThrow, roots, mutation);
    }

    private void removeArenaRecord(String arenaName) {
        String root = "arenas." + this.arenaTaskKey(arenaName);
        this.commitConfig(List.of(root), () -> this.getConfig().set(root, null));
    }

    private String arenaTaskKey(String arenaName) {
        return arenaName.toLowerCase(Locale.ROOT);
    }

    private void trackGenerationTask(String arenaName, BukkitTask task) {
        if (task != null) {
            this.activeGenerationTasks
                .computeIfAbsent(this.arenaTaskKey(arenaName), ignored -> new HashSet<>())
                .add(task);
        }
    }

    private void untrackGenerationTask(String arenaName, BukkitTask task) {
        if (task != null) {
            String key = this.arenaTaskKey(arenaName);
            Set<BukkitTask> tasks = this.activeGenerationTasks.get(key);
            if (tasks != null) {
                tasks.remove(task);
                if (tasks.isEmpty()) {
                    this.activeGenerationTasks.remove(key);
                }
            }
        }
    }

    private boolean hasActiveGenerationTasks(String arenaName) {
        Set<BukkitTask> tasks = this.activeGenerationTasks.get(this.arenaTaskKey(arenaName));
        return tasks != null && !tasks.isEmpty();
    }

    private void cancelAllGenerationTasks() {
        for (Set<BukkitTask> tasks : this.activeGenerationTasks.values()) {
            for (BukkitTask task : tasks) {
                task.cancel();
            }
        }

        this.activeGenerationTasks.clear();
        this.buildingArenas.clear();
    }

    boolean isIncompleteArena(ArenaData arena) {
        return arena != null && this.incompleteArenas.containsKey(arena.name);
    }

    @Override
    public void onDisable() {
        if (this.updateNotifier != null) {
            this.updateNotifier.stop();
            this.updateNotifier = null;
        }

        this.cancelAllGenerationTasks();
        if (this.playerArenaListener != null) {
            this.playerArenaListener.cleanupAll();
        }

        if (this.relicCarrierMovementTask != null) {
            this.relicCarrierMovementTask.cancel();
            this.relicCarrierMovementTask = null;
        }

        if (this.classAbilityListener != null) {
            this.classAbilityListener.cleanupAll();
        }

        if (this.classPassiveManager != null) {
            this.classPassiveManager.cleanupAll();
        }

        for (MatchState match : new ArrayList<>(this.matches.values())) {
            this.cancelMatchTasks(match);
            match.ending = true;
            match.running = false;
            match.preparing = false;
            match.launching = false;
            this.clearMatchHud(match);
            this.restorePlayerSnapshots(match);
            ArenaData arena = this.arenas.get(match.arenaName);
            if (arena == null) {
                arena = this.incompleteArenas.get(match.arenaName);
            }

            if (arena != null) {
                Map<BlockPos, String> changes = this.combinedMatchChanges(match);
                this.restoreMapImmediately(arena, changes);
                this.removeRelicItems(arena, match);
            }
        }

        this.saveConfig();
    }

    private void loadQuarantinedAreas() {
        this.quarantinedAreas.clear();
        ConfigurationSection quarantine = this.getConfig().getConfigurationSection("quarantined-arenas");
        if (quarantine != null) {
            for (String key : quarantine.getKeys(false)) {
                String base = "quarantined-arenas." + key + ".";
                String world = this.getConfig().getString(base + "world");
                if (world != null) {
                    String name = key.toLowerCase(Locale.ROOT);
                    this.quarantinedAreas
                        .put(
                            name,
                            new ArenaSystemPlugin.QuarantinedArea(
                                name,
                                world,
                                this.getConfig().getInt(base + "center-x"),
                                this.getConfig().getInt(base + "center-z"),
                                this.getConfig().getInt(base + "radius", 56)
                            )
                        );
                }
            }

            if (!this.quarantinedAreas.isEmpty()) {
                this.getLogger()
                    .warning(
                        this.quarantinedAreas.size()
                            + " area karantina aktif; pembuatan arena baru di area itu diblokir. Gunakan /arena quarantine list."
                    );
            }
        }
    }

    void loadArenas() {
        this.loadQuarantinedAreas();
        ConfigurationSection section = this.getConfig().getConfigurationSection("arenas");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String base = "arenas." + key + ".";
                String worldName = this.getConfig().getString(base + "world");
                World world = worldName == null ? null : Bukkit.getWorld(worldName);
                if (world == null) {
                    this.getLogger().warning("World untuk arena '" + key + "' tidak termuat: " + worldName);
                } else {
                    int legacyY = this.getConfig().getInt(base + "floor-y", 64);
                    int centerY = this.getConfig().getInt(base + "center-y", legacyY);
                    String mode = this.getConfig().getString(base + "mode", "tdm");
                    int spawnOffset = this.getConfig().getInt(base + "spawn-offset", 27);
                    int radius = this.getConfig().getInt(base + "radius", 40);
                    ArenaData data = new ArenaData(
                        key.toLowerCase(Locale.ROOT),
                        worldName,
                        this.getConfig().getLong(base + "seed", 0L),
                        this.getConfig().getInt(base + "center-x"),
                        this.getConfig().getInt(base + "center-z"),
                        centerY,
                        this.getConfig().getInt(base + "red-y", legacyY),
                        this.getConfig().getInt(base + "blue-y", legacyY),
                        this.getConfig().getInt(base + "objective-y", centerY + 4),
                        this.getConfig().getInt(base + "min-surface-y", legacyY),
                        this.getConfig().getInt(base + "max-surface-y", legacyY),
                        mode,
                        spawnOffset,
                        radius
                    );
                    boolean complete = this.getConfig().getBoolean(base + "complete", false);
                    GenerationStatus status = this.readGenerationStatus(base, complete);
                    if (status != GenerationStatus.COMPLETE) {
                        complete = false;
                    }

                    if (complete) {
                        this.arenas.put(data.name, data);
                        if (!this.getConfig().getBoolean(base + "combat-vegetation-cleaned-v4", false)
                            && this.clearCombatVegetationSafely(data)) {
                            this.getConfig().set(base + "combat-vegetation-cleaned-v4", true);
                            this.saveConfig();
                        }

                        this.clearMobsInArena(data);
                    } else {
                        this.incompleteArenas.put(data.name, data);
                        this.getConfig().set(base + "complete", false);
                        this.getConfig().set(base + "generation-state", status.name());
                        this.getLogger()
                            .warning(
                                "Arena '"
                                    + key
                                    + "' berstatus "
                                    + status
                                    + ". Periksa snapshot dan gunakan /arena recover "
                                    + key
                                    + "."
                            );
                    }
                }
            }
        }
    }

    private GenerationStatus readGenerationStatus(String base, boolean completeFlag) {
        String raw = this.getConfig().getString(base + "generation-state");
        if (raw != null && !raw.isBlank()) {
            try {
                GenerationStatus parsed = GenerationStatus.valueOf(raw.toUpperCase(Locale.ROOT));
                return parsed == GenerationStatus.COMPLETE && !completeFlag
                    ? GenerationStatus.RECOVERY_REQUIRED
                    : parsed;
            } catch (IllegalArgumentException ex) {
                this.getLogger()
                    .warning(
                        "Status generation tidak dikenal pada "
                            + base
                            + "; arena akan diperlakukan sebagai RECOVERY_REQUIRED."
                    );
                return GenerationStatus.RECOVERY_REQUIRED;
            }
        } else {
            return completeFlag ? GenerationStatus.COMPLETE : GenerationStatus.RECOVERY_REQUIRED;
        }
    }

    private void markRecoveryRequired(String arenaName) {
        ArenaData data = this.incompleteArenas.get(this.arenaTaskKey(arenaName));
        if (data != null) {
            try {
                this.saveArenaData(data, GenerationStatus.RECOVERY_REQUIRED);
            } catch (RuntimeException ex) {
                this.getLogger()
                    .severe("Tidak dapat menyimpan RECOVERY_REQUIRED untuk arena " + arenaName + ": " + ex.getMessage());
            }
        }
    }

    int activeTeamCount(String arenaName, String mode) {
        return Math.max(2, Math.min(4, this.getConfig().getInt("arenas." + arenaName + ".team-count", 2)));
    }

    int teamCountFor(ArenaData arena) {
        return arena == null
            ? 2
            : Math.max(2, Math.min(4, this.getConfig().getInt("arenas." + arena.name + ".team-count", 2)));
    }

    List<String> teamNamesFor(ArenaData arena) {
        return RelicTeamLayout.teamNames(this.teamCountFor(arena));
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
        ArenaData arena = this.arenas.get(arenaName.toLowerCase(Locale.ROOT));
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
        } else if (this.matches.containsKey(arena.name)) {
            sender.sendMessage(ChatColor.RED + "Hentikan/reset antrean atau pertandingan sebelum mengubah jumlah tim.");
        } else {
            int count;
            try {
                count = Integer.parseInt(rawCount);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Jumlah tim harus angka 2, 3, atau 4.");
                return;
            }

            if (count >= 2 && count <= 4) {
                this.getConfig().set("arenas." + arena.name + ".team-count", count);
                this.saveConfig();
                sender.sendMessage(
                    ChatColor.GREEN
                        + "Arena "
                        + arena.name
                        + " sekarang memakai "
                        + count
                        + " tim: "
                        + String.join(", ", this.teamNamesFor(arena)).toUpperCase(Locale.ROOT)
                        + "."
                );
                sender.sendMessage(
                    ChatColor.GRAY
                        + "Jalankan /arena regenerate "
                        + arena.name
                        + " untuk menyesuaikan markas arena dengan jumlah tim baru."
                );
            } else {
                sender.sendMessage(ChatColor.RED + "Jumlah tim yang didukung adalah 2-4.");
            }
        }
    }

    boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("arenasystem.admin")) {
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Kamu tidak punya izin. Permission: arenasystem.admin");
        return false;
    }

    void createArena(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Jalankan perintah ini dari dalam game, di titik tengah arena.");
        } else if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena create <nama> [seed] [tdm|relic_rush]");
            sender.sendMessage(
                ChatColor.GRAY + "Contoh: /arena create relic relic_rush atau /arena create relic 12345 relic_rush"
            );
        } else {
            String name = args[1].toLowerCase(Locale.ROOT);
            if (!name.matches("[a-z0-9_-]{1,20}")) {
                sender.sendMessage(
                    ChatColor.RED + "Nama arena hanya boleh huruf kecil, angka, _ dan - (maks. 20 karakter)."
                );
            } else if (!this.arenas.containsKey(name)
                && !this.incompleteArenas.containsKey(name)
                && !this.buildingArenas.contains(name)
                && !this.quarantinedAreas.containsKey(name)
                && !this.getConfig().contains("arenas." + name)) {
                Player player = (Player)sender;
                World world = player.getWorld();
                int centerX = player.getLocation().getBlockX();
                int centerZ = player.getLocation().getBlockZ();
                long seed = System.currentTimeMillis();
                String mode = "tdm";
                if (args.length >= 3) {
                    if (this.isSupportedMode(args[2])) {
                        mode = args[2].toLowerCase(Locale.ROOT);
                        if (args.length >= 4) {
                            player.sendMessage(
                                ChatColor.RED
                                    + "Jika mode ditulis langsung setelah nama, seed tidak dapat ditambahkan setelahnya."
                            );
                            return;
                        }
                    } else {
                        try {
                            seed = Long.parseLong(args[2]);
                        } catch (NumberFormatException ex) {
                            player.sendMessage(
                                ChatColor.RED + "Argumen ketiga harus berupa seed angka atau mode tdm/relic_rush."
                            );
                            return;
                        }

                        if (args.length >= 4) {
                            if (!this.isSupportedMode(args[3])) {
                                player.sendMessage(ChatColor.RED + "Mode tidak valid. Pilih tdm atau relic_rush.");
                                return;
                            }

                            mode = args[3].toLowerCase(Locale.ROOT);
                        }
                    }
                }

                int radius = radiusForMode(mode);
                if (this.overlapsExistingArena(world, centerX, centerZ, radius)) {
                    player.sendMessage(
                        ChatColor.RED
                            + "Lokasi bertumpang tindih dengan arena terdaftar atau area karantina (lihat /arena quarantine list)."
                    );
                } else {
                    int span = radius * 2 + 1;
                    player.sendMessage(
                        ChatColor.GREEN
                            + "Memindai kontur medan "
                            + span
                            + "x"
                            + span
                            + " untuk mode "
                            + mode.toUpperCase(Locale.ROOT)
                            + ". Bentuk arena akan dibuat khusus untuk mode ini..."
                    );
                    player.sendMessage(
                        ChatColor.GRAY
                            + "Seed layout: "
                            + seed
                            + " | simpan angka ini untuk mereproduksi variasi struktur pada lokasi yang sama."
                    );
                    this.scanAndBuild(player, name, world, centerX, centerZ, seed, mode);
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Nama arena sudah terdaftar atau masih memiliki data pemulihan.");
            }
        }
    }

    void scanAndBuild(CommandSender sender, String name, World world, int centerX, int centerZ, long seed, String mode) {
        if (!this.buildingArenas.add(this.arenaTaskKey(name))) {
            sender.sendMessage(ChatColor.RED + "Arena ini sedang diproses. Tunggu hingga operasi sebelumnya selesai.");
        } else {
            this.scanAndBuildLocked(sender, name, world, centerX, centerZ, seed, mode);
        }
    }

    private void scanAndBuildLocked(
        final CommandSender sender,
        final String name,
        final World world,
        final int centerX,
        final int centerZ,
        final long seed,
        final String mode
    ) {
        final int radius = radiusForMode(mode);
        final int size = radius * 2 + 1;
        final int[][] heights = new int[size][size];
        final int[] cursor = new int[]{-radius, -radius};
        final int[] limits = new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE};
        final int[] structureColumns = new int[]{0};
        final BukkitTask[] scanTaskReference = new BukkitTask[1];
        BukkitRunnable scanTask = new BukkitRunnable()        // $VF: Compiled from ArenaSystemPlugin.java
 {
            private void finishScan(boolean releaseLock) {
                this.cancel();
                ArenaSystemPlugin.this.untrackGenerationTask(name, scanTaskReference[0]);
                if (releaseLock) {
                    ArenaSystemPlugin.this.buildingArenas.remove(ArenaSystemPlugin.this.arenaTaskKey(name));
                }
            }

            @Override
            public void run() {
                try {
                    int processed = 0;

                    while (cursor[0] <= radius && processed < 256) {
                        int y = TerrainScanner.findSurface(world, centerX + cursor[0], centerZ + cursor[1]);
                        if (y == Integer.MIN_VALUE) {
                            this.finishScan(true);
                            ArenaSystemPlugin.this.abortBeforeBuild(
                                sender, name, "Tidak menemukan permukaan tanah yang cocok pada area ini."
                            );
                            return;
                        }

                        heights[cursor[0] + radius][cursor[1] + radius] = y;
                        limits[0] = Math.min(limits[0], y);
                        limits[1] = Math.max(limits[1], y);
                        if (TerrainScanner.hasGenericStructureAbove(world, centerX + cursor[0], y, centerZ + cursor[1])
                            )
                         {
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
                        this.finishScan(false);
                        TerrainProfile originalProfile = new TerrainProfile(radius, heights, limits[0], limits[1]);
                        int totalColumns = size * size;
                        double structureCoverage = totalColumns == 0 ? 0.0 : (double)structureColumns[0] / totalColumns;
                        double maxStructureCoverage = Math.max(
                            0.0,
                            Math.min(
                                1.0,
                                ArenaSystemPlugin.this.getConfig().getDouble("terrain.max-structure-coverage", 0.25)
                            )
                        );
                        if (structureCoverage > maxStructureCoverage) {
                            sender.sendMessage(
                                ChatColor.RED
                                    + "Area ini terlalu padat oleh rumah/bangunan/struktur dari medan asli: "
                                    + Math.round(structureCoverage * 100.0)
                                    + "% kolom (batas "
                                    + Math.round(maxStructureCoverage * 100.0)
                                    + "%)."
                            );
                            sender.sendMessage(
                                ChatColor.GRAY
                                    + "Atur terrain.max-structure-coverage di config.yml atau pilih area yang tidak terlalu padat bangunan."
                            );
                            ArenaSystemPlugin.this.abortBeforeBuild(sender, name, null);
                            return;
                        }

                        int maxAdjacentStep = 1;
                        int spawnOffset = mode.equals("relic_rush") ? radius - 8 : 27;
                        int activeTeams = ArenaSystemPlugin.this.activeTeamCount(name, mode);
                        int maxSeedDelta = Math.max(
                            0, Math.min(8, ArenaSystemPlugin.this.getConfig().getInt("terrain.max-seed-delta", 6))
                        );

                        TerrainNormalizer.Result normalization;
                        try {
                            boolean relicRush = "relic_rush".equalsIgnoreCase(mode);
                            TerrainProfile blended = SeededTerrainBlender.blend(
                                originalProfile,
                                seed,
                                spawnOffset,
                                activeTeams,
                                relicRush,
                                maxSeedDelta,
                                world.getMinHeight(),
                                world.getMaxHeight()
                            );
                            normalization = TerrainNormalizer.normalize(
                                blended,
                                maxAdjacentStep,
                                SeededTerrainBlender.anchors(spawnOffset, activeTeams, relicRush)
                            );
                        } catch (IllegalStateException | IllegalArgumentException ex) {
                            ArenaSystemPlugin.this.abortBeforeBuild(
                                sender, name, "Terrain tidak dapat dinormalkan dengan aman: " + ex.getMessage()
                            );
                            return;
                        }

                        TerrainProfile profile = normalization.profile();
                        int maxColumnAdjustment = Math.max(
                            1, ArenaSystemPlugin.this.getConfig().getInt("terrain.max-column-adjustment", 12)
                        );
                        int maxScannedAdjustment = 0;

                        for (int ax = -radius; ax <= radius; ax++) {
                            for (int az = -radius; az <= radius; az++) {
                                maxScannedAdjustment = Math.max(
                                    maxScannedAdjustment,
                                    Math.abs(profile.heightAt(ax, az) - originalProfile.heightAt(ax, az))
                                );
                            }
                        }

                        sender.sendMessage(
                            ChatColor.AQUA
                                + "[TerrainNormalizer] "
                                + normalization.changedColumns()
                                + " kolom disesuaikan; perubahan maksimum "
                                + normalization.maxAdjustment()
                                + " blok, rata-rata "
                                + String.format(Locale.ROOT, "%.2f", normalization.averageAdjustment())
                                + " blok; beda tinggi antarkolom dibatasi "
                                + normalization.maxAdjacentStep()
                                + " blok; kontur seed (maks "
                                + maxSeedDelta
                                + " blok) dicampur; elevasi dan pad spawn seluruh tim aktif diseimbangkan."
                        );
                        if (maxScannedAdjustment > maxColumnAdjustment) {
                            sender.sendMessage(
                                ChatColor.RED
                                    + "Terrain terlalu ekstrem untuk dinormalkan dengan aman: perubahan per kolom perlu "
                                    + maxScannedAdjustment
                                    + " blok (batas "
                                    + maxColumnAdjustment
                                    + ")."
                            );
                            sender.sendMessage(
                                ChatColor.GRAY
                                    + "Atur terrain.max-column-adjustment di config.yml atau pilih area lain."
                            );
                            ArenaSystemPlugin.this.abortBeforeBuild(sender, name, null);
                            return;
                        }

                        TerrainDetector.Report terrainReport = TerrainDetector.analyze(
                            profile, radius, spawnOffset, activeTeams
                        );
                        sender.sendMessage(
                            ChatColor.GOLD + "[TerrainDetector] Setelah normalisasi: " + terrainReport.summary()
                        );

                        for (String warning : terrainReport.warnings()) {
                            sender.sendMessage(ChatColor.YELLOW + "[TerrainDetector] " + warning);
                        }

                        if (!terrainReport.accepted()) {
                            sender.sendMessage(
                                ChatColor.RED
                                    + "Terrain belum memenuhi batas kelayakan arena: "
                                    + terrainReport.rejectionReason()
                            );
                            sender.sendMessage(
                                ChatColor.GRAY + "Pilih lokasi lain atau tinjau batas normalisasi dengan hati-hati."
                            );
                            ArenaSystemPlugin.this.abortBeforeBuild(sender, name, null);
                            return;
                        }

                        int redY = profile.heightAt(-spawnOffset, 0);
                        int blueY = profile.heightAt(spawnOffset, 0);
                        if (!"relic_rush".equalsIgnoreCase(mode)
                            && (!ArenaSystemPlugin.this.hasBuildClearanceAfterNormalization(
                                    world, centerX - spawnOffset, redY, centerZ, originalProfile.heightAt(-spawnOffset, 0)
                                )
                                || !ArenaSystemPlugin.this.hasBuildClearanceAfterNormalization(
                                    world, centerX + spawnOffset, blueY, centerZ, originalProfile.heightAt(spawnOffset, 0)
                                )
                                || !ArenaSystemPlugin.this.hasObjectiveClearanceAfterNormalization(
                                    world, centerX, profile.heightAt(0, 0), centerZ, originalProfile.heightAt(0, 0)
                                ))) {
                            ArenaSystemPlugin.this.abortBeforeBuild(
                                sender,
                                name,
                                "Ruang spawn/objektif terhalang block entity atau struktur yang tidak aman dipindahkan."
                            );
                            return;
                        }

                        ArenaData data = ArenaSystemPlugin.this.makeArenaData(
                            name, world.getName(), seed, centerX, centerZ, profile, mode, radius
                        );
                        ArenaSystemPlugin.this.beginGeneration(sender, data, world, originalProfile, profile);
                    }
                } catch (RuntimeException ex) {
                    this.finishScan(true);
                    ArenaSystemPlugin.this.getLogger()
                        .severe("Pemindaian arena " + name + " gagal: " + ex.getMessage());
                    ArenaSystemPlugin.this.abortBeforeBuild(
                        sender,
                        name,
                        "Pemindaian arena gagal. Tidak ada perubahan terrain yang diterapkan: " + ex.getMessage()
                    );
                }
            }
        };
        scanTaskReference[0] = scanTask.runTaskTimer(this, 1L, 1L);
        this.trackGenerationTask(name, scanTaskReference[0]);
    }

    private void abortBeforeBuild(CommandSender sender, String name, String reason) {
        String key = this.arenaTaskKey(name);
        if (this.hasActiveGenerationTasks(key)) {
            this.getLogger().severe("abortBeforeBuild(" + key + ") diabaikan: masih ada task generasi aktif.");
        } else {
            this.buildingArenas.remove(key);
            if (reason != null) {
                sender.sendMessage(ChatColor.RED + reason);
            }

            if (this.incompleteArenas.containsKey(key)) {
                try {
                    this.removeArenaRecord(key);
                } catch (RuntimeException ex) {
                    this.markRecoveryRequired(key);
                    sender.sendMessage(
                        ChatColor.RED
                            + "World sudah dikembalikan ke kondisi awal, tetapi status arena tidak dapat disimpan ("
                            + ex.getMessage()
                            + "). Arena tetap RECOVERY_REQUIRED dan snapshot dipertahankan; jalankan /arena recover "
                            + key
                            + " setelah masalah penyimpanan teratasi."
                    );
                    return;
                }

                this.incompleteArenas.remove(key);
                this.arenas.remove(key);

                try {
                    this.snapshots.delete(key);
                } catch (IOException ex) {
                    this.getLogger()
                        .warning(
                            "Snapshot arena "
                                + key
                                + " gagal dibersihkan setelah regenerasi dibatalkan: "
                                + ex.getMessage()
                        );
                }

                sender.sendMessage(
                    ChatColor.YELLOW
                        + "Regenerasi dibatalkan. World arena sudah dikembalikan ke kondisi awal dan arena lama "
                        + key
                        + " TIDAK lagi tersedia (record dihapus). Buat ulang dengan /arena create "
                        + key
                        + "."
                );
            }
        }
    }

    boolean clearCombatVegetationSafely(ArenaData arena) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) {
            return false;
        }

        if (!this.snapshots.exists(arena.name)) {
            this.getLogger()
                .warning(
                    "Tidak dapat membersihkan vegetasi arena '"
                        + arena.name
                        + "': snapshot asli tidak tersedia. Regenerate manual diperlukan."
                );
            return false;
        }

        Map<BlockPos, String> snapshot;
        try {
            snapshot = this.snapshots.load(arena);
        } catch (IOException ex) {
            this.getLogger()
                .warning("Gagal membaca snapshot arena '" + arena.name + "'; vegetasi tidak diubah: " + ex.getMessage());
            return false;
        }

        Map<BlockPos, String> toClear = new LinkedHashMap<>();
        int scanMinY = Math.max(world.getMinHeight(), Math.min(arena.minSurfaceY, arena.maxSurfaceY) + 1);
        int scanMaxY = Math.min(world.getMaxHeight() - 1, Math.max(arena.minSurfaceY, arena.maxSurfaceY) + 12);

        for (int x = -arena.radius; x <= arena.radius; x++) {
            for (int z = -arena.radius; z <= arena.radius; z++) {
                for (int y = scanMinY; y <= scanMaxY; y++) {
                    Block block = world.getBlockAt(arena.centerX + x, y, arena.centerZ + z);
                    if (ArenaGenerator.isCombatVegetation(block.getType()) && !(block.getState() instanceof TileState)) {
                        BlockPos relative = new BlockPos(x, y - arena.centerY, z);
                        String originalData = block.getBlockData().getAsString();
                        snapshot.putIfAbsent(relative, originalData);
                        toClear.put(relative, originalData);
                    }
                }
            }
        }

        if (toClear.isEmpty()) {
            return true;
        }

        try {
            this.snapshots.save(arena, snapshot);
        } catch (IOException ex) {
            this.getLogger()
                .severe(
                    "Gagal memperbarui snapshot vegetasi arena '"
                        + arena.name
                        + "'; tidak ada rumput yang dihapus: "
                        + ex.getMessage()
                );
            return false;
        }

        for (BlockPos pos : toClear.keySet()) {
            world.getBlockAt(arena.centerX + pos.x, arena.centerY + pos.y, arena.centerZ + pos.z)
                .setType(Material.AIR, false);
        }

        this.getLogger()
            .info(
                "Menghapus "
                    + toClear.size()
                    + " blok vegetasi pengganggu dari arena '"
                    + arena.name
                    + "'. Snapshot pemulihan diperbarui."
            );
        return true;
    }

    void clearArenaVegetation(CommandSender sender, String arenaName) {
        ArenaData arena = this.arenas.get(arenaName.toLowerCase(Locale.ROOT));
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan atau belum selesai dibuat.");
        } else if (this.matches.containsKey(arena.name)) {
            sender.sendMessage(ChatColor.RED + "Hentikan atau reset pertandingan sebelum membersihkan vegetasi.");
        } else if (!this.clearCombatVegetationSafely(arena)) {
            sender.sendMessage(
                ChatColor.RED + "Vegetasi tidak dibersihkan. Pastikan snapshot arena tersedia dan periksa log."
            );
        } else {
            this.getConfig().set("arenas." + arena.name + ".combat-vegetation-cleaned-v4", true);
            this.saveConfig();
            sender.sendMessage(
                ChatColor.GREEN
                    + "Rumput dan tanaman pengganggu di arena "
                    + arena.name
                    + " telah dibersihkan. Snapshot pemulihan tetap diperbarui."
            );
        }
    }

    void clearMobsInArena(ArenaData arena) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world != null) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!(entity instanceof Player) && !(entity instanceof ArmorStand)) {
                    Location loc = entity.getLocation();
                    if (loc.getWorld().getName().equals(arena.worldName)) {
                        int dx = Math.abs(loc.getBlockX() - arena.centerX);
                        int dz = Math.abs(loc.getBlockZ() - arena.centerZ);
                        if (dx <= arena.radius && dz <= arena.radius) {
                            entity.remove();
                        }
                    }
                }
            }
        }
    }

    boolean hasBuildClearanceAfterNormalization(World world, int x, int targetY, int z, int originalY) {
        for (int offset = 1; offset <= 3; offset++) {
            int y = targetY + offset;
            if (y > originalY) {
                if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                    return false;
                }

                Block block = world.getBlockAt(x, y, z);
                if (block.getState() instanceof TileState || !block.isPassable()) {
                    return false;
                }
            }
        }

        return true;
    }

    boolean hasObjectiveClearanceAfterNormalization(World world, int x, int targetY, int z, int originalY) {
        for (int offset = 1; offset <= 5; offset++) {
            int y = targetY + offset;
            if (y > originalY) {
                if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                    return false;
                }

                Block block = world.getBlockAt(x, y, z);
                if (block.getState() instanceof TileState || !block.isPassable()) {
                    return false;
                }
            }
        }

        return true;
    }

    ArenaData makeArenaData(
        String name,
        String worldName,
        long seed,
        int centerX,
        int centerZ,
        TerrainProfile terrain,
        String mode,
        int radius
    ) {
        int centerY = terrain.heightAt(0, 0);
        int spawnOffset = "relic_rush".equalsIgnoreCase(mode) ? radius - 8 : 27;
        int redY = terrain.heightAt(-spawnOffset, 0);
        int blueY = terrain.heightAt(spawnOffset, 0);
        return new ArenaData(
            name,
            worldName,
            seed,
            centerX,
            centerZ,
            centerY,
            redY,
            blueY,
            centerY + 5,
            terrain.minY,
            terrain.maxY,
            mode,
            spawnOffset,
            radius
        );
    }

    void beginGeneration(
        final CommandSender sender,
        final ArenaData data,
        final World world,
        TerrainProfile originalProfile,
        TerrainProfile profile
    ) {
        final List<BlockEdit> edits;
        try {
            int configuredTeams = this.activeTeamCount(data.name, data.mode);
            edits = ArenaGenerator.createPlan(
                world, data, originalProfile, profile, data.seed, data.radius, configuredTeams
            );
        } catch (IllegalStateException ex) {
            this.getLogger()
                .warning("Terrain normalization dibatalkan untuk arena " + data.name + ": " + ex.getMessage());
            this.abortBeforeBuild(sender, data.name, "Pembuatan arena dibatalkan demi keamanan: " + ex.getMessage());
            return;
        }

        Map<BlockPos, String> original;
        try {
            original = this.captureOriginalBlocks(world, data, edits);
        } catch (IllegalStateException ex) {
            this.getLogger().warning("Snapshot arena " + data.name + " ditolak: " + ex.getMessage());
            this.abortBeforeBuild(sender, data.name, "Pembuatan arena dibatalkan: " + ex.getMessage());
            return;
        }

        if (original.isEmpty()) {
            this.abortBeforeBuild(
                sender, data.name, "Tidak ada blok aman untuk dibangun. Tidak ada perubahan yang dilakukan."
            );
        } else {
            try {
                this.snapshots.save(data, original);
            } catch (IOException ex) {
                this.getLogger().severe("Tidak dapat menyimpan snapshot arena " + data.name + ": " + ex.getMessage());
                this.abortBeforeBuild(
                    sender, data.name, "Gagal menyimpan snapshot. Arena tidak dibuat demi keamanan world."
                );
                return;
            }

            ArenaData previousRecord = this.incompleteArenas.put(data.name, data);

            try {
                this.saveArenaData(data, GenerationStatus.BUILDING);
            } catch (RuntimeException ex) {
                if (previousRecord == null) {
                    this.incompleteArenas.remove(data.name);

                    try {
                        this.snapshots.delete(data.name);
                    } catch (IOException cleanup) {
                        this.getLogger()
                            .warning(
                                "Snapshot yatim arena " + data.name + " gagal dibersihkan: " + cleanup.getMessage()
                            );
                    }
                } else {
                    this.incompleteArenas.put(data.name, previousRecord);
                }

                this.getLogger()
                    .severe("Status BUILDING arena " + data.name + " tidak dapat disimpan: " + ex.getMessage());
                this.abortBeforeBuild(
                    sender, data.name, "Status BUILDING tidak dapat disimpan; world tidak diubah: " + ex.getMessage()
                );
                return;
            }

            sender.sendMessage(
                ChatColor.GREEN
                    + "Pemindaian selesai. Membangun "
                    + edits.size()
                    + " perubahan blok secara bertahap..."
            );
            final BukkitTask[] buildTaskReference = new BukkitTask[1];
            BukkitRunnable buildTask = new BukkitRunnable()            // $VF: Compiled from ArenaSystemPlugin.java
 {
                private int index;

                private void finishBuildTask() {
                    ArenaSystemPlugin.this.untrackGenerationTask(data.name, buildTaskReference[0]);
                }

                @Override
                public void run() {
                    try {
                        for (int end = Math.min(this.index + 300, edits.size()); this.index < end; this.index++) {
                            BlockEdit edit = edits.get(this.index);
                            world.getBlockAt(
                                    data.centerX + edit.position.x,
                                    data.centerY + edit.position.y,
                                    data.centerZ + edit.position.z
                                )
                                .setBlockData(
                                    edit.blockData != null
                                        ? edit.blockData.clone()
                                        : Bukkit.createBlockData(edit.material),
                                    false
                                );
                        }

                        if (this.index >= edits.size()) {
                            this.cancel();
                            boolean vegetationCleanupComplete = ArenaSystemPlugin.this.clearCombatVegetationSafely(data);
                            ArenaSystemPlugin.this.getConfig()
                                .set("arenas." + data.name + ".combat-vegetation-cleaned-v4", vegetationCleanupComplete);
                            if (!vegetationCleanupComplete) {
                                ArenaSystemPlugin.this.getLogger()
                                    .warning(
                                        "Vegetation cleanup for arena '"
                                            + data.name
                                            + "' did not complete. It can be retried with /arena clearvegetation "
                                            + data.name
                                            + "."
                                    );
                            }

                            ArenaSystemPlugin.this.saveArenaData(data, GenerationStatus.COMPLETE);
                            this.finishBuildTask();
                            ArenaSystemPlugin.this.incompleteArenas.remove(data.name);
                            ArenaSystemPlugin.this.arenas.put(data.name, data);
                            ArenaSystemPlugin.this.buildingArenas
                                .remove(ArenaSystemPlugin.this.arenaTaskKey(data.name));
                            ArenaSystemPlugin.this.clearMobsInArena(data);
                            sender.sendMessage(
                                ChatColor.GREEN + "Arena " + data.name + " berhasil dibuat dan dibersihkan dari mob!"
                            );
                            sender.sendMessage(
                                ChatColor.GRAY
                                    + "Spawn: /arena tp "
                                    + data.name
                                    + " red|blue|green|yellow | Desain: "
                                    + data.mode.toUpperCase(Locale.ROOT)
                            );
                            sender.sendMessage(
                                ChatColor.GRAY
                                    + (
                                        data.mode.equals("relic_rush")
                                            ? "Tujuan: rebut Relic di tengah dan antar ke sanctum markas tim."
                                            : "Tujuan: kalahkan lawan untuk mencapai target kill."
                                    )
                            );
                            ArenaSystemPlugin.this.getLogger()
                                .info(
                                    "Arena '"
                                        + data.name
                                        + "' dibuat di "
                                        + data.worldName
                                        + " dan disterilkan dari mob."
                                );
                        }
                    } catch (RuntimeException ex) {
                        this.cancel();
                        this.finishBuildTask();
                        ArenaSystemPlugin.this.buildingArenas.remove(ArenaSystemPlugin.this.arenaTaskKey(data.name));
                        ArenaSystemPlugin.this.markRecoveryRequired(data.name);
                        ArenaSystemPlugin.this.getLogger()
                            .severe("Pembuatan arena " + data.name + " terhenti: " + ex.getMessage());
                        sender.sendMessage(
                            ChatColor.RED
                                + "Pembuatan terhenti. Snapshot masih disimpan; gunakan /arena recover "
                                + data.name
                                + " setelah memeriksa log."
                        );
                    }
                }
            };
            buildTaskReference[0] = buildTask.runTaskTimer(this, 1L, 1L);
            this.trackGenerationTask(data.name, buildTaskReference[0]);
        }
    }

    Map<BlockPos, String> captureOriginalBlocks(World world, ArenaData arena, List<BlockEdit> edits) {
        Map<BlockPos, String> original = new LinkedHashMap<>();

        for (BlockEdit edit : edits) {
            Block block = world.getBlockAt(
                arena.centerX + edit.position.x, arena.centerY + edit.position.y, arena.centerZ + edit.position.z
            );
            if (block.getState() instanceof TileState) {
                throw new IllegalStateException(
                    "Rencana generator menyentuh block entity pada "
                        + block.getX()
                        + ","
                        + block.getY()
                        + ","
                        + block.getZ()
                        + ". Blok tersebut tidak dapat dipulihkan hanya dengan BlockData."
                );
            }

            original.put(edit.position, block.getBlockData().getAsString());
        }

        return original;
    }

    boolean overlapsExistingArena(World world, int centerX, int centerZ, int newRadius) {
        for (ArenaData arena : this.arenas.values()) {
            if (arena.worldName.equals(world.getName())
                && Math.abs(arena.centerX - centerX) < arena.radius + newRadius + 4
                && Math.abs(arena.centerZ - centerZ) < arena.radius + newRadius + 4) {
                return true;
            }
        }

        for (ArenaData arena : this.incompleteArenas.values()) {
            if (arena.worldName.equals(world.getName())
                && Math.abs(arena.centerX - centerX) < arena.radius + newRadius + 4
                && Math.abs(arena.centerZ - centerZ) < arena.radius + newRadius + 4) {
                return true;
            }
        }

        for (ArenaSystemPlugin.QuarantinedArea area : this.quarantinedAreas.values()) {
            if (area.worldName().equals(world.getName())
                && Math.abs(area.centerX() - centerX) < area.radius() + newRadius + 4
                && Math.abs(area.centerZ() - centerZ) < area.radius() + newRadius + 4) {
                return true;
            }
        }

        return false;
    }

    void saveArenaData(ArenaData data, GenerationStatus status) {
        if (data != null && status != null) {
            String root = "arenas." + data.name;
            String base = root + ".";
            this.commitConfig(List.of(root), () -> {
                this.getConfig().set(base + "world", data.worldName);
                this.getConfig().set(base + "seed", data.seed);
                this.getConfig().set(base + "center-x", data.centerX);
                this.getConfig().set(base + "center-z", data.centerZ);
                this.getConfig().set(base + "center-y", data.centerY);
                this.getConfig().set(base + "floor-y", data.centerY);
                this.getConfig().set(base + "red-y", data.redY);
                this.getConfig().set(base + "blue-y", data.blueY);
                this.getConfig().set(base + "objective-y", data.objectiveY);
                this.getConfig().set(base + "min-surface-y", data.minSurfaceY);
                this.getConfig().set(base + "max-surface-y", data.maxSurfaceY);
                this.getConfig().set(base + "mode", data.mode);
                this.getConfig().set(base + "spawn-offset", data.spawnOffset);
                this.getConfig().set(base + "radius", data.radius);
                this.getConfig().set(base + "complete", status == GenerationStatus.COMPLETE);
                this.getConfig().set(base + "generation-state", status.name());
            });
        } else {
            throw new IllegalArgumentException("arena and status are required");
        }
    }

    void listArenas(CommandSender sender) {
        if (this.arenas.isEmpty() && this.incompleteArenas.isEmpty() && this.quarantinedAreas.isEmpty()) {
            sender.sendMessage(
                ChatColor.YELLOW + "Belum ada arena. Gunakan /arena create <nama> [seed] [tdm|relic_rush]."
            );
        } else {
            sender.sendMessage(ChatColor.GOLD + "Arena tersimpan:");

            for (ArenaData data : this.arenas.values()) {
                sender.sendMessage(
                    ChatColor.GRAY
                        + "- "
                        + ChatColor.AQUA
                        + data.name
                        + ChatColor.GRAY
                        + " | "
                        + data.worldName
                        + " | mode "
                        + data.mode
                        + " | elevasi "
                        + data.minSurfaceY
                        + "–"
                        + data.maxSurfaceY
                );
            }

            for (String name : this.incompleteArenas.keySet()) {
                sender.sendMessage(ChatColor.RED + "- " + name + " | belum selesai / perlu recovery");
            }

            if (!this.quarantinedAreas.isEmpty()) {
                sender.sendMessage(
                    ChatColor.GOLD
                        + "Area karantina: "
                        + this.quarantinedAreas.size()
                        + " (lihat /arena quarantine list)"
                );
            }
        }
    }

    void showInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena info <nama>");
        } else {
            ArenaData data = this.arenas.get(args[1].toLowerCase(Locale.ROOT));
            if (data == null) {
                sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan atau belum selesai.");
            } else {
                sender.sendMessage(ChatColor.GOLD + "Arena " + data.name);
                sender.sendMessage(ChatColor.GRAY + "World: " + data.worldName + " | Seed: " + data.seed);
                sender.sendMessage(
                    ChatColor.GRAY
                        + "Tim aktif: "
                        + String.join(", ", this.teamNamesFor(data)).toUpperCase(Locale.ROOT)
                );
                sender.sendMessage(
                    ChatColor.GRAY
                        + "Mode: "
                        + data.mode
                        + " | Elevasi: "
                        + data.minSurfaceY
                        + "–"
                        + data.maxSurfaceY
                        + " | Jumlah tim: "
                        + this.teamCountFor(data)
                );
                sender.sendMessage(
                    ChatColor.GRAY
                        + "Desain: "
                        + (
                            data.mode.equals("relic_rush")
                                ? "sanctum 13x13 + tiga jalur lebar + delapan outpost + altar Relic tengah"
                                : "battlefield simetris + jalur rotasi + cover"
                        )
                );
                sender.sendMessage(
                    ChatColor.GRAY
                        + "Tujuan: "
                        + (
                            data.mode.equals("relic_rush")
                                ? "rebut Relic dan antar ke altar markas"
                                : "kalahkan lawan untuk mencapai target kill"
                        )
                );
                sender.sendMessage(
                    ChatColor.GRAY
                        + "Spawn merah: "
                        + (data.centerX - data.spawnOffset)
                        + ", "
                        + (data.redY + 2)
                        + ", "
                        + data.centerZ
                );
                sender.sendMessage(
                    ChatColor.GRAY
                        + "Spawn biru: "
                        + (data.centerX + data.spawnOffset)
                        + ", "
                        + (data.blueY + 2)
                        + ", "
                        + data.centerZ
                );
                sender.sendMessage(
                    ChatColor.GRAY + "Objective: " + data.centerX + ", " + data.objectiveY + ", " + data.centerZ
                );
                sender.sendMessage(
                    ChatColor.GRAY
                        + "Snapshot pemulihan: "
                        + (this.snapshots.exists(data.name) ? "tersedia" : "tidak ada (arena legacy)")
                );
            }
        }
    }

    void teleportToArena(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Perintah teleport hanya dapat digunakan pemain.");
        } else if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena tp <nama> <red|blue|green|yellow|center>");
        } else {
            Player player = (Player)sender;
            ArenaData arena = this.arenas.get(args[1].toLowerCase(Locale.ROOT));
            if (arena == null) {
                player.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
            } else {
                World world = Bukkit.getWorld(arena.worldName);
                if (world == null) {
                    player.sendMessage(ChatColor.RED + "World arena tidak termuat.");
                } else {
                    String point = args[2].toLowerCase(Locale.ROOT);
                    if (point.equals("tengah")) {
                        point = "center";
                    }

                    int spawnOffset = arena.spawnOffset;
                    if (!point.equals("center")
                        && !this.teamNamesFor(arena)
                            .contains(
                                point.equals("merah")
                                    ? "red"
                                    : (
                                        point.equals("biru")
                                            ? "blue"
                                            : (
                                                point.equals("hijau")
                                                    ? "green"
                                                    : (point.equals("kuning") ? "yellow" : point)
                                            )
                                    )
                            )) {
                        player.sendMessage(
                            ChatColor.RED
                                + "Markas yang tersedia: "
                                + String.join(", ", this.teamNamesFor(arena)).toUpperCase(Locale.ROOT)
                                + "."
                        );
                    } else {
                        Location target;
                        if (point.equals("red") || point.equals("merah")) {
                            target = new Location(
                                world,
                                arena.centerX - spawnOffset + 0.5,
                                arena.redY + 2.0,
                                arena.centerZ + 0.5,
                                -90.0F,
                                0.0F
                            );
                        } else if (point.equals("blue") || point.equals("biru")) {
                            target = new Location(
                                world,
                                arena.centerX + spawnOffset + 0.5,
                                arena.blueY + 2.0,
                                arena.centerZ + 0.5,
                                90.0F,
                                0.0F
                            );
                        } else if (point.equals("green") || point.equals("hijau")) {
                            target = new Location(
                                world,
                                arena.centerX + 0.5,
                                world.getHighestBlockYAt(arena.centerX, arena.centerZ - spawnOffset) + 1.0,
                                arena.centerZ - spawnOffset + 0.5,
                                180.0F,
                                0.0F
                            );
                        } else if (!point.equals("yellow") && !point.equals("kuning")) {
                            if (!point.equals("center") && !point.equals("tengah")) {
                                player.sendMessage(ChatColor.RED + "Pilih red, blue, green, yellow, atau center.");
                                return;
                            }

                            target = new Location(
                                world, arena.centerX + 0.5, arena.objectiveY + 1.0, arena.centerZ + 0.5
                            );
                        } else {
                            target = new Location(
                                world,
                                arena.centerX + 0.5,
                                world.getHighestBlockYAt(arena.centerX, arena.centerZ + spawnOffset) + 1.0,
                                arena.centerZ + spawnOffset + 0.5,
                                0.0F,
                                0.0F
                            );
                        }

                        player.teleport(target);
                        player.sendMessage(ChatColor.GREEN + "Teleport ke arena " + arena.name + ".");
                    }
                }
            }
        }
    }

    void deleteArena(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[2].equalsIgnoreCase("confirm")) {
            String name = args[1].toLowerCase(Locale.ROOT);
            ArenaData arena = this.arenas.get(name);
            if (arena == null) {
                sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
            } else if (this.buildingArenas.contains(name) || this.hasActiveGenerationTasks(name)) {
                sender.sendMessage(
                    ChatColor.RED + "Arena sedang diproses atau menunggu pemulihan blok; penghapusan ditolak."
                );
            } else if (!this.snapshots.exists(arena.name)) {
                sender.sendMessage(
                    ChatColor.RED
                        + "Snapshot asli tidak tersedia. Arena legacy 0.1.0 tidak bisa dihapus otomatis dengan aman."
                );
                sender.sendMessage(
                    ChatColor.GRAY + "Pulihkan dari backup world atau buat ulang arena 0.2.0 di lokasi lain."
                );
            } else {
                this.buildingArenas.add(name);

                try {
                    this.saveArenaData(arena, GenerationStatus.RESTORING);
                } catch (RuntimeException ex) {
                    this.buildingArenas.remove(name);
                    sender.sendMessage(
                        ChatColor.RED + "Status penghapusan gagal disimpan; world tidak diubah: " + ex.getMessage()
                    );
                    return;
                }

                this.incompleteArenas.put(name, arena);
                this.arenas.remove(name);
                this.stopAndRestoreMatch(
                    name,
                    () -> this.restoreArenaSnapshot(
                        sender,
                        arena,
                        true,
                        () -> {
                            this.removeArenaRecord(name);
                            this.arenas.remove(name);
                            this.incompleteArenas.remove(name);
                            this.buildingArenas.remove(name);
                            sender.sendMessage(
                                ChatColor.GREEN + "Arena " + name + " dihapus dan blok yang dicatat telah dipulihkan."
                            );
                        },
                        ex -> {
                            this.buildingArenas.remove(name);
                            sender.sendMessage(
                                ChatColor.RED
                                    + "Penghapusan gagal; arena tetap dikunci untuk recovery: "
                                    + ex.getMessage()
                            );
                            this.getLogger().severe("Penghapusan arena " + name + " gagal: " + ex.getMessage());
                        }
                    ),
                    ex -> {
                        this.markRecoveryRequired(name);
                        this.buildingArenas.remove(name);
                        sender.sendMessage(
                            ChatColor.RED
                                + "Pemulihan perubahan match gagal; arena tetap perlu recovery: "
                                + ex.getMessage()
                        );
                        this.getLogger()
                            .severe("Stop-match sebelum penghapusan " + name + " gagal: " + ex.getMessage());
                    }
                );
            }
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Konfirmasi pemulihan world dengan: /arena delete <nama> confirm");
        }
    }

    void regenerateArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena regenerate <nama> [seed]");
        } else {
            Long seedOverride = null;
            if (args.length >= 3 && !args[2].isBlank()) {
                try {
                    seedOverride = Long.parseLong(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Seed harus berupa angka bulat (long).");
                    return;
                }
            }

            this.regenerateInternal(sender, args[1].toLowerCase(Locale.ROOT), seedOverride, null);
        }
    }

    private boolean regenerateInternal(CommandSender sender, String name, Long seedOverride, String modeOverride) {
        ArenaData old = this.arenas.get(name);
        if (old == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
            return false;
        }

        if (this.buildingArenas.contains(name) || this.hasActiveGenerationTasks(name)) {
            sender.sendMessage(
                ChatColor.RED + "Arena ini sedang diproses atau menunggu pemulihan blok. Tunggu hingga selesai."
            );
            return false;
        }

        if (!this.snapshots.exists(old.name)) {
            sender.sendMessage(
                ChatColor.RED
                    + "Snapshot asli tidak tersedia. Regenerate ditolak agar tidak menimpa world tanpa pemulihan."
            );
            return false;
        }

        long finalSeed = seedOverride != null ? seedOverride : old.seed;
        String targetMode = modeOverride != null ? modeOverride : old.mode;
        this.buildingArenas.add(name);

        try {
            this.saveArenaData(old, GenerationStatus.RESTORING);
        } catch (RuntimeException ex) {
            this.buildingArenas.remove(name);
            sender.sendMessage(
                ChatColor.RED + "Status regeneration gagal disimpan; world tidak diubah: " + ex.getMessage()
            );
            return false;
        }

        this.incompleteArenas.put(name, old);
        this.arenas.remove(name);
        sender.sendMessage(
            ChatColor.GRAY
                + "Seed layout regenerate: "
                + finalSeed
                + " | gunakan seed lain untuk variasi struktur yang berbeda."
        );
        this.stopAndRestoreMatch(
            name,
            () -> this.restoreArenaSnapshot(
                sender,
                old,
                false,
                () -> {
                    World world = Bukkit.getWorld(old.worldName);
                    if (world == null) {
                        this.markRecoveryRequired(name);
                        this.buildingArenas.remove(name);
                        sender.sendMessage(
                            ChatColor.RED
                                + "World arena tidak termuat setelah pemulihan. Arena tetap berstatus incomplete."
                        );
                    } else {
                        try {
                            this.saveArenaData(old, GenerationStatus.SCANNING);
                        } catch (RuntimeException ex) {
                            this.markRecoveryRequired(name);
                            this.buildingArenas.remove(name);
                            sender.sendMessage(
                                ChatColor.RED
                                    + "Status scanning gagal disimpan; arena tetap memerlukan recovery: "
                                    + ex.getMessage()
                            );
                            return;
                        }

                        this.arenas.remove(name);
                        this.incompleteArenas.put(name, old);
                        sender.sendMessage(
                            ChatColor.YELLOW + "Arena lama dipulihkan. Memindai medan untuk regenerate..."
                        );
                        this.scanAndBuildLocked(sender, name, world, old.centerX, old.centerZ, finalSeed, targetMode);
                    }
                },
                ex -> {
                    this.markRecoveryRequired(name);
                    this.buildingArenas.remove(name);
                    sender.sendMessage(
                        ChatColor.RED
                            + "Regeneration gagal; snapshot dipertahankan dan arena memerlukan recovery: "
                            + ex.getMessage()
                    );
                    this.getLogger().severe("Regeneration arena " + name + " gagal: " + ex.getMessage());
                }
            ),
            ex -> {
                this.markRecoveryRequired(name);
                this.buildingArenas.remove(name);
                sender.sendMessage(
                    ChatColor.RED + "Pemulihan state match gagal; regeneration dibatalkan: " + ex.getMessage()
                );
                this.getLogger().severe("Stop-match sebelum regenerate " + name + " gagal: " + ex.getMessage());
            }
        );
        return true;
    }

    void recoverArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena recover <nama>");
        } else {
            String name = args[1].toLowerCase(Locale.ROOT);
            ArenaData data = this.incompleteArenas.get(name);
            if (data == null) {
                sender.sendMessage(ChatColor.RED + "Tidak ada arena tertunda dengan nama itu.");
            } else if (!this.buildingArenas.contains(name) && !this.hasActiveGenerationTasks(name)) {
                if (args.length >= 3 && args[2].equalsIgnoreCase("discard")) {
                    this.discardArenaRecord(sender, data, args.length >= 4 && args[3].equalsIgnoreCase("confirm"));
                } else if (!this.snapshots.exists(name)) {
                    sender.sendMessage(
                        ChatColor.RED
                            + "Snapshot recovery tidak ditemukan. Pulihkan world dari backup manual, atau lepaskan registrasi dengan /arena recover "
                            + name
                            + " discard (world tidak diubah)."
                    );
                } else {
                    this.buildingArenas.add(name);

                    try {
                        this.saveArenaData(data, GenerationStatus.RESTORING);
                    } catch (RuntimeException ex) {
                        this.buildingArenas.remove(name);
                        sender.sendMessage(
                            ChatColor.RED
                                + "Tidak dapat menyimpan status recovery; world tidak diubah: "
                                + ex.getMessage()
                        );
                        return;
                    }

                    this.stopAndRestoreMatch(
                        name,
                        () -> this.restoreArenaSnapshot(
                            sender,
                            data,
                            true,
                            () -> {
                                this.removeArenaRecord(name);
                                this.incompleteArenas.remove(name);
                                this.arenas.remove(name);
                                this.buildingArenas.remove(name);
                                sender.sendMessage(
                                    ChatColor.GREEN
                                        + "Recovery "
                                        + name
                                        + " selesai. World dikembalikan ke keadaan sebelum generator dijalankan."
                                );
                            },
                            ex -> {
                                this.markRecoveryRequired(name);
                                this.buildingArenas.remove(name);
                                sender.sendMessage(
                                    ChatColor.RED
                                        + "Recovery gagal; arena tetap berstatus incomplete: "
                                        + ex.getMessage()
                                );
                                this.getLogger().severe("Recovery arena " + name + " gagal: " + ex.getMessage());
                            }
                        ),
                        ex -> {
                            this.markRecoveryRequired(name);
                            this.buildingArenas.remove(name);
                            sender.sendMessage(
                                ChatColor.RED
                                    + "Pemulihan state pertandingan gagal; recovery arena dibatalkan: "
                                    + ex.getMessage()
                            );
                            this.getLogger()
                                .severe("Restore match sebelum recovery " + name + " gagal: " + ex.getMessage());
                        }
                    );
                }
            } else {
                sender.sendMessage(
                    ChatColor.RED + "Arena sedang diproses atau menunggu pemulihan blok. Tunggu hingga selesai."
                );
            }
        }
    }

    private void discardArenaRecord(CommandSender sender, ArenaData data, boolean confirmed) {
        String name = data.name;
        if (!confirmed) {
            sender.sendMessage(ChatColor.RED + "PERINGATAN: ini hanya melepaskan registrasi arena " + name + ".");
            sender.sendMessage(
                ChatColor.RED
                    + "Blok world TIDAK dipulihkan; area bisa tetap berada dalam kondisi parsial (sebagian terrain/struktur arena). Berkas snapshot/backup tidak dihapus, hanya diganti nama."
            );
            sender.sendMessage(
                ChatColor.YELLOW
                    + "Area akan dikarantina dan tidak bisa dipakai untuk arena baru sampai Anda menjalankan /arena quarantine release "
                    + name
                    + " confirm."
            );
            sender.sendMessage(ChatColor.YELLOW + "Konfirmasi dengan: /arena recover " + name + " discard confirm");
        } else if (this.matches.containsKey(name)) {
            sender.sendMessage(
                ChatColor.RED
                    + "Masih ada state pertandingan untuk arena ini. Jalankan /arena recover "
                    + name
                    + " lebih dulu agar perubahan match dipulihkan."
            );
        } else if (!this.buildingArenas.contains(name) && !this.hasActiveGenerationTasks(name)) {
            String arenaRoot = "arenas." + name;
            String quarantineRoot = "quarantined-arenas." + name;
            String previousState = this.getConfig().getString(arenaRoot + ".generation-state", "UNKNOWN");
            String discardedAt = Instant.now().toString();

            try {
                this.commitConfig(List.of(arenaRoot, quarantineRoot), () -> {
                    this.getConfig().set(quarantineRoot + ".world", data.worldName);
                    this.getConfig().set(quarantineRoot + ".center-x", data.centerX);
                    this.getConfig().set(quarantineRoot + ".center-z", data.centerZ);
                    this.getConfig().set(quarantineRoot + ".radius", data.radius);
                    this.getConfig().set(quarantineRoot + ".previous-state", previousState);
                    this.getConfig().set(quarantineRoot + ".discarded-at", discardedAt);
                    this.getConfig().set(arenaRoot, null);
                });
            } catch (RuntimeException ex) {
                sender.sendMessage(
                    ChatColor.RED
                        + "Registrasi tidak dilepas karena penyimpanan gagal; tidak ada yang berubah: "
                        + ex.getMessage()
                );
                return;
            }

            this.incompleteArenas.remove(name);
            this.arenas.remove(name);
            this.quarantinedAreas
                .put(
                    name,
                    new ArenaSystemPlugin.QuarantinedArea(name, data.worldName, data.centerX, data.centerZ, data.radius)
                );
            List<String> preserved = List.of();

            try {
                preserved = this.snapshots.quarantine(name, System.currentTimeMillis());
            } catch (IOException ex) {
                this.getLogger()
                    .warning(
                        "Berkas snapshot arena "
                            + name
                            + " tidak dapat diganti nama dan tetap berada di tempatnya: "
                            + ex.getMessage()
                    );
            }

            this.getLogger()
                .warning(
                    "Registrasi arena "
                        + name
                        + " dilepas secara administratif (state sebelumnya "
                        + previousState
                        + "). World tidak diubah dan mungkin parsial. Berkas yang dipertahankan: "
                        + preserved
                );
            sender.sendMessage(
                ChatColor.YELLOW
                    + "Registrasi arena "
                    + name
                    + " dilepas. World TIDAK diubah dan mungkin masih parsial."
            );
            sender.sendMessage(
                ChatColor.GRAY
                    + (
                        preserved.isEmpty()
                            ? "Tidak ada berkas snapshot yang ditemukan/dipindahkan."
                            : "Berkas snapshot dipertahankan sebagai: "
                                + String.join(", ", preserved)
                                + " (folder snapshots)."
                    )
            );
            sender.sendMessage(
                ChatColor.GRAY
                    + "Area dikarantina untuk arena baru. Lepaskan dengan /arena quarantine release "
                    + name
                    + " confirm."
            );
        } else {
            sender.sendMessage(ChatColor.RED + "Arena sedang diproses; tidak dapat dilepaskan.");
        }
    }

    void quarantineCommand(CommandSender sender, String[] args) {
        if (args.length >= 2 && !args[1].equalsIgnoreCase("list")) {
            if (args[1].equalsIgnoreCase("release") && args.length >= 3) {
                String name = args[2].toLowerCase(Locale.ROOT);
                ArenaSystemPlugin.QuarantinedArea area = this.quarantinedAreas.get(name);
                if (area == null) {
                    sender.sendMessage(ChatColor.RED + "Tidak ada area karantina dengan nama itu.");
                } else if (args.length >= 4 && args[3].equalsIgnoreCase("confirm")) {
                    String root = "quarantined-arenas." + name;

                    try {
                        this.commitConfig(List.of(root), () -> this.getConfig().set(root, null));
                    } catch (RuntimeException ex) {
                        sender.sendMessage(
                            ChatColor.RED + "Karantina tidak dilepas karena penyimpanan gagal: " + ex.getMessage()
                        );
                        return;
                    }

                    this.quarantinedAreas.remove(name);
                    this.getLogger().warning("Karantina area " + name + " dilepas oleh " + sender.getName() + ".");
                    sender.sendMessage(
                        ChatColor.GREEN
                            + "Karantina "
                            + name
                            + " dilepas. Berkas snapshot berlabel .discarded- tidak dihapus."
                    );
                } else {
                    sender.sendMessage(
                        ChatColor.RED
                            + "Melepas karantina menandai area "
                            + name
                            + " boleh dipakai arena baru, walaupun world di sana mungkin masih parsial."
                    );
                    sender.sendMessage(
                        ChatColor.YELLOW + "Konfirmasi dengan: /arena quarantine release " + name + " confirm"
                    );
                }
            } else {
                sender.sendMessage(
                    ChatColor.YELLOW + "Gunakan: /arena quarantine list | /arena quarantine release <nama> confirm"
                );
            }
        } else if (this.quarantinedAreas.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Tidak ada area karantina.");
        } else {
            sender.sendMessage(ChatColor.GOLD + "Area karantina (world mungkin parsial):");

            for (ArenaSystemPlugin.QuarantinedArea area : this.quarantinedAreas.values()) {
                sender.sendMessage(
                    ChatColor.GRAY
                        + "- "
                        + ChatColor.AQUA
                        + area.name()
                        + ChatColor.GRAY
                        + " | "
                        + area.worldName()
                        + " | pusat "
                        + area.centerX()
                        + ","
                        + area.centerZ()
                        + " | radius "
                        + area.radius()
                );
            }
        }
    }

    void restoreArenaSnapshot(CommandSender sender, ArenaData arena, boolean deleteSnapshot, Runnable after) {
        this.restoreArenaSnapshot(sender, arena, deleteSnapshot, after, ex -> {
            sender.sendMessage(ChatColor.RED + "Pemulihan snapshot arena gagal: " + ex.getMessage());
            this.getLogger().severe("Pemulihan snapshot arena " + arena.name + " gagal: " + ex.getMessage());
        });
    }

    void restoreArenaSnapshot(
        CommandSender sender,
        ArenaData arena,
        boolean deleteSnapshot,
        Runnable after,
        Consumer<RuntimeException> onFailure
    ) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) {
            this.markRecoveryRequired(arena.name);
            onFailure.accept(new IllegalStateException("World " + arena.worldName + " tidak termuat."));
        } else {
            SnapshotStore.LoadedSnapshot snapshot;
            try {
                snapshot = this.snapshots.loadSnapshot(arena);
            } catch (IOException ex) {
                this.markRecoveryRequired(arena.name);
                onFailure.accept(new IllegalStateException("Gagal membaca snapshot: " + ex.getMessage(), ex));
                return;
            }

            Map<BlockPos, String> data = snapshot.blocks();
            if (snapshot.legacy()) {
                this.getLogger()
                    .warning(
                        "Snapshot ARN2 arena "
                            + arena.name
                            + " tidak memiliki origin absolut; memakai centerY dari config untuk kompatibilitas."
                    );
            }

            sender.sendMessage(
                ChatColor.YELLOW + "Memulihkan " + data.size() + " posisi blok untuk arena " + arena.name + "..."
            );
            int footprintBound = Math.max(arena.radius, Math.max(40, 56));
            this.restoreMapBatched(
                world,
                arena,
                snapshot.centerY(),
                footprintBound,
                data,
                () -> {
                    try {
                        after.run();
                    } catch (RuntimeException ex) {
                        this.markRecoveryRequired(arena.name);
                        onFailure.accept(ex);
                        return;
                    }

                    if (deleteSnapshot) {
                        try {
                            this.snapshots.delete(arena.name);
                        } catch (IOException ex) {
                            this.getLogger()
                                .warning(
                                    "World arena "
                                        + arena.name
                                        + " sudah dipulihkan, tetapi berkas snapshot orphan gagal dibersihkan: "
                                        + ex.getMessage()
                                );
                        }
                    }
                },
                onFailure
            );
        }
    }

    BukkitTask restoreMapBatched(World world, ArenaData arena, Map<BlockPos, String> changes, Runnable after) {
        return this.restoreMapBatched(
            world,
            arena,
            changes,
            after,
            ex -> this.getLogger().severe("Pemulihan blok arena " + arena.name + " gagal: " + ex.getMessage())
        );
    }

    BukkitTask restoreMapBatched(
        World world,
        ArenaData arena,
        Map<BlockPos, String> changes,
        Runnable after,
        Consumer<RuntimeException> onFailure
    ) {
        return this.restoreMapBatched(world, arena, arena.centerY, -1, changes, after, onFailure);
    }

    private BukkitTask restoreMapBatched(
        final World world,
        final ArenaData arena,
        int originY,
        int horizontalBound,
        Map<BlockPos, String> changes,
        final Runnable after,
        final Consumer<RuntimeException> onFailure
    ) {
        if (world == null) {
            onFailure.accept(new IllegalStateException("World arena tidak tersedia."));
            return null;
        }

        final List<SnapshotRestoreValidator.Entry<BlockData>> plan;
        try {
            plan = SnapshotRestoreValidator.validate(
                changes,
                arena.centerX,
                originY,
                arena.centerZ,
                horizontalBound,
                world.getMinHeight(),
                world.getMaxHeight(),
                raw -> Bukkit.createBlockData(raw)
            );
        } catch (RuntimeException ex) {
            if (this.isIncompleteArena(arena)) {
                this.markRecoveryRequired(arena.name);
            }

            onFailure.accept(ex);
            return null;
        }

        final BukkitTask[] taskReference = new BukkitTask[1];
        BukkitRunnable restoreTask = new BukkitRunnable()        // $VF: Compiled from ArenaSystemPlugin.java
 {
            private int index;

            private void finishTask() {
                ArenaSystemPlugin.this.untrackGenerationTask(arena.name, taskReference[0]);
            }

            @Override
            public void run() {
                try {
                    for (int end = Math.min(this.index + 300, plan.size()); this.index < end; this.index++) {
                        SnapshotRestoreValidator.Entry<BlockData> entry = plan.get(this.index);
                        world.getBlockAt(entry.worldX, entry.worldY, entry.worldZ).setBlockData(entry.data, false);
                    }
                } catch (RuntimeException ex) {
                    this.cancel();
                    this.finishTask();
                    if (ArenaSystemPlugin.this.isIncompleteArena(arena)) {
                        ArenaSystemPlugin.this.markRecoveryRequired(arena.name);
                    }

                    onFailure.accept(ex);
                    return;
                }

                if (this.index >= plan.size()) {
                    this.cancel();
                    this.finishTask();

                    try {
                        after.run();
                    } catch (RuntimeException ex) {
                        onFailure.accept(ex);
                    }
                }
            }
        };
        taskReference[0] = restoreTask.runTaskTimer(this, 1L, 1L);
        this.trackGenerationTask(arena.name, taskReference[0]);
        return taskReference[0];
    }

    void restoreMapImmediately(ArenaData arena, Map<BlockPos, String> changes) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world != null) {
            for (Entry<BlockPos, String> entry : changes.entrySet()) {
                BlockPos pos = entry.getKey();

                try {
                    world.getBlockAt(arena.centerX + pos.x, arena.centerY + pos.y, arena.centerZ + pos.z)
                        .setBlockData(Bukkit.createBlockData(entry.getValue()), false);
                } catch (RuntimeException ex) {
                    this.getLogger().warning("Gagal mengembalikan blok match " + arena.name + ": " + ex.getMessage());
                }
            }
        }
    }

    void resetMatch(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena reset <nama>");
        } else {
            String name = args[1].toLowerCase(Locale.ROOT);
            if (!this.buildingArenas.contains(name)
                && !this.incompleteArenas.containsKey(name)
                && !this.hasActiveGenerationTasks(name)) {
                MatchState state = this.matches.get(name);
                if (state == null) {
                    sender.sendMessage(
                        ChatColor.YELLOW + "Tidak ada perubahan pertandingan yang perlu di-reset untuk arena itu."
                    );
                } else {
                    ArenaData arena = this.arenas.get(name);
                    if (arena == null) {
                        this.matches.remove(name);
                        sender.sendMessage(ChatColor.RED + "Data arena hilang; state pertandingan dibersihkan.");
                    } else {
                        this.cancelMatchTasks(state);
                        if (this.playerArenaListener != null) {
                            this.playerArenaListener.stopDeathCamerasForArena(name);
                        }

                        if (this.classPassiveManager != null) {
                            this.classPassiveManager.clearMatch(state);
                        }

                        state.running = false;
                        state.preparing = false;
                        state.launching = false;
                        state.ending = true;
                        this.clearMatchHud(state);
                        this.restorePlayerSnapshots(state);
                        World world = Bukkit.getWorld(arena.worldName);
                        if (world == null) {
                            sender.sendMessage(ChatColor.RED + "World arena tidak termuat; reset dibatalkan.");
                        } else {
                            this.restoreMapBatched(
                                world,
                                arena,
                                this.combinedMatchChanges(state),
                                () -> {
                                    this.removeRelicItems(arena, state);
                                    this.matches.remove(name);
                                    sender.sendMessage(
                                        ChatColor.GREEN
                                            + "Perubahan blok selama persiapan/pertandingan pada arena "
                                            + name
                                            + " telah di-reset."
                                    );
                                }
                            );
                        }
                    }
                }
            } else {
                sender.sendMessage(
                    ChatColor.RED
                        + "Arena sedang diproses atau memerlukan recovery. Gunakan /arena recover "
                        + name
                        + "."
                );
            }
        }
    }

    void stopAndRestoreMatch(String name, Runnable after) {
        this.stopAndRestoreMatch(
            name,
            after,
            ex -> this.getLogger().severe("Stop-match arena " + name + " gagal memulihkan blok: " + ex.getMessage())
        );
    }

    void stopAndRestoreMatch(String name, Runnable after, Consumer<RuntimeException> onFailure) {
        MatchState state = this.matches.get(name);
        ArenaData resolvedArena = this.arenas.get(name);
        if (resolvedArena == null) {
            resolvedArena = this.incompleteArenas.get(name);
        }

        if (state != null && resolvedArena != null) {
            ArenaData arena = resolvedArena;
            this.cancelMatchTasks(state);
            if (this.playerArenaListener != null) {
                this.playerArenaListener.stopDeathCamerasForArena(name);
            }

            if (this.classPassiveManager != null) {
                this.classPassiveManager.clearMatch(state);
            }

            state.running = false;
            state.preparing = false;
            state.launching = false;
            state.ending = true;
            this.clearMatchHud(state);
            this.restorePlayerSnapshots(state);
            World world = Bukkit.getWorld(arena.worldName);
            if (world == null) {
                onFailure.accept(new IllegalStateException("World arena tidak termuat."));
            } else {
                this.restoreMapBatched(world, arena, this.combinedMatchChanges(state), () -> {
                    this.removeRelicItems(arena, state);
                    this.matches.remove(name);
                    after.run();
                }, onFailure);
            }
        } else {
            try {
                after.run();
            } catch (RuntimeException ex) {
                onFailure.accept(ex);
            }
        }
    }

    void cancelMatchTasks(MatchState state) {
        if (state.timer != null) {
            state.timer.cancel();
        }

        if (state.preparationTimer != null) {
            state.preparationTimer.cancel();
        }

        if (state.lobbyBuildTask != null) {
            state.lobbyBuildTask.cancel();
        }

        if (state.lobbyRestoreTask != null) {
            state.lobbyRestoreTask.cancel();
            this.untrackGenerationTask(state.arenaName, state.lobbyRestoreTask);
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
        for (Entry<UUID, PlayerSnapshot> entry : new ArrayList<>(state.playerSnapshots.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                this.restoreRelicCarrierMovement(player, state);
                if (this.classAbilityListener != null) {
                    this.classAbilityListener.cancelFor(player);
                }

                if (this.classPassiveManager != null) {
                    this.classPassiveManager.clearPlayer(entry.getKey());
                }

                player.closeInventory();
                if (!this.restorePlayerSnapshot(player, entry.getValue())) {
                    this.queuePendingPlayerRestore(entry.getKey(), entry.getValue());
                }
            } else {
                this.queuePendingPlayerRestore(entry.getKey(), entry.getValue());
            }
        }

        state.playerSnapshots.clear();
        state.relicOriginalWalkSpeeds.clear();
    }

    boolean persistPlayerSnapshot(UUID playerId, PlayerSnapshot snapshot) {
        if (this.playerSnapshotStore == null) {
            this.getLogger().severe("PlayerSnapshotStore belum siap; pemain " + playerId + " tidak boleh masuk arena.");
            return false;
        }

        try {
            this.playerSnapshotStore.save(playerId, snapshot);
            return true;
        } catch (IOException ex) {
            this.getLogger()
                .log(Level.SEVERE, "Gagal menyimpan snapshot pemain " + playerId + ". Join arena dibatalkan.", ex);
            return false;
        }
    }

    void queuePendingPlayerRestore(UUID playerId, PlayerSnapshot snapshot) {
        this.pendingPlayerRestores.put(playerId, snapshot);
        if (!this.persistPlayerSnapshot(playerId, snapshot)) {
            this.getLogger()
                .severe(
                    "Snapshot pemain "
                        + playerId
                        + " hanya aman di memori saat ini. Periksa storage plugin sebelum server dihentikan."
                );
        }
    }

    void restorePendingPlayerRestore(Player player, PlayerSnapshot snapshot) {
        if (player != null) {
            UUID playerId = player.getUniqueId();
            if (player.isOnline() && !player.isDead() && this.pendingPlayerRestores.get(playerId) == snapshot) {
                if (this.restorePlayerSnapshot(player, snapshot)) {
                    this.pendingPlayerRestores.remove(playerId, snapshot);
                } else {
                    this.getLogger()
                        .warning(
                            "Snapshot pemain "
                                + playerId
                                + " belum dipulihkan (world asal belum tersedia atau teleport ditolak). Snapshot tetap pending."
                        );
                }
            }
        }
    }

    boolean restorePlayerSnapshot(Player player, PlayerSnapshot snapshot) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }

        if (!snapshot.restore(player)) {
            return false;
        }

        if (this.playerSnapshotStore != null) {
            try {
                this.playerSnapshotStore.delete(player.getUniqueId());
            } catch (IOException ex) {
                this.getLogger()
                    .log(
                        Level.SEVERE,
                        "Pemain " + player.getUniqueId() + " berhasil dipulihkan, tetapi file snapshot gagal dihapus.",
                        ex
                    );
            }
        }

        return true;
    }

    void applyRelicCarrierMovement(Player player, MatchState state) {
        if (player != null && state != null) {
            state.relicOriginalWalkSpeeds.putIfAbsent(player.getUniqueId(), player.getWalkSpeed());
            player.setSprinting(false);
            player.setWalkSpeed(0.1F);
        }
    }

    void enforceRelicCarrierMovement() {
        double maxAirHorizontalSpeed = 0.055;

        for (MatchState state : this.matches.values()) {
            if (state.running && "relic_rush".equals(state.mode) && state.relicCarrier != null) {
                Player carrier = Bukkit.getPlayer(state.relicCarrier);
                if (carrier != null && carrier.isOnline() && carrier.isValid()) {
                    if (carrier.isSprinting()) {
                        carrier.setSprinting(false);
                    }

                    if (Math.abs(carrier.getWalkSpeed() - 0.1F) > 0.001F) {
                        carrier.setWalkSpeed(0.1F);
                    }

                    if (!carrier.isOnGround()) {
                        Vector velocity = carrier.getVelocity();
                        double horizontalSpeed = Math.hypot(velocity.getX(), velocity.getZ());
                        if (horizontalSpeed > 0.055) {
                            double scale = 0.055 / horizontalSpeed;
                            velocity.setX(velocity.getX() * scale);
                            velocity.setZ(velocity.getZ() * scale);
                            carrier.setVelocity(velocity);
                        }
                    }
                }
            }
        }
    }

    void restoreRelicCarrierMovement(Player player, MatchState state) {
        if (player != null && state != null) {
            Float originalSpeed = state.relicOriginalWalkSpeeds.remove(player.getUniqueId());
            if (originalSpeed != null) {
                player.setWalkSpeed(originalSpeed);
            }
        }
    }

    boolean isSupportedMode(String mode) {
        return mode != null && (mode.equalsIgnoreCase("tdm") || mode.equalsIgnoreCase("relic_rush"));
    }

    static int radiusForMode(String mode) {
        return "relic_rush".equalsIgnoreCase(mode) ? 56 : 40;
    }

    void setMode(CommandSender sender, String arenaName, String requestedMode) {
        ArenaData old = this.arenas.get(arenaName.toLowerCase(Locale.ROOT));
        if (old == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
        } else if (this.matches.containsKey(old.name)) {
            sender.sendMessage(
                ChatColor.RED + "Hentikan/reset pertandingan dan kosongkan antrean sebelum mengubah mode."
            );
        } else if (this.buildingArenas.contains(old.name) || this.hasActiveGenerationTasks(old.name)) {
            sender.sendMessage(ChatColor.RED + "Arena sedang diproses; mode tidak diubah.");
        } else if (!this.isSupportedMode(requestedMode)) {
            sender.sendMessage(ChatColor.RED + "Mode tersedia: tdm atau relic_rush.");
        } else if (!this.snapshots.exists(old.name)) {
            sender.sendMessage(
                ChatColor.RED
                    + "Snapshot terrain arena tidak tersedia. Mode tidak diubah karena bentuk arena tidak dapat dibangun ulang dengan aman."
            );
        } else {
            String mode = requestedMode.toLowerCase(Locale.ROOT);
            if (this.regenerateInternal(sender, old.name, null, mode)) {
                sender.sendMessage(
                    ChatColor.GREEN + "Mode arena " + old.name + " dipilih: " + mode.toUpperCase(Locale.ROOT) + "."
                );
                sender.sendMessage(
                    ChatColor.YELLOW
                        + "Membangun ulang bentuk arena khusus mode "
                        + mode.toUpperCase(Locale.ROOT)
                        + "..."
                );
            }
        }
    }

    @EventHandler
    public void onArenaSystemPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String url = this.getConfig().getString("resource-pack.assassin-dagger.url", "").trim();
        if (!url.isEmpty()) {
            String sha1 = this.getConfig()
                .getString("resource-pack.assassin-dagger.sha1", "")
                .trim()
                .toLowerCase(Locale.ROOT);
            if (!sha1.matches("[0-9a-f]{40}")) {
                this.getLogger()
                    .warning(
                        "Resource pack Assassin Dagger tidak dikirim ke "
                            + player.getName()
                            + ": resource-pack.assassin-dagger.sha1 harus berupa 40 karakter SHA-1 heksadesimal."
                    );
                player.sendMessage(
                    ChatColor.RED
                        + "Resource pack ArenaSystem belum dikonfigurasi dengan benar. Tampilan dagger mungkin tidak tersedia."
                );
            } else {
                Bukkit.getScheduler()
                    .runTaskLater(
                        this,
                        () -> {
                            if (player.isOnline()) {
                                try {
                                    player.setResourcePack(
                                        ASSASSIN_DAGGER_RESOURCE_PACK_ID,
                                        url,
                                        HexFormat.of().parseHex(sha1),
                                        Component.text("ArenaSystem Assassin Dagger — model dan tekstur senjata"),
                                        false
                                    );
                                } catch (IllegalArgumentException ex) {
                                    this.getLogger()
                                        .warning(
                                            "URL/hash resource pack Assassin Dagger tidak valid: " + ex.getMessage()
                                        );
                                    player.sendMessage(
                                        ChatColor.RED + "Resource pack ArenaSystem gagal diminta. Beri tahu admin."
                                    );
                                }
                            }
                        },
                        20L
                    );
            }
        }
    }

    @EventHandler
    public void onAssassinDaggerResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (ASSASSIN_DAGGER_RESOURCE_PACK_ID.equals(event.getID())) {
            Player player = event.getPlayer();
            Status status = event.getStatus();
            if (status == Status.SUCCESSFULLY_LOADED) {
                player.sendMessage(
                    ChatColor.GREEN + "Resource pack ArenaSystem berhasil dimuat. Model Assassin Dagger siap."
                );
            } else if (status == Status.DECLINED
                || status == Status.FAILED_DOWNLOAD
                || status == Status.FAILED_RELOAD
                || status == Status.INVALID_URL
                || status == Status.DISCARDED) {
                player.sendMessage(
                    ChatColor.RED
                        + "Resource pack ArenaSystem belum aktif. "
                        + ChatColor.YELLOW
                        + "Model Assassin Dagger bisa tampil ungu/hitam."
                );
                player.sendMessage(
                    ChatColor.GRAY + "Aktifkan server resource pack atau pasang pack AssassinDagger 26.3."
                );
                this.getLogger()
                    .info(
                        "Resource pack ArenaSystem belum aktif untuk "
                            + player.getName()
                            + " (status: "
                            + status
                            + ")."
                    );
            }
        }
    }

    void joinArena(Player player, String arenaName, String teamInput) {
        ArenaData arena = this.arenas.get(arenaName.toLowerCase(Locale.ROOT));
        if (arena == null) {
            player.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
        } else {
            String team = teamInput.toLowerCase(Locale.ROOT);
            if (!this.teamNamesFor(arena).contains(team)) {
                player.sendMessage(
                    ChatColor.RED + "Tim yang tersedia: " + String.join(", ", this.teamNamesFor(arena)) + "."
                );
            } else {
                for (MatchState current : this.matches.values()) {
                    if (current.teams.containsKey(player.getUniqueId())) {
                        player.sendMessage(
                            ChatColor.RED + "Kamu sudah terdaftar di pertandingan arena " + current.arenaName + "."
                        );
                        return;
                    }
                }

                UUID playerId = player.getUniqueId();
                if (this.pendingPlayerRestores.containsKey(playerId)) {
                    player.sendMessage(
                        ChatColor.RED
                            + "Data pemainmu masih menunggu pemulihan. Relog atau hubungi admin sebelum masuk arena lagi."
                    );
                } else {
                    MatchState state = this.matches
                        .computeIfAbsent(arena.name, ignored -> new MatchState(arena.name, arena.mode));
                    if (!state.running && !state.preparing && !state.launching) {
                        PlayerSnapshot savedSnapshot = state.playerSnapshots.get(playerId);
                        if (savedSnapshot == null) {
                            savedSnapshot = PlayerSnapshot.capture(player);
                            if (!this.persistPlayerSnapshot(playerId, savedSnapshot)) {
                                player.sendMessage(
                                    ChatColor.RED
                                        + "Join arena dibatalkan karena backup inventory gagal disimpan. Hubungi admin."
                                );
                                if (state.teams.isEmpty() && state.playerSnapshots.isEmpty()) {
                                    this.matches.remove(arena.name, state);
                                }

                                return;
                            }

                            state.playerSnapshots.put(playerId, savedSnapshot);
                        }

                        state.selectedClasses.putIfAbsent(player.getUniqueId(), "knight");
                        state.teams.put(player.getUniqueId(), team);
                        player.teleport(this.spawnLocation(arena, team));
                        player.sendMessage(
                            ChatColor.GREEN
                                + "Kamu bergabung dengan tim "
                                + team.toUpperCase(Locale.ROOT)
                                + " di arena "
                                + arena.name
                                + "."
                        );
                        this.showTeamJoinedTitle(player, arena, state, team);
                        this.announce(state, ChatColor.YELLOW + player.getName() + " bergabung ke tim " + team + ".");
                        this.updateMatchHud(state);
                    } else {
                        player.sendMessage(
                            ChatColor.RED + "Lobby persiapan atau pertandingan sudah dimulai. Join ditutup."
                        );
                    }
                }
            }
        }
    }

    void leaveArena(Player player) {
        for (MatchState state : new ArrayList<>(this.matches.values())) {
            if (state.teams.containsKey(player.getUniqueId())) {
                if (!state.running && !state.launching) {
                    state.teams.remove(player.getUniqueId());
                    state.selectedClasses.remove(player.getUniqueId());
                    state.abilityCooldowns.remove(player.getUniqueId());
                    if (this.classAbilityListener != null) {
                        this.classAbilityListener.cancelFor(player);
                    }

                    if (this.classPassiveManager != null) {
                        this.classPassiveManager.clearPlayer(player.getUniqueId());
                    }

                    player.closeInventory();
                    Scoreboard previousBoard = state.previousScoreboards.remove(player.getUniqueId());
                    if (previousBoard != null) {
                        player.setScoreboard(previousBoard);
                    }

                    state.hudScoreboards.remove(player.getUniqueId());
                    state.hudObjectives.remove(player.getUniqueId());
                    PlayerSnapshot snapshot = state.playerSnapshots.remove(player.getUniqueId());
                    if (snapshot != null && !this.restorePlayerSnapshot(player, snapshot)) {
                        this.queuePendingPlayerRestore(player.getUniqueId(), snapshot);
                    }

                    player.sendMessage(ChatColor.GREEN + "Kamu keluar dari antrean arena " + state.arenaName + ".");
                    if (state.teams.isEmpty()) {
                        if (state.preparing) {
                            this.stopAndRestoreMatch(
                                state.arenaName,
                                () -> this.getLogger()
                                    .info("Lobby persiapan " + state.arenaName + " dibatalkan karena tidak ada pemain.")
                            );
                        } else {
                            this.matches.remove(state.arenaName);
                        }
                    } else {
                        if (state.preparing) {
                            this.adaptSinglePlayerPreparation(state);
                        }

                        this.updateMatchHud(state);
                    }

                    return;
                }

                player.sendMessage(ChatColor.RED + "Kamu tidak dapat keluar dari tim saat pertandingan berlangsung.");
                return;
            }
        }

        player.sendMessage(ChatColor.YELLOW + "Kamu tidak sedang bergabung ke arena mana pun.");
    }

    void startMatch(CommandSender sender, String arenaName) {
        ArenaData arena = this.arenas.get(arenaName.toLowerCase(Locale.ROOT));
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
        } else {
            MatchState state = this.matches.get(arena.name);
            if (state != null && !state.running && !state.preparing && !state.launching) {
                state.teams.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
                boolean singlePlayerMode = state.teams.size() == 1
                    && this.getConfig().getBoolean("matches.allow-single-player", true);
                if (state.teams.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "Tidak ada pemain online yang terdaftar di arena.");
                } else {
                    List<String> requiredTeams = this.teamNamesFor(arena);
                    List<String> missingTeams = requiredTeams.stream()
                        .filter(teamx -> state.teams.values().stream().noneMatch(teamx::equals))
                        .toList();
                    if (!singlePlayerMode && !missingTeams.isEmpty()) {
                        sender.sendMessage(
                            ChatColor.RED
                                + "Minimal satu pemain diperlukan di setiap tim: "
                                + String.join(", ", requiredTeams)
                                + ". Tim kosong: "
                                + String.join(", ", missingTeams)
                                + "."
                        );
                    } else {
                        state.singlePlayerMode = singlePlayerMode;
                        state.redScore = 0;
                        state.blueScore = 0;
                        state.secondsElapsed = 0;
                        state.teamScores.clear();

                        for (String team : requiredTeams) {
                            state.teamScores.put(team, 0);
                        }

                        state.preparationSecondsRemaining = Math.max(
                            5, Math.min(180, this.getConfig().getInt("matches.preparation-seconds", 30))
                        );
                        state.preparing = true;
                        this.clearMobsInArena(arena);
                        boolean built = this.buildPreparationLobby(
                            arena,
                            state,
                            () -> {
                                if (state.preparing && !state.ending) {
                                    for (Entry<UUID, String> entry : state.teams.entrySet()) {
                                        Player player = Bukkit.getPlayer(entry.getKey());
                                        if (player != null) {
                                            this.teleportToPreparationStation(player, arena, state, entry.getValue());
                                            if (state.mode.equals("tdm")) {
                                                state.selectedClasses.putIfAbsent(entry.getKey(), "knight");
                                                this.giveClassSelector(player);
                                            } else {
                                                state.selectedClasses.remove(entry.getKey());
                                                player.getInventory().clear();
                                                player.getInventory().setArmorContents(new ItemStack[4]);
                                            }
                                        }
                                    }

                                    this.updateMatchHud(state);
                                    this.showPreparationIntro(state);
                                    if (state.mode.equals("tdm")) {
                                        for (UUID uuid : state.teams.keySet()) {
                                            Player player = Bukkit.getPlayer(uuid);
                                            if (player != null) {
                                                this.openClassMenu(player, arena, state);
                                            }
                                        }
                                    }

                                    state.preparationTimer = (new BukkitRunnable()                                // $VF: Compiled from ArenaSystemPlugin.java
         {
                                            @Override
                                            public void run() {
                                                if (state.preparing && !state.ending) {
                                                    state.preparationSecondsRemaining--;
                                                    ArenaSystemPlugin.this.updateMatchHud(state);
                                                    if (state.preparationSecondsRemaining <= 0) {
                                                        this.cancel();
                                                        state.preparationTimer = null;
                                                        ArenaSystemPlugin.this.launchMatch(arena, state);
                                                    } else if (state.preparationSecondsRemaining <= 5) {
                                                        ArenaSystemPlugin.this.showPreparationCountdown(
                                                            state, state.preparationSecondsRemaining
                                                        );
                                                    }
                                                } else {
                                                    this.cancel();
                                                }
                                            }
                                        })
                                        .runTaskTimer(this, 20L, 20L);
                                }
                            }
                        );
                        if (!built) {
                            state.preparing = false;
                            if (!state.originalBlocks.isEmpty()) {
                                this.restoreMapImmediately(arena, state.originalBlocks);
                                state.originalBlocks.clear();
                            }

                            sender.sendMessage(
                                ChatColor.RED
                                    + "Lobby persiapan tidak dapat dibangun dengan aman. Pastikan area di atas arena kosong dan tidak berisi block entity."
                            );
                        } else {
                            sender.sendMessage(
                                ChatColor.GREEN
                                    + "Membangun lobby persiapan "
                                    + this.preparationLobbyName(state.mode)
                                    + " untuk arena "
                                    + arena.name
                                    + "..."
                            );
                        }
                    }
                }
            } else {
                sender.sendMessage(
                    ChatColor.RED + "Belum ada antrean pemain atau arena sudah dalam proses pertandingan."
                );
            }
        }
    }

    boolean buildAdditionalTeamSpawnPads(ArenaData arena, MatchState state, List<String> teams) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) {
            return false;
        }

        Map<BlockPos, Material> edits = new LinkedHashMap<>();
        if (teams.contains("green")
            && !this.collectAdditionalSpawnPad(world, arena, edits, 0, -arena.spawnOffset, Material.GREEN_TERRACOTTA)) {
            return false;
        }

        if (teams.contains("yellow")
            && !this.collectAdditionalSpawnPad(world, arena, edits, 0, arena.spawnOffset, Material.YELLOW_TERRACOTTA)) {
            return false;
        }

        Map<BlockPos, String> originals = new LinkedHashMap<>();

        for (BlockPos pos : edits.keySet()) {
            Block block = world.getBlockAt(arena.centerX + pos.x, arena.centerY + pos.y, arena.centerZ + pos.z);
            if (block.getState() instanceof TileState) {
                return false;
            }

            originals.put(pos, block.getBlockData().getAsString());
        }

        state.originalBlocks.putAll(originals);

        for (Entry<BlockPos, Material> entry : edits.entrySet()) {
            BlockPos pos = entry.getKey();
            world.getBlockAt(arena.centerX + pos.x, arena.centerY + pos.y, arena.centerZ + pos.z)
                .setType(entry.getValue(), false);
        }

        return true;
    }

    boolean collectAdditionalSpawnPad(
        World world, ArenaData arena, Map<BlockPos, Material> edits, int centerX, int centerZ, Material teamMaterial
    ) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int surfaceY = TerrainScanner.findSurface(world, arena.centerX + x, arena.centerZ + z);
                if (surfaceY == Integer.MIN_VALUE) {
                    return false;
                }

                Block surface = world.getBlockAt(arena.centerX + x, surfaceY, arena.centerZ + z);
                if (dx == 0 && dz == 0 && surface.getType() == Material.SEA_LANTERN) {
                    if (--surfaceY < world.getMinHeight()) {
                        return false;
                    }

                    surface = world.getBlockAt(arena.centerX + x, surfaceY, arena.centerZ + z);
                }

                if (surfaceY + 3 >= world.getMaxHeight()) {
                    return false;
                }

                if (surface.getState() instanceof TileState) {
                    return false;
                }

                Material pad = Math.max(Math.abs(dx), Math.abs(dz)) == 4 ? Material.WHITE_CONCRETE : teamMaterial;
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    pad = Material.SMOOTH_STONE;
                }

                edits.put(new BlockPos(x, surfaceY - arena.centerY, z), pad);

                for (int offset = 1; offset <= 3; offset++) {
                    Block clear = world.getBlockAt(arena.centerX + x, surfaceY + offset, arena.centerZ + z);
                    if (clear.getState() instanceof TileState) {
                        return false;
                    }

                    edits.put(new BlockPos(x, surfaceY + offset - arena.centerY, z), Material.AIR);
                }
            }
        }

        return true;
    }

    boolean buildPreparationLobby(final ArenaData arena, final MatchState state, final Runnable after) {
        final World world = Bukkit.getWorld(arena.worldName);
        if (world == null) {
            return false;
        }

        int lobbyY = arena.maxSurfaceY + 12;
        if (lobbyY >= world.getMinHeight() + 2 && lobbyY + 6 < world.getMaxHeight()) {
            Map<BlockPos, Material> plan = new LinkedHashMap<>();
            int floorRelativeY = lobbyY - arena.centerY;

            for (int x = -10; x <= 10; x++) {
                for (int z = -10; z <= 10; z++) {
                    for (int y = 1; y <= 5; y++) {
                        this.addLobbyBlock(plan, x, floorRelativeY + y, z, Material.AIR);
                    }
                }
            }

            if (state.mode.equals("relic_rush")) {
                this.buildRelicPreparationLayout(plan, floorRelativeY);
            } else {
                this.buildTdmPreparationLayout(plan, floorRelativeY, this.teamNamesFor(arena));
            }

            Map<BlockPos, String> originals = new LinkedHashMap<>();

            for (BlockPos pos : plan.keySet()) {
                int worldX = arena.centerX + pos.x;
                int worldY = arena.centerY + pos.y;
                int worldZ = arena.centerZ + pos.z;
                if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) {
                    return false;
                }

                Block block = world.getBlockAt(worldX, worldY, worldZ);
                if (block.getState() instanceof TileState) {
                    return false;
                }

                originals.put(pos, block.getBlockData().getAsString());
            }

            state.lobbyY = lobbyY;
            state.lobbyOriginalBlocks.clear();
            state.lobbyOriginalBlocks.putAll(originals);
            final List<Entry<BlockPos, Material>> edits = new ArrayList<>(plan.entrySet());
            state.lobbyBuildTask = (new BukkitRunnable()            // $VF: Compiled from ArenaSystemPlugin.java
     {
                    private int index;

                    @Override
                    public void run() {
                        if (state.preparing && !state.ending) {
                            try {
                                for (int end = Math.min(this.index + 300, edits.size()); this.index < end; this.index++) {
                                    Entry<BlockPos, Material> edit = edits.get(this.index);
                                    BlockPos pos = edit.getKey();
                                    world.getBlockAt(
                                            arena.centerX + pos.x, arena.centerY + pos.y, arena.centerZ + pos.z
                                        )
                                        .setType(edit.getValue(), false);
                                }

                                if (this.index >= edits.size()) {
                                    this.cancel();
                                    state.lobbyBuildTask = null;
                                    after.run();
                                }
                            } catch (RuntimeException ex) {
                                this.cancel();
                                state.lobbyBuildTask = null;
                                ArenaSystemPlugin.this.getLogger()
                                    .severe("Gagal membangun lobby arena " + arena.name + ": " + ex.getMessage());
                                ArenaSystemPlugin.this.stopAndRestoreMatch(
                                    arena.name,
                                    () -> ArenaSystemPlugin.this.getLogger()
                                        .warning("Lobby arena " + arena.name + " dibatalkan dan blok dipulihkan.")
                                );
                            }
                        } else {
                            this.cancel();
                            state.lobbyBuildTask = null;
                        }
                    }
                })
                .runTaskTimer(this, 1L, 1L);
            return true;
        } else {
            return false;
        }
    }

    void buildTdmPreparationLayout(Map<BlockPos, Material> plan, int y, List<String> activeTeams) {
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                int edge = Math.max(Math.abs(x), Math.abs(z));
                Material floor = edge == 10 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_TILES;
                if (activeTeams.contains("red") && Math.abs(x + 7) <= 2 && Math.abs(z) <= 2) {
                    floor = Material.RED_CONCRETE;
                }

                if (activeTeams.contains("blue") && Math.abs(x - 7) <= 2 && Math.abs(z) <= 2) {
                    floor = Material.BLUE_CONCRETE;
                }

                if (activeTeams.contains("green") && Math.abs(x) <= 2 && Math.abs(z + 7) <= 2) {
                    floor = Material.GREEN_CONCRETE;
                }

                if (activeTeams.contains("yellow") && Math.abs(x) <= 2 && Math.abs(z - 7) <= 2) {
                    floor = Material.YELLOW_CONCRETE;
                }

                if (Math.abs(x) <= 2 && Math.abs(z) <= 2) {
                    floor = Material.POLISHED_ANDESITE;
                }

                this.addLobbyBlock(plan, x, y, z, floor);
                boolean wall = edge == 10;
                boolean gate = Math.abs(x) <= 2 && Math.abs(z) == 10 || Math.abs(z) <= 2 && Math.abs(x) == 10;
                if (wall && !gate) {
                    for (int h = 1; h <= 3; h++) {
                        this.addLobbyBlock(plan, x, y + h, z, Material.POLISHED_BLACKSTONE_BRICKS);
                    }
                }

                if (edge == 10 && Math.abs(x) == 10 && Math.abs(z) == 10) {
                    for (int h = 1; h <= 4; h++) {
                        this.addLobbyBlock(plan, x, y + h, z, Material.CHISELED_POLISHED_BLACKSTONE);
                    }
                }
            }
        }

        if (activeTeams.contains("red")) {
            this.addLobbyBlock(plan, -7, y + 1, 0, Material.REDSTONE_BLOCK);
        }

        if (activeTeams.contains("blue")) {
            this.addLobbyBlock(plan, 7, y + 1, 0, Material.SEA_LANTERN);
        }

        if (activeTeams.contains("green")) {
            this.addLobbyBlock(plan, 0, y + 1, -7, Material.LIME_CONCRETE);
        }

        if (activeTeams.contains("yellow")) {
            this.addLobbyBlock(plan, 0, y + 1, 7, Material.YELLOW_CONCRETE);
        }

        this.addLobbyBlock(plan, 0, y + 1, 0, Material.IRON_BLOCK);
        this.addLobbyBlock(plan, 0, y + 2, 0, Material.SEA_LANTERN);
    }

    void buildRelicPreparationLayout(Map<BlockPos, Material> plan, int y) {
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                int diamondDistance = Math.abs(x) + Math.abs(z);
                if (diamondDistance <= 16) {
                    Material floor = diamondDistance >= 15 ? Material.CUT_SANDSTONE : Material.SMOOTH_SANDSTONE;
                    if (diamondDistance >= 10 && diamondDistance <= 12) {
                        floor = Material.CHISELED_SANDSTONE;
                    }

                    if (Math.abs(x + 7) <= 2 && Math.abs(z) <= 2) {
                        floor = Material.RED_TERRACOTTA;
                    }

                    if (Math.abs(x - 7) <= 2 && Math.abs(z) <= 2) {
                        floor = Material.BLUE_TERRACOTTA;
                    }

                    if (Math.abs(x) <= 2 && Math.abs(z) <= 2) {
                        floor = Material.GOLD_BLOCK;
                    }

                    this.addLobbyBlock(plan, x, y, z, floor);
                }
            }
        }

        this.addLobbyBlock(plan, 0, y + 1, 0, Material.AMETHYST_BLOCK);
        this.addLobbyBlock(plan, 0, y + 2, 0, Material.SEA_LANTERN);
        this.addLobbyBlock(plan, 0, y + 3, 0, Material.AMETHYST_BLOCK);

        for (int sx : new int[]{-6, 6}) {
            for (int sz : new int[]{-6, 6}) {
                for (int h = 1; h <= 3; h++) {
                    this.addLobbyBlock(plan, sx, y + h, sz, h == 3 ? Material.AMETHYST_BLOCK : Material.PURPUR_BLOCK);
                }
            }
        }

        for (int h = 1; h <= 3; h++) {
            this.addLobbyBlock(plan, -7, y + h, 0, Material.RED_TERRACOTTA);
            this.addLobbyBlock(plan, 7, y + h, 0, Material.BLUE_TERRACOTTA);
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
        String normalizedTeam = team == null ? "" : team.toLowerCase(Locale.ROOT);
        Location station;
        if ("red".equals(normalizedTeam)) {
            station = new Location(world, arena.centerX - 7.5, state.lobbyY + 1.0, arena.centerZ + 1.5, -90.0F, 0.0F);
        } else if ("blue".equals(normalizedTeam)) {
            station = new Location(world, arena.centerX + 7.5, state.lobbyY + 1.0, arena.centerZ + 1.5, 90.0F, 0.0F);
        } else if ("green".equals(normalizedTeam)) {
            station = new Location(world, arena.centerX + 1.5, state.lobbyY + 1.0, arena.centerZ - 7.5, 0.0F, 0.0F);
        } else if ("yellow".equals(normalizedTeam)) {
            station = new Location(world, arena.centerX + 1.5, state.lobbyY + 1.0, arena.centerZ + 7.5, 180.0F, 0.0F);
        } else {
            station = new Location(world, arena.centerX + 0.5, state.lobbyY + 1.0, arena.centerZ + 7.5, 180.0F, 0.0F);
        }

        return this.faceArenaCenter(station, arena);
    }

    Location faceArenaCenter(Location location, ArenaData arena) {
        if (location != null && arena != null) {
            Vector towardCenter = new Vector(
                arena.centerX + 0.5 - location.getX(), 0.0, arena.centerZ + 0.5 - location.getZ()
            );
            if (towardCenter.lengthSquared() > 1.0E-6) {
                location.setDirection(towardCenter);
            }

            return location;
        } else {
            return location;
        }
    }

    void teleportToPreparationStation(Player player, ArenaData arena, MatchState state, String team) {
        if (player != null && arena != null && state != null && team != null) {
            Location station = this.preparationSpawnLocation(arena, state, team, state.singlePlayerMode);
            if (station.getWorld() == null) {
                this.getLogger().warning("World lobby tidak ditemukan untuk arena " + arena.name + ".");
            } else {
                player.setVelocity(new Vector(0.0, 0.0, 0.0));
                player.setFallDistance(0.0F);
                boolean moved = player.teleport(station);
                if (!moved) {
                    this.getLogger()
                        .warning(
                            "Teleport persiapan gagal untuk "
                                + player.getName()
                                + " ke stasiun tim "
                                + team.toUpperCase(Locale.ROOT)
                                + " di arena "
                                + arena.name
                                + "."
                        );
                } else {
                    Bukkit.getScheduler()
                        .runTask(
                            this,
                            () -> {
                                MatchState active = this.matches.get(arena.name);
                                if (player.isOnline()
                                    && active == state
                                    && state.preparing
                                    && team.equals(state.teams.get(player.getUniqueId()))) {
                                    Location current = player.getLocation();
                                    double yawDifference = Math.abs(
                                        ((double)current.getYaw() - station.getYaw() + 540.0) % 360.0 - 180.0
                                    );
                                    boolean facingMismatch = yawDifference > 1.0
                                        || Math.abs(current.getPitch() - station.getPitch()) > 1.0F;
                                    if (current.getWorld() != station.getWorld()
                                        || current.distanceSquared(station) > 1.0
                                        || facingMismatch) {
                                        player.teleport(station);
                                        player.setVelocity(new Vector(0.0, 0.0, 0.0));
                                        player.setFallDistance(0.0F);
                                    }
                                }
                            }
                        );
                }
            }
        }
    }

    void showTeamJoinedTitle(Player joinedPlayer, ArenaData arena, MatchState state, String team) {
        if (joinedPlayer != null && arena != null && state != null && team != null) {
            ChatColor color = this.teamColor(team);
            String teamName = this.teamDisplayName(team);
            String subtitle = ChatColor.WHITE
                + joinedPlayer.getName()
                + " bergabung ke "
                + color
                + teamName
                + ChatColor.WHITE
                + " TEAM";

            for (UUID memberId : state.teams.keySet()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.sendTitle(
                        ChatColor.GOLD + "ARENA", subtitle + ChatColor.GRAY + " • " + arena.name, 5, 55, 10
                    );
                }
            }
        }
    }

    void showPreparationIntro(MatchState state) {
        String title;
        String subtitle;
        if (state.mode.equals("tdm")) {
            title = ChatColor.GOLD + "TEAM DEATHMATCH";
            subtitle = ChatColor.WHITE
                + "Pilih class  •  Target "
                + this.getConfig().getInt("matches.tdm-target-score", 20)
                + " kill";
        } else {
            title = ChatColor.GOLD + "RELIC RUSH";
            subtitle = ChatColor.WHITE + "Rebut Relic  •  Antar ke markas";
        }

        for (UUID uuid : state.teams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendTitle(title, subtitle, 5, 55, 10);
            }
        }
    }

    void showPreparationCountdown(MatchState state, int seconds) {
        for (UUID uuid : state.teams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendTitle(
                    ChatColor.YELLOW + Integer.toString(seconds),
                    seconds == 1 ? ChatColor.GREEN + "MULAI!" : ChatColor.WHITE + "Bersiap!",
                    0,
                    20,
                    0
                );
                if (seconds == 1) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9F, 1.85F);
                } else {
                    player.playSound(
                        player.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_HAT,
                        0.8F,
                        seconds == 5 ? 0.85F : (seconds == 4 ? 0.95F : (seconds == 3 ? 1.05F : 1.15F))
                    );
                }
            }
        }
    }

    void showTdmGoTitle(MatchState state) {
        for (UUID uuid : state.teams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendTitle(
                    ChatColor.GOLD + "ARENA\n" + ChatColor.YELLOW + "Teamdeathmatch",
                    ChatColor.GREEN + "GO!",
                    5,
                    35,
                    10
                );
            }
        }
    }

    void launchMatch(ArenaData arena, MatchState state) {
        if (state.preparing && !state.ending) {
            state.preparing = false;
            state.launching = true;
            this.updateMatchHud(state);
            if (state.preparationTimer != null) {
                state.preparationTimer.cancel();
            }

            state.preparationTimer = null;

            for (Entry<UUID, String> entry : state.teams.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    player.closeInventory();
                    player.teleport(this.spawnLocation(arena, entry.getValue()));
                }
            }

            state.lobbyRestoreTask = this.restoreMapBatched(
                Bukkit.getWorld(arena.worldName), arena, state.lobbyOriginalBlocks, () -> {
                    state.lobbyRestoreTask = null;
                    state.lobbyOriginalBlocks.clear();
                    if (!state.ending) {
                        this.beginLiveMatch(arena, state);
                    }
                }
            );
        }
    }

    void beginLiveMatch(final ArenaData arena, final MatchState state) {
        state.launching = false;
        state.running = true;
        state.secondsRemaining = this.getConfig().getInt("matches.duration-seconds", 300);

        for (Entry<UUID, String> entry : state.teams.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                if (state.mode.equals("tdm")) {
                    this.giveClassLoadout(player, state.selectedClasses.getOrDefault(entry.getKey(), "knight"));
                } else {
                    this.giveRelicRunnerLoadout(player, entry.getValue());
                }
            }
        }

        this.updateMatchHud(state);
        if (state.mode.equals("tdm")) {
            this.showTdmGoTitle(state);
        }

        this.announce(
            state,
            ChatColor.GOLD
                + "Pertandingan "
                + state.mode.toUpperCase(Locale.ROOT)
                + " dimulai! Durasi "
                + this.formatTime(state.secondsRemaining)
                + "."
        );
        if (state.mode.equals("relic_rush")) {
            this.announce(
                state,
                ChatColor.YELLOW
                    + "OBJEKTIF: dekati Relic di altar tengah untuk mengambilnya, lalu dekati sanctum markas RED/BLUE untuk mengamankan. Bunuh pembawa Relic untuk menjatuhkannya. Target: "
                    + this.getConfig().getInt("matches.relic-target-score", 3)
                    + " capture."
            );
            state.relicToken = UUID.randomUUID().toString();
            state.relicCarrier = null;
            state.relicDropped = false;
            this.spawnRelic(arena, state);
        } else {
            this.announce(
                state,
                ChatColor.YELLOW
                    + "OBJEKTIF: kalahkan pemain lawan. Setiap kill bernilai 1 poin; target "
                    + this.getConfig().getInt("matches.tdm-target-score", 20)
                    + " kill."
            );
        }

        state.timer = (new BukkitRunnable()        // $VF: Compiled from ArenaSystemPlugin.java
     {
                @Override
                public void run() {
                    state.secondsRemaining--;
                    state.secondsElapsed++;
                    if (state.secondsRemaining <= 0) {
                        ArenaSystemPlugin.this.finishMatch(state, arena, "Waktu habis.");
                    } else {
                        int randomStuffInterval = Math.max(
                            1,
                            Math.min(
                                600,
                                ArenaSystemPlugin.this.getConfig().getInt("matches.random-stuff-interval-seconds", 30)
                            )
                        );
                        if (state.secondsElapsed % randomStuffInterval == 0) {
                            ArenaSystemPlugin.this.distributeRandomStuff(state);
                        }

                        ArenaSystemPlugin.this.updateMatchHud(state);
                        if (state.secondsRemaining % 60 == 0 || state.secondsRemaining <= 10) {
                            ArenaSystemPlugin.this.announce(
                                state,
                                ChatColor.GRAY
                                    + "Sisa waktu: "
                                    + ArenaSystemPlugin.this.formatTime(state.secondsRemaining)
                            );
                        }
                    }
                }
            })
            .runTaskTimer(this, 20L, 20L);
    }

    void distributeRandomStuff(MatchState state) {
        List<Player> eligible = new ArrayList<>();

        for (UUID uuid : state.teams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !player.isDead() && player.getGameMode() == GameMode.SURVIVAL) {
                eligible.add(player);
            }
        }

        if (!eligible.isEmpty()) {
            Collections.shuffle(eligible, ThreadLocalRandom.current());
            int maxRecipients = Math.max(
                1, Math.min(6, this.getConfig().getInt("matches.random-stuff-max-recipients", 6))
            );
            int recipients = ThreadLocalRandom.current().nextInt(1, Math.min(maxRecipients, eligible.size()) + 1);
            List<Material> pool = List.of(
                Material.GOLDEN_APPLE,
                Material.HONEY_BOTTLE,
                Material.ARROW,
                Material.SHIELD,
                Material.FIREWORK_ROCKET,
                Material.SNOWBALL,
                Material.FIRE_CHARGE,
                Material.ENDER_PEARL,
                Material.IRON_SWORD
            );

            for (int i = 0; i < recipients; i++) {
                Player recipient = eligible.get(i);
                this.removeRandomStuffItems(recipient, state);
                Material reward = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                String token = UUID.randomUUID().toString();
                ItemStack single = new ItemStack(reward, 1);
                ItemMeta meta = single.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(
                        ChatColor.GOLD + "RANDOM STUFF: " + ChatColor.YELLOW + this.randomStuffDisplayName(reward)
                    );
                    meta.setLore(
                        List.of(
                            ChatColor.GRAY + this.randomStuffDescription(reward),
                            ChatColor.GRAY + "Klik kanan untuk menggunakan",
                            ChatColor.DARK_GRAY + "Hilang dalam 20 detik"
                        )
                    );
                    meta.getPersistentDataContainer().set(this.randomStuffKey, PersistentDataType.STRING, token);
                    single.setItemMeta(meta);
                }

                Map<Integer, ItemStack> leftovers = recipient.getInventory().addItem(single);

                for (ItemStack leftover : leftovers.values()) {
                    Item dropped = recipient.getWorld().dropItemNaturally(recipient.getLocation(), leftover);
                    this.scheduleRandomStuffGroundRemoval(dropped);
                }

                state.randomStuffTokens.put(recipient.getUniqueId(), token);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    MatchState current = this.matches.get(state.arenaName);
                    if (current != null && token.equals(current.randomStuffTokens.get(recipient.getUniqueId()))) {
                        this.removeRandomStuffItems(recipient, current);
                        current.randomStuffTokens.remove(recipient.getUniqueId());
                        if (recipient.isOnline()) {
                            recipient.sendMessage(ChatColor.GRAY + "Random Stuff kedaluwarsa.");
                        }
                    }
                }, Math.max(1, this.getConfig().getInt("matches.random-stuff-expire-seconds", 20)) * 20L);
                recipient.sendMessage(
                    ChatColor.GOLD
                        + "RANDOM STUFF: "
                        + ChatColor.YELLOW
                        + this.randomStuffDisplayName(reward)
                        + ChatColor.GRAY
                        + " (1 item, 20 detik)"
                );
                recipient.playSound(recipient.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8F, 1.25F);
            }

            this.announce(state, ChatColor.GOLD + "Random Stuff muncul untuk " + recipients + " pemain!");
        }
    }

    private void removeRandomStuffItems(Player player, MatchState state) {
        String token = state.randomStuffTokens.get(player.getUniqueId());
        if (token != null) {
            PlayerInventory inventory = player.getInventory();

            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.hasItemMeta()) {
                    String found = item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(this.randomStuffKey, PersistentDataType.STRING);
                    if (token.equals(found)) {
                        inventory.setItem(slot, null);
                    }
                }
            }

            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Item dropped && dropped.getItemStack().hasItemMeta()) {
                        String found = dropped.getItemStack()
                            .getItemMeta()
                            .getPersistentDataContainer()
                            .get(this.randomStuffKey, PersistentDataType.STRING);
                        if (token.equals(found)) {
                            dropped.remove();
                        }
                    }
                }
            }
        }
    }

    private void scheduleRandomStuffGroundRemoval(Item dropped) {
        if (dropped != null && dropped.isValid()) {
            long lifetimeSeconds = Math.max(
                1, Math.min(30, this.getConfig().getInt("matches.random-stuff-ground-expire-seconds", 5))
            );
            long expiresAt = System.currentTimeMillis() + lifetimeSeconds * 1000L;
            dropped.getPersistentDataContainer()
                .set(this.randomStuffDropExpiresKey, PersistentDataType.LONG, expiresAt);
            this.scheduleRandomStuffGroundRemoval(dropped.getUniqueId(), expiresAt);
        }
    }

    private void scheduleRandomStuffGroundRemoval(UUID entityId, long expiresAt) {
        long remainingMillis = expiresAt - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof Item) {
                entity.remove();
            }
        } else {
            long delayTicks = Math.max(1L, (remainingMillis + 49L) / 50L);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (Bukkit.getEntity(entityId) instanceof Item item && item.isValid()) {
                    item.remove();
                }
            }, delayTicks);
        }
    }

    boolean isRandomStuffItem(ItemStack item) {
        return item != null
            && item.hasItemMeta()
            && this.randomStuffKey != null
            && item.getItemMeta().getPersistentDataContainer().has(this.randomStuffKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRandomStuffDrop(PlayerDropItemEvent event) {
        Item dropped = event.getItemDrop();
        ItemStack stack = dropped.getItemStack();
        if (this.isRandomStuffItem(stack)) {
            String token = stack.getItemMeta()
                .getPersistentDataContainer()
                .get(this.randomStuffKey, PersistentDataType.STRING);
            UUID playerId = event.getPlayer().getUniqueId();
            boolean belongsToActiveMatch = this.matches
                .values()
                .stream()
                .anyMatch(state -> token.equals(state.randomStuffTokens.get(playerId)));
            if (!belongsToActiveMatch) {
                event.setCancelled(true);
            } else {
                this.scheduleRandomStuffGroundRemoval(dropped);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRandomStuffChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Item item) {
                Long expiresAt = item.getPersistentDataContainer()
                    .get(this.randomStuffDropExpiresKey, PersistentDataType.LONG);
                if (expiresAt != null) {
                    this.scheduleRandomStuffGroundRemoval(item.getUniqueId(), expiresAt);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRandomStuffUse(PlayerInteractEvent event) {
        EquipmentSlot hand = event.getHand();
        Player player = event.getPlayer();
        ItemStack heldItem = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        if (!this.isRandomStuffItem(heldItem) && this.isRandomStuffItem(event.getItem())) {
            heldItem = event.getItem();
        }

        if (this.isRandomStuffItem(heldItem)) {
            event.setCancelled(true);
            if (hand != EquipmentSlot.OFF_HAND) {
                Action action = event.getAction();
                if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                    this.activateRandomStuff(player, heldItem);
                }
            }
        }
    }

    private void activateRandomStuff(Player player, ItemStack heldItem) {
        if (this.isRandomStuffItem(heldItem)) {
            String token = heldItem.getItemMeta()
                .getPersistentDataContainer()
                .get(this.randomStuffKey, PersistentDataType.STRING);
            if (token != null) {
                ArenaData arena = this.findArena(player.getLocation());
                MatchState state = arena == null ? null : this.matches.get(arena.name);
                if (state != null
                    && state.running
                    && state.teams.containsKey(player.getUniqueId())
                    && token.equals(state.randomStuffTokens.get(player.getUniqueId()))) {
                    Material reward = heldItem.getType();
                    this.removeRandomStuffItems(player, state);
                    state.randomStuffTokens.remove(player.getUniqueId());
                    this.applyRandomStuffEffect(player, state, reward);
                }
            }
        }
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
            case GOLDEN_APPLE:
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1));
                break;
            case HONEY_BOTTLE:
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 240, 0));
                break;
            case ARROW:
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 1));
                break;
            case SHIELD:
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 0));
                break;
            case FIREWORK_ROCKET:
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, 0));
                break;
            case SNOWBALL:
                this.launchRandomStuffProjectile(player, "frost", false);
                break;
            case FIRE_CHARGE:
                this.launchRandomStuffProjectile(player, "ember", true);
                break;
            case ENDER_PEARL:
                this.launchRandomStuffProjectile(player, "rift", false);
                break;
            case IRON_SWORD:
                this.useRandomStuffDuelistBlade(player, state);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Efek Random Stuff tidak dikenali.");
                return;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.5F);
        player.sendMessage(ChatColor.GREEN + this.randomStuffDisplayName(reward) + " digunakan!");
    }

    private void launchRandomStuffProjectile(Player player, String projectileType, boolean fireball) {
        Projectile projectile;
        if (fireball) {
            SmallFireball launched = player.launchProjectile(SmallFireball.class);
            launched.setIsIncendiary(false);
            launched.setYield(0.0F);
            launched.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(1.15));
            projectile = launched;
        } else {
            Snowball launched = player.launchProjectile(Snowball.class);
            launched.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(1.45));
            projectile = launched;
        }

        projectile.getPersistentDataContainer()
            .set(this.randomStuffProjectileKey, PersistentDataType.STRING, projectileType);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (projectile.isValid()) {
                projectile.remove();
            }
        }, 80L);
        player.getWorld()
            .playSound(
                player.getLocation(), fireball ? Sound.ENTITY_BLAZE_SHOOT : Sound.ENTITY_SNOWBALL_THROW, 0.8F, 1.1F
            );
    }

    private void useRandomStuffDuelistBlade(Player player, MatchState state) {
        Vector direction = player.getEyeLocation().getDirection().normalize();
        RayTraceResult result = player.getWorld()
            .rayTrace(
                player.getEyeLocation(),
                direction,
                4.0,
                FluidCollisionMode.NEVER,
                true,
                0.75,
                entity -> entity instanceof Player targetx && this.isRandomStuffEnemy(player, targetx, state)
            );
        if (result != null && result.getHitEntity() instanceof Player target) {
            target.damage(4.5, player);
            target.getWorld()
                .spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 8, 0.25, 0.35, 0.25, 0.08);
            player.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.9F, 1.0F);
        } else {
            player.sendMessage(ChatColor.GRAY + "Duelist Blade tidak mengenai lawan.");
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7F, 1.2F);
        }
    }

    private boolean isRandomStuffEnemy(Player shooter, Player target, MatchState expectedState) {
        if (shooter != null
            && target != null
            && shooter.isOnline()
            && target.isOnline()
            && !shooter.isDead()
            && !target.isDead()
            && !shooter.getUniqueId().equals(target.getUniqueId())) {
            ArenaData shooterArena = this.findArena(shooter.getLocation());
            ArenaData targetArena = this.findArena(target.getLocation());
            if (shooterArena != null && targetArena != null && shooterArena.name.equals(targetArena.name)) {
                MatchState state = this.matches.get(shooterArena.name);
                if (state != null && state == expectedState && state.running) {
                    String shooterTeam = state.teams.get(shooter.getUniqueId());
                    String targetTeam = state.teams.get(target.getUniqueId());
                    return shooterTeam != null && targetTeam != null && !shooterTeam.equals(targetTeam);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private void applyRandomStuffImpact(
        Player shooter, Location impact, String projectileType, Player directTarget, MatchState state
    ) {
        if (shooter != null && state != null && impact != null && impact.getWorld() != null) {
            switch (projectileType) {
                case "frost":
                    if (directTarget != null && this.isRandomStuffEnemy(shooter, directTarget, state)) {
                        directTarget.damage(3.0, shooter);
                        directTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                        directTarget.getWorld()
                            .spawnParticle(
                                Particle.SNOWFLAKE,
                                directTarget.getLocation().add(0.0, 1.0, 0.0),
                                10,
                                0.25,
                                0.35,
                                0.25,
                                0.02
                            );
                    }

                    impact.getWorld().playSound(impact, Sound.BLOCK_GLASS_BREAK, 0.65F, 1.7F);
                    break;
                case "ember":
                    this.damageRandomStuffRadius(shooter, state, impact, 1.65, 4.75, 0);
                    impact.getWorld()
                        .spawnParticle(Particle.FLAME, impact.clone().add(0.0, 0.15, 0.0), 22, 0.45, 0.35, 0.45, 0.025);
                    impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.75F, 1.35F);
                    break;
                case "rift":
                    this.damageRandomStuffRadius(shooter, state, impact, 2.25, 3.5, 1);
                    impact.getWorld()
                        .spawnParticle(Particle.PORTAL, impact.clone().add(0.0, 0.5, 0.0), 28, 0.65, 0.45, 0.65, 0.15);
                    impact.getWorld().playSound(impact, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7F, 1.25F);
            }
        }
    }

    private void damageRandomStuffRadius(
        Player shooter, MatchState state, Location center, double radius, double damage, int knockbackLevel
    ) {
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Player target
                && this.isRandomStuffEnemy(shooter, target, state)
                && !(target.getLocation().distanceSquared(center) > radius * radius)) {
                target.damage(damage, shooter);
                if (knockbackLevel > 0) {
                    Vector push = target.getLocation().toVector().subtract(center.toVector());
                    if (push.lengthSquared() > 1.0E-6) {
                        push.normalize().multiply(0.28).setY(0.12);
                        target.setVelocity(target.getVelocity().add(push));
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffProjectileDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Projectile projectile
            && projectile.getPersistentDataContainer().has(this.randomStuffProjectileKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRandomStuffProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String projectileType = projectile.getPersistentDataContainer()
            .get(this.randomStuffProjectileKey, PersistentDataType.STRING);
        if (projectileType != null) {
            Player shooter = projectile.getShooter() instanceof Player player ? player : null;
            Location impact = projectile.getLocation().clone();
            projectile.remove();
            if (shooter != null && shooter.isOnline()) {
                ArenaData arena = this.findArena(shooter.getLocation());
                MatchState state = arena == null ? null : this.matches.get(arena.name);
                if (state != null && state.running && state.teams.containsKey(shooter.getUniqueId())) {
                    Player directTarget = event.getHitEntity() instanceof Player player ? player : null;
                    this.applyRandomStuffImpact(shooter, impact, projectileType, directTarget, state);
                }
            }
        }
    }

    private boolean playerUsesRandomStuffInMainHand(Player player) {
        return player != null && this.isRandomStuffItem(player.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffEntityUse(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack held = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        if (this.isRandomStuffItem(held)) {
            event.setCancelled(true);
            if (hand != EquipmentSlot.OFF_HAND) {
                this.activateRandomStuff(player, held);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffBlockDamage(BlockDamageEvent event) {
        if (this.playerUsesRandomStuffInMainHand(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffBlockBreak(BlockBreakEvent event) {
        if (this.playerUsesRandomStuffInMainHand(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && this.playerUsesRandomStuffInMainHand(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffConsume(PlayerItemConsumeEvent event) {
        if (this.isRandomStuffItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void suppressRandomStuffProjectile(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && this.playerUsesRandomStuffInMainHand(player)) {
            event.setCancelled(true);
        }
    }

    void giveClassLoadout(Player player, String className) {
        player.setGameMode(GameMode.SURVIVAL);
        String selected = className == null ? "knight" : className.toLowerCase(Locale.ROOT);
        if (selected.equals("assasin")) {
            selected = "assassin";
        }

        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(selected.equals("tank") ? 28.0 : 20.0);
            player.setHealth(maxHealth.getValue());
        }

        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        switch (selected) {
            case "archer":
                player.getInventory().setItem(0, new ItemStack(Material.BOW));
                player.getInventory().setItem(1, new ItemStack(Material.ARROW, 48));
                player.getInventory().setItem(3, new ItemStack(Material.STONE_SWORD));
                player.getInventory()
                    .setArmorContents(
                        new ItemStack[]{
                            new ItemStack(Material.LEATHER_BOOTS),
                            new ItemStack(Material.LEATHER_LEGGINGS),
                            new ItemStack(Material.LEATHER_CHESTPLATE),
                            new ItemStack(Material.LEATHER_HELMET)
                        }
                    );
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory()
                    .setItem(
                        2,
                        this.classSkillItem(
                            Material.FEATHER,
                            "archer_volley",
                            ChatColor.GREEN + "Volley",
                            "Lepaskan tiga panah sekaligus",
                            "Cooldown: 20 detik | Membutuhkan 3 panah"
                        )
                    );
                player.getInventory()
                    .setItem(
                        7,
                        this.classSkillItem(
                            Material.SPECTRAL_ARROW,
                            "archer_pinning_shot",
                            ChatColor.YELLOW + "Pinning Shot",
                            "Panah berikutnya memperlambat lawan 2,5 detik",
                            "Persiapan 10 detik | Cooldown 18 detik setelah panah dilepaskan"
                        )
                    );
                break;
            case "sniper":
                player.getInventory().setItem(0, new ItemStack(Material.CROSSBOW));
                ItemStack harmingArrows = new ItemStack(Material.TIPPED_ARROW, 32);
                PotionMeta arrowMeta = (PotionMeta)harmingArrows.getItemMeta();
                arrowMeta.setBasePotionType(PotionType.HARMING);
                arrowMeta.setDisplayName(ChatColor.DARK_RED + "Arrow of Harming I");
                harmingArrows.setItemMeta(arrowMeta);
                player.getInventory().setItem(1, harmingArrows);
                player.getInventory().setItem(3, new ItemStack(Material.STONE_SWORD));
                player.getInventory().setItem(4, new ItemStack(Material.SPYGLASS));
                player.getInventory()
                    .setArmorContents(
                        new ItemStack[]{
                            new ItemStack(Material.LEATHER_BOOTS),
                            new ItemStack(Material.CHAINMAIL_LEGGINGS),
                            new ItemStack(Material.CHAINMAIL_CHESTPLATE),
                            new ItemStack(Material.LEATHER_HELMET)
                        }
                    );
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 6));
                player.getInventory()
                    .setItem(
                        2,
                        this.classSkillItem(
                            Material.CLOCK,
                            "sniper_mark",
                            ChatColor.AQUA + "Mark Target",
                            "Tandai lawan yang kamu bidik",
                            "Glowing + menerima 20% damage tambahan selama 8 detik",
                            "Tekan Q untuk AIM LOCK | Hapus setelah 3 panah sniper mengenai target",
                            "Jarak 32 blok | Cooldown: 25 detik"
                        )
                    );
                player.getInventory()
                    .setItem(
                        7,
                        this.classSkillItem(
                            Material.AMETHYST_SHARD,
                            "sniper_focus_shot",
                            ChatColor.LIGHT_PURPLE + "Focus Mode",
                            "Semua panah +15% damage dan lebih cepat",
                            "Durasi: 8 detik | Cooldown: 25 detik"
                        )
                    );
                break;
            case "tank":
                player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                player.getInventory().addItem(new ItemStack(Material.SHIELD));
                player.getInventory()
                    .setArmorContents(
                        new ItemStack[]{
                            new ItemStack(Material.DIAMOND_BOOTS),
                            new ItemStack(Material.DIAMOND_LEGGINGS),
                            new ItemStack(Material.DIAMOND_CHESTPLATE),
                            new ItemStack(Material.DIAMOND_HELMET)
                        }
                    );
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory()
                    .setItem(
                        2,
                        this.classSkillItem(
                            Material.IRON_BLOCK,
                            "tank_endurance",
                            ChatColor.GOLD + "Endurance",
                            "Resistance III selama 5 detik",
                            "Gerak melambat | Cooldown: 45 detik"
                        )
                    );
                player.getInventory()
                    .setItem(
                        7,
                        this.classSkillItem(
                            Material.ANVIL,
                            "tank_ground_slam",
                            ChatColor.GOLD + "Ground Slam",
                            "Gelombang kejut area radius 5 blok",
                            "Knockback + Slowness II selama 2 detik | Cooldown: 30 detik"
                        )
                    );
                break;
            case "assassin":
                player.getInventory().setItem(0, this.createAssassinBlade());
                player.getInventory().setItemInOffHand(this.createAssassinBlade());
                player.getInventory().setItem(1, new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory()
                    .setArmorContents(
                        new ItemStack[]{
                            new ItemStack(Material.LEATHER_BOOTS),
                            new ItemStack(Material.LEATHER_LEGGINGS),
                            new ItemStack(Material.LEATHER_CHESTPLATE),
                            new ItemStack(Material.LEATHER_HELMET)
                        }
                    );
                player.getInventory()
                    .setItem(
                        2,
                        this.classSkillItem(
                            Material.SUGAR,
                            "assassin_burst",
                            ChatColor.DARK_RED + "Burst",
                            "Speed III + Haste II selama 3 detik",
                            "Strength II selama 2 detik | Cooldown: 30 detik"
                        )
                    );
                player.getInventory()
                    .setItem(
                        7,
                        this.classSkillItem(
                            Material.FERMENTED_SPIDER_EYE,
                            "assassin_venom_edge",
                            ChatColor.DARK_GREEN + "Venom Edge",
                            "Serangan melee berikutnya memberi Poison II",
                            "Berlaku 8 detik | Cooldown: 22 detik"
                        )
                    );
                break;
            case "ninja":
                player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
                player.getInventory().addItem(new ItemStack(Material.SNOWBALL, 7));
                player.getInventory()
                    .setArmorContents(
                        new ItemStack[]{
                            new ItemStack(Material.LEATHER_BOOTS),
                            new ItemStack(Material.LEATHER_LEGGINGS),
                            new ItemStack(Material.LEATHER_CHESTPLATE),
                            new ItemStack(Material.LEATHER_HELMET)
                        }
                    );
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory()
                    .setItem(
                        2,
                        this.classSkillItem(
                            Material.GRAY_DYE,
                            "ninja_vanish",
                            ChatColor.DARK_GREEN + "Smoke Bomb",
                            "Menghilang bersama armor dan item",
                            "Durasi: 6 detik | Cooldown: 34 detik"
                        )
                    );
                player.getInventory()
                    .setItem(
                        3,
                        this.classSkillItem(
                            Material.FEATHER,
                            "ninja_shadow_step",
                            ChatColor.GREEN + "Shadow Step",
                            "Teleport aman ke arah pandangan",
                            "Jarak hingga 8 blok | Cooldown: 20 detik"
                        )
                    );
                break;
            case "boxer":
                player.getInventory()
                    .setArmorContents(
                        new ItemStack[]{
                            new ItemStack(Material.LEATHER_BOOTS),
                            new ItemStack(Material.CHAINMAIL_LEGGINGS),
                            new ItemStack(Material.CHAINMAIL_CHESTPLATE),
                            new ItemStack(Material.LEATHER_HELMET)
                        }
                    );
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory()
                    .setItem(
                        2,
                        this.classSkillItem(
                            Material.SLIME_BALL,
                            "boxer_rapid_jab",
                            ChatColor.LIGHT_PURPLE + "Rapid Jab",
                            "Tiga pukulan cepat ke arah depan",
                            "Total dasar 3,45 damage | Jarak 3,4 blok | Cooldown: 20 detik"
                        )
                    );
                player.getInventory()
                    .setItem(
                        7,
                        this.classSkillItem(
                            Material.FIREWORK_STAR,
                            "boxer_barrage",
                            ChatColor.LIGHT_PURPLE + "Barrage",
                            "Enam pukulan cepat pada satu target",
                            "Slow I | 8% peluang Weakness I | Cooldown: 28 detik"
                        )
                    );
                break;
            case "demoman":
                player.getInventory()
                    .setItem(
                        0,
                        this.classSkillItem(
                            Material.TNT_MINECART,
                            "demoman_primary_bomb",
                            ChatColor.RED + "Pocket Bomb",
                            "Lempar satu bom ke arah pandangan",
                            "Damage maksimum 3,4 | Radius 3 blok | Cooldown: 2 detik"
                        )
                    );
                player.getInventory()
                    .setArmorContents(
                        new ItemStack[]{
                            new ItemStack(Material.CHAINMAIL_BOOTS),
                            new ItemStack(Material.LEATHER_LEGGINGS),
                            new ItemStack(Material.CHAINMAIL_CHESTPLATE),
                            new ItemStack(Material.LEATHER_HELMET)
                        }
                    );
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory()
                    .setItem(
                        2,
                        this.classSkillItem(
                            Material.FIRE_CHARGE,
                            "demoman_dynamite",
                            ChatColor.RED + "Dynamite Burst",
                            "Luncurkan tiga bom menyebar satu per satu",
                            "Maksimum 4,8 damage per bom | Knockback rendah | Jeda 0,2 detik | Cooldown: 10 detik"
                        )
                    );
                player.getInventory()
                    .setItem(
                        7,
                        this.classSkillItem(
                            Material.TNT,
                            "demoman_blast_mine",
                            ChatColor.DARK_RED + "Blast Mine",
                            "Pasang penanda Creeper yang memicu saat lawan mendekat",
                            "Aktif 20 detik | Maksimal 1 ranjau | Cooldown: 20 detik"
                        )
                    );
                break;
            default:
                player.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
                player.getInventory().addItem(new ItemStack(Material.SHIELD));
                player.getInventory()
                    .setArmorContents(
                        new ItemStack[]{
                            new ItemStack(Material.CHAINMAIL_BOOTS),
                            new ItemStack(Material.CHAINMAIL_LEGGINGS),
                            new ItemStack(Material.CHAINMAIL_CHESTPLATE),
                            new ItemStack(Material.CHAINMAIL_HELMET)
                        }
                    );
                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory()
                    .setItem(
                        2,
                        this.classSkillItem(
                            Material.IRON_INGOT,
                            "knight_guard",
                            ChatColor.RED + "Power Strike",
                            "Serangan kuat ke lawan di depan",
                            "Damage tinggi | Cooldown: 26 detik"
                        )
                    );
                player.getInventory()
                    .setItem(
                        7,
                        this.classSkillItem(
                            Material.GOLDEN_APPLE,
                            "knight_rally",
                            ChatColor.AQUA + "Rally",
                            "Beri Resistance I kepada rekan tim dalam radius 8 blok",
                            "Durasi 4 detik | Cooldown: 32 detik"
                        )
                    );
        }

        this.applyClassEffects(player, selected);
        this.markKitItemsUnbreakable(player);
        if (selected.equals("assassin")) {
            player.getInventory().setHeldItemSlot(0);
        }
    }

    void applyClassEffects(Player player, String className) {
        String selected = className == null ? "knight" : className.toLowerCase(Locale.ROOT);
        if (selected.equals("assasin")) {
            selected = "assassin";
        }

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(selected.equals("tank") ? 28.0 : 20.0);
            player.setHealth(maxHealth.getValue());
        }

        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) {
            double classAttackSpeed = selected.equals("assassin") ? 5.5 : (selected.equals("boxer") ? 6.0 : 4.0);
            attackSpeed.setBaseValue(classAttackSpeed);
        }

        switch (selected) {
            case "sniper":
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 72000, 0, false, false, true));
                break;
            case "assassin":
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 72000, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 72000, 1, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 72000, 0, false, false, true));
                break;
            case "ninja":
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 72000, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 72000, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 72000, 1, false, false, true));
                break;
            case "boxer":
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 72000, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 72000, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 72000, 0, false, false, true));
        }
    }

    void giveRelicRunnerLoadout(Player player, String team) {
        player.setGameMode(GameMode.SURVIVAL);

        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0);
            player.setHealth(maxHealth.getValue());
        }

        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) {
            attackSpeed.setBaseValue(4.0);
        }

        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItem(0, new ItemStack(Material.STONE_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.SHIELD));
        player.getInventory().setItem(8, new ItemStack(Material.BREAD, 8));
        Color tint = team.equals("red") ? Color.fromRGB(220, 55, 55) : Color.fromRGB(65, 105, 220);
        player.getInventory()
            .setArmorContents(
                new ItemStack[]{
                    this.dyedLeather(Material.LEATHER_BOOTS, tint),
                    this.dyedLeather(Material.LEATHER_LEGGINGS, tint),
                    this.dyedLeather(Material.LEATHER_CHESTPLATE, tint),
                    this.dyedLeather(Material.LEATHER_HELMET, tint)
                }
            );
        this.markKitItemsUnbreakable(player);
        player.sendMessage(ChatColor.GOLD + "Kit Relic Runner: pedang batu, shield, 8 roti, dan armor tim.");
    }

    ItemStack dyedLeather(Material material, Color tint) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta leatherMeta) {
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
            meta.setLore(
                List.of(
                    ChatColor.GRAY + "Klik kanan untuk membuka menu class",
                    ChatColor.DARK_GRAY + "Hanya aktif selama persiapan"
                )
            );
            meta.getPersistentDataContainer().set(this.classSelectorKey, PersistentDataType.BYTE, (byte)1);
            selector.setItemMeta(meta);
        }

        player.getInventory().setItem(4, selector);
    }

    boolean isClassSelector(ItemStack item) {
        if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            return meta != null
                && meta.getPersistentDataContainer().has(this.classSelectorKey, PersistentDataType.BYTE);
        } else {
            return false;
        }
    }

    void openClassMenuFor(Player player) {
        for (MatchState state : this.matches.values()) {
            if (state.preparing && state.teams.containsKey(player.getUniqueId())) {
                if (!state.mode.equals("tdm")) {
                    player.sendMessage(
                        ChatColor.YELLOW
                            + "Relic Rush tidak menggunakan class TDM. Semua pemain memakai Relic Runner kit."
                    );
                    return;
                }

                ArenaData arena = this.arenas.get(state.arenaName);
                if (arena != null) {
                    this.openClassMenu(player, arena, state);
                }

                return;
            }
        }

        player.sendMessage(
            ChatColor.RED
                + "Menu class hanya tersedia di lobby persiapan. Jalankan /arena join lalu tunggu admin menjalankan /arena start."
        );
    }

    void openClassMenu(Player player, ArenaData arena, MatchState state) {
        this.openClassMenu(player, arena, state, 0);
    }

    void openClassMenu(Player player, ArenaData arena, MatchState state, int requestedPage) {
        int page = Math.max(0, Math.min(1, requestedPage));
        ClassMenuHolder holder = new ClassMenuHolder(arena.name, page);
        Inventory menu = Bukkit.createInventory(
            holder, 27, ChatColor.DARK_AQUA + "Pilih Class " + ChatColor.GRAY + "(" + (page + 1) + "/2)"
        );
        holder.inventory = menu;
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(ChatColor.DARK_GRAY + " ");
            filler.setItemMeta(fillerMeta);
        }

        for (int slot = 0; slot < menu.getSize(); slot++) {
            menu.setItem(slot, filler);
        }

        int[] classSlots = new int[]{10, 12, 14, 16};
        String[] classIds = page == 0
            ? new String[]{"knight", "tank", "archer", "sniper"}
            : new String[]{"assassin", "ninja", "boxer", "demoman"};

        for (int i = 0; i < classIds.length; i++) {
            String classId = classIds[i];
            int slot = classSlots[i];
            holder.classBySlot.put(slot, classId);
            menu.setItem(slot, this.classMenuCard(classId));
        }

        menu.setItem(
            18,
            page > 0
                ? this.classMenuNavigationItem(
                    Material.ARROW, ChatColor.YELLOW + "Halaman Sebelumnya", "Kembali ke halaman class sebelumnya"
                )
                : this.classMenuNavigationItem(
                    Material.BARRIER,
                    ChatColor.DARK_GRAY + "Tidak Ada Halaman Sebelumnya",
                    "Kamu sedang di halaman pertama"
                )
        );
        menu.setItem(
            22,
            this.classMenuNavigationItem(
                Material.BOOK,
                ChatColor.AQUA + "Halaman " + (page + 1) + " dari 2",
                page == 0 ? "Class dasar" : "Class spesialis"
            )
        );
        menu.setItem(
            26,
            page < 1
                ? this.classMenuNavigationItem(
                    Material.ARROW,
                    ChatColor.GREEN + "Next — Class Lainnya",
                    "Klik untuk melihat Assassin, Ninja, Boxer, dan Demoman"
                )
                : this.classMenuNavigationItem(
                    Material.BARRIER,
                    ChatColor.DARK_GRAY + "Tidak Ada Halaman Berikutnya",
                    "Kamu sedang di halaman terakhir"
                )
        );
        player.openInventory(menu);
    }

    void openClassMenuPage(Player player, String arenaName, int requestedPage) {
        if (requestedPage >= 0 && requestedPage <= 1) {
            for (MatchState state : this.matches.values()) {
                if (state.preparing
                    && state.teams.containsKey(player.getUniqueId())
                    && state.arenaName.equalsIgnoreCase(arenaName)) {
                    if (!"tdm".equals(state.mode)) {
                        player.sendMessage(ChatColor.YELLOW + "Menu class hanya tersedia pada Team Deathmatch.");
                        return;
                    }

                    ArenaData arena = this.arenas.get(state.arenaName.toLowerCase(Locale.ROOT));
                    if (arena != null) {
                        this.openClassMenu(player, arena, state, requestedPage);
                    }

                    return;
                }
            }

            player.sendMessage(ChatColor.RED + "Sesi pemilihan class sudah tidak tersedia.");
        }
    }

    private ItemStack classMenuCard(String classId) {
        return switch (classId) {
            case "knight" -> this.classMenuItem(
                Material.IRON_SWORD,
                ChatColor.RED + "Knight",
                "Frontliner seimbang",
                "Pedang besi, shield, armor chainmail",
                "20 HP | Damage dan pertahanan seimbang",
                "Skill: Power Strike + Rally"
            );
            case "tank" -> this.classMenuItem(
                Material.DIAMOND_CHESTPLATE,
                ChatColor.GOLD + "Tank",
                "Benteng berjalan",
                "Full diamond, shield, pedang kayu",
                "+4 hati | Serangan lebih lemah",
                "Skill: Endurance + Ground Slam"
            );
            case "archer" -> this.classMenuItem(
                Material.BOW,
                ChatColor.GREEN + "Archer",
                "Penyerang jarak menengah",
                "Bow, 48 panah, pedang batu",
                "Armor kulit | Memberi cover untuk tim",
                "Skill: Volley + Pinning Shot"
            );
            case "sniper" -> this.classMenuItem(
                Material.CROSSBOW,
                ChatColor.AQUA + "Sniper",
                "Penembak jarak jauh",
                "Crossbow, 32 panah, spyglass",
                "Spesialis serangan presisi jarak jauh",
                "Skill: Mark Target + Focus Mode"
            );
            case "assassin" -> this.classMenuItem(
                Material.GOLDEN_SWORD,
                ChatColor.DARK_RED + "Assassin",
                "Penyerang cepat",
                "Pedang emas dan armor kulit",
                "Haste I + Speed II + Jump Boost I",
                "Skill: Burst + Venom Edge"
            );
            case "ninja" -> this.classMenuItem(
                Material.ENDER_PEARL,
                ChatColor.DARK_GREEN + "Ninja",
                "Pengacau lincah",
                "Pedang batu, 7 snowball, armor kulit",
                "Mobilitas dan stealth",
                "Skill: Smoke Bomb + Shadow Step"
            );
            case "boxer" -> this.classMenuItem(
                Material.LEATHER_CHESTPLATE,
                ChatColor.LIGHT_PURPLE + "Boxer",
                "Petarung tangan kosong",
                "Rangkaian pukulan jarak dekat",
                "Strength I + Speed I + Haste I",
                "Skill: Rapid Jab + Barrage"
            );
            case "demoman" -> this.classMenuItem(
                Material.TNT,
                ChatColor.RED + "Demoman",
                "Spesialis pengendali area",
                "Pocket Bomb + armor campuran",
                "Ledakan khusus tanpa merusak terrain",
                "Skill: Dynamite Burst + Blast Mine"
            );
            default -> this.classMenuItem(
                Material.BARRIER, ChatColor.RED + "Class tidak ditemukan", "Silakan laporkan masalah ini kepada admin"
            );
        };
    }

    private ItemStack classMenuNavigationItem(Material material, String name, String... lore) {
        return this.classMenuItem(material, name, lore);
    }

    ItemStack classMenuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lines = new ArrayList<>();

            for (String line : lore) {
                lines.add(ChatColor.GRAY + line);
            }

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

            for (String line : lore) {
                lines.add(ChatColor.GRAY + line);
            }

            meta.setLore(lines);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(this.classSkillKey, PersistentDataType.STRING, skillId);
            item.setItemMeta(meta);
        }

        return item;
    }

    String readClassSkill(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            return meta == null
                ? null
                : meta.getPersistentDataContainer().get(this.classSkillKey, PersistentDataType.STRING);
        } else {
            return null;
        }
    }

    private ItemStack createAssassinBlade() {
        ItemStack blade = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta meta = blade.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Assassin's Twin Blade");
            meta.setLore(
                List.of(
                    ChatColor.GRAY + "Main-hand: serangan normal",
                    ChatColor.GRAY + "Right-click: serangan off-hand",
                    ChatColor.DARK_GRAY + "Off-hand: 2.0 damage | cooldown 12 tick"
                )
            );
            meta.setUnbreakable(true);
            meta.setItemModel(new NamespacedKey(this, "assassin_dagger"));
            meta.getPersistentDataContainer().set(this.assassinBladeKey, PersistentDataType.BYTE, (byte)1);
            blade.setItemMeta(meta);
        }

        return blade;
    }

    boolean isAssassinBlade(ItemStack item) {
        if (item != null
            && item.getType() == Material.GOLDEN_SWORD
            && item.hasItemMeta()
            && this.assassinBladeKey != null) {
            ItemMeta meta = item.getItemMeta();
            return meta != null
                && meta.getPersistentDataContainer().has(this.assassinBladeKey, PersistentDataType.BYTE);
        } else {
            return false;
        }
    }

    void markKitItemsUnbreakable(Player player) {
        PlayerInventory inventory = player.getInventory();

        for (ItemStack item : inventory.getContents()) {
            this.markItemUnbreakable(item);
        }

        for (ItemStack item : inventory.getArmorContents()) {
            this.markItemUnbreakable(item);
        }

        this.markItemUnbreakable(inventory.getItemInOffHand());
    }

    private void markItemUnbreakable(ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && !meta.isUnbreakable()) {
                meta.setUnbreakable(true);
                item.setItemMeta(meta);
            }
        }
    }

    void selectClassByName(Player player, String input) {
        String selected = input.toLowerCase(Locale.ROOT);
        if (selected.equals("assasin")) {
            selected = "assassin";
        }

        if (!Set.of("knight", "archer", "sniper", "tank", "assassin", "ninja", "boxer", "demoman").contains(selected)) {
            player.sendMessage(
                ChatColor.YELLOW + "Class tersedia: knight, tank, archer, sniper, assassin, ninja, boxer, demoman."
            );
        } else {
            for (MatchState state : this.matches.values()) {
                if (state.preparing && state.teams.containsKey(player.getUniqueId())) {
                    if (!state.mode.equals("tdm")) {
                        player.sendMessage(ChatColor.YELLOW + "Pemilihan class hanya tersedia pada Team Deathmatch.");
                        return;
                    }

                    state.selectedClasses.put(player.getUniqueId(), selected);
                    this.updateMatchHud(state);
                    player.sendMessage(
                        ChatColor.GREEN + "Class kamu dipilih: " + selected.toUpperCase(Locale.ROOT) + "."
                    );
                    player.closeInventory();
                    return;
                }
            }

            player.sendMessage(ChatColor.RED + "Kamu hanya dapat memilih class di lobby persiapan.");
        }
    }

    void stopMatch(CommandSender sender, String arenaName, String reason) {
        String name = arenaName.toLowerCase(Locale.ROOT);
        if (!this.buildingArenas.contains(name) && !this.hasActiveGenerationTasks(name)) {
            ArenaData arena = this.arenas.get(name);
            MatchState state = this.matches.get(name);
            if (arena != null && state != null) {
                this.announce(state, ChatColor.YELLOW + reason);
                this.stopAndRestoreMatch(
                    arena.name,
                    () -> sender.sendMessage(ChatColor.GREEN + "Pertandingan dihentikan dan perubahan blok dipulihkan.")
                );
            } else {
                sender.sendMessage(ChatColor.RED + "Tidak ada pertandingan untuk arena itu.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Arena sedang diproses atau bloknya sedang dipulihkan.");
        }
    }

    void finishMatch(MatchState state, ArenaData arena, String reason) {
        if (state.running) {
            state.running = false;
            if (state.timer != null) {
                state.timer.cancel();
            }

            List<String> configuredTeams = this.teamNamesFor(arena);
            int highestScore = configuredTeams.stream()
                .mapToInt(team -> state.teamScores.getOrDefault(team, 0))
                .max()
                .orElse(0);
            List<String> winners = configuredTeams.stream()
                .filter(team -> state.teamScores.getOrDefault(team, 0) == highestScore)
                .toList();
            String result = state.singlePlayerMode
                ? ""
                : (winners.size() == 1 ? "Tim " + winners.get(0).toUpperCase(Locale.ROOT) + " menang!" : "Seri!");
            this.announce(
                state,
                ChatColor.GOLD
                    + "Pertandingan selesai. "
                    + reason
                    + (result.isEmpty() ? "" : " " + result)
                    + " Skor: "
                    + this.scoreSummary(state, configuredTeams)
                    + "."
            );
            if (state.mode.equals("tdm") && !state.singlePlayerMode) {
                for (UUID uuid : state.teams.keySet()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        String playerTeam = state.teams.get(uuid);
                        if (winners.size() > 1) {
                            player.sendTitle(
                                ChatColor.GOLD + "ARENA\n" + ChatColor.YELLOW + "DRAW",
                                ChatColor.WHITE + "Match tied!",
                                5,
                                75,
                                15
                            );
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9F, 1.0F);
                        } else if (winners.contains(playerTeam)) {
                            player.sendTitle(
                                ChatColor.GOLD + "ARENA\n" + ChatColor.GREEN + "STREAK!",
                                ChatColor.WHITE + "Your team won!",
                                5,
                                85,
                                20
                            );
                            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0F, 1.0F);
                        } else {
                            player.sendTitle(
                                ChatColor.GOLD + "ARENA\n" + ChatColor.DARK_RED + "DOWN...",
                                ChatColor.WHITE + "Your team lost!",
                                5,
                                85,
                                20
                            );
                            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 0.85F);
                        }
                    }
                }
            }

            this.stopAndRestoreMatch(arena.name, () -> {
                for (UUID uuidx : new ArrayList<>(state.teams.keySet())) {
                    Player playerx = Bukkit.getPlayer(uuidx);
                    if (playerx != null) {
                        playerx.sendMessage(ChatColor.GRAY + "State arena telah dikembalikan ke awal pertandingan.");
                    }
                }
            });
        }
    }

    String scoreSummary(MatchState state, List<String> teams) {
        List<String> parts = new ArrayList<>();

        for (String team : teams) {
            parts.add(
                this.teamColor(team)
                    + team.toUpperCase(Locale.ROOT)
                    + ChatColor.WHITE
                    + " "
                    + state.teamScores.getOrDefault(team, 0)
            );
        }

        return String.join(ChatColor.DARK_GRAY + " | ", parts);
    }

    void addScore(MatchState state, ArenaData arena, String team, int amount) {
        int updated = state.teamScores.merge(team, amount, Integer::sum);
        if (team.equals("red")) {
            state.redScore = updated;
        }

        if (team.equals("blue")) {
            state.blueScore = updated;
        }

        this.updateMatchHud(state);
        this.announce(state, ChatColor.YELLOW + "Skor: " + this.scoreSummary(state, this.teamNamesFor(arena)) + ".");
        int target = state.mode.equals("relic_rush")
            ? this.getConfig().getInt("matches.relic-target-score", 3)
            : this.getConfig().getInt("matches.tdm-target-score", 20);
        if (updated >= target) {
            this.finishMatch(state, arena, "Target skor tercapai.");
        }
    }

    void spawnRelic(ArenaData arena, MatchState state) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world != null && state.relicToken != null) {
            state.relicCarrier = null;
            state.relicDropped = false;
            ItemStack stack = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "Relic " + arena.name);
                meta.getPersistentDataContainer().set(this.relicKey, PersistentDataType.STRING, state.relicToken);
                stack.setItemMeta(meta);
            }

            int relicFloorY = world.getHighestBlockYAt(arena.centerX, arena.centerZ + 2);
            Item relicItem = world.dropItem(
                new Location(world, arena.centerX + 0.5, relicFloorY + 1.15, arena.centerZ + 2.5), stack
            );
            relicItem.setPickupDelay(Integer.MAX_VALUE);
        }
    }

    boolean isRelic(ItemStack item, MatchState state) {
        if (item != null
            && state != null
            && state.relicToken != null
            && item.getType() == Material.NETHER_STAR
            && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            String token = meta.getPersistentDataContainer().get(this.relicKey, PersistentDataType.STRING);
            return state.relicToken.equals(token);
        } else {
            return false;
        }
    }

    void consumeHandItem(Player player, EquipmentSlot hand) {
        ItemStack item = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        if (item.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            item.setAmount(item.getAmount() - 1);
        }
    }

    void removeRelicItems(ArenaData arena, MatchState state) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world != null && state != null) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (this.isRelic(item.getItemStack(), state)) {
                    item.remove();
                }
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (this.isRelic(stack, state)) {
                        player.getInventory().setItem(slot, null);
                    }
                }

                if (this.isRelic(player.getInventory().getItemInOffHand(), state)) {
                    player.getInventory().setItemInOffHand(null);
                }
            }

            state.relicCarrier = null;
            state.relicDropped = false;
        }
    }

    void announce(MatchState state, String message) {
        for (UUID uuid : state.teams.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    void updateMatchHud(MatchState state) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            boolean preparationView = state.preparing || state.launching;
            boolean queueView = !state.running && !preparationView;
            int targetScore = state.mode.equals("relic_rush")
                ? this.getConfig().getInt("matches.relic-target-score", 3)
                : this.getConfig().getInt("matches.tdm-target-score", 20);

            for (UUID uuid : state.teams.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
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

                    this.refreshTeamMarkers(board, state);

                    for (String entry : new HashSet<>(board.getEntries())) {
                        board.resetScores(entry);
                    }

                    String team = state.teams.getOrDefault(uuid, "?").toUpperCase(Locale.ROOT);
                    String className = state.selectedClasses.getOrDefault(uuid, "knight").toUpperCase(Locale.ROOT);
                    List<String> lines = new ArrayList<>();
                    if (queueView) {
                        lines.add(ChatColor.GOLD + "TEAM ROSTER");
                        this.appendOccupiedTeamRoster(lines, state, this.arenas.get(state.arenaName));
                        lines.add(ChatColor.DARK_GRAY + "Menunggu start...");
                    } else if (preparationView) {
                        lines.add(
                            ChatColor.GRAY
                                + "Mulai: "
                                + ChatColor.YELLOW
                                + this.formatTime(state.preparationSecondsRemaining)
                        );
                        lines.add(ChatColor.GOLD + "ROSTER");
                        this.appendOccupiedTeamRoster(lines, state, this.arenas.get(state.arenaName));
                        if (state.mode.equals("tdm")) {
                            lines.add(ChatColor.GRAY + "Class: " + ChatColor.WHITE + className);
                        } else {
                            lines.add(ChatColor.GOLD + "Relic: " + ChatColor.WHITE + "Belum dimulai");
                        }
                    } else {
                        String mode = state.mode.replace('_', ' ').toUpperCase(Locale.ROOT);
                        lines.add(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + mode);
                        lines.add(
                            ChatColor.GRAY + "Waktu: " + ChatColor.YELLOW + this.formatTime(state.secondsRemaining)
                        );

                        for (String listedTeam : this.teamNamesFor(this.arenas.get(state.arenaName))) {
                            lines.add(
                                this.teamColor(listedTeam)
                                    + listedTeam.toUpperCase(Locale.ROOT)
                                    + ": "
                                    + ChatColor.WHITE
                                    + state.teamScores.getOrDefault(listedTeam, 0)
                                    + "/"
                                    + targetScore
                            );
                        }

                        lines.add(ChatColor.GRAY + "Tim: " + this.teamColor(team.toLowerCase(Locale.ROOT)) + team);
                        if (state.mode.equals("relic_rush")) {
                            lines.add(ChatColor.GOLD + "Misi: " + ChatColor.WHITE + "Antar Relic");
                            String relicStatus;
                            if (state.relicCarrier != null) {
                                Player carrier = Bukkit.getPlayer(state.relicCarrier);
                                String carrierTeam = state.teams
                                    .getOrDefault(state.relicCarrier, "?")
                                    .toUpperCase(Locale.ROOT);
                                relicStatus = carrier == null
                                    ? "Tidak diketahui"
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

                    if (lines.size() > 15) {
                        lines = new ArrayList<>(lines.subList(0, 15));
                    }

                    int score = lines.size();

                    for (String line : lines) {
                        objective.getScore(line).setScore(score--);
                    }
                }
            }

            this.refreshRelicGlow(state);
        }
    }

    private void appendOccupiedTeamRoster(List<String> lines, MatchState state, ArenaData arena) {
        for (String teamName : this.teamNamesFor(arena)) {
            ChatColor color = this.teamColor(teamName);
            List<String> names = new ArrayList<>();

            for (Entry<UUID, String> entry : state.teams.entrySet()) {
                if (teamName.equals(entry.getValue())) {
                    Player member = Bukkit.getPlayer(entry.getKey());
                    if (member != null && member.isOnline()) {
                        names.add(member.getName());
                    }
                }
            }

            if (!names.isEmpty()) {
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
                    }

                    if (!rowNames.isEmpty()) {
                        rowNames.append(", ");
                    }

                    rowNames.append(name);
                }

                if (!rowNames.isEmpty()) {
                    String prefix = row == 0 ? teamLabel + ": " : "↳ ";
                    lines.add(color + prefix + ChatColor.WHITE + rowNames);
                }
            }
        }
    }

    void refreshRelicGlow(MatchState state) {
        if (state != null && state.mode.equals("relic_rush")) {
            String carrierTeam = state.relicCarrier == null ? null : state.teams.get(state.relicCarrier);
            Player carrier = state.relicCarrier == null ? null : Bukkit.getPlayer(state.relicCarrier);

            for (UUID viewerId : state.teams.keySet()) {
                Player viewer = Bukkit.getPlayer(viewerId);
                Scoreboard board = state.hudScoreboards.get(viewerId);
                if (viewer != null && board != null) {
                    Team glowTeam = board.getTeam("arena_relic_glow");
                    if (glowTeam == null) {
                        glowTeam = board.registerNewTeam("arena_relic_glow");
                    }

                    for (String entry : new HashSet<>(glowTeam.getEntries())) {
                        glowTeam.removeEntry(entry);
                    }

                    if (carrier != null && carrier.isOnline()) {
                        boolean ally = state.teams.get(viewerId) != null
                            && state.teams.get(viewerId).equals(carrierTeam);
                        glowTeam.setColor(ally ? ChatColor.GREEN : ChatColor.RED);
                        glowTeam.setPrefix(
                            "red".equals(carrierTeam) ? ChatColor.RED + "[RED] " : ChatColor.BLUE + "[BLUE] "
                        );
                        glowTeam.addEntry(carrier.getName());
                    }
                }
            }

            for (UUID memberId : state.teams.keySet()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.setGlowing(carrier != null && memberId.equals(state.relicCarrier));
                }
            }
        }
    }

    void refreshTeamMarkers(Scoreboard board, MatchState state) {
        for (String teamName : List.of("red", "blue", "green", "yellow")) {
            Team scoreboardTeam = board.getTeam("arena_" + teamName);
            if (scoreboardTeam == null) {
                scoreboardTeam = board.registerNewTeam("arena_" + teamName);
            }

            this.configureTeamMarker(
                scoreboardTeam, this.teamColor(teamName), "[" + teamName.toUpperCase(Locale.ROOT) + "] "
            );

            for (Entry<UUID, String> entry : state.teams.entrySet()) {
                if (teamName.equals(entry.getValue())) {
                    Player member = Bukkit.getPlayer(entry.getKey());
                    if (member != null) {
                        scoreboardTeam.addEntry(member.getName());
                    }
                }
            }
        }
    }

    void configureTeamMarker(Team team, ChatColor color, String prefix) {
        team.setPrefix(color + prefix + ChatColor.RESET);
        team.setColor(color);
        team.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.ALWAYS);

        for (String entry : new HashSet<>(team.getEntries())) {
            team.removeEntry(entry);
        }
    }

    void clearMatchHud(MatchState state) {
        for (UUID uuid : state.teams.keySet()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) {
                member.setGlowing(false);
            }
        }

        for (Entry<UUID, Scoreboard> entry : state.previousScoreboards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.setScoreboard(entry.getValue());
            }
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
        if ("relic_rush".equalsIgnoreCase(arena.mode)) {
            int[] offset = RelicTeamLayout.offset(team, this.teamCountFor(arena), arena.spawnOffset);
            int x = arena.centerX + offset[0];
            int z = arena.centerZ + offset[1];
            float yaw = (float)Math.toDegrees(Math.atan2(offset[0], -offset[1]));
            return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1.0, z + 0.5, yaw, 0.0F);
        }

        int spawnOffset = arena.spawnOffset;

        Location spawn = switch (team.toLowerCase(Locale.ROOT)) {
            case "red" -> new Location(
                world, arena.centerX - spawnOffset + 0.5, arena.redY + 2.0, arena.centerZ + 0.5, -90.0F, 0.0F
            );
            case "blue" -> new Location(
                world, arena.centerX + spawnOffset + 0.5, arena.blueY + 2.0, arena.centerZ + 0.5, 90.0F, 0.0F
            );
            case "green" -> new Location(
                world,
                arena.centerX + 0.5,
                world.getHighestBlockYAt(arena.centerX, arena.centerZ - spawnOffset) + 1.0,
                arena.centerZ - spawnOffset + 0.5,
                180.0F,
                0.0F
            );
            case "yellow" -> new Location(
                world,
                arena.centerX + 0.5,
                world.getHighestBlockYAt(arena.centerX, arena.centerZ + spawnOffset) + 1.0,
                arena.centerZ + spawnOffset + 0.5,
                0.0F,
                0.0F
            );
            default -> new Location(world, arena.centerX + 0.5, arena.centerY + 2.0, arena.centerZ + 0.5);
        };
        return this.faceArenaCenter(spawn, arena);
    }

    void setProtectionFromCommand(
        CommandSender sender, String arenaName, String actionName, String[] args, int scopeIndex
    ) {
        if (this.requireAdmin(sender)) {
            if (args.length <= scopeIndex) {
                sender.sendMessage(
                    ChatColor.YELLOW
                        + "Gunakan: /arena "
                        + (arenaName == null ? "" : arenaName + " ")
                        + actionName
                        + " <op|@s|@a|namaPemain>"
                );
            } else {
                boolean allowed = actionName.equals("breakable") || actionName.equals("placeable");
                String action = !actionName.equals("breakable") && !actionName.equals("disbreakable")
                    ? "place"
                    : "break";
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

                    scope = "player." + ((Player)sender).getName().toLowerCase(Locale.ROOT);
                } else {
                    if (!scopeInput.matches("[A-Za-z0-9_]{1,16}")) {
                        sender.sendMessage(ChatColor.RED + "Target tidak valid. Gunakan op, @s, @a, atau nama pemain.");
                        return;
                    }

                    scope = "player." + scopeInput.toLowerCase(Locale.ROOT);
                }

                String path = arenaName == null
                    ? "protection.global." + action + "." + scope
                    : "protection.arenas." + arenaName + "." + action + "." + scope;
                this.getConfig().set(path, allowed);
                this.saveConfig();
                String targetLabel = scope.startsWith("player.") ? scope.substring("player.".length()) : scope;
                sender.sendMessage(
                    ChatColor.GREEN
                        + (allowed ? "Diizinkan" : "Dilarang")
                        + " melakukan "
                        + action
                        + " untuk "
                        + targetLabel
                        + (arenaName == null ? " di semua arena." : " di arena " + arenaName + ".")
                );
            }
        }
    }

    boolean isProtectionAction(String action) {
        return action.equals("breakable")
            || action.equals("disbreakable")
            || action.equals("placeable")
            || action.equals("displaceable");
    }

    boolean isPreparing(ArenaData arena) {
        MatchState state = this.matches.get(arena.name);
        return state != null && (state.preparing || state.launching);
    }

    boolean isMatchRunning(ArenaData arena) {
        MatchState state = this.matches.get(arena.name);
        return state != null && (state.running || state.preparing || state.launching);
    }

    boolean isActionAllowed(String action, ArenaData arena, Player player) {
        if (player.hasPermission("arenasystem.bypass")) {
            return true;
        }

        String playerScope = "player." + player.getName().toLowerCase(Locale.ROOT);
        String[] paths = new String[]{
            "protection.arenas." + arena.name + "." + action + "." + playerScope,
            "protection.arenas." + arena.name + "." + action + ".op",
            "protection.arenas." + arena.name + "." + action + ".all",
            "protection.global." + action + "." + playerScope,
            "protection.global." + action + ".op",
            "protection.global." + action + ".all"
        };

        for (int i = 0; i < paths.length; i++) {
            if ((i % 3 != 1 || player.isOp()) && this.getConfig().contains(paths[i])) {
                return this.getConfig().getBoolean(paths[i]);
            }
        }

        return false;
    }

    boolean hasExplicitProtectionRule(String action, ArenaData arena, Player player) {
        String playerScope = "player." + player.getName().toLowerCase(Locale.ROOT);
        String[] paths = new String[]{
            "protection.arenas." + arena.name + "." + action + "." + playerScope,
            "protection.arenas." + arena.name + "." + action + ".op",
            "protection.arenas." + arena.name + "." + action + ".all",
            "protection.global." + action + "." + playerScope,
            "protection.global." + action + ".op",
            "protection.global." + action + ".all"
        };

        for (int i = 0; i < paths.length; i++) {
            if ((i % 3 != 1 || player.isOp()) && this.getConfig().contains(paths[i])) {
                return true;
            }
        }

        return false;
    }

    void recordMatchBlock(ArenaData arena, Block block, String oldData) {
        MatchState match = this.matches.get(arena.name);
        if (match != null && match.running) {
            BlockPos pos = new BlockPos(
                block.getX() - arena.centerX, block.getY() - arena.centerY, block.getZ() - arena.centerZ
            );
            match.originalBlocks.putIfAbsent(pos, oldData);
        }
    }

    ArenaData findArenaFootprint(Location location) {
        if (location != null && location.getWorld() != null) {
            String worldName = location.getWorld().getName();
            int blockY = location.getBlockY();

            for (ArenaData arena : this.arenas.values()) {
                if (arena.worldName.equals(worldName)
                    && Math.abs(location.getBlockX() - arena.centerX) <= arena.radius
                    && Math.abs(location.getBlockZ() - arena.centerZ) <= arena.radius
                    && blockY >= arena.minSurfaceY - 20
                    && blockY <= arena.maxSurfaceY + 64) {
                    return arena;
                }
            }

            for (ArenaData arena : this.incompleteArenas.values()) {
                if (arena.worldName.equals(worldName)
                    && Math.abs(location.getBlockX() - arena.centerX) <= arena.radius
                    && Math.abs(location.getBlockZ() - arena.centerZ) <= arena.radius
                    && blockY >= arena.minSurfaceY - 20
                    && blockY <= arena.maxSurfaceY + 64) {
                    return arena;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    boolean touchesIncompleteArena(Location location) {
        return !this.incompleteArenas.isEmpty()
            && location != null
            && location.getWorld() != null
            && IncompleteArenaGuard.touches(
                this.incompleteArenas.values(),
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
            );
    }

    boolean touchesIncompleteArenaMovement(Location location, int dx, int dy, int dz) {
        return !this.incompleteArenas.isEmpty()
            && location != null
            && location.getWorld() != null
            && IncompleteArenaGuard.touchesMovement(
                this.incompleteArenas.values(),
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                dx,
                dy,
                dz
            );
    }

    ArenaData findArena(Location location) {
        if (location != null && location.getWorld() != null) {
            for (ArenaData arena : this.arenas.values()) {
                if (!this.incompleteArenas.containsKey(arena.name)
                    && arena.worldName.equals(location.getWorld().getName())
                    && Math.abs(location.getBlockX() - arena.centerX) <= arena.radius
                    && Math.abs(location.getBlockZ() - arena.centerZ) <= arena.radius
                    && location.getBlockY() >= arena.minSurfaceY - 20
                    && location.getBlockY() <= arena.maxSurfaceY + 64) {
                    return arena;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    void sendHelp(CommandSender sender) {
        sender.sendMessage(
            ChatColor.GOLD
                + "ArenaSystem 0.2.8.6"
                + ChatColor.GRAY
                + " — mode-aware hybrid arena, terrain normalizer & match engine"
        );
        sender.sendMessage(
            ChatColor.YELLOW
                + "/arena create <nama> [seed] [tdm|relic_rush] "
                + ChatColor.GRAY
                + "- seed menentukan variasi jalur dan cover"
        );
        sender.sendMessage(ChatColor.YELLOW + "/arena list | info <nama> | tp <nama> <red|blue|green|yellow|center>");
        sender.sendMessage(
            ChatColor.YELLOW
                + "/arena delete <nama> confirm | regenerate <nama> [seed] | recover <nama> [discard confirm]"
        );
        sender.sendMessage(
            ChatColor.YELLOW
                + "/arena quarantine list | release <nama> confirm "
                + ChatColor.GRAY
                + "- area dari recover discard"
        );
        sender.sendMessage(
            ChatColor.YELLOW + "/arena reset <nama> " + ChatColor.GRAY + "- pulihkan perubahan blok pertandingan"
        );
        sender.sendMessage(
            ChatColor.YELLOW
                + "/arena mode <nama> <tdm|relic_rush> "
                + ChatColor.GRAY
                + "- ubah mode dan bangun ulang layout"
        );
        sender.sendMessage(
            ChatColor.YELLOW
                + "/arena join <nama> <red|blue|green|yellow> [player] | leave | start <nama> | stop <nama>"
        );
        sender.sendMessage(
            ChatColor.YELLOW
                + "/arena team <nama> [2|3|4] "
                + ChatColor.GRAY
                + "- atur jumlah tim TDM (default 2: RED, BLUE)"
        );
        sender.sendMessage(
            ChatColor.YELLOW
                + "/arena class [knight|tank|archer|sniper|assassin|ninja|boxer|demoman] "
                + ChatColor.GRAY
                + "- pilih class di lobby persiapan"
        );
        sender.sendMessage(
            ChatColor.GRAY
                + "Skill class: klik kanan item skill di hotbar. Boxer juga dapat klik kanan saat tangan kosong untuk Heavy Jab (2,1 detik cooldown)."
        );
        sender.sendMessage(ChatColor.GRAY + "Cooldown dan durasi skill dapat diatur di config.yml.");
        sender.sendMessage(
            ChatColor.YELLOW + "/arena clearmobs <nama> " + ChatColor.GRAY + "- bersihkan mob di area arena"
        );
        sender.sendMessage(
            ChatColor.YELLOW
                + "/arena clearvegetation <nama> "
                + ChatColor.GRAY
                + "- bersihkan rumput/tanaman pengganggu dengan snapshot aman"
        );
        sender.sendMessage(
            ChatColor.YELLOW + "/arena <nama> breakable|disbreakable|placeable|displaceable <op|@s|@a|player>"
        );
        sender.sendMessage(
            ChatColor.GRAY + "Default: blok arena dilindungi. arenasystem.bypass melewati aturan proteksi."
        );
    }

    void adaptSinglePlayerPreparation(MatchState state) {
        if (state.preparing
            && state.teams.size() == 1
            && this.getConfig().getBoolean("matches.allow-single-player", true)
            && !state.singlePlayerMode) {
            state.singlePlayerMode = true;
        }
    }

    MatchState findPreparingMatch(UUID playerId) {
        for (MatchState state : this.matches.values()) {
            if (state.preparing && state.teams.containsKey(playerId)) {
                return state;
            }
        }

        return null;
    }

    // $VF: Compiled from ArenaSystemPlugin.java
    record QuarantinedArea(String name, String worldName, int centerX, int centerZ, int radius) {
    }
}
