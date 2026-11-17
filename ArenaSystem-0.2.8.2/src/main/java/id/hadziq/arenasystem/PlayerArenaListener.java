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
        // Offset dari posisi killer, dikunci saat sesi dimulai (C1).
        // Disimpan sebagai primitif agar tidak ada Vector/Location mutable yang berubah secara tidak sengaja.
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private BukkitTask followTask;
        private boolean applyingFollow;

        private DeathCameraSession(String arenaName, String team, UUID killerId, double offsetX, double offsetY, double offsetZ) {
            this.arenaName = arenaName;
            this.team = team;
            this.killerId = killerId;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }
    }

    // ==========================================
    // BAGIAN UTAMA: LOGIKA DEATH CAMERA & RESPAWN
    // ==========================================

    private void beginDeathCamera(Player victim, Player killer, ArenaData arena, String team) {
        UUID victimId = victim.getUniqueId();
        pendingDeathCameras.put(victimId, new DeathCamera(arena.name, team, killer.getUniqueId()));
        cancelTask(respawnTasks.remove(victimId));
        cancelTask(deathCameraTasks.remove(victimId));
        
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            respawnTasks.remove(victimId);
            MatchState active = plugin.matches.get(arena.name);
            if (active == null || !active.running || !victim.isOnline() || !team.equals(active.teams.get(victimId))) {
                pendingDeathCameras.remove(victimId);
                return;
            }
            // Melakukan respawn handshake via Spigot bridge; PlayerRespawnEvent menangani urutan spawn & spectator.
            if (victim.isDead()) victim.spigot().respawn();
        }, RESPAWN_REQUEST_DELAY_TICKS);
        
        respawnTasks.put(victimId, task);
    }

    private void startDeathCamera(Player victim, Player killer, ArenaData arena, String team) {
        UUID victimId = victim.getUniqueId();
        stopDeathCameraFollow(victimId);
        MatchState state = plugin.matches.get(arena.name);
        
        // C1: Offset dihitung sekali dari posisi awal killer dan dijaga tetap konstan
        Location killerLocation = killer.getLocation();
        Location initialCamera = initialDeathCameraLocation(killerLocation);
        DeathCameraSession session = new DeathCameraSession(
            arena.name, team, killer.getUniqueId(),
            initialCamera.getX() - killerLocation.getX(),
            initialCamera.getY() - killerLocation.getY(),
            initialCamera.getZ() - killerLocation.getZ()
        );
        activeDeathCameras.put(victimId, session);
        
        victim.setGameMode(GameMode.SPECTATOR);
        // Jangan gunakan setSpectatorTarget agar pandangan bebas (yaw/pitch) milik viewer tetap terjaga
        victim.setSpectatorTarget(null);
        
        session.applyingFollow = true;
        try {
            victim.teleport(initialCamera);
        } finally {
            session.applyingFollow = false;
        }

        // Pertahankan HUD/scoreboard privat tetap terikat pada pemain selama mode spectator
        if (state != null) {
            org.bukkit.scoreboard.Scoreboard hud = state.hudScoreboards.get(victimId);
            if (hud != null && victim.getScoreboard() != hud) {
                victim.setScoreboard(hud);
            }
        }

        session.followTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickDeathCamera(viewerId=victimId), 1L, 1L);
        victim.sendMessage(ChatColor.GRAY + "Kamera mengikuti " + ChatColor.YELLOW + killer.getName() + ChatColor.GRAY + ". Gerakkan mouse untuk melihat bebas; posisi mengikuti pergerakan lawan.");

        int seconds = Math.max(1, Math.min(10, plugin.getConfig().getInt("matches.death-camera-seconds", 3)));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            deathCameraTasks.remove(victimId);
            stopDeathCameraFollow(victimId);
            Player online = Bukkit.getPlayer(victimId);
            MatchState active = plugin.matches.get(arena.name);
            if (online == null || !online.isOnline() || active == null || !active.running || !team.equals(active.teams.get(victimId))) {
                return;
            }
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

    /** Posisi kamera awal: di belakang killer setinggi kepala, menghadap ke arah pandangan killer. */
    private Location initialDeathCameraLocation(Location target) {
        Vector forward = target.getDirection().setY(0.0D);
        if (forward.lengthSquared() < 1.0E-6D) {
            forward = new Vector(0.0D, 0.0D, 1.0D);
        }
        forward.normalize();
        Location camera = target.clone().add(forward.multiply(-3.25D)).add(0.0D, 1.48D, 0.0D);
        camera.setYaw(target.getYaw());
        camera.setPitch(6.0F);
        return camera;
    }

    /** Posisi follow: Lokasi terkini killer ditambah offset yang disimpan saat sesi dimulai. */
    private Location followCameraLocation(DeathCameraSession session, Location killerLocation, float viewerYaw, float viewerPitch) {
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
                && session.team.equals(state.teams.get(viewerId)) && state.teams.containsKey(session.killerId);
                
        if (!valid) {
            cancelTask(session.followTask);
            session.followTask = null;
            return;
        }

        Location currentCamera = viewer.getLocation();
        Location desired = followCameraLocation(session, killer.getLocation(), currentCamera.getYaw(), currentCamera.getPitch());
        
        if (currentCamera.getWorld() == desired.getWorld() && currentCamera.distanceSquared(desired) < 0.0025D) {
            return;
        }

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

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
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
        
        Player killer = victim.getKiller();
        String victimTeam = state.teams.get(victimId);
        String killerTeam = killer == null ? null : state.teams.get(killer.getUniqueId());
        
        boolean validEnemyKiller = killer != null && killer.isOnline() && !killer.getUniqueId().equals(victimId)
                && victimTeam != null && killerTeam != null && !victimTeam.equals(killerTeam);
                
        if (validEnemyKiller) {
            victim.sendTitle(ChatColor.DARK_GREEN + "Slimed!", ChatColor.GRAY + "you was slain by " + ChatColor.YELLOW + killer.getName(), 0, 60, 10);
            beginDeathCamera(victim, killer, arena, victimTeam);
            if (state.mode.equals("tdm")) {
                plugin.addScore(state, arena, killerTeam, 1);
            }
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
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || player.isDead()) return;
                if (plugin.pendingPlayerRestores.remove(playerId, deferredSnapshot)) {
                    deferredSnapshot.restore(player);
                }
            });
            return;
        }

        ArenaData arena = deathCamera == null ? plugin.findArena(player.getLocation()) : plugin.arenas.get(deathCamera.arenaName());
        if (arena == null) return;
        MatchState state = plugin.matches.get(arena.name);
        if (state == null || !state.running) return;
        String team = state.teams.get(playerId);
        if (team == null) return;
        
        event.setRespawnLocation(plugin.spawnLocation(arena, team));
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            MatchState active = plugin.matches.get(arena.name);
            if (active == null || !active.running || !player.isOnline() || !team.equals(active.teams.get(playerId))) return;
            if (plugin.classAbilityListener != null) {
                plugin.classAbilityListener.resetCooldownsForRespawn(player, active);
            } else {
                active.abilityCooldowns.remove(playerId);
            }
            Player killer = deathCamera == null ? null : Bukkit.getPlayer(deathCamera.killerId());
            if (deathCamera != null && killer != null && killer.isOnline() && !killer.isDead() && active.teams.containsKey(killer.getUniqueId())) {
                startDeathCamera(player, killer, arena, team);
                return;
            }
            if (player.getGameMode() == GameMode.SPECTATOR) player.setGameMode(GameMode.SURVIVAL);
            if (active.mode.equals("tdm")) {
                plugin.giveClassLoadout(player, active.selectedClasses.getOrDefault(playerId, "knight"));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        DeathCameraSession session = activeDeathCameras.get(player.getUniqueId());
        if (session == null || session.applyingFollow) return;
        
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setTo(from);
        }
    }

    @EventHandler
    public void onPlayerStartSpectating(PlayerStartSpectatingEntityEvent event) {
        Player player = event.getPlayer();
        if (activeDeathCameras.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingDeathCameras.remove(playerId);
        cancelTask(respawnTasks.remove(playerId));
        cancelTask(deathCameraTasks.remove(playerId));
        stopDeathCameraFollow(playerId);
    }
}
