package com.reystin.ninelives.core;

import com.reystin.ninelives.NineLives;
import com.reystin.ninelives.net.PayloadTypes;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Сетевой слой серверной части: регистрация кодеков (на обеих сторонах) и
 * отправка только тем клиентам, у которых есть мод.
 */
public final class Net {
	private Net() {
	}

	/** Регистрация payload-типов. Вызывается в общем инициализаторе — кодеки нужны обеим сторонам. */
	public static void registerTypes() {
		// C2S
		PayloadTypeRegistry.serverboundPlay().register(PayloadTypes.HelloPayload.TYPE, PayloadTypes.HelloPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PayloadTypes.OpenScreenPayload.TYPE, PayloadTypes.OpenScreenPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PayloadTypes.GivePayload.TYPE, PayloadTypes.GivePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PayloadTypes.ModeSelectPayload.TYPE, PayloadTypes.ModeSelectPayload.CODEC);
		// S2C
		PayloadTypeRegistry.clientboundPlay().register(PayloadTypes.SyncPayload.TYPE, PayloadTypes.SyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PayloadTypes.ScreenDataPayload.TYPE, PayloadTypes.ScreenDataPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PayloadTypes.LifeEventPayload.TYPE, PayloadTypes.LifeEventPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PayloadTypes.TransferFxPayload.TYPE, PayloadTypes.TransferFxPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PayloadTypes.DownloadProgressPayload.TYPE, PayloadTypes.DownloadProgressPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PayloadTypes.ModePromptPayload.TYPE, PayloadTypes.ModePromptPayload.CODEC);
	}

	/** У клиента игрока есть мод (handshake получен)? */
	public static boolean isModded(ServerPlayer player) {
		if (player == null) return false;
		Boolean modded = player.getAttached(Attachments.MODDED_CLIENT);
		return modded != null && modded;
	}

	/** Отправка payload игроку с модом; ванильному клиенту ничего не шлём. */
	public static void sendIfModded(ServerPlayer player, CustomPacketPayload payload) {
		if (player != null && isModded(player)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** Полная синхронизация данных игрока его клиенту (если у него есть мод). */
	public static void syncTo(ServerPlayer player) {
		sendIfModded(player, ServerMessages.buildSync(player));
	}
}
