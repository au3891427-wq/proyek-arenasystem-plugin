package id.hadziq.arenasystem;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

// $VF: Compiled from CompanionClientListener.java
final class CompanionClientListener implements Listener, PluginMessageListener {
    static final String CHANNEL = "arenasystem:mod_handshake";
    private static final String BYPASS_PERMISSION = "arenasystem.client.bypass";
    private static final long CHALLENGE_DELAY_TICKS = 100L;
    private final JavaPlugin plugin;
    private final Map<UUID, CompanionClientListener.PendingHandshake> pending = new HashMap<>();
    private final Map<UUID, BukkitTask> scheduledChallenges = new HashMap<>();
    private final Set<UUID> gatedPlayers = new HashSet<>();
    private boolean closed;

    CompanionClientListener(JavaPlugin plugin) {
        this.plugin = plugin;
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, "arenasystem:mod_handshake", this);
        messenger.registerOutgoingPluginChannel(plugin, "arenasystem:mod_handshake");
    }

    void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.scheduleChallenge(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.scheduleChallenge(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        this.cancelScheduledChallenge(playerId);
        this.cancelPendingHandshake(playerId);
        this.gatedPlayers.remove(playerId);
    }

    private void scheduleChallenge(Player player) {
        if (!this.closed && !this.isBypassed(player)) {
            UUID playerId = player.getUniqueId();
            this.cancelScheduledChallenge(playerId);
            this.cancelPendingHandshake(playerId);
            this.gatedPlayers.add(playerId);
            player.setGameMode(GameMode.SPECTATOR);
            BukkitTask task = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                this.scheduledChallenges.remove(playerId);
                if (!this.closed && player.isOnline()) {
                    if (this.isBypassed(player)) {
                        this.releaseForBypass(player);
                    } else {
                        this.sendServerHello(player);
                    }
                }
            }, 100L);
            this.scheduledChallenges.put(playerId, task);
        }
    }

    private boolean isBypassed(Player player) {
        return player.isOp() || player.hasPermission("arenasystem.client.bypass");
    }

    private void sendServerHello(Player player) {
        UUID playerId = player.getUniqueId();
        UUID sessionId = UUID.randomUUID();
        byte[] serverNonce = CompanionHandshakeProtocol.randomBytes(32);
        long timeoutSeconds = Math.max(
            5L, Math.min(60L, this.plugin.getConfig().getLong("client-mod.timeout-seconds", 10L))
        );
        BukkitTask timeoutTask = Bukkit.getScheduler()
            .runTaskLater(this.plugin, () -> this.handleTimeout(playerId, sessionId), timeoutSeconds * 20L);
        CompanionClientListener.PendingHandshake handshake = new CompanionClientListener.PendingHandshake(
            sessionId, serverNonce, timeoutTask
        );
        this.pending.put(playerId, handshake);
        boolean listening = player.getListeningPluginChannels().contains("arenasystem:mod_handshake");
        this.plugin.getLogger().info("[ClientMod-Debug] " + player.getName() + " listening=" + listening);
        this.sendMessage(player, CompanionHandshakeProtocol.encodeServerHello(sessionId, serverNonce));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] payload) {
        if (!this.closed && "arenasystem:mod_handshake".equals(channel) && player != null && player.isOnline()) {
            CompanionClientListener.PendingHandshake handshake = this.pending.get(player.getUniqueId());
            if (handshake != null) {
                CompanionHandshakeProtocol.Message message = CompanionHandshakeProtocol.decode(payload);
                if (message == null) {
                    this.plugin
                        .getLogger()
                        .fine("[ClientMod] Ignoring malformed handshake packets from " + player.getName() + ".");
                } else {
                    this.plugin
                        .getLogger()
                        .info(
                            "[ClientMod-Debug] Received "
                                + message.type()
                                + " from "
                                + player.getName()
                                + ", stage="
                                + handshake.stage
                        );
                    if (!handshake.sessionId.equals(message.sessionId())) {
                        this.plugin
                            .getLogger()
                            .fine(
                                "[ClientMod] Ignoring packages from old sessions belonging to "
                                    + player.getName()
                                    + "."
                            );
                    } else if (message.protocolVersion() != 2) {
                        this.rejectIncompatibleClient(player, handshake, message.protocolVersion());
                    } else {
                        switch (message.type()) {
                            case CLIENT_HELLO:
                                this.handleClientHello(player, handshake, message.body());
                                break;
                            case AUTHENTICATE:
                                this.handleAuthenticate(player, handshake, message.body());
                                break;
                            case CONNECTION_CHECK:
                                this.handleConnectionCheck(player, handshake, message.body());
                                break;
                            default:
                                this.plugin
                                    .getLogger()
                                    .fine(
                                        "[ClientMod] Ignoring a message "
                                            + message.type()
                                            + " that does not align with the direction of "
                                            + player.getName()
                                            + "."
                                    );
                        }
                    }
                }
            }
        }
    }

    private void handleClientHello(Player player, CompanionClientListener.PendingHandshake handshake, byte[] body) {
        if (handshake.stage == CompanionClientListener.Stage.WAITING_CLIENT_HELLO && body.length == 64) {
            byte[] echoedServerNonce = Arrays.copyOfRange(body, 0, 32);
            if (!CompanionHandshakeProtocol.constantTimeEquals(handshake.serverNonce, echoedServerNonce)) {
                this.plugin
                    .getLogger()
                    .fine("[ClientMod] CLIENT_HELLO does not match the active challenge from " + player.getName() + ".");
            } else {
                handshake.clientNonce = Arrays.copyOfRange(body, 32, body.length);
                handshake.sessionSecret = CompanionHandshakeProtocol.randomBytes(32);
                handshake.authNonce = CompanionHandshakeProtocol.randomBytes(32);
                handshake.stage = CompanionClientListener.Stage.WAITING_AUTHENTICATE;
                this.sendMessage(
                    player,
                    CompanionHandshakeProtocol.encodeAuthChallenge(
                        handshake.sessionId, handshake.sessionSecret, handshake.authNonce
                    )
                );
            }
        }
    }

    private void handleAuthenticate(Player player, CompanionClientListener.PendingHandshake handshake, byte[] body) {
        if (handshake.stage == CompanionClientListener.Stage.WAITING_AUTHENTICATE && body.length == 32) {
            byte[] expectedProof = CompanionHandshakeProtocol.createClientAuthProof(
                handshake.sessionSecret,
                handshake.sessionId,
                handshake.serverNonce,
                handshake.clientNonce,
                handshake.authNonce
            );
            if (!CompanionHandshakeProtocol.constantTimeEquals(expectedProof, body)) {
                this.plugin.getLogger().fine("[ClientMod] AUTHENTICATE is invalid from " + player.getName() + ".");
            } else {
                handshake.checkNonce = CompanionHandshakeProtocol.randomBytes(32);
                handshake.clientCheckProof = CompanionHandshakeProtocol.createClientCheckProof(
                    handshake.sessionSecret,
                    handshake.sessionId,
                    handshake.serverNonce,
                    handshake.clientNonce,
                    handshake.authNonce,
                    handshake.checkNonce
                );
                byte[] serverProof = CompanionHandshakeProtocol.createServerChallengeProof(
                    handshake.sessionSecret,
                    handshake.sessionId,
                    handshake.serverNonce,
                    handshake.clientNonce,
                    handshake.authNonce,
                    handshake.checkNonce
                );
                handshake.stage = CompanionClientListener.Stage.WAITING_CONNECTION_CHECK;
                this.sendMessage(
                    player,
                    CompanionHandshakeProtocol.encodeServerChallenge(
                        handshake.sessionId, handshake.checkNonce, serverProof
                    )
                );
            }
        }
    }

    private void handleConnectionCheck(Player player, CompanionClientListener.PendingHandshake handshake, byte[] body) {
        if (handshake.stage == CompanionClientListener.Stage.WAITING_CONNECTION_CHECK && body.length == 32) {
            if (!CompanionHandshakeProtocol.constantTimeEquals(handshake.clientCheckProof, body)) {
                this.plugin.getLogger().fine("[ClientMod] CONNECTION_CHECK is Invalid from " + player.getName() + ".");
            } else {
                byte[] serverAckProof = CompanionHandshakeProtocol.createServerAckProof(
                    handshake.sessionSecret,
                    handshake.sessionId,
                    handshake.serverNonce,
                    handshake.clientNonce,
                    handshake.authNonce,
                    handshake.checkNonce,
                    handshake.clientCheckProof
                );
                this.pending.remove(player.getUniqueId());
                handshake.timeoutTask.cancel();
                this.gatedPlayers.remove(player.getUniqueId());
                GameMode defaultGameMode = this.plugin.getServer().getDefaultGameMode();
                player.setGameMode(defaultGameMode);
                this.sendMessage(
                    player, CompanionHandshakeProtocol.encodeConnectionAck(handshake.sessionId, serverAckProof)
                );
                this.plugin
                    .getLogger()
                    .info(
                        "[ClientMod] "
                            + player.getName()
                            + " Successfully completed the Handshake Protocol v2; gamemode has been reset to the server's default setting ("
                            + defaultGameMode
                            + ")."
                    );
            }
        }
    }

    private void rejectIncompatibleClient(
        Player player, CompanionClientListener.PendingHandshake handshake, int clientVersion
    ) {
        this.pending.remove(player.getUniqueId());
        handshake.timeoutTask.cancel();
        this.plugin
            .getLogger()
            .warning(
                "[ClientMod] "
                    + player.getName()
                    + " uses protocol "
                    + clientVersion
                    + ", while the server requires protocol 2."
            );
        player.kickPlayer(
            ChatColor.RED
                + "[ArenaSystem] The companion client version is incompatible.\n"
                + ChatColor.YELLOW
                + "Update the ArenaSystem Client Mod, then log in again."
        );
    }

    private void releaseForBypass(Player player) {
        UUID playerId = player.getUniqueId();
        this.cancelPendingHandshake(playerId);
        this.gatedPlayers.remove(playerId);
        player.setGameMode(this.plugin.getServer().getDefaultGameMode());
        this.plugin
            .getLogger()
            .info("[ClientMod] " + player.getName() + " bypassing verification via permission bypass.");
    }

    private void sendMessage(Player player, byte[] payload) {
        try {
            player.sendPluginMessage(this.plugin, "arenasystem:mod_handshake", payload);
        } catch (RuntimeException exception) {
            this.plugin
                .getLogger()
                .warning(
                    "[ClientMod] Failed to send a handshake message to "
                        + player.getName()
                        + ": "
                        + exception.getMessage()
                );
        }
    }

    private void handleTimeout(UUID playerId, UUID sessionId) {
        CompanionClientListener.PendingHandshake handshake = this.pending.get(playerId);
        if (handshake != null && handshake.sessionId.equals(sessionId)) {
            this.pending.remove(playerId);
            this.gatedPlayers.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                this.plugin
                    .getLogger()
                    .warning(
                        "[ClientMod] "
                            + player.getName()
                            + " Rejected because the Handshake Protocol v2 did not complete before the timeout."
                    );
                player.kickPlayer(
                    ChatColor.RED
                        + "[ArenaSystem] Failed to verify the client mod.\n"
                        + ChatColor.YELLOW
                        + "Install or update the Fabric API and ArenaSystem Client Mod, then log in again."
                );
            }
        }
    }

    private void cancelScheduledChallenge(UUID playerId) {
        BukkitTask task = this.scheduledChallenges.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelPendingHandshake(UUID playerId) {
        CompanionClientListener.PendingHandshake handshake = this.pending.remove(playerId);
        if (handshake != null) {
            handshake.timeoutTask.cancel();
        }
    }

    private boolean isGated(Player player) {
        return this.gatedPlayers.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPendingMove(PlayerMoveEvent event) {
        if (this.isGated(event.getPlayer()) && event.getTo() != null) {
            Location from = event.getFrom();
            Location to = event.getTo();
            boolean positionChanged = from.getWorld() != to.getWorld()
                || from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ();
            if (positionChanged) {
                event.setTo(
                    new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch())
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPendingCommand(PlayerCommandPreprocessEvent event) {
        if (this.isGated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPendingInteract(PlayerInteractEvent event) {
        if (this.isGated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPendingEntityInteract(PlayerInteractEntityEvent event) {
        if (this.isGated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    void shutdown() {
        if (!this.closed) {
            this.closed = true;

            for (BukkitTask task : this.scheduledChallenges.values()) {
                task.cancel();
            }

            this.scheduledChallenges.clear();

            for (CompanionClientListener.PendingHandshake handshake : this.pending.values()) {
                handshake.timeoutTask.cancel();
            }

            this.pending.clear();
            GameMode defaultGameMode = this.plugin.getServer().getDefaultGameMode();

            for (UUID playerId : new HashSet<>(this.gatedPlayers)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.setGameMode(defaultGameMode);
                }
            }

            this.gatedPlayers.clear();
            Messenger messenger = this.plugin.getServer().getMessenger();
            messenger.unregisterIncomingPluginChannel(this.plugin, "arenasystem:mod_handshake", this);
            messenger.unregisterOutgoingPluginChannel(this.plugin, "arenasystem:mod_handshake");
        }
    }

    // $VF: Compiled from CompanionClientListener.java
    private static final class PendingHandshake {
        private final UUID sessionId;
        private final byte[] serverNonce;
        private final BukkitTask timeoutTask;
        private CompanionClientListener.Stage stage = CompanionClientListener.Stage.WAITING_CLIENT_HELLO;
        private byte[] clientNonce;
        private byte[] sessionSecret;
        private byte[] authNonce;
        private byte[] checkNonce;
        private byte[] clientCheckProof;

        private PendingHandshake(UUID sessionId, byte[] serverNonce, BukkitTask timeoutTask) {
            this.sessionId = sessionId;
            this.serverNonce = (byte[])serverNonce.clone();
            this.timeoutTask = timeoutTask;
        }
    }

    // $VF: Compiled from CompanionClientListener.java
    private enum Stage {
        WAITING_CLIENT_HELLO,
        WAITING_AUTHENTICATE,
        WAITING_CONNECTION_CHECK;
    }
}
