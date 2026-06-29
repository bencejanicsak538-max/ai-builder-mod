package hu.bence.aibuilder;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tobbszintes undo kezelese - max MAX_UNDO_LEVELS epitemenyt tarol jatekosokenkent.
 * /aiundo -> utolso visszavonasa
 * /aiundo <N> -> N-edik visszavonasa
 * /aiundo list -> osszes szint listazasa
 */
public class AIUndoManager {

    public static final int MAX_UNDO_LEVELS = 5;

    public static class UndoEntry {
        public final BlockPos pos;
        public final net.minecraft.block.BlockState state;
        public UndoEntry(BlockPos pos, net.minecraft.block.BlockState state) {
            this.pos = pos; this.state = state;
        }
    }

    public static class UndoLevel {
        public final List<UndoEntry> entries;
        public final String prompt;
        public final long timestamp;
        public final int blockCount;

        public UndoLevel(List<UndoEntry> entries, String prompt, int blockCount) {
            this.entries = entries;
            this.prompt = prompt;
            this.timestamp = System.currentTimeMillis();
            this.blockCount = blockCount;
        }

        public String timeAgo() {
            long sec = (System.currentTimeMillis() - timestamp) / 1000;
            if (sec < 60) return sec + "mp";
            if (sec < 3600) return (sec / 60) + "perc";
            return (sec / 3600) + "ora";
        }
    }

    private static final Map<String, Deque<UndoLevel>> UNDO_STACK = new ConcurrentHashMap<>();

    public static void push(String playerUuid, List<UndoEntry> entries, String prompt) {
        if (entries == null || entries.isEmpty()) return;
        UNDO_STACK.computeIfAbsent(playerUuid, k -> new ArrayDeque<>());
        Deque<UndoLevel> stack = UNDO_STACK.get(playerUuid);
        stack.addFirst(new UndoLevel(entries, prompt, entries.size()));
        while (stack.size() > MAX_UNDO_LEVELS) stack.removeLast();
    }

    /** Eredeti egyszeru push - prompt nelkul (visszafele kompatibilis) */
    public static void push(String playerUuid, List<UndoEntry> entries) {
        push(playerUuid, entries, "(ismeretlen)");
    }

    public static int undoLast(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            return undoLevel(source, player, 1);
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos tudja hasznalni."));
            return 0;
        }
    }

    public static int undoLevel(ServerCommandSource source, ServerPlayerEntity player, int level) {
        String uuid = player.getUuidAsString();
        Deque<UndoLevel> stack = UNDO_STACK.get(uuid);
        if (stack == null || stack.isEmpty()) {
            source.sendFeedback(() -> Text.literal("\u00a7c[AI Builder] Nincs visszavonhato epites!"), false);
            return 0;
        }
        if (level < 1 || level > stack.size()) {
            source.sendFeedback(() -> Text.literal(
                "\u00a7c[AI Builder] Ervenytelen undo szint. Max: " + stack.size()), false);
            return 0;
        }
        // Levesszuk az elso N szintet
        List<UndoLevel> toUndo = new ArrayList<>();
        for (int i = 0; i < level; i++) {
            UndoLevel ul = stack.pollFirst();
            if (ul != null) toUndo.add(ul);
        }
        ServerWorld world = (ServerWorld) player.getWorld();
        int restored = 0;
        for (UndoLevel ul : toUndo) {
            for (UndoEntry e : ul.entries) {
                try { world.setBlockState(e.pos, e.state, 3); restored++; }
                catch (Exception ignored) {}
            }
        }
        final int r = restored;
        source.sendFeedback(() -> Text.literal(
            "\u00a7a[AI Builder] " + level + " szint visszavonva, " + r + " blokk visszaallitva."
        ), false);
        return 1;
    }

    public static int listUndo(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            String uuid = player.getUuidAsString();
            Deque<UndoLevel> stack = UNDO_STACK.get(uuid);
            if (stack == null || stack.isEmpty()) {
                source.sendFeedback(() -> Text.literal("\u00a77[AI Builder] Nincs undo elozmenyed."), false);
                return 1;
            }
            source.sendFeedback(() -> Text.literal("\u00a7e=== AI Builder Undo Stack ==="), false);
            int i = 1;
            for (UndoLevel ul : stack) {
                final int idx = i;
                final String line = "\u00a77" + idx + ". " + ul.blockCount + " blokk | " +
                    ul.timeAgo() + " ezelott | \"" + HttpUtil.truncate(ul.prompt, 40) + "\"";
                source.sendFeedback(() -> Text.literal(line), false);
                i++;
            }
            source.sendFeedback(() -> Text.literal(
                "\u00a77Hasznalat: /aiundo | /aiundo 2 (2 szint)"), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos tudja hasznalni."));
            return 0;
        }
    }
}
