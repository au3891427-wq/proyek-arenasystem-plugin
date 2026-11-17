package id.hadziq.arenasystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates an already-scanned height map for arena suitability.
 * It measures elevation range, adjacent-column cliffs, average roughness,
 * spawn balance, and the main RED-to-BLUE route. It does not modify the world.
 */
final class TerrainDetector {
    private TerrainDetector() {}

    static Report analyze(TerrainProfile terrain, int radius, int spawnOffset) {
        int range = terrain.maxY - terrain.minY;
        long edgeCount = 0;
        long steepEdges = 0;
        long totalStep = 0;
        int maxStep = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int here = terrain.heightAt(x, z);
                if (x < radius) {
                    int step = Math.abs(here - terrain.heightAt(x + 1, z));
                    edgeCount++; totalStep += step; maxStep = Math.max(maxStep, step);
                    if (step >= 5) steepEdges++;
                }
                if (z < radius) {
                    int step = Math.abs(here - terrain.heightAt(x, z + 1));
                    edgeCount++; totalStep += step; maxStep = Math.max(maxStep, step);
                    if (step >= 5) steepEdges++;
                }
            }
        }

        int routeMaxStep = 0;
        long routeTotalStep = 0;
        int routeEdges = 0;
        for (int x = -spawnOffset; x < spawnOffset; x++) {
            int step = Math.abs(terrain.heightAt(x, 0) - terrain.heightAt(x + 1, 0));
            routeMaxStep = Math.max(routeMaxStep, step);
            routeTotalStep += step;
            routeEdges++;
        }

        int spawnDifference = Math.abs(terrain.heightAt(-spawnOffset, 0)
                - terrain.heightAt(spawnOffset, 0));
        double averageStep = edgeCount == 0 ? 0.0 : (double) totalStep / edgeCount;
        double steepRatio = edgeCount == 0 ? 0.0 : (double) steepEdges / edgeCount;
        double routeAverage = routeEdges == 0 ? 0.0 : (double) routeTotalStep / routeEdges;

        List<String> warnings = new ArrayList<>();
        List<String> rejection = new ArrayList<>();
        if (range > 48) rejection.add("rentang elevasi " + range + " blok (maksimum 48)");
        else if (range > 28) warnings.add("rentang elevasi cukup besar: " + range + " blok");

        if (spawnDifference > 8) rejection.add("selisih elevasi spawn RED/BLUE " + spawnDifference + " blok (maksimum 8)");
        else if (spawnDifference > 4) warnings.add("spawn RED/BLUE berbeda tinggi " + spawnDifference + " blok");

        if (maxStep >= 9) rejection.add("terdapat tebing/lompatan permukaan hingga " + maxStep + " blok dalam satu kolom tetangga");
        else if (maxStep >= 5) warnings.add("ada perubahan tinggi tajam hingga " + maxStep + " blok antar kolom");

        if (routeMaxStep > 5) rejection.add("jalur utama RED ke BLUE memiliki tanjakan/tebing " + routeMaxStep + " blok per langkah");
        else if (routeMaxStep >= 3) warnings.add("jalur tengah memiliki perubahan tinggi hingga " + routeMaxStep + " blok");

        if (steepRatio > 0.10) rejection.add(String.format(java.util.Locale.ROOT,
                "%.1f%% batas kolom memiliki lonjakan minimal 5 blok", steepRatio * 100.0));
        else if (steepRatio > 0.035) warnings.add(String.format(java.util.Locale.ROOT,
                "%.1f%% batas kolom memiliki lonjakan minimal 5 blok", steepRatio * 100.0));

        if (averageStep > 2.4) warnings.add(String.format(java.util.Locale.ROOT,
                "permukaan cukup kasar (rata-rata beda tinggi %.2f blok)", averageStep));
        if (routeAverage > 1.8) warnings.add(String.format(java.util.Locale.ROOT,
                "jalur utama tidak rata (rata-rata beda tinggi %.2f blok)", routeAverage));

        return new Report(range, maxStep, averageStep, steepRatio, spawnDifference,
                routeMaxStep, routeAverage, warnings, rejection);
    }

    static final class Report {
        private final int elevationRange, maxAdjacentStep, spawnDifference, routeMaxStep;
        private final double averageStep, steepEdgeRatio, routeAverageStep;
        private final List<String> warnings, rejectionReasons;

        private Report(int elevationRange, int maxAdjacentStep, double averageStep,
                       double steepEdgeRatio, int spawnDifference, int routeMaxStep,
                       double routeAverageStep, List<String> warnings, List<String> rejectionReasons) {
            this.elevationRange = elevationRange;
            this.maxAdjacentStep = maxAdjacentStep;
            this.averageStep = averageStep;
            this.steepEdgeRatio = steepEdgeRatio;
            this.spawnDifference = spawnDifference;
            this.routeMaxStep = routeMaxStep;
            this.routeAverageStep = routeAverageStep;
            this.warnings = List.copyOf(warnings);
            this.rejectionReasons = List.copyOf(rejectionReasons);
        }

        boolean accepted() { return rejectionReasons.isEmpty(); }
        List<String> warnings() { return warnings; }
        String rejectionReason() { return String.join("; ", rejectionReasons); }
        String summary() {
            String status = accepted() ? (warnings.isEmpty() ? "LAYAK" : "LAYAK DENGAN CATATAN") : "DITOLAK";
            return status + " | elevasi=" + elevationRange + " | lonjakan tetangga maks=" + maxAdjacentStep
                    + " | rata-rata beda tinggi=" + String.format(java.util.Locale.ROOT, "%.2f", averageStep)
                    + " | spawn diff=" + spawnDifference + " | lonjakan jalur=" + routeMaxStep;
        }
    }
}
