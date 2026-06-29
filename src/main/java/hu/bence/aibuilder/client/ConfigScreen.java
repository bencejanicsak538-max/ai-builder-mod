package hu.bence.aibuilder.client;

import hu.bence.aibuilder.ConfigManager;
import hu.bence.aibuilder.SimpleConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class ConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget providerField;
    private TextFieldWidget geminiKeyField;
    private TextFieldWidget openrouterKeyField;
    private TextFieldWidget maxBlocksField;
    private TextFieldWidget maxRadiusField;
    private SimpleConfig cfg;

    public ConfigScreen(Screen parent) {
        super(Text.literal("\u00a7l AI Builder v2 - Beallitasok"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cfg = ConfigManager.load();
        if (cfg == null) cfg = new SimpleConfig();
        if (cfg.gemini == null) cfg.gemini = new SimpleConfig.Provider(
            "PUT_KEY_HERE", "gemini-2.0-flash",
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
        );
        if (cfg.openrouter == null) cfg.openrouter = new SimpleConfig.Provider(
            "PUT_KEY_HERE", "google/gemini-2.0-flash-exp:free",
            "https://openrouter.ai/api/v1/chat/completions"
        );

        int cx = this.width / 2;
        int y = 46;
        int w = 280;

        // Provider
        addLabel(cx, y - 10, w, "\u00a7eProvider: gemini \u00a77(ajanlott, ingyenes) \u00a7bvagy openrouter");
        providerField = addField(cx, y, w, cfg.provider != null ? cfg.provider : "gemini", 32);
        y += 34;

        // Gemini Key
        addLabel(cx, y - 10, w, "\u00a7bGemini API Key \u00a77(aistudio.google.com - ingyenes):");
        geminiKeyField = addField(cx, y, w, cleanKey(cfg.gemini.apiKey), 256);
        y += 34;

        // OpenRouter Key
        addLabel(cx, y - 10, w, "\u00a7aOpenRouter API Key \u00a77(openrouter.ai - ingyenes tier):");
        openrouterKeyField = addField(cx, y, w, cleanKey(cfg.openrouter.apiKey), 256);
        y += 34;

        // Max blocks & radius on same row
        addLabel(cx - 75, y - 10, 130, "\u00a77Max blokkok:");
        addLabel(cx + 75, y - 10, 130, "\u00a77Max sugar (blokk):");
        maxBlocksField = addFieldAt(cx - 75, y, 120, String.valueOf(cfg.maxBlocks), 6);
        maxRadiusField = addFieldAt(cx + 75, y, 120, String.valueOf(cfg.maxRadius), 6);
        y += 34;

        // Replace solid toggle
        boolean replaceOn = cfg.allowReplaceSolid;
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Teli blokkok felulirasa: " + (replaceOn ? "\u00a7aBekapcsolt" : "\u00a7cKikapcsolt")),
            b -> {
                cfg.allowReplaceSolid = !cfg.allowReplaceSolid;
                b.setMessage(Text.literal("Teli blokkok felulirasa: " + (cfg.allowReplaceSolid ? "\u00a7aBekapcsolt" : "\u00a7cKikapcsolt")));
            }
        ).dimensions(cx - 130, y, 260, 20).build());
        y += 28;

        // Save / Cancel
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7a\u2714 Mentes"), b -> save())
            .dimensions(cx - 130, y, 120, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7c\u2716 Megse"), b -> close())
            .dimensions(cx + 10, y, 120, 20).build());
    }

    private TextFieldWidget addField(int cx, int y, int w, String text, int maxLen) {
        return addFieldAt(cx, y, w, text, maxLen);
    }

    private TextFieldWidget addFieldAt(int cx, int y, int w, String text, int maxLen) {
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
        String prov = providerField.getText().trim().toLowerCase();
        if (!prov.equals("gemini") && !prov.equals("openrouter")) {
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("\u00a7c[AI Builder] Ervenytelen provider! Csak 'gemini' vagy 'openrouter' lehet."), false);
            return;
        }
        cfg.provider = prov;

        if (!geminiKeyField.getText().isBlank()) cfg.gemini.apiKey = geminiKeyField.getText().trim();
        if (cfg.gemini.model == null || cfg.gemini.model.isBlank()) cfg.gemini.model = "gemini-2.0-flash";
        if (cfg.gemini.url == null || cfg.gemini.url.isBlank())
            cfg.gemini.url = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

        if (!openrouterKeyField.getText().isBlank()) cfg.openrouter.apiKey = openrouterKeyField.getText().trim();
        if (cfg.openrouter.model == null || cfg.openrouter.model.isBlank()) cfg.openrouter.model = "google/gemini-2.0-flash-exp:free";
        if (cfg.openrouter.url == null || cfg.openrouter.url.isBlank())
            cfg.openrouter.url = "https://openrouter.ai/api/v1/chat/completions";

        try { cfg.maxBlocks = Math.min(512, Math.max(1, Integer.parseInt(maxBlocksField.getText().trim()))); }
        catch (NumberFormatException ignored) { cfg.maxBlocks = 512; }

        try { cfg.maxRadius = Math.min(64, Math.max(1, Integer.parseInt(maxRadiusField.getText().trim()))); }
        catch (NumberFormatException ignored) { cfg.maxRadius = 24; }

        ConfigManager.save(cfg);
        close();
        if (client != null && client.player != null)
            client.player.sendMessage(Text.literal(
                "\u00a7a[AI Builder] Mentve! Provider: \u00a7e" + cfg.provider +
                " \u00a77| Max: " + cfg.maxBlocks + " blokk, " + cfg.maxRadius + "bl sugar"
            ), false);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("\u00a77Config fajl: config/ai-builder.json"),
            width / 2, 26, 0x888888);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
