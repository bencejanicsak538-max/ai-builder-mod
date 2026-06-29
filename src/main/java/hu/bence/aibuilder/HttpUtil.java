package hu.bence.aibuilder;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HttpUtil {
    private static final String SYSTEM_PROMPT =
        "You are a Minecraft 1.20.1 build assistant. Output ONLY valid JSON, no markdown, no extra text, no explanation.\n" +
        "Schema: {\"originMode\":\"player\",\"blocks\":[{\"dx\":0,\"dy\":0,\"dz\":0,\"block\":\"minecraft:stone\"}]}\n" +
        "Rules:\n" +
        "- dx/dy/dz are integer offsets from player position. dy=0 is player foot level.\n" +
        "- Use only valid vanilla Minecraft 1.20.1 block IDs (e.g. minecraft:stone, minecraft:oak_planks).\n" +
        "- Max 512 blocks total.\n" +
        "- For doors, beds, and multi-block structures, include all required blocks.\n" +
        "- Prefer solid, placeable blocks. Do not use items (minecraft:stick is not a block).\n" +
        "- Vary block types for realism (e.g. mix stone_bricks and mossy_stone_bricks).\n" +
        "- Output ONLY the JSON object, starting with { and ending with }.";

    public static final String SYSTEM = SYSTEM_PROMPT;

    public static String post(String urlStr, String body, String authHeader) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("User-Agent", "AIBuilderMod/2.0 (Minecraft 1.20.1)");
        conn.setRequestProperty("Accept", "application/json");
        if (authHeader != null) conn.setRequestProperty("Authorization", authHeader);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) throw new IOException("Ures valasz a szervertol. HTTP " + code);

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }

        if (code == 429) throw new IOException("429 Rate limit - tulterhelt szerver. Var nehany masodpercet.");
        if (code == 401 || code == 403) throw new IOException("HTTP " + code + " - Ervenytelen API kulcs!");
        if (code >= 400) throw new IOException("HTTP " + code + ": " + sb.toString().substring(0, Math.min(300, sb.length())));

        return sb.toString();
    }
}
