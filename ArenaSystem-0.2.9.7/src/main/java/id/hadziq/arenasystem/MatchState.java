package id.hadziq.arenasystem;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

// $VF: Compiled from MatchState.java
final class MatchState {
    final String arenaName;
    final String mode;
    final Map<UUID, String> teams = new LinkedHashMap<>();
    final Map<BlockPos, String> originalBlocks = new LinkedHashMap<>();
    final Map<BlockPos, String> lobbyOriginalBlocks = new LinkedHashMap<>();
    final Map<UUID, PlayerSnapshot> playerSnapshots = new HashMap<>();
    final Map<UUID, String> selectedClasses = new HashMap<>();
    final Map<UUID, String> randomStuffTokens = new HashMap<>();
    final Map<UUID, Map<String, Long>> abilityCooldowns = new HashMap<>();
    final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    final Map<UUID, Scoreboard> hudScoreboards = new HashMap<>();
    final Map<UUID, Objective> hudObjectives = new HashMap<>();
    int redScore;
    int blueScore;
    final Map<String, Integer> teamScores = new LinkedHashMap<>();
    int secondsElapsed;
    int secondsRemaining;
    int preparationSecondsRemaining;
    int lobbyY;
    String relicToken;
    UUID relicCarrier;
    final Map<UUID, Float> relicOriginalWalkSpeeds = new HashMap<>();
    boolean relicDropped;
    boolean running;
    boolean preparing;
    boolean launching;
    boolean ending;
    boolean singlePlayerMode;
    BukkitTask timer;
    BukkitTask preparationTimer;
    BukkitTask lobbyBuildTask;
    BukkitTask lobbyRestoreTask;

    MatchState(String arenaName, String mode) {
        this.arenaName = arenaName;
        this.mode = mode == null ? "tdm" : mode;
    }
}
