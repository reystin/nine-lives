package com.reystin.ninelives.core;

import com.reystin.ninelives.NineLives;
import com.reystin.ninelives.net.PayloadTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Серверные сообщения для всех клиентов (в т.ч. ванильных): чат, action bar,
 * title. Текст — translatableWithFallback: клиент с модом показывает перевод
 * из lang-файлов, ванильный — fallback с языком серверного конфига.
 */
public final class ServerMessages {
	private ServerMessages() {
	}

	/** Локализованное сообщение с fallback'ом по серверному конфигу. */
	public static MutableComponent tr(String key, Object... args) {
		return Component.translatableWithFallback(key, com.reystin.ninelives.config.L10n.fallback(key), args);
	}

	public static void chat(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(tr(key, args));
	}

	public static void broadcast(String key, Object... args) {
		for (ServerPlayer player : playerList()) {
			player.sendSystemMessage(tr(key, args));
		}
	}

	public static void actionbar(ServerPlayer player, String key, Object... args) {
		player.connection.send(new ClientboundSetActionBarTextPacket(tr(key, args)));
	}

	public static void title(ServerPlayer player, String titleKey, String subtitleKey, Object... args) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
		player.connection.send(new ClientboundSetSubtitleTextPacket(tr(subtitleKey, args)));
		player.connection.send(new ClientboundSetTitleTextPacket(tr(titleKey)));
	}

	/**
	 * Tab: имя в списке игроков — «♥12 · ★45 Ник». Используются глифы,
	 * которые есть в шрифте Minecraft (★ U+2605, а не эмодзи ⭐).
	 * Наблюдатель показывается серым с фактическим числом жизней: оператор
	 * может быть в спектаторе и с жизнями, а выбывший — с нулём.
	 */
	public static Component tabName(ServerPlayer player) {
		PlayerData data = player.getAttached(Attachments.PLAYER_DATA);
		if (data == null) return player.getName();
		int lives = data.lives();
		int adv = data.counted().size();

		boolean spectator = data.eliminated() || player.isSpectator();
		if (spectator) {
			return Component.literal("♥" + lives + " · ★").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(String.valueOf(adv)).withStyle(ChatFormatting.GRAY))
					.append(Component.literal(" ").withStyle(ChatFormatting.RESET))
					.append(player.getName().copy().withStyle(ChatFormatting.GRAY));
		}
		ChatFormatting livesColor = lives <= 2 ? ChatFormatting.RED : lives >= 15 ? ChatFormatting.GOLD : ChatFormatting.WHITE;
		ChatFormatting advColor = adv >= 30 ? ChatFormatting.GOLD : ChatFormatting.YELLOW;
		return Component.literal("♥")
				.withStyle(ChatFormatting.RED)
				.append(Component.literal(String.valueOf(lives)).withStyle(livesColor))
				.append(Component.literal(" · ★").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(String.valueOf(adv)).withStyle(advColor))
				.append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
				.append(player.getName());
	}

	/** Звук только для ванильных клиентов: полная версия проигрывает его сама. */
	public static void notifySound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		if (Net.isModded(player)) return;
		var holder = player.level().registryAccess()
				.lookupOrThrow(net.minecraft.core.registries.Registries.SOUND_EVENT)
				.wrapAsHolder(sound);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
				holder, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(),
				volume, pitch, player.getRandom().nextLong()));
	}

	/** Оператор (или владелец одиночного мира без читов — он тоже хост). */
	public static boolean isOperator(ServerPlayer player) {
		var server = NineLives.serverOf(player);
		return server != null && server.getPlayerList().isOp(player.nameAndId());
	}

	public static boolean isHost(ServerPlayer player) {
		var server = NineLives.serverOf(player);
		return server != null && server.isSingleplayer()
				&& server.getSingleplayerProfile() != null
				&& server.getSingleplayerProfile().id().equals(player.getUUID());
	}

	/** Кто вправе выбирать режим и запускать установку датапака. */
	public static boolean isPrompter(ServerPlayer player) {
		return isPrompter(isHost(player), isOperator(player));
	}

	/** Правило выбора: хост без OP тоже вправе (host здесь — владелец мира). */
	public static boolean isPrompter(boolean host, boolean operator) {
		return host || operator;
	}

	/** Обновить имя в Tab у всех, кто видит игрока (включая его самого). */
	public static void updateTabName(ServerPlayer player) {
		var packet = new ClientboundPlayerInfoUpdatePacket(
				ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player);
		for (ServerPlayer other : playerList()) {
			other.connection.send(packet);
		}
	}

	private static java.util.List<ServerPlayer> playerList() {
		return NineLives.server() != null ? NineLives.server().getPlayerList().getPlayers() : java.util.List.of();
	}

	/** Sync-payload с текущими данными игрока. */
	public static PayloadTypes.SyncPayload buildSync(ServerPlayer player) {
		PlayerData data = player.getAttached(Attachments.PLAYER_DATA);
		if (data == null) data = PlayerData.DEFAULT;
		int mode = ModeManager.modeId(player);
		int perLife = Math.max(1, ModeManager.effectiveMode(NineLives.serverOf(player))
				.advancementsPerLife(NineLives.config()));
		// periodic — счётчик за всё время; клиенту нужен прогресс текущего цикла.
		int progress = ModeManager.currentPeriodic(player) % perLife;
		long protectionTicks = Math.max(0L, data.protectionUntil() - NineLives.serverOf(player).getTickCount());
		int protection = (int) ((protectionTicks + 19L) / 20L);
		return new PayloadTypes.SyncPayload(
				data.lives(), data.counted().size(),
				progress, perLife,
				mode, protection, data.eliminated(), data.newLifePulse());
	}
}
