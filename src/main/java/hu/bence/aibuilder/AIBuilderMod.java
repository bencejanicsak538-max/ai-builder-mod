package hu.bence.aibuilder;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
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
        LOGGER.info("[AI Builder] v2.1 betoltve! Provider: {}, Modell: {}, MaxBlokk: {}, MaxSugar: {}",
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
        });
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
            source.sendError(Text.literal("\u00a7c[AI Builder] A prompt tul hosszu! Max 500 karakter (jelenlegi: " + prompt.length() + ")"));
            return 0;
        }

        // Config ellenorzese elore, hogy ne kelljen varni az AI valaszara
        SimpleConfig cfg = ConfigManager.load();
        if (cfg.openrouter == null || cfg.openrouter.apiKey == null
            || cfg.openrouter.apiKey.isBlank() || cfg.openrouter.apiKey.contains("PUT_YOUR")) {
            source.sendError(Text.literal(
                "\u00a7c[AI Builder] OpenRouter API kulcs nincs beallitva! " +
                "Nyomd meg B-t, vagy szerkeszd: .minecraft/config/ai-builder.json"));
            return 0;
        }

        ACTIVE_BUILDS.add(pid);
        source.sendFeedback(() -> Text.literal("\u00a7e[AI Builder] Kereses: " + prompt), false);
        source.sendFeedback(() -> Text.literal(
            "\u00a77[AI Builder] Gondolkodok... | Provider: openrouter | Modell: " +
            (cfg.openrouter != null ? cfg.openrouter.model : "N/A") +
            " | Leallitas: /aicancel"
        ), false);

        new Thread(() -> {
            try {
                // 1. AI terv lekerese
                String json = AIProviderRouter.requestBuildPlan(prompt, source);

                if (!ACTIVE_BUILDS.contains(pid)) return;

                // 2. Parse
                BuildPlan plan;
                try {
                    plan = BuildPlanParser.parse(json);
                } catch (Exception e) {
                    throw new RuntimeException(
                        "Az AI valasza nem ertheto epitestarv!\n" +
                        "Hiba: " + e.getMessage() + "\n" +
                        "AI valasz eleje: " + HttpUtil.truncate(json, 200));
                }

                int total = plan.blocks.size();
                if (total == 0) {
                    source.sendError(Text.literal(
                        "\u00a7c[AI Builder] Az AI 0 blokkal valaszolt! " +
                        "Probald meg pontosabban leirni az epitmeny."));
                    return;
                }

                source.sendFeedback(() -> Text.literal(
                    "\u00a7a[AI Builder] Terv kesz! " + total + " blokk | Epitek..."
                ), false);

                if (!ACTIVE_BUILDS.contains(pid)) return;

                // 3. Epites
                int placed = StructureBuilder.placePlan(source, plan);
                int skipped = total - placed;

                if (skipped > 0) {
                    source.sendFeedback(() -> Text.literal(
                        "\u00a7a[AI Builder] Kesz! Lerakott: " + placed + "/" + total +
                        " blokk (" + skipped + " kihagyva - mar foglalt vagy hataron kivul) | Visszavon: /aiundo"
                    ), false);
                } else {
                    source.sendFeedback(() -> Text.literal(
                        "\u00a7a[AI Builder] Kesz! " + placed + " blokk lerakva. | Visszavon: /aiundo"
                    ), false);
                }

            } catch (Exception e) {
                LOGGER.error("[AI Builder] Epites kozben hiba tortent", e);
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                // Tobb soros hibauzenet a chaten
                String[] lines = msg.split("\\n");
                for (String line : lines) {
                    if (!line.isBlank()) {
                        source.sendError(Text.literal("\u00a7c[AI Builder] " + line));
                    }
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
                source.sendFeedback(() -> Text.literal("\u00a7c[AI Builder] Epites leallitva."), false);
                LOGGER.info("[AI Builder] Epites megszakitva: {}", pid);
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
            source.sendFeedback(() -> Text.literal(
                "\u00a7e[AI Builder] Statusz: " + (active ? "\u00a7cEPITES FOLYAMATBAN" : "\u00a7aSZABAD") +
                " | Provider: openrouter" +
                " | Modell: " + (cfg.openrouter != null ? cfg.openrouter.model : "N/A") +
                " | MaxBlokk: " + cfg.maxBlocks
            ), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }
}
