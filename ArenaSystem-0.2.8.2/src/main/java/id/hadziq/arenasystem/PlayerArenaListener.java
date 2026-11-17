package id.hadziq.arenasystem;

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Siklus hidup pemain relatif terhadap arena: batas gerak/keluar arena,
 * portal, kematian (skor TDM + relic drop), respawn ke spawn tim, cleanup
 * saat quit, damage preparation, dan friendly fire.
 */
final class PlayerArenaListener implements Listener {
    private static final long RESPAWN_REQUEST_DELAY_TICKS = 1L;

    private final ArenaSystemPlugin plugin;
    private final Map<UUID, DeathCamera> pendingDeathCameras = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> deathCameraTasks = new HashMap<>();
    private final Map<UUID, DeathCameraSession> activeDeathCameras = new HashMap<>();

    private record DeathCamera(String arenaName, String team, UUID killerId) {}

    private static final class DeathCameraSession {
        private final String arenaName;
        private final String team;
        private final UUID killerId;
        // Offset from the killer's location, fixed when the session starts (C1).
        // Stored as primitives so no mutable Vector/Location can be altered by accident.
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private BukkitTask followTask;
        private boolean applyingFollow;

        private DeathCameraSession(String arenaName, String team, UUID killerId,
                                   double offsetX, double offsetY, double offsetZ) {
            this.arenaName = arenaName;
            this.team = team;
            this.killerId = killerId;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }
    }

    PlayerArenaListener(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
    }

@EventHandler(ignoreCancelled = true)
public void onPlayerPortal(PlayerPortalEvent event) {
    if (plugin.findArena(event.getFrom()) != null) {
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "Portal tidak dapat digunakan dari arena.");
    }
}

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onPlayerArenaMovement(PlayerMoveEvent event) {
    if (event.getTo() == null) return;

    // Death-camera spectators may look around, but their position is controlled
    // exclusively by the killer-follow task. This keeps WASD from detaching the camera.
    DeathCameraSession cameraSession = activeDeathCameras.get(event.getPlayer().getUniqueId());
    if (cameraSession != null) {
        if (cameraSession.applyingFollow) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        // Rotation-only packets are left untouched so mouse look is never altered.
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
        event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                to.getYaw(), to.getPitch()));
        return;
    }

    ArenaData arena = plugin.findArena(event.getFrom());
    if (arena == null) return;
    Location to = event.getTo();
    MatchState prepState = plugin.matches.get(arena.name);
    if (prepState != null && (prepState.ending
            || !prepState.teams.containsKey(event.getPlayer().getUniqueId()))) return;
    if (prepState != null && prepState.preparing && to.getY() < prepState.lobbyY - 2) {
        String team = prepState.teams.get(event.getPlayer().getUniqueId());
        if (team != null) {
            plugin.teleportToPreparationStation(event.getPlayer(), arena, prepState, team);
            event.getPlayer().setFallDistance(0.0F);
            return;
        }
    }
    if (to.getY() < arena.minSurfaceY - 6) {
        event.getPlayer().teleport(new Location(event.getPlayer().getWorld(), arena.centerX + 0.5D,
                arena.objectiveY + 1.0D, arena.centerZ + 0.5D));
        event.getPlayer().sendMessage(ChatColor.YELLOW + "Kamu dikembalikan ke area tengah arena.");
        return;
    }
    if (Math.abs(to.getBlockX() - arena.centerX) > arena.radius
            || Math.abs(to.getBlockZ() - arena.centerZ) > arena.radius
            || to.getY() > arena.maxSurfaceY + 32) {
        event.setTo(event.getFrom());
    }
}

