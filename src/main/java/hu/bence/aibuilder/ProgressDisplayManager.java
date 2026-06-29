package hu.bence.aibuilder;

import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProgressDisplayManager {

    // FIX: ConcurrentHashMap - thread-safe
    private static final Map<String, UUID> DISPLAYS = new ConcurrentHashMap<>();

    public static void spawnDisplay(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        removeDisplay(world, player.getUuidAsString());

        ArmorStandEntity stand = new ArmorStandEntity(world,
            pos.getX() + 0.5, pos.getY() + 1.8, pos.getZ() + 0.5);

        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setNoGravity(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(Text.literal("\u00a7e[AI Builder] Varo az epitesre..."));
        stand.setSilent(true);
        stand.setShowArms(false);
        stand.setHideBasePlate(true);

        // setMarker() private -> NBT-n keresztul
        NbtCompound nbt = stand.writeNbt(new NbtCompound());
        nbt.putBoolean("Marker", true);
        stand.readNbt(nbt);

        world.spawnEntity(stand);
        DISPLAYS.put(player.getUuidAsString(), stand.getUuid());

        player.sendMessage(Text.literal(
            "\u00a7a[AI Builder] Progress display lerakva! Inditsd el: /ai <prompt> | Eltavolit: /aidisplay remove"
        ), false);
    }

    public static void updateDisplay(ServerWorld world, String playerUuid, int placed, int total) {
        UUID standUuid = DISPLAYS.get(playerUuid);
        if (standUuid == null) return;
        net.minecraft.entity.Entity ent = world.getEntity(standUuid);
        if (!(ent instanceof ArmorStandEntity stand)) return;

        int pct = total > 0 ? (placed * 100 / total) : 0;
        String bar = StructureBuilder.buildBar(pct);
        stand.setCustomName(Text.literal(
            "\u00a7e[AI Builder] " + bar + " " + pct + "% | " + placed + "/" + total
        ));
    }

    public static void finishDisplay(ServerWorld world, String playerUuid, int placed, int total) {
        UUID standUuid = DISPLAYS.get(playerUuid);
        if (standUuid == null) return;
        net.minecraft.entity.Entity ent = world.getEntity(standUuid);
        if (!(ent instanceof ArmorStandEntity stand)) return;

        // FIX: cancel eset (placed == -1) kulonbozo uzenet
        if (placed < 0) {
            stand.setCustomName(Text.literal(
                "\u00a7c[AI Builder] \u2716 Leallitva | /ai <prompt> uj epiteshez"
            ));
        } else {
            stand.setCustomName(Text.literal(
                "\u00a7a[AI Builder] \u2714 KESZ! " + placed + "/" + total + " blokk | /aiundo"
            ));
        }
    }

    public static void removeDisplay(ServerWorld world, String playerUuid) {
        UUID standUuid = DISPLAYS.remove(playerUuid);
        if (standUuid == null) return;
        net.minecraft.entity.Entity ent = world.getEntity(standUuid);
        if (ent != null) ent.discard();
    }

    public static boolean hasDisplay(String playerUuid) {
        return DISPLAYS.containsKey(playerUuid);
    }
}
