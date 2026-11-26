package id.hadziq.arenasystem;

import java.util.Collection;

/**
 * Pure geometry used by the incomplete-arena protection listeners. It only answers
 * whether a block coordinate lies inside the protected volume of an incomplete arena;
 * arenas that are complete are never passed in, so their behaviour is unaffected.
 */
final class IncompleteArenaGuard {
    private IncompleteArenaGuard() { }

    static boolean contains(ArenaData arena, String worldName, int x, int y, int z) {
        if (!arena.worldName.equals(worldName)) return false;
        if (Math.abs((long) x - arena.centerX) > arena.radius) return false;
        if (Math.abs((long) z - arena.centerZ) > arena.radius) return false;
        return y >= arena.minSurfaceY - 20 && y <= arena.maxSurfaceY + 64;
    }

    static boolean touches(Collection<ArenaData> incomplete, String worldName, int x, int y, int z) {
        if (incomplete.isEmpty()) return false;
        for (ArenaData arena : incomplete) {
            if (contains(arena, worldName, x, y, z)) return true;
        }
        return false;
    }

    /**
     * A block that moves along (dx,dy,dz) touches an incomplete arena at its origin or
     * at either neighbour on that axis. Checking both neighbours keeps this correct for
     * extend and retract regardless of which way the API reports the direction.
     */
    static boolean touchesMovement(Collection<ArenaData> incomplete, String worldName,
                                   int x, int y, int z, int dx, int dy, int dz) {
        return touches(incomplete, worldName, x, y, z)
                || touches(incomplete, worldName, x + dx, y + dy, z + dz)
                || touches(incomplete, worldName, x - dx, y - dy, z - dz);
    }
}
