package id.hadziq.arenasystem;

import java.util.List;
import java.util.Locale;

final class RelicTeamLayout {
    private static final List<String> TEAM_NAMES = List.of("red", "blue", "green", "yellow");

    private RelicTeamLayout() {
    }

    static List<String> teamNames(int teamCount) {
        int safeCount = Math.max(2, Math.min(4, teamCount));
        return TEAM_NAMES.subList(0, safeCount);
    }

    static int[] sanctumGate(String team, int teamCount, int distance) {
        if (distance <= 0) {
            throw new IllegalArgumentException("spawn distance must be positive");
        }
        int[] spawn = offset(team, teamCount, distance);
        double length = Math.hypot(spawn[0], spawn[1]);
        if (length == 0.0) {
            throw new IllegalArgumentException("unknown team has no spawn position");
        }
        return new int[]{
            spawn[0] - (int)Math.round(spawn[0] * 6.0 / length),
            spawn[1] - (int)Math.round(spawn[1] * 6.0 / length)
        };
    }

    static int[] offset(String team, int teamCount, int distance) {
        String safeTeam = team == null ? "" : team.toLowerCase(Locale.ROOT);
        if (teamCount == 3) {
            int halfWidth = (int)Math.round(distance * Math.sqrt(3.0) / 2.0);
            int halfHeight = (int)Math.round(distance / 2.0);
            return switch (safeTeam) {
                case "red" -> new int[]{-halfWidth, halfHeight};
                case "blue" -> new int[]{halfWidth, halfHeight};
                case "green" -> new int[]{0, -distance};
                default -> new int[]{0, 0};
            };
        }

        return switch (safeTeam) {
            case "red" -> new int[]{-distance, 0};
            case "blue" -> new int[]{distance, 0};
            case "green" -> new int[]{0, -distance};
            case "yellow" -> new int[]{0, distance};
            default -> new int[]{0, 0};
        };
    }
}
