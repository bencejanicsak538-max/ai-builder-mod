package hu.bence.aibuilder;

import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lerakott "display" armor stand - lebego szoveggel mutatja az epites halalasat.
 * A jatekos /aidisplay paranccsal rak le egyet, es onnan latja a szazalekot.
 */
public class ProgressDisplayManager {

    // UUID (player) -> armor stand entity UUID
    private static final Map<String, UUID> DISPLAYS = new ConcurrentHashMap<>();

    /** Letrehoz egy invisible, floating text armor stand-ot a megadott pozicioban. */
    public static void spawnDisplay(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        // Ha mar van, toroljuk
        removeDisplay(world, player.getUuidAsString());

        ArmorStandEntity stand = new ArmorStandEntity(world, pos.getX() + 0.5, pos.getY() + 1.8, pos.getZ() + 0.5);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setNoGravity(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(Text.literal("\u00a7e[AI Builder] Varo..."));
        stand.setSilent(true);
        stand.setShowArms(false);
        stand.setHideBasePlate(true);
        stand.setMarker(true); // nem mozog, nem esik le

        world.spawnEntity(stand);
        DISPLAYS.put(player.getUuidAsString(), stand.getUuid());

        player.sendMessage(Text.literal(
            "\u00a7a[AI Builder] Progress display lerakva! Inditsd el az epitest: /ai <prompt>"
        ), false);

        AIBuilderMod.LOGGER.info("[AI Builder] Display armor stand spawned at {} for player {}",
            pos, player.getGameProfile().getName());
    }

    /** Frissiti a lebego szoveget az epites soran (minden blokk utan hivva). */
    public static void updateDisplay(ServerWorld world, String playerUuid, int placed, int total) {
        UUID standUuid = DISPLAYS.get(playerUuid);
        if (standUuid == null) return;

        world.getEntity(standUuid);
        net.minecraft.entity.Entity ent = world.getEntity(standUuid);
        if (!(ent instanceof ArmorStandEntity stand)) return;

        int pct = total > 0 ? (placed * 100 / total) : 0;
        String bar = StructureBuilder.buildBar(pct);
        stand.setCustomName(Text.literal(
            "\u00a7e[AI Builder] " + bar + " " + pct + "% | " + placed + "/" + total
        ));
    }

    /** Vegso allapot: kesz! */
    public static void finishDisplay(ServerWorld world, String playerUuid, int placed, int total) {
        UUID standUuid = DISPLAYS.get(playerUuid);
        if (standUuid == null) return;

        net.minecraft.entity.Entity ent = world.getEntity(standUuid);
        if (!(ent instanceof ArmorStandEntity stand)) return;

        stand.setCustomName(Text.literal(
            "\u00a7a[AI Builder] \u2714 KESZ! " + placed + "/" + total + " blokk | /aiundo visszavonashoz"
        ));
    }

    /** Torli a display-t (epites vegen, vagy /aicancel utan). */
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
