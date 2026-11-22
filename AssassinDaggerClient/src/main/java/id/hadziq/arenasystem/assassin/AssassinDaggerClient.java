package id.hadziq.arenasystem.assassin;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AssassinDaggerClient implements ClientModInitializer {
    private static final ExecutorService UPDATE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ArenaSystem-Updater");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onInitializeClient() {
        ArenaSystemResourcePack.register();
        ArenaSystemKeyMappings.initialize();
        ArenaSystemHandshake.initialize();

        CompletableFuture.runAsync(() -> {
            try {
                UpdateService.checkAndStage(FabricLoader.getInstance());
            } catch (Exception exception) {
                System.err.println("[ArenaSystem] Update check failed: " + exception.getMessage());
            }
        }, UPDATE_EXECUTOR);
    }
}
