package hu.bence.aibuilder.client;

import hu.bence.aibuilder.ConfigManager;
import hu.bence.aibuilder.SimpleConfig;
import hu.bence.aibuilder.Json;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget providerField;
    private TextFieldWidget geminiKeyField;
    private TextFieldWidget openrouterKeyField;
    private TextFieldWidget ollamaModelField;
    private SimpleConfig cfg;

    public ConfigScreen(Screen parent) {
        super(Text.literal("AI Builder - Konfiguráció"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cfg = Json.GSON.fromJson(ConfigManager.loadConfig(), SimpleConfig.class);
        if (cfg == null) cfg = new SimpleConfig();
        if (cfg.gemini == null) cfg.gemini = new SimpleConfig.Provider();
        if (cfg.openrouter == null) cfg.openrouter = new SimpleConfig.Provider();
        if (cfg.ollama == null) cfg.ollama = new SimpleConfig.Provider();

        int centerX = this.width / 2;
        int startY = 40;
        int fieldWidth = 280;
        int gap = 32;

        this.addDrawableChild(new net.minecraft.client.gui.widget.TextWidget(
            centerX - fieldWidth / 2, startY - 10, fieldWidth, 10,
            Text.literal("§eProvider (gemini / openrouter / ollama):"), this.textRenderer));
        providerField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY + 2, fieldWidth, 18, Text.literal("provider"));
        providerField.setMaxLength(32);
        providerField.setText(cfg.provider != null ? cfg.provider : "gemini");
        this.addDrawableChild(providerField);

        this.addDrawableChild(new net.minecraft.client.gui.widget.TextWidget(
            centerX - fieldWidth / 2, startY + gap - 10, fieldWidth, 10,
            Text.literal("§bGemini API Key:"), this.textRenderer));
        geminiKeyField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY + gap + 2, fieldWidth, 18, Text.literal("gemini key"));
        geminiKeyField.setMaxLength(256);
        String gk = (cfg.gemini.apiKey != null && !cfg.gemini.apiKey.contains("PUT_NEW")) ? cfg.gemini.apiKey : "";
        geminiKeyField.setText(gk);
        this.addDrawableChild(geminiKeyField);

        this.addDrawableChild(new net.minecraft.client.gui.widget.TextWidget(
            centerX - fieldWidth / 2, startY + gap * 2 - 10, fieldWidth, 10,
            Text.literal("§aOpenRouter API Key:"), this.textRenderer));
        openrouterKeyField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY + gap * 2 + 2, fieldWidth, 18, Text.literal("openrouter key"));
        openrouterKeyField.setMaxLength(256);
        String ok = (cfg.openrouter.apiKey != null && !cfg.openrouter.apiKey.contains("PUT_NEW")) ? cfg.openrouter.apiKey : "";
        openrouterKeyField.setText(ok);
        this.addDrawableChild(openrouterKeyField);

        this.addDrawableChild(new net.minecraft.client.gui.widget.TextWidget(
            centerX - fieldWidth / 2, startY + gap * 3 - 10, fieldWidth, 10,
            Text.literal("§7Ollama Model (pl: llama3.1:8b):"), this.textRenderer));
        ollamaModelField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY + gap * 3 + 2, fieldWidth, 18, Text.literal("ollama model"));
        ollamaModelField.setMaxLength(128);
        ollamaModelField.setText(cfg.ollama.model != null ? cfg.ollama.model : "llama3.1:8b");
        this.addDrawableChild(ollamaModelField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("§a✔ Mentés"), btn -> saveAndClose())
            .dimensions(centerX - 105, startY + gap * 4 + 10, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§c✖ Mégse"), btn -> close())
            .dimensions(centerX + 5, startY + gap * 4 + 10, 100, 20).build());
    }

    private void saveAndClose() {
        cfg.provider = providerField.getText().trim();

        if (cfg.gemini == null) cfg.gemini = new SimpleConfig.Provider();
        if (!geminiKeyField.getText().isBlank()) cfg.gemini.apiKey = geminiKeyField.getText().trim();
        if (cfg.gemini.model == null || cfg.gemini.model.isBlank()) cfg.gemini.model = "gemini-2.0-flash";
        if (cfg.gemini.url == null || cfg.gemini.url.isBlank()) cfg.gemini.url = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

        if (cfg.openrouter == null) cfg.openrouter = new SimpleConfig.Provider();
        if (!openrouterKeyField.getText().isBlank()) cfg.openrouter.apiKey = openrouterKeyField.getText().trim();
        if (cfg.openrouter.model == null || cfg.openrouter.model.isBlank()) cfg.openrouter.model = "meta-llama/llama-3.3-70b-instruct:free";
        if (cfg.openrouter.url == null || cfg.openrouter.url.isBlank()) cfg.openrouter.url = "https://openrouter.ai/api/v1/chat/completions";

        if (cfg.ollama == null) cfg.ollama = new SimpleConfig.Provider();
        cfg.ollama.model = ollamaModelField.getText().trim();
        if (cfg.ollama.url == null || cfg.ollama.url.isBlank()) cfg.ollama.url = "http://127.0.0.1:11434/api/generate";

        ConfigManager.saveConfig(cfg);
        close();

        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(Text.literal("§a[AI Builder] Config elmentve! Provider: §e" + cfg.provider), false);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§7Kulcsok mentve: config/ai-builder.json"),
            this.width / 2, 28, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
