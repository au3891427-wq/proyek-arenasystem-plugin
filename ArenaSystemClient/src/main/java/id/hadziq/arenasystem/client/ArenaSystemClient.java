package id.hadziq.arenasystem.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ArenaSystemClient implements ClientModInitializer {
    private static final ExecutorService UPDATE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ArenaSystem-Updater");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onInitializeClient() {
        CompletableFuture.runAsync(() -> {
            try {
                UpdateService.checkAndStage(FabricLoader.getInstance());
            } catch (Exception exception) {
                System.err.println("[ArenaSystem] Update check failed: " + exception.getMessage());
            }
        }, UPDATE_EXECUTOR);
    }
}