/** Death-camera viewers must not detach by clicking an entity or using the spectator menu. */
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onDeathCameraSpectateEntity(PlayerStartSpectatingEntityEvent event) {
    if (activeDeathCameras.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
}

@EventHandler
public void onPlayerDeath(PlayerDeathEvent event) {
    Player victim = event.getEntity();
    // C6: a new death ends any earlier camera session of this player. Only the follow
    // session, its expiry timer and stale pending state are cleared here; the respawn
    // request for this death is created later by beginDeathCamera().
    UUID victimId = victim.getUniqueId();
    stopDeathCameraFollow(victimId);
    cancelDeathCameraExpiry(victimId);
    pendingDeathCameras.remove(victimId);
    ArenaData arena = plugin.findArena(victim.getLocation());
    if (arena == null) return;
    MatchState state = plugin.matches.get(arena.name);
    if (state == null || !state.running) return;

    boolean keepInventory = plugin.getConfig().getBoolean("matches.keep-inventory." + state.mode, true);
    event.setKeepInventory(keepInventory);
    if (keepInventory) event.getDrops().clear();

    if (state.mode.equals("relic_rush")) {
        if (victim.getUniqueId().equals(state.relicCarrier)) {
            plugin.restoreRelicCarrierMovement(victim, state);
        }
        ItemStack relic = null;
        for (int slot = 0; slot < victim.getInventory().getSize(); slot++) {
            ItemStack stack = victim.getInventory().getItem(slot);
            if (plugin.isRelic(stack, state)) {
                if (relic == null) relic = stack.clone();
                victim.getInventory().setItem(slot, null);
            }
        }
        ItemStack offHand = victim.getInventory().getItemInOffHand();
        if (plugin.isRelic(offHand, state)) {
            if (relic == null) relic = offHand.clone();
            victim.getInventory().setItemInOffHand(null);
        }
        event.getDrops().removeIf(drop -> plugin.isRelic(drop, state));
        event.getItemsToKeep().removeIf(kept -> plugin.isRelic(kept, state));
        if (relic != null) {
            state.relicCarrier = null;
            state.relicDropped = true;
            org.bukkit.entity.Item droppedRelic = victim.getWorld().dropItemNaturally(victim.getLocation(), relic);
            droppedRelic.setPickupDelay(Integer.MAX_VALUE);
            plugin.announce(state, ChatColor.GOLD + "Relic terjatuh karena pembawanya kalah! Rebut kembali sebelum lawan mengamankannya.");
            plugin.updateMatchHud(state);
        }
    }

    Player killer = victim.getKiller();
    String victimTeam = state.teams.get(victim.getUniqueId());
    String killerTeam = killer == null ? null : state.teams.get(killer.getUniqueId());
    boolean validEnemyKiller = killer != null && killer.isOnline()
            && !killer.getUniqueId().equals(victim.getUniqueId())
            && victimTeam != null && killerTeam != null && !victimTeam.equals(killerTeam);

    if (validEnemyKiller) {
        victim.sendTitle(ChatColor.DARK_GREEN + "Slimed!",
                ChatColor.GRAY + "you was slain by " + ChatColor.YELLOW + killer.getName(), 0, 60, 10);
        beginDeathCamera(victim, killer, arena, victimTeam);
        if (state.mode.equals("tdm")) plugin.addScore(state, arena, killerTeam, 1);
    }
}

@EventHandler
public void onPlayerJoinRestore(PlayerJoinEvent event) {
    PlayerSnapshot snapshot = plugin.pendingPlayerRestores.remove(event.getPlayer().getUniqueId());
    if (snapshot == null) return;
    Bukkit.getScheduler().runTask(plugin, () -> snapshot.restore(event.getPlayer()));
}

@EventHandler
public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    pendingDeathCameras.remove(playerId);
    cancelTask(respawnTasks.remove(playerId));
    cancelTask(deathCameraTasks.remove(playerId));
    stopDeathCameraFollow(playerId);
    for (MatchState state : new ArrayList<>(plugin.matches.values())) {
        if (state.teams.containsKey(player.getUniqueId())) {
            plugin.restoreRelicCarrierMovement(player, state);
            state.teams.remove(player.getUniqueId());
            state.selectedClasses.remove(player.getUniqueId());
            PlayerSnapshot snapshot = state.playerSnapshots.remove(player.getUniqueId());
            if (snapshot != null) plugin.pendingPlayerRestores.put(player.getUniqueId(), snapshot);
            state.previousScoreboards.remove(player.getUniqueId());
            state.hudScoreboards.remove(player.getUniqueId());
            state.hudObjectives.remove(player.getUniqueId());
            if (state.preparing && !state.teams.isEmpty()) plugin.adaptSinglePlayerPreparation(state);
            if (state.teams.isEmpty() && (state.running || state.preparing || state.launching)) {
                ArenaData arena = plugin.arenas.get(state.arenaName);
                if (arena != null) {
                    plugin.stopAndRestoreMatch(arena.name, () -> plugin.getLogger().info("Match di arena " + arena.name + " dihentikan karena semua pemain keluar."));
                }
            }
            break;
        }
    }

    ArenaData arena = plugin.findArena(player.getLocation());
    if (arena == null) return;
    MatchState state = plugin.matches.get(arena.name);
    if (state == null || !state.running || !state.mode.equals("relic_rush")) return;

    boolean relicDroppedOnQuit = false;
    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (plugin.isRelic(stack, state)) {
            org.bukkit.entity.Item droppedItem = player.getWorld().dropItemNaturally(player.getLocation(), stack.clone());
            droppedItem.setPickupDelay(Integer.MAX_VALUE);
            player.getInventory().setItem(slot, null);
            relicDroppedOnQuit = true;
        }
    }
    ItemStack offHand = player.getInventory().getItemInOffHand();
    if (plugin.isRelic(offHand, state)) {
        org.bukkit.entity.Item droppedItem = player.getWorld().dropItemNaturally(player.getLocation(), offHand.clone());
        droppedItem.setPickupDelay(Integer.MAX_VALUE);
        player.getInventory().setItemInOffHand(null);
        relicDroppedOnQuit = true;
    }
    if (relicDroppedOnQuit) {
        state.relicCarrier = null;
        state.relicDropped = true;
        plugin.announce(state, ChatColor.GOLD + "Pembawa Relic keluar dari server. Relic dijatuhkan di lokasinya!");
        plugin.updateMatchHud(state);
    }
}

