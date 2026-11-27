package id.hadziq.arenasystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// $VF: Compiled from TerrainDetector.java
final class TerrainDetector {
    private TerrainDetector() {
    }

    static TerrainDetector.Report analyze(TerrainProfile terrain, int radius, int spawnOffset) {
        return analyze(terrain, radius, spawnOffset, 2);
    }

    static TerrainDetector.Report analyze(TerrainProfile terrain, int radius, int spawnOffset, int teamCount) {
        int range = terrain.maxY - terrain.minY;
        long edgeCount = 0L;
        long totalStep = 0L;
        int maxStep = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int here = terrain.heightAt(x, z);
                if (x < radius) {
                    int step = Math.abs(here - terrain.heightAt(x + 1, z));
                    edgeCount++;
                    totalStep += step;
                    maxStep = Math.max(maxStep, step);
                }

                if (z < radius) {
                    int step = Math.abs(here - terrain.heightAt(x, z + 1));
                    edgeCount++;
                    totalStep += step;
                    maxStep = Math.max(maxStep, step);
                }
            }
        }

        int routeMaxStep = 0;
        long routeTotalStep = 0L;
        int routeEdges = 0;

        for (int x = -spawnOffset; x < spawnOffset; x++) {
            int step = Math.abs(terrain.heightAt(x, 0) - terrain.heightAt(x + 1, 0));
            routeMaxStep = Math.max(routeMaxStep, step);
            routeTotalStep += step;
            routeEdges++;
        }

        int spawnLow = Math.min(terrain.heightAt(-spawnOffset, 0), terrain.heightAt(spawnOffset, 0));
        int spawnHigh = Math.max(terrain.heightAt(-spawnOffset, 0), terrain.heightAt(spawnOffset, 0));

        for (int[] anchor : SeededTerrainBlender.anchors(spawnOffset, teamCount)) {
            int h = terrain.heightAt(anchor[0], anchor[1]);
            spawnLow = Math.min(spawnLow, h);
            spawnHigh = Math.max(spawnHigh, h);
        }

        int spawnDifference = spawnHigh - spawnLow;
        double averageStep = edgeCount == 0L ? 0.0 : (double)totalStep / edgeCount;
        double routeAverage = routeEdges == 0 ? 0.0 : (double)routeTotalStep / routeEdges;
        List<String> warnings = new ArrayList<>();
        List<String> rejection = new ArrayList<>();
        if (range > 48) {
            rejection.add("rentang elevasi " + range + " blok (maksimum 48)");
        } else if (range > 28) {
            warnings.add("rentang elevasi cukup besar: " + range + " blok");
        }

        if (spawnDifference > 8) {
            rejection.add("selisih elevasi spawn antar tim " + spawnDifference + " blok (maksimum 8)");
        } else if (spawnDifference > 4) {
            warnings.add("spawn antar tim berbeda tinggi " + spawnDifference + " blok");
        }

        if (maxStep > 1) {
            rejection.add("beda tinggi antarkolom mencapai " + maxStep + " blok (batas wajib 1)");
        }

        if (routeMaxStep > 1) {
            rejection.add(
                "jalur utama RED ke BLUE memiliki beda tinggi " + routeMaxStep + " blok per langkah (batas 1)"
            );
        }

        return new TerrainDetector.Report(
            range, maxStep, averageStep, spawnDifference, routeMaxStep, routeAverage, warnings, rejection
        );
    }

    // $VF: Compiled from TerrainDetector.java
    static final class Report {
        private final int elevationRange;
        private final int maxAdjacentStep;
        private final int spawnDifference;
        private final int routeMaxStep;
        private final double averageStep;
        private final double routeAverageStep;
        private final List<String> warnings;
        private final List<String> rejectionReasons;

        private Report(
            int elevationRange,
            int maxAdjacentStep,
            double averageStep,
            int spawnDifference,
            int routeMaxStep,
            double routeAverageStep,
            List<String> warnings,
            List<String> rejectionReasons
        ) {
            this.elevationRange = elevationRange;
            this.maxAdjacentStep = maxAdjacentStep;
            this.averageStep = averageStep;
            this.spawnDifference = spawnDifference;
            this.routeMaxStep = routeMaxStep;
            this.routeAverageStep = routeAverageStep;
            this.warnings = List.copyOf(warnings);
            this.rejectionReasons = List.copyOf(rejectionReasons);
        }

        boolean accepted() {
            return this.rejectionReasons.isEmpty();
        }

        List<String> warnings() {
            return this.warnings;
        }

        String rejectionReason() {
            return String.join("; ", this.rejectionReasons);
        }

        int maxAdjacentStep() {
            return this.maxAdjacentStep;
        }

        String summary() {
            String status = this.accepted() ? (this.warnings.isEmpty() ? "LAYAK" : "LAYAK DENGAN CATATAN") : "DITOLAK";
            return status
                + " | elevasi="
                + this.elevationRange
                + " | lonjakan tetangga maks="
                + this.maxAdjacentStep
                + " | rata-rata beda tinggi="
                + String.format(Locale.ROOT, "%.2f", this.averageStep)
                + " | spawn diff="
                + this.spawnDifference
                + " | lonjakan jalur="
                + this.routeMaxStep
                + " | rata-rata jalur="
                + String.format(Locale.ROOT, "%.2f", this.routeAverageStep);
        }
    }
}
