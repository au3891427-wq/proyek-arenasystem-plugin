package id.hadziq.arenasystem;

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.util.RayTraceResult;
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
    /** Clearance between the spectator camera eye and the first blocking surface. */
    private static final double DEATH_CAMERA_BLOCK_CLEARANCE = 0.35D;

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

        DeathCameraSession cameraSession = activeDeathCameras.get(event.getPlayer().getUniqueId());
        if (cameraSession != null) {
            if (cameraSession.applyingFollow) return;
            Location from = event.getFrom();
            Location to = event.getTo();
            // Rotation-only packets tetap dibiarkan agar mouse look tidak terkunci.
            if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
            event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                    to.getYaw(), to.getPitch()));
            return;
        }

        // Pemain dengan snapshot hasil recovery harus boleh kembali ke lokasi asalnya,
        // meskipun lokasi saat login masih berada di dalam footprint arena.
        if (plugin.pendingPlayerRestores.containsKey(event.getPlayer().getUniqueId())) return;

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeathCameraSpectateEntity(PlayerStartSpectatingEntityEvent event) {
        if (activeDeathCameras.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
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
        Player player = event.getPlayer();
        PlayerSnapshot snapshot = plugin.pendingPlayerRestores.get(player.getUniqueId());
        if (snapshot == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> plugin.restorePendingPlayerRestore(player, snapshot));
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
            if (state.teams.containsKey(playerId)) {
                plugin.restoreRelicCarrierMovement(player, state);
                state.teams.remove(playerId);
                state.selectedClasses.remove(playerId);
                PlayerSnapshot snapshot = state.playerSnapshots.remove(playerId);
                if (snapshot != null) plugin.queuePendingPlayerRestore(playerId, snapshot);
                state.previousScoreboards.remove(playerId);
                state.hudScoreboards.remove(playerId);
                state.hudObjectives.remove(playerId);
                if (state.teams.isEmpty()) {
                    if (state.running || state.preparing || state.launching) {
                        ArenaData arena = plugin.arenas.get(state.arenaName);
                        if (arena != null) {
                            plugin.stopAndRestoreMatch(arena.name, () -> plugin.getLogger().info(
                                    "Match di arena " + arena.name + " dihentikan karena semua pemain keluar."));
                        }
                    } else {
                        // No players remain in a pre-match queue; discard the empty state.
                        plugin.matches.remove(state.arenaName, state);
                    }
                } else {
                    if (state.preparing) plugin.adaptSinglePlayerPreparation(state);
                    // Update every remaining participant's sidebar immediately after disconnect.
                    plugin.updateMatchHud(state);
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

        PlayerSnapshot deferredSnapshot = plugin.pendingPlayerRestores.get(playerId);
        if (deferredSnapshot != null) {
            if (deferredSnapshot.location.getWorld() != null) {
                event.setRespawnLocation(deferredSnapshot.location);
            }
            Bukkit.getScheduler().runTask(plugin,
                    () -> plugin.restorePendingPlayerRestore(player, deferredSnapshot));
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

            if (player.getGameMode() == GameMode.SPECTATOR) player.setGameMode(GameMode.SURVIVAL);
            if (active.mode.equals("tdm")) {
                plugin.giveClassLoadout(player, active.selectedClasses.getOrDefault(playerId, "knight"));
            }
        });
    }

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
            if (victim.isDead()) victim.spigot().respawn();
        }, RESPAWN_REQUEST_DELAY_TICKS);
        respawnTasks.put(victimId, task);
    }

    private void startDeathCamera(Player victim, Player killer, ArenaData arena, String team) {
        UUID victimId = victim.getUniqueId();
        stopDeathCameraFollow(victimId);

        MatchState state = plugin.matches.get(arena.name);
        Location killerLocation = killer.getLocation();
        Location desiredInitialCamera = initialDeathCameraLocation(killerLocation);
        Location initialCamera = resolveDeathCameraCollision(victim, killer, desiredInitialCamera);
        DeathCameraSession session = new DeathCameraSession(arena.name, team, killer.getUniqueId(),
                desiredInitialCamera.getX() - killerLocation.getX(),
                desiredInitialCamera.getY() - killerLocation.getY(),
                desiredInitialCamera.getZ() - killerLocation.getZ());
        activeDeathCameras.put(victimId, session);

        victim.setGameMode(GameMode.SPECTATOR);
        victim.setSpectatorTarget(null);
        session.applyingFollow = true;
        try {
            victim.teleport(initialCamera);
        } finally {
            session.applyingFollow = false;
        }

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

    private Location initialDeathCameraLocation(Location target) {
        Vector forward = target.getDirection().setY(0.0D);
        if (forward.lengthSquared() < 1.0E-6D) forward = new Vector(0.0D, 0.0D, 1.0D);
        forward.normalize();

        Location camera = target.clone().add(forward.multiply(-3.25D)).add(0.0D, 1.48D, 0.0D);
        camera.setYaw(target.getYaw());
        camera.setPitch(6.0F);
        return camera;
    }

    private Location followCameraLocation(DeathCameraSession session, Location killerLocation,
                                          float viewerYaw, float viewerPitch) {
        Location camera = killerLocation.clone();
        camera.add(session.offsetX, session.offsetY, session.offsetZ);
        camera.setYaw(viewerYaw);
        camera.setPitch(viewerPitch);
        return camera;
    }

    /**
     * Keep the camera's freely controlled yaw/pitch, but move its position forward
     * when a solid block lies between the killer and the desired camera point.
     * The stored follow offset is deliberately not changed, so the camera returns
     * to its normal offset as soon as the obstruction is gone.
     */
    private Location resolveDeathCameraCollision(Player viewer, Player killer, Location desired) {
        if (viewer == null || killer == null || desired == null
                || desired.getWorld() == null || killer.getWorld() != desired.getWorld()) {
            return desired;
        }

        Location rayStart = killer.getEyeLocation();
        Location rayEnd = desired.clone().add(0.0D, viewer.getEyeHeight(), 0.0D);
        Vector ray = rayEnd.toVector().subtract(rayStart.toVector());
        double distance = ray.length();
        if (distance <= 1.0E-6D) return desired;

        Vector direction = ray.multiply(1.0D / distance);
        RayTraceResult hit = desired.getWorld().rayTraceBlocks(
                rayStart, direction, distance, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitPosition() == null) return desired;

        double hitDistance = hit.getHitPosition().distance(rayStart.toVector());
        double safeDistance = Math.max(0.0D, hitDistance - DEATH_CAMERA_BLOCK_CLEARANCE);
        Vector safeEyePosition = rayStart.toVector().add(direction.multiply(safeDistance));
        Location safeCamera = safeEyePosition.toLocation(desired.getWorld())
                .subtract(0.0D, viewer.getEyeHeight(), 0.0D);
        safeCamera.setYaw(desired.getYaw());
        safeCamera.setPitch(desired.getPitch());
        return safeCamera;
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
            cancelTask(session.followTask);
            session.followTask = null;
            return;
        }

        Location currentCamera = viewer.getLocation();
        Location desired = resolveDeathCameraCollision(viewer, killer,
                followCameraLocation(session, killer.getLocation(),
                        currentCamera.getYaw(), currentCamera.getPitch()));
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
    public void onPlayerPreparationDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        ArenaData arena = plugin.findArenaFootprint(event.getEntity().getLocation());
        if (arena != null && plugin.isPreparing(arena)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player damager = null;
        if (event.getDamager() instanceof Player player) {
            damager = player;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            damager = player;
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
