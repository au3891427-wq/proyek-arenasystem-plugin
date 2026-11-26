package id.hadziq.arenasystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Deterministic, mode-specific tactical layout choices derived from an arena seed.
 * Coordinates are local to the arena centre. The generator mirrors placements
 * around both axes in both modes to preserve fairness for multi-team setups.
 */
final class SeededArenaLayout {
    private static final long TDM_COVER_SALT = 0x54444D434F564552L;
    private static final long RELIC_COVER_SALT = 0x52454C4943434F56L;
    private static final long TDM_PATH_SALT = 0x54444D5041544853L;
    private static final long RELIC_OUTPOST_SALT = 0x52454C49434F5554L;

    private SeededArenaLayout() {
    }

    record CoverSite(int x, int z, boolean alongX, int paletteIndex) {
    }

    static Random tdmPathRandom(long seed) {
        return new Random(mixSeed(seed, TDM_PATH_SALT));
    }

    static Random relicOutpostRandom(long seed) {
        return new Random(mixSeed(seed, RELIC_OUTPOST_SALT));
    }

    /**
     * Returns six unique cover anchors in the positive-X/positive-Z quadrant.
     * ArenaGenerator mirrors each anchor across both axes, preserving the
     * corresponding cover layout for all two-, three-, and four-team setups.
     */
    static List<CoverSite> teamDeathmatch(long seed) {
        Random random = new Random(mixSeed(seed, TDM_COVER_SALT));
        List<int[]> candidates = new ArrayList<>();

        for (int x = 8; x <= 24; x++) {
            for (int absZ = 7; absZ <= 23; absZ++) {
                // Keep the seeded additions clear of the four fixed ruined landmarks.
                if (Math.abs(x - 15) <= 4 && Math.abs(absZ - 15) <= 4) continue;
                candidates.add(new int[]{x, absZ});
            }
        }
        Collections.shuffle(candidates, random);

        List<CoverSite> selected = new ArrayList<>(6);
        for (int[] candidate : candidates) {
            boolean clustered = false;
            for (CoverSite existing : selected) {
                if (Math.abs(existing.x() - candidate[0]) < 4
                        && Math.abs(existing.z() - candidate[1]) < 4) {
                    clustered = true;
                    break;
                }
            }
            if (clustered) continue;

            selected.add(new CoverSite(candidate[0], candidate[1],
                    random.nextBoolean(), random.nextInt(4)));
            if (selected.size() == 6) break;
        }

        if (selected.size() != 6) {
            throw new IllegalStateException("Tidak cukup slot aman untuk seeded TDM cover layout.");
        }
        return List.copyOf(selected);
    }

    /**
     * Chooses two of four safe diagonal midfield slots. The generator mirrors
     * every selected slot across X and Z, producing four-way balanced cover.
     */
    static List<CoverSite> relicRush(long seed) {
        Random random = new Random(mixSeed(seed, RELIC_COVER_SALT));
        List<int[]> candidates = new ArrayList<>(List.of(
                new int[]{12, 14},
                new int[]{16, 14},
                new int[]{12, 19},
                new int[]{16, 19}
        ));
        Collections.shuffle(candidates, random);

        List<CoverSite> selected = new ArrayList<>(2);
        for (int i = 0; i < 2; i++) {
            int[] candidate = candidates.get(i);
            selected.add(new CoverSite(candidate[0], candidate[1],
                    random.nextBoolean(), random.nextInt(4)));
        }
        return List.copyOf(selected);
    }

    private static long mixSeed(long seed, long salt) {
        long value = seed ^ salt;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
