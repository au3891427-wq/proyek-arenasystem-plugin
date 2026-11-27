package id.hadziq.arenasystem;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

// $VF: Compiled from NavigationScanner.java
final class NavigationScanner {
    private static final Set<Material> PATH_MATERIALS = Set.of(
        Material.GRAVEL,
        Material.COBBLESTONE,
        Material.COARSE_DIRT,
        Material.MOSSY_COBBLESTONE,
        Material.CUT_SANDSTONE,
        Material.SMOOTH_SANDSTONE,
        Material.SANDSTONE
    );
    private final int radius;
    private final boolean[][] mainPath;

    private NavigationScanner(int radius, boolean[][] mainPath) {
        this.radius = radius;
        this.mainPath = mainPath;
    }

    static NavigationScanner scan(ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits) {
        int radius = terrain.radius();
        boolean[][] path = new boolean[radius * 2 + 1][radius * 2 + 1];

        for (BlockEdit edit : edits.values()) {
            int x = edit.position.x;
            int z = edit.position.z;
            if (Math.abs(x) <= radius
                && Math.abs(z) <= radius
                && edit.position.y == terrain.heightAt(x, z) - arena.centerY
                && PATH_MATERIALS.contains(edit.material)) {
                path[x + radius][z + radius] = true;
            }
        }

        return new NavigationScanner(radius, path);
    }

    boolean isMainPath(int x, int z) {
        return this.inside(x, z) && this.mainPath[x + this.radius][z + this.radius];
    }

    boolean isNearMainPath(int x, int z, int clearance) {
        for (int dx = -clearance; dx <= clearance; dx++) {
            for (int dz = -clearance; dz <= clearance; dz++) {
                if (this.isMainPath(x + dx, z + dz)) {
                    return true;
                }
            }
        }

        return false;
    }

    static boolean hasRelicRoutes(
        ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int spawnOffset, int teamCount
    ) {
        // Altar (0,0) sendiri tidak walkable: buildRelicShrine menumpuk monumen dekoratif
        // 4 blok tepat di kolom (0,0) sebelum validasi ini berjalan, sama seperti titik
        // spawn tim yang juga tidak dipakai langsung (digeser 6 blok). Maka rute divalidasi
        // menuju sel walkable terdekat dari altar, bukan ke blok monumen itu sendiri.
        int[] altarAccess = nearestWalkable(arena, terrain, edits, 0, 0, 8);
        if (altarAccess == null) {
            return false;
        }

        for (String team : RelicTeamLayout.teamNames(teamCount)) {
            int[] spawn = RelicTeamLayout.offset(team, teamCount, spawnOffset);
            int startX = spawn[0] - Integer.signum(spawn[0]) * 6;
            int startZ = spawn[1] - Integer.signum(spawn[1]) * 6;
            if (!hasTwoVertexDisjointPaths(arena, terrain, edits, startX, startZ, altarAccess[0], altarAccess[1])) {
                return false;
            }
        }

        return true;
    }

    /**
     * BFS lokal (dibatasi maxRadius) untuk menemukan sel walkable terdekat dari (x, z).
     * Dipakai agar validasi rute tidak menembak langsung ke blok yang memang tidak
     * walkable secara desain (mis. dasar monumen altar).
     */
    private static int[] nearestWalkable(
        ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int x, int z, int maxRadius
    ) {
        int radius = terrain.radius();
        if (Math.abs(x) <= radius && Math.abs(z) <= radius && walkable(arena, terrain, edits, x, z)) {
            return new int[]{x, z};
        }

        int[] dx = new int[]{1, -1, 0, 0};
        int[] dz = new int[]{0, 0, 1, -1};
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        Set<Long> visited = new java.util.HashSet<>();
        queue.add(new int[]{x, z});
        visited.add(((long) x << 32) ^ (z & 0xffffffffL));

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            if (Math.abs(cur[0] - x) > maxRadius || Math.abs(cur[1] - z) > maxRadius) {
                continue;
            }

            for (int i = 0; i < 4; i++) {
                int nx = cur[0] + dx[i];
                int nz = cur[1] + dz[i];
                if (Math.abs(nx) > radius || Math.abs(nz) > radius) {
                    continue;
                }

                long key = ((long) nx << 32) ^ (nz & 0xffffffffL);
                if (visited.add(key)) {
                    if (walkable(arena, terrain, edits, nx, nz)) {
                        return new int[]{nx, nz};
                    }
                    queue.add(new int[]{nx, nz});
                }
            }
        }

