package com.reystin.ninelives.core;

import com.reystin.ninelives.NineLives;
import com.reystin.ninelives.advancement.AdvancementCounter;
import com.reystin.ninelives.bacap.BacapInstaller;
import com.reystin.ninelives.net.PayloadTypes;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Хуки в игровые события: смерть, возрождение, вход/выход, урон, тик сервера
 * (кламп здоровья + защита + синхронизация), приём C2S-пакетов.
 */
public final class CommonEvents {
	/** Операторы, которым уже показывали выбор режима за этот запуск сервера. */
	private static final Set<UUID> MODE_PROMPTED = new HashSet<>();
	/** Операторы, которым уже сообщали о невозможности авто-установки BACAP. */
	private static final Set<UUID> BACAP_NOTIFIED = new HashSet<>();

	private CommonEvents() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			NineLives.setServer(server);
			BacapInstaller.resetSession();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> NineLives.setServer(null));
		// После перезагрузки датапаков (например, после установки BACAP) пересчитать прогресс.
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				AdvancementCounter.recalculateAll(server);
			}
		});

		// ===== Тик сервера: кламп здоровья, защита, синхронизация =====
		ServerTickEvents.END_SERVER_TICK.register(CommonEvents::onEndTick);

		// ===== Урон =====
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CommonEvents::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(CommonEvents::afterDamage);

		// ===== Смерть и возрождение =====
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				LivesManager.onDeath(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> LivesManager.onRespawn(newPlayer));

		// ===== Вход игрока =====
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			LivesManager.onJoin(player);
			AdvancementCounter.importExisting(player);
			promptModeIfNotChosen(player);
			BacapInstaller.ensureInstalled(server, player, BACAP_NOTIFIED);
		});

		// ===== C2S пакеты от клиентов с модом =====
		ServerPlayNetworking.registerGlobalReceiver(PayloadTypes.HelloPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.setAttached(Attachments.MODDED_CLIENT, Boolean.TRUE);
			Net.syncTo(player);
			promptModeIfNotChosen(player);
			BacapInstaller.ensureInstalled(NineLives.serverOf(player), player, BACAP_NOTIFIED);
		});
		ServerPlayNetworking.registerGlobalReceiver(PayloadTypes.OpenScreenPayload.TYPE, (payload, context) ->
				handleOpenScreen(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(PayloadTypes.GivePayload.TYPE, (payload, context) -> {
			ServerPlayer from = context.player();
			ServerPlayer to = LivesManager.byUuid(payload.target());
			if (to == null) {
				ServerMessages.chat(from, "ninelives.msg.give_nothing");
				return;
			}
			// transfer сам обновляет экраны обоих игроков.
			LivesManager.transfer(from, to, payload.amount());
		});
		ServerPlayNetworking.registerGlobalReceiver(PayloadTypes.ModeSelectPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			MinecraftServer server = NineLives.serverOf(player);
			if (ServerMessages.isPrompter(player) && !ModeManager.isModeChosen(server)) {
				Mode mode = payload.mode() == 1 ? Mode.BACAP : Mode.VANILLA;
				// setMode синхронизирует клиентов; для BACAP сразу запускается авто-установка.
				ModeManager.setMode(server, mode);
				ServerMessages.broadcast("ninelives.msg.mode_selected", mode.translationKey());
			}
		});
	}

	// =====================================================================
	// Тик
	// =====================================================================

	/** Открытие экрана «гасит» пульс кнопки инвентаря и обновляет данные. */
	public static void handleOpenScreen(ServerPlayer player) {
		if (LivesManager.data(player).newLifePulse()) {
			player.modifyAttached(Attachments.PLAYER_DATA, d -> new PlayerData(
					d.lives(), d.counted(), d.periodicVanilla(), d.periodicBacap(),
					d.eliminated(), d.protectionUntil(), false));
		}
		Net.sendIfModded(player, ScreenData.build(player));
	}

	private static void onEndTick(MinecraftServer server) {
		int tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// Жёсткий максимум здоровья.
			HealthEnforcer.enforce(player);

			// Защита после возрождения: обратный отсчёт.
			PlayerData data = player.getAttached(Attachments.PLAYER_DATA);
			if (data != null && data.protectionUntil() > 0) {
				long remainingTicks = data.protectionUntil() - tick;
				if (remainingTicks <= 0) {
					LivesManager.endProtection(player, false);
				} else if (tick % 20 == 0) {
					int seconds = (int) ((remainingTicks + 19) / 20);
					if (isModded(player)) {
						Net.syncTo(player);
					} else {
						ServerMessages.actionbar(player, "ninelives.hud.protection", seconds);
					}
				}
			}

			// Tab-имена обновляем периодически как страховку (дёшево, раз в 5 секунд).
			if (tick % 100 == (player.getId() % 100)) {
				ServerMessages.updateTabName(player);
			}
		}
	}

	private static boolean isModded(ServerPlayer player) {
		return Net.isModded(player);
	}

	// =====================================================================
	// Урон
	// =====================================================================

	/** Защита после возрождения: урон отменяется (кроме /kill и пустоты). */
	private static boolean allowDamage(net.minecraft.world.entity.LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayer player && LivesManager.isProtected(player)) {
			return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		}
		return true;
	}

	/** Защита снимается досрочно, если защищённый игрок сам атакует. */
	private static void afterDamage(net.minecraft.world.entity.LivingEntity entity, DamageSource source, float amount, float absorbed, boolean blocked) {
		if (source.getEntity() instanceof ServerPlayer attacker && LivesManager.isProtected(attacker)) {
			LivesManager.endProtection(attacker, true);
		}
	}

	// =====================================================================
	// Диалоги для операторов
	// =====================================================================

	private static void promptModeIfNotChosen(ServerPlayer player) {
		if (ModeManager.isModeChosen(NineLives.serverOf(player)) || !ServerMessages.isPrompter(player)) {
			return;
		}
		// Хост одиночной игры может быть без OP — isPrompter это учитывает.
		if (isModded(player)) {
			Net.sendIfModded(player, new PayloadTypes.ModePromptPayload(
					NineLives.config().advancementsPerLifeVanilla, NineLives.config().advancementsPerLifeBacap));
		} else if (MODE_PROMPTED.add(player.getUUID())) {
			ServerMessages.chat(player, "ninelives.msg.mode_prompt");
			player.sendSystemMessage(com.reystin.ninelives.command.Clickables.modeButtons());
		}
	}
}
