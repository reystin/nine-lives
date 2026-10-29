package com.reystin.ninelives.core;

import com.reystin.ninelives.NineLives;
import com.reystin.ninelives.config.NLConfig;
import com.reystin.ninelives.net.PayloadTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.GameType;

import java.util.UUID;

/**
 * Все операции с жизнями: потеря при смерти, передача, защита после
 * возрождения, переход в спектатор при нуле жизней и возврат обратно.
 */
public final class LivesManager {
	private LivesManager() {
	}

	public static int lives(ServerPlayer player) {
		PlayerData data = player.getAttached(Attachments.PLAYER_DATA);
		return data != null ? data.lives() : NineLives.config().startingLives;
	}

	public static PlayerData data(ServerPlayer player) {
		PlayerData data = player.getAttached(Attachments.PLAYER_DATA);
		if (data == null) {
			data = new PlayerData(NineLives.config().startingLives,
					new java.util.HashSet<>(), 0, 0, false, 0L, false);
			player.setAttached(Attachments.PLAYER_DATA, data);
		}
		return data;
	}

	public static void setLives(ServerPlayer player, int value, boolean clearPulse) {
		int clamped = Math.max(0, value);
		player.modifyAttached(Attachments.PLAYER_DATA, d -> new PlayerData(
				clamped, d.counted(), d.periodicVanilla(), d.periodicBacap(),
				d.eliminated(), d.protectionUntil(), clearPulse ? false : d.newLifePulse()));
		afterLivesChanged(player);
	}

	public static void addLives(ServerPlayer player, int delta) {
		setLives(player, lives(player) + delta, false);
	}

	private static void afterLivesChanged(ServerPlayer player) {
		// Игрок с 0 жизней, но не в спектаторе (например, оператор выдал жизни через set)
		// возвращается в выживание вместе с ростом числа жизней.
		PlayerData data = data(player);
		if (data.lives() > 0 && data.eliminated()) {
			revive(player, false);
		}
		Net.syncTo(player);
		ServerMessages.updateTabName(player);
	}

	// =====================================================================
	// Смерть и возрождение
	// =====================================================================

	/**
	 * Вызывается при реальной смерти игрока (тотем не даёт этой точке
	 * сработать — он отменяет смерть до вызова). Тратит одну жизнь.
	 */
	public static void onDeath(ServerPlayer player) {
		PlayerData data = data(player);
		int remaining = Math.max(0, data.lives() - 1);
		player.modifyAttached(Attachments.PLAYER_DATA, d -> new PlayerData(
				remaining, d.counted(), d.periodicVanilla(), d.periodicBacap(),
				d.eliminated(), d.protectionUntil(), d.newLifePulse()));

		if (remaining <= 0) {
			ServerMessages.chat(player, "ninelives.msg.life_lost", 0);
			NineLives.LOGGER.info("[Nine Lives] {} ran out of lives", player.nameAndId().name());
		} else {
			ServerMessages.chat(player, "ninelives.msg.life_lost", remaining);
		}

		// Клиент с модом: тост + красное мерцание краёв экрана.
		Net.sendIfModded(player, new PayloadTypes.LifeEventPayload(1, java.util.Optional.empty()));
		ServerMessages.notifySound(player, SoundEvents.WITHER_HURT, 0.6f, 0.7f);
		Net.syncTo(player);
	}

	/** Вызывается после возрождения: защита, спектатор при нуле жизней. */
	public static void onRespawn(ServerPlayer player) {
		PlayerData data = data(player);
		NLConfig config = NineLives.config();

		long now = NineLives.serverOf(player).getTickCount();
		long protectionEnds = now + config.protectionSeconds * 20L;
		player.modifyAttached(Attachments.PLAYER_DATA, d -> new PlayerData(
				d.lives(), d.counted(), d.periodicVanilla(), d.periodicBacap(),
				d.eliminated(), protectionEnds, d.newLifePulse()));

		if (data.lives() <= 0) {
			eliminate(player);
		} else {
			ServerMessages.chat(player, "ninelives.msg.protection_start", config.protectionSeconds);
		}
		Net.syncTo(player);
		ServerMessages.updateTabName(player);
	}

