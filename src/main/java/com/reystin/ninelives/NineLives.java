package com.reystin.ninelives;

import com.reystin.ninelives.bacap.BacapInstaller;
import com.reystin.ninelives.command.NinelivesCommand;
import com.reystin.ninelives.config.NLConfig;
import com.reystin.ninelives.core.CommonEvents;
import com.reystin.ninelives.core.Net;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nine Lives — у всех игроков максимум здоровья всегда 1 HP (пол-сердца).
 * Жизни зарабатываются достижениями и передаются между игроками; при нуле
 * жизней игрок становится наблюдателем, пока ему не передадут жизнь.
 *
 * Общая (серверная) логика живёт в этом source set и работает как в одиночной
 * игре, так и на выделенном сервере — в том числе с ванильными клиентами.
 */
public class NineLives implements ModInitializer {
	public static final String MOD_ID = "ninelives";
	public static final Logger LOGGER = LoggerFactory.getLogger("Nine Lives");

	private static NLConfig config;
	private static net.minecraft.server.MinecraftServer server;

	/** Активный сервер (null вне работы сервера). */
	public static net.minecraft.server.MinecraftServer server() {
		return server;
	}

	public static void setServer(net.minecraft.server.MinecraftServer server) {
		NineLives.server = server;
	}

	/** Сервер, на котором находится игрок (надёжно для gametest-серверов). */
	public static net.minecraft.server.MinecraftServer serverOf(net.minecraft.world.entity.Entity entity) {
		return entity.level().getServer();
	}

	/** Серверный конфиг (загружается до инициализации модулей). */
	public static NLConfig config() {
		if (config == null) config = NLConfig.load();
		return config;
	}

	public static void reloadConfig() {
		config = NLConfig.load();
	}

	@Override
	public void onInitialize() {
		config();
		Net.registerTypes();
		CommonEvents.register();
		NinelivesCommand.register();
		LOGGER.info("[Nine Lives] initialized");
	}
}
