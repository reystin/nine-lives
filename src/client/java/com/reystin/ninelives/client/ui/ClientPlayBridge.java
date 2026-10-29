package com.reystin.ninelives.client.ui;

import com.reystin.ninelives.client.ClientNet;
import com.reystin.ninelives.net.PayloadTypes;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.UUID;

/**
 * Мостик для отправки C2S-команд с экранов.
 */
public final class ClientPlayBridge {
	private ClientPlayBridge() {
	}

	/** Передать жизни с экрана. */
	public static void give(UUID target, int amount) {
		ClientPlayNetworking.send(new PayloadTypes.GivePayload(target, amount));
		ClientNet.requestScreenData(); // обновить данные после передачи
	}
}
