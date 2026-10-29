package com.reystin.ninelives.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.reystin.ninelives.NineLives;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Локализация серверных сообщений для ванильных клиентов.
 * Клиенты с модом сами переводят ключи из своих lang-файлов; ванильные клиенты
 * видят fallback-текст, язык которого задаёт конфиг (chatLanguage).
 * Строки берутся из собственных lang-файлов мода — единый источник текстов.
 */
public final class L10n {
	private static final Gson GSON = new Gson();
	private static final Map<String, String> EN = new HashMap<>();
	private static final Map<String, String> RU = new HashMap<>();
	private static boolean loaded;

	private L10n() {
	}

	private static void ensureLoaded() {
		if (loaded) return;
		loaded = true;
		try (var en = NineLives.class.getResourceAsStream("/assets/ninelives/lang/en_us.json");
			 var ru = NineLives.class.getResourceAsStream("/assets/ninelives/lang/ru_ru.json")) {
			if (en != null) GSON.fromJson(new InputStreamReader(en, StandardCharsets.UTF_8), JsonObject.class)
					.entrySet().forEach(e -> EN.put(e.getKey(), e.getValue().getAsString()));
			if (ru != null) GSON.fromJson(new InputStreamReader(ru, StandardCharsets.UTF_8), JsonObject.class)
					.entrySet().forEach(e -> RU.put(e.getKey(), e.getValue().getAsString()));
		} catch (Exception e) {
			NineLives.LOGGER.error("[Nine Lives] failed to load embedded lang files", e);
		}
	}

	/** Fallback-текст ключа на языке серверного конфига. */
	public static String fallback(String key) {
		ensureLoaded();
		Map<String, String> lang = "ru".equalsIgnoreCase(NLConfigHolder.get().chatLanguage) ? RU : EN;
		String text = lang.get(key);
		return text != null ? text : EN.getOrDefault(key, key);
	}

	/** Заглушка, пока NLConfig не создан официально. */
	private static final class NLConfigHolder {
		static NLConfig get() {
			return NineLives.config();
		}
	}
}
