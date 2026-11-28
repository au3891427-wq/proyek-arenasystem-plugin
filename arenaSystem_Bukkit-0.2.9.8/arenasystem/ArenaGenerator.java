package id.hadziq.arenasystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

// $VF: Compiled from ArenaGenerator.java
final class ArenaGenerator {
    private ArenaGenerator() {
    }

    static List<BlockEdit> createPlan(
        World world,
        ArenaData arena,
        TerrainProfile originalTerrain,
        TerrainProfile terrain,
        long seed,
        int radius,
        int teamCount
    ) {
        Map<BlockPos, BlockEdit> edits = new LinkedHashMap<>();
        normalizeSurface(world, arena, originalTerrain, terrain, edits, radius);
        queueCombatVegetationCleanup(world, arena, terrain, edits, radius);
        if ("relic_rush".equalsIgnoreCase(arena.mode)) {
            buildRelicRush(world, arena, terrain, edits, seed, radius, teamCount);
        } else {
            buildTeamDeathmatch(world, arena, terrain, edits, seed, radius, teamCount);
        }

        buildGenericStructures(world, arena, terrain, edits, seed, radius, teamCount);
        if ("relic_rush".equalsIgnoreCase(arena.mode)) {
            clearRelicSpawnHeadroom(arena, terrain, edits, teamCount);
            if (teamCount >= 3) {
                clearRelicLaneHeadroom(world, arena, terrain, edits, teamCount);
            }

            // Build the shrine last so team-route headroom edits cannot erase altar blocks.
            buildRelicShrine(world, arena, terrain, edits);
        }
        if ("relic_rush".equalsIgnoreCase(arena.mode)
            && !NavigationScanner.hasRelicRoutes(arena, terrain, edits, arena.spawnOffset, teamCount)) {
            throw new IllegalStateException(
                "Relic Rush ditolak: minimal dua rute terpisah dari setiap markas menuju altar tidak tersedia."
            );
        }

        buildPerimeter(world, arena, terrain, edits, radius);
        return new ArrayList<>(edits.values());
    }

    private static void queueCombatVegetationCleanup(
        World world, ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int radius
    ) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int surfaceY = terrain.heightAt(x, z);
                int maxY = Math.min(world.getMaxHeight() - 1, surfaceY + 12);

