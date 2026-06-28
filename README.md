# AI Builder Mod

Minecraft Fabric 1.20.1 mod – AI építési asszisztens.

## Használat
- `/ai <utánbasítás>` – pl. `/ai építs egy 7x7-es kőházat tölgy tetővel`
- `/aiundo` – az utolsó AI építés visszavonása

## Providerek
- **Gemini** (Google AI Studio kulcs)
- **OpenRouter** (ingyenes modellek, pl. `meta-llama/llama-3.3-70b-instruct:free`)
- **Ollama** (lokális, ingyenes)

## Telepítés
1. Töltsd le a `.jar`-t az **Actions** fülről (lásd lent)
2. Tedd a Minecraft `mods/` mappába
3. Szerkeszd a `config/ai-builder.json` fájlt – írd be az API kulcsokat

## Config (`config/ai-builder.json`)
```json
{
  "provider": "gemini",
  "maxBlocks": 512,
  "maxRadius": 24,
  "allowReplaceSolid": false,
  "openrouter": {
    "apiKey": "IDE_ÍRD_AZ_OPENROUTER_KULCSOT",
    "model": "meta-llama/llama-3.3-70b-instruct:free",
    "url": "https://openrouter.ai/api/v1/chat/completions"
  },
  "gemini": {
    "apiKey": "IDE_ÍRD_A_GEMINI_KULCSOT",
    "model": "gemini-2.0-flash",
    "url": "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
  },
  "ollama": {
    "url": "http://127.0.0.1:11434/api/generate",
    "model": "llama3.1:8b"
  }
}
```

## .jar letöltése (GitHub Actions)
1. Men j a repo **Actions** fülére
2. Kattints az első (legfrissebb) `Build Mod` futtatásra
3. Alul az **Artifacts** résznél kattints: `ai-builder-mod`
4. Csomagold ki, a `.jar`-t tedd a `mods/` mappába
