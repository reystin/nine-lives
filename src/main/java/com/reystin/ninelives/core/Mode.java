package com.reystin.ninelives.core;

import com.reystin.ninelives.config.NLConfig;

/**
 * Режимы подсчёта достижений.
 * VANILLA — только namespace minecraft, хард-список даёт +1 жизнь;
 * BACAP — только namespace датапака, супер-челленджи дают +1 жизнь.
 */
public enum Mode {
	VANILLA("vanilla"),
	BACAP("bc");

	public final String id;

	Mode(String id) {
		this.id = id;
	}

	public int advancementsPerLife(NLConfig config) {
		return this == VANILLA ? config.advancementsPerLifeVanilla : config.advancementsPerLifeBacap;
	}

	/** Имя режима для сообщений (переводится на клиенте с модом). */
	public String translationKey() {
		return "ninelives.mode." + id;
	}
}
