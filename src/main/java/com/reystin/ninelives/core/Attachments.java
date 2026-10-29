package com.reystin.ninelives.core;

import com.reystin.ninelives.NineLives;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;

import java.util.HashSet;

/**
 * Attachment-типы мода. PlayerData копируется при смерти (copyOnDeath) и
 * сохраняется на диск (persistent) — см. {@link PlayerData}.
 */
public final class Attachments {
	/** Данные жизней игрока. */
	public static final AttachmentType<PlayerData> PLAYER_DATA = AttachmentRegistry.<PlayerData>builder()
			.initializer(() -> new PlayerData(
					NineLives.config().startingLives, new HashSet<>(), 0, 0, false, 0L, false))
			.persistent(PlayerData.CODEC)
			.copyOnDeath()
			.buildAndRegister(Identifier.fromNamespaceAndPath(NineLives.MOD_ID, "player_data"));

	/**
	 * Есть ли у клиента игрока мод (устанавливается handshake-пакетом).
	 * Не сохраняется на диск и копируется при смерти: после респавна сервер
	 * продолжает слать интерфейсные пакеты этому же клиенту.
	 */
	public static final AttachmentType<Boolean> MODDED_CLIENT = AttachmentRegistry.<Boolean>builder()
			.initializer(() -> Boolean.FALSE)
			.copyOnDeath()
			.buildAndRegister(Identifier.fromNamespaceAndPath(NineLives.MOD_ID, "modded_client"));

	private Attachments() {
	}
}
