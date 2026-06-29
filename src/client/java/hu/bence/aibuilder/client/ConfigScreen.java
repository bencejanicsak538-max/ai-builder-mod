package hu.bence.aibuilder.client;

import hu.bence.aibuilder.ConfigManager;
import hu.bence.aibuilder.SimpleConfig;
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
    private SimpleConfig cfg;

    public ConfigScreen(Screen parent) {
        super(Text.literal("AI Builder Beallitasok"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cfg = ConfigManager.load();
        if (cfg == null) cfg = new SimpleConfig();
        if (cfg.gemini == null) cfg.gemini = new SimpleConfig.Provider();
        if (cfg.openrouter == null) cfg.openrouter = new SimpleConfig.Provider();

        int cx = this.width / 2;
        int y = 50;
        int w = 300;

        addLabel(cx, y - 12, w, "§eProvider: gemini / openrouter / ollama");
        providerField = addField(cx, y, w, cfg.provider != null ? cfg.provider : "gemini", 32);
        y += 36;

        addLabel(cx, y - 12, w, "§bGemini API Key:");
        geminiKeyField = addField(cx, y, w, cleanKey(cfg.gemini.apiKey), 256);
        y += 36;

        addLabel(cx, y - 12, w, "§aOpenRouter API Key:");
        openrouterKeyField = addField(cx, y, w, cleanKey(cfg.openrouter.apiKey), 256);
        y += 40;

        addDrawableChild(ButtonWidget.builder(Text.literal("§a✔ Mentés"), b -> save())
            .dimensions(cx - 110, y, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("§c✖ Mégse"), b -> close())
            .dimensions(cx + 10, y, 100, 20).build());
    }

    private TextFieldWidget addField(int cx, int y, int w, String text, int maxLen) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, cx - w / 2, y, w, 18, Text.empty());
        f.setMaxLength(maxLen);
        f.setText(text != null ? text : "");
        addDrawableChild(f);
        return f;
    }

    private void addLabel(int cx, int y, int w, String text) {
        addDrawableChild(new net.minecraft.client.gui.widget.TextWidget(
            cx - w / 2, y, w, 10, Text.literal(text), this.textRenderer));
    }

    private String cleanKey(String k) {
        return (k == null || k.contains("PUT_KEY") || k.isBlank()) ? "" : k;
    }

    private void save() {
        cfg.provider = providerField.getText().trim();
        if (!geminiKeyField.getText().isBlank()) cfg.gemini.apiKey = geminiKeyField.getText().trim();
        if (cfg.gemini.model == null) cfg.gemini.model = "gemini-2.0-flash";
        if (cfg.gemini.url == null) cfg.gemini.url = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";
        if (!openrouterKeyField.getText().isBlank()) cfg.openrouter.apiKey = openrouterKeyField.getText().trim();
        if (cfg.openrouter.model == null) cfg.openrouter.model = "meta-llama/llama-3.3-70b-instruct:free";
        if (cfg.openrouter.url == null) cfg.openrouter.url = "https://openrouter.ai/api/v1/chat/completions";
        ConfigManager.save(cfg);
        close();
        if (client != null && client.player != null)
            client.player.sendMessage(Text.literal("§a[AI Builder] Mentve! Provider: §e" + cfg.provider), false);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§7Mentve: config/ai-builder.json"), width / 2, 32, 0xAAAAAA);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
