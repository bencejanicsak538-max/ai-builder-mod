package hu.bence.aibuilder;

import net.minecraft.block.BlockState;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class AIUndoManager {
    public record UndoEntry(BlockPos pos, BlockState oldState) {}
    private static final Map<String, Deque<List<UndoEntry>>> HISTORY = new HashMap<>();

    public static void push(String pid, List<UndoEntry> entries) {
        HISTORY.computeIfAbsent(pid, k -> new ArrayDeque<>()).push(entries);
    }

    public static int undoLast(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            Deque<List<UndoEntry>> stack = HISTORY.get(player.getUuidAsString());
            if (stack == null || stack.isEmpty()) {
                source.sendError(Text.literal("Nincs mit visszavonni."));
                return 0;
            }
            List<UndoEntry> entries = stack.pop();
            for (UndoEntry e : entries) player.getWorld().setBlockState(e.pos(), e.oldState(), 3);
            source.sendFeedback(() -> Text.literal("[AI Builder] Visszavonva: " + entries.size() + " blokk"), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Csak jatekos hasznalhatja."));
            return 0;
        }
    }
}
