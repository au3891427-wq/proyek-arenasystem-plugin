package id.hadziq.arenasystem;

import java.util.List;
import java.util.Locale;

// $VF: Compiled from RelicTeamLayout.java
final class RelicTeamLayout {
    private static final List<String> TEAM_NAMES = List.of("red", "blue", "green", "yellow");

    private RelicTeamLayout() {
    }

    static List<String> teamNames(int teamCount) {
        int safeCount = Math.max(2, Math.min(4, teamCount));
        return TEAM_NAMES.subList(0, safeCount);
    }

    static int[] offset(String team, int teamCount, int distance) {
        return switch (team == null ? "" : team.toLowerCase(Locale.ROOT)) {
            case "red" -> new int[]{-distance, 0};
            case "blue" -> new int[]{distance, 0};
            case "green" -> new int[]{0, -distance};
            case "yellow" -> new int[]{0, distance};
            default -> new int[]{0, 0};
        };
    }
}
