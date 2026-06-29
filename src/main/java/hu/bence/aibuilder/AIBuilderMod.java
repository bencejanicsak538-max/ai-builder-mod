package hu.bence.aibuilder;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AIBuilderMod implements ModInitializer {
    public static final String MOD_ID = "ai-builder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Set<String> ACTIVE_BUILDS = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitialize() {
        ConfigManager.ensureConfig();
        SimpleConfig cfg = ConfigManager.load();
        LOGGER.info("[AI Builder] v2.3 betoltve! Provider: {}, Modell: {}, MaxBlokk: {}, MaxSugar: {}",
            cfg.provider,
            cfg.openrouter != null ? cfg.openrouter.model : "N/A",
            cfg.maxBlocks,
            cfg.maxRadius);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // /ai <prompt>
            dispatcher.register(CommandManager.literal("ai")
                .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                    .executes(ctx -> executeAI(ctx.getSource(), StringArgumentType.getString(ctx, "prompt")))));

            // /aiundo
            dispatcher.register(CommandManager.literal("aiundo")
                .executes(ctx -> AIUndoManager.undoLast(ctx.getSource())));

            // /aicancel
            dispatcher.register(CommandManager.literal("aicancel")
                .executes(ctx -> cancelBuild(ctx.getSource())));

            // /aistatus
            dispatcher.register(CommandManager.literal("aistatus")
                .executes(ctx -> showStatus(ctx.getSource())));

            // /aidisplay - lerak egy progress display-t a labadnal
            dispatcher.register(CommandManager.literal("aidisplay")
                .executes(ctx -> placeDisplay(ctx.getSource())));

            // /aidisplay remove - torli a display-t
            dispatcher.register(CommandManager.literal("aidisplay")
                .then(CommandManager.literal("remove")
                    .executes(ctx -> removeDisplay(ctx.getSource()))));
        });
    }

    private int placeDisplay(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = (ServerWorld) player.getWorld();
            BlockPos pos = player.getBlockPos();
            ProgressDisplayManager.spawnDisplay(world, player, pos);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos rakhat le display-t."));
            return 0;
        }
    }

    private int removeDisplay(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = (ServerWorld) player.getWorld();
            ProgressDisplayManager.removeDisplay(world, player.getUuidAsString());
            source.sendFeedback(() -> Text.literal("\u00a7a[AI Builder] Display eltavolitva."), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Hiba a display eltavolitasakor."));
            return 0;
        }
    }

    private int executeAI(ServerCommandSource source, String prompt) {
        try { source.getPlayerOrThrow(); }
        catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja ezt a parancsot!"));
            return 0;
        }

        String pid;
        try { pid = source.getPlayerOrThrow().getUuidAsString(); }
        catch (Exception e) { return 0; }

        if (ACTIVE_BUILDS.contains(pid)) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Mar folyamatban van egy epites! Leallitashoz: /aicancel"));
            return 0;
        }

        if (prompt.isBlank()) {
            source.sendError(Text.literal("\u00a7c[AI Builder] A prompt nem lehet ures! Pelda: /ai epits egy hazat"));
            return 0;
        }
        if (prompt.length() > 500) {
            source.sendError(Text.literal("\u00a7c[AI Builder] A prompt tul hosszu! Max 500 karakter (" + prompt.length() + ")"));
            return 0;
        }

        SimpleConfig cfg = ConfigManager.load();
        if (cfg.openrouter == null || cfg.openrouter.apiKey == null
            || cfg.openrouter.apiKey.isBlank() || cfg.openrouter.apiKey.contains("PUT_YOUR")) {
            source.sendError(Text.literal(
                "\u00a7c[AI Builder] OpenRouter API kulcs nincs beallitva! " +
                "Nyomd meg B-t, vagy szerkeszd: .minecraft/config/ai-builder.json"));
            return 0;
        }

        ACTIVE_BUILDS.add(pid);
        WandProgressTracker.clear(pid);

        boolean hasDisp = ProgressDisplayManager.hasDisplay(pid);
        source.sendFeedback(() -> Text.literal("\u00a7e[AI Builder] Kereses: " + prompt), false);
        source.sendFeedback(() -> Text.literal(
            "\u00a77[AI Builder] Gondolkodok... | Modell: " +
            (cfg.openrouter != null ? cfg.openrouter.model : "N/A") +
            " | /aicancel a leallitashoz"
        ), false);
        if (!hasDisp) {
            source.sendFeedback(() -> Text.literal(
                "\u00a77[AI Builder] Tipp: /aidisplay paranccsal lerakhatod a progress kijelzot magad ele!"
            ), false);
        }

        new Thread(() -> {
            try {
                String json = AIProviderRouter.requestBuildPlan(prompt, source);
                if (!ACTIVE_BUILDS.contains(pid)) return;

                BuildPlan plan;
                try {
                    plan = BuildPlanParser.parse(json);
                } catch (Exception e) {
                    throw new RuntimeException(
                        "Az AI valasza nem ertheto epitesterv!\nHiba: " + e.getMessage() +
                        "\nAI valasz eleje: " + HttpUtil.truncate(json, 200));
                }

                int total = plan.blocks.size();
                if (total == 0) {
                    source.sendError(Text.literal(
                        "\u00a7c[AI Builder] Az AI 0 blokkal valaszolt! Probald pontosabban."));
                    return;
                }

                long estimatedSec = (total * StructureBuilder.DELAY_PER_BLOCK_MS) / 1000;
                source.sendFeedback(() -> Text.literal(
                    "\u00a7a[AI Builder] Terv kesz! " + total + " blokk | Becsult ido: ~" + estimatedSec + "mp | Epites indul..."
                ), false);

                if (!ACTIVE_BUILDS.contains(pid)) return;

                int placed = StructureBuilder.placePlan(source, plan);
                int skipped = total - placed;

                if (skipped > 0) {
                    source.sendFeedback(() -> Text.literal(
                        "\u00a7a[AI Builder] Kesz! " + placed + "/" + total +
                        " blokk (" + skipped + " kihagyva) | /aiundo"
                    ), false);
                } else {
                    source.sendFeedback(() -> Text.literal(
                        "\u00a7a[AI Builder] \u2714 Kesz! " + placed + " blokk | /aiundo"
                    ), false);
                }

            } catch (Exception e) {
                LOGGER.error("[AI Builder] Epites kozben hiba", e);
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                for (String line : msg.split("\\n")) {
                    if (!line.isBlank()) source.sendError(Text.literal("\u00a7c[AI Builder] " + line));
                }
            } finally {
                ACTIVE_BUILDS.remove(pid);
            }
        }, "AIBuilder-" + pid.substring(0, 8)).start();
        return 1;
    }

    private int cancelBuild(ServerCommandSource source) {
        try {
            String pid = source.getPlayerOrThrow().getUuidAsString();
            if (ACTIVE_BUILDS.remove(pid)) {
                WandProgressTracker.clear(pid);
                source.sendFeedback(() -> Text.literal("\u00a7c[AI Builder] Epites leallitva."), false);
            } else {
                source.sendFeedback(() -> Text.literal("\u00a77[AI Builder] Nincs aktiv epites."), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }

    private int showStatus(ServerCommandSource source) {
        try {
            String pid = source.getPlayerOrThrow().getUuidAsString();
            SimpleConfig cfg = ConfigManager.load();
            boolean active = ACTIVE_BUILDS.contains(pid);
            WandProgressTracker.Progress prog = WandProgressTracker.get(pid);
            String progStr = "";
            if (prog != null) {
                int pct = prog.total > 0 ? (prog.placed * 100 / prog.total) : 0;
                progStr = " | " + StructureBuilder.buildBar(pct) + " " + prog.placed + "/" + prog.total + " (" + pct + "%)";
            }
            final String ps = progStr;
            source.sendFeedback(() -> Text.literal(
                "\u00a7e[AI Builder] Statusz: " + (active ? "\u00a7cEPITES FOLYAMATBAN" : "\u00a7aSZABAD") +
                ps + " | Modell: " + (cfg.openrouter != null ? cfg.openrouter.model : "N/A") +
                " | MaxBlokk: " + cfg.maxBlocks
            ), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }
}
