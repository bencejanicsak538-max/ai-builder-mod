package hu.bence.aibuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StructureBuilder {

    public static int placePlan(ServerCommandSource source, BuildPlan plan) throws Exception {
        SimpleConfig cfg = ConfigManager.load();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        World world = player.getWorld();
        BlockPos origin = player.getBlockPos();
        List<AIUndoManager.UndoEntry> undo = new ArrayList<>();
        int placed = 0;
        int skipped = 0;

        for (BuildPlan.BlockEntry b : plan.blocks) {
            // Cancel check every 50 blocks
            if (placed % 50 == 0 && !AIBuilderMod.ACTIVE_BUILDS.contains(player.getUuidAsString())) {
                source.sendFeedback(() -> net.minecraft.text.Text.literal("\u00a7c[AI Builder] Epites megszakitva."), false);
                break;
            }

            if (placed >= cfg.maxBlocks) break;

            // Bounds check
            if (Math.abs(b.dx) > cfg.maxRadius || Math.abs(b.dy) > cfg.maxRadius || Math.abs(b.dz) > cfg.maxRadius) {
                skipped++;
                continue;
            }

            // Validate block ID
            if (b.block == null || b.block.isBlank()) { skipped++; continue; }
            String blockId = b.block.contains(":") ? b.block : "minecraft:" + b.block;
            Identifier id = Identifier.tryParse(blockId);
            if (id == null || !Registries.BLOCK.containsId(id)) { skipped++; continue; }

            Block block = Registries.BLOCK.get(id);
            BlockPos pos = origin.add(b.dx, b.dy, b.dz);

            // Don't place outside world bounds
            if (!World.isValid(pos)) { skipped++; continue; }

            // Don't place in unloaded chunks
            if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) { skipped++; continue; }

            BlockState old = world.getBlockState(pos);

            // Skip solid blocks unless allowed
            if (!cfg.allowReplaceSolid && !old.isAir() && !old.isReplaceable()) { skipped++; continue; }

            // Get block state, applying optional state properties
            BlockState state = applyStateProperties(block.getDefaultState(), b.state);

            undo.add(new AIUndoManager.UndoEntry(pos, old));
            world.setBlockState(pos, state, 3);
            placed++;
        }

        AIUndoManager.push(player.getUuidAsString(), undo);

        if (skipped > 0) {
            final int s = skipped;
            source.sendFeedback(() -> net.minecraft.text.Text.literal(
                "\u00a77[AI Builder] " + s + " blokk kihagyva (hataron kivul / ervenytelen / foglalt)"
            ), false);
        }

        return placed;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState applyStateProperties(BlockState state, String stateStr) {
        if (stateStr == null || stateStr.isBlank()) return state;
        try {
            for (String part : stateStr.split(",")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length != 2) continue;
                String key = kv[0].trim();
                String value = kv[1].trim();
                Optional<Property<?>> propOpt = state.getProperties().stream()
                    .filter(p -> p.getName().equals(key))
                    .findFirst();
                if (propOpt.isEmpty()) continue;
                Property prop = propOpt.get();
                Optional<?> valOpt = prop.parse(value);
                if (valOpt.isPresent()) {
                    state = state.with(prop, (Comparable) valOpt.get());
                }
            }
        } catch (Exception ignored) {
            // If state parsing fails, use default state - not a fatal error
        }
        return state;
    }
}
