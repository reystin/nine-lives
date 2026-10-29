package com.reystin.ninelives.client.ui;

import com.reystin.ninelives.config.NLClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Экран настроек полной версии: HUD, анимации, частицы, звуки, масштаб.
 * Открывается через Mod Menu; без него настройки правятся в
 * config/ninelives-client.json.
 */
public class ConfigScreen extends Screen {
	private final Screen parent;
	private float scaleValue;

	public ConfigScreen(Screen parent) {
		super(Component.translatable("ninelives.modmenu.title"));
		this.parent = parent;
		this.scaleValue = NLClientConfig.get().uiScale;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = 40;
		NLClientConfig config = NLClientConfig.get();

		this.addRenderableWidget(CycleButton.onOffBuilder(config.hudEnabled)
				.create(centerX - 100, y, 200, 20,
						Component.translatable("ninelives.config.hud"),
						(button, value) -> config.hudEnabled = value));
		y += 24;
		this.addRenderableWidget(CycleButton.onOffBuilder(config.animations)
				.create(centerX - 100, y, 200, 20,
						Component.translatable("ninelives.config.animations"),
						(button, value) -> config.animations = value));
		y += 24;
		this.addRenderableWidget(CycleButton.onOffBuilder(config.particles)
				.create(centerX - 100, y, 200, 20,
						Component.translatable("ninelives.config.particles"),
						(button, value) -> config.particles = value));
		y += 24;
		this.addRenderableWidget(CycleButton.onOffBuilder(config.sounds)
				.create(centerX - 100, y, 200, 20,
						Component.translatable("ninelives.config.sounds"),
						(button, value) -> config.sounds = value));
		y += 24;
		this.addRenderableWidget(new AbstractSliderButton(centerX - 100, y, 200, 20,
				Component.translatable("ninelives.config.scale"), (scaleValue - 0.5) / 1.5) {
			@Override
			protected void updateMessage() {
				this.setMessage(Component.translatable("ninelives.config.scale")
						.append(": " + String.format("%.2f", scaleValue)));
			}

			@Override
			protected void applyValue() {
				scaleValue = (float) Mth.lerp(this.value, 0.5, 2.0);
			}
		});
		y += 28;

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
					config.uiScale = scaleValue;
					config.save();
					this.onClose();
				})
				.bounds(centerX - 100, y, 200, 20)
				.build());
	}

	@Override
	public void onClose() {
		NLClientConfig.get().uiScale = scaleValue;
		NLClientConfig.get().save();
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFD980);
	}
}
