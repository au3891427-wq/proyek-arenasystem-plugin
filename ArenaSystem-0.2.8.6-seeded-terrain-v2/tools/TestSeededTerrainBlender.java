package id.hadziq.arenasystem;

import java.util.List;

/** Standalone checks for SeededTerrainBlender + anchor-aware TerrainNormalizer/TerrainDetector (no server needed). */
public final class TestSeededTerrainBlender {
    static final int MAX_DELTA = SeededTerrainBlender.DEFAULT_MAX_SEED_DELTA;
    static final int MIN_Y = -64, MAX_Y = 320;

    public static void main(String[] args) {
        for (int[] cfg : new int[][]{{40, 27, 2}, {40, 27, 3}, {40, 27, 4}, {56, 48, 2}}) { // {radius, spawnOffset, teams}
            int four = cfg[2];
            testDeterminism(cfg[0], cfg[1], four);
            testDeltaBound(cfg[0], cfg[1], four);
            testFlatWorldVariation(cfg[0], cfg[1], four);
            testNonFlatKeepsCharacter(cfg[0], cfg[1], four);
            testFairnessAndPlayability(cfg[0], cfg[1], four);
        }
        testInactiveTeamsAreNotConstrained();
        testWorldHeightClamp();
        testLegacyNormalizerOverloadUnchanged();
        System.out.println("PASS: seeded blender determinism, bounded delta, flat/non-flat variation, 4-team fairness, lane/objective, post-normalization relief.");
    }

    static TerrainProfile flat(int radius, int y) {
        int size = radius * 2 + 1;
        int[][] h = new int[size][size];
        for (int[] row : h) java.util.Arrays.fill(row, y);
        return new TerrainProfile(radius, h, y, y);
    }

