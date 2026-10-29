package com.reystin.ninelives.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.reystin.ninelives.NineLives;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Клиентский конфиг (config/ninelives-client.json): настройки интерфейса полной версии.
 */
public class NLClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public boolean hudEnabled = true;
	public boolean animations = true;
	public boolean particles = true;
	public boolean sounds = true;
	/** Масштаб интерфейса мода (1.0 = как ваниль). */
	public float uiScale = 1.0f;

	private static NLClientConfig instance;

	public static NLClientConfig get() {
		if (instance == null) load();
		return instance;
	}

	public static void load() {
		Path path = configPath();
		try {
			if (Files.exists(path)) {
				instance = GSON.fromJson(Files.readString(path), NLClientConfig.class);
				if (instance != null) return;
			}
		} catch (Exception e) {
			NineLives.LOGGER.error("[Nine Lives] failed to read client config, using defaults", e);
		}
		instance = new NLClientConfig();
		instance.save();
	}

	public void save() {
		try {
			Files.createDirectories(configPath().getParent());
			Files.writeString(configPath(), GSON.toJson(this));
		} catch (IOException e) {
			NineLives.LOGGER.error("[Nine Lives] failed to save client config", e);
		}
	}

	private static Path configPath() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("ninelives-client.json");
	}
}
