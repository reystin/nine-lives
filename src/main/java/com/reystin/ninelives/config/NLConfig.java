package com.reystin.ninelives.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.reystin.ninelives.NineLives;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Серверный конфиг (config/ninelives.json). Читается и на выделенном сервере, и в одиночной игре.
 */
public class NLConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Стартовое число жизней у нового игрока. */
	public int startingLives = 9;
	/** Режим 1: сколько достижений дают одну жизнь. */
	public int advancementsPerLifeVanilla = 10;
	/** Режим 2 (BACAP): сколько достижений дают одну жизнь. */
	public int advancementsPerLifeBacap = 50;
	/** Длительность защиты после возрождения, секунд. */
	public int protectionSeconds = 15;
	/** Режим по умолчанию для миров, где режим ещё не выбран: "vanilla" или "bc". */
	public String defaultMode = "vanilla";
	/** Язык серверных сообщений для ванильных клиентов: "en" или "ru". */
	public String chatLanguage = "en";
	/** Каждое достижение из этого списка (режим 1) даёт +1 жизнь. Id сверены с jar 26.3
	 *  (data/minecraft/advancement/): только действительно долгие и трудные. */
	public List<String> hardAdvancements = new ArrayList<>(List.of(
			"minecraft:nether/all_effects",            // Невозможное возможно! — все эффекты
			"minecraft:nether/all_potions",            // Энергетический коктейль — все зелья
			"minecraft:adventure/adventuring_time",    // Время приключений — все биомы
			"minecraft:nether/explore_nether",         // Горящий тур — все биомы Незера
			"minecraft:adventure/kill_all_mobs",       // Зверобой — все виды мобов
			"minecraft:husbandry/complete_catalogue",  // Полный котолог — все кошки
			"minecraft:husbandry/bred_all_animals",    // Счастливый фермер — все животные
			"minecraft:husbandry/balanced_diet",       // Робин-Бобин — вся еда
			"minecraft:husbandry/whole_pack",          // Барбоскины — все волки
			"minecraft:end/kill_dragon",               // Освободите Энд
			"minecraft:nether/create_full_beacon",     // Маяковский — полный маяк
			"minecraft:husbandry/froglights",          // Перекрёсток семи болот — все фрогсветы
			"minecraft:adventure/trim_with_all_exclusive_armor_patterns" // Кузнец своего стиля — все узоры
	));
	/** Корни вкладок супер-челленджей BACAP: всё дерево под корнем — супер-челленджи.
	 *  Подтверждено по реальному паку 1.21.1 (вкладка Super Challenges). */
	public List<String> bacapSuperRoots = new ArrayList<>(List.of("blazeandcave:challenges/root"));
	/** Запасной список точных id супер-челленджей, если авто-детект не сработал. */
	public List<String> superChallenges = new ArrayList<>();
	/** Пространства имён достижений BACAP. */
	public List<String> bacapNamespaces = new ArrayList<>(List.of("blazeandcave"));
	/** Проект BACAP на Modrinth. */
	public String bacapProject = "VoVJ47kN";
	/** Домены, с которых разрешено скачивать датапак. */
	public List<String> allowedDomains = new ArrayList<>(List.of("api.modrinth.com", "cdn.modrinth.com"));
	/** Ссылка для ручной установки BACAP (показывается при ошибке авто-установки). */
	public String bacapManualUrl = "https://modrinth.com/datapack/blazeandcaves-advancements-pack";
	/** При неудаче авто-установки откатываться на режим 1. */
	public boolean fallbackToVanillaOnFailure = true;

	public static NLConfig load() {
		Path path = configPath();
		try {
			if (Files.exists(path)) {
				NLConfig cfg = GSON.fromJson(Files.readString(path), NLConfig.class);
				if (cfg != null) {
					NineLives.LOGGER.info("[Nine Lives] config loaded from {}", path);
					return cfg;
				}
			}
		} catch (Exception e) {
			NineLives.LOGGER.error("[Nine Lives] failed to read config, using defaults", e);
		}
		NLConfig cfg = new NLConfig();
		cfg.save();
		return cfg;
	}

	public void save() {
		try {
			Files.createDirectories(configPath().getParent());
			Files.writeString(configPath(), GSON.toJson(this));
		} catch (IOException e) {
			NineLives.LOGGER.error("[Nine Lives] failed to save config", e);
		}
	}

	private static Path configPath() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("ninelives.json");
	}
}
