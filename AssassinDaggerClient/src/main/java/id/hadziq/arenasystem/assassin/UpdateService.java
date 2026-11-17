package id.hadziq.arenasystem.assassin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
    private static final String HELPER_JAR = "assassin-dagger-updater-0.1.0.jar";
    private static final String RELEASE_PATH_PREFIX =
            "/au3891427-wq/proyek-arenasystem-plugin/releases/download/";
    private static final URI MANIFEST_URI = URI.create(
            "https://github.com/au3891427-wq/proyek-arenasystem-plugin/releases/latest/download/update-manifest.json");
    private static final URI HELPER_URI = URI.create(
            "https://github.com/au3891427-wq/proyek-arenasystem-plugin/releases/latest/download/" + HELPER_JAR);
    private static final long MAX_MANIFEST_BYTES = 64L * 1024;
    private static final long MAX_MOD_BYTES = 64L * 1024 * 1024;
    private static final long MAX_HELPER_BYTES = 8L * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private UpdateService() { }

    static void checkAndStage(FabricLoader loader) throws Exception {
        Optional<ModContainer> localMod = loader.getModContainer(MOD_ID);
        if (localMod.isEmpty()) return;

        JsonObject manifest = fetchManifest();
        validateManifest(manifest);

        String remoteVersion = requiredString(manifest, "version");
        String localVersion = localMod.get().getMetadata().getVersion().getFriendlyString();
        BigInteger[] remoteParts = parseStableVersion(remoteVersion, "manifest version");
        BigInteger[] localParts = parseStableVersion(localVersion, "installed mod version");

        String gameVersion = loader.getRawGameVersion();
        if (!requiredString(manifest, "minecraft").equals(gameVersion)) {
            System.out.println("[ArenaSystem] Update skipped: incompatible Minecraft version " + gameVersion);
            return;
        }
        if (compareVersions(remoteParts, localParts) <= 0) return;

        Path target = localMod.get().getOrigin().getPaths().stream()
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .findFirst()
                .orElseThrow(() -> new IOException("Could not locate installed mod JAR."))
                .toAbsolutePath().normalize();
        if (!MOD_JAR.equalsIgnoreCase(target.getFileName().toString())) {
            throw new IOException("Unexpected installed mod filename: " + target.getFileName());
        }
        Path modsDirectory = target.getParent();
        if (modsDirectory == null || !"mods".equalsIgnoreCase(modsDirectory.getFileName().toString())) {
            throw new IOException("Mod must be installed directly inside a mods directory.");
        }

        URI downloadUri = validateReleaseAssetUrl(requiredString(manifest, "downloadUrl"), MOD_JAR);
        String expectedHash = requiredString(manifest, "sha256").toLowerCase(Locale.ROOT);
        if (!expectedHash.matches("[a-f0-9]{64}")) {
            throw new IOException("Manifest SHA-256 is malformed.");
        }

        Path staged = modsDirectory.resolve(MOD_JAR + ".pending");
        downloadVerified(downloadUri, staged, expectedHash, MAX_MOD_BYTES, MOD_JAR);
        downloadHelper(loader.getGameDir(), requiredString(manifest, "helperSha256"));

        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> launchHelper(target, staged, expectedHash, loader.getGameDir()),
                "ArenaSystem-Update-Installer"));
        System.out.println("[ArenaSystem] Update " + remoteVersion
                + " downloaded and verified; installation is queued for game shutdown.");
    }

    private static JsonObject fetchManifest() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(MANIFEST_URI)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "ArenaSystem-AssassinDagger-Updater")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Manifest request returned HTTP " + response.statusCode());
        }

        validateGitHubRedirect(response.uri());
        try (InputStream body = response.body()) {
            byte[] bytes = body.readNBytes((int) MAX_MANIFEST_BYTES + 1);
            if (bytes.length > MAX_MANIFEST_BYTES) {
                throw new IOException("Manifest exceeds the 64 KiB size limit.");
            }
            try {
                var parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) throw new IOException("Manifest must be a JSON object.");
                return parsed.getAsJsonObject();
            } catch (JsonParseException | IllegalStateException exception) {
                throw new IOException("Manifest contains invalid JSON.", exception);
            }
        }
    }

    private static void validateManifest(JsonObject manifest) throws IOException {
        if (!manifest.has("schemaVersion")
                || !manifest.get("schemaVersion").isJsonPrimitive()
                || !manifest.get("schemaVersion").getAsJsonPrimitive().isNumber()
                || !"1".equals(manifest.get("schemaVersion").getAsString())) {
            throw new IOException("Unsupported or missing manifest schemaVersion.");
        }
        if (!MOD_ID.equals(requiredString(manifest, "modId"))) {
            throw new IOException("Manifest modId mismatch.");
        }
        if (!"stable".equals(requiredString(manifest, "channel"))) {
            throw new IOException("Refusing non-stable update channel.");
        }
        if (!"fabric".equals(requiredString(manifest, "loader"))) {
            throw new IOException("Manifest loader is not Fabric.");
        }
        parseStableVersion(requiredString(manifest, "version"), "manifest version");
        validateReleaseAssetUrl(requiredString(manifest, "downloadUrl"), MOD_JAR);
        String modHash = requiredString(manifest, "sha256");
        String helperHash = requiredString(manifest, "helperSha256");
        if (!modHash.matches("(?i)[a-f0-9]{64}") || !helperHash.matches("(?i)[a-f0-9]{64}")) {
            throw new IOException("Manifest contains a malformed SHA-256 value.");
        }
    }

    private static String requiredString(JsonObject object, String key) throws IOException {
        if (!object.has(key)
                || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isString()) {
            throw new IOException("Missing or invalid manifest field: " + key);
        }
        String value = object.get(key).getAsString();
        if (value.isBlank()) throw new IOException("Manifest field is blank: " + key);
        return value;
    }

    private static URI validateReleaseAssetUrl(String value, String expectedAsset) throws IOException {
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Manifest download URL is malformed.", exception);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"github.com".equalsIgnoreCase(uri.getHost())
                || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IOException("Download URL must be a plain HTTPS GitHub release URL.");
        }

        String path = uri.getPath();
        if (path == null || !path.startsWith(RELEASE_PATH_PREFIX)
                || !path.endsWith("/" + expectedAsset)) {
            throw new IOException("Download URL does not point to the expected release asset.");
        }
        String tag = path.substring(RELEASE_PATH_PREFIX.length(), path.length() - expectedAsset.length() - 1);
        if (tag.isBlank() || tag.contains("/")) {
            throw new IOException("Download URL contains an invalid release tag.");
        }
        return uri;
    }

    private static void validateGitHubRedirect(URI uri) throws IOException {
        String host = uri.getHost();
        boolean allowedHost = host != null && (
                host.equalsIgnoreCase("github.com")
                        || host.equalsIgnoreCase("release-assets.githubusercontent.com")
                        || host.equalsIgnoreCase("objects.githubusercontent.com"));
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowedHost) {
            throw new IOException("GitHub download redirected to an untrusted host.");
        }
    }

    private static void downloadHelper(Path gameDirectory, String helperHash)
            throws IOException, InterruptedException {
        if (!helperHash.matches("(?i)[a-f0-9]{64}")) {
            throw new IOException("Helper SHA-256 is malformed.");
        }
        Path helper = gameDirectory.resolve(".arenasystem/updater").resolve(HELPER_JAR);
        Path staged = helper.resolveSibling(helper.getFileName() + ".download");
        Files.createDirectories(helper.getParent());
        downloadVerified(HELPER_URI, staged, helperHash, MAX_HELPER_BYTES, HELPER_JAR);
        Files.move(staged, helper, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void downloadVerified(
            URI uri, Path destination, String expectedHash, long maxBytes, String expectedAsset)
            throws IOException, InterruptedException {
        if (uri.equals(HELPER_URI)) {
            validateGitHubRedirect(uri);
        } else {
            validateReleaseAssetUrl(uri.toString(), expectedAsset);
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "ArenaSystem-AssassinDagger-Updater")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Download returned HTTP " + response.statusCode());
        }
        validateGitHubRedirect(response.uri());

        Path temporary = destination.resolveSibling(destination.getFileName() + ".part");
        MessageDigest digest = sha256Digest();
        long total = 0;
        try (InputStream input = response.body(); var output = Files.newOutputStream(temporary)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IOException("Download exceeds size limit.");
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        } catch (IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }

        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expectedHash)) {
            Files.deleteIfExists(temporary);
            throw new IOException("SHA-256 verification failed.");
        }
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception exception) {
            throw new IOException("SHA-256 unavailable.", exception);
        }
    }

    private static void launchHelper(Path target, Path staged, String hash, Path gameDirectory) {
        try {
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                System.err.println("[ArenaSystem] Automatic installation is currently Windows-only; verified update remains staged.");
                return;
            }
            Path helper = gameDirectory.resolve(".arenasystem/updater").resolve(HELPER_JAR);
            if (!Files.isRegularFile(helper) || !Files.isRegularFile(staged)) {
                throw new IOException("Helper or staged JAR is missing.");
            }
            String javaw = Path.of(System.getProperty("java.home"), "bin", "javaw.exe").toString();
            new ProcessBuilder(javaw, "-jar", helper.toString(),
                    Long.toString(ProcessHandle.current().pid()), target.toString(), staged.toString(), hash)
                    .directory(gameDirectory.toFile())
                    .start();
        } catch (Exception exception) {
            System.err.println("[ArenaSystem] Could not start updater helper: " + exception.getMessage());
        }
    }

    private static BigInteger[] parseStableVersion(String value, String field) throws IOException {
        if (!value.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")) {
            throw new IOException("Invalid stable semantic version in " + field + ": " + value);
        }
        String[] parts = value.split("\\.");
        try {
            return new BigInteger[] {
                    new BigInteger(parts[0]),
                    new BigInteger(parts[1]),
                    new BigInteger(parts[2])
            };
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid version number in " + field + ".", exception);
        }
    }

    private static int compareVersions(BigInteger[] left, BigInteger[] right) {
        for (int i = 0; i < 3; i++) {
            int result = left[i].compareTo(right[i]);
            if (result != 0) return result;
        }
        return 0;
    }
}
