package id.hadziq.arenasystem;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;

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
        buildGenericStructures(world, arena, terrain, edits, seed, radius);
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
    private static void buildGenericStructures(World world, ArenaData arena, TerrainProfile terrain,
                                               Map<BlockPos, BlockEdit> edits, long seed, int radius) {
        boolean relic = "relic_rush".equalsIgnoreCase(arena.mode);
        int siteX = Math.min(radius - 9, relic ? 39 : 29);
        int siteZ = Math.min(radius - 10, relic ? 34 : 20);
        if (siteX < 10 || siteZ < 10) return;
        if (!isGenericRegionSafe(arena, siteX, siteZ, relic)) return;

        Random random = new Random(seed ^ 0x47454E5354525543L);
        int template = random.nextInt(3);
        Material wall = switch (random.nextInt(3)) {
            case 0 -> Material.STONE_BRICKS;
            case 1 -> Material.MOSSY_STONE_BRICKS;
            default -> Material.COBBLESTONE;
        };

        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                int x = sx * siteX;
                int z = sz * siteZ;
                if (!isGenericSiteClear(world, arena, terrain, edits, x, z, template)) continue;
                if (template == 0) buildGenericCabin(world, arena, terrain, edits, x, z, wall);
                else if (template == 1) buildGenericTower(world, arena, terrain, edits, x, z, wall);
                else buildGenericRuin(world, arena, terrain, edits, x, z, wall);
            }
        }
    }

    private static boolean isGenericRegionSafe(ArenaData arena, int x, int z, boolean relic) {
        if (x <= 8 || z <= 8) return false;
        if (relic) {
            if (Math.abs(x - 18) <= 5 || Math.abs(z - 18) <= 5) return false;
        } else if (x <= 5 || z <= 5) return false;
        for (int[] spawn : SeededTerrainBlender.anchors(arena.spawnOffset, relic ? 2 : 4)) {
            int dx = Math.max(0, Math.abs(x - spawn[0]) - 7);
            int dz = Math.max(0, Math.abs(z - spawn[1]) - 7);
            if (dx + dz < 8) return false;
        }
        return true;
    }

    private static boolean isGenericSiteClear(World world, ArenaData arena, TerrainProfile terrain,
                                              Map<BlockPos, BlockEdit> edits, int cx, int cz, int template) {
        int half = template == 1 ? 2 : 3;
        int height = template == 1 ? 5 : 3;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                if (Math.abs(x) > terrain.radius() - 3 || Math.abs(z) > terrain.radius() - 3) return false;
                int surface = terrain.heightAt(x, z);
                minY = Math.min(minY, surface);
                maxY = Math.max(maxY, surface);
                for (int dy = 1; dy <= height; dy++) {
                    BlockPos pos = new BlockPos(x, surface + dy - arena.centerY, z);
                    BlockEdit planned = edits.get(pos);
                    if (planned != null) {
                        if (planned.material.isAir() || !planned.material.isSolid()) continue;
                        return false;
                    }
                    Block block = world.getBlockAt(arena.centerX + x, surface + dy, arena.centerZ + z);
                    if (block.getState() instanceof TileState || !block.isPassable()) return false;
                }
            }
        }
        return maxY - minY <= 2;
    }

    private static void buildGenericCabin(World world, ArenaData arena, TerrainProfile terrain,
                                          Map<BlockPos, BlockEdit> edits, int cx, int cz, Material wall) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                addSurface(world, arena, terrain, edits, cx + x, cz + z,
                        Math.abs(x) == 3 || Math.abs(z) == 3 ? Material.COBBLESTONE : Material.SPRUCE_PLANKS);
                if (Math.abs(x) != 3 && Math.abs(z) != 3) continue;
                int base = terrain.heightAt(cx + x, cz + z) - arena.centerY;
                for (int y = 1; y <= 2; y++) {
                    boolean doorway = z == 3 && x == 0;
                    boolean window = y == 2 && ((Math.abs(x) == 3 && z == 0) || (z == -3 && x == -2));
                    if (!doorway && !window) addAir(world, arena, edits, cx + x, base + y, cz + z, wall);
                }
            }
        }
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            int y = terrain.heightAt(cx + x, cz + z) - arena.centerY + 3;
            addAir(world, arena, edits, cx + x, y, cz + z, Material.SPRUCE_SLAB);
        }
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
