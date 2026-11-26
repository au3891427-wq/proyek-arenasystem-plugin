package id.hadziq.arenasystem;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Standalone checks for SnapshotRestoreValidator, including ARN2/ARN3 snapshots read from disk. */
public final class TestSnapshotRestoreValidator {
    static final Function<String, String> PARSER = raw -> {
        if (raw.contains("INVALID")) throw new IllegalArgumentException("bad block data: " + raw);
        return raw;
    };

    public static void main(String[] args) throws Exception {
        for (int badIndex : new int[]{0, 5, 9}) testInvalidBlockDataAnywhereMutatesNothing(badIndex);
        testOutOfBoundsRelativePosition();
        testAbsoluteHeightBounds();
        testNegativeBoundSkipsHorizontalCheckOnly();
        testValidPlanKeepsAllEntries();
        testArn2AndArn3SnapshotsValidateAfterReload();
        System.out.println("PASS: restore pre-validation (BlockData start/middle/end, relative bounds, world height, ARN2/ARN3).");
    }

    static Map<BlockPos, String> tenBlocks(int badIndex) {
        Map<BlockPos, String> blocks = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            blocks.put(new BlockPos(i, 0, 0), i == badIndex ? "minecraft:INVALID[x=1]" : "minecraft:stone");
        }
        return blocks;
    }

    static void testInvalidBlockDataAnywhereMutatesNothing(int badIndex) {
        int[] worldWrites = {0};
        try {
            List<SnapshotRestoreValidator.Entry<String>> plan = SnapshotRestoreValidator.validate(
                    tenBlocks(badIndex), 100, 64, 100, 40, -64, 320, PARSER);
            for (SnapshotRestoreValidator.Entry<String> ignored : plan) worldWrites[0]++; // the apply loop
            throw new AssertionError("invalid entry at index " + badIndex + " was accepted");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("INVALID"), "message names the bad data");
        }
        check(worldWrites[0] == 0, "no block may be written when entry " + badIndex + " is invalid");
    }

    static void testOutOfBoundsRelativePosition() {
        for (BlockPos bad : List.of(new BlockPos(41, 0, 0), new BlockPos(0, 0, -41), new BlockPos(Integer.MIN_VALUE, 0, 0))) {
            Map<BlockPos, String> blocks = new LinkedHashMap<>();
            blocks.put(new BlockPos(0, 0, 0), "minecraft:stone");
            blocks.put(bad, "minecraft:stone");
            expectRejected(() -> SnapshotRestoreValidator.validate(blocks, 0, 64, 0, 40, -64, 320, PARSER),
                    "relative position " + bad.x + "," + bad.z + " must be rejected");
        }
        Map<BlockPos, String> edge = Map.of(new BlockPos(40, 0, -40), "minecraft:stone");
        check(SnapshotRestoreValidator.validate(edge, 0, 64, 0, 40, -64, 320, PARSER).size() == 1, "edge accepted");
    }

    static void testAbsoluteHeightBounds() {
        expectRejected(() -> SnapshotRestoreValidator.validate(
                Map.of(new BlockPos(0, 300, 0), "minecraft:stone"), 0, 64, 0, 40, -64, 320, PARSER), "above max height");
        expectRejected(() -> SnapshotRestoreValidator.validate(
                Map.of(new BlockPos(0, -200, 0), "minecraft:stone"), 0, 64, 0, 40, -64, 320, PARSER), "below min height");
        expectRejected(() -> SnapshotRestoreValidator.validate(
                Map.of(new BlockPos(0, 0, 0), "minecraft:stone"), 0, Integer.MAX_VALUE, 0, 40, -64, 320, PARSER), "absolute Y overflow");
        expectRejected(() -> SnapshotRestoreValidator.validate(
                Map.of(new BlockPos(10, 0, 0), "minecraft:stone"), Integer.MAX_VALUE, 64, 0, 40, -64, 320, PARSER), "absolute X overflow");
        check(SnapshotRestoreValidator.validate(Map.of(new BlockPos(0, 254, 0), "minecraft:stone"),
                0, 65, 0, 40, -64, 320, PARSER).size() == 1, "y=319 is the last valid block");
        expectRejected(() -> SnapshotRestoreValidator.validate(Map.of(new BlockPos(0, 255, 0), "minecraft:stone"),
                0, 65, 0, 40, -64, 320, PARSER), "y=320 is one past the last valid block");
    }

    static void testNegativeBoundSkipsHorizontalCheckOnly() {
        Map<BlockPos, String> far = Map.of(new BlockPos(500, 0, 0), "minecraft:stone");
        check(SnapshotRestoreValidator.validate(far, 0, 64, 0, -1, -64, 320, PARSER).size() == 1, "match changes: no horizontal bound");
        expectRejected(() -> SnapshotRestoreValidator.validate(
                Map.of(new BlockPos(500, 900, 0), "minecraft:stone"), 0, 64, 0, -1, -64, 320, PARSER), "height still enforced");
    }

    static void testValidPlanKeepsAllEntries() {
        List<SnapshotRestoreValidator.Entry<String>> plan = SnapshotRestoreValidator.validate(
                tenBlocks(-1), 100, 64, -50, 40, -64, 320, PARSER);
        check(plan.size() == 10, "all entries kept");
        check(plan.get(3).worldX == 103 && plan.get(3).worldY == 64 && plan.get(3).worldZ == -50, "absolute coordinates computed");
    }

    static void testArn2AndArn3SnapshotsValidateAfterReload() throws Exception {
        Path dir = Files.createTempDirectory("arenasystem-validator-");
        try {
            SnapshotStore store = new SnapshotStore(dir.toFile());
            ArenaData arena = new ArenaData("arena1", "world", 1L, 100, -45, 70, 70, 70, 74, 70, 70, "tdm", 27, 40);
            Map<BlockPos, String> original = tenBlocks(-1);
            store.save(arena, original);                       // ARN3
            SnapshotStore.LoadedSnapshot arn3 = store.loadSnapshot(arena);
            check(!arn3.legacy(), "ARN3");
            check(SnapshotRestoreValidator.validate(arn3.blocks(), arena.centerX, arn3.centerY(), arena.centerZ,
                    40, -64, 320, PARSER).size() == 10, "ARN3 validates");
            store.delete("arena1");
            store.save("arena1", original);                    // ARN2
            SnapshotStore.LoadedSnapshot arn2 = store.loadSnapshot(arena);
            check(arn2.legacy(), "ARN2");
            check(SnapshotRestoreValidator.validate(arn2.blocks(), arena.centerX, arn2.centerY(), arena.centerZ,
                    40, -64, 320, PARSER).size() == 10, "ARN2 validates");
            // A well-formed gzip with an out-of-range snapshot entry is accepted by the store
            // (format-level check only) and must be caught by the restore validator.
            store.delete("arena1");
            Map<BlockPos, String> hostile = new LinkedHashMap<>(original);
            hostile.put(new BlockPos(9999, 0, 0), "minecraft:stone");
            store.save(arena, hostile);
            SnapshotStore.LoadedSnapshot loaded = store.loadSnapshot(arena);
            expectRejected(() -> SnapshotRestoreValidator.validate(loaded.blocks(), arena.centerX, loaded.centerY(),
                    arena.centerZ, 56, -64, 320, PARSER), "hostile snapshot entry rejected before restore");
        } finally {
            TestAtomicFileWriter.deleteTree(dir);
        }
    }

    static void expectRejected(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
