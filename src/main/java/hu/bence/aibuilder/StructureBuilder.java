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
    public static int placePlan(ServerCommandSource source, BuildPlan plan) {
        SimpleConfig cfg = Json.GSON.fromJson(ConfigManager.loadConfig(), SimpleConfig.class);
        if (plan.blocks.size() > cfg.maxBlocks) throw new RuntimeException("T\u00fal sok blokk: " + plan.blocks.size());
        ServerPlayerEntity player;
        try { player = source.getPlayer(); }
        catch (Exception e) { throw new RuntimeException("Csak j\u00e1t\u00e9kos haszn\u00e1lhatja."); }
        World world = player.getWorld();
        BlockPos origin = player.getBlockPos();
        List<AIUndoManager.UndoEntry> undo = new ArrayList<>();
        int placed = 0;
        for (BuildPlan.BlockInstruction b : plan.blocks) {
            if (Math.abs(b.dx) > cfg.maxRadius || Math.abs(b.dy) > cfg.maxRadius || Math.abs(b.dz) > cfg.maxRadius) continue;
            Identifier id = Identifier.tryParse(b.block);
            if (id == null || !Registries.BLOCK.containsId(id)) continue;
            Block block = Registries.BLOCK.get(id);
            BlockPos pos = origin.add(b.dx, b.dy, b.dz);
            BlockState oldState = world.getBlockState(pos);
            if (!cfg.allowReplaceSolid && !oldState.isAir()) continue;
            undo.add(new AIUndoManager.UndoEntry(pos, oldState));
            world.setBlockState(pos, block.getDefaultState(), 3);
            placed++;
        }
        AIUndoManager.push(player.getUuidAsString(), undo);
        return placed;
    }
}
