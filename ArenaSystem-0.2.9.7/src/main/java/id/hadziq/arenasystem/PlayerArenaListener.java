package id.hadziq.arenasystem;

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Item;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

// $VF: Compiled from PlayerArenaListener.java
final class PlayerArenaListener implements Listener {
    private static final long RESPAWN_REQUEST_DELAY_TICKS = 1L;
    private static final double DEATH_CAMERA_BLOCK_CLEARANCE = 0.35;
    private final ArenaSystemPlugin plugin;
    private final Map<UUID, PlayerArenaListener.DeathCamera> pendingDeathCameras = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> deathCameraTasks = new HashMap<>();
    private final Map<UUID, PlayerArenaListener.DeathCameraSession> activeDeathCameras = new HashMap<>();

    PlayerArenaListener(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (this.plugin.findArena(event.getFrom()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Portal tidak dapat digunakan dari arena.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerArenaMovement(PlayerMoveEvent event) {
        if (event.getTo() != null) {
            PlayerArenaListener.DeathCameraSession cameraSession = this.activeDeathCameras
                .get(event.getPlayer().getUniqueId());
            if (cameraSession != null) {
                if (!cameraSession.applyingFollow) {
                    Location from = event.getFrom();
                    Location to = event.getTo();
                    if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                        event.setTo(
                            new Location(
                                from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()
                            )
                        );
                    }
                }
            } else if (!this.plugin.pendingPlayerRestores.containsKey(event.getPlayer().getUniqueId())) {
                ArenaData arena = this.plugin.findArena(event.getFrom());
                if (arena != null) {
                    Location to = event.getTo();
                    MatchState prepState = this.plugin.matches.get(arena.name);
                    if (prepState == null
                        || !prepState.ending && prepState.teams.containsKey(event.getPlayer().getUniqueId())) {
                        if (prepState != null && prepState.preparing && to.getY() < prepState.lobbyY - 2) {
                            String team = prepState.teams.get(event.getPlayer().getUniqueId());
                            if (team != null) {
                                this.plugin.teleportToPreparationStation(event.getPlayer(), arena, prepState, team);
                                event.getPlayer().setFallDistance(0.0F);
                                return;
                            }
                        }

                        if (to.getY() < arena.minSurfaceY - 6) {
                            event.getPlayer()
                                .teleport(
                                    new Location(
                                        event.getPlayer().getWorld(),
                                        arena.centerX + 0.5,
                                        arena.objectiveY + 1.0,
                                        arena.centerZ + 0.5
                                    )
                                );
                            event.getPlayer().sendMessage(ChatColor.YELLOW + "Kamu dikembalikan ke area tengah arena.");
                        } else {
                            if (Math.abs(to.getBlockX() - arena.centerX) > arena.radius
                                || Math.abs(to.getBlockZ() - arena.centerZ) > arena.radius
                                || to.getY() > arena.maxSurfaceY + 32) {
                                event.setTo(event.getFrom());
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeathCameraSpectateEntity(PlayerStartSpectatingEntityEvent event) {
        if (this.activeDeathCameras.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();
        this.stopDeathCameraFollow(victimId);
        this.cancelDeathCameraExpiry(victimId);
        this.pendingDeathCameras.remove(victimId);
        ArenaData arena = this.plugin.findArena(victim.getLocation());
        if (arena != null) {
            MatchState state = this.plugin.matches.get(arena.name);
            if (state != null && state.running) {
                boolean keepInventory = this.plugin
                    .getConfig()
                    .getBoolean("matches.keep-inventory." + state.mode, true);
                event.setKeepInventory(keepInventory);
                if (keepInventory) {
                    event.getDrops().clear();
                }

                if (state.mode.equals("relic_rush")) {
                    if (victim.getUniqueId().equals(state.relicCarrier)) {
                        this.plugin.restoreRelicCarrierMovement(victim, state);
                    }

                    ItemStack relic = null;

                    for (int slot = 0; slot < victim.getInventory().getSize(); slot++) {
                        ItemStack stack = victim.getInventory().getItem(slot);
                        if (this.plugin.isRelic(stack, state)) {
                            if (relic == null) {
                                relic = stack.clone();
                            }

                            victim.getInventory().setItem(slot, null);
                        }
                    }

                    ItemStack offHand = victim.getInventory().getItemInOffHand();
                    if (this.plugin.isRelic(offHand, state)) {
                        if (relic == null) {
                            relic = offHand.clone();
                        }

                        victim.getInventory().setItemInOffHand(null);
                    }

                    event.getDrops().removeIf(drop -> this.plugin.isRelic(drop, state));
                    event.getItemsToKeep().removeIf(kept -> this.plugin.isRelic(kept, state));
                    if (relic != null) {
                        state.relicCarrier = null;
                        state.relicDropped = true;
                        Item droppedRelic = victim.getWorld().dropItemNaturally(victim.getLocation(), relic);
                        droppedRelic.setPickupDelay(Integer.MAX_VALUE);
                        this.plugin
                            .announce(
                                state,
                                ChatColor.GOLD
                                    + "Relic terjatuh karena pembawanya kalah! Rebut kembali sebelum lawan mengamankannya."
                            );
                        this.plugin.updateMatchHud(state);
                    }
                }

                Player killer = victim.getKiller();
                String victimTeam = state.teams.get(victim.getUniqueId());
                String killerTeam = killer == null ? null : state.teams.get(killer.getUniqueId());
                boolean validEnemyKiller = killer != null
                    && killer.isOnline()
                    && !killer.getUniqueId().equals(victim.getUniqueId())
                    && victimTeam != null
                    && killerTeam != null
                    && !victimTeam.equals(killerTeam);
                if (validEnemyKiller) {
                    victim.sendTitle(
                        ChatColor.DARK_GREEN + "Slimed!",
                        ChatColor.GRAY + "you was slain by " + ChatColor.YELLOW + killer.getName(),
                        0,
                        60,
                        10
                    );
                    this.beginDeathCamera(victim, killer, arena, victimTeam);
                    if (state.mode.equals("tdm")) {
                        this.plugin.addScore(state, arena, killerTeam, 1);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoinRestore(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerSnapshot snapshot = this.plugin.pendingPlayerRestores.get(player.getUniqueId());
        if (snapshot != null) {
            Bukkit.getScheduler().runTask(this.plugin, () -> this.plugin.restorePendingPlayerRestore(player, snapshot));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        this.pendingDeathCameras.remove(playerId);
        this.cancelTask(this.respawnTasks.remove(playerId));
        this.cancelTask(this.deathCameraTasks.remove(playerId));
        this.stopDeathCameraFollow(playerId);

        for (MatchState state : new ArrayList<>(this.plugin.matches.values())) {
            if (state.teams.containsKey(playerId)) {
                this.plugin.restoreRelicCarrierMovement(player, state);
                state.teams.remove(playerId);
                state.selectedClasses.remove(playerId);
                PlayerSnapshot snapshot = state.playerSnapshots.remove(playerId);
                if (snapshot != null) {
                    this.plugin.queuePendingPlayerRestore(playerId, snapshot);
                }

                state.previousScoreboards.remove(playerId);
                state.hudScoreboards.remove(playerId);
                state.hudObjectives.remove(playerId);
                if (state.teams.isEmpty()) {
                    if (!state.running && !state.preparing && !state.launching) {
                        this.plugin.matches.remove(state.arenaName, state);
                    } else {
                        ArenaData arena = this.plugin.arenas.get(state.arenaName);
                        if (arena != null) {
                            this.plugin
                                .stopAndRestoreMatch(
                                    arena.name,
                                    () -> this.plugin
                                        .getLogger()
                                        .info(
                                            "Match di arena " + arena.name + " dihentikan karena semua pemain keluar."
                                        )
                                );
                        }
                    }
                } else {
                    if (state.preparing) {
                        this.plugin.adaptSinglePlayerPreparation(state);
                    }

                    this.plugin.updateMatchHud(state);
                }
                break;
            }
        }

        ArenaData arena = this.plugin.findArena(player.getLocation());
        if (arena != null) {
            MatchState state = this.plugin.matches.get(arena.name);
            if (state != null && state.running && state.mode.equals("relic_rush")) {
                boolean relicDroppedOnQuit = false;

                for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (this.plugin.isRelic(stack, state)) {
                        Item droppedItem = player.getWorld().dropItemNaturally(player.getLocation(), stack.clone());
                        droppedItem.setPickupDelay(Integer.MAX_VALUE);
                        player.getInventory().setItem(slot, null);
                        relicDroppedOnQuit = true;
                    }
                }

                ItemStack offHand = player.getInventory().getItemInOffHand();
                if (this.plugin.isRelic(offHand, state)) {
                    Item droppedItem = player.getWorld().dropItemNaturally(player.getLocation(), offHand.clone());
                    droppedItem.setPickupDelay(Integer.MAX_VALUE);
                    player.getInventory().setItemInOffHand(null);
                    relicDroppedOnQuit = true;
                }

                if (relicDroppedOnQuit) {
                    state.relicCarrier = null;
                    state.relicDropped = true;
                    this.plugin
                        .announce(
                            state, ChatColor.GOLD + "Pembawa Relic keluar dari server. Relic dijatuhkan di lokasinya!"
                        );
                    this.plugin.updateMatchHud(state);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerArenaListener.DeathCamera deathCamera = this.pendingDeathCameras.remove(playerId);
        PlayerSnapshot deferredSnapshot = this.plugin.pendingPlayerRestores.get(playerId);
        if (deferredSnapshot != null) {
            if (deferredSnapshot.location.getWorld() != null) {
                event.setRespawnLocation(deferredSnapshot.location);
            }

            Bukkit.getScheduler()
                .runTask(this.plugin, () -> this.plugin.restorePendingPlayerRestore(player, deferredSnapshot));
        } else {
            ArenaData arena = deathCamera == null
                ? this.plugin.findArena(player.getLocation())
                : this.plugin.arenas.get(deathCamera.arenaName());
            if (arena != null) {
                MatchState state = this.plugin.matches.get(arena.name);
                if (state != null && state.running) {
                    String team = state.teams.get(playerId);
                    if (team != null) {
                        event.setRespawnLocation(this.plugin.spawnLocation(arena, team));
                        Bukkit.getScheduler()
                            .runTask(
                                this.plugin,
                                () -> {
                                    MatchState active = this.plugin.matches.get(arena.name);
                                    if (active != null
                                        && active.running
                                        && player.isOnline()
                                        && team.equals(active.teams.get(playerId))) {
                                        if (this.plugin.classAbilityListener != null) {
                                            this.plugin.classAbilityListener.resetCooldownsForRespawn(player, active);
                                        } else {
                                            active.abilityCooldowns.remove(playerId);
                                        }

                                        Player killer = deathCamera == null
                                            ? null
                                            : Bukkit.getPlayer(deathCamera.killerId());
                                        if (deathCamera != null
                                            && killer != null
                                            && killer.isOnline()
                                            && !killer.isDead()
                                            && active.teams.containsKey(killer.getUniqueId())) {
                                            this.startDeathCamera(player, killer, arena, team);
                                        } else {
                                            if (player.getGameMode() == GameMode.SPECTATOR) {
                                                player.setGameMode(GameMode.SURVIVAL);
                                            }

                                            if (active.mode.equals("tdm")) {
                                                this.plugin
                                                    .giveClassLoadout(
                                                        player, active.selectedClasses.getOrDefault(playerId, "knight")
                                                    );
                                            }
                                        }
                                    }
                                }
                            );
                    }
                }
            }
        }
    }

    void requestRespawnAfterMatch(Player player) {
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online != null && online.isOnline() && online.isDead()) {
                online.spigot().respawn();
            }
        }, 1L);
    }

    private void beginDeathCamera(Player victim, Player killer, ArenaData arena, String team) {
        UUID victimId = victim.getUniqueId();
        this.pendingDeathCameras
            .put(victimId, new PlayerArenaListener.DeathCamera(arena.name, team, killer.getUniqueId()));
        this.cancelTask(this.respawnTasks.remove(victimId));
        this.cancelTask(this.deathCameraTasks.remove(victimId));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.respawnTasks.remove(victimId);
            MatchState active = this.plugin.matches.get(arena.name);
            if (active != null && active.running && victim.isOnline() && team.equals(active.teams.get(victimId))) {
                if (victim.isDead()) {
                    victim.spigot().respawn();
                }
            } else {
                this.pendingDeathCameras.remove(victimId);
            }
        }, 1L);
        this.respawnTasks.put(victimId, task);
    }

    private void startDeathCamera(Player victim, Player killer, ArenaData arena, String team) {
        UUID victimId = victim.getUniqueId();
        this.stopDeathCameraFollow(victimId);
        MatchState state = this.plugin.matches.get(arena.name);
        Location killerLocation = killer.getLocation();
        Location desiredInitialCamera = this.initialDeathCameraLocation(killerLocation);
        Location initialCamera = this.resolveDeathCameraCollision(victim, killer, desiredInitialCamera);
        PlayerArenaListener.DeathCameraSession session = new PlayerArenaListener.DeathCameraSession(
            arena.name,
            team,
            killer.getUniqueId(),
            desiredInitialCamera.getX() - killerLocation.getX(),
            desiredInitialCamera.getY() - killerLocation.getY(),
            desiredInitialCamera.getZ() - killerLocation.getZ()
        );
        this.activeDeathCameras.put(victimId, session);
        victim.setGameMode(GameMode.SPECTATOR);
        victim.setSpectatorTarget(null);
        session.applyingFollow = true;

        try {
            victim.teleport(initialCamera);
        } finally {
            session.applyingFollow = false;
        }

        if (state != null) {
            Scoreboard hud = state.hudScoreboards.get(victimId);
            if (hud != null && victim.getScoreboard() != hud) {
                victim.setScoreboard(hud);
            }
        }

        session.followTask = Bukkit.getScheduler()
            .runTaskTimer(this.plugin, () -> this.tickDeathCamera(victimId), 1L, 1L);
        victim.sendMessage(
            ChatColor.GRAY
                + "Kamera mengikuti "
                + ChatColor.YELLOW
                + killer.getName()
                + ChatColor.GRAY
                + ". Gerakkan mouse untuk melihat bebas; posisi mengikuti pergerakan lawan."
        );
        int seconds = Math.max(1, Math.min(10, this.plugin.getConfig().getInt("matches.death-camera-seconds", 3)));
        BukkitTask task = Bukkit.getScheduler()
            .runTaskLater(
                this.plugin,
                () -> {
                    this.deathCameraTasks.remove(victimId);
                    this.stopDeathCameraFollow(victimId);
                    Player online = Bukkit.getPlayer(victimId);
                    MatchState active = this.plugin.matches.get(arena.name);
                    if (online != null
                        && online.isOnline()
                        && active != null
                        && active.running
                        && team.equals(active.teams.get(victimId))) {
                        online.setSpectatorTarget(null);
                        online.teleport(this.plugin.spawnLocation(arena, team));
                        online.setGameMode(GameMode.SURVIVAL);
                        online.setFallDistance(0.0F);
                        online.setVelocity(new Vector<>(0, 0, 0));
                        if (active.mode.equals("tdm")) {
                            this.plugin
                                .giveClassLoadout(online, active.selectedClasses.getOrDefault(victimId, "knight"));
                        }

                        online.sendMessage(ChatColor.GREEN + "Kamu telah kembali ke pertandingan!");
                    }
                },
                seconds * 20L
            );
        this.cancelTask(this.deathCameraTasks.put(victimId, task));
    }

    private Location initialDeathCameraLocation(Location target) {
        Vector forward = target.getDirection().setY(0.0);
        if (forward.lengthSquared() < 1.0E-6) {
            forward = new Vector<>(0.0, 0.0, 1.0);
        }

        forward.normalize();
        Location camera = target.clone().add(forward.multiply(-3.25)).add(0.0, 1.48, 0.0);
        camera.setYaw(target.getYaw());
        camera.setPitch(6.0F);
        return camera;
    }

    private Location followCameraLocation(
        PlayerArenaListener.DeathCameraSession session, Location killerLocation, float viewerYaw, float viewerPitch
    ) {
        Location camera = killerLocation.clone();
        camera.add(session.offsetX, session.offsetY, session.offsetZ);
        camera.setYaw(viewerYaw);
        camera.setPitch(viewerPitch);
        return camera;
    }

    private Location resolveDeathCameraCollision(Player viewer, Player killer, Location desired) {
        if (viewer != null
            && killer != null
            && desired != null
            && desired.getWorld() != null
            && killer.getWorld() == desired.getWorld()) {
            Location rayStart = killer.getEyeLocation();
            Location rayEnd = desired.clone().add(0.0, viewer.getEyeHeight(), 0.0);
            Vector ray = rayEnd.toVector().subtract(rayStart.toVector());
            double distance = ray.length();
            if (distance <= 1.0E-6) {
                return desired;
            } else {
                Vector direction = ray.multiply(1.0 / distance);
                RayTraceResult hit = desired.getWorld()
                    .rayTraceBlocks(rayStart, direction, distance, FluidCollisionMode.NEVER, true);
                if (hit != null && hit.getHitPosition() != null) {
                    double hitDistance = hit.getHitPosition().distance(rayStart.toVector());
                    double safeDistance = Math.max(0.0, hitDistance - 0.35);
                    Vector safeEyePosition = rayStart.toVector().add(direction.multiply(safeDistance));
                    Location safeCamera = safeEyePosition.toLocation(desired.getWorld())
                        .subtract(0.0, viewer.getEyeHeight(), 0.0);
                    safeCamera.setYaw(desired.getYaw());
                    safeCamera.setPitch(desired.getPitch());
                    return safeCamera;
                } else {
                    return desired;
                }
            }
        } else {
            return desired;
        }
    }

    private void tickDeathCamera(UUID viewerId) {
        PlayerArenaListener.DeathCameraSession session = this.activeDeathCameras.get(viewerId);
        if (session != null) {
            Player viewer = Bukkit.getPlayer(viewerId);
            Player killer = Bukkit.getPlayer(session.killerId);
            MatchState state = this.plugin.matches.get(session.arenaName);
            boolean valid = viewer != null
                && viewer.isOnline()
                && killer != null
                && killer.isOnline()
                && !killer.isDead()
                && state != null
                && state.running
                && session.team.equals(state.teams.get(viewerId))
                && state.teams.containsKey(session.killerId);
            if (!valid) {
                this.cancelTask(session.followTask);
                session.followTask = null;
            } else {
                Location currentCamera = viewer.getLocation();
                Location desired = this.resolveDeathCameraCollision(
                    viewer,
                    killer,
                    this.followCameraLocation(
                        session, killer.getLocation(), currentCamera.getYaw(), currentCamera.getPitch()
                    )
                );
                if (currentCamera.getWorld() != desired.getWorld()
                    || !(currentCamera.distanceSquared(desired) < 0.0025)) {
                    session.applyingFollow = true;

                    try {
                        viewer.teleport(desired);
                    } finally {
                        session.applyingFollow = false;
                    }
                }
            }
        }
    }

    private void stopDeathCameraFollow(UUID viewerId) {
        PlayerArenaListener.DeathCameraSession session = this.activeDeathCameras.remove(viewerId);
        if (session != null) {
            this.cancelTask(session.followTask);
            session.followTask = null;
        }
    }

    private void cancelDeathCameraExpiry(UUID viewerId) {
        this.cancelTask(this.deathCameraTasks.remove(viewerId));
    }

    void stopDeathCamerasForArena(String arenaName) {
        for (Entry<UUID, PlayerArenaListener.DeathCameraSession> entry : new ArrayList<>(
            this.activeDeathCameras.entrySet()
        )) {
            if (arenaName.equals(entry.getValue().arenaName)) {
                UUID viewerId = entry.getKey();
                this.stopDeathCameraFollow(viewerId);
                this.cancelTask(this.deathCameraTasks.remove(viewerId));
            }
        }

        for (Entry<UUID, PlayerArenaListener.DeathCamera> entry : new ArrayList<>(this.pendingDeathCameras.entrySet())) {
            if (arenaName.equals(entry.getValue().arenaName())) {
                UUID viewerId = entry.getKey();
                this.pendingDeathCameras.remove(viewerId);
                this.cancelTask(this.respawnTasks.remove(viewerId));
                this.cancelTask(this.deathCameraTasks.remove(viewerId));
            }
        }
    }

    void cleanupAll() {
        for (UUID playerId : new ArrayList<>(this.respawnTasks.keySet())) {
            this.cancelTask(this.respawnTasks.remove(playerId));
        }

        for (UUID playerId : new ArrayList<>(this.deathCameraTasks.keySet())) {
            this.cancelTask(this.deathCameraTasks.remove(playerId));
        }

        for (UUID playerId : new ArrayList<>(this.activeDeathCameras.keySet())) {
            this.stopDeathCameraFollow(playerId);
        }

        this.pendingDeathCameras.clear();
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPreparationDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            ArenaData arena = this.plugin.findArenaFootprint(event.getEntity().getLocation());
            if (arena != null && this.plugin.isPreparing(arena)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFriendlyFire(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            Player var8 = null;
            if (event.getDamager() instanceof Player player) {
                var8 = player;
            } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
                var8 = player;
            }

            if (var8 != null) {
                ArenaData arena = this.plugin.findArena(victim.getLocation());
                if (arena != null) {
                    MatchState state = this.plugin.matches.get(arena.name);
                    if (state != null && state.running) {
                        String victimTeam = state.teams.get(victim.getUniqueId());
                        String damagerTeam = state.teams.get(var8.getUniqueId());
                        if (victimTeam != null && victimTeam.equals(damagerTeam)) {
                            event.setCancelled(true);
                        }
                    }
                }
            }
        }
    }

    // $VF: Compiled from PlayerArenaListener.java
    private record DeathCamera(String arenaName, String team, UUID killerId) {
    }

    // $VF: Compiled from PlayerArenaListener.java
    private static final class DeathCameraSession {
        private final String arenaName;
        private final String team;
        private final UUID killerId;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private BukkitTask followTask;
        private boolean applyingFollow;

        private DeathCameraSession(
            String arenaName, String team, UUID killerId, double offsetX, double offsetY, double offsetZ
        ) {
            this.arenaName = arenaName;
            this.team = team;
            this.killerId = killerId;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }
    }
}
