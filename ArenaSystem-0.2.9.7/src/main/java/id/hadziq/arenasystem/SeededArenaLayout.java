package id.hadziq.arenasystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// $VF: Compiled from SeededArenaLayout.java
final class SeededArenaLayout {
    private static final long TDM_COVER_SALT = 6072063149091472722L;
    private static final long RELIC_COVER_SALT = 5928228362047934294L;
    private static final long TDM_PATH_SALT = 6072063204691036243L;
    private static final long RELIC_OUTPOST_SALT = 5928228362048722260L;

    private SeededArenaLayout() {
    }

    static Random tdmPathRandom(long seed) {
        return new Random(mixSeed(seed, 6072063204691036243L));
    }

    static Random relicOutpostRandom(long seed) {
        return new Random(mixSeed(seed, 5928228362048722260L));
    }

    static List<SeededArenaLayout.CoverSite> teamDeathmatch(long seed) {
        Random random = new Random(mixSeed(seed, 6072063149091472722L));
        List<int[]> candidates = new ArrayList<>();

        for (int x = 8; x <= 24; x++) {
            for (int absZ = 7; absZ <= 23; absZ++) {
                if (Math.abs(x - 15) > 4 || Math.abs(absZ - 15) > 4) {
                    candidates.add(new int[]{x, absZ});
                }
            }
        }

        Collections.shuffle(candidates, random);
        List<SeededArenaLayout.CoverSite> selected = new ArrayList<>(6);

        for (int[] candidate : candidates) {
            boolean clustered = false;

            for (SeededArenaLayout.CoverSite existing : selected) {
                if (Math.abs(existing.x() - candidate[0]) < 4 && Math.abs(existing.z() - candidate[1]) < 4) {
                    clustered = true;
                    break;
                }
            }

            if (!clustered) {
                selected.add(
                    new SeededArenaLayout.CoverSite(candidate[0], candidate[1], random.nextBoolean(), random.nextInt(4))
                );
                if (selected.size() == 6) {
                    break;
                }
            }
        }

        if (selected.size() != 6) {
            throw new IllegalStateException("Tidak cukup slot aman untuk seeded TDM cover layout.");
        } else {
            return List.copyOf(selected);
        }
    }

    static List<SeededArenaLayout.CoverSite> relicRush(long seed) {
        Random random = new Random(mixSeed(seed, 5928228362047934294L));
        List<int[]> candidates = new ArrayList<>(
            List.of(new int[]{12, 14}, new int[]{16, 14}, new int[]{12, 19}, new int[]{16, 19})
        );
        Collections.shuffle(candidates, random);
        List<SeededArenaLayout.CoverSite> selected = new ArrayList<>(2);

        for (int i = 0; i < 2; i++) {
            int[] candidate = candidates.get(i);
            selected.add(
                new SeededArenaLayout.CoverSite(candidate[0], candidate[1], random.nextBoolean(), random.nextInt(4))
            );
        }

        return List.copyOf(selected);
    }

    private static long mixSeed(long seed, long salt) {
        long value = seed ^ salt;
        value = (value ^ value >>> 30) * -4658895280553007687L;
        value = (value ^ value >>> 27) * -7723592293110705685L;
        return value ^ value >>> 31;
    }

    // $VF: Compiled from SeededArenaLayout.java
    record CoverSite(int x, int z, boolean alongX, int paletteIndex) {
    }
}
