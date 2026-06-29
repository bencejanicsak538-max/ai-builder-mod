package hu.bence.aibuilder;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gyujti a jatekos kornyezetere vonatkozo informaciokat
 * hogy az AI pontosabb kontextust kapjon az epiteshez.
 */
public class ContextCollector {

    public static class Context {
        public String biomeId = "unknown";
        public String timeOfDay = "day";
        public boolean isRaining = false;
        public String nearbyBlocks = "";
        public String groundBlock = "minecraft:grass_block";
        public int y = 64;
        public String dimension = "overworld";

        public String toPromptString() {
            return String.format(
                "[CONTEXT: biome=%s, time=%s, raining=%b, ground=%s, y=%d, dimension=%s, nearby_blocks=%s]",
                biomeId, timeOfDay, isRaining, groundBlock, y, dimension, nearbyBlocks
            );
        }
    }

    public static Context collect(ServerPlayerEntity player) {
        Context ctx = new Context();
        try {
            ServerWorld world = (ServerWorld) player.getWorld();
            BlockPos pos = player.getBlockPos();
            ctx.y = pos.getY();

            // Biome
            try {
                RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
                String biomeKey = biomeEntry.getKey()
                    .map(k -> k.getValue().getPath())
                    .orElse("unknown");
                ctx.biomeId = biomeKey;
            } catch (Exception ignored) {}

            // Napszak
            long time = world.getTimeOfDay() % 24000;
            if (time < 1000 || time > 23000) ctx.timeOfDay = "sunrise/sunset";
            else if (time < 13000) ctx.timeOfDay = "day";
            else ctx.timeOfDay = "night";

            // Eso
            ctx.isRaining = world.isRaining();

            // Dimenzio
            String dimKey = world.getRegistryKey().getValue().getPath();
            ctx.dimension = dimKey;

            // Labak alatti blokk
            BlockPos groundPos = pos.down();
            Block groundBlock = world.getBlockState(groundPos).getBlock();
            ctx.groundBlock = Registries.BLOCK.getId(groundBlock).toString();

            // Kozeli blokkok (3x3 area lentrol)
            Map<String, Integer> blockCounts = new LinkedHashMap<>();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        Block b = world.getBlockState(pos.add(dx, dy, dz)).getBlock();
                        if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) continue;
                        String bid = Registries.BLOCK.getId(b).toString();
                        blockCounts.merge(bid, 1, Integer::sum);
                    }
                }
            }
            // Top 5 leggyakoribb blokk
            StringBuilder sb = new StringBuilder();
            blockCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> sb.append(e.getKey().replace("minecraft:", "")).append("(").append(e.getValue()).append("),"));
            if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
            ctx.nearbyBlocks = sb.toString();

        } catch (Exception e) {
            AIBuilderMod.LOGGER.warn("[AI Builder] ContextCollector hiba: {}", e.getMessage());
        }
        return ctx;
    }
}
