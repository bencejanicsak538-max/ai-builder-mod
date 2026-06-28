package hu.bence.aibuilder;

import net.minecraft.block.BlockState;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AIUndoManager {
    public record UndoEntry(BlockPos pos, BlockState oldState) {}
    private static final Map<String, Deque<List<UndoEntry>>> HISTORY = new HashMap<>();

    public static void push(String playerId, List<UndoEntry> entries) {
        HISTORY.computeIfAbsent(playerId, k -> new ArrayDeque<>()).push(entries);
    }

    public static int undoLast(ServerCommandSource source) {
        ServerPlayerEntity player;
        try { player = source.getPlayer(); }
        catch (Exception e) {
            source.sendError(Text.literal("Csak j\u00e1t\u00e9kos haszn\u00e1lhatja."));
            return 0;
        }
        Deque<List<UndoEntry>> stack = HISTORY.get(player.getUuidAsString());
        if (stack == null || stack.isEmpty()) {
            source.sendError(Text.literal("Nincs mit visszavonni."));
            return 0;
        }
        List<UndoEntry> entries = stack.pop();
        for (UndoEntry entry : entries) player.getWorld().setBlockState(entry.pos(), entry.oldState(), 3);
        source.sendFeedback(() -> Text.literal("Visszavonva: " + entries.size() + " blokk"), false);
        return 1;
    }
}
