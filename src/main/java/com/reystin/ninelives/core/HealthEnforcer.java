package com.reystin.ninelives.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Жёсткий максимум здоровья 1.0 (пол-сердца):
 * - базовое значение атрибута MAX_HEALTH = 1.0;
 * - каждый тик снимаются ВСЕ модификаторы MAX_HEALTH — это закрывает эффект
 *   «Прилив здоровья» (HEALTH_BOOST), модификаторы предметов и
 *   /attribute ... modifier;
 * - здоровье не может превышать максимум: health = min(health, 1.0).
 *
 * Поглощение, золотые сердца и тотем не трогаются: у них отдельные атрибуты
 * (MAX_ABSORPTION/ABSORPTION), а тотем сам ставит health = 1.0.
 */
public final class HealthEnforcer {
	private HealthEnforcer() {
	}

	public static void enforce(ServerPlayer player) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			if (maxHealth.getBaseValue() != 1.0) {
				maxHealth.setBaseValue(1.0);
			}
			// Снимаем все модификаторы (эффекты, предметы, команды): любой из них
			// поднял бы итоговый максимум выше 1.0. Итерируемся по копии —
			// removeModifier изменяет исходную коллекцию.
			for (AttributeModifier modifier : java.util.List.copyOf(maxHealth.getModifiers())) {
				maxHealth.removeModifier(modifier);
			}
		}
		// Итоговый кламп здоровья (на случай, если health уже успел вырасти).
		if (player.getHealth() > 1.0F) {
			player.setHealth(1.0F);
		}
	}
}
