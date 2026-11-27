package id.hadziq.arenasystem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mode-aware hybrid arena generator.
 * Every surface placement follows the scanned terrain height so the generator
 * adds structure without requiring a flat 81x81 plot.
 */
final class ArenaGenerator {
    private ArenaGenerator() {
    }

    static List<BlockEdit> createPlan(World world, ArenaData arena, TerrainProfile originalTerrain,
                                      TerrainProfile terrain, long seed, int radius, int teamCount) {
        Map<BlockPos, BlockEdit> edits = new LinkedHashMap<>();

        // Apply the normalized heightmap as real cut/fill edits before adding mode structures.
        // All edits are captured by SnapshotStore before the world is modified.
        normalizeSurface(world, arena, originalTerrain, terrain, edits, radius);
        // Vegetation cleanup is part of the same snapshot-backed generation plan.
        // This prevents tall grass from surviving on columns whose terrain height
        // did not need cut/fill normalization.
        queueCombatVegetationCleanup(world, arena, terrain, edits, radius);

        if ("relic_rush".equalsIgnoreCase(arena.mode)) {
            buildRelicRush(world, arena, terrain, edits, seed, radius);
        } else {
            buildTeamDeathmatch(world, arena, terrain, edits, seed, radius, teamCount);
        }
        buildGenericStructures(world, arena, terrain, edits, seed, radius, teamCount);
        buildPerimeter(world, arena, terrain, edits, radius);
        return new ArrayList<>(edits.values());
    }

