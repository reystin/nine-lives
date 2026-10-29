package com.reystin.ninelives.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import com.reystin.ninelives.NineLives;
import net.minecraft.resources.Identifier;

/**
 * Мировое состояние Nine Lives (сохраняется в папке мира):
 * выбранный режим достижений (null = ещё не выбран — действует defaultMode из конфига).
 */
public class WorldState extends SavedData {
	public static final SavedDataType<WorldState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(NineLives.MOD_ID, "world_state"),
			WorldState::new,
			RecordCodecBuilder.create(instance -> instance.group(
					Codec.STRING.optionalFieldOf("mode", "").forGetter(s -> s.mode == null ? "" : s.mode)
			).apply(instance, mode -> {
				WorldState state = new WorldState();
				state.mode = mode.isEmpty() ? null : mode;
				return state;
			})),
			// Нейтральный тип датафикса: версии файла всегда совпадают с версией
			// игры, поэтому update() — no-op; null здесь дал бы NPE при загрузке.
			net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	/** Выбранный режим: "vanilla" | "bc" | null (не выбран). */
	public String mode;

	public WorldState() {
	}

	public void setMode(String mode) {
		this.mode = mode;
		setDirty();
	}
}
