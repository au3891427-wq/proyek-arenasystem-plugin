package id.hadziq.arenasystem.client;

import id.hadziq.arenasystem.client.mixin.KeyMappingAccessor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

// $VF: Compiled from ArenaSystemClient.java
public final class ArenaSystemClient implements ClientModInitializer {
    private static final ExecutorService UPDATE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ArenaSystem-Updater");
        thread.setDaemon(true);
        return thread;
    });

    public void onInitializeClient() {
        KeybindChordConfig.load();
        CompanionHandshakeClient.initialize();
        SkillInputClient.initialize();
        AssassinOffhandCooldownClient.initialize();
        AssassinOffhandInputClient.initialize();
        ArenaSystemKeybinds.initialize();
        CompletableFuture.runAsync(() -> {
            try {
                UpdateService.checkAndStage(FabricLoader.getInstance());
            } catch (Exception exception) {
                System.err.println("[ArenaSystem] Update check failed: " + exception.getMessage());
            }
        }, UPDATE_EXECUTOR);
    }
}