                for (int y = surfaceY + 1; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y - arena.centerY, z);
                    if (!edits.containsKey(pos)) {
                        Block block = world.getBlockAt(arena.centerX + x, y, arena.centerZ + z);
                        if (isCombatVegetation(block.getType()) && !(block.getState() instanceof TileState)) {
                            add(edits, x, y - arena.centerY, z, Material.AIR);
                        }
                    }
                }
            }
        }
    }

    private static void normalizeSurface(
        World world,
        ArenaData arena,
        TerrainProfile originalTerrain,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int radius
    ) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int originalY = originalTerrain.heightAt(x, z);
                int targetY = terrain.heightAt(x, z);
                if (originalY != targetY) {
                    int lowY = Math.min(originalY, targetY);
                    int highY = Math.max(originalY, targetY);
                    if (lowY < world.getMinHeight() || highY >= world.getMaxHeight()) {
                        throw new IllegalStateException(
                            "normalisasi melewati batas tinggi world pada kolom "
                                + (arena.centerX + x)
                                + ","
                                + (arena.centerZ + z)
                        );
                    }

                    for (int y = lowY; y <= highY; y++) {
                        Block block = world.getBlockAt(arena.centerX + x, y, arena.centerZ + z);
                        if (block.getState() instanceof TileState) {
                            throw new IllegalStateException(
                                "normalisasi akan menyentuh block entity di "
                                    + (arena.centerX + x)
                                    + ","
                                    + y
                                    + ","
                                    + (arena.centerZ + z)
                            );
                        }
                    }

                    if (targetY > originalY) {
                        for (int y = originalY; y < targetY; y++) {
                            add(edits, x, y - arena.centerY, z, Material.DIRT);
                        }

                        add(edits, x, targetY - arena.centerY, z, Material.GRASS_BLOCK);
                    } else {
                        for (int y = targetY + 1; y <= originalY; y++) {
                            add(edits, x, y - arena.centerY, z, Material.AIR);
                        }

                        add(edits, x, targetY - arena.centerY, z, Material.GRASS_BLOCK);
                    }
                }
            }
        }
    }

    private static void buildTeamDeathmatch(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        long seed,
        int radius,
        int teamCount
    ) {
        Random pathRandom = SeededArenaLayout.tdmPathRandom(seed);

        for (int x = -27; x <= 27; x++) {
            for (int z = -2; z <= 2; z++) {
                Material path = pathRandom.nextInt(10) < 4
                    ? Material.GRAVEL
                    : (pathRandom.nextBoolean() ? Material.COBBLESTONE : Material.COARSE_DIRT);
                addSurface(world, arena, terrain, edits, x, z, path);
            }
        }

        for (int z = -19; z <= 19; z++) {
            for (int x = -2; x <= 2; x++) {
                addSurface(
                    world,
                    arena,
                    terrain,
                    edits,
                    x,
                    z,
                    Math.abs(x) == 2 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE
                );
            }
        }

        buildTdmSpawnPad(world, arena, terrain, edits, -27, 0, Material.RED_TERRACOTTA);
        buildTdmSpawnPad(world, arena, terrain, edits, 27, 0, Material.BLUE_TERRACOTTA);
        if (teamCount >= 3) {
            buildTdmSpawnPad(world, arena, terrain, edits, 0, -27, Material.GREEN_TERRACOTTA);
        }

        if (teamCount >= 4) {
            buildTdmSpawnPad(world, arena, terrain, edits, 0, 27, Material.YELLOW_TERRACOTTA);
        }

        buildTdmCentre(world, arena, terrain, edits);
        buildRuin(world, arena, terrain, edits, -15, -15, Material.MOSSY_STONE_BRICKS);
        buildRuin(world, arena, terrain, edits, -15, 15, Material.MOSSY_STONE_BRICKS);
        buildRuin(world, arena, terrain, edits, 15, -15, Material.MOSSY_STONE_BRICKS);
        buildRuin(world, arena, terrain, edits, 15, 15, Material.MOSSY_STONE_BRICKS);
        Material[] coverPalette = new Material[]{
            Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE_BRICKS, Material.ANDESITE
        };

        for (SeededArenaLayout.CoverSite site : SeededArenaLayout.teamDeathmatch(seed)) {
            Material coverMaterial = coverPalette[site.paletteIndex()];

            for (int signX : new int[]{-1, 1}) {
                for (int signZ : new int[]{-1, 1}) {
                    buildLowCover(
                        world, arena, terrain, edits, signX * site.x(), signZ * site.z(), coverMaterial, site.alongX()
                    );
                }
            }
        }
    }

    private static void buildRelicRush(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        long seed,
        int radius,
        int teamCount
    ) {
        double scale = radius / 40.0;
        int laneEnd = radius - 14;
        int sideLaneOffset = (int)Math.round(18.0 * scale);
        int crossLaneOuter = (int)Math.round(29.0 * scale);
        int crossLaneInner = (int)Math.round(16.0 * scale);
        int laneHalfWidth = Math.max(2, (int)Math.round(2.0 * scale));
        if (teamCount <= 2) {
            for (int x = -laneEnd; x <= laneEnd; x++) {
                for (int z = -laneHalfWidth; z <= laneHalfWidth; z++) {
                    addSurface(
                        world,
                        arena,
                        terrain,
                        edits,
                        x,
                        z,
                        Math.abs(z) == laneHalfWidth ? Material.CUT_SANDSTONE : Material.SMOOTH_SANDSTONE
                    );
                }

                for (int side : new int[]{-sideLaneOffset, sideLaneOffset}) {
                    for (int dz = -laneHalfWidth; dz <= laneHalfWidth; dz++) {
                        addSurface(
                            world,
                            arena,
                            terrain,
                            edits,
                            x,
                            side + dz,
                            Math.abs(dz) == laneHalfWidth ? Material.SANDSTONE : Material.CUT_SANDSTONE
                        );
                    }
                }
            }

            int[] crossLanes = new int[]{-crossLaneOuter, -crossLaneInner, 0, crossLaneInner, crossLaneOuter};

            for (int x : crossLanes) {
                for (int z = -sideLaneOffset; z <= sideLaneOffset; z++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        addSurface(
                            world,
                            arena,
                            terrain,
                            edits,
                            x + dx,
                            z,
                            Math.abs(dx) == 1 ? Material.CUT_SANDSTONE : Material.SMOOTH_SANDSTONE
                        );
                    }
                }
            }
        } else {
            buildRelicTeamRoutes(world, arena, terrain, edits, teamCount);
        }

        for (String team : RelicTeamLayout.teamNames(teamCount)) {
            int[] spawn = RelicTeamLayout.offset(team, teamCount, arena.spawnOffset);
            buildRelicSanctum(
                world,
                arena,
                terrain,
                edits,
                spawn[0],
                spawn[1],
                relicTeamMaterial(team),
                -Integer.signum(spawn[0]),
                -Integer.signum(spawn[1])
            );
        }

        Random outpostLayout = SeededArenaLayout.relicOutpostRandom(seed);
        if (teamCount <= 2) {
            int nearX = (int)Math.round(19.0 * scale);
            int nearZ = (int)Math.round(9.0 * scale);
            int farX = (int)Math.round(9.0 * scale);
            int farZ = (int)Math.round(28.0 * scale);
            buildRelicOutpostPair(
                world, arena, terrain, edits, nearX, -nearZ, Material.MOSSY_STONE_BRICKS, outpostLayout.nextBoolean()
            );
            buildRelicOutpostPair(
                world, arena, terrain, edits, nearX, nearZ, Material.MOSSY_STONE_BRICKS, outpostLayout.nextBoolean()
            );
            buildRelicOutpostPair(
                world, arena, terrain, edits, farX, -farZ, Material.CHISELED_SANDSTONE, outpostLayout.nextBoolean()
            );
            buildRelicOutpostPair(
                world, arena, terrain, edits, farX, farZ, Material.CHISELED_SANDSTONE, outpostLayout.nextBoolean()
            );
            int ringInnerX = (int)Math.round(24.0 * scale);
            int ringInnerZ = (int)Math.round(28.0 * scale);
            int ringOuterX = (int)Math.round(36.0 * scale);
            int ringOuterZ = (int)Math.round(18.0 * scale);
            int ringOuter = (int)Math.round(25.0 * scale);
            buildRelicOutpostPair(
                world,
                arena,
                terrain,
                edits,
                ringInnerX,
                -ringInnerZ,
                Material.MOSSY_STONE_BRICKS,
                outpostLayout.nextBoolean()
            );
            buildRelicOutpostPair(
                world,
                arena,
                terrain,
                edits,
                ringInnerX,
                ringInnerZ,
                Material.MOSSY_STONE_BRICKS,
                outpostLayout.nextBoolean()
            );
            buildRelicOutpostPair(
                world,
                arena,
                terrain,
                edits,
                ringOuterX,
                -ringOuterZ,
                Material.CHISELED_SANDSTONE,
                outpostLayout.nextBoolean()
            );
            buildRelicOutpostPair(
                world,
                arena,
                terrain,
                edits,
                ringOuterX,
                ringOuterZ,
                Material.CHISELED_SANDSTONE,
                outpostLayout.nextBoolean()
            );
            int[] coverXs = new int[]{
                (int)Math.round(-27.0 * scale),
                (int)Math.round(-13.0 * scale),
                (int)Math.round(13.0 * scale),
                (int)Math.round(27.0 * scale)
            };

            for (int x : coverXs) {
                buildLowCover(world, arena, terrain, edits, x, 0, Material.COBBLESTONE);
                buildLowCover(world, arena, terrain, edits, x, -sideLaneOffset, Material.MOSSY_STONE_BRICKS);
                buildLowCover(world, arena, terrain, edits, x, sideLaneOffset, Material.MOSSY_STONE_BRICKS);
            }

            for (int x : new int[]{-crossLaneOuter, crossLaneOuter}) {
                for (int z : new int[]{-ringOuterZ, ringOuterZ, -ringOuter, ringOuter}) {
                    buildLowCover(world, arena, terrain, edits, x, z, Material.COBBLESTONE);
                }
            }

            buildRuin(world, arena, terrain, edits, -crossLaneOuter, -sideLaneOffset + 2, Material.CUT_SANDSTONE);
            buildRuin(world, arena, terrain, edits, -crossLaneOuter, sideLaneOffset - 2, Material.CUT_SANDSTONE);
            buildRuin(world, arena, terrain, edits, crossLaneOuter, -sideLaneOffset + 2, Material.CUT_SANDSTONE);
            buildRuin(world, arena, terrain, edits, crossLaneOuter, sideLaneOffset - 2, Material.CUT_SANDSTONE);
            Material[] palette = new Material[]{
                Material.MOSSY_STONE_BRICKS, Material.CHISELED_SANDSTONE, Material.CUT_SANDSTONE, Material.STONE_BRICKS
            };

            for (SeededArenaLayout.CoverSite site : SeededArenaLayout.relicRush(seed)) {
                Material material = palette[site.paletteIndex()];

                for (int signX : new int[]{-1, 1}) {
                    for (int signZ : new int[]{-1, 1}) {
                        buildLowCover(
                            world, arena, terrain, edits, signX * site.x(), signZ * site.z(), material, site.alongX()
                        );
                    }
                }
            }
        } else {
            buildRelicComplexRings(world, arena, terrain, edits, outpostLayout, teamCount);
        }
    }

    private static Material relicTeamMaterial(String team) {
        return switch (team) {
            case "red" -> Material.RED_TERRACOTTA;
            case "blue" -> Material.BLUE_TERRACOTTA;
            case "green" -> Material.GREEN_TERRACOTTA;
            case "yellow" -> Material.YELLOW_TERRACOTTA;
            default -> Material.WHITE_TERRACOTTA;
        };
    }

    private static void buildRelicTeamRoutes(
        World world, ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int teamCount
    ) {
        for (String team : RelicTeamLayout.teamNames(teamCount)) {
            int[] spawn = RelicTeamLayout.offset(team, teamCount, arena.spawnOffset);
            double length = Math.hypot(spawn[0], spawn[1]);
            double inwardX = -spawn[0] / length;
            double inwardZ = -spawn[1] / length;
            double sideX = -inwardZ;
            double sideZ = inwardX;
            int startX = (int)Math.round(spawn[0] + inwardX * 7.0);
            int startZ = (int)Math.round(spawn[1] + inwardZ * 7.0);
            paintRelicLane(world, arena, terrain, edits, startX, startZ, 0, 0);

            for (int side : new int[]{-1, 1}) {
                int sideStartX = (int)Math.round(startX + sideX * side * 7.0);
                int sideStartZ = (int)Math.round(startZ + sideZ * side * 7.0);
                int sideEndX = (int)Math.round(sideX * side * 7.0);
                int sideEndZ = (int)Math.round(sideZ * side * 7.0);
                paintRelicLane(world, arena, terrain, edits, startX, startZ, sideStartX, sideStartZ);
                paintRelicLane(world, arena, terrain, edits, sideStartX, sideStartZ, sideEndX, sideEndZ);
                paintRelicLane(world, arena, terrain, edits, sideEndX, sideEndZ, 0, 0);
            }
        }
    }