    /** Rolling hills with real character (amplitude 8, wavelength ~30) plus a ridge. */
    static TerrainProfile rolling(int radius) {
        int size = radius * 2 + 1;
        int[][] h = new int[size][size];
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            int y = (int) Math.round(70 + 8 * Math.sin(x / 9.0) * Math.cos(z / 11.0) + 0.1 * (x - radius));
            h[x][z] = y; min = Math.min(min, y); max = Math.max(max, y);
        }
        return new TerrainProfile(radius, h, min, max);
    }

    static TerrainProfile blend(TerrainProfile p, long seed, int so, int four) {
        return SeededTerrainBlender.blend(p, seed, so, four, MAX_DELTA, MIN_Y, MAX_Y);
    }

    static TerrainNormalizer.Result normalize(TerrainProfile blended, int so, int four) {
        return TerrainNormalizer.normalize(blended, 1, SeededTerrainBlender.anchors(so, four));
    }

    static void testDeterminism(int r, int so, int four) {
        TerrainProfile a = blend(rolling(r), 12345L, so, four);
        TerrainProfile b = blend(rolling(r), 12345L, so, four);
        check(java.util.Arrays.deepEquals(a.copyHeights(), b.copyHeights()), "same seed must give identical contours");
        check(java.util.Arrays.deepEquals(normalize(a, so, four).profile().copyHeights(),
                normalize(b, so, four).profile().copyHeights()), "same seed must give identical normalized contours");
    }

    static void testDeltaBound(int r, int so, int four) {
        TerrainProfile base = rolling(r);
        int worst = 0;
        for (long seed = 0; seed < 150; seed++) {
            TerrainProfile p = blend(base, seed * 7919 + 1, so, four);
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++)
                worst = Math.max(worst, Math.abs(p.heightAt(x, z) - base.heightAt(x, z)));
        }
        check(worst <= MAX_DELTA, "delta must stay within +-" + MAX_DELTA + " (worst " + worst + ")");
        check(worst >= MAX_DELTA - 1, "bound should actually be used (worst " + worst + ")");
        System.out.println("r=" + r + " delta worst=" + worst + " (limit " + MAX_DELTA + ")");
    }

    static void testFlatWorldVariation(int r, int so, int four) {
        TerrainProfile flat = flat(r, 64);
        int minRange = Integer.MAX_VALUE, minChanged = 100;
        TerrainProfile first = null;
        int differentSeeds = 0;
        for (long seed = 1; seed <= 40; seed++) {
            TerrainProfile n = normalize(blend(flat, seed, so, four), so, four).profile();
            minRange = Math.min(minRange, n.maxY - n.minY);
            int size = r * 2 + 1, bumpy = 0;
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) if (Math.abs(n.heightAt(x, z) - 64) >= 2) bumpy++;
            minChanged = Math.min(minChanged, bumpy * 100 / (size * size));
            if (first == null) first = n;
            else if (!java.util.Arrays.deepEquals(first.copyHeights(), n.copyHeights())) differentSeeds++;
        }
        System.out.println("r=" + r + " flat world after normalization: min relief=" + minRange + " blocks, min bumpy columns(|dy|>=2)=" + minChanged + "%");
        check(differentSeeds >= 38, "different seeds must give different flat-world contours");
        check(minRange >= 6, "flat world relief must be visible after maxAdjacentStep==1 (min " + minRange + ")");
        check(minChanged >= 15, "a meaningful share of columns must differ from flat (min " + minChanged + "%)");
    }

    static void testNonFlatKeepsCharacter(int r, int so, int four) {
        TerrainProfile base = rolling(r);
        TerrainProfile a = normalize(blend(base, 1L, so, four), so, four).profile();
        TerrainProfile b = normalize(blend(base, 2L, so, four), so, four).profile();
        check(!java.util.Arrays.deepEquals(a.copyHeights(), b.copyHeights()), "seeds differ on non-flat world");
        for (TerrainProfile p : List.of(a, b)) {
            double corr = correlation(base, p, r);
            long within = 0, total = 0;
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) { total++; if (Math.abs(p.heightAt(x, z) - base.heightAt(x, z)) <= MAX_DELTA + 3) within++; }
            System.out.println("r=" + r + " non-flat correlation with original=" + String.format("%.3f", corr) + " within(delta+3)=" + (within * 100 / total) + "%");
            check(corr > 0.8, "original character must be preserved (corr " + corr + ")");
            check(within * 100 / total >= 90, "most columns stay near the scanned surface");
        }
    }

    static void testFairnessAndPlayability(int r, int so, int four) {
        int laneCap = (int) Math.ceil(0.35 * MAX_DELTA) + 1;
        for (TerrainProfile base : List.of(flat(r, 64), rolling(r))) {
            for (long seed = 1; seed <= 60; seed++) {
                TerrainProfile blended = blend(base, seed, so, four);
                // pads and objective core are untouched by seed noise
                for (int[] a : SeededTerrainBlender.anchors(so, four))
                    for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++)
                        check(blended.heightAt(a[0] + dx, a[1] + dz) == base.heightAt(a[0] + dx, a[1] + dz), "spawn pad column must have zero seed delta");
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
                    check(blended.heightAt(x, z) == base.heightAt(x, z), "objective core must have zero seed delta");
                // lanes damped
                for (int x = -so; x <= so; x++)
                    check(Math.abs(blended.heightAt(x, 0) - base.heightAt(x, 0)) <= laneCap, "main lane delta damped");
                for (int z = (four >= 3 ? -so : 0); z <= (four >= 4 ? so : 0); z++)
                    check(Math.abs(blended.heightAt(0, z) - base.heightAt(0, z)) <= laneCap, "active Z lane delta damped");

                TerrainNormalizer.Result n = normalize(blended, so, four);
                TerrainProfile p = n.profile();
                // every active anchor pad is level at one shared elevation
                int shared = p.heightAt(-so, 0);
                for (int[] a : SeededTerrainBlender.anchors(so, four))
                    for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++)
                        check(p.heightAt(a[0] + dx, a[1] + dz) == shared, "all active spawn pads must be level and equal (seed " + seed + ")");
                TerrainDetector.Report rep = TerrainDetector.analyze(p, r, so, four);
                check(rep.accepted(), "detector must accept: " + rep.summary() + " " + rep.rejectionReason());
                check(n.maxAdjacentStep() == 1, "slope limit exactly 1");
            }
        }
    }

    /** 2 teams: GREEN/YELLOW spots keep seed variation and are not leveled; 3 teams: only YELLOW is free. */
    static void testInactiveTeamsAreNotConstrained() {
        TerrainProfile base = flat(40, 64);
        for (int teams = 2; teams <= 3; teams++) {
            int freeZ = teams == 2 ? -27 : 27; // first inactive anchor on the Z axis
            boolean maskedFree = false, leveledFree = true;
            int sharedY = -1;
            for (long seed = 1; seed <= 40; seed++) {
                TerrainProfile blended = blend(base, seed, 27, teams);
                for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++)
                    if (blended.heightAt(dx, freeZ + dz) != 64) maskedFree = true;
                TerrainProfile n = normalize(blended, 27, teams).profile();
                sharedY = n.heightAt(-27, 0);
                for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++)
                    if (n.heightAt(dx, freeZ + dz) != sharedY) leveledFree = false;
            }
            check(maskedFree, teams + " teams: inactive spawn area must keep seed variation (no fairness mask)");
            check(!leveledFree, teams + " teams: inactive spawn area must not be forced level");
            // Active anchors still verified by testFairnessAndPlayability for the same teams count.
        }
        check(SeededTerrainBlender.anchors(27, 2).size() == 2 && SeededTerrainBlender.anchors(27, 3).size() == 3
                && SeededTerrainBlender.anchors(27, 4).size() == 4, "anchor count follows team count");
        int[] green = SeededTerrainBlender.anchors(27, 3).get(2);
        check(green[0] == 0 && green[1] == -27, "GREEN anchor (0,-offset)");
        int[] yellow = SeededTerrainBlender.anchors(27, 4).get(3);
        check(yellow[0] == 0 && yellow[1] == 27, "YELLOW anchor (0,+offset)");
    }

    static void testWorldHeightClamp() {
        TerrainProfile low = flat(40, MIN_Y + 5), high = flat(40, MAX_Y - 30);
        for (long seed = 1; seed <= 30; seed++) {
            TerrainProfile l = SeededTerrainBlender.blend(low, seed, 27, 4, 8, MIN_Y, MAX_Y);
            TerrainProfile h = SeededTerrainBlender.blend(high, seed, 27, 4, 8, MIN_Y, MAX_Y);
            check(l.minY >= MIN_Y + 4, "never below world floor margin: " + l.minY);
            check(h.maxY <= MAX_Y - 40 || h.maxY <= MAX_Y - 30, "never pushed above ceiling margin: " + h.maxY);
        }
        TerrainProfile zero = SeededTerrainBlender.blend(flat(40, 64), 9L, 27, 4, 0, MIN_Y, MAX_Y);
        check(zero.maxY == 64 && zero.minY == 64, "maxSeedDelta 0 leaves the scan unchanged");
    }

    static void testLegacyNormalizerOverloadUnchanged() {
        TerrainProfile base = rolling(40);
        TerrainNormalizer.Result legacy = TerrainNormalizer.normalize(base, 1, 27);
        TerrainNormalizer.Result viaAnchors = TerrainNormalizer.normalize(base, 1, SeededTerrainBlender.anchors(27, 2));
        check(java.util.Arrays.deepEquals(legacy.profile().copyHeights(), viaAnchors.profile().copyHeights()), "2-anchor path == legacy 3-arg path");
    }

    static double correlation(TerrainProfile a, TerrainProfile b, int r) {
        double ma = 0, mb = 0; int n = 0;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) { ma += a.heightAt(x, z); mb += b.heightAt(x, z); n++; }
        ma /= n; mb /= n;
        double sab = 0, saa = 0, sbb = 0;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            double da = a.heightAt(x, z) - ma, db = b.heightAt(x, z) - mb;
            sab += da * db; saa += da * da; sbb += db * db;
        }
        return sab / Math.sqrt(saa * sbb);
    }

    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }
}
