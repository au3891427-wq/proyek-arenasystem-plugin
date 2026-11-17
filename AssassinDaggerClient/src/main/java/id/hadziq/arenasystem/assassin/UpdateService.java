package id.hadziq.arenasystem.assassin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

final class UpdateService {
    private static final String MOD_ID = "arenasystem_assassin_dagger";
    private static final String MOD_JAR = "arenasystem-assassin-dagger-client.jar";
    private static final URI MANIFEST_URI = URI.create("https://github.com/au3891427-wq/proyek-arenasystem-plugin/releases/latest/download/update-manifest.json");
    private static final URI HELPER_URI = URI.create("https://github.com/au3891427-wq/proyek-arenasystem-plugin/releases/latest/download/assassin-dagger-updater-0.1.0.jar");
    private static final long MAX_MOD_BYTES = 64L * 1024 * 1024;
    private static final long MAX_HELPER_BYTES = 8L * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private UpdateService() { }

    static void checkAndStage(FabricLoader loader) throws Exception {
        Optional<ModContainer> localMod = loader.getModContainer(MOD_ID);
        if (localMod.isEmpty()) return;

        JsonObject manifest = fetchManifest();
        validateManifest(manifest);
        String remoteVersion = requiredString(manifest, "version");
        String localVersion = localMod.get().getMetadata().getVersion().getFriendlyString();
        String gameVersion = loader.getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        if (!requiredString(manifest, "minecraft").equals(gameVersion)) {
            System.out.println("[ArenaSystem] Update skipped: incompatible Minecraft version " + gameVersion);
            return;
        }
        if (compareVersions(remoteVersion, localVersion) <= 0) return;

        Path target = localMod.get().getOrigin().getPaths().stream()
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .findFirst().orElseThrow(() -> new IOException("Could not locate installed mod JAR."))
                .toAbsolutePath().normalize();
        if (!MOD_JAR.equalsIgnoreCase(target.getFileName().toString())) throw new IOException("Unexpected installed mod filename: " + target.getFileName());
        Path modsDirectory = target.getParent();
        if (modsDirectory == null || !"mods".equalsIgnoreCase(modsDirectory.getFileName().toString())) throw new IOException("Mod must be installed directly inside a mods directory.");

        String downloadUrl = requiredString(manifest, "downloadUrl");
        String expectedHash = requiredString(manifest, "sha256").toLowerCase(Locale.ROOT);
        if (!expectedHash.matches("[a-f0-9]{64}")) throw new IOException("Manifest SHA-256 is malformed.");
        Path staged = modsDirectory.resolve(MOD_JAR + ".pending");
        downloadVerified(URI.create(downloadUrl), staged, expectedHash, MAX_MOD_BYTES);
        downloadHelper(loader.getGameDir(), requiredString(manifest, "helperSha256"));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> launchHelper(target, staged, expectedHash, loader.getGameDir()), "ArenaSystem-Update-Installer"));
        System.out.println("[ArenaSystem] Update " + remoteVersion + " downloaded and verified; installation is queued for game shutdown.");
    }

    private static JsonObject fetchManifest() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(MANIFEST_URI).timeout(Duration.ofSeconds(20)).header("Accept", "application/json").header("User-Agent", "ArenaSystem-AssassinDagger-Updater").GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Manifest request returned HTTP " + response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static void validateManifest(JsonObject manifest) throws IOException {
        if (!MOD_ID.equals(requiredString(manifest, "modId"))) throw new IOException("Manifest modId mismatch.");
        if (!"stable".equals(requiredString(manifest, "channel"))) throw new IOException("Refusing non-stable update channel.");
        if (!"fabric".equals(requiredString(manifest, "loader"))) throw new IOException("Manifest loader is not Fabric.");
        validateGitHubUrl(requiredString(manifest, "downloadUrl"));
    }

    private static String requiredString(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isString()) throw new IOException("Missing or invalid manifest field: " + key);
        return object.get(key).getAsString();
    }

    private static void validateGitHubUrl(String value) throws IOException {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())) throw new IOException("Download URL must use HTTPS on github.com.");
    }

    private static void downloadHelper(Path gameDirectory, String helperHash) throws IOException, InterruptedException {
        if (!helperHash.matches("(?i)[a-f0-9]{64}")) throw new IOException("Helper SHA-256 is malformed.");
        Path helper = gameDirectory.resolve(".arenasystem/updater/assassin-dagger-updater-0.1.0.jar");
        Path staged = helper.resolveSibling(helper.getFileName() + ".download");
        Files.createDirectories(helper.getParent());
        downloadVerified(HELPER_URI, staged, helperHash, MAX_HELPER_BYTES);
        Files.move(staged, helper, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void downloadVerified(URI uri, Path destination, String expectedHash, long maxBytes) throws IOException, InterruptedException {
        validateGitHubUrl(uri.toString());
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(3)).header("User-Agent", "ArenaSystem-AssassinDagger-Updater").GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) { response.body().close(); throw new IOException("Download returned HTTP " + response.statusCode()); }
        Path temporary = destination.resolveSibling(destination.getFileName() + ".part");
        MessageDigest digest = sha256Digest();
        long total = 0;
        try (InputStream input = response.body(); var output = Files.newOutputStream(temporary)) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IOException("Download exceeds size limit.");
                digest.update(buffer, 0, read); output.write(buffer, 0, read);
            }
        } catch (IOException exception) { Files.deleteIfExists(temporary); throw exception; }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expectedHash)) { Files.deleteIfExists(temporary); throw new IOException("SHA-256 verification failed."); }
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static MessageDigest sha256Digest() throws IOException {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (Exception exception) { throw new IOException("SHA-256 unavailable.", exception); }
    }

    private static void launchHelper(Path target, Path staged, String hash, Path gameDirectory) {
        try {
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                System.err.println("[ArenaSystem] Automatic installation is currently Windows-only; verified update remains staged.");
                return;
            }
            Path helper = gameDirectory.resolve(".arenasystem/updater/assassin-dagger-updater-0.1.0.jar");
            if (!Files.isRegularFile(helper) || !Files.isRegularFile(staged)) throw new IOException("Helper or staged JAR is missing.");
            String javaw = Path.of(System.getProperty("java.home"), "bin", "javaw.exe").toString();
            new ProcessBuilder(javaw, "-jar", helper.toString(), Long.toString(ProcessHandle.current().pid()), target.toString(), staged.toString(), hash).directory(gameDirectory.toFile()).start();
        } catch (Exception exception) { System.err.println("[ArenaSystem] Could not start updater helper: " + exception.getMessage()); }
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("[.+-]"); String[] b = right.split("[.+-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String x = i < a.length ? a[i] : "0"; String y = i < b.length ? b[i] : "0"; int result;
            try { result = Integer.compare(Integer.parseInt(x), Integer.parseInt(y)); }
            catch (NumberFormatException ignored) { result = x.compareToIgnoreCase(y); }
            if (result != 0) return result;
        }
        return 0;
    }
}
