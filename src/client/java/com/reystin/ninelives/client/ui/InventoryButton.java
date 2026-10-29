package com.reystin.ninelives.client.ui;

import com.reystin.ninelives.client.ClientState;
import com.reystin.ninelives.client.hud.Textures;
import com.reystin.ninelives.net.PayloadTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * Кнопка «Nine Lives» в инвентаре: иконка сердца с бейджем числа жизней,
 * увеличение при наведении, пульс пока новая жизнь не просмотрена.
 */
public class InventoryButton extends AbstractWidget {
	private final Minecraft minecraft;
	private float hoverGrow = 1f;
	private long lastFrameNanos;

	private InventoryButton(Minecraft minecraft, int x, int y) {
		super(x, y, 14, 14, Component.translatable("ninelives.screen.title"));
		this.minecraft = minecraft;
	}

	/** Добавить кнопку в правый верхний угол инвентаря. */
	public static void addTo(InventoryScreen screen, Minecraft minecraft) {
		net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen)
				.add(new InventoryButton(minecraft, screen.width - 30, 6));
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		PayloadTypes.SyncPayload sync = ClientState.sync;
		if (sync == null) return;

		boolean hovered = this.isHovered;
		// Плавный рост при наведении (~200 мс, не зависит от FPS).
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
		lastFrameNanos = now;
		float target = hovered ? 1.25f : 1f;
		hoverGrow += (target - hoverGrow) * Math.min(1f, dt * 14f);

		// Пульс, пока есть непросмотренная новая жизнь.
		float pulseScale = 1f;
		if (sync.newLifePulse()) {
			float t = (System.nanoTime() % 900_000_000L) / 900_000_000f;
			pulseScale = 1f + 0.18f * (float) Math.sin(t * Math.PI * 2);
		}

		float scale = hoverGrow * pulseScale;
		int size = Math.round(14 * scale);
		int x = this.getX() + (this.width - size) / 2;
		int y = this.getY() + (this.height - size) / 2;

		// Панелька-фон.
		graphics.fill(this.getX() - 1, this.getY() - 1, this.getX() + this.width + 1, this.getY() + this.height + 1,
				hovered ? 0x6630141C : 0x441A0A10);

		Textures.full(graphics, Textures.HEART_FULL, x, y, size, size);

		// Бейдж с числом жизней.
		String badge = String.valueOf(sync.lives());
		int badgeWidth = this.minecraft.font.width(badge);
		int chipW = Math.max(14, badgeWidth + 6);
		int chipH = 11;
		int chipX = this.getX() + this.width - chipW + 1;
		int chipY = this.getY() + this.height - chipH + 1;
		Textures.full(graphics, Textures.BADGE, chipX, chipY, chipW, chipH);
		graphics.centeredText(this.minecraft.font, badge, chipX + chipW / 2, chipY + 2,
				sync.lives() <= 2 ? 0xFFFF5555 : 0xFFFFD980);

		// Подсказка при наведении.
		if (hovered) {
			graphics.setTooltipForNextFrame(
					Component.translatable("ninelives.inventory_button.tooltip", sync.lives()), mouseX, mouseY);
		}
	}

	@Override
	public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
		com.reystin.ninelives.client.NineLivesClient.openLivesScreen();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
}
