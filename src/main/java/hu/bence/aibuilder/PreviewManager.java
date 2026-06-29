package hu.bence.aibuilder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Epitesi elonezet (preview) - szellemblokkok mutatja az epitmenyt 
 * mielott tenyleges blokkokat raknank le.
 * Villogo stained_glass blokkokkal jeloli a helyeket.
 */
public class PreviewManager {

    private static final Map<String, List<BlockPos>> PREVIEW_POSITIONS = new ConcurrentHashMap<>();
    private static final Map<String, List<BlockState>> PREVIEW_ORIGINALS = new ConcurrentHashMap<>();

    /**
     * Megmutatja a preview-t: lecsereli a blokkokat stained_glass-ra 8 masodpercre,
     * majd visszaallitja oket.
     */
    public static void showPreview(ServerWorld world, ServerPlayerEntity player, BuildPlan plan) {
        String uuid = player.getUuidAsString();
        clearPreview(world, uuid);

        BlockPos origin = player.getBlockPos();
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> originals = new ArrayList<>();

        SimpleConfig cfg = ConfigManager.load();
        int count = 0;

        for (BuildPlan.BlockEntry b : plan.blocks) {
            if (count >= 200) break; // preview max 200 blokk
            if (b.block == null || b.block.isBlank()) continue;
            if (Math.abs(b.dx) > cfg.maxRadius || Math.abs(b.dy) > cfg.maxRadius || Math.abs(b.dz) > cfg.maxRadius) continue;
            BlockPos pos = origin.add(b.dx, b.dy, b.dz);
            positions.add(pos);
            originals.add(world.getBlockState(pos));
            count++;
        }

        PREVIEW_POSITIONS.put(uuid, positions);
        PREVIEW_ORIGINALS.put(uuid, originals);

        // Szellemblokkok lerakasa
        world.getServer().execute(() -> {
            BlockState glass = Blocks.LIME_STAINED_GLASS.getDefaultState();
            for (BlockPos pos : positions) {
                world.setBlockState(pos, glass, 3);
            }
        });

        // 8 masodperc utan visszaallitas
        new Thread(() -> {
            try { Thread.sleep(8000); } catch (InterruptedException ignored) {}
            clearPreview(world, uuid);
            world.getServer().execute(() ->
                player.sendMessage(
                    net.minecraft.text.Text.literal("\u00a77[AI Builder] Preview lejart. /ai <prompt> az epiteshez."), false));
        }, "AIBuilder-Preview-" + uuid.substring(0, 8)).start();

        int total = positions.size();
        player.sendMessage(net.minecraft.text.Text.literal(
            "\u00a7a[AI Builder] Preview: " + total + " blokk | 8mp utan eltunt | /ai " + plan.blocks.size() + " blokk lesz"
        ), false);
    }

    public static void clearPreview(ServerWorld world, String playerUuid) {
        List<BlockPos> positions = PREVIEW_POSITIONS.remove(playerUuid);
        List<BlockState> originals = PREVIEW_ORIGINALS.remove(playerUuid);
        if (positions == null || originals == null) return;
        world.getServer().execute(() -> {
            for (int i = 0; i < positions.size(); i++) {
                try {
                    BlockState cur = world.getBlockState(positions.get(i));
                    // Csak akkor allitjuk vissza ha meg mindig glass
                    if (cur.getBlock() == Blocks.LIME_STAINED_GLASS) {
                        world.setBlockState(positions.get(i), originals.get(i), 3);
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    public static boolean hasPreview(String playerUuid) {
        return PREVIEW_POSITIONS.containsKey(playerUuid);
    }
}
