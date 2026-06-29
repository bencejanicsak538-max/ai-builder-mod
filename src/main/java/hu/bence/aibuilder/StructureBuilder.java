package hu.bence.aibuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StructureBuilder {

    // Animalt epites: ennyi ms szunet blokkonkent
    private static final long DELAY_PER_BLOCK_MS = 60L;
    // Progress uzenet minden N blokk utan
    private static final int PROGRESS_EVERY = 10;

    public static int placePlan(ServerCommandSource source, BuildPlan plan) throws Exception {
        SimpleConfig cfg = ConfigManager.load();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos origin = player.getBlockPos();
        List<AIUndoManager.UndoEntry> undo = new ArrayList<>();
        int placed = 0;
        int skipped = 0;
        int total = plan.blocks.size();

        for (BuildPlan.BlockEntry b : plan.blocks) {
            // Cancel check
            if (!AIBuilderMod.ACTIVE_BUILDS.contains(player.getUuidAsString())) {
                source.sendFeedback(() -> net.minecraft.text.Text.literal("\u00a7c[AI Builder] Epites megszakitva."), false);
                break;
            }

            if (placed >= cfg.maxBlocks) break;

            // Bounds check
            if (Math.abs(b.dx) > cfg.maxRadius || Math.abs(b.dy) > cfg.maxRadius || Math.abs(b.dz) > cfg.maxRadius) {
                skipped++;
                continue;
            }

            if (b.block == null || b.block.isBlank()) { skipped++; continue; }
            String blockId = b.block.contains(":") ? b.block : "minecraft:" + b.block;
            Identifier id = Identifier.tryParse(blockId);
            if (id == null || !Registries.BLOCK.containsId(id)) { skipped++; continue; }

            Block block = Registries.BLOCK.get(id);
            BlockPos pos = origin.add(b.dx, b.dy, b.dz);

            if (!World.isValid(pos)) { skipped++; continue; }
            if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) { skipped++; continue; }

            BlockState old = world.getBlockState(pos);
            if (!cfg.allowReplaceSolid && !old.isAir() && !old.isReplaceable()) { skipped++; continue; }

            BlockState state = applyStateProperties(block.getDefaultState(), b.state);

            undo.add(new AIUndoManager.UndoEntry(pos, old));
            world.setBlockState(pos, state, 3);
            placed++;

            // ---- Particle effect a lerakott blokk kore ----
            spawnPlaceParticle(world, pos);

            // ---- Progress update az AI Wand-nak ----
            WandProgressTracker.update(player.getUuidAsString(), placed, total);

            // ---- Progress uzenet chatbe minden PROGRESS_EVERY blokk utan ----
            if (placed % PROGRESS_EVERY == 0) {
                final int p = placed;
                final int pct = (int) ((p * 100.0) / Math.max(1, total));
                source.sendFeedback(() -> net.minecraft.text.Text.literal(
                    "\u00a77[AI Builder] Epites: " + buildBar(pct) + " " + p + "/" + total + " (" + pct + "%)"
                ), false);
            }

            // ---- Animalt epites: kis varakozas blokkonkent ----
            try { Thread.sleep(DELAY_PER_BLOCK_MS); } catch (InterruptedException e) { break; }
        }

        AIUndoManager.push(player.getUuidAsString(), undo);

        // Vegso progress 100%
        WandProgressTracker.finish(player.getUuidAsString());

        if (skipped > 0) {
            final int s = skipped;
            source.sendFeedback(() -> net.minecraft.text.Text.literal(
                "\u00a77[AI Builder] " + s + " blokk kihagyva (hataron kivul / ervenytelen / foglalt)"
            ), false);
        }

        return placed;
    }

    /** Kis szines particle a lerakott blokk felett */
    private static void spawnPlaceParticle(ServerWorld world, BlockPos pos) {
        try {
            world.spawnParticles(
                ParticleTypes.END_ROD,
                pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                3,    // count
                0.2, 0.1, 0.2,  // spread
                0.05  // speed
            );
        } catch (Exception ignored) {}
    }

    /** ASCII progress bar, pl: [=====-----] */
    public static String buildBar(int pct) {
        int filled = Math.min(10, pct / 10);
        StringBuilder sb = new StringBuilder("\u00a7a[");
        for (int i = 0; i < 10; i++) {
            if (i < filled) sb.append('=');
            else { sb.append("\u00a77-"); }
        }
        sb.append("\u00a7a]");
        return sb.toString();
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
        } catch (Exception ignored) {}
        return state;
    }
}
