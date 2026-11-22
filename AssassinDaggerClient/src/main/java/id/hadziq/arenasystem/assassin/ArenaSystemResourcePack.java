package id.hadziq.arenasystem.assassin;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ArenaSystemResourcePack {
    private static final String MOD_ID = "arenasystem_assassin_dagger";
    private static final Identifier PACK_ID =
            Identifier.fromNamespaceAndPath(MOD_ID, "assassin_dagger");

    private ArenaSystemResourcePack() {
    }

    static void register() {
        ModContainer container = FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "ArenaSystem client mod container is not available."));

        boolean registered = ResourceLoader.registerBuiltinPack(
                PACK_ID,
                container,
                Component.literal("ArenaSystem — Assassin Dagger"),
                PackActivationType.ALWAYS_ENABLED
        );

        if (!registered) {
            System.err.println("[ArenaSystem] Built-in Assassin Dagger resource pack registration failed.");
        }
    }
}
