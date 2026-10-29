package com.reystin.ninelives.core;

import com.reystin.ninelives.NineLives;
import com.reystin.ninelives.advancement.AdvancementCounter;
import com.reystin.ninelives.net.PayloadTypes;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Сборка данных для экрана жизней (S2C screen_data): свой статус, карточки
 * всех онлайн-игроков, ближайшие достижения и хард-список.
 */
public final class ScreenData {
	private ScreenData() {
	}

	public static PayloadTypes.ScreenDataPayload build(ServerPlayer player) {
		var server = NineLives.serverOf(player);
		List<PayloadTypes.ScreenDataPayload.PlayerEntry> players = new ArrayList<>();
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			PlayerData data = other.getAttached(Attachments.PLAYER_DATA);
			players.add(new PayloadTypes.ScreenDataPayload.PlayerEntry(
					other.getUUID(), other.nameAndId().name(),
					data != null ? data.lives() : 0,
					data != null ? data.counted().size() : 0,
					data != null && data.eliminated()));
		}
		return new PayloadTypes.ScreenDataPayload(
				ServerMessages.buildSync(player),
				players,
				AdvancementCounter.nearestAdvancements(player, 14),
				AdvancementCounter.hardListFor(player));
	}
}
