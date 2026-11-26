package id.hadziq.arenasystem;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks the public release manifest and informs server admins; it never downloads or installs files. */
final class PluginUpdateNotifier implements Listener {
    private static final URI DEFAULT_MANIFEST_URI = URI.create(
            "https://raw.githubusercontent.com/au3891427-wq/proyek-arenasystem-plugin/main/arena-system-update-manifest.json");
    private static final String EXPECTED_RELEASE_PREFIX =
            "https://github.com/au3891427-wq/proyek-arenasystem-plugin/releases/tag/arena-system-v";
    private static final long MAX_MANIFEST_BYTES = 32L * 1024L;
    private static final Pattern STRING_FIELD = Pattern.compile(
            "\\\"([A-Za-z][A-Za-z0-9]*)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
    private static final Pattern SCHEMA_VERSION_ONE = Pattern.compile(
            "\\\"schemaVersion\\\"\\s*:\\s*1(?:\\s*[,}])");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final JavaPlugin plugin;
    private final URI manifestUri;
    private volatile AvailableUpdate availableUpdate;
    private volatile String lastLoggedVersion;
    private BukkitTask checkTask;

    private record AvailableUpdate(String version, String releaseUrl) { }

    PluginUpdateNotifier(JavaPlugin plugin) {
        this.plugin = plugin;
        URI configuredUri;
        try {
            configuredUri = parseManifestUri(
                    plugin.getConfig().getString("updates.manifest-url", DEFAULT_MANIFEST_URI.toString()));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning(exception.getMessage() + " Using the default manifest URL.");
            configuredUri = DEFAULT_MANIFEST_URI;
        }
        this.manifestUri = configuredUri;
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        long intervalHours = Math.max(1L,
                Math.min(168L, plugin.getConfig().getLong("updates.check-interval-hours", 6L)));
        long intervalTicks = intervalHours * 60L * 60L * 20L;
        checkTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::checkForUpdates, 20L, intervalTicks);
    }

    void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    private void checkForUpdates() {
        try {
            AvailableUpdate remote = fetchAvailableUpdate();
            String installedVersion = plugin.getDescription().getVersion();
            if (compareVersions(remote.version(), installedVersion) <= 0) {
                availableUpdate = null;
                lastLoggedVersion = null;
                return;
            }

            availableUpdate = remote;
            if (!remote.version().equals(lastLoggedVersion)) {
                lastLoggedVersion = remote.version();
                Bukkit.getScheduler().runTask(plugin, () -> announceUpdate(remote));
            }
        } catch (Exception exception) {
            plugin.getLogger().fine("Pemeriksaan update gagal: " + exception.getMessage());
        }
    }

    private AvailableUpdate fetchAvailableUpdate() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(manifestUri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("User-Agent", "ArenaSystem-Paper-Update-Notifier")
                .GET()
                .build();

        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Manifest returned HTTP " + response.statusCode());
        }
        if (!"https".equalsIgnoreCase(response.uri().getScheme())
                || !"raw.githubusercontent.com".equalsIgnoreCase(response.uri().getHost())) {
            response.body().close();
            throw new IOException("Manifest response came from an untrusted host.");
        }

        String json;
        try (InputStream body = response.body()) {
            byte[] bytes = body.readNBytes((int) MAX_MANIFEST_BYTES + 1);
            if (bytes.length > MAX_MANIFEST_BYTES) {
                throw new IOException("Manifest exceeds the 32 KiB size limit.");
            }
            json = new String(bytes, StandardCharsets.UTF_8);
        }

        var fields = new java.util.HashMap<String, String>();
        Matcher matcher = STRING_FIELD.matcher(json);
        while (matcher.find()) fields.put(matcher.group(1), matcher.group(2));

        if (!SCHEMA_VERSION_ONE.matcher(json).find()) {
            throw new IOException("Unsupported or missing update manifest schemaVersion.");
        }
        if (!"ArenaSystem".equals(fields.get("plugin"))) {
            throw new IOException("Manifest plugin name mismatch.");
        }
        String version = fields.get("version");
        String releaseUrl = fields.get("releaseUrl");
        if (version == null || releaseUrl == null) {
            throw new IOException("Manifest is missing version or releaseUrl.");
        }
        parseVersion(version);
        String expectedUrl = EXPECTED_RELEASE_PREFIX + version;
        if (!expectedUrl.equals(releaseUrl)) {
            throw new IOException("Manifest release URL does not match its version.");
        }
        return new AvailableUpdate(version, releaseUrl);
    }

    private void announceUpdate(AvailableUpdate update) {
        plugin.getLogger().warning("Update ArenaSystem tersedia: " + update.version()
                + " (terpasang: " + plugin.getDescription().getVersion() + ").");
        plugin.getLogger().warning("Unduh manual: " + update.releaseUrl());
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendNotice(player, update);
        }
    }

    @EventHandler
    public void onAdminJoin(PlayerJoinEvent event) {
        AvailableUpdate update = availableUpdate;
        if (update != null) sendNotice(event.getPlayer(), update);
    }

    private void sendNotice(Player player, AvailableUpdate update) {
        if (!player.isOp() && !player.hasPermission("arenasystem.admin")) return;
        player.sendMessage(ChatColor.GOLD + "[ArenaSystem] Update tersedia: "
                + ChatColor.YELLOW + update.version());
        player.sendMessage(ChatColor.GRAY + "Unduh manual: " + ChatColor.AQUA + update.releaseUrl());
        player.sendMessage(ChatColor.GRAY + "Plugin tidak diperbarui otomatis; pasang JAR saat server mati.");
    }

    private static URI parseManifestUri(String value) {
        try {
            URI uri = URI.create(value == null || value.isBlank()
                    ? DEFAULT_MANIFEST_URI.toString() : value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"raw.githubusercontent.com".equalsIgnoreCase(uri.getHost())
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || uri.getRawQuery() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new IllegalArgumentException("updates.manifest-url must be an HTTPS raw.githubusercontent.com URL.");
            }
            return uri;
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
            if (result != 0) return result;
        }
        return 0;
    }

    private static BigInteger[] parseVersion(String value) throws IOException {
        if (value == null || !value.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(\\.(0|[1-9][0-9]*))?")) {
            throw new IOException("Invalid ArenaSystem semantic version: " + value);
        }
        String[] parts = value.split("\\.");
        BigInteger[] parsed = new BigInteger[parts.length];
        for (int i = 0; i < parts.length; i++) parsed[i] = new BigInteger(parts[i]);
        return parsed;
    }
}
