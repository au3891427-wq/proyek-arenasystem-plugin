package id.hadziq.arenasystem;

/** Lightweight deterministic checks for the hard one-block terrain step limit. */
public final class TestTerrainConstraints {
    public static void main(String[] args) {
        testNormalizerRejectsRelaxedLimit();
        testDetectorAcceptsOneBlockStep();
        testDetectorRejectsTwoBlockStep();
        System.out.println("PASS: terrain constraints enforce a maximum one-block adjacent step.");
    }

    private static void testNormalizerRejectsRelaxedLimit() {
        int[][] heights = flatMap(64);
        TerrainProfile profile = new TerrainProfile(1, heights, 64, 64);
        try {
            TerrainNormalizer.normalize(profile, 2, 1);
            throw new AssertionError("Normalizer accepted a relaxed maxAdjacentStep=2.");
        } catch (IllegalArgumentException expected) {
            // Required: callers cannot raise the combat-terrain slope limit.
        }
    }

    private static void testDetectorAcceptsOneBlockStep() {
        int[][] heights = flatMap(64);
        heights[0][0] = 65;
        TerrainDetector.Report report = TerrainDetector.analyze(
                new TerrainProfile(1, heights, 64, 65), 1, 1);
        if (!report.accepted() || report.maxAdjacentStep() != 1) {
            throw new AssertionError("Detector rejected a valid one-block adjacent step: "
                    + report.rejectionReason());
        }
    }

    private static void testDetectorRejectsTwoBlockStep() {
        int[][] heights = flatMap(64);
        heights[0][0] = 66;
        TerrainDetector.Report report = TerrainDetector.analyze(
                new TerrainProfile(1, heights, 64, 66), 1, 1);
        if (report.accepted() || report.maxAdjacentStep() != 2
                || !report.rejectionReason().contains("batas wajib 1")) {
            throw new AssertionError("Detector failed to reject a two-block adjacent step.");
        }
    }

    private static int[][] flatMap(int height) {
        int[][] heights = new int[3][3];
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) heights[x][z] = height;
        }
        return heights;
    }
}
