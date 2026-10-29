package com.reystin.ninelives.client;

import com.reystin.ninelives.NineLives;
import com.reystin.ninelives.client.hud.HudElements;
import com.reystin.ninelives.client.ui.InventoryButton;
import com.reystin.ninelives.client.ui.LivesScreen;
import com.reystin.ninelives.config.NLClientConfig;
import com.reystin.ninelives.net.PayloadTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;

/**
 * Полный клиентский интерфейс мода. Отсутствует в серверной сборке: серверный
 * jar не содержит пакет client, поэтому на выделенном сервере этот код
 * никогда не загружается.
 */
public class NineLivesClient implements ClientModInitializer {
	/** Клавиша открытия экрана жизней (настраивается в управлении). */
	public static KeyMapping openScreenKey;

	@Override
	public void onInitializeClient() {
		NLClientConfig.load();

		// Клавиша O — открыть экран жизней (SDL: коды клавиш через InputConstants).
		openScreenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.ninelives.open",
				com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD,
				com.mojang.blaze3d.platform.InputConstants.KEY_O,
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath(NineLives.MOD_ID, "main"))));

		ClientNet.register();
		HudElements.register();
		Showcase.init();

		// Клавиша.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openScreenKey.consumeClick()) {
				openLivesScreen();
			}
		});

		// Кнопка в инвентаре (справа сверху).
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof InventoryScreen inventoryScreen) {
				InventoryButton.addTo(inventoryScreen, client);
			}
		});

		NineLives.LOGGER.info("[Nine Lives] client initialized");
	}

	/** Открыть экран жизней: сразу показываем окно, данные подтянутся с сервера. */
	public static void openLivesScreen() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		ClientNet.requestScreenData();
		mc.gui.setScreen(new LivesScreen());
	}
}