@EventHandler
public void onPlayerRespawn(PlayerRespawnEvent event) {
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    DeathCamera deathCamera = pendingDeathCameras.remove(playerId);

    // C3: the match ended while this player was dead, so the snapshot restore was deferred.
    // Respawn at the saved location and restore once the player is alive.
    // The entry stays in the map until the restore actually runs, so onDisable() or a rejoin
    // can still finish it; the guarded remove() below prevents a double restore.
    PlayerSnapshot deferredSnapshot = plugin.pendingPlayerRestores.get(playerId);
    if (deferredSnapshot != null) {
        if (deferredSnapshot.location.getWorld() != null) {
            event.setRespawnLocation(deferredSnapshot.location);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return; // stays pending for the next respawn/join
            if (plugin.pendingPlayerRestores.remove(playerId, deferredSnapshot)) {
                deferredSnapshot.restore(player);
            }
        });
        return;
    }

    ArenaData arena = deathCamera == null
            ? plugin.findArena(player.getLocation())
            : plugin.arenas.get(deathCamera.arenaName());
    if (arena == null) return;

    MatchState state = plugin.matches.get(arena.name);
    if (state == null || !state.running) return;

    String team = state.teams.get(playerId);
    if (team == null) return;
    event.setRespawnLocation(plugin.spawnLocation(arena, team));

    // Wait until Bukkit has completed the respawn before changing game mode or
    // native item cooldown displays.
    Bukkit.getScheduler().runTask(plugin, () -> {
        MatchState active = plugin.matches.get(arena.name);
        if (active == null || !active.running || !player.isOnline()
                || !team.equals(active.teams.get(playerId))) return;

        if (plugin.classAbilityListener != null) {
            plugin.classAbilityListener.resetCooldownsForRespawn(player, active);
        } else {
            active.abilityCooldowns.remove(playerId);
        }

        Player killer = deathCamera == null ? null : Bukkit.getPlayer(deathCamera.killerId());
        if (deathCamera != null && killer != null && killer.isOnline() && !killer.isDead()
                && active.teams.containsKey(killer.getUniqueId())) {
            startDeathCamera(player, killer, arena, team);
            return;
        }

        // A match participant must never leave a respawn in spectator mode.
        if (player.getGameMode() == GameMode.SPECTATOR) player.setGameMode(GameMode.SURVIVAL);
        if (active.mode.equals("tdm")) {
            plugin.giveClassLoadout(player, active.selectedClasses.getOrDefault(playerId, "knight"));
        }
    });
}

/**
 * Used when a match ends while the player is still dead: after the camera/respawn
 * tasks were cancelled the normal respawn flow would never run, so request it once.
 */
void requestRespawnAfterMatch(Player player) {
    UUID playerId = player.getUniqueId();
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline() && online.isDead()) online.spigot().respawn();
    }, RESPAWN_REQUEST_DELAY_TICKS);
}

