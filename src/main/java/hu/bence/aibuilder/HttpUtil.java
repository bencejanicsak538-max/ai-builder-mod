package hu.bence.aibuilder;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HttpUtil {

    // FIX: kompakt JSON kerés - nincs whitespace/sortörés, igy tobb blokk fer bele a token limitbe
    public static final String SYSTEM_PROMPT =
        "You are a Minecraft 1.20.1 build assistant. Output ONLY compact valid JSON (no spaces, no newlines, no markdown, no explanation).\n" +
        "Schema: {\"originMode\":\"player\",\"blocks\":[{\"dx\":0,\"dy\":0,\"dz\":0,\"block\":\"minecraft:stone\"}]}\n" +
        "Rules:\n" +
        "- CRITICAL: Output must be a single line of compact JSON. No pretty-printing. No whitespace between tokens.\n" +
        "- dx/dy/dz are integer offsets from player position. dy=0 is player foot level.\n" +
        "- Use only valid vanilla Minecraft 1.20.1 block IDs (e.g. minecraft:stone, minecraft:oak_planks).\n" +
        "- Max 300 blocks total. Keep builds small and compact to fit within token limits.\n" +
        "- For doors, beds, multi-block structures: include all required blocks.\n" +
        "- Prefer solid, placeable blocks. Do not use items (minecraft:stick is not a block).\n" +
        "- Vary block types for realism (e.g. mix stone_bricks and mossy_stone_bricks).\n" +
        "- IMPORTANT: Always close the JSON properly with ]}.\n" +
        "- Output ONLY the JSON object, starting with { and ending with }. Nothing else.";

    public static String post(String urlStr, String body, String authHeader) throws IOException {
        URL url;
        try {
            url = new URL(urlStr);
        } catch (Exception e) {
            throw new IOException("Ervenytelen URL a configban: '" + urlStr + "'");
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(90000);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "AIBuilderMod/2.5 Minecraft/1.20.1");
        conn.setRequestProperty("X-Title", "AI Builder Mod");
        if (authHeader != null && !authHeader.isBlank()) {
            conn.setRequestProperty("Authorization", authHeader);
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        } catch (SocketTimeoutException e) {
            throw new IOException("Kapcsolodasi idotullepes (20mp). Ellenorizd az internetkapcsolatodat!");
        }

        int code;
        try {
            code = conn.getResponseCode();
        } catch (SocketTimeoutException e) {
            throw new IOException("A szerver nem valaszolt 90 masodpercen belul. Probald kesobb!");
        }

        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String responseBody = "";
        if (is != null) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }
            responseBody = sb.toString().trim();
        }

        switch (code) {
            case 200, 201 -> { /* OK */ }
            case 400 -> throw new IOException(
                "HTTP 400 Bad Request - hibas kerelem. " +
                "Valoszinuleg a model neve rossz a configban. Modell: '" +
                extractModel(body) + "'. Valasz: " + truncate(responseBody, 300));
            case 401 -> throw new IOException(
                "HTTP 401 Unauthorized - ERVENYTELEN API KULCS! " +
                "Nyomd meg B-t es adj meg helyes OpenRouter API kulcsot. " +
                "Regisztralt kulcs: https://openrouter.ai/keys");
            case 402 -> throw new IOException(
                "HTTP 402 Payment Required - nincs eleg kredit az OpenRouter fiokodon. " +
                "Ingyenes modell hasznalatahoz regisztralt fiok kell: https://openrouter.ai");
            case 403 -> throw new IOException(
                "HTTP 403 Forbidden - hozzaferes megtagadva. " +
                "Az API kulcs nem fer hozza ehhez a modellhez: '" + extractModel(body) + "'");
            case 404 -> throw new IOException(
                "HTTP 404 - A modell nem letezik az OpenRouteren: '" + extractModel(body) + "'. " +
                "Ellenorizd a modell nevet: https://openrouter.ai/models");
            case 429 -> throw new IOException(
                "HTTP 429 Rate Limit - tulterhelt. " +
                "Az ingyenes modellen eler a per-perc limitet. Var 30 masodpercet!");
            case 500 -> throw new IOException(
                "HTTP 500 Internal Server Error - OpenRouter szerver hiba. Probald kesobb!");
            case 502, 503, 504 -> throw new IOException(
                "HTTP " + code + " - OpenRouter szerver ideiglenesen nem erheto el. Probald kesobb!");
            default -> {
                if (code >= 400) throw new IOException(
                    "HTTP " + code + ": " + truncate(responseBody, 400));
            }
        }

        if (responseBody.isEmpty()) {
            throw new IOException("Ures valasz erkezett a szervertol (HTTP " + code + ").");
        }

        return responseBody;
    }

    private static String extractModel(String requestBody) {
        try {
            int idx = requestBody.indexOf("\"model\"");
            if (idx < 0) return "ismeretlen";
            int colon = requestBody.indexOf(':', idx);
            int q1 = requestBody.indexOf('"', colon);
            int q2 = requestBody.indexOf('"', q1 + 1);
            return requestBody.substring(q1 + 1, q2);
        } catch (Exception e) {
            return "ismeretlen";
        }
    }

    public static String truncate(String s, int maxLen) {
        if (s == null || s.isEmpty()) return "(ures)";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