        return null;
    }

    /**
     * Menentukan apakah terdapat minimal dua rute dari (startX, startZ) ke (goalX, goalZ)
     * yang tidak berbagi sel perantara (vertex-disjoint), dengan titik awal dan tujuan
     * boleh dipakai bersama oleh kedua rute.
     *
     * Dibangun sebagai jaringan maximum-flow dengan vertex splitting: setiap sel (x, z)
     * dipecah menjadi node "in" dan "out" yang dihubungkan dengan kapasitas 1 (kapasitas 2
     * khusus untuk sel awal dan tujuan, karena keduanya boleh dibagi). Dua rute terpisah ada
     * jika dan hanya jika max-flow dari start ke goal pada graf ini mencapai 2. Dihitung
     * dengan algoritma Dinic, dihentikan lebih awal begitu flow mencapai 2.
     */
    private static boolean hasTwoVertexDisjointPaths(
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int startX,
        int startZ,
        int goalX,
        int goalZ
    ) {
        int radius = terrain.radius();
        if (Math.abs(startX) > radius || Math.abs(startZ) > radius
            || Math.abs(goalX) > radius || Math.abs(goalZ) > radius) {
            return false;
        }

        if (!walkable(arena, terrain, edits, startX, startZ) || !walkable(arena, terrain, edits, goalX, goalZ)) {
            return false;
        }

        if (startX == goalX && startZ == goalZ) {
            return true;
        }

        int size = radius * 2 + 1;
        int cellCount = size * size;
        int source = cellCount * 2;
        int sink = cellCount * 2 + 1;
        // Per sel: 1 edge in->out + sampai 4 edge out->in tetangga, masing-masing addEdge
        // menyimpan 2 slot (edge maju + edge balik). Ditambah 2 edge source/sink.
        int maxEdges = cellCount * (1 + 4) * 2 + 4;
        DinicMaxFlow graph = new DinicMaxFlow(cellCount * 2 + 2, maxEdges);

        boolean[] isWalkable = new boolean[cellCount];
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                isWalkable[cellIndex(x, z, radius, size)] = walkable(arena, terrain, edits, x, z);
            }
        }

        int[] dx = new int[]{1, -1, 0, 0};
        int[] dz = new int[]{0, 0, 1, -1};

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int index = cellIndex(x, z, radius, size);
                if (!isWalkable[index]) {
                    continue;
                }

                boolean isEndpoint = x == startX && z == startZ || x == goalX && z == goalZ;
                graph.addEdge(nodeIn(index), nodeOut(index), isEndpoint ? 2 : 1);

                for (int i = 0; i < 4; i++) {
                    int nx = x + dx[i];
                    int nz = z + dz[i];
                    if (Math.abs(nx) <= radius && Math.abs(nz) <= radius) {
                        int neighborIndex = cellIndex(nx, nz, radius, size);
                        if (isWalkable[neighborIndex]
                            && Math.abs(terrain.heightAt(nx, nz) - terrain.heightAt(x, z)) <= 1) {
                            graph.addEdge(nodeOut(index), nodeIn(neighborIndex), 2);
                        }
                    }
                }
            }
        }

        int startIndex = cellIndex(startX, startZ, radius, size);
        int goalIndex = cellIndex(goalX, goalZ, radius, size);
        graph.addEdge(source, nodeIn(startIndex), 2);
        graph.addEdge(nodeOut(goalIndex), sink, 2);

        return graph.maxFlow(source, sink, 2) >= 2;
    }

    private static int cellIndex(int x, int z, int radius, int size) {
        return (x + radius) * size + (z + radius);
    }

    private static int nodeIn(int cellIndex) {
        return cellIndex * 2;
    }

    private static int nodeOut(int cellIndex) {
        return cellIndex * 2 + 1;
    }

    /**
     * Implementasi Dinic's algorithm ringan berbasis array (tanpa alokasi objek per edge)
     * agar efisien untuk arena hingga 113x113 (~25k node hasil vertex splitting).
     */
    private static final class DinicMaxFlow {
        private final int[] head;
        private final int[] nextEdge;
        private final int[] to;
        private final int[] capacity;
        private final int[] level;
        private final int[] iter;
        private int edgeCount;

        DinicMaxFlow(int nodeCount, int maxEdgeSlots) {
            this.head = new int[nodeCount];
            Arrays.fill(this.head, -1);
            this.nextEdge = new int[maxEdgeSlots];
            this.to = new int[maxEdgeSlots];
            this.capacity = new int[maxEdgeSlots];
            this.level = new int[nodeCount];
            this.iter = new int[nodeCount];
        }

        void addEdge(int from, int toNode, int edgeCapacity) {
            this.to[this.edgeCount] = toNode;
            this.capacity[this.edgeCount] = edgeCapacity;
            this.nextEdge[this.edgeCount] = this.head[from];
            this.head[from] = this.edgeCount++;

            this.to[this.edgeCount] = from;
            this.capacity[this.edgeCount] = 0;
            this.nextEdge[this.edgeCount] = this.head[toNode];
            this.head[toNode] = this.edgeCount++;
        }

        int maxFlow(int source, int sink, int limit) {
            int flow = 0;
            while (flow < limit && bfs(source, sink)) {
                System.arraycopy(this.head, 0, this.iter, 0, this.head.length);
                int pushed;
                while (flow < limit && (pushed = dfs(source, sink, limit - flow)) > 0) {
                    flow += pushed;
                }
            }

            return flow;
        }

        private boolean bfs(int source, int sink) {
            Arrays.fill(this.level, -1);
            int[] queue = new int[this.level.length];
            int head0 = 0;
            int tail = 0;
            this.level[source] = 0;
            queue[tail++] = source;

            while (head0 < tail) {
                int u = queue[head0++];
                for (int e = this.head[u]; e != -1; e = this.nextEdge[e]) {
                    if (this.capacity[e] > 0 && this.level[this.to[e]] < 0) {
                        this.level[this.to[e]] = this.level[u] + 1;
                        queue[tail++] = this.to[e];
                    }
                }
            }

            return this.level[sink] >= 0;
        }

        private int dfs(int u, int sink, int limit) {
            if (u == sink) {
                return limit;
            }

            for (; this.iter[u] != -1; this.iter[u] = this.nextEdge[this.iter[u]]) {
                int e = this.iter[u];
                int v = this.to[e];
                if (this.capacity[e] > 0 && this.level[v] == this.level[u] + 1) {
                    int pushed = dfs(v, sink, Math.min(limit, this.capacity[e]));
                    if (pushed > 0) {
                        this.capacity[e] -= pushed;
                        this.capacity[e ^ 1] += pushed;
                        return pushed;
                    }
                }
            }

            return 0;
        }
    }

    private static boolean walkable(
        ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int x, int z
    ) {
        int surfaceY = terrain.heightAt(x, z);
        BlockEdit surface = edits.get(new BlockPos(x, surfaceY - arena.centerY, z));
        if (surface != null && !surface.material.isSolid()) {
            return false;
        }

        for (int dy = 1; dy <= 2; dy++) {
            BlockEdit above = edits.get(new BlockPos(x, surfaceY + dy - arena.centerY, z));
            if (above != null
                && !above.material.isAir()
                && above.material.isSolid()
                && !above.material.name().endsWith("_DOOR")) {
                return false;
            }
        }

        return true;
    }

    private boolean inside(int x, int z) {
        return Math.abs(x) <= this.radius && Math.abs(z) <= this.radius;
    }
}
