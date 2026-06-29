package hu.bence.aibuilder;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AIBuilderMod implements ModInitializer {
    public static final String MOD_ID = "ai-builder";
    public static final String VERSION = "3.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Set<String> ACTIVE_BUILDS = ConcurrentHashMap.newKeySet();

    // Separator vonal chat uzenetekhez
    private static final String SEP = "\u00a78\u00a7m--------------------\u00a7r";

    @Override
    public void onInitialize() {
        ConfigManager.ensureConfig();
        SimpleConfig cfg = ConfigManager.load();
        LOGGER.info("[AI Builder] v{} betoltve! Provider: {}, Modell: {}, MaxBlokk: {}, MaxSugar: {}",
            VERSION, cfg.provider,
            cfg.openrouter != null ? cfg.openrouter.model : "N/A",
            cfg.maxBlocks, cfg.maxRadius);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // --- /ai <prompt> ---
            dispatcher.register(CommandManager.literal("ai")
                .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                    .executes(ctx -> executeAI(ctx.getSource(), StringArgumentType.getString(ctx, "prompt")))));

            // --- /aiundo [list] ---
            dispatcher.register(CommandManager.literal("aiundo")
                .executes(ctx -> AIUndoManager.undoLast(ctx.getSource()))
                .then(CommandManager.literal("list")
                    .executes(ctx -> showUndoList(ctx.getSource()))));

            // --- /aicancel ---
            dispatcher.register(CommandManager.literal("aicancel")
                .executes(ctx -> cancelBuild(ctx.getSource())));

            // --- /aistatus ---
            dispatcher.register(CommandManager.literal("aistatus")
                .executes(ctx -> showStatus(ctx.getSource())));

            // --- /aiversion ---
            dispatcher.register(CommandManager.literal("aiversion")
                .executes(ctx -> showVersion(ctx.getSource())));

            // --- /aihelp ---
            dispatcher.register(CommandManager.literal("aihelp")
                .executes(ctx -> showHelp(ctx.getSource())));

            // --- /aihistory [ujraepites <szam>] ---
            dispatcher.register(CommandManager.literal("aihistory")
                .executes(ctx -> showHistory(ctx.getSource()))
                .then(CommandManager.literal("rebuild")
                    .then(CommandManager.argument("index", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> rebuildHistory(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index"))))));

            // --- /aistyle [medieval|modern|fantasy|nature|clear] ---
            dispatcher.register(CommandManager.literal("aistyle")
                .executes(ctx -> showStyle(ctx.getSource()))
                .then(CommandManager.argument("style", StringArgumentType.word())
                    .executes(ctx -> setStyle(ctx.getSource(), StringArgumentType.getString(ctx, "style")))));

            // --- /aidebug ---
            dispatcher.register(CommandManager.literal("aidebug")
                .executes(ctx -> showDebug(ctx.getSource())));

            // --- /ailog ---
            dispatcher.register(CommandManager.literal("ailog")
                .executes(ctx -> saveLog(ctx.getSource())));

            // --- /aidisplay [remove] ---
            dispatcher.register(CommandManager.literal("aidisplay")
                .executes(ctx -> placeDisplay(ctx.getSource()))
                .then(CommandManager.literal("remove")
                    .executes(ctx -> removeDisplay(ctx.getSource()))));
        });
    }

    // ============================================================
    //  /ai <prompt>
    // ============================================================
    private int executeAI(ServerCommandSource source, String prompt) {
        try { source.getPlayerOrThrow(); }
        catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja!"));
            return 0;
        }
        String pid;
        try { pid = source.getPlayerOrThrow().getUuidAsString(); } catch (Exception e) { return 0; }

        if (ACTIVE_BUILDS.contains(pid)) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Mar folyamatban van egy epites! /aicancel a megszakitashoz."));
            return 0;
        }
        if (prompt.isBlank()) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Ures prompt! Pl: /ai epits egy kozepkori kastely tornyot"));
            return 0;
        }
        if (prompt.length() > 500) {
            source.sendError(Text.literal("\u00a7c[AI Builder] A prompt max 500 karakter lehet! (jelenleg: " + prompt.length() + ")"));
            return 0;
        }

        SimpleConfig cfg = ConfigManager.load();
        if (cfg.openrouter == null || cfg.openrouter.apiKey == null
                || cfg.openrouter.apiKey.isBlank() || cfg.openrouter.apiKey.contains("PUT_YOUR")) {
            source.sendError(Text.literal("\u00a7c[AI Builder] API kulcs nincs beallitva! Nyomd meg B-t a config menuhoz."));
            return 0;
        }

        ACTIVE_BUILDS.add(pid);
        WandProgressTracker.clear(pid);
        BuildStats.start(pid, prompt, cfg.openrouter != null ? cfg.openrouter.model : "N/A");

        source.sendFeedback(() -> Text.literal(SEP), false);
        source.sendFeedback(() -> Text.literal("\u00a7b\u00a7l[AI Builder v" + VERSION + "] \u00a7eKereses: \u00a7f" + prompt), false);
        source.sendFeedback(() -> Text.literal("\u00a77Modell: " + (cfg.openrouter != null ? cfg.openrouter.model : "N/A") + " | /aicancel a leallitashoz"), false);

        String style = StyleManager.getStyle(pid, cfg);
        if (!style.equals("none") && !style.equals("")) {
            final String fs = style;
            source.sendFeedback(() -> Text.literal("\u00a77Stilus: \u00a7d" + fs + " \u00a77| /aistyle clear a torleshezhez"), false);
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
                    source.sendError(Text.literal("\u00a7c[AI Builder] Az AI 0 blokkal valaszolt! Probald pontosabban leirni."));
                    return;
                }

                long estimatedSec = (total * StructureBuilder.DELAY_PER_BLOCK_MS) / 1000;
                source.sendFeedback(() -> Text.literal(
                    "\u00a7a[AI Builder] Terv kesz! \u00a7f" + total + " blokk\u00a7a | Becsult ido: ~" + estimatedSec + "mp"
                ), false);

                if (!ACTIVE_BUILDS.contains(pid)) return;
                int placed = StructureBuilder.placePlan(source, plan);

                // Mentsuk el a torteneti adatokba
                try {
                    BuildHistory.add(pid, prompt, placed, total);
                } catch (Exception ignored) {}

                // Stats befejezese
                BuildStats.Stats stats = BuildStats.get(pid);
                if (stats != null) {
                    stats.totalBlocks = total;
                    stats.placedBlocks = placed;
                    stats.skippedBlocks = total - placed;
                    stats.finishBuild();
                    try {
                        String pname = source.getPlayerOrThrow().getName().getString();
                        DebugLogger.log(stats, pname);
                    } catch (Exception ignored) {}
                }

                final int fp = placed;
                final int ft = total;
                source.sendFeedback(() -> Text.literal(
                    "\u00a7a[AI Builder] \u2714 Kesz! " + fp + "/" + ft + " blokk lerakva | /aiundo a visszavonashoz"
                ), false);
                source.sendFeedback(() -> Text.literal(SEP), false);

            } catch (Exception e) {
                LOGGER.error("[AI Builder] Epites kozben hiba", e);
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                for (String line : msg.split("\\n")) {
                    if (!line.isBlank()) source.sendError(Text.literal("\u00a7c[AI Builder] " + line));
                }
                try {
                    ServerPlayerEntity player = source.getPlayerOrThrow();
                    ServerWorld world = (ServerWorld) player.getWorld();
                    ProgressDisplayManager.finishDisplay(world, pid, 0, 0);
                } catch (Exception ignored) {}
            } finally {
                ACTIVE_BUILDS.remove(pid);
                WandProgressTracker.clear(pid);
            }
        }, "AIBuilder-" + pid.substring(0, 8)).start();
        return 1;
    }

    // ============================================================
    //  /aiversion
    // ============================================================
    private int showVersion(ServerCommandSource source) {
        SimpleConfig cfg = ConfigManager.load();
        source.sendFeedback(() -> Text.literal(SEP), false);
        source.sendFeedback(() -> Text.literal("\u00a7b\u00a7l  AI Builder v" + VERSION), false);
        source.sendFeedback(() -> Text.literal("\u00a77  Provider: \u00a7f" + cfg.provider), false);
        source.sendFeedback(() -> Text.literal("\u00a77  Modell:   \u00a7f" + (cfg.openrouter != null ? cfg.openrouter.model : "N/A")), false);
        source.sendFeedback(() -> Text.literal("\u00a77  MaxBlokk: \u00a7f" + cfg.maxBlocks + " \u00a77| MaxSugar: \u00a7f" + cfg.maxRadius), false);
        source.sendFeedback(() -> Text.literal("\u00a77  Minecraft: \u00a7f1.20.1 \u00a77| Fabric Loom: \u00a7f1.6.5"), false);
        source.sendFeedback(() -> Text.literal("\u00a77  /aihelp a parancsok listajahaz"), false);
        source.sendFeedback(() -> Text.literal(SEP), false);
        return 1;
    }

    // ============================================================
    //  /aihelp
    // ============================================================
    private int showHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal(SEP), false);
        source.sendFeedback(() -> Text.literal("\u00a7b\u00a7l  AI Builder v" + VERSION + " \u00a77- Parancsok"), false);
        source.sendFeedback(() -> Text.literal(SEP), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/ai \u00a7f<prompt>  \u00a77- Epitesi kerelem az AI-nak"), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/aiundo      \u00a77- Utolso epites visszavonasa"), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/aiundo list \u00a77- Visszavonhato epitesek listaja"), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/aicancel    \u00a77- Folyamatban levo epites leallitasa"), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/aistatus    \u00a77- Jelenlegi epites allapota"), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/aihistory   \u00a77- Utolso 10 epites listaja"), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/aihistory rebuild <1-10>  \u00a77- Ujraepites history-bol"), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/aistyle     \u00a77- Aktualis epitesei stilus megtekintese"), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/aistyle \u00a7f<medieval|modern|fantasy|nature|clear>"), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/aidebug     \u00a77- Debug statisztikak (API latency, blokkok)"), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/ailog       \u00a77- Utolso AI valasz mentese fajlba"), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/aidisplay   \u00a77- Progress kijelzo lerakasa"), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/aidisplay remove  \u00a77- Kijelzo eltavolitasa"), false);
        source.sendFeedback(() -> Text.literal("\u00a7e/aiversion   \u00a77- Verzio es konfiguracioinfo"), false);
        source.sendFeedback(() -> Text.literal(SEP), false);
        return 1;
    }

    // ============================================================
    //  /aihistory [rebuild <n>]
    // ============================================================
    private int showHistory(ServerCommandSource source) {
        try {
            String pid = source.getPlayerOrThrow().getUuidAsString();
            List<BuildHistory.Entry> entries = BuildHistory.get(pid);
            source.sendFeedback(() -> Text.literal(SEP), false);
            source.sendFeedback(() -> Text.literal("\u00a7b\u00a7l  AI Builder v" + VERSION + " \u00a77- Epit\u00e9si elozmenyek"), false);
            if (entries.isEmpty()) {
                source.sendFeedback(() -> Text.literal("\u00a77  Meg nincs epiteselozmenyed."), false);
            } else {
                for (int i = 0; i < entries.size(); i++) {
                    final int idx = i + 1;
                    BuildHistory.Entry e = entries.get(i);
                    source.sendFeedback(() -> Text.literal(
                        "\u00a7e  #" + idx + " \u00a7f" + e.prompt +
                        " \u00a77(" + e.placed + " blokk, " + e.timestamp + ") " +
                        "\u00a7a[rebuild: /aihistory rebuild " + idx + "]"
                    ), false);
                }
            }
            source.sendFeedback(() -> Text.literal(SEP), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }

    private int rebuildHistory(ServerCommandSource source, int index) {
        try {
            String pid = source.getPlayerOrThrow().getUuidAsString();
            List<BuildHistory.Entry> entries = BuildHistory.get(pid);
            if (index < 1 || index > entries.size()) {
                source.sendError(Text.literal("\u00a7c[AI Builder] Nincs " + index + ". epites az elozmenyek kozott."));
                return 0;
            }
            String prompt = entries.get(index - 1).prompt;
            source.sendFeedback(() -> Text.literal("\u00a7e[AI Builder] Ujraepites: \u00a7f" + prompt), false);
            return executeAI(source, prompt);
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }

    // ============================================================
    //  /aistyle
    // ============================================================
    private int showStyle(ServerCommandSource source) {
        try {
            String pid = source.getPlayerOrThrow().getUuidAsString();
            SimpleConfig cfg = ConfigManager.load();
            String style = StyleManager.getStyle(pid, cfg);
            source.sendFeedback(() -> Text.literal(SEP), false);
            source.sendFeedback(() -> Text.literal("\u00a7b\u00a7l  AI Builder v" + VERSION + " \u00a77- Epitesei stilus"), false);
            source.sendFeedback(() -> Text.literal("\u00a77  Aktualis stilus: \u00a7d" + style), false);
            source.sendFeedback(() -> Text.literal("\u00a77  Valaszthatoak: \u00a7fmedieval, modern, fantasy, nature, clear"), false);
            source.sendFeedback(() -> Text.literal("\u00a77  Hasznalat: /aistyle medieval"), false);
            source.sendFeedback(() -> Text.literal(SEP), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }

    private int setStyle(ServerCommandSource source, String style) {
        try {
            String pid = source.getPlayerOrThrow().getUuidAsString();
            String[] valid = {"medieval", "modern", "fantasy", "nature", "clear", "none"};
            boolean ok = false;
            for (String v : valid) if (v.equalsIgnoreCase(style)) { ok = true; break; }
            if (!ok) {
                source.sendError(Text.literal("\u00a7c[AI Builder] Ismeretlen stilus! Valaszthatoak: medieval, modern, fantasy, nature, clear"));
                return 0;
            }
            StyleManager.setStyle(pid, style.toLowerCase());
            if (style.equalsIgnoreCase("clear") || style.equalsIgnoreCase("none")) {
                source.sendFeedback(() -> Text.literal("\u00a7a[AI Builder v" + VERSION + "] Stilus torolve."), false);
            } else {
                final String fs = style.toLowerCase();
                source.sendFeedback(() -> Text.literal("\u00a7a[AI Builder v" + VERSION + "] Stilus beallitva: \u00a7d" + fs), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }

    // ============================================================
    //  /aidebug
    // ============================================================
    private int showDebug(ServerCommandSource source) {
        try {
            String pid = source.getPlayerOrThrow().getUuidAsString();
            SimpleConfig cfg = ConfigManager.load();
            source.sendFeedback(() -> Text.literal(SEP), false);
            source.sendFeedback(() -> Text.literal("\u00a7b\u00a7l  AI Builder v" + VERSION + " \u00a77- Debug panel"), false);
            source.sendFeedback(() -> Text.literal("\u00a77  Provider:  \u00a7f" + cfg.provider), false);
            source.sendFeedback(() -> Text.literal("\u00a77  Modell:    \u00a7f" + (cfg.openrouter != null ? cfg.openrouter.model : "N/A")), false);
            source.sendFeedback(() -> Text.literal("\u00a77  MaxBlokk:  \u00a7f" + cfg.maxBlocks + " \u00a77| MaxSugar: \u00a7f" + cfg.maxRadius), false);
            source.sendFeedback(() -> Text.literal("\u00a77  Aktiv:     \u00a7f" + (ACTIVE_BUILDS.contains(pid) ? "\u00a7cEPIT" : "\u00a7aSZABAD")), false);

            List<DebugLogger.LogEntry> logs = DebugLogger.getRecentLogs(5);
            if (logs.isEmpty()) {
                source.sendFeedback(() -> Text.literal("\u00a77  Log: Meg nem volt epites ebben a munkamenetben."), false);
            } else {
                source.sendFeedback(() -> Text.literal("\u00a77  --- Utolso " + logs.size() + " epites ---"), false);
                for (DebugLogger.LogEntry l : logs) {
                    source.sendFeedback(() -> Text.literal(
                        "\u00a7e  " + l.timestamp + " \u00a7f" + l.prompt +
                        "\u00a77  =>  " + l.placedBlocks + "/" + l.totalBlocks +
                        " blokk | API: " + l.apiLatencyMs + "ms" +
                        (l.retries > 0 ? " | Ujraproba: " + l.retries : "") +
                        (l.errorMsg != null ? " | \u00a7cHIBA: " + l.errorMsg : "")
                    ), false);
                }
            }

            WandProgressTracker.Progress prog = WandProgressTracker.get(pid);
            if (prog != null) {
                int pct = prog.total > 0 ? (prog.placed * 100 / prog.total) : 0;
                source.sendFeedback(() -> Text.literal(
                    "\u00a77  Progress: " + StructureBuilder.buildBar(pct) + " " + prog.placed + "/" + prog.total + " (" + pct + "%)"
                ), false);
            }
            source.sendFeedback(() -> Text.literal("\u00a77  /ailog a raw JSON menteshez fajlba"), false);
            source.sendFeedback(() -> Text.literal(SEP), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }

    // ============================================================
    //  /ailog
    // ============================================================
    private int saveLog(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            String pid = player.getUuidAsString();
            String pname = player.getName().getString();
            boolean ok = DebugLogger.saveToFile(player.getServer(), pid, pname);
            if (ok) {
                source.sendFeedback(() -> Text.literal(
                    "\u00a7a[AI Builder v" + VERSION + "] Raw JSON log mentve! -> config/ai-builder-logs/"
                ), false);
            } else {
                source.sendError(Text.literal("\u00a7c[AI Builder] Nincs mentendo log! Elobb epitsd le az AI-val."));
            }
            return ok ? 1 : 0;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }

    // ============================================================
    //  /aiundo list
    // ============================================================
    private int showUndoList(ServerCommandSource source) {
        try {
            String pid = source.getPlayerOrThrow().getUuidAsString();
            int count = AIUndoManager.getUndoCount(pid);
            source.sendFeedback(() -> Text.literal(SEP), false);
            source.sendFeedback(() -> Text.literal("\u00a7b\u00a7l  AI Builder v" + VERSION + " \u00a77- Visszavonhato epitesek"), false);
            if (count == 0) {
                source.sendFeedback(() -> Text.literal("\u00a77  Nincs visszavonhato epites."), false);
            } else {
                source.sendFeedback(() -> Text.literal("\u00a77  Visszavonhato szintek: \u00a7f" + count + " \u00a77(max 5)"), false);
                source.sendFeedback(() -> Text.literal("\u00a77  /aiundo - legutolso epites torlese"), false);
            }
            source.sendFeedback(() -> Text.literal(SEP), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }

    // ============================================================
    //  /aistatus
    // ============================================================
    private int showStatus(ServerCommandSource source) {
        try {
            String pid = source.getPlayerOrThrow().getUuidAsString();
            SimpleConfig cfg = ConfigManager.load();
            boolean active = ACTIVE_BUILDS.contains(pid);
            WandProgressTracker.Progress prog = WandProgressTracker.get(pid);
            String progStr = "";
            if (prog != null) {
                int pct = prog.total > 0 ? (prog.placed * 100 / prog.total) : 0;
                progStr = "\n  " + StructureBuilder.buildBar(pct) + " " + prog.placed + "/" + prog.total + " (" + pct + "%)";
            }
            final String ps = progStr;
            source.sendFeedback(() -> Text.literal(SEP), false);
            source.sendFeedback(() -> Text.literal("\u00a7b\u00a7l  AI Builder v" + VERSION + " \u00a77- Statusz"), false);
            source.sendFeedback(() -> Text.literal("\u00a77  Allapot: " + (active ? "\u00a7cEPITES FOLYAMATBAN" : "\u00a7aSZABAD") + ps), false);
            source.sendFeedback(() -> Text.literal("\u00a77  Modell:  \u00a7f" + (cfg.openrouter != null ? cfg.openrouter.model : "N/A")), false);
            source.sendFeedback(() -> Text.literal("\u00a77  MaxBlokk: \u00a7f" + cfg.maxBlocks), false);
            source.sendFeedback(() -> Text.literal(SEP), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }

    // ============================================================
    //  /aicancel
    // ============================================================
    private int cancelBuild(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            String pid = player.getUuidAsString();
            if (ACTIVE_BUILDS.remove(pid)) {
                WandProgressTracker.clear(pid);
                ServerWorld world = (ServerWorld) player.getWorld();
                ProgressDisplayManager.finishDisplay(world, pid, -1, -1);
                source.sendFeedback(() -> Text.literal("\u00a7c[AI Builder v" + VERSION + "] Epites leallitva."), false);
            } else {
                source.sendFeedback(() -> Text.literal("\u00a77[AI Builder v" + VERSION + "] Nincs aktiv epites."), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Csak jatekos hasznalhatja."));
            return 0;
        }
    }

    // ============================================================
    //  /aidisplay [remove]
    // ============================================================
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
            source.sendFeedback(() -> Text.literal("\u00a7a[AI Builder v" + VERSION + "] Display eltavolitva."), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("\u00a7c[AI Builder] Hiba a display eltavolitasakor."));
            return 0;
        }
    }
}