	/** Перевод выбывшего игрока в режим наблюдателя. */
	public static void eliminate(ServerPlayer player) {
		player.modifyAttached(Attachments.PLAYER_DATA, d -> new PlayerData(
				d.lives(), d.counted(), d.periodicVanilla(), d.periodicBacap(),
				true, d.protectionUntil(), d.newLifePulse()));
		if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
			player.setGameMode(GameType.SPECTATOR);
		}
		ServerMessages.chat(player, "ninelives.msg.eliminated");
		ServerMessages.broadcast("ninelives.msg.eliminated_broadcast", player.nameAndId().name());
		ServerMessages.title(player, "ninelives.observer.title", "ninelives.observer.subtitle");
		ServerMessages.notifySound(player, SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.7f);
		Net.sendIfModded(player, new PayloadTypes.LifeEventPayload(2, java.util.Optional.empty()));
		NineLives.LOGGER.info("[Nine Lives] {} eliminated -> spectator", player.nameAndId().name());
	}

	/** Возврат из спектатора в выживание (передали жизнь). */
	public static void revive(ServerPlayer player, boolean message) {
		player.modifyAttached(Attachments.PLAYER_DATA, d -> new PlayerData(
				d.lives(), d.counted(), d.periodicVanilla(), d.periodicBacap(),
				false, d.protectionUntil(), d.newLifePulse()));
		if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
			player.setGameMode(GameType.SURVIVAL);
		}
		if (message) {
			ServerMessages.chat(player, "ninelives.msg.revived");
			Net.sendIfModded(player, new PayloadTypes.LifeEventPayload(3, java.util.Optional.empty()));
			ServerMessages.notifySound(player, SoundEvents.TOTEM_USE, 0.9f, 1.1f);
		}
		Net.syncTo(player);
		ServerMessages.updateTabName(player);
		NineLives.LOGGER.info("[Nine Lives] {} revived", player.nameAndId().name());
	}

	// =====================================================================
	// Вход игрока
	// =====================================================================

	/**
	 * Надёжность после перезахода: выбывший игрок (0 жизней) снова ставится в
	 * спектатор; если жизни появились (передали, пока был оффлайн) — обратно
	 * в выживание.
	 */
	public static void onJoin(ServerPlayer player) {
		PlayerData data = data(player);
		if (data.lives() <= 0) {
			if (!data.eliminated()) {
				eliminate(player);
			} else if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
					&& !ServerMessages.isOperator(player)) {
				// Оператор вправе сам менять себе режим; обычный игрок без жизней
				// остаётся наблюдателем до передачи жизни.
				player.setGameMode(GameType.SPECTATOR);
			}
		} else if (data.eliminated()) {
			revive(player, true);
		}
		ServerMessages.updateTabName(player);
		Net.syncTo(player);
	}

	// =====================================================================
	// Защита после возрождения
	// =====================================================================

	public static boolean isProtected(ServerPlayer player) {
		PlayerData data = player.getAttached(Attachments.PLAYER_DATA);
		return data != null && NineLives.serverOf(player).getTickCount() < data.protectionUntil();
	}

	public static void endProtection(ServerPlayer player, boolean message) {
		player.modifyAttached(Attachments.PLAYER_DATA, d -> new PlayerData(
				d.lives(), d.counted(), d.periodicVanilla(), d.periodicBacap(),
				d.eliminated(), 0L, d.newLifePulse()));
		if (message) ServerMessages.chat(player, "ninelives.msg.protection_end_attack");
		Net.syncTo(player);
	}

	// =====================================================================
	// Передача жизней
	// =====================================================================

	/**
	 * Передача жизней: у отдающего должна остаться минимум 1 жизнь, у
	 * получателя лимита нет. Возвращает фактически переданное количество
	 * или -1 при неудаче.
	 */
	public static int transfer(ServerPlayer from, ServerPlayer to, int amount) {
		if (from.getUUID().equals(to.getUUID())) {
			ServerMessages.chat(from, "ninelives.msg.give_self");
			return -1;
		}
		PlayerData fromData = data(from);
		int transferable = Math.max(0, Math.min(amount, fromData.lives() - 1));
		if (transferable <= 0) {
			ServerMessages.chat(from, "ninelives.msg.give_not_enough");
			return -1;
		}
		boolean wasEliminated = data(to).eliminated();
		setLives(from, fromData.lives() - transferable, false);
		setLives(to, lives(to) + transferable, false); // выбывший автоматически возвращается в выживание
		// Получатель должен увидеть пульс новой жизни на кнопке инвентаря.
		to.modifyAttached(Attachments.PLAYER_DATA, d -> new PlayerData(
				d.lives(), d.counted(), d.periodicVanilla(), d.periodicBacap(),
				d.eliminated(), d.protectionUntil(), true));

		ServerMessages.chat(from, "ninelives.msg.give_sent", transferable, to.nameAndId().name(), lives(from));
		ServerMessages.chat(to, "ninelives.msg.give_received", from.nameAndId().name(), transferable, lives(to));

		// Возврат из наблюдателя — отдельное событие, иначе LifeEvent 0 не сообщит
		// клиенту о смене игрового режима.
		if (wasEliminated) {
			revive(to, true);
		} else {
			Net.sendIfModded(to, new PayloadTypes.LifeEventPayload(0, java.util.Optional.empty()));
			ServerMessages.notifySound(to, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.4f);
		}

		// Анимация и тосты на экранах обоих (полная версия).
		String fromName = from.nameAndId().name();
		String toName = to.nameAndId().name();
		Net.sendIfModded(from, new PayloadTypes.TransferFxPayload(fromName, toName, transferable));
		Net.sendIfModded(to, new PayloadTypes.TransferFxPayload(fromName, toName, transferable));
		ServerMessages.notifySound(from, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
		// Открытые экраны жизней должны увидеть новые числа сразу.
		Net.sendIfModded(from, ScreenData.build(from));
		Net.sendIfModded(to, ScreenData.build(to));
		return transferable;
	}

	/** Поиск игрока по UUID среди онлайн-игроков. */
	public static ServerPlayer byUuid(UUID uuid) {
		return NineLives.server() != null ? NineLives.server().getPlayerList().getPlayer(uuid) : null;
	}
}