    /** Queue low vegetation above the normalized surface as AIR edits. Existing
     * terrain edits take priority so cut/fill blocks are never accidentally erased.
     */
    private static void queueCombatVegetationCleanup(World world, ArenaData arena,
                                                      TerrainProfile terrain,
                                                      Map<BlockPos, BlockEdit> edits,
                                                      int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int surfaceY = terrain.heightAt(x, z);
                int maxY = Math.min(world.getMaxHeight() - 1, surfaceY + 12);
                for (int y = surfaceY + 1; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y - arena.centerY, z);
                    if (edits.containsKey(pos)) continue;
                    Block block = world.getBlockAt(arena.centerX + x, y, arena.centerZ + z);
                    if (isCombatVegetation(block.getType())
                            && !(block.getState() instanceof TileState)) {
                        add(edits, x, y - arena.centerY, z, Material.AIR);
                    }
                }
            }
        }
    }

    /**
     * Materialize the normalized profile as real world blocks. Every change becomes a
     * BlockEdit and is therefore included in the same original-terrain snapshot as the
     * arena structures. Block entities are rejected before the world is touched because
     * this plugin's BlockData snapshot does not preserve inventories or tile data.
     */
    private static void normalizeSurface(World world, ArenaData arena, TerrainProfile originalTerrain,
                                         TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int originalY = originalTerrain.heightAt(x, z);
                int targetY = terrain.heightAt(x, z);
                if (originalY == targetY) continue;

                int lowY = Math.min(originalY, targetY);
                int highY = Math.max(originalY, targetY);
                if (lowY < world.getMinHeight() || highY >= world.getMaxHeight()) {
                    throw new IllegalStateException("normalisasi melewati batas tinggi world pada kolom "
                            + (arena.centerX + x) + "," + (arena.centerZ + z));
                }
                for (int y = lowY; y <= highY; y++) {
                    Block block = world.getBlockAt(arena.centerX + x, y, arena.centerZ + z);
                    if (block.getState() instanceof TileState) {
                        throw new IllegalStateException("normalisasi akan menyentuh block entity di "
                                + (arena.centerX + x) + "," + y + "," + (arena.centerZ + z));
                    }
                }

                if (targetY > originalY) {
                    // Fill lower columns and cap the new surface with grass.
                    for (int y = originalY; y < targetY; y++) {
                        add(edits, x, y - arena.centerY, z, Material.DIRT);
                    }
                    add(edits, x, targetY - arena.centerY, z, Material.GRASS_BLOCK);
                } else {
                    // Cut high columns and expose a grass surface at the target level.
                    for (int y = targetY + 1; y <= originalY; y++) {
                        add(edits, x, y - arena.centerY, z, Material.AIR);
                    }
                    add(edits, x, targetY - arena.centerY, z, Material.GRASS_BLOCK);
                }
            }
        }
    }

    /** TDM: symmetric, relatively open battlefield with flank cover and a contested centre. */
    private static void buildTeamDeathmatch(World world, ArenaData arena, TerrainProfile terrain,
                                            Map<BlockPos, BlockEdit> edits, long seed, int radius,
                                            int teamCount) {
        Random pathRandom = SeededArenaLayout.tdmPathRandom(seed);

        // Main combat lane and a cross-lane for rotations. The seed chooses a
        // reproducible paving pattern without affecting the independent cover layout.
        for (int x = -27; x <= 27; x++) {
            for (int z = -2; z <= 2; z++) {
                Material path = pathRandom.nextInt(10) < 4 ? Material.GRAVEL
                        : pathRandom.nextBoolean() ? Material.COBBLESTONE : Material.COARSE_DIRT;
                addSurface(world, arena, terrain, edits, x, z, path);
            }
        }
        for (int z = -19; z <= 19; z++) {
            for (int x = -2; x <= 2; x++) {
                addSurface(world, arena, terrain, edits, x, z,
                        Math.abs(x) == 2 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
            }
        }

        // Spawn pads are generated from the arena's configured team count.
        // Regeneration restores the previous snapshot first, so inactive team pads
        // from an older 3/4-team layout return to their original terrain blocks.
        buildTdmSpawnPad(world, arena, terrain, edits, -27, 0, Material.RED_TERRACOTTA);
        buildTdmSpawnPad(world, arena, terrain, edits, 27, 0, Material.BLUE_TERRACOTTA);
        if (teamCount >= 3) {
            buildTdmSpawnPad(world, arena, terrain, edits, 0, -27, Material.GREEN_TERRACOTTA);
        }
        if (teamCount >= 4) {
            buildTdmSpawnPad(world, arena, terrain, edits, 0, 27, Material.YELLOW_TERRACOTTA);
        }
        buildTdmCentre(world, arena, terrain, edits);

        // Four ruined cover points, mirrored to keep both teams' routes comparable.
        buildRuin(world, arena, terrain, edits, -15, -15, Material.MOSSY_STONE_BRICKS);
        buildRuin(world, arena, terrain, edits, -15, 15, Material.MOSSY_STONE_BRICKS);
        buildRuin(world, arena, terrain, edits, 15, -15, Material.MOSSY_STONE_BRICKS);
        buildRuin(world, arena, terrain, edits, 15, 15, Material.MOSSY_STONE_BRICKS);

        // Seed chooses six unique cover anchors, their orientation and material.
        // Mirroring across both axes keeps all two-, three-, and four-team setups balanced.
        Material[] coverPalette = {
                Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
                Material.STONE_BRICKS, Material.ANDESITE
        };
        for (SeededArenaLayout.CoverSite site : SeededArenaLayout.teamDeathmatch(seed)) {
            Material coverMaterial = coverPalette[site.paletteIndex()];
            for (int signX : new int[]{-1, 1}) {
                for (int signZ : new int[]{-1, 1}) {
                    buildLowCover(world, arena, terrain, edits,
                            signX * site.x(), signZ * site.z(), coverMaterial, site.alongX());
                }
            }
        }
    }

    /** Relic Rush: expanded three-lane battlefield with mirrored sanctums and multiple cover zones. */
    private static void buildRelicRush(World world, ArenaData arena, TerrainProfile terrain,
                                       Map<BlockPos, BlockEdit> edits, long seed, int radius) {
        // Semua jarak mempertahankan rasio desain radius 40, sementara sanctum
        // mengikuti spawnOffset = radius - 8 agar posisi spawn dan bangunan selalu sinkron.
        double scale = radius / 40.0D;
        int laneEnd = radius - 14;
        int sideLaneOffset = (int) Math.round(18 * scale);
        int crossLaneOuter = (int) Math.round(29 * scale);
        int crossLaneInner = (int) Math.round(16 * scale);
        int sanctumOffset = radius - 8;

        // Jalur utama dan dua jalur samping diperlebar secara proporsional.
        int laneHalfWidth = Math.max(2, (int) Math.round(2 * scale));
        for (int x = -laneEnd; x <= laneEnd; x++) {
            for (int z = -laneHalfWidth; z <= laneHalfWidth; z++) {
                addSurface(world, arena, terrain, edits, x, z,
                        Math.abs(z) == laneHalfWidth ? Material.CUT_SANDSTONE : Material.SMOOTH_SANDSTONE);
            }
            for (int side : new int[]{-sideLaneOffset, sideLaneOffset}) {
                for (int dz = -laneHalfWidth; dz <= laneHalfWidth; dz++) {
                    addSurface(world, arena, terrain, edits, x, side + dz,
                            Math.abs(dz) == laneHalfWidth ? Material.SANDSTONE : Material.CUT_SANDSTONE);
                }
            }
        }

        // Cross-lane tetap memiliki rasio posisi yang sama dengan layout radius 40.
        int[] crossLanes = {-crossLaneOuter, -crossLaneInner, 0, crossLaneInner, crossLaneOuter};
        for (int x : crossLanes) {
            for (int z = -sideLaneOffset; z <= sideLaneOffset; z++) {
                for (int dx = -1; dx <= 1; dx++) {
                    addSurface(world, arena, terrain, edits, x + dx, z,
                            Math.abs(dx) == 1 ? Material.CUT_SANDSTONE : Material.SMOOTH_SANDSTONE);
                }
            }
        }

        buildRelicSanctum(world, arena, terrain, edits, -sanctumOffset, Material.RED_TERRACOTTA, true);
        buildRelicSanctum(world, arena, terrain, edits, sanctumOffset, Material.BLUE_TERRACOTTA, false);
        buildRelicShrine(world, arena, terrain, edits);

        // Delapan outpost utama, seluruh koordinat diskalakan terhadap radius 40.
        int nearX = (int) Math.round(19 * scale);
        int nearZ = (int) Math.round(9 * scale);
        int farX = (int) Math.round(9 * scale);
        int farZ = (int) Math.round(28 * scale);
        Random outpostLayout = SeededArenaLayout.relicOutpostRandom(seed);
        buildRelicOutpostPair(world, arena, terrain, edits, nearX, -nearZ,
                Material.MOSSY_STONE_BRICKS, outpostLayout.nextBoolean());
        buildRelicOutpostPair(world, arena, terrain, edits, nearX, nearZ,
                Material.MOSSY_STONE_BRICKS, outpostLayout.nextBoolean());
        buildRelicOutpostPair(world, arena, terrain, edits, farX, -farZ,
                Material.CHISELED_SANDSTONE, outpostLayout.nextBoolean());
        buildRelicOutpostPair(world, arena, terrain, edits, farX, farZ,
                Material.CHISELED_SANDSTONE, outpostLayout.nextBoolean());

        // Ring outpost tambahan di mid-field: delapan titik staggered di antara jalur,
        // memberi rotasi dan cover tanpa menutup koridor utama.
        int ringInnerX = (int) Math.round(24 * scale);
        int ringInnerZ = (int) Math.round(28 * scale);
        int ringOuterX = (int) Math.round(36 * scale);
        int ringOuterZ = (int) Math.round(18 * scale);
        int ringOuter = (int) Math.round(25 * scale);
        buildRelicOutpostPair(world, arena, terrain, edits, ringInnerX, -ringInnerZ,
                Material.MOSSY_STONE_BRICKS, outpostLayout.nextBoolean());
        buildRelicOutpostPair(world, arena, terrain, edits, ringInnerX, ringInnerZ,
                Material.MOSSY_STONE_BRICKS, outpostLayout.nextBoolean());
        buildRelicOutpostPair(world, arena, terrain, edits, ringOuterX, -ringOuterZ,
                Material.CHISELED_SANDSTONE, outpostLayout.nextBoolean());
        buildRelicOutpostPair(world, arena, terrain, edits, ringOuterX, ringOuterZ,
                Material.CHISELED_SANDSTONE, outpostLayout.nextBoolean());

        // Low-cover baru di sepanjang lane utama, dipisahkan agar tidak menjadi tembok.
        int[] coverXs = {
                (int) Math.round(-27 * scale), (int) Math.round(-13 * scale),
                (int) Math.round(13 * scale), (int) Math.round(27 * scale)
        };
        for (int x : coverXs) {
            buildLowCover(world, arena, terrain, edits, x, 0, Material.COBBLESTONE);
            buildLowCover(world, arena, terrain, edits, x, -sideLaneOffset, Material.MOSSY_STONE_BRICKS);
            buildLowCover(world, arena, terrain, edits, x, sideLaneOffset, Material.MOSSY_STONE_BRICKS);
        }

        // Cover pendek pada pendekatan luar dan reruntuhan sebagai landmark.
        for (int x : new int[]{-crossLaneOuter, crossLaneOuter}) {
            for (int z : new int[]{-ringOuterZ, ringOuterZ, -ringOuter, ringOuter}) {
                buildLowCover(world, arena, terrain, edits, x, z, Material.COBBLESTONE);
            }
        }
        buildRuin(world, arena, terrain, edits, -crossLaneOuter, -sideLaneOffset + 2, Material.CUT_SANDSTONE);
        buildRuin(world, arena, terrain, edits, -crossLaneOuter, sideLaneOffset - 2, Material.CUT_SANDSTONE);
        buildRuin(world, arena, terrain, edits, crossLaneOuter, -sideLaneOffset + 2, Material.CUT_SANDSTONE);
        buildRuin(world, arena, terrain, edits, crossLaneOuter, sideLaneOffset - 2, Material.CUT_SANDSTONE);

        // Two seed-selected diagonal midfield sites are copied into all four
        // quadrants, keeping both teams and both side routes structurally even.
        Material[] seededCoverPalette = {
                Material.MOSSY_STONE_BRICKS, Material.CHISELED_SANDSTONE,
                Material.CUT_SANDSTONE, Material.STONE_BRICKS
        };
        for (SeededArenaLayout.CoverSite site : SeededArenaLayout.relicRush(seed)) {
            Material material = seededCoverPalette[site.paletteIndex()];
            for (int signX : new int[]{-1, 1}) {
                for (int signZ : new int[]{-1, 1}) {
                    buildLowCover(world, arena, terrain, edits,
                            signX * site.x(), signZ * site.z(), material, site.alongX());
                }
            }
        }
    }

    private static void buildRelicOutpostPair(World world, ArenaData arena, TerrainProfile terrain,
                                              Map<BlockPos, BlockEdit> edits, int absX, int z,
                                              Material wallMaterial, boolean coverAlongX) {
        buildRelicOutpost(world, arena, terrain, edits, -absX, z, wallMaterial, coverAlongX);
        buildRelicOutpost(world, arena, terrain, edits, absX, z, wallMaterial, coverAlongX);
    }

    private static void buildTdmSpawnPad(World world, ArenaData arena, TerrainProfile terrain,
                                         Map<BlockPos, BlockEdit> edits, int centerX, int centerZ,
                                         Material teamMaterial) {
        for (int x = centerX - 4; x <= centerX + 4; x++) {
            for (int z = centerZ - 4; z <= centerZ + 4; z++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                int distance = Math.max(Math.abs(dx), Math.abs(dz));
                Material material = distance == 4 ? Material.WHITE_CONCRETE : teamMaterial;
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) material = Material.SMOOTH_STONE;
                addSurface(world, arena, terrain, edits, x, z, material);
            }
        }
        int spawnY = terrain.heightAt(centerX, centerZ);
        addAir(world, arena, edits, centerX, spawnY + 1 - arena.centerY, centerZ, Material.SEA_LANTERN);
    }

    private static void buildTdmCentre(World world, ArenaData arena, TerrainProfile terrain,
                                       Map<BlockPos, BlockEdit> edits) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                int distance = Math.max(Math.abs(x), Math.abs(z));
                Material material = distance == 4 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS;
                if (distance <= 1) material = Material.SMOOTH_STONE;
                addSurface(world, arena, terrain, edits, x, z, material);
            }
        }
        int y = terrain.heightAt(0, 0) - arena.centerY;
        addAir(world, arena, edits, 0, y + 1, 0, Material.GOLD_BLOCK);
        addAir(world, arena, edits, 0, y + 2, 0, Material.SEA_LANTERN);
        for (int[] corner : new int[][]{{-4, -4}, {-4, 4}, {4, -4}, {4, 4}}) {
            for (int h = 1; h <= 3; h++) {
                addAir(world, arena, edits, corner[0], y + h, corner[1],
                        h == 3 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
            }
        }
    }

    private static void buildRelicSanctum(World world, ArenaData arena, TerrainProfile terrain,
                                          Map<BlockPos, BlockEdit> edits, int centerX,
                                          Material teamMaterial, boolean red) {
        // The capture zone is a clearly marked 13x13 team-coloured sanctuary.
        for (int dx = -6; dx <= 6; dx++) {
            for (int z = -6; z <= 6; z++) {
                int distance = Math.max(Math.abs(dx), Math.abs(z));
                Material floor = distance == 6 ? Material.WHITE_CONCRETE : teamMaterial;
                if (Math.abs(dx) <= 1 && Math.abs(z) <= 1) floor = Material.SMOOTH_STONE;
                if (distance == 5 && (dx == 0 || z == 0)) floor = Material.CHISELED_SANDSTONE;
                addSurface(world, arena, terrain, edits, centerX + dx, z, floor);
            }
        }

        // Corner pillars and a front-facing gate make the delivery base recognizable from the lanes.
        for (int dx : new int[]{-6, 6}) {
            for (int z : new int[]{-6, 6}) {
                buildTerrainPillar(world, arena, terrain, edits, centerX + dx, z, 4,
                        teamMaterial, Material.SMOOTH_SANDSTONE);
            }
        }
        int inward = red ? 1 : -1;
        for (int z : new int[]{-3, 3}) {
            buildTerrainPillar(world, arena, terrain, edits, centerX + inward * 6, z, 4,
                    teamMaterial, Material.SMOOTH_SANDSTONE);
        }
        int gateX = centerX + inward * 6;
        int gateY = terrain.heightAt(gateX, 0) - arena.centerY;
        for (int z = -2; z <= 2; z++) {
            addAir(world, arena, edits, gateX, gateY + 4, z, Material.CHISELED_SANDSTONE);
        }

        int spawnY = terrain.heightAt(centerX, 0) - arena.centerY;
        addAir(world, arena, edits, centerX, spawnY + 1, 0,
                red ? Material.REDSTONE_LAMP : Material.SEA_LANTERN);
    }

    private static void buildRelicShrine(World world, ArenaData arena, TerrainProfile terrain,
                                         Map<BlockPos, BlockEdit> edits) {
        // Sandstone ring and blackstone inlay distinguish the objective from the TDM centre marker.
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                int distance = Math.max(Math.abs(x), Math.abs(z));
                if (distance > 6) continue;
                Material floor = distance == 6 ? Material.CHISELED_SANDSTONE
                        : distance >= 4 ? Material.CUT_SANDSTONE : Material.SMOOTH_SANDSTONE;
                if (Math.abs(x) == 2 || Math.abs(z) == 2) floor = Material.POLISHED_BLACKSTONE_BRICKS;
                addSurface(world, arena, terrain, edits, x, z, floor);
            }
        }

        int groundY = terrain.heightAt(0, 0) - arena.centerY;
        addAir(world, arena, edits, 0, groundY + 1, 0, Material.POLISHED_BLACKSTONE_BRICKS);
        addAir(world, arena, edits, 0, groundY + 2, 0, Material.CHISELED_SANDSTONE);
        addAir(world, arena, edits, 0, groundY + 3, 0, Material.GOLD_BLOCK);
        addAir(world, arena, edits, 0, groundY + 4, 0, Material.SEA_LANTERN);

        for (int[] point : new int[][]{{-5, -5}, {-5, 5}, {5, -5}, {5, 5}}) {
            buildTerrainPillar(world, arena, terrain, edits, point[0], point[1], 3,
                    Material.PURPUR_BLOCK, Material.AMETHYST_BLOCK);
        }
    }

    private static void buildRelicOutpost(World world, ArenaData arena, TerrainProfile terrain,
                                          Map<BlockPos, BlockEdit> edits, int centerX, int centerZ,
                                          Material wallMaterial, boolean coverAlongX) {
        // Broken 7x7 walls with four open entrances; corner columns provide taller visual cover.
        for (int offset = -2; offset <= 2; offset++) {
            if (offset != 0) {
                placeRelicWallColumn(world, arena, terrain, edits, centerX + offset, centerZ - 3,
                        wallMaterial, 2);
                placeRelicWallColumn(world, arena, terrain, edits, centerX + offset, centerZ + 3,
                        wallMaterial, 2);
                placeRelicWallColumn(world, arena, terrain, edits, centerX - 3, centerZ + offset,
                        wallMaterial, 2);
                placeRelicWallColumn(world, arena, terrain, edits, centerX + 3, centerZ + offset,
                        wallMaterial, 2);
            }
        }
        for (int dx : new int[]{-3, 3}) {
            for (int dz : new int[]{-3, 3}) {
                placeRelicWallColumn(world, arena, terrain, edits, centerX + dx, centerZ + dz,
                        Material.CHISELED_STONE_BRICKS, 3);
            }
        }
        // Interior knee-high barriers create a second layer of cover inside the outpost.
        buildLowCover(world, arena, terrain, edits, centerX, centerZ, wallMaterial, coverAlongX);
    }

    private static void placeRelicWallColumn(World world, ArenaData arena, TerrainProfile terrain,
                                             Map<BlockPos, BlockEdit> edits, int x, int z,
                                             Material material, int height) {
        int baseY = terrain.heightAt(x, z) - arena.centerY;
        for (int y = 1; y <= height; y++) {
            addAir(world, arena, edits, x, baseY + y, z,
                    y == height ? Material.STONE_BRICK_WALL : material);
        }
    }

    private static void buildTerrainPillar(World world, ArenaData arena, TerrainProfile terrain,
                                           Map<BlockPos, BlockEdit> edits, int x, int z, int height,
                                           Material body, Material cap) {
        int baseY = terrain.heightAt(x, z) - arena.centerY;
        for (int h = 1; h <= height; h++) {
            addAir(world, arena, edits, x, baseY + h, z, h == height ? cap : body);
        }
    }

    private static void buildLowCover(World world, ArenaData arena, TerrainProfile terrain,
                                      Map<BlockPos, BlockEdit> edits, int centerX, int centerZ,
                                      Material material) {
        buildLowCover(world, arena, terrain, edits, centerX, centerZ, material, true);
    }

    private static void buildLowCover(World world, ArenaData arena, TerrainProfile terrain,
                                      Map<BlockPos, BlockEdit> edits, int centerX, int centerZ,
                                      Material material, boolean alongX) {
        // Short, traversable cover: three blocks wide and two blocks high.
        for (int offset = -1; offset <= 1; offset++) {
            int x = alongX ? centerX + offset : centerX;
            int z = alongX ? centerZ : centerZ + offset;
            int y = terrain.heightAt(x, z) - arena.centerY;
            addAir(world, arena, edits, x, y + 1, z, material);
            if (offset == 0) addAir(world, arena, edits, x, y + 2, z, Material.STONE_BRICK_WALL);
        }
    }

    private static void buildRuin(World world, ArenaData arena, TerrainProfile terrain,
                                  Map<BlockPos, BlockEdit> edits, int centerX, int centerZ, Material material) {
        int[][] corners = {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}};
        for (int[] corner : corners) {
            int x = centerX + corner[0];
            int z = centerZ + corner[1];
            int baseY = terrain.heightAt(x, z) - arena.centerY;
            int height = 2 + Math.floorMod(centerX * 7 + centerZ * 3 + corner[0] - corner[1], 3);
            for (int y = 1; y <= height; y++) {
                addAir(world, arena, edits, x, baseY + y, z,
                        y == height ? Material.CHISELED_STONE_BRICKS : material);
            }
        }
    }


    /**
     * Adds seed-deterministic environmental structures in mirrored quadrants.
     * Their footprints avoid the objective, lanes, active spawns and existing
     * gameplay edits. They remain ordinary BlockEdits in the arena snapshot plan.
     */
    /**
     * Seeded village set. Two distinct templates are selected per seed and
     * distributed as opposite pairs (180-degree symmetry), so the four
     * quadrants receive a balanced mix instead of four copies of one template.
     * Every placement is preflighted before any structure is queued.
     */
    private static void buildGenericStructures(World world, ArenaData arena, TerrainProfile terrain,
                                               Map<BlockPos, BlockEdit> edits, long seed, int radius,
                                               int teamCount) {
        boolean relic = "relic_rush".equalsIgnoreCase(arena.mode);
        Random random = new Random(seed ^ 0x47454E5354525543L);
        Material wall = switch (random.nextInt(3)) {
            case 0 -> Material.STONE_BRICKS;
            case 1 -> Material.MOSSY_STONE_BRICKS;
            default -> Material.COBBLESTONE;
        };
        NavigationScanner navigation = NavigationScanner.scan(arena, terrain, edits);
        int targetPairs = relic ? 4 : 3;
        int placedPairs = 0;

        for (int[] candidate : genericStructureCandidates(radius, relic, random, navigation)) {
            if (placedPairs >= targetPairs) break;
            int x = candidate[0];
            int z = candidate[1];
            int template = random.nextInt(2);
            int[][] pair = candidate[2] == 0
                    ? new int[][]{{-x, -z}, {x, z}}
                    : new int[][]{{-x, z}, {x, -z}};
            if (!isGenericRegionSafe(arena, pair[0][0], pair[0][1], relic, teamCount)
                    || !isGenericRegionSafe(arena, pair[1][0], pair[1][1], relic, teamCount)) {
                continue;
            }
            if (!isGenericSiteClear(world, arena, terrain, edits, pair[0][0], pair[0][1], template)
                    || !isGenericSiteClear(world, arena, terrain, edits, pair[1][0], pair[1][1], template)) {
                continue;
            }

            Map<BlockPos, BlockEdit> beforePair = new LinkedHashMap<>(edits);
            buildGenericStructure(world, arena, terrain, edits,
                    pair[0][0], pair[0][1], template, wall);
            buildGenericStructure(world, arena, terrain, edits,
                    pair[1][0], pair[1][1], template, wall);

            boolean housesAccessible = isGenericHouseAccessible(world, arena, terrain, edits,
                    pair[0][0], pair[0][1]) && isGenericHouseAccessible(world, arena, terrain, edits,
                    pair[1][0], pair[1][1]);
            // Relic Rush may use the main lane as a deliberate chokepoint, but
            // never at the cost of disconnecting either team's altar route.
            boolean routesConnected = !relic
                    || NavigationScanner.hasRelicRoutes(arena, terrain, edits, arena.spawnOffset);
            if (!housesAccessible || !routesConnected) {
                edits.clear();
                edits.putAll(beforePair);
                continue;
            }
            placedPairs++;
        }
    }

    private static List<int[]> genericStructureCandidates(int radius, boolean relic, Random random,
                                                          NavigationScanner navigation) {
        List<int[]> candidates = new ArrayList<>();
        int min = 10;
        int max = Math.max(min, radius - 10);
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                // TDM keeps its detected main and cross lanes clear. Relic Rush
                // deliberately permits structures on its main lane; connectivity
                // is validated after every mirrored pair is planned.
                if (!relic && (navigation.isNearMainPath(x, z, 4)
                        || navigation.isNearMainPath(-x, -z, 4))) continue;
                candidates.add(new int[]{x, z, random.nextBoolean() ? 1 : 0});
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

    private static void buildGenericStructure(World world, ArenaData arena, TerrainProfile terrain,
                                              Map<BlockPos, BlockEdit> edits, int x, int z,
                                              int template, Material wall) {
        switch (template) {
            case 0 -> buildGenericCabin(world, arena, terrain, edits, x, z, wall);
            case 1 -> buildGenericStoneHouse(world, arena, terrain, edits, x, z, wall);
            case 2 -> buildGenericTower(world, arena, terrain, edits, x, z, wall);
            default -> buildGenericRuin(world, arena, terrain, edits, x, z, wall);
        }
    }

    private static boolean isGenericHouseAccessible(World world, ArenaData arena, TerrainProfile terrain,
                                                     Map<BlockPos, BlockEdit> edits, int cx, int cz) {
        int doorY = terrain.heightAt(cx, cz + 3) - arena.centerY + 1;
        BlockEdit lowerDoor = edits.get(new BlockPos(cx, doorY, cz + 3));
        BlockEdit upperDoor = edits.get(new BlockPos(cx, doorY + 1, cz + 3));
        if (lowerDoor == null || upperDoor == null
                || !lowerDoor.material.name().endsWith("_DOOR")
                || !upperDoor.material.name().endsWith("_DOOR")) return false;
        return hasTwoBlockClearance(world, arena, terrain, edits, cx, cz + 2)
                && hasTwoBlockClearance(world, arena, terrain, edits, cx, cz + 4);
    }

    private static boolean hasTwoBlockClearance(World world, ArenaData arena, TerrainProfile terrain,
                                                 Map<BlockPos, BlockEdit> edits, int x, int z) {
        int surfaceY = terrain.heightAt(x, z);
        for (int dy = 1; dy <= 2; dy++) {
            int relativeY = surfaceY + dy - arena.centerY;
            BlockEdit planned = edits.get(new BlockPos(x, relativeY, z));
            if (planned != null) {
                if (planned.material.isAir() || !planned.material.isSolid()
                        || planned.material.name().endsWith("_DOOR")) continue;
                return false;
            }
            if (!world.getBlockAt(arena.centerX + x, surfaceY + dy, arena.centerZ + z).isPassable()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isGenericRegionSafe(ArenaData arena, int x, int z,
                                               boolean relic, int teamCount) {
        int absX = Math.abs(x);
        int absZ = Math.abs(z);
        if (absX <= 8 || absZ <= 8) return false;
        // Relic Rush intentionally allows buildings to occupy primary lanes.
        // Post-placement navigation validation preserves routes to the altar.
        for (int[] spawn : SeededTerrainBlender.anchors(arena.spawnOffset, teamCount)) {
            int dx = Math.max(0, Math.abs(x - spawn[0]) - 7);
            int dz = Math.max(0, Math.abs(z - spawn[1]) - 7);
            if (dx + dz < 8) return false;
        }
        return true;
    }

    private static boolean isGenericSiteClear(World world, ArenaData arena, TerrainProfile terrain,
                                              Map<BlockPos, BlockEdit> edits, int cx, int cz, int template) {
        int half = template == 2 ? 2 : 3;
        int height = template == 2 ? 5 : template == 3 ? 3 : 7;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                if (Math.abs(x) > terrain.radius() - 3 || Math.abs(z) > terrain.radius() - 3) return false;
                int surface = terrain.heightAt(x, z);
                minY = Math.min(minY, surface);
                maxY = Math.max(maxY, surface);
                for (int dy = 1; dy <= height; dy++) {
                    int worldY = surface + dy;
                    if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) return false;
                    BlockPos pos = new BlockPos(x, worldY - arena.centerY, z);
                    BlockEdit planned = edits.get(pos);
                    if (planned != null) {
                        if (planned.material.isAir() || !planned.material.isSolid()) continue;
                        return false;
                    }
                    Block block = world.getBlockAt(arena.centerX + x, worldY, arena.centerZ + z);
                    if (block.getState() instanceof TileState || !block.isPassable()) return false;
                }
            }
        }
        // A near-level footprint keeps the floor, threshold and two-block door aligned.
        return maxY - minY <= 1;
    }

    /** Wooden village house: usable two-block door, glass windows, furnished interior and pitched roof. */
    private static void buildGenericCabin(World world, ArenaData arena, TerrainProfile terrain,
                                          Map<BlockPos, BlockEdit> edits, int cx, int cz, Material wall) {
        buildGenericHouseShell(world, arena, terrain, edits, cx, cz,
                Material.SPRUCE_PLANKS, Material.COBBLESTONE, Material.SPRUCE_DOOR,
                Material.SPRUCE_STAIRS, Material.SPRUCE_SLAB, wall);
        addGenericInterior(world, arena, terrain, edits, cx, cz, Material.SPRUCE_PLANKS);
    }

    /** Stone village house variant with a contrasting timber frame and stone roof. */
    private static void buildGenericStoneHouse(World world, ArenaData arena, TerrainProfile terrain,
                                               Map<BlockPos, BlockEdit> edits, int cx, int cz, Material wall) {
        buildGenericHouseShell(world, arena, terrain, edits, cx, cz,
                Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.OAK_DOOR,
                Material.STONE_BRICK_STAIRS, Material.STONE_BRICK_SLAB, wall);
        addGenericInterior(world, arena, terrain, edits, cx, cz, Material.OAK_PLANKS);
        for (int z : new int[]{-2, 2}) {
            int y = terrain.heightAt(cx, cz + z) - arena.centerY + 3;
            addAir(world, arena, edits, cx, y, cz + z, Material.STRIPPED_OAK_LOG);
        }
    }

    private static void buildGenericHouseShell(World world, ArenaData arena, TerrainProfile terrain,
                                               Map<BlockPos, BlockEdit> edits, int cx, int cz,
                                               Material floor, Material foundation, Material doorMaterial,
                                               Material roofStair, Material roofSlab, Material wall) {
        // 7x7 footprint and continuous floor; perimeter foundation follows the terrain profile.
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                addSurface(world, arena, terrain, edits, cx + x, cz + z,
                        Math.abs(x) == 3 || Math.abs(z) == 3 ? foundation : floor);
            }
        }

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) != 3 && Math.abs(z) != 3) continue;
                int base = terrain.heightAt(cx + x, cz + z) - arena.centerY;
                for (int y = 1; y <= 3; y++) {
                    boolean doorway = z == 3 && x == 0 && y <= 2;
                    boolean sideWindow = Math.abs(x) == 3 && z == 0 && y == 2;
                    boolean rearWindow = z == -3 && x == 0 && y == 2;
                    if (doorway || sideWindow || rearWindow) continue;
                    addAir(world, arena, edits, cx + x, base + y, cz + z, wall);
                }
                if (Math.abs(x) == 3 && z == 0) {
                    addGenericBlockData(world, arena, edits, cx + x, base + 2, cz + z,
                            Bukkit.createBlockData("minecraft:glass_pane"));
                }
                if (z == -3 && x == 0) {
                    addGenericBlockData(world, arena, edits, cx + x, base + 2, cz + z,
                            Bukkit.createBlockData("minecraft:glass_pane"));
                }
            }
        }

        // Door is explicitly represented as lower + upper BlockData, not a lone Material.
        int doorY = terrain.heightAt(cx, cz + 3) - arena.centerY + 1;
        addGenericBlockData(world, arena, edits, cx, doorY, cz + 3,
                Bukkit.createBlockData("minecraft:" + doorMaterial.name().toLowerCase(java.util.Locale.ROOT)
                        + "[facing=south,half=lower,hinge=left,open=false,powered=false]"));
        addGenericBlockData(world, arena, edits, cx, doorY + 1, cz + 3,
                Bukkit.createBlockData("minecraft:" + doorMaterial.name().toLowerCase(java.util.Locale.ROOT)
                        + "[facing=south,half=upper,hinge=left,open=false,powered=false]"));

        // Gable roof: mirrored stair slopes meet at a continuous ridge.
        for (int z = -3; z <= 3; z++) {
            for (int x = -3; x <= 3; x++) {
                int base = terrain.heightAt(cx + x, cz + z) - arena.centerY;
                if (x == 0) {
                    addAir(world, arena, edits, cx + x, base + 6, cz + z, roofSlab);
                } else {
                    String facing = x < 0 ? "west" : "east";
                    int roofY = base + 4 + (3 - Math.abs(x));
                    addGenericBlockData(world, arena, edits, cx + x, roofY, cz + z,
                            Bukkit.createBlockData("minecraft:" + roofStair.name().toLowerCase(java.util.Locale.ROOT)
                                    + "[facing=" + facing + ",half=bottom,shape=straight,waterlogged=false]"));
                }
            }
        }
    }

    private static void addGenericInterior(World world, ArenaData arena, TerrainProfile terrain,
                                           Map<BlockPos, BlockEdit> edits, int cx, int cz, Material floor) {
        int base = terrain.heightAt(cx, cz) - arena.centerY;
        // Furniture is deliberately made from ordinary blocks (no inventories or tile entities),
        // so the existing BlockData snapshot can restore every part safely.
        addAir(world, arena, edits, cx - 2, base + 1, cz - 2, Material.BOOKSHELF);
        addAir(world, arena, edits, cx + 2, base + 1, cz - 2, Material.CRAFTING_TABLE);
        addAir(world, arena, edits, cx - 2, base + 1, cz + 1, Material.BOOKSHELF);
        addAir(world, arena, edits, cx + 2, base + 1, cz + 1, Material.COBBLESTONE_WALL);
        addAir(world, arena, edits, cx, base + 1, cz, Material.OAK_FENCE);
        addAir(world, arena, edits, cx, base + 2, cz, Material.LANTERN);
        addGenericBlockData(world, arena, edits, cx - 1, base + 1, cz,
                Bukkit.createBlockData("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"));
        addGenericBlockData(world, arena, edits, cx + 1, base + 1, cz,
                Bukkit.createBlockData("minecraft:oak_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"));
        addAir(world, arena, edits, cx, base + 1, cz + 2, Material.WHITE_CARPET);
    }

    private static void buildGenericTower(World world, ArenaData arena, TerrainProfile terrain,
                                          Map<BlockPos, BlockEdit> edits, int cx, int cz, Material wall) {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (Math.abs(x) != 2 && Math.abs(z) != 2) continue;
            int base = terrain.heightAt(cx + x, cz + z) - arena.centerY;
            for (int y = 1; y <= 4; y++) {
                addAir(world, arena, edits, cx + x, base + y, cz + z,
                        y == 4 ? Material.STONE_BRICK_WALL : wall);
            }
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            int y = terrain.heightAt(cx + x, cz + z) - arena.centerY + 4;
            addAir(world, arena, edits, cx + x, y, cz + z, Material.SPRUCE_SLAB);
        }
    }

    private static void buildGenericRuin(World world, ArenaData arena, TerrainProfile terrain,
                                         Map<BlockPos, BlockEdit> edits, int cx, int cz, Material wall) {
        for (int offset = -3; offset <= 3; offset++) {
            if (offset == 0) continue;
            addGenericRuinColumn(world, arena, terrain, edits, cx + offset, cz - 3, wall,
                    1 + Math.floorMod(offset, 3));
            addGenericRuinColumn(world, arena, terrain, edits, cx + offset, cz + 3, wall,
                    1 + Math.floorMod(offset + 1, 3));
        }
        addGenericRuinColumn(world, arena, terrain, edits, cx - 3, cz, wall, 2);
        addGenericRuinColumn(world, arena, terrain, edits, cx + 3, cz, wall, 2);
    }

    private static void addGenericRuinColumn(World world, ArenaData arena, TerrainProfile terrain,
                                             Map<BlockPos, BlockEdit> edits, int x, int z,
                                             Material material, int height) {
        int base = terrain.heightAt(x, z) - arena.centerY;
        for (int y = 1; y <= height; y++) {
            addAir(world, arena, edits, x, base + y, z,
                    y == height ? Material.CHISELED_STONE_BRICKS : material);
        }
    }

    private static void addGenericBlockData(World world, ArenaData arena, Map<BlockPos, BlockEdit> edits,
                                            int x, int y, int z, BlockData blockData) {
        Block block = world.getBlockAt(arena.centerX + x, arena.centerY + y, arena.centerZ + z);
        if (block.getState() instanceof TileState) return;
        BlockEdit planned = edits.get(new BlockPos(x, y, z));
        if (planned != null && !planned.material.isAir() && planned.material.isSolid()
                && planned.material != blockData.getMaterial()) return;
        if (planned == null && !block.isPassable()) return;
        edits.put(new BlockPos(x, y, z), new BlockEdit(x, y, z, blockData));
    }

    private static void buildPerimeter(World world, ArenaData arena, TerrainProfile terrain,
                                        Map<BlockPos, BlockEdit> edits, int radius) {
        for (int i = -radius; i <= radius; i++) {
            addPerimeterMarker(world, arena, terrain, edits, -radius, i);
            addPerimeterMarker(world, arena, terrain, edits, radius, i);
            addPerimeterMarker(world, arena, terrain, edits, i, -radius);
            addPerimeterMarker(world, arena, terrain, edits, i, radius);
        }
    }

    private static void addPerimeterMarker(World world, ArenaData arena, TerrainProfile terrain,
                                           Map<BlockPos, BlockEdit> edits, int x, int z) {
        int y = terrain.heightAt(x, z) + 1 - arena.centerY;
        addAir(world, arena, edits, x, y, z, Material.STONE_BRICK_WALL);
    }

    private static void addSurface(World world, ArenaData arena, TerrainProfile terrain,
                                   Map<BlockPos, BlockEdit> edits, int x, int z, Material material) {
        int surfaceY = terrain.heightAt(x, z);
        Block surface = world.getBlockAt(arena.centerX + x, surfaceY, arena.centerZ + z);
        Block above = world.getBlockAt(arena.centerX + x, surfaceY + 1, arena.centerZ + z);
        BlockPos surfacePos = new BlockPos(x, surfaceY - arena.centerY, z);
        BlockPos abovePos = new BlockPos(x, surfaceY + 1 - arena.centerY, z);
        BlockEdit plannedSurface = edits.get(surfacePos);
        BlockEdit plannedAbove = edits.get(abovePos);

        boolean surfaceSolid = plannedSurface != null
                ? (plannedSurface.material.isSolid() || isLiquid(plannedSurface.material))
                : (surface.getType().isSolid() || surface.isLiquid());
        boolean abovePassable = plannedAbove != null
                ? (plannedAbove.material.isAir() || !plannedAbove.material.isSolid())
                : above.isPassable();
        if (surface.getState() instanceof TileState || !surfaceSolid || !abovePassable) return;
        add(edits, x, surfaceY - arena.centerY, z, material);
    }

    private static void addAir(World world, ArenaData arena, Map<BlockPos, BlockEdit> edits,
                               int x, int y, int z, Material material) {
        Block block = world.getBlockAt(arena.centerX + x, arena.centerY + y, arena.centerZ + z);
        if (block.getState() instanceof TileState) return;
        BlockEdit planned = edits.get(new BlockPos(x, y, z));
        boolean plannedAir = planned != null && (planned.material.isAir() || !planned.material.isSolid());
        if (!plannedAir && !block.isPassable()) return;
        add(edits, x, y, z, material);
    }

    private static void add(Map<BlockPos, BlockEdit> edits, int x, int y, int z, Material material) {
        BlockPos position = new BlockPos(x, y, z);
        edits.put(position, new BlockEdit(x, y, z, material));
    }

    /** Bukkit Material has no isLiquid() method; water and lava are the liquid block materials. */
    private static boolean isLiquid(Material material) {
        return material == Material.WATER || material == Material.LAVA;
    }

    /**
     * Plants that clutter low combat sightlines or catch players while moving.
     * Base terrain (for example GRASS_BLOCK), logs, crops, and deliberate arena
     * structures are intentionally excluded.
     */
    static boolean isCombatVegetation(Material material) {
        String name = material.name();
        return switch (name) {
            case "GRASS", "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH",
                    "VINE", "SWEET_BERRY_BUSH", "HANGING_ROOTS", "DANDELION", "POPPY",
                    "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET", "OXEYE_DAISY", "CORNFLOWER",
                    "LILY_OF_THE_VALLEY", "WITHER_ROSE", "TORCHFLOWER", "PINK_PETALS",
                    "SUNFLOWER", "LILAC", "ROSE_BUSH", "PEONY", "BROWN_MUSHROOM",
                    "RED_MUSHROOM", "CRIMSON_FUNGUS", "WARPED_FUNGUS", "NETHER_SPROUTS",
                    "CRIMSON_ROOTS", "WARPED_ROOTS", "TWISTING_VINES", "TWISTING_VINES_PLANT",
                    "WEEPING_VINES", "WEEPING_VINES_PLANT", "CAVE_VINES", "CAVE_VINES_PLANT",
                    "GLOW_LICHEN", "SMALL_DRIPLEAF", "BIG_DRIPLEAF", "SEAGRASS",
                    "TALL_SEAGRASS", "KELP", "KELP_PLANT", "LILY_PAD" -> true;
            default -> name.endsWith("_TULIP") || name.endsWith("_SAPLING")
                    || name.equals("AZALEA")
                    || name.equals("FLOWERING_AZALEA") || name.equals("SPORE_BLOSSOM");
        };
    }
}
