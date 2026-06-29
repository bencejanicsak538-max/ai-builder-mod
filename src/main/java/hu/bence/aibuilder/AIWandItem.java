package hu.bence.aibuilder;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * AI Wand - lehajitod a foldre es latod felette az epites halalasat.
 * Jobb klikk: megmutatja az aktualis haladas statuuszt.
 */
public class AIWandItem extends Item {

    public static final String ID = "ai_wand";

    public AIWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            String uuid = player.getUuidAsString();
            WandProgressTracker.Progress prog = WandProgressTracker.get(uuid);

            if (prog == null) {
                player.sendMessage(Text.literal(
                    "\u00a7e[AI Wand] Nincs aktiv epites. Hasznald: /ai <prompt>"
                ), true);  // action bar
            } else if (prog.done) {
                int pct = prog.total > 0 ? (prog.placed * 100 / prog.total) : 100;
                player.sendMessage(Text.literal(
                    "\u00a7a[AI Wand] Epites kesz! " + prog.placed + "/" + prog.total +
                    " blokk (" + pct + "%) | /aiundo a visszavonashoz"
                ), true);
            } else {
                int pct = prog.total > 0 ? (prog.placed * 100 / prog.total) : 0;
                player.sendMessage(Text.literal(
                    "\u00a7e[AI Wand] Epites: " + StructureBuilder.buildBar(pct) +
                    " " + prog.placed + "/" + prog.total + " (" + pct + "%) | /aicancel"
                ), true);
            }
        }
        return TypedActionResult.success(stack);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;  // enchant-szeru csillanas, hogy kitunjon
    }
}
