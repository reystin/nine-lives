package com.reystin.ninelives.net;

import com.reystin.ninelives.NineLives;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Все payload-каналы мода. Каналы опциональны: сервер шлёт S2C-пакеты только
 * игрокам с модом (после handshake), поэтому ванильный клиент подключается
 * без ошибок и киков за отсутствие мода.
 */
public final class PayloadTypes {
	private PayloadTypes() {
	}

	// =====================================================================
	// C2S: клиент с модом -> сервер
	// =====================================================================

	/** Handshake: клиент сообщает серверу, что мод установлен. */
	public record HelloPayload() implements CustomPacketPayload {
		public static final HelloPayload INSTANCE = new HelloPayload();
		public static final CustomPacketPayload.Type<HelloPayload> TYPE =
				new CustomPacketPayload.Type<>(id("hello"));
		public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, HelloPayload> CODEC =
				StreamCodec.unit(INSTANCE);

		@Override
		public CustomPacketPayload.Type<HelloPayload> type() {
			return TYPE;
		}
	}

	/** Запрос данных для экрана жизней. */
	public record OpenScreenPayload() implements CustomPacketPayload {
		public static final OpenScreenPayload INSTANCE = new OpenScreenPayload();
		public static final CustomPacketPayload.Type<OpenScreenPayload> TYPE =
				new CustomPacketPayload.Type<>(id("open_screen"));
		public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, OpenScreenPayload> CODEC =
				StreamCodec.unit(INSTANCE);

		@Override
		public CustomPacketPayload.Type<OpenScreenPayload> type() {
			return TYPE;
		}
	}

	/** Передача жизней с экрана. */
	public record GivePayload(UUID target, int amount) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<GivePayload> TYPE =
				new CustomPacketPayload.Type<>(id("give"));
		public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, GivePayload> CODEC =
				StreamCodec.composite(
						net.minecraft.core.UUIDUtil.STREAM_CODEC, GivePayload::target,
						ByteBufCodecs.VAR_INT, GivePayload::amount,
						GivePayload::new);

