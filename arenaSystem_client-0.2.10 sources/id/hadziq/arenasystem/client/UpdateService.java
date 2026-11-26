package id.hadziq.arenasystem.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

// $VF: Compiled from UpdateService.java
final class UpdateService {
    private static final String MOD_ID = "arstm_client0qb201";
    private static final String LEGACY_MOD_ID = "arenasystem_assassin_dagger";
    private static final String HELPER_JAR = "ArenaSystem_Client_Updater-0.1.0.jar";
    private static final String RELEASE_PATH_PREFIX = "/au3891427-wq/proyek-arenasystem-plugin/releases/download/";
    private static final URI MANIFEST_URI = URI.create(
        "https://raw.githubusercontent.com/au3891427-wq/proyek-arenasystem-plugin/main/arstm-client0qb201-update-manifest.json"
    );
    private static final String RELEASE_DOWNLOAD_PREFIX = "https://github.com/au3891427-wq/proyek-arenasystem-plugin/releases/download/";
    private static final String UPDATE_LOCK_FILE = ".arenasystem-assassin-dagger-update.lock";
    private static final long MAX_MANIFEST_BYTES = 65536L;
    private static final long MAX_MOD_BYTES = 67108864L;
    private static final long MAX_HELPER_BYTES = 8388608L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15L))
        .followRedirects(Redirect.NORMAL)
        .build();
    private static FileChannel updateLockChannel;
    private static FileLock updateFileLock;

    private UpdateService() {
    }

    static void checkAndStage(FabricLoader loader) throws Exception {
        Optional<ModContainer> localMod = loader.getModContainer("arstm_client0qb201")
            .or(() -> loader.getModContainer("arenasystem_assassin_dagger"));
        if (localMod.isEmpty()) {
            System.out.println("[ArenaSystem Updater] Client mod container not found; update check skipped.");
        } else {
            String installedVersion = localMod.get().getMetadata().getVersion().getFriendlyString();
            System.out
                .println(
                    "[ArenaSystem Updater] Checking updates for arstm_client0qb201 (installed "
                        + installedVersion
                        + ")."
                );
            JsonObject manifest = fetchManifest();
            validateManifest(manifest);
            String remoteVersion = requiredString(manifest, "version");
            String localVersion = localMod.get().getMetadata().getVersion().getFriendlyString();
            BigInteger[] remoteParts = parseStableVersion(remoteVersion, "manifest version");
            BigInteger[] localParts = parseStableVersion(localVersion, "installed mod version");
            String gameVersion = loader.getRawGameVersion();
            if (!requiredString(manifest, "minecraft").equals(gameVersion)) {
                System.out.println("[ArenaSystem] Update skipped: incompatible Minecraft version " + gameVersion);
            } else {
                String minimumLoaderVersion = requiredString(manifest, "loaderVersion");
                BigInteger[] minimumLoaderParts = parseStableVersion(minimumLoaderVersion, "manifest loader version");
                ModContainer loaderMod = loader.getModContainer("fabricloader")
                    .orElseThrow(() -> new IOException("Could not determine the installed Fabric Loader version."));
                String installedLoaderVersion = loaderMod.getMetadata().getVersion().getFriendlyString();
                BigInteger[] installedLoaderParts = parseStableVersion(
                    installedLoaderVersion, "installed Fabric Loader version"
                );
                if (compareVersions(installedLoaderParts, minimumLoaderParts) < 0) {
                    System.out
                        .println(
                            "[ArenaSystem] Update skipped: requires Fabric Loader "
                                + minimumLoaderVersion
                                + " or newer; installed "
                                + installedLoaderVersion
                        );
                } else if (compareVersions(remoteParts, localParts) <= 0) {
                    System.out.println("[ArenaSystem Updater] Client is already up to date (" + localVersion + ").");
                } else {
                    System.out
                        .println(
                            "[ArenaSystem Updater] Update available: " + localVersion + " -> " + remoteVersion + "."
                        );
                    List<Path> installedJars = localMod.get()
                        .getOrigin()
                        .getPaths()
                        .stream()
                        .map(path -> path.toAbsolutePath().normalize())
                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .distinct()
                        .toList();
                    if (installedJars.size() != 1) {
                        throw new IOException(
                            "Expected exactly one installed JAR for mod ID arstm_client0qb201, found "
                                + installedJars.size()
                                + "."
                        );
                    }

                    Path target = installedJars.get(0);
                    System.out.println("[ArenaSystem Updater] Located installed client JAR: " + target.getFileName());
                    Path modsDirectory = target.getParent();
                    if (modsDirectory == null
                        || !"mods".equalsIgnoreCase(modsDirectory.getFileName().toString())
                        || Files.isSymbolicLink(modsDirectory)) {
                        throw new IOException("Mod must be installed directly inside a real mods directory.");
                    }

                    String targetFilename = target.getFileName().toString();
                    if (!targetFilename.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                        throw new IOException("Installed mod origin is not a JAR file: " + targetFilename);
                    }

                    Path destination = modsDirectory.resolve("ArenaSystem_Client-" + remoteVersion + ".jar")
                        .toAbsolutePath()
                        .normalize();
                    if (!destination.getParent().equals(modsDirectory)) {
                        throw new IOException("Generated update destination escaped the mods directory.");
                    }

                    if (!destination.equals(target) && Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException(
                            "Update destination already exists; refusing to overwrite: " + destination.getFileName()
                        );
                    }

                    if (!acquireUpdateLock(modsDirectory)) {
                        System.out
                            .println(
                                "[ArenaSystem] Another ArenaSystem Client update is already in progress; skipping this check."
                            );
                    } else {
                        boolean shutdownHookRegistered = false;
                        Path staged = destination.resolveSibling(destination.getFileName() + ".pending");
                        boolean modStaged = false;

                        try {
                            URI downloadUri = validateReleaseAssetUrl(
                                requiredString(manifest, "downloadUrl"),
                                releaseAssetName(remoteVersion),
                                "assassin-client-v" + remoteVersion
                            );
                            String expectedHash = requiredString(manifest, "sha256").toLowerCase(Locale.ROOT);
                            if (!expectedHash.matches("[a-f0-9]{64}")) {
                                throw new IOException("Manifest SHA-256 is malformed.");
                            }

                            downloadVerified(downloadUri, staged, expectedHash, 67108864L);
                            modStaged = true;
                            downloadHelper(loader.getGameDir(), remoteVersion, requiredString(manifest, "helperSha256"));
                            Runtime.getRuntime()
                                .addShutdownHook(
                                    new Thread(
                                        () -> launchHelper(target, staged, expectedHash, loader.getGameDir()),
                                        "ArenaSystem-Update-Installer"
                                    )
                                );
                            shutdownHookRegistered = true;
                            System.out
                                .println(
                                    "[ArenaSystem] Update "
                                        + remoteVersion
                                        + " downloaded and verified; installation is queued for game shutdown."
                                );
                        } finally {
                            if (!shutdownHookRegistered) {
                                if (modStaged) {
                                    try {
                                        Files.deleteIfExists(staged);
                                    } catch (IOException cleanupFailure) {
                                        System.err
                                            .println(
                                                "[ArenaSystem] Could not remove uncommitted staged JAR: "
                                                    + cleanupFailure.getMessage()
                                            );
                                    }
                                }

                                releaseUpdateLock();
                            }
                        }
                    }
                }
            }
        }
    }

    private static synchronized boolean acquireUpdateLock(Path modsDirectory) throws IOException {
        if (updateFileLock != null && updateFileLock.isValid()) {
            return false;
        }

        Path lockPath = modsDirectory.resolve(".arenasystem-assassin-dagger-update.lock");
        if (Files.isSymbolicLink(lockPath)) {
            throw new IOException("Updater lock path must not be a symbolic link.");
        }

        FileChannel channel = FileChannel.open(
            lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS
        );

        try {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException exception) {
                lock = null;
            }

            if (lock == null) {
                channel.close();
                return false;
            } else {
                updateLockChannel = channel;
                updateFileLock = lock;
                return true;
            }
        } catch (IOException | RuntimeException exception) {
            try {
                channel.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }

            throw exception;
        }
    }

    private static synchronized void releaseUpdateLock() {
        IOException failure = null;
        if (updateFileLock != null) {
            try {
                updateFileLock.release();
            } catch (IOException exception) {
                failure = exception;
            } finally {
                updateFileLock = null;
            }
        }

        if (updateLockChannel != null) {
            try {
                updateLockChannel.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            } finally {
                updateLockChannel = null;
            }
        }

        if (failure != null) {
            System.err.println("[ArenaSystem] Could not release updater lock: " + failure.getMessage());
        }
    }

    private static String releaseAssetName(String version) {
        return "ArenaSystem_Client-" + version + ".jar";
    }

    private static URI helperAssetUri(String version) {
        return URI.create(
            "https://github.com/au3891427-wq/proyek-arenasystem-plugin/releases/download/assassin-client-v"
                + version
                + "/ArenaSystem_Client_Updater-0.1.0.jar"
        );
    }

    private static JsonObject fetchManifest() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(MANIFEST_URI)
            .timeout(Duration.ofSeconds(20L))
            .header("Accept", "application/json")
            .header("User-Agent", "ArenaSystem-Client-Updater")
            .GET()
            .build();
        HttpResponse<InputStream> response = HTTP.send(request, BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Manifest request returned HTTP " + response.statusCode());
        }

        try (InputStream body = response.body()) {
            validateGitHubRedirect(response.uri());
            byte[] bytes = body.readNBytes(65537);
            if (bytes.length > 65536L) {
                throw new IOException("Manifest exceeds the 64 KiB size limit.");
            }

            try {
                JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) {
                    throw new IOException("Manifest must be a JSON object.");
                } else {
                    return parsed.getAsJsonObject();
                }
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
        } else if (!"arstm_client0qb201".equals(requiredString(manifest, "modId"))) {
            throw new IOException("Manifest modId mismatch.");
        } else if (!"stable".equals(requiredString(manifest, "channel"))) {
            throw new IOException("Refusing non-stable update channel.");
        } else if (!"fabric".equals(requiredString(manifest, "loader"))) {
            throw new IOException("Manifest loader is not Fabric.");
        } else {
            String version = requiredString(manifest, "version");
            parseStableVersion(version, "manifest version");
            parseStableVersion(requiredString(manifest, "loaderVersion"), "manifest loader version");
            validateReleaseAssetUrl(
                requiredString(manifest, "downloadUrl"), releaseAssetName(version), "assassin-client-v" + version
            );
            String modHash = requiredString(manifest, "sha256");
            String helperHash = requiredString(manifest, "helperSha256");
            if (!modHash.matches("(?i)[a-f0-9]{64}") || !helperHash.matches("(?i)[a-f0-9]{64}")) {
                throw new IOException("Manifest contains a malformed SHA-256 value.");
            }
        }
    }

    private static String requiredString(JsonObject object, String key) throws IOException {
        if (object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isString()) {
            String value = object.get(key).getAsString();
            if (value.isBlank()) {
                throw new IOException("Manifest field is blank: " + key);
            } else {
                return value;
            }
        } else {
            throw new IOException("Missing or invalid manifest field: " + key);
        }
    }

    private static URI validateReleaseAssetUrl(String value, String expectedAsset, String expectedTag) throws IOException {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Manifest download URL is malformed.", exception);
        }

        if ("https".equalsIgnoreCase(uri.getScheme())
            && "github.com".equalsIgnoreCase(uri.getHost())
            && uri.getRawUserInfo() == null
            && (uri.getPort() == -1 || uri.getPort() == 443)
            && uri.getRawQuery() == null
            && uri.getRawFragment() == null) {
            String path = uri.getPath();
            if (path != null
                && path.startsWith("/au3891427-wq/proyek-arenasystem-plugin/releases/download/")
                && path.endsWith("/" + expectedAsset)) {
                String tag = path.substring(
                    "/au3891427-wq/proyek-arenasystem-plugin/releases/download/".length(),
                    path.length() - expectedAsset.length() - 1
                );
                if (!tag.isBlank() && !tag.contains("/") && tag.equals(expectedTag)) {
                    return uri;
                } else {
                    throw new IOException("Download URL tag does not match the manifest version.");
                }
            } else {
                throw new IOException("Download URL does not point to the expected release asset.");
            }
        } else {
            throw new IOException("Download URL must be a plain HTTPS GitHub release URL.");
        }
    }

    private static void validateGitHubRedirect(URI uri) throws IOException {
        String host = uri.getHost();
        boolean allowedHost = host != null
            && (
                host.equalsIgnoreCase("github.com")
                    || host.equalsIgnoreCase("raw.githubusercontent.com")
                    || host.equalsIgnoreCase("release-assets.githubusercontent.com")
                    || host.equalsIgnoreCase("objects.githubusercontent.com")
            );
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowedHost) {
            throw new IOException("GitHub download redirected to an untrusted host.");
        }
    }

    private static void downloadHelper(Path gameDirectory, String version, String helperHash) throws IOException, InterruptedException {
        if (!helperHash.matches("(?i)[a-f0-9]{64}")) {
            throw new IOException("Helper SHA-256 is malformed.");
        }

        URI helperUri = helperAssetUri(version);
        validateReleaseAssetUrl(
            helperUri.toString(), "ArenaSystem_Client_Updater-0.1.0.jar", "assassin-client-v" + version
        );
        Path helper = gameDirectory.resolve(".arenasystem/updater").resolve("ArenaSystem_Client_Updater-0.1.0.jar");
        Path staged = helper.resolveSibling(helper.getFileName() + ".download");
        Files.createDirectories(helper.getParent());
        downloadVerified(helperUri, staged, helperHash, 8388608L);

        try {
            Files.move(staged, helper, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveFailure) {
            try {
                Files.deleteIfExists(staged);
            } catch (IOException cleanupFailure) {
                moveFailure.addSuppressed(cleanupFailure);
            }

            throw moveFailure;
        }
    }

    private static void downloadVerified(URI uri, Path destination, String expectedHash, long maxBytes) throws IOException, InterruptedException {
        validateGitHubRedirect(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(3L))
            .header("User-Agent", "ArenaSystem-Client-Updater")
            .GET()
            .build();
        MessageDigest digest = sha256Digest();
        HttpResponse<InputStream> response = HTTP.send(request, BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Download returned HTTP " + response.statusCode());
        }

        Path temporary = destination.resolveSibling(destination.getFileName() + ".part");
        long total = 0L;

        try (InputStream input = response.body()) {
            validateGitHubRedirect(response.uri());

            try (OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[8192];

                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > maxBytes) {
                        throw new IOException("Download exceeds size limit.");
                    }

                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }

            throw exception;
        }

        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expectedHash)) {
            Files.deleteIfExists(temporary);
            throw new IOException("SHA-256 verification failed.");
        }

        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveFailure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                moveFailure.addSuppressed(cleanupFailure);
            }

            throw moveFailure;
        }
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
                System.err
                    .println(
                        "[ArenaSystem] Automatic installation is currently Windows-only; verified update remains staged."
                    );
                return;
            }

            Path helper = gameDirectory.resolve(".arenasystem/updater").resolve("ArenaSystem_Client_Updater-0.1.0.jar");
            if (!Files.isRegularFile(helper) || !Files.isRegularFile(staged)) {
                throw new IOException("Helper or staged JAR is missing.");
            }

            Path javaBin = Path.of(System.getProperty("java.home"), "bin");
            Path javaLauncher = javaBin.resolve("javaw.exe");
            if (!Files.isRegularFile(javaLauncher)) {
                javaLauncher = javaBin.resolve("java.exe");
            }

            if (!Files.isRegularFile(javaLauncher)) {
                throw new IOException("Neither javaw.exe nor java.exe is available in java.home/bin.");
            }

            new ProcessBuilder(
                    javaLauncher.toString(),
                    "-jar",
                    helper.toString(),
                    Long.toString(ProcessHandle.current().pid()),
                    target.toString(),
                    staged.toString(),
                    hash
                )
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
            return new BigInteger[]{new BigInteger(parts[0]), new BigInteger(parts[1]), new BigInteger(parts[2])};
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid version number in " + field + ".", exception);
        }
    }

    private static int compareVersions(BigInteger[] left, BigInteger[] right) {
        for (int i = 0; i < 3; i++) {
            int result = left[i].compareTo(right[i]);
            if (result != 0) {
                return result;
            }
        }

        return 0;
    }
}
