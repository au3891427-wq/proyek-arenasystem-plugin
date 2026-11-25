package id.hadziq.arenasystem;

import java.util.Collection;

// $VF: Compiled from IncompleteArenaGuard.java
final class IncompleteArenaGuard {
    private IncompleteArenaGuard() {
    }

    static boolean contains(ArenaData arena, String worldName, int x, int y, int z) {
        return arena.worldName.equals(worldName)
            && Math.abs((long)x - arena.centerX) <= arena.radius
            && Math.abs((long)z - arena.centerZ) <= arena.radius
            && y >= arena.minSurfaceY - 20
            && y <= arena.maxSurfaceY + 64;
    }

    static boolean touches(Collection<ArenaData> incomplete, String worldName, int x, int y, int z) {
        if (incomplete.isEmpty()) {
            return false;
        }

        for (ArenaData arena : incomplete) {
            if (contains(arena, worldName, x, y, z)) {
                return true;
            }
        }

        return false;
    }

    static boolean touchesMovement(
        Collection<ArenaData> incomplete, String worldName, int x, int y, int z, int dx, int dy, int dz
    ) {
        return touches(incomplete, worldName, x, y, z)
            || touches(incomplete, worldName, x + dx, y + dy, z + dz)
            || touches(incomplete, worldName, x - dx, y - dy, z - dz);
    }
}
