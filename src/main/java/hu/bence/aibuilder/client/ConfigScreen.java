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
    private TextFieldWidget openrouterKeyField;
    private TextFieldWidget openrouterModelField;
    private TextFieldWidget maxBlocksField;
    private TextFieldWidget maxRadiusField;
    private SimpleConfig cfg;

    public ConfigScreen(Screen parent) {
        super(Text.literal("\u00a7l AI Builder v2.4 - Beallitasok"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cfg = ConfigManager.load();
        if (cfg == null) cfg = new SimpleConfig();
        if (cfg.openrouter == null) cfg.openrouter = new SimpleConfig.OpenRouterConfig(
            "PUT_YOUR_OPENROUTER_API_KEY_HERE",
            "meta-llama/llama-3.3-8b-instruct:free",
            "https://openrouter.ai/api/v1/chat/completions"
        );

        int cx = this.width / 2;
        int y = 46;
        int w = 300;

        addLabel(cx, y - 10, w, "\u00a7aOpenRouter API Key \u00a77(openrouter.ai/keys):");
        openrouterKeyField = addField(cx, y, w, cleanKey(cfg.openrouter.apiKey), 256);
        y += 34;

        addLabel(cx, y - 10, w, "\u00a7eOpenRouter Modell \u00a77(pl: meta-llama/llama-3.3-8b-instruct:free):");
        openrouterModelField = addField(cx, y, w,
            cfg.openrouter.model != null ? cfg.openrouter.model : "meta-llama/llama-3.3-8b-instruct:free",
            128);
        y += 34;

        // FIX: limit szöveg a mezők felett a pontos határokkal
        addLabel(cx - 75, y - 10, 150,
            "\u00a77Max blokkok \u00a78(1-" + ConfigManager.MAX_BLOCKS_LIMIT + "):");
        addLabel(cx + 75, y - 10, 150,
            "\u00a77Max sugar \u00a78(1-" + ConfigManager.MAX_RADIUS_LIMIT + "):");
        maxBlocksField = addFieldAt(cx - 75, y, 120, String.valueOf(cfg.maxBlocks), 6);
        maxRadiusField = addFieldAt(cx + 75, y, 120, String.valueOf(cfg.maxRadius), 6);
        y += 34;

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Teli blokkok felulirasa: " + (cfg.allowReplaceSolid ? "\u00a7aBekapcsolt" : "\u00a7cKikapcsolt")),
            b -> {
                cfg.allowReplaceSolid = !cfg.allowReplaceSolid;
                b.setMessage(Text.literal("Teli blokkok felulirasa: " + (cfg.allowReplaceSolid ? "\u00a7aBekapcsolt" : "\u00a7cKikapcsolt")));
            }
        ).dimensions(cx - 150, y, 300, 20).build());
        y += 28;

        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7a\u2714 Mentes"), b -> save())
            .dimensions(cx - 150, y, 140, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7c\u2716 Megse"), b -> close())
            .dimensions(cx + 10, y, 140, 20).build());
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
        return (k == null || k.contains("PUT_YOUR") || k.contains("PUT_KEY") || k.isBlank()) ? "" : k;
    }

    private void save() {
        cfg.provider = "openrouter";

        String key = openrouterKeyField.getText().trim();
        if (!key.isBlank()) cfg.openrouter.apiKey = key;

        String model = openrouterModelField.getText().trim();
        cfg.openrouter.model = model.isBlank() ? "meta-llama/llama-3.3-8b-instruct:free" : model;

        if (cfg.openrouter.url == null || cfg.openrouter.url.isBlank())
            cfg.openrouter.url = "https://openrouter.ai/api/v1/chat/completions";

        // FIX: kozponti limiteket hasznalunk, es ha levagja jelzi
        boolean capped = false;
        try {
            int val = Integer.parseInt(maxBlocksField.getText().trim());
            int clamped = Math.max(ConfigManager.MIN_BLOCKS, Math.min(ConfigManager.MAX_BLOCKS_LIMIT, val));
            if (clamped != val) capped = true;
            cfg.maxBlocks = clamped;
        } catch (NumberFormatException ignored) {
            cfg.maxBlocks = 512;
            capped = true;
        }
        try {
            int val = Integer.parseInt(maxRadiusField.getText().trim());
            int clamped = Math.max(ConfigManager.MIN_RADIUS, Math.min(ConfigManager.MAX_RADIUS_LIMIT, val));
            if (clamped != val) capped = true;
            cfg.maxRadius = clamped;
        } catch (NumberFormatException ignored) {
            cfg.maxRadius = 24;
            capped = true;
        }

        if (cfg.openrouter.apiKey != null && !cfg.openrouter.apiKey.isBlank()
            && !cfg.openrouter.apiKey.startsWith("sk-or-")) {
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal(
                    "\u00a7e[AI Builder] Figyelem: OpenRouter kulcsok 'sk-or-' prefixszel kezdodnek. Ellenorizd: openrouter.ai/keys"
                ), false);
        }

        ConfigManager.save(cfg);

        // FIX: close() UTAN kuldjuk az uzenetet, hogy biztosan latssa a jatekos
        final boolean wasCapped = capped;
        final SimpleConfig savedCfg = cfg;
        close(); // elobb zarjuk be
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(
                "\u00a7a[AI Builder] Mentve! Modell: \u00a7e" + savedCfg.openrouter.model +
                " \u00a77| Max: " + savedCfg.maxBlocks + " blokk, " + savedCfg.maxRadius + " bl sugar"
            ), false);
            if (wasCapped) {
                client.player.sendMessage(Text.literal(
                    "\u00a7e[AI Builder] Figyelem: egy vagy tobb ertek kiigazitva a megengendett hatarokra (blokk: 1-"
                    + ConfigManager.MAX_BLOCKS_LIMIT + ", sugar: 1-" + ConfigManager.MAX_RADIUS_LIMIT + ")."
                ), false);
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("\u00a77Provider: OpenRouter | config/ai-builder.json"),
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
