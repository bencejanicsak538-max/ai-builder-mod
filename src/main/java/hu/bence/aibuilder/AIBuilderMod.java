package hu.bence.aibuilder;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class AIBuilderMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ConfigManager.ensureConfig();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("ai")
                .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                    .executes(ctx -> executePrompt(ctx.getSource(), StringArgumentType.getString(ctx, "prompt")))));
            dispatcher.register(CommandManager.literal("aiundo")
                .executes(ctx -> AIUndoManager.undoLast(ctx.getSource())));
        });
    }

    private int executePrompt(ServerCommandSource source, String prompt) {
        source.sendFeedback(() -> Text.literal("[AI Builder] Keres\u00e9s: " + prompt), false);
        try {
            String json = AIProviderRouter.requestBuildPlan(prompt, source);
            BuildPlan plan = BuildPlanParser.parse(json);
            int placed = StructureBuilder.placePlan(source, plan);
            source.sendFeedback(() -> Text.literal("[AI Builder] K\u00e9sz! Lerakott blokkok: " + placed), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("[AI Builder] Hiba: " + e.getMessage()));
            return 0;
        }
    }
}