private void beginDeathCamera(Player victim, Player killer, ArenaData arena, String team) {
    UUID victimId = victim.getUniqueId();
    pendingDeathCameras.put(victimId, new DeathCamera(arena.name, team, killer.getUniqueId()));
    cancelTask(respawnTasks.remove(victimId));
    cancelTask(deathCameraTasks.remove(victimId));

    BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
        respawnTasks.remove(victimId);
        MatchState active = plugin.matches.get(arena.name);
        if (active == null || !active.running || !victim.isOnline()
                || !team.equals(active.teams.get(victimId))) {
            pendingDeathCameras.remove(victimId);
            return;
        }
        // The documented Spigot bridge performs the normal respawn handshake;
        // PlayerRespawnEvent then applies the arena spawn and spectator sequence.
        if (victim.isDead()) victim.spigot().respawn();
    }, RESPAWN_REQUEST_DELAY_TICKS);
    respawnTasks.put(victimId, task);
}

private void startDeathCamera(Player victim, Player killer, ArenaData arena, String team) {
    UUID victimId = victim.getUniqueId();
    stopDeathCameraFollow(victimId);

    MatchState state = plugin.matches.get(arena.name);
    // C1: the offset is computed once from the killer's facing at session start and kept.
    Location killerLocation = killer.getLocation();
    Location initialCamera = initialDeathCameraLocation(killerLocation);
    DeathCameraSession session = new DeathCameraSession(arena.name, team, killer.getUniqueId(),
            initialCamera.getX() - killerLocation.getX(),
            initialCamera.getY() - killerLocation.getY(),
            initialCamera.getZ() - killerLocation.getZ());
    activeDeathCameras.put(victimId, session);

    victim.setGameMode(GameMode.SPECTATOR);
    // Do not use setSpectatorTarget: it locks the view to the target's camera.
    // A manually positioned spectator camera can preserve its own yaw/pitch.
    victim.setSpectatorTarget(null);
    session.applyingFollow = true;
    try {
        victim.teleport(initialCamera);
    } finally {
        session.applyingFollow = false;
    }

    // Keep the private match HUD/scoreboard assigned to this player while spectating.
    if (state != null) {
        org.bukkit.scoreboard.Scoreboard hud = state.hudScoreboards.get(victimId);
        if (hud != null && victim.getScoreboard() != hud) victim.setScoreboard(hud);
    }

    session.followTask = Bukkit.getScheduler().runTaskTimer(plugin,
            () -> tickDeathCamera(victimId), 1L, 1L);
    victim.sendMessage(ChatColor.GRAY + "Kamera mengikuti " + ChatColor.YELLOW + killer.getName()
            + ChatColor.GRAY + ". Gerakkan mouse untuk melihat bebas; posisi mengikuti pergerakan lawan.");

    int seconds = Math.max(1, Math.min(10,
            plugin.getConfig().getInt("matches.death-camera-seconds", 3)));
    BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
        deathCameraTasks.remove(victimId);
        stopDeathCameraFollow(victimId);
        Player online = Bukkit.getPlayer(victimId);
        MatchState active = plugin.matches.get(arena.name);
        if (online == null || !online.isOnline() || active == null || !active.running
                || !team.equals(active.teams.get(victimId))) return;

        online.setSpectatorTarget(null);
        online.teleport(plugin.spawnLocation(arena, team));
        online.setGameMode(GameMode.SURVIVAL);
        online.setFallDistance(0.0F);
        online.setVelocity(new Vector(0, 0, 0));
        if (active.mode.equals("tdm")) {
            plugin.giveClassLoadout(online, active.selectedClasses.getOrDefault(victimId, "knight"));
        }
        online.sendMessage(ChatColor.GREEN + "Kamu telah kembali ke pertandingan!");
    }, seconds * 20L);
    cancelTask(deathCameraTasks.put(victimId, task));
}

/** Starting camera: behind the killer at head height, looking along the killer's facing. */
private Location initialDeathCameraLocation(Location target) {
    Vector forward = target.getDirection().setY(0.0D);
    if (forward.lengthSquared() < 1.0E-6D) forward = new Vector(0.0D, 0.0D, 1.0D);
    forward.normalize();

    Location camera = target.clone().add(forward.multiply(-3.25D)).add(0.0D, 1.48D, 0.0D);
    camera.setYaw(target.getYaw());
    camera.setPitch(6.0F);
    return camera;
}

