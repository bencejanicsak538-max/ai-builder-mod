package hu.bence.aibuilder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Undo manager v3.0 - max 5 szint undo, /aiundo list tamogatas.
 */
public class AIUndoManager {

    public static class UndoEntry {
        public final String prompt;
        public final List<BlockSnapshot> snapshots;
        public UndoEntry(String prompt, List<BlockSnapshot> snapshots) {
            this.prompt = prompt;
            this.snapshots = snapshots;
        }
    }

    public static class BlockSnapshot {
        public final BlockPos pos;
        public final BlockState state;
        public BlockSnapshot(BlockPos pos, BlockState state) {
            this.pos = pos;
            this.state = state;
        }
    }

    private static final int MAX_UNDO_LEVELS = 5;
    private static final Map<String, Deque<UndoEntry>> UNDO_STACKS = new ConcurrentHashMap<>();

    public static void pushSnapshot(String playerUuid, String prompt, List<BlockSnapshot> snapshots) {
        Deque<UndoEntry> stack = UNDO_STACKS.computeIfAbsent(playerUuid, k -> new ArrayDeque<>());
        stack.push(new UndoEntry(prompt, snapshots));
        while (stack.size() > MAX_UNDO_LEVELS) stack.pollLast();
    }

    public static int getUndoCount(String playerUuid) {
        Deque<UndoEntry> stack = UNDO_STACKS.get(playerUuid);
        return stack == null ? 0 : stack.size();
    }

    public static int undoLast(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            String pid = player.getUuidAsString();
            Deque<UndoEntry> stack = UNDO_STACKS.get(pid);

            if (stack == null || stack.isEmpty()) {
                source.sendFeedback(() -> Text.literal(
                    "\u00a77[AI Builder v" + AIBuilderMod.VERSION + "] Nincs visszavonhato epites."
                ), false);
                return 0;
            }

            UndoEntry entry = stack.pop();
            ServerWorld world = (ServerWorld) player.getWorld();
            int restored = 0;

            for (BlockSnapshot snap : entry.snapshots) {
                world.setBlockState(snap.pos, snap.state);
                restored++;
            }

            final int fr = restored;
            final int remaining = stack.size();
            source.sendFeedback(() -> Text.literal(
                "\u00a7a[AI Builder v" + AIBuilderMod.VERSION + "] Visszavonva: '" + entry.prompt +
                "' (" + fr + " blokk visszaallitva) | Meg " + remaining + " visszavonhato"
            ), false);
            return 1;

        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }
}
