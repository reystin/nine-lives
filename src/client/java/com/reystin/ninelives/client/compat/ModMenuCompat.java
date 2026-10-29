package com.reystin.ninelives.client.compat;

import com.reystin.ninelives.client.ui.ConfigScreen;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Интеграция с Mod Menu: кнопка настроек. Энтрипоинт загружается только
 * самим Mod Menu, поэтому при его отсутствии класс просто не инстанцируется.
 */
public class ModMenuCompat implements ModMenuApi {
	@Override
	public com.terraformersmc.modmenu.api.ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
