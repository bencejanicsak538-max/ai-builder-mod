package hu.bence.aibuilder;

import net.minecraft.block.BlockState;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class AIUndoManager {
    public record UndoEntry(BlockPos pos, BlockState oldState) {}

    // Max 10 undo levels per player
    private static final int MAX_HISTORY = 10;
    private static final Map<String, Deque<List<UndoEntry>>> HISTORY = new HashMap<>();

    public static void push(String pid, List<UndoEntry> entries) {
        if (entries.isEmpty()) return;
        Deque<List<UndoEntry>> stack = HISTORY.computeIfAbsent(pid, k -> new ArrayDeque<>());
        stack.push(entries);
        // Trim old history
        while (stack.size() > MAX_HISTORY) stack.pollLast();
    }

    public static int undoLast(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            Deque<List<UndoEntry>> stack = HISTORY.get(player.getUuidAsString());
            if (stack == null || stack.isEmpty()) {
                source.sendError(Text.literal("[AI Builder] Nincs mit visszavonni."));
                return 0;
            }
            List<UndoEntry> entries = stack.pop();
            for (UndoEntry e : entries) {
                // Safety: only undo within loaded chunks
                if (player.getWorld().isChunkLoaded(e.pos().getX() >> 4, e.pos().getZ() >> 4)) {
                    player.getWorld().setBlockState(e.pos(), e.oldState(), 3);
                }
            }
            int remaining = stack.size();
            source.sendFeedback(() -> Text.literal(
                "\u00a7a[AI Builder] Visszavonva: " + entries.size() + " blokk. Meg " + remaining + " visszavonhato lepés."
            ), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }

    public static int getHistorySize(String pid) {
        Deque<List<UndoEntry>> stack = HISTORY.get(pid);
        return stack != null ? stack.size() : 0;
    }
}