/** Follow position: killer's current location plus the offset stored at session start. */
private Location followCameraLocation(DeathCameraSession session, Location killerLocation,
                                      float viewerYaw, float viewerPitch) {
    Location camera = killerLocation.clone();
    camera.add(session.offsetX, session.offsetY, session.offsetZ);
    camera.setYaw(viewerYaw);
    camera.setPitch(viewerPitch);
    return camera;
}

private void tickDeathCamera(UUID viewerId) {
    DeathCameraSession session = activeDeathCameras.get(viewerId);
    if (session == null) return;

    Player viewer = Bukkit.getPlayer(viewerId);
    Player killer = Bukkit.getPlayer(session.killerId);
    MatchState state = plugin.matches.get(session.arenaName);
    boolean valid = viewer != null && viewer.isOnline() && killer != null && killer.isOnline()
            && !killer.isDead() && state != null && state.running
            && session.team.equals(state.teams.get(viewerId))
            && state.teams.containsKey(session.killerId);
    if (!valid) {
        // Keep the camera position locked until its normal respawn timer expires,
        // even if the killer dies or leaves first.
        cancelTask(session.followTask);
        session.followTask = null;
        return;
    }

    Location currentCamera = viewer.getLocation();
    Location desired = followCameraLocation(session, killer.getLocation(),
            currentCamera.getYaw(), currentCamera.getPitch());
    if (currentCamera.getWorld() == desired.getWorld()
            && currentCamera.distanceSquared(desired) < 0.0025D) return;

    session.applyingFollow = true;
    try {
        viewer.teleport(desired);
    } finally {
        session.applyingFollow = false;
    }
}

private void stopDeathCameraFollow(UUID viewerId) {
    DeathCameraSession session = activeDeathCameras.remove(viewerId);
    if (session != null) {
        cancelTask(session.followTask);
        session.followTask = null;
    }
}

/** Cancels only the timer that ends the camera and returns the player to the match. */
private void cancelDeathCameraExpiry(UUID viewerId) {
    cancelTask(deathCameraTasks.remove(viewerId));
}

void stopDeathCamerasForArena(String arenaName) {
    for (Map.Entry<UUID, DeathCameraSession> entry : new ArrayList<>(activeDeathCameras.entrySet())) {
        if (!arenaName.equals(entry.getValue().arenaName)) continue;
        UUID viewerId = entry.getKey();
        stopDeathCameraFollow(viewerId);
        cancelTask(deathCameraTasks.remove(viewerId));
    }
    for (Map.Entry<UUID, DeathCamera> entry : new ArrayList<>(pendingDeathCameras.entrySet())) {
        if (!arenaName.equals(entry.getValue().arenaName())) continue;
        UUID viewerId = entry.getKey();
        pendingDeathCameras.remove(viewerId);
        cancelTask(respawnTasks.remove(viewerId));
        cancelTask(deathCameraTasks.remove(viewerId));
    }
}

void cleanupAll() {
    for (UUID playerId : new ArrayList<>(respawnTasks.keySet())) {
        cancelTask(respawnTasks.remove(playerId));
    }
    for (UUID playerId : new ArrayList<>(deathCameraTasks.keySet())) {
        cancelTask(deathCameraTasks.remove(playerId));
    }
    for (UUID playerId : new ArrayList<>(activeDeathCameras.keySet())) {
        stopDeathCameraFollow(playerId);
    }
    pendingDeathCameras.clear();
}

private void cancelTask(BukkitTask task) {
    if (task != null) task.cancel();
}

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onPlayerPreparationDamage(org.bukkit.event.entity.EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player)) return;
    ArenaData arena = plugin.findArenaFootprint(event.getEntity().getLocation());
    if (arena != null && plugin.isPreparing(arena)) event.setCancelled(true);
}

@EventHandler(ignoreCancelled = true)
public void onPlayerFriendlyFire(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player victim)) return;
    
    Player damager = null;
    if (event.getDamager() instanceof Player p) {
        damager = p;
    } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) {
        damager = p;
    }
    if (damager == null) return;

    ArenaData arena = plugin.findArena(victim.getLocation());
    if (arena == null) return;
    MatchState state = plugin.matches.get(arena.name);
    if (state == null || !state.running) return;

    String victimTeam = state.teams.get(victim.getUniqueId());
    String damagerTeam = state.teams.get(damager.getUniqueId());

    if (victimTeam != null && victimTeam.equals(damagerTeam)) {
        event.setCancelled(true);
    }
}
}
