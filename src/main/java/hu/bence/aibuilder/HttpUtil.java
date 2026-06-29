package hu.bence.aibuilder;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HttpUtil {
    private static final String SYSTEM_PROMPT =
        "You are a Minecraft build assistant. Output ONLY valid JSON, no markdown, no extra text.\n" +
        "Schema: {\"originMode\":\"player\",\"blocks\":[{\"dx\":0,\"dy\":0,\"dz\":0,\"block\":\"minecraft:stone\"}]}\n" +
        "Rules: dx/dy/dz are integer offsets from player. Use only vanilla 1.20.1 block IDs. Max 512 blocks. dy=0 is ground level.";

    public static final String SYSTEM = SYSTEM_PROMPT;

    public static String post(String urlStr, String body, String authHeader) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "AIBuilderMod/1.0");
        if (authHeader != null) conn.setRequestProperty("Authorization", authHeader);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) throw new IOException("No response. HTTP " + code);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        if (code >= 400) throw new IOException("HTTP " + code + ": " + sb);
        return sb.toString();
    }
}
