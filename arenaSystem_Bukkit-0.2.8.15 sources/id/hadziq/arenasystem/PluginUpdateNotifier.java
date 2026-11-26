package id.hadziq.arenasystem;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

// $VF: Compiled from PluginUpdateNotifier.java
final class PluginUpdateNotifier implements Listener {
    private static final URI DEFAULT_MANIFEST_URI = URI.create(
        "https://raw.githubusercontent.com/au3891427-wq/Arena-System-Plugins-Client-and-Server/main/arenasystem-server-update-manifest.json"
    );
    private static final String EXPECTED_RELEASE_URL = "https://github.com/au3891427-wq/Arena-System-Plugins-Client-and-Server/releases/tag/Bukkit";
    private static final String EXPECTED_DOWNLOAD_PREFIX = "https://github.com/au3891427-wq/Arena-System-Plugins-Client-and-Server/releases/download/Bukkit/";
    private static final long MAX_MANIFEST_BYTES = 32768L;
    private static final Pattern STRING_FIELD = Pattern.compile(
        "\\\"([A-Za-z][A-Za-z0-9]*)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\""
    );
    private static final Pattern SCHEMA_VERSION_ONE = Pattern.compile("\\\"schemaVersion\\\"\\s*:\\s*1(?:\\s*[,}])");
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10L))
        .followRedirects(Redirect.NEVER)
        .build();
    private final JavaPlugin plugin;
    private final URI manifestUri;
    private volatile PluginUpdateNotifier.AvailableUpdate availableUpdate;
    private volatile String lastLoggedVersion;
    private BukkitTask checkTask;

    PluginUpdateNotifier(JavaPlugin plugin) {
        this.plugin = plugin;

        URI configuredUri;
        try {
            configuredUri = parseManifestUri(
                plugin.getConfig().getString("updates.manifest-url", DEFAULT_MANIFEST_URI.toString())
            );
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning(exception.getMessage() + " Using the default manifest URL.");
            configuredUri = DEFAULT_MANIFEST_URI;
        }

        this.manifestUri = configuredUri;
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        long intervalHours = Math.max(
            1L, Math.min(168L, this.plugin.getConfig().getLong("updates.check-interval-hours", 6L))
        );
        long intervalTicks = intervalHours * 60L * 60L * 20L;
        this.checkTask = Bukkit.getScheduler()
            .runTaskTimerAsynchronously(this.plugin, this::checkForUpdates, 20L, intervalTicks);
    }

    void stop() {
        if (this.checkTask != null) {
            this.checkTask.cancel();
            this.checkTask = null;
        }
    }

    private void checkForUpdates() {
        try {
            PluginUpdateNotifier.AvailableUpdate remote = this.fetchAvailableUpdate();
            String installedVersion = this.plugin.getDescription().getVersion();
            if (compareVersions(remote.version(), installedVersion) <= 0) {
                this.availableUpdate = null;
                this.lastLoggedVersion = null;
                return;
            }

            this.availableUpdate = remote;
            if (!remote.version().equals(this.lastLoggedVersion)) {
                this.lastLoggedVersion = remote.version();
                Bukkit.getScheduler().runTask(this.plugin, () -> this.announceUpdate(remote));
            }
        } catch (Exception exception) {
            this.plugin.getLogger().fine("Pemeriksaan update gagal: " + exception.getMessage());
        }
    }

    private PluginUpdateNotifier.AvailableUpdate fetchAvailableUpdate() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(this.manifestUri)
            .timeout(Duration.ofSeconds(15L))
            .header("Accept", "application/json")
            .header("User-Agent", "ArenaSystem-Paper-Update-Notifier")
            .GET()
            .build();
        HttpResponse<InputStream> response = HTTP.send(request, BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Manifest returned HTTP " + response.statusCode());
        }

        if ("https".equalsIgnoreCase(response.uri().getScheme())
            && "raw.githubusercontent.com".equalsIgnoreCase(response.uri().getHost())) {
            String json;
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(32769);
                if (bytes.length > 32768L) {
                    throw new IOException("Manifest exceeds the 32 KiB size limit.");
                }

                json = new String(bytes, StandardCharsets.UTF_8);
            }

            HashMap<String, String> fields = new HashMap<>();
            Matcher matcher = STRING_FIELD.matcher(json);

            while (matcher.find()) {
                fields.put(matcher.group(1), matcher.group(2));
            }

            if (!SCHEMA_VERSION_ONE.matcher(json).find()) {
                throw new IOException("Unsupported or missing update manifest schemaVersion.");
            }

            if (!"ArenaSystem".equals(fields.get("plugin"))) {
                throw new IOException("Manifest plugin name mismatch.");
            }

            String channel = fields.get("channel");
            String releaseTag = fields.get("releaseTag");
            String version = fields.get("version");
            String releaseUrl = fields.get("releaseUrl");
            String downloadUrl = fields.get("downloadUrl");
            String sha256 = fields.get("sha256");
            if (sha256 == null || !sha256.matches("(?i)[a-f0-9]{64}")) {
                throw new IOException("Manifest contains a missing or invalid server JAR SHA-256.");
            }

            if (!"stable".equals(channel)) {
                throw new IOException("Refusing a non-stable server update manifest.");
            }

            if (!"Bukkit".equals(releaseTag)) {
                throw new IOException("Manifest release tag is not the stable Bukkit release.");
            }

            if (version != null && releaseUrl != null && downloadUrl != null) {
                parseVersion(version);
                String expectedDownloadUrl = "https://github.com/au3891427-wq/Arena-System-Plugins-Client-and-Server/releases/download/Bukkit/arenaSystem_Bukkit-"
                    + version
                    + ".jar";
                if (!"https://github.com/au3891427-wq/Arena-System-Plugins-Client-and-Server/releases/tag/Bukkit"
                    .equals(releaseUrl)) {
                    throw new IOException("Manifest release URL is not the stable Bukkit release page.");
                } else if (!expectedDownloadUrl.equals(downloadUrl)) {
                    throw new IOException("Manifest download URL does not match its version.");
                } else {
                    return new PluginUpdateNotifier.AvailableUpdate(version, releaseUrl, downloadUrl);
                }
            } else {
                throw new IOException("Manifest is missing version or download links.");
            }
        } else {
            response.body().close();
            throw new IOException("Manifest response came from an untrusted host.");
        }
    }

    private void announceUpdate(PluginUpdateNotifier.AvailableUpdate update) {
        this.plugin
            .getLogger()
            .warning(
                "Update ArenaSystem tersedia: "
                    + update.version()
                    + " (terpasang: "
                    + this.plugin.getDescription().getVersion()
                    + ")."
            );
        this.plugin.getLogger().warning("Unduh JAR secara manual: " + update.downloadUrl());
        this.plugin.getLogger().warning("Catatan rilis: " + update.releaseUrl());
        this.plugin
            .getLogger()
            .warning("Tidak ada file yang diunduh atau dipasang otomatis. Pasang JAR saat server mati.");

        for (Player player : Bukkit.getOnlinePlayers()) {
            this.sendNotice(player, update);
        }
    }

    @EventHandler
    public void onAdminJoin(PlayerJoinEvent event) {
        PluginUpdateNotifier.AvailableUpdate update = this.availableUpdate;
        if (update != null) {
            this.sendNotice(event.getPlayer(), update);
        }
    }

    private void sendNotice(Player player, PluginUpdateNotifier.AvailableUpdate update) {
        if (player.isOp() || player.hasPermission("arenasystem.admin")) {
            player.sendMessage(ChatColor.GOLD + "[ArenaSystem] Update tersedia: " + ChatColor.YELLOW + update.version());
            player.sendMessage(ChatColor.GRAY + "Unduh JAR manual: " + ChatColor.AQUA + update.downloadUrl());
            player.sendMessage(ChatColor.GRAY + "Catatan rilis: " + ChatColor.AQUA + update.releaseUrl());
            player.sendMessage(ChatColor.GRAY + "Plugin tidak diperbarui otomatis; pasang JAR saat server mati.");
        }
    }

    private static URI parseManifestUri(String value) {
        try {
            URI uri = URI.create(value != null && !value.isBlank() ? value : DEFAULT_MANIFEST_URI.toString());
            if ("https".equalsIgnoreCase(uri.getScheme())
                && "raw.githubusercontent.com".equalsIgnoreCase(uri.getHost())
                && uri.getRawUserInfo() == null
                && uri.getRawFragment() == null
                && uri.getRawQuery() == null
                && (uri.getPort() == -1 || uri.getPort() == 443)) {
                return uri;
            } else {
                throw new IllegalArgumentException(
                    "updates.manifest-url must be an HTTPS raw.githubusercontent.com URL."
                );
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid updates.manifest-url configuration.", exception);
        }
    }

    private static int compareVersions(String left, String right) throws IOException {
        BigInteger[] a = parseVersion(left);
        BigInteger[] b = parseVersion(right);

        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            BigInteger av = i < a.length ? a[i] : BigInteger.ZERO;
            BigInteger bv = i < b.length ? b[i] : BigInteger.ZERO;
            int result = av.compareTo(bv);
            if (result != 0) {
                return result;
            }
        }

        return 0;
    }

    private static BigInteger[] parseVersion(String value) throws IOException {
        if (value != null && value.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(\\.(0|[1-9][0-9]*))?")) {
            String[] parts = value.split("\\.");
            BigInteger[] parsed = new BigInteger[parts.length];

            for (int i = 0; i < parts.length; i++) {
                parsed[i] = new BigInteger(parts[i]);
            }

            return parsed;
        } else {
            throw new IOException("Invalid ArenaSystem semantic version: " + value);
        }
    }

    // $VF: Compiled from PluginUpdateNotifier.java
    private record AvailableUpdate(String version, String releaseUrl, String downloadUrl) {
    }
}
