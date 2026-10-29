package com.reystin.ninelives.client.ui;

import com.reystin.ninelives.client.hud.Textures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Тост Nine Lives: иконка сердца, заголовок («+1 Жизнь») и опциональная
 * подпись (название достижения). Золотой при получении, красный при потере.
 */
public class NineLivesToast implements Toast {
	private static final int WIDTH = 160;
	private static final int HEIGHT = 32;

	private final Component title;
	private final Component subtitle;
	private final Kind kind;
	private long startTime = -1;

	public NineLivesToast(Component title, Component subtitle, Kind kind) {
		this.title = title;
		this.subtitle = subtitle;
		this.kind = kind;
	}

	@Override
	public Visibility getWantedVisibility() {
		if (startTime == -1) return Visibility.HIDE;
		// Показываем 4 секунды.
		return System.currentTimeMillis() - startTime < 4000L ? Visibility.SHOW : Visibility.HIDE;
	}

	@Override
	public void update(ToastManager manager, long now) {
		if (startTime == -1) startTime = now;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long time) {
		Minecraft mc = Minecraft.getInstance();
		int w = WIDTH;
		int x0 = 0;
		int y0 = 0;
		// Тёмная панель с рамкой цвета события.
		int border = kind == Kind.GOLD ? 0xFFFFC84A : 0xFFFF4444;
		graphics.fill(x0, y0, x0 + w, y0 + HEIGHT, 0xF0100508);
		graphics.fill(x0, y0, x0 + w, y0 + 1, border);
		graphics.fill(x0, y0 + HEIGHT - 1, x0 + w, y0 + HEIGHT, border);
		graphics.fill(x0, y0, x0 + 1, y0 + HEIGHT, border);
		graphics.fill(x0 + w - 1, y0, x0 + w, y0 + HEIGHT, border);

		// Иконка сердца.
		Identifier icon = kind == Kind.GOLD ? Textures.HEART_FULL_GOLD : Textures.HEART_CRACKED;
		com.reystin.ninelives.client.hud.Textures.full(graphics, icon, x0 + 8, y0 + 11, 9, 9);

		// Тексты.
		graphics.text(font, title, x0 + 24, y0 + 7, 0xFFFFFFFF, true);
		if (subtitle != null) {
			var lines = font.split(subtitle, w - 32);
			if (!lines.isEmpty()) {
				graphics.text(font, lines.get(0), x0 + 24, y0 + 18, 0xFFB9B4B9, false);
			}
		}
	}

	/** Кинд тоста: золотой (получение) или красный (потеря). */
	public enum Kind {
		GOLD,
		RED
	}
}
