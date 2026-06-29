package hu.bence.aibuilder;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIBuilderMod implements ModInitializer {
    public static final String MOD_ID = "ai-builder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ConfigManager.ensureConfig();
        LOGGER.info("AI Builder Mod betoltve!");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("ai")
                .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                    .executes(ctx -> executeAI(ctx.getSource(), StringArgumentType.getString(ctx, "prompt")))));

            dispatcher.register(CommandManager.literal("aiundo")
                .executes(ctx -> AIUndoManager.undoLast(ctx.getSource())));
        });
    }

    private int executeAI(ServerCommandSource source, String prompt) {
        source.sendFeedback(() -> Text.literal("[AI Builder] Kereses: " + prompt), false);
        new Thread(() -> {
            try {
                String json = AIProviderRouter.requestBuildPlan(prompt, source);
                BuildPlan plan = BuildPlanParser.parse(json);
                int placed = StructureBuilder.placePlan(source, plan);
                source.sendFeedback(() -> Text.literal("[AI Builder] Kesz! Lerakott blokkok: " + placed), false);
            } catch (Exception e) {
                LOGGER.error("AI Builder hiba", e);
                source.sendError(Text.literal("[AI Builder] Hiba: " + e.getMessage()));
            }
        }, "AIBuilder-Thread").start();
        return 1;
    }
}
