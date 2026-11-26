package id.hadziq.arenasystem;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Lightweight deterministic-layout regression test; run with javac/java. */
public final class TestSeededArenaLayout {
    public static void main(String[] args) {
        assertDeterministic("TDM seed 123456", ArenaLayoutMode.TDM, 123456L);
        assertDeterministic("Relic seed 123456", ArenaLayoutMode.RELIC, 123456L);
        assertDifferentSeedsProduceVariants(ArenaLayoutMode.TDM, 123456L, 654321L);
        assertDifferentSeedsProduceVariants(ArenaLayoutMode.RELIC, 123456L, 654321L);
        assertRandomStreamIsSeeded(SeededArenaLayout.tdmPathRandom(123456L),
                SeededArenaLayout.tdmPathRandom(123456L), "TDM path stream same seed");
        assertRandomStreamDiffers(SeededArenaLayout.tdmPathRandom(123456L),
                SeededArenaLayout.tdmPathRandom(654321L), "TDM path stream different seeds");
        assertRandomStreamDiffers(SeededArenaLayout.relicOutpostRandom(123456L),
                SeededArenaLayout.relicOutpostRandom(654321L), "Relic outpost stream different seeds");
        assertValidTdmSites(SeededArenaLayout.teamDeathmatch(123456L));
        assertValidRelicSites(SeededArenaLayout.relicRush(123456L));
        for (long seed : new long[]{0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 987654321L}) {
            assertDeterministic("TDM edge seed " + seed, ArenaLayoutMode.TDM, seed);
            assertDeterministic("Relic edge seed " + seed, ArenaLayoutMode.RELIC, seed);
            assertValidTdmSites(SeededArenaLayout.teamDeathmatch(seed));
            assertValidRelicSites(SeededArenaLayout.relicRush(seed));
        }
        System.out.println("SeededArenaLayout tests passed.");
    }

    private static void assertDeterministic(String label, ArenaLayoutMode mode, long seed) {
        List<SeededArenaLayout.CoverSite> first = layout(mode, seed);
        List<SeededArenaLayout.CoverSite> second = layout(mode, seed);
        if (!first.equals(second)) throw new AssertionError(label + " is not deterministic");
    }

    private static void assertDifferentSeedsProduceVariants(ArenaLayoutMode mode, long firstSeed, long secondSeed) {
        if (layout(mode, firstSeed).equals(layout(mode, secondSeed))) {
            throw new AssertionError(mode + " layouts did not change for the tested seeds");
        }
    }

    private static List<SeededArenaLayout.CoverSite> layout(ArenaLayoutMode mode, long seed) {
        return mode == ArenaLayoutMode.TDM
                ? SeededArenaLayout.teamDeathmatch(seed)
                : SeededArenaLayout.relicRush(seed);
    }

    private static void assertRandomStreamIsSeeded(Random first, Random second, String label) {
        for (int i = 0; i < 128; i++) {
            if (first.nextInt() != second.nextInt()) throw new AssertionError(label + " is not deterministic");
        }
    }

    private static void assertRandomStreamDiffers(Random first, Random second, String label) {
        boolean differs = false;
        for (int i = 0; i < 128; i++) {
            if (first.nextInt() != second.nextInt()) {
                differs = true;
                break;
            }
        }
        if (!differs) throw new AssertionError(label + " did not vary");
    }

    private static void assertValidTdmSites(List<SeededArenaLayout.CoverSite> sites) {
        if (sites.size() != 6) throw new AssertionError("TDM must have exactly six positive-quadrant seed anchors");
        Set<String> coordinates = new HashSet<>();
        for (SeededArenaLayout.CoverSite site : sites) {
            if (site.x() < 8 || site.x() > 24 || site.z() < 7 || site.z() > 23) {
                throw new AssertionError("TDM cover outside its safe candidate region: " + site);
            }
            if (site.paletteIndex() < 0 || site.paletteIndex() > 3) {
                throw new AssertionError("Invalid TDM material palette index");
            }
            if (!coordinates.add(site.x() + ":" + site.z())) {
                throw new AssertionError("Duplicate TDM cover site: " + site);
            }
        }
    }

    private static void assertValidRelicSites(List<SeededArenaLayout.CoverSite> sites) {
        if (sites.size() != 2) throw new AssertionError("Relic Rush must have exactly two seeded site groups");
        Set<String> coordinates = new HashSet<>();
        for (SeededArenaLayout.CoverSite site : sites) {
            if ((site.x() != 12 && site.x() != 16) || (site.z() != 14 && site.z() != 19)) {
                throw new AssertionError("Relic cover outside its safe candidate region: " + site);
            }
            if (site.paletteIndex() < 0 || site.paletteIndex() > 3) {
                throw new AssertionError("Invalid Relic Rush material palette index");
            }
            if (!coordinates.add(site.x() + ":" + site.z())) {
                throw new AssertionError("Duplicate Relic Rush cover site: " + site);
            }
        }
    }

    private enum ArenaLayoutMode { TDM, RELIC }
}