		@Override
		public CustomPacketPayload.Type<GivePayload> type() {
			return TYPE;
		}
	}

	/** Выбор режима с экрана выбора (0 = vanilla, 1 = bc). */
	public record ModeSelectPayload(int mode) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ModeSelectPayload> TYPE =
				new CustomPacketPayload.Type<>(id("mode_select"));
		public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ModeSelectPayload> CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_INT, ModeSelectPayload::mode, ModeSelectPayload::new);

		@Override
		public CustomPacketPayload.Type<ModeSelectPayload> type() {
			return TYPE;
		}
	}

	// =====================================================================
	// S2C: сервер -> клиент с модом
	// =====================================================================

	/** Периодическая синхронизация данных игрока для HUD/индикаторов. */
	public record SyncPayload(
			int lives,
			int advCount,
			int progress,
			int perLife,
			int mode, // -1 не выбран, 0 vanilla, 1 bc
			int protectionSeconds,
			boolean eliminated,
			boolean newLifePulse
	) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<SyncPayload> TYPE =
				new CustomPacketPayload.Type<>(id("sync"));
		public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, SyncPayload> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.VAR_INT, SyncPayload::lives,
						ByteBufCodecs.VAR_INT, SyncPayload::advCount,
						ByteBufCodecs.VAR_INT, SyncPayload::progress,
						ByteBufCodecs.VAR_INT, SyncPayload::perLife,
						ByteBufCodecs.VAR_INT, SyncPayload::mode,
						ByteBufCodecs.VAR_INT, SyncPayload::protectionSeconds,
						ByteBufCodecs.BOOL, SyncPayload::eliminated,
						ByteBufCodecs.BOOL, SyncPayload::newLifePulse,
						SyncPayload::new);

		@Override
		public CustomPacketPayload.Type<SyncPayload> type() {
			return TYPE;
		}
	}

	/** Данные для экрана жизней: свой статус + карточки игроков + ближайшие/хардовые достижения. */
	public record ScreenDataPayload(
			SyncPayload self,
			java.util.List<PlayerEntry> players,
			java.util.List<AdvEntry> nearest,
			java.util.List<AdvEntry> hard
	) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ScreenDataPayload> TYPE =
				new CustomPacketPayload.Type<>(id("screen_data"));

		public record PlayerEntry(UUID uuid, String name, int lives, int advCount, boolean spectator) {
			public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, PlayerEntry> CODEC =
					StreamCodec.composite(
							net.minecraft.core.UUIDUtil.STREAM_CODEC, PlayerEntry::uuid,
							ByteBufCodecs.STRING_UTF8, PlayerEntry::name,
							ByteBufCodecs.VAR_INT, PlayerEntry::lives,
							ByteBufCodecs.VAR_INT, PlayerEntry::advCount,
							ByteBufCodecs.BOOL, PlayerEntry::spectator,
							PlayerEntry::new);
		}

		/**
		 * Запись достижения. Заголовок и описание приходят с сервера уже
		 * локализованными компонентами — клиент не зависит от того, успело ли
		 * синхронизироваться дерево достижений (BACAP, revoke, чужой пак).
		 */
		public record AdvEntry(String id, net.minecraft.network.chat.Component title,
				net.minecraft.network.chat.Component description, boolean done, int percent) {
			public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, AdvEntry> CODEC =
					StreamCodec.composite(
							ByteBufCodecs.STRING_UTF8, AdvEntry::id,
							net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC, AdvEntry::title,
							net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC, AdvEntry::description,
							ByteBufCodecs.BOOL, AdvEntry::done,
							ByteBufCodecs.VAR_INT, AdvEntry::percent,
							AdvEntry::new);
		}

		public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ScreenDataPayload> CODEC =
				StreamCodec.of(ScreenDataPayload::encode, ScreenDataPayload::decode);

		private static void encode(net.minecraft.network.RegistryFriendlyByteBuf buf, ScreenDataPayload payload) {
			SyncPayload.CODEC.encode(buf, payload.self);
			ByteBufCodecs.VAR_INT.encode(buf, payload.players.size());
			for (PlayerEntry entry : payload.players) PlayerEntry.CODEC.encode(buf, entry);
			ByteBufCodecs.VAR_INT.encode(buf, payload.nearest.size());
			for (AdvEntry entry : payload.nearest) AdvEntry.CODEC.encode(buf, entry);
			ByteBufCodecs.VAR_INT.encode(buf, payload.hard.size());
			for (AdvEntry entry : payload.hard) AdvEntry.CODEC.encode(buf, entry);
		}

		private static ScreenDataPayload decode(net.minecraft.network.RegistryFriendlyByteBuf buf) {
			SyncPayload self = SyncPayload.CODEC.decode(buf);
			int playerCount = ByteBufCodecs.VAR_INT.decode(buf);
			var players = new java.util.ArrayList<PlayerEntry>(playerCount);
			for (int i = 0; i < playerCount; i++) players.add(PlayerEntry.CODEC.decode(buf));
			int nearestCount = ByteBufCodecs.VAR_INT.decode(buf);
			var nearest = new java.util.ArrayList<AdvEntry>(nearestCount);
			for (int i = 0; i < nearestCount; i++) nearest.add(AdvEntry.CODEC.decode(buf));
			int hardCount = ByteBufCodecs.VAR_INT.decode(buf);
			var hard = new java.util.ArrayList<AdvEntry>(hardCount);
			for (int i = 0; i < hardCount; i++) hard.add(AdvEntry.CODEC.decode(buf));
			return new ScreenDataPayload(self, players, nearest, hard);
		}

		@Override
		public CustomPacketPayload.Type<ScreenDataPayload> type() {
			return TYPE;
		}
	}

	/** Событие жизни: 0 = получена, 1 = потеряна, 2 = выбывание, 3 = возврат в игру. */
	public record LifeEventPayload(int kind, java.util.Optional<String> advancement) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<LifeEventPayload> TYPE =
				new CustomPacketPayload.Type<>(id("life_event"));
		public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, LifeEventPayload> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.VAR_INT, LifeEventPayload::kind,
						ByteBufCodecs.stringUtf8(512).map(
								s -> s.isEmpty() ? java.util.Optional.<String>empty() : java.util.Optional.of(s),
								o -> o.orElse("")), LifeEventPayload::advancement,
						LifeEventPayload::new);

		@Override
		public CustomPacketPayload.Type<LifeEventPayload> type() {
			return TYPE;
		}
	}

	/** Анимация передачи жизни: летящее сердце от отправителя к получателю. */
	public record TransferFxPayload(String from, String to, int amount) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<TransferFxPayload> TYPE =
				new CustomPacketPayload.Type<>(id("transfer_fx"));
		public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, TransferFxPayload> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.STRING_UTF8, TransferFxPayload::from,
						ByteBufCodecs.STRING_UTF8, TransferFxPayload::to,
						ByteBufCodecs.VAR_INT, TransferFxPayload::amount,
						TransferFxPayload::new);

		@Override
		public CustomPacketPayload.Type<TransferFxPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Прогресс установки BACAP: 5=подготовка, 0=скачивание, 1=проверка,
	 * 2=включение, 3=готово, 4=ошибка.
	 */
	public record DownloadProgressPayload(int stage, int percent, String errorKey, String errorDetail)
			implements CustomPacketPayload {
		public static final int STAGE_PREPARING = 5;
		public static final int STAGE_DOWNLOADING = 0;
		public static final int STAGE_VERIFYING = 1;
		public static final int STAGE_ENABLING = 2;
		public static final int STAGE_DONE = 3;
		public static final int STAGE_ERROR = 4;

		public static final CustomPacketPayload.Type<DownloadProgressPayload> TYPE =
				new CustomPacketPayload.Type<>(id("download_progress"));
		public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, DownloadProgressPayload> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.VAR_INT, DownloadProgressPayload::stage,
						ByteBufCodecs.VAR_INT, DownloadProgressPayload::percent,
						ByteBufCodecs.STRING_UTF8, DownloadProgressPayload::errorKey,
						ByteBufCodecs.STRING_UTF8, DownloadProgressPayload::errorDetail,
						DownloadProgressPayload::new);

		@Override
		public CustomPacketPayload.Type<DownloadProgressPayload> type() {
			return TYPE;
		}
	}

	/** Мир без выбранного режима: запрос выбора оператору/хосту. */
	public record ModePromptPayload(int vanillaPerLife, int bacapPerLife) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ModePromptPayload> TYPE =
				new CustomPacketPayload.Type<>(id("mode_prompt"));
		public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ModePromptPayload> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.VAR_INT, ModePromptPayload::vanillaPerLife,
						ByteBufCodecs.VAR_INT, ModePromptPayload::bacapPerLife,
						ModePromptPayload::new);

		@Override
		public CustomPacketPayload.Type<ModePromptPayload> type() {
			return TYPE;
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NineLives.MOD_ID, path);
	}
}