private static void clearRelicSpawnHeadroom(
        ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int teamCount
    ) {
        for (String team : RelicTeamLayout.teamNames(teamCount)) {
            int[] spawn = RelicTeamLayout.offset(team, teamCount, arena.spawnOffset);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = spawn[0] + dx;
                    int z = spawn[1] + dz;
                    int surfaceY = terrain.heightAt(x, z);
                    for (int dy = 1; dy <= 3; dy++) {
                        add(edits, x, surfaceY + dy - arena.centerY, z, Material.AIR);
                    }
                }
            }
        }
    }

private static void clearRelicLaneHeadroom(
        World world, ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int teamCount
    ) {
        for (String team : RelicTeamLayout.teamNames(teamCount)) {
            int[] spawn = RelicTeamLayout.offset(team, teamCount, arena.spawnOffset);
            double length = Math.hypot(spawn[0], spawn[1]);
            double inwardX = -spawn[0] / length;
            double inwardZ = -spawn[1] / length;
            double sideX = -inwardZ;
            double sideZ = inwardX;
            int startX = (int)Math.round(spawn[0] + inwardX * 7.0);
            int startZ = (int)Math.round(spawn[1] + inwardZ * 7.0);
            clearRelicSegmentHeadroom(world, arena, terrain, edits, startX, startZ, 0, 0);

            for (int side : new int[]{-1, 1}) {
                int sideStartX = (int)Math.round(startX + sideX * side * 7.0);
                int sideStartZ = (int)Math.round(startZ + sideZ * side * 7.0);
                int sideEndX = (int)Math.round(sideX * side * 7.0);
                int sideEndZ = (int)Math.round(sideZ * side * 7.0);
                clearRelicSegmentHeadroom(
                    world, arena, terrain, edits, startX, startZ, sideStartX, sideStartZ
                );
                clearRelicSegmentHeadroom(
                    world, arena, terrain, edits, sideStartX, sideStartZ, sideEndX, sideEndZ
                );
                clearRelicSegmentHeadroom(
                    world, arena, terrain, edits, sideEndX, sideEndZ, 0, 0
                );
            }
        }
    }

