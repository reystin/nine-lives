package com.reystin.ninelives.core;

import com.reystin.ninelives.NineLives;
import com.reystin.ninelives.bacap.BacapInstaller;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Работа с режимами подсчёта достижений: эффективный режим мира
 * (выбранный, а до выбора — defaultMode из конфига) и его смена.
 */
public final class ModeManager {
	private ModeManager() {
	}

	/** Эффективный режим мира: выбранный, а до выбора — defaultMode из конфига. */
	public static Mode effectiveMode(MinecraftServer server) {
		WorldState state = state(server);
		String mode = state != null ? state.mode : null;
		if (mode == null) mode = NineLives.config().defaultMode;
		return "bc".equalsIgnoreCase(mode) ? Mode.BACAP : Mode.VANILLA;
	}

	/** Выбран ли режим для мира (если нет — действует defaultMode из конфига). */
	public static boolean isModeChosen(MinecraftServer server) {
		WorldState state = state(server);
		return state != null && state.mode != null;
	}

	public static void setMode(MinecraftServer server, Mode mode) {
		WorldState state = WorldStateHolder.get(server);
		state.setMode(mode.id);
		if (mode == Mode.BACAP) {
			// Явный выбор режима = согласие на установку: без отдельного диалога.
			BacapInstaller.onModeActivated(server);
		}
		// После смены режима уже полученные достижения нового пространства имён
		// должны быть засчитаны (importExisting идемпотентен).
		com.reystin.ninelives.advancement.AdvancementCounter.recalculateAll(server);
		refreshClients(server);
		NineLives.LOGGER.info("[Nine Lives] mode set to {}", mode.id);
	}

	/** Сбросить выбор режима (снова действует defaultMode, хост сможет выбрать). */
	public static void clearMode(MinecraftServer server) {
		WorldStateHolder.get(server).setMode(null);
		com.reystin.ninelives.advancement.AdvancementCounter.recalculateAll(server);
		refreshClients(server);
		NineLives.LOGGER.info("[Nine Lives] mode choice cleared");
	}

	/** Обновить HUD и данные экрана у всех онлайн-игроков после смены режима. */
	public static void refreshClients(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Net.syncTo(player);
			if (Net.isModded(player)) {
				Net.sendIfModded(player, ScreenData.build(player));
			}
		}
	}

	public static int modeId(ServerPlayer player) {
		if (!isModeChosen(NineLives.serverOf(player))) return -1;
		return effectiveMode(NineLives.serverOf(player)) == Mode.BACAP ? 1 : 0;
	}

	public static int currentPeriodic(ServerPlayer player) {
		PlayerData data = player.getAttached(Attachments.PLAYER_DATA);
		if (data == null) return 0;
		return effectiveMode(NineLives.serverOf(player)) == Mode.BACAP ? data.periodicBacap() : data.periodicVanilla();
	}

	private static WorldState state(MinecraftServer server) {
		return WorldStateHolder.get(server);
	}

	/** Ленивый доступ к SavedData мира. */
	public static final class WorldStateHolder {
		public static WorldState get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(WorldState.TYPE);
		}
	}
}
