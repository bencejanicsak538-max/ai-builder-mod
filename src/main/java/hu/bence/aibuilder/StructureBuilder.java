package hu.bence.aibuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class StructureBuilder {
    public static int placePlan(ServerCommandSource source, BuildPlan plan) throws Exception {
        SimpleConfig cfg = ConfigManager.load();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        World world = player.getWorld();
        BlockPos origin = player.getBlockPos();
        List<AIUndoManager.UndoEntry> undo = new ArrayList<>();
        int placed = 0;

        for (BuildPlan.BlockEntry b : plan.blocks) {
            if (placed >= cfg.maxBlocks) break;
            if (Math.abs(b.dx) > cfg.maxRadius || Math.abs(b.dy) > cfg.maxRadius || Math.abs(b.dz) > cfg.maxRadius) continue;
            Identifier id = Identifier.tryParse(b.block);
            if (id == null || !Registries.BLOCK.containsId(id)) continue;
            Block block = Registries.BLOCK.get(id);
            BlockPos pos = origin.add(b.dx, b.dy, b.dz);
            BlockState old = world.getBlockState(pos);
            if (!cfg.allowReplaceSolid && !old.isAir()) continue;
            undo.add(new AIUndoManager.UndoEntry(pos, old));
            world.setBlockState(pos, block.getDefaultState(), 3);
            placed++;
        }
        AIUndoManager.push(player.getUuidAsString(), undo);
        return placed;
    }
}