private static void clearRelicSegmentHeadroom(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int startX,
        int startZ,
        int endX,
        int endZ
    ) {
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        steps = Math.max(1, steps);

        for (int step = 0; step <= steps; step++) {
            int x = (int)Math.round(startX + (endX - startX) * ((double)step / steps));
            int z = (int)Math.round(startZ + (endZ - startZ) * ((double)step / steps));

            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int laneX = x + dx;
                    int laneZ = z + dz;
                    int surfaceY = terrain.heightAt(laneX, laneZ);
                    for (int dy = 1; dy <= 2; dy++) {
                        Block block = world.getBlockAt(
                            arena.centerX + laneX, surfaceY + dy, arena.centerZ + laneZ
                        );
                        if (block.getState() instanceof TileState) {
                            throw new IllegalStateException(
                                "jalur Relic Rush akan menyentuh block entity pada kolom "
                                    + (arena.centerX + laneX)
                                    + ","
                                    + (arena.centerZ + laneZ)
                            );
                        }
                        add(edits, laneX, surfaceY + dy - arena.centerY, laneZ, Material.AIR);
                    }
                }
            }
        }
    }

    private static void paintRelicLane(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int startX,
        int startZ,
        int endX,
        int endZ
    ) {
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        steps = Math.max(1, steps);

        for (int step = 0; step <= steps; step++) {
            int x = (int)Math.round(startX + (endX - startX) * ((double)step / steps));
            int z = (int)Math.round(startZ + (endZ - startZ) * ((double)step / steps));

            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int edge = Math.max(Math.abs(dx), Math.abs(dz));
                    addSurface(
                        world,
                        arena,
                        terrain,
                        edits,
                        x + dx,
                        z + dz,
                        edge == 2 ? Material.CUT_SANDSTONE : Material.SMOOTH_SANDSTONE
                    );
                }
            }
        }
    }

    private static void buildRelicComplexRings(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        Random random,
        int teamCount
    ) {
        int count = teamCount == 3 ? 6 : 8;

        for (int ring = 0; ring < 2; ring++) {
            double radius = ring == 0 ? 21.0 : 35.0;
            double rotation = ring == 0 ? 0.0 : Math.PI / count;

            for (int i = 0; i < count; i++) {
                double angle = rotation + (Math.PI * 2) * i / count;
                int x = (int)Math.round(Math.cos(angle) * radius);
                int z = (int)Math.round(Math.sin(angle) * radius);
                Material wall = (i + ring) % 2 == 0 ? Material.MOSSY_STONE_BRICKS : Material.CHISELED_SANDSTONE;
                buildRelicOutpost(
                    world, arena, terrain, edits, x, z, wall, Math.abs(Math.cos(angle)) >= Math.abs(Math.sin(angle))
                );
            }
        }
    }

    private static void buildRelicOutpostPair(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int absX,
        int z,
        Material wallMaterial,
        boolean coverAlongX
    ) {
        buildRelicOutpost(world, arena, terrain, edits, -absX, z, wallMaterial, coverAlongX);
        buildRelicOutpost(world, arena, terrain, edits, absX, z, wallMaterial, coverAlongX);
    }

    private static void buildTdmSpawnPad(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int centerX,
        int centerZ,
        Material teamMaterial
    ) {
        for (int x = centerX - 4; x <= centerX + 4; x++) {
            for (int z = centerZ - 4; z <= centerZ + 4; z++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                int distance = Math.max(Math.abs(dx), Math.abs(dz));
                Material material = distance == 4 ? Material.WHITE_CONCRETE : teamMaterial;
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    material = Material.SMOOTH_STONE;
                }

                addSurface(world, arena, terrain, edits, x, z, material);
            }
        }

        int spawnY = terrain.heightAt(centerX, centerZ);
        addAir(world, arena, edits, centerX, spawnY + 1 - arena.centerY, centerZ, Material.SEA_LANTERN);
    }

    private static void buildTdmCentre(
        World world, ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits
    ) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                int distance = Math.max(Math.abs(x), Math.abs(z));
                Material material = distance == 4 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS;
                if (distance <= 1) {
                    material = Material.SMOOTH_STONE;
                }

                addSurface(world, arena, terrain, edits, x, z, material);
            }
        }

        int y = terrain.heightAt(0, 0) - arena.centerY;
        addAir(world, arena, edits, 0, y + 1, 0, Material.GOLD_BLOCK);
        addAir(world, arena, edits, 0, y + 2, 0, Material.SEA_LANTERN);

        for (int[] corner : new int[][]{{-4, -4}, {-4, 4}, {4, -4}, {4, 4}}) {
            for (int h = 1; h <= 3; h++) {
                addAir(
                    world,
                    arena,
                    edits,
                    corner[0],
                    y + h,
                    corner[1],
                    h == 3 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS
                );
            }
        }
    }

    private static void buildRelicSanctum(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int centerX,
        int centerZ,
        Material teamMaterial,
        int inwardX,
        int inwardZ
    ) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int distance = Math.max(Math.abs(dx), Math.abs(dz));
                Material floor = distance == 6 ? Material.WHITE_CONCRETE : teamMaterial;
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    floor = Material.SMOOTH_STONE;
                }

                if (distance == 5 && (dx == 0 || dz == 0)) {
                    floor = Material.CHISELED_SANDSTONE;
                }

                addSurface(world, arena, terrain, edits, centerX + dx, centerZ + dz, floor);
            }
        }

        for (int dx : new int[]{-6, 6}) {
            for (int dz : new int[]{-6, 6}) {
                buildTerrainPillar(
                    world,
                    arena,
                    terrain,
                    edits,
                    centerX + dx,
                    centerZ + dz,
                    4,
                    teamMaterial,
                    Material.SMOOTH_SANDSTONE
                );
            }
        }

        int perpendicularX = -inwardZ;
        int perpendicularZ = inwardX;
        int gateX = centerX + inwardX * 6;
        int gateZ = centerZ + inwardZ * 6;

        for (int side : new int[]{-3, 3}) {
            buildTerrainPillar(
                world,
                arena,
                terrain,
                edits,
                gateX + perpendicularX * side,
                gateZ + perpendicularZ * side,
                4,
                teamMaterial,
                Material.SMOOTH_SANDSTONE
            );
        }

        int gateY = terrain.heightAt(gateX, gateZ) - arena.centerY;

        for (int width = -2; width <= 2; width++) {
            addAir(
                world,
                arena,
                edits,
                gateX + perpendicularX * width,
                gateY + 4,
                gateZ + perpendicularZ * width,
                Material.CHISELED_SANDSTONE
            );
        }

        int spawnY = terrain.heightAt(centerX, centerZ) - arena.centerY;
        addAir(
            world,
            arena,
            edits,
            centerX,
            spawnY + 1,
            centerZ,
            teamMaterial == Material.RED_TERRACOTTA ? Material.REDSTONE_LAMP : Material.SEA_LANTERN
        );
    }

    private static void buildRelicShrine(
        World world, ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits
    ) {
        int altarY = terrain.heightAt(0, 0);
        if (altarY <= world.getMinHeight() || altarY + 4 >= world.getMaxHeight()) {
            throw new IllegalStateException("ketinggian fondasi altar berada di luar batas world.");
        }

        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                int surfaceY = terrain.heightAt(x, z);
                int supportY = findRelicAltarSupportY(
                    world, arena.centerX + x, arena.centerZ + z, altarY
                );
                if (supportY == Integer.MIN_VALUE) {
                    throw new IllegalStateException(
                        "tidak ditemukan blok tanah solid untuk menopang fondasi altar pada kolom "
                            + (arena.centerX + x) + "," + (arena.centerZ + z)
                    );
                }

                int clearTopY = Math.max(surfaceY, altarY + 4);
                if (clearTopY >= world.getMaxHeight()) {
                    throw new IllegalStateException(
                        "ruang altar melewati batas tinggi world pada kolom "
                            + (arena.centerX + x) + "," + (arena.centerZ + z)
                    );
                }

                // Validate the complete support-to-headroom column before scheduling any edits.
                for (int y = supportY; y <= clearTopY; y++) {
                    Block block = world.getBlockAt(arena.centerX + x, y, arena.centerZ + z);
                    if (block.getState() instanceof TileState) {
                        throw new IllegalStateException(
                            "fondasi altar akan menyentuh block entity pada "
                                + (arena.centerX + x) + "," + y + "," + (arena.centerZ + z)
                        );
                    }
                }

                int distance = Math.max(Math.abs(x), Math.abs(z));
                Material floor = distance == 6
                    ? Material.CHISELED_SANDSTONE
                    : (distance >= 4 ? Material.CUT_SANDSTONE : Material.SMOOTH_SANDSTONE);
                if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    floor = Material.POLISHED_BLACKSTONE_BRICKS;
                }

                // Bridge every gap below the altar floor down to the first solid terrain block.
                for (int y = supportY + 1; y < altarY; y++) {
                    add(edits, x, y - arena.centerY, z, Material.CUT_SANDSTONE);
                }

                // Level the floor and clear terrain/obstructions above it.
                add(edits, x, altarY - arena.centerY, z, floor);
                for (int y = altarY + 1; y <= clearTopY; y++) {
                    add(edits, x, y - arena.centerY, z, Material.AIR);
                }
            }
        }

        int groundY = altarY - arena.centerY;
        add(edits, 0, groundY + 1, 0, Material.POLISHED_BLACKSTONE_BRICKS);
        add(edits, 0, groundY + 2, 0, Material.CHISELED_SANDSTONE);
        add(edits, 0, groundY + 3, 0, Material.GOLD_BLOCK);
        add(edits, 0, groundY + 4, 0, Material.SEA_LANTERN);

        for (int[] point : new int[][]{{-5, -5}, {-5, 5}, {5, -5}, {5, 5}}) {
            int pillarBaseY = altarY - arena.centerY;
            for (int height = 1; height <= 3; height++) {
                add(
                    edits,
                    point[0],
                    pillarBaseY + height,
                    point[1],
                    height == 3 ? Material.AMETHYST_BLOCK : Material.PURPUR_BLOCK
                );
            }
        }
    }

    private static int findRelicAltarSupportY(World world, int blockX, int blockZ, int altarY) {
        for (int y = altarY - 1; y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(blockX, y, blockZ);
            if (block.getState() instanceof TileState) {
                throw new IllegalStateException(
                    "pencarian penopang altar menemukan block entity pada "
                        + blockX + "," + y + "," + blockZ
                );
            }

            Material material = block.getType();
            String name = material.name();
            boolean foliage = name.endsWith("_LEAVES")
                || name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.equals("AZALEA")
                || name.equals("FLOWERING_AZALEA");
            if (material.isSolid()
                && !material.isAir()
                && !isLiquid(material)
                && !isCombatVegetation(material)
                && !foliage) {
                return y;
            }
        }

        return Integer.MIN_VALUE;
    }

    private static void buildRelicOutpost(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int centerX,
        int centerZ,
        Material wallMaterial,
        boolean coverAlongX
    ) {
        for (int offset = -6; offset <= 6; offset++) {
            if (Math.abs(offset) > 1) {
                placeRelicWallColumn(world, arena, terrain, edits, centerX + offset, centerZ - 6, wallMaterial, 2);
                placeRelicWallColumn(world, arena, terrain, edits, centerX + offset, centerZ + 6, wallMaterial, 2);
                placeRelicWallColumn(world, arena, terrain, edits, centerX - 6, centerZ + offset, wallMaterial, 2);
                placeRelicWallColumn(world, arena, terrain, edits, centerX + 6, centerZ + offset, wallMaterial, 2);
            }
        }

        for (int offset = -6; offset <= 6; offset++) {
            if (Math.abs(offset) > 1 && Math.abs(offset) != 4) {
                placeRelicWallColumn(world, arena, terrain, edits, centerX, centerZ + offset, wallMaterial, 2);
                placeRelicWallColumn(world, arena, terrain, edits, centerX + offset, centerZ, wallMaterial, 2);
            }
        }

        for (int dx : new int[]{-6, 6}) {
            for (int dz : new int[]{-6, 6}) {
                placeRelicWallColumn(
                    world, arena, terrain, edits, centerX + dx, centerZ + dz, Material.CHISELED_STONE_BRICKS, 3
                );
            }
        }

        for (int signX : new int[]{-1, 1}) {
            for (int signZ : new int[]{-1, 1}) {
                for (int dx = 2; dx <= 5; dx++) {
                    for (int dz = 2; dz <= 5; dz++) {
                        addSurface(
                            world,
                            arena,
                            terrain,
                            edits,
                            centerX + signX * dx,
                            centerZ + signZ * dz,
                            Material.CUT_SANDSTONE
                        );
                    }
                }

                buildLowCover(
                    world, arena, terrain, edits, centerX + signX * 4, centerZ + signZ * 4, wallMaterial, coverAlongX
                );
            }
        }
    }

    private static void placeRelicWallColumn(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int x,
        int z,
        Material material,
        int height
    ) {
        int baseY = terrain.heightAt(x, z) - arena.centerY;

        for (int y = 1; y <= height; y++) {
            addAir(world, arena, edits, x, baseY + y, z, y == height ? Material.STONE_BRICK_WALL : material);
        }
    }

    private static void buildTerrainPillar(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int x,
        int z,
        int height,
        Material body,
        Material cap
    ) {
        int baseY = terrain.heightAt(x, z) - arena.centerY;

        for (int h = 1; h <= height; h++) {
            addAir(world, arena, edits, x, baseY + h, z, h == height ? cap : body);
        }
    }

    private static void buildLowCover(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int centerX,
        int centerZ,
        Material material
    ) {
        buildLowCover(world, arena, terrain, edits, centerX, centerZ, material, true);
    }

    private static void buildLowCover(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int centerX,
        int centerZ,
        Material material,
        boolean alongX
    ) {
        for (int offset = -1; offset <= 1; offset++) {
            int x = alongX ? centerX + offset : centerX;
            int z = alongX ? centerZ : centerZ + offset;
            int y = terrain.heightAt(x, z) - arena.centerY;
            addAir(world, arena, edits, x, y + 1, z, material);
            if (offset == 0) {
                addAir(world, arena, edits, x, y + 2, z, Material.STONE_BRICK_WALL);
            }
        }
    }

    private static void buildRuin(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int centerX,
        int centerZ,
        Material material
    ) {
        int[][] corners = new int[][]{{-2, -2}, {-2, 2}, {2, -2}, {2, 2}};

        for (int[] corner : corners) {
            int x = centerX + corner[0];
            int z = centerZ + corner[1];
            int baseY = terrain.heightAt(x, z) - arena.centerY;
            int height = 2 + Math.floorMod(centerX * 7 + centerZ * 3 + corner[0] - corner[1], 3);

            for (int y = 1; y <= height; y++) {
                addAir(world, arena, edits, x, baseY + y, z, y == height ? Material.CHISELED_STONE_BRICKS : material);
            }
        }
    }

    private static void buildGenericStructures(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        long seed,
        int radius,
        int teamCount
    ) {
        boolean relic = "relic_rush".equalsIgnoreCase(arena.mode);
        if (!relic || teamCount < 3) {
            Random random = new Random(seed ^ 5135597069889852739L);

            Material wall = switch (random.nextInt(3)) {
                case 0 -> Material.STONE_BRICKS;
                case 1 -> Material.MOSSY_STONE_BRICKS;
                default -> Material.COBBLESTONE;
            };
            NavigationScanner navigation = NavigationScanner.scan(arena, terrain, edits);
            int targetPairs = relic ? 4 : 3;
            int placedPairs = 0;

            for (int[] candidate : genericStructureCandidates(radius, relic, random, navigation)) {
                if (placedPairs >= targetPairs) {
                    break;
                }

                int x = candidate[0];
                int z = candidate[1];
                int template = random.nextInt(2);
                int[][] pair = candidate[2] == 0 ? new int[][]{{-x, -z}, {x, z}} : new int[][]{{-x, z}, {x, -z}};
                if (isGenericRegionSafe(arena, pair[0][0], pair[0][1], relic, teamCount)
                    && isGenericRegionSafe(arena, pair[1][0], pair[1][1], relic, teamCount)
                    && isGenericSiteClear(world, arena, terrain, edits, pair[0][0], pair[0][1], template)
                    && isGenericSiteClear(world, arena, terrain, edits, pair[1][0], pair[1][1], template)) {
                    Map<BlockPos, BlockEdit> beforePair = new LinkedHashMap<>(edits);
                    buildGenericStructure(world, arena, terrain, edits, pair[0][0], pair[0][1], template, wall);
                    buildGenericStructure(world, arena, terrain, edits, pair[1][0], pair[1][1], template, wall);
                    boolean housesAccessible = isGenericHouseAccessible(
                            world, arena, terrain, edits, pair[0][0], pair[0][1]
                        )
                        && isGenericHouseAccessible(world, arena, terrain, edits, pair[1][0], pair[1][1]);
                    boolean routesConnected = !relic
                        || NavigationScanner.hasRelicRoutes(arena, terrain, edits, arena.spawnOffset, teamCount);
                    if (housesAccessible && routesConnected) {
                        placedPairs++;
                    } else {
                        edits.clear();
                        edits.putAll(beforePair);
                    }
                }
            }
        }
    }

    private static List<int[]> genericStructureCandidates(
        int radius, boolean relic, Random random, NavigationScanner navigation
    ) {
        List<int[]> candidates = new ArrayList<>();
        int min = 10;
        int max = Math.max(min, radius - 10);

        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                if (relic || !navigation.isNearMainPath(x, z, 4) && !navigation.isNearMainPath(-x, -z, 4)) {
                    candidates.add(new int[]{x, z, random.nextBoolean() ? 1 : 0});
                }
            }
        }

        for (int i = candidates.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] value = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, value);
        }

        return candidates;
    }

    private static void buildGenericStructure(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int x,
        int z,
        int template,
        Material wall
    ) {
        switch (template) {
            case 0:
                buildGenericCabin(world, arena, terrain, edits, x, z, wall);
                break;
            case 1:
                buildGenericStoneHouse(world, arena, terrain, edits, x, z, wall);
                break;
            case 2:
                buildGenericTower(world, arena, terrain, edits, x, z, wall);
                break;
            default:
                buildGenericRuin(world, arena, terrain, edits, x, z, wall);
        }
    }

    private static boolean isGenericHouseAccessible(
        World world, ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int cx, int cz
    ) {
        int doorY = terrain.heightAt(cx, cz + 3) - arena.centerY + 1;
        BlockEdit lowerDoor = edits.get(new BlockPos(cx, doorY, cz + 3));
        BlockEdit upperDoor = edits.get(new BlockPos(cx, doorY + 1, cz + 3));
        return lowerDoor != null
            && upperDoor != null
            && lowerDoor.material.name().endsWith("_DOOR")
            && upperDoor.material.name().endsWith("_DOOR")
            && hasTwoBlockClearance(world, arena, terrain, edits, cx, cz + 2)
            && hasTwoBlockClearance(world, arena, terrain, edits, cx, cz + 4);
    }

    private static boolean hasTwoBlockClearance(
        World world, ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int x, int z
    ) {
        int surfaceY = terrain.heightAt(x, z);

        for (int dy = 1; dy <= 2; dy++) {
            int relativeY = surfaceY + dy - arena.centerY;
            BlockEdit planned = edits.get(new BlockPos(x, relativeY, z));
            if (planned != null) {
                if (!planned.material.isAir()
                    && planned.material.isSolid()
                    && !planned.material.name().endsWith("_DOOR")) {
                    return false;
                }
            } else if (!world.getBlockAt(arena.centerX + x, surfaceY + dy, arena.centerZ + z).isPassable()) {
                return false;
            }
        }

        return true;
    }

    private static boolean isGenericRegionSafe(ArenaData arena, int x, int z, boolean relic, int teamCount) {
        int absX = Math.abs(x);
        int absZ = Math.abs(z);
        if (absX > 8 && absZ > 8) {
            for (int[] spawn : SeededTerrainBlender.anchors(arena.spawnOffset, teamCount)) {
                int dx = Math.max(0, Math.abs(x - spawn[0]) - 7);
                int dz = Math.max(0, Math.abs(z - spawn[1]) - 7);
                if (dx + dz < 8) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private static boolean isGenericSiteClear(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int cx,
        int cz,
        int template
    ) {
        int half = template == 2 ? 2 : 3;
        int height = template == 2 ? 5 : (template == 3 ? 3 : 7);
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                if (Math.abs(x) > terrain.radius() - 3 || Math.abs(z) > terrain.radius() - 3) {
                    return false;
                }

                int surface = terrain.heightAt(x, z);
                minY = Math.min(minY, surface);
                maxY = Math.max(maxY, surface);

                for (int dy = 1; dy <= height; dy++) {
                    int worldY = surface + dy;
                    if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) {
                        return false;
                    }

                    BlockPos pos = new BlockPos(x, worldY - arena.centerY, z);
                    BlockEdit planned = edits.get(pos);
                    if (planned != null) {
                        if (!planned.material.isAir() && planned.material.isSolid()) {
                            return false;
                        }
                    } else {
                        Block block = world.getBlockAt(arena.centerX + x, worldY, arena.centerZ + z);
                        if (block.getState() instanceof TileState || !block.isPassable()) {
                            return false;
                        }
                    }
                }
            }
        }

        return maxY - minY <= 1;
    }

    private static void buildGenericCabin(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int cx,
        int cz,
        Material wall
    ) {
        buildGenericHouseShell(
            world,
            arena,
            terrain,
            edits,
            cx,
            cz,
            Material.SPRUCE_PLANKS,
            Material.COBBLESTONE,
            Material.SPRUCE_DOOR,
            Material.SPRUCE_STAIRS,
            Material.SPRUCE_SLAB,
            wall
        );
        addGenericInterior(world, arena, terrain, edits, cx, cz, Material.SPRUCE_PLANKS);
    }

    private static void buildGenericStoneHouse(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int cx,
        int cz,
        Material wall
    ) {
        buildGenericHouseShell(
            world,
            arena,
            terrain,
            edits,
            cx,
            cz,
            Material.STONE_BRICKS,
            Material.MOSSY_STONE_BRICKS,
            Material.OAK_DOOR,
            Material.STONE_BRICK_STAIRS,
            Material.STONE_BRICK_SLAB,
            wall
        );
        addGenericInterior(world, arena, terrain, edits, cx, cz, Material.OAK_PLANKS);

        for (int z : new int[]{-2, 2}) {
            int y = terrain.heightAt(cx, cz + z) - arena.centerY + 3;
            addAir(world, arena, edits, cx, y, cz + z, Material.STRIPPED_OAK_LOG);
        }
    }

    private static void buildGenericHouseShell(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int cx,
        int cz,
        Material floor,
        Material foundation,
        Material doorMaterial,
        Material roofStair,
        Material roofSlab,
        Material wall
    ) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                addSurface(
                    world,
                    arena,
                    terrain,
                    edits,
                    cx + x,
                    cz + z,
                    Math.abs(x) != 3 && Math.abs(z) != 3 ? floor : foundation
                );
            }
        }

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 3) {
                    int base = terrain.heightAt(cx + x, cz + z) - arena.centerY;

                    for (int y = 1; y <= 3; y++) {
                        boolean doorway = z == 3 && x == 0 && y <= 2;
                        boolean sideWindow = Math.abs(x) == 3 && z == 0 && y == 2;
                        boolean rearWindow = z == -3 && x == 0 && y == 2;
                        if (!doorway && !sideWindow && !rearWindow) {
                            addAir(world, arena, edits, cx + x, base + y, cz + z, wall);
                        }
                    }

                    if (Math.abs(x) == 3 && z == 0) {
                        addGenericBlockData(
                            world,
                            arena,
                            edits,
                            cx + x,
                            base + 2,
                            cz + z,
                            Bukkit.createBlockData("minecraft:glass_pane")
                        );
                    }

                    if (z == -3 && x == 0) {
                        addGenericBlockData(
                            world,
                            arena,
                            edits,
                            cx + x,
                            base + 2,
                            cz + z,
                            Bukkit.createBlockData("minecraft:glass_pane")
                        );
                    }
                }
            }
        }

        int doorY = terrain.heightAt(cx, cz + 3) - arena.centerY + 1;
        addGenericBlockData(
            world,
            arena,
            edits,
            cx,
            doorY,
            cz + 3,
            Bukkit.createBlockData(
                "minecraft:"
                    + doorMaterial.name().toLowerCase(Locale.ROOT)
                    + "[facing=south,half=lower,hinge=left,open=false,powered=false]"
            )
        );
        addGenericBlockData(
            world,
            arena,
            edits,
            cx,
            doorY + 1,
            cz + 3,
            Bukkit.createBlockData(
                "minecraft:"
                    + doorMaterial.name().toLowerCase(Locale.ROOT)
                    + "[facing=south,half=upper,hinge=left,open=false,powered=false]"
            )
        );

        for (int z = -3; z <= 3; z++) {
            for (int x = -3; x <= 3; x++) {
                int base = terrain.heightAt(cx + x, cz + z) - arena.centerY;
                if (x == 0) {
                    addAir(world, arena, edits, cx + x, base + 6, cz + z, roofSlab);
                } else {
                    String facing = x < 0 ? "west" : "east";
                    int roofY = base + 4 + (3 - Math.abs(x));
                    addGenericBlockData(
                        world,
                        arena,
                        edits,
                        cx + x,
                        roofY,
                        cz + z,
                        Bukkit.createBlockData(
                            "minecraft:"
                                + roofStair.name().toLowerCase(Locale.ROOT)
                                + "[facing="
                                + facing
                                + ",half=bottom,shape=straight,waterlogged=false]"
                        )
                    );
                }
            }
        }
    }

    private static void addGenericInterior(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int cx,
        int cz,
        Material floor
    ) {
        int base = terrain.heightAt(cx, cz) - arena.centerY;
        addAir(world, arena, edits, cx - 2, base + 1, cz - 2, Material.BOOKSHELF);
        addAir(world, arena, edits, cx + 2, base + 1, cz - 2, Material.CRAFTING_TABLE);
        addAir(world, arena, edits, cx - 2, base + 1, cz + 1, Material.BOOKSHELF);
        addAir(world, arena, edits, cx + 2, base + 1, cz + 1, Material.COBBLESTONE_WALL);
        addAir(world, arena, edits, cx, base + 1, cz, Material.OAK_FENCE);
        addAir(world, arena, edits, cx, base + 2, cz, Material.LANTERN);
        addGenericBlockData(
            world,
            arena,
            edits,
            cx - 1,
            base + 1,
            cz,
            Bukkit.createBlockData("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]")
        );
        addGenericBlockData(
            world,
            arena,
            edits,
            cx + 1,
            base + 1,
            cz,
            Bukkit.createBlockData("minecraft:oak_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]")
        );
        addAir(world, arena, edits, cx, base + 1, cz + 2, Material.WHITE_CARPET);
    }

    private static void buildGenericTower(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int cx,
        int cz,
        Material wall
    ) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    int base = terrain.heightAt(cx + x, cz + z) - arena.centerY;

                    for (int y = 1; y <= 4; y++) {
                        addAir(world, arena, edits, cx + x, base + y, cz + z, y == 4 ? Material.STONE_BRICK_WALL : wall);
                    }
                }
            }
        }

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                int y = terrain.heightAt(cx + x, cz + z) - arena.centerY + 4;
                addAir(world, arena, edits, cx + x, y, cz + z, Material.SPRUCE_SLAB);
            }
        }
    }

    private static void buildGenericRuin(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int cx,
        int cz,
        Material wall
    ) {
        for (int offset = -3; offset <= 3; offset++) {
            if (offset != 0) {
                addGenericRuinColumn(
                    world, arena, terrain, edits, cx + offset, cz - 3, wall, 1 + Math.floorMod(offset, 3)
                );
                addGenericRuinColumn(
                    world, arena, terrain, edits, cx + offset, cz + 3, wall, 1 + Math.floorMod(offset + 1, 3)
                );
            }
        }

        addGenericRuinColumn(world, arena, terrain, edits, cx - 3, cz, wall, 2);
        addGenericRuinColumn(world, arena, terrain, edits, cx + 3, cz, wall, 2);
    }

    private static void addGenericRuinColumn(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int x,
        int z,
        Material material,
        int height
    ) {
        int base = terrain.heightAt(x, z) - arena.centerY;

        for (int y = 1; y <= height; y++) {
            addAir(world, arena, edits, x, base + y, z, y == height ? Material.CHISELED_STONE_BRICKS : material);
        }
    }

    private static void addGenericBlockData(
        World world, ArenaData arena, Map<BlockPos, BlockEdit> edits, int x, int y, int z, BlockData blockData
    ) {
        Block block = world.getBlockAt(arena.centerX + x, arena.centerY + y, arena.centerZ + z);
        if (!(block.getState() instanceof TileState)) {
            BlockEdit planned = edits.get(new BlockPos(x, y, z));
            if ((
                    planned == null
                        || planned.material.isAir()
                        || !planned.material.isSolid()
                        || planned.material == blockData.getMaterial()
                )
                && (planned != null || block.isPassable())) {
                edits.put(new BlockPos(x, y, z), new BlockEdit(x, y, z, blockData));
            }
        }
    }

    private static void buildPerimeter(
        World world, ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int radius
    ) {
        for (int i = -radius; i <= radius; i++) {
            addPerimeterMarker(world, arena, terrain, edits, -radius, i);
            addPerimeterMarker(world, arena, terrain, edits, radius, i);
            addPerimeterMarker(world, arena, terrain, edits, i, -radius);
            addPerimeterMarker(world, arena, terrain, edits, i, radius);
        }
    }

    private static void addPerimeterMarker(
        World world, ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int x, int z
    ) {
        int y = terrain.heightAt(x, z) + 1 - arena.centerY;
        addAir(world, arena, edits, x, y, z, Material.STONE_BRICK_WALL);
    }

    private static void addSurface(
        World world,
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int x,
        int z,
        Material material
    ) {
        int surfaceY = terrain.heightAt(x, z);
        Block surface = world.getBlockAt(arena.centerX + x, surfaceY, arena.centerZ + z);
        Block above = world.getBlockAt(arena.centerX + x, surfaceY + 1, arena.centerZ + z);
        BlockPos surfacePos = new BlockPos(x, surfaceY - arena.centerY, z);
        BlockPos abovePos = new BlockPos(x, surfaceY + 1 - arena.centerY, z);
        BlockEdit plannedSurface = edits.get(surfacePos);
        BlockEdit plannedAbove = edits.get(abovePos);
        boolean surfaceSolid = plannedSurface != null
            ? plannedSurface.material.isSolid() || isLiquid(plannedSurface.material)
            : surface.getType().isSolid() || surface.isLiquid();
        boolean abovePassable = plannedAbove != null
            ? plannedAbove.material.isAir() || !plannedAbove.material.isSolid()
            : above.isPassable();
        if (!(surface.getState() instanceof TileState) && surfaceSolid && abovePassable) {
            add(edits, x, surfaceY - arena.centerY, z, material);
        }
    }

    private static void addAir(
        World world, ArenaData arena, Map<BlockPos, BlockEdit> edits, int x, int y, int z, Material material
    ) {
        Block block = world.getBlockAt(arena.centerX + x, arena.centerY + y, arena.centerZ + z);
        if (!(block.getState() instanceof TileState)) {
            BlockEdit planned = edits.get(new BlockPos(x, y, z));
            boolean plannedAir = planned != null && (planned.material.isAir() || !planned.material.isSolid());
            if (plannedAir || block.isPassable()) {
                add(edits, x, y, z, material);
            }
        }
    }

    private static void add(Map<BlockPos, BlockEdit> edits, int x, int y, int z, Material material) {
        BlockPos position = new BlockPos(x, y, z);
        edits.put(position, new BlockEdit(x, y, z, material));
    }

    private static boolean isLiquid(Material material) {
        return material == Material.WATER || material == Material.LAVA;
    }

    static boolean isCombatVegetation(Material material) {
        String name = material.name();

        return switch (name) {
            case "GRASS", "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH", "VINE", "SWEET_BERRY_BUSH", "HANGING_ROOTS", "DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET", "OXEYE_DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY", "WITHER_ROSE", "TORCHFLOWER", "PINK_PETALS", "SUNFLOWER", "LILAC", "ROSE_BUSH", "PEONY", "BROWN_MUSHROOM", "RED_MUSHROOM", "CRIMSON_FUNGUS", "WARPED_FUNGUS", "NETHER_SPROUTS", "CRIMSON_ROOTS", "WARPED_ROOTS", "TWISTING_VINES", "TWISTING_VINES_PLANT", "WEEPING_VINES", "WEEPING_VINES_PLANT", "CAVE_VINES", "CAVE_VINES_PLANT", "GLOW_LICHEN", "SMALL_DRIPLEAF", "BIG_DRIPLEAF", "SEAGRASS", "TALL_SEAGRASS", "KELP", "KELP_PLANT", "LILY_PAD" -> true;
            default -> name.endsWith("_TULIP")
                || name.endsWith("_SAPLING")
                || name.equals("AZALEA")
                || name.equals("FLOWERING_AZALEA")
                || name.equals("SPORE_BLOSSOM");
        };
    }
}
