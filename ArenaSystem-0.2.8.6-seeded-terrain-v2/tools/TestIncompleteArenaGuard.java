package id.hadziq.arenasystem;

import java.util.List;

/**
 * Standalone checks for the geometry behind the incomplete-arena listeners (bucket, fluid flow,
 * piston, EntityChangeBlock). The Bukkit event glue in BlockProtectionListener needs the Paper
 * API and is NOT exercised here.
 */
public final class TestIncompleteArenaGuard {
    public static void main(String[] args) {
        ArenaData incomplete = new ArenaData("building", "world", 1L, 1000, 1000, 64, 64, 64, 68, 60, 70, "tdm", 27, 40);
        List<ArenaData> list = List.of(incomplete);

        // Inside the footprint, any of the six event kinds resolves through the same predicate.
        check(IncompleteArenaGuard.touches(list, "world", 1000, 64, 1000), "center blocked");
        check(IncompleteArenaGuard.touches(list, "world", 1040, 64, 960), "edge blocked");
        check(IncompleteArenaGuard.touches(list, "world", 1000, 40, 1000), "lower vertical band blocked (min-20)");
        check(IncompleteArenaGuard.touches(list, "world", 1000, 134, 1000), "upper vertical band blocked (max+64)");

        // Outside: normal behaviour must be preserved.
        check(!IncompleteArenaGuard.touches(list, "world", 1041, 64, 1000), "just outside X not blocked");
        check(!IncompleteArenaGuard.touches(list, "world", 1000, 64, 959), "just outside Z not blocked");
        check(!IncompleteArenaGuard.touches(list, "world", 1000, 39, 1000), "below band not blocked");
        check(!IncompleteArenaGuard.touches(list, "world", 1000, 135, 1000), "above band not blocked");
        check(!IncompleteArenaGuard.touches(list, "nether", 1000, 64, 1000), "other world not blocked");
        check(!IncompleteArenaGuard.touches(List.of(), "world", 1000, 64, 1000), "no incomplete arena -> nothing blocked");

        // Fluid flow / dragon egg: source outside flowing in, and source inside flowing out, both touch.
        boolean outsideToInside = IncompleteArenaGuard.touches(list, "world", 1041, 64, 1000)
                || IncompleteArenaGuard.touches(list, "world", 1040, 64, 1000);
        boolean insideToOutside = IncompleteArenaGuard.touches(list, "world", 1040, 64, 1000)
                || IncompleteArenaGuard.touches(list, "world", 1041, 64, 1000);
        boolean outsideToOutside = IncompleteArenaGuard.touches(list, "world", 1041, 64, 1000)
                || IncompleteArenaGuard.touches(list, "world", 1042, 64, 1000);
        check(outsideToInside, "fluid flowing in blocked");
        check(insideToOutside, "fluid flowing out blocked");
        check(!outsideToOutside, "fluid flow entirely outside unaffected");

        // Piston: a piston just outside pushing a block into the footprint, and one just inside pushing out.
        check(IncompleteArenaGuard.touchesMovement(list, "world", 1041, 64, 1000, -1, 0, 0), "outside piston pushing in");
        check(IncompleteArenaGuard.touchesMovement(list, "world", 1040, 64, 1000, 1, 0, 0), "inside block pushed out");
        check(IncompleteArenaGuard.touchesMovement(list, "world", 1041, 64, 1000, 1, 0, 0), "retract direction reported inverted");
        check(!IncompleteArenaGuard.touchesMovement(list, "world", 1050, 64, 1000, 1, 0, 0), "far piston unaffected");
        check(!IncompleteArenaGuard.touchesMovement(list, "world", 1042, 64, 1000, 1, 0, 0), "two blocks away unaffected");
        check(IncompleteArenaGuard.touchesMovement(list, "world", 1000, 63, 1000, 0, 1, 0), "vertical piston inside");

        // Two incomplete arenas: only the one containing the block matters.
        ArenaData other = new ArenaData("other", "world", 1L, 2000, 2000, 64, 64, 64, 68, 60, 70, "tdm", 27, 40);
        List<ArenaData> two = List.of(incomplete, other);
        check(IncompleteArenaGuard.touches(two, "world", 2000, 64, 2000), "second arena blocked");
        check(!IncompleteArenaGuard.touches(two, "world", 1500, 64, 1500), "between arenas not blocked");
        System.out.println("PASS: incomplete-arena guard blocks inside the footprint and leaves everything else alone.");
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
