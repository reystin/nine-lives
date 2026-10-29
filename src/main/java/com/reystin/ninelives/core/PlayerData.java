package com.reystin.ninelives.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Данные игрока Nine Lives, живущие в Fabric Data Attachment:
 * - {@code copyOnDeath()} — данные переживают смерть и возрождение (жизни,
 *   засчитанные достижения и флаг eliminated не обнуляются при респавне);
 * - {@code persistent(...)} — данные сохраняются в playerdata и переживают
 *   выход из мира.
 *
 * @param lives            текущее число жизней (старт 9, максимума нет)
 * @param counted          id достижений, уже засчитанных за всё время (анти-накрутка)
 * @param periodicVanilla  сколько достижений режима vanilla засчитано в текущий цикл
 * @param periodicBacap    сколько достижений BACAP засчитано в текущий цикл
 * @param eliminated       игрок выбыл (0 жизней, спектатор)
 * @param protectionUntil  игровое время окончания защиты после респавна
 * @param newLifePulse     есть непросмотренная новая жизнь (пульс кнопки в инвентаре)
 */
public record PlayerData(
		int lives,
		Set<String> counted,
		int periodicVanilla,
		int periodicBacap,
		boolean eliminated,
		long protectionUntil,
		boolean newLifePulse
) {
	public static final PlayerData DEFAULT =
			new PlayerData(0, Set.of(), 0, 0, false, 0L, false);

	public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("lives").forGetter(PlayerData::lives),
			Codec.STRING.listOf().fieldOf("counted").forGetter(d -> List.copyOf(d.counted)),
			Codec.INT.optionalFieldOf("periodic_vanilla", 0).forGetter(PlayerData::periodicVanilla),
			Codec.INT.optionalFieldOf("periodic_bacap", 0).forGetter(PlayerData::periodicBacap),
			Codec.BOOL.optionalFieldOf("eliminated", false).forGetter(PlayerData::eliminated),
			Codec.LONG.optionalFieldOf("protection_until", 0L).forGetter(PlayerData::protectionUntil),
			Codec.BOOL.optionalFieldOf("new_life_pulse", false).forGetter(PlayerData::newLifePulse)
	).apply(instance, (lives, countedList, pv, pb, eliminated, protection, pulse) ->
			new PlayerData(lives, new HashSet<>(countedList), pv, pb, eliminated, protection, pulse)));
}
