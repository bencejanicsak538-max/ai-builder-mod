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

    // Players currently running a build - prevents spam/duplicate requests
    public static final Set<String> ACTIVE_BUILDS = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitialize() {
        ConfigManager.ensureConfig();
        LOGGER.info("[AI Builder] v2.0 betoltve!");

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
        // Must be a player
        try { source.getPlayerOrThrow(); }
        catch (Exception e) {
            source.sendError(Text.literal("[AI Builder] Csak jatekos hasznalhatja ezt a parancsot!"));
            return 0;
        }

        String pid;
        try { pid = source.getPlayerOrThrow().getUuidAsString(); }
        catch (Exception e) { return 0; }

        // Flood protection: max 1 active build per player
        if (ACTIVE_BUILDS.contains(pid)) {
            source.sendError(Text.literal("[AI Builder] Mar folyamatban van egy epites! Hasznald /aicancel parancsot."));
            return 0;
        }

        // Input validation
        if (prompt.isBlank() || prompt.length() > 500) {
            source.sendError(Text.literal("[AI Builder] A prompt nem lehet ures, es max 500 karakter!"));
            return 0;
        }

        ACTIVE_BUILDS.add(pid);
        source.sendFeedback(() -> Text.literal("\u00a7e[AI Builder] Kereses: " + prompt), false);
        source.sendFeedback(() -> Text.literal("\u00a77[AI Builder] Gondolkodok... (leallitas: /aicancel)"), false);

        new Thread(() -> {
            try {
                // Step 1: get build plan
                String json = AIProviderRouter.requestBuildPlan(prompt, source);

                // Check if cancelled mid-flight
                if (!ACTIVE_BUILDS.contains(pid)) return;

                // Step 2: parse
                BuildPlan plan = BuildPlanParser.parse(json);
                int total = plan.blocks.size();
                source.sendFeedback(() -> Text.literal("\u00a7a[AI Builder] Terv kész! " + total + " blokk, epitek..."), false);

                // Check again
                if (!ACTIVE_BUILDS.contains(pid)) return;

                // Step 3: place
                int placed = StructureBuilder.placePlan(source, plan);
                source.sendFeedback(() -> Text.literal("\u00a7a[AI Builder] Kesz! Lerakott blokkok: " + placed + "/" + total + " (visszavon: /aiundo)"), false);
            } catch (Exception e) {
                LOGGER.error("[AI Builder] hiba", e);
                String msg = e.getMessage() != null ? e.getMessage() : "Ismeretlen hiba";
                source.sendError(Text.literal("[AI Builder] Hiba: " + msg));
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
            } else {
                source.sendFeedback(() -> Text.literal("\u00a77[AI Builder] Nincs aktiv epites."), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Csak jatekos hasznalhatja."));
            return 0;
        }
    }

    private int showStatus(ServerCommandSource source) {
        try {
            String pid = source.getPlayerOrThrow().getUuidAsString();
            boolean active = ACTIVE_BUILDS.contains(pid);
            source.sendFeedback(() -> Text.literal(
                active ? "\u00a7e[AI Builder] Aktiv epites folyamatban..." : "\u00a7a[AI Builder] Nincs aktiv epites."
            ), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Csak jatekos hasznalhatja."));
            return 0;
        }
    }
}
