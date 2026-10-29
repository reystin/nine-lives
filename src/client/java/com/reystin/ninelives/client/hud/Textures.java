package com.reystin.ninelives.client.hud;

import com.reystin.ninelives.NineLives;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Текстуры (пиксель-арт мода) и хелперы отрисовки.
 * В 26.3 blit принимает нормализованные UV — все координаты текстур здесь
 * задаются в пикселях и нормализуются в {@link #region}.
 */
public final class Textures {
	private static final String GUI = "textures/gui/";

	/** Сердца для HUD и строк (9×9). */
	public static final Identifier HEART_FULL = tex("heart_full.png");
	public static final Identifier HEART_HALF = tex("heart_half.png");
	public static final Identifier HEART_CRACKED = tex("heart_cracked.png");
	public static final Identifier HEART_FULL_GOLD = tex("heart_full_gold.png");
	/** Крупные сердца для экрана жизней (32×32). */
	public static final Identifier HEART_BIG_FULL = tex("heart_big_full.png");
	public static final Identifier HEART_BIG_CRACKED = tex("heart_big_cracked.png");
	public static final Identifier HEART_BIG_GOLD = tex("heart_big_gold.png");

	/** Панель 24×24, рамка 8 px (9-slice). */
	public static final Identifier PANEL = tex("panel.png");
	public static final Identifier PANEL_HIGHLIGHT = tex("panel_highlight.png");
	public static final Identifier BUTTON = tex("button.png");
	public static final Identifier BUTTON_HOVER = tex("button_hover.png");
	public static final Identifier TAB = tex("tab.png");
	public static final Identifier TAB_ACTIVE = tex("tab_active.png");
	public static final Identifier PROGRESS_FRAME = tex("progress_frame.png");
	public static final Identifier PROGRESS_FILL = tex("progress_fill.png");
	public static final Identifier BADGE = tex("badge.png");

	public static final int PANEL_TEX_SIZE = 24;
	public static final int PANEL_BORDER = 8;
	public static final int BUTTON_W = 96;
	public static final int BUTTON_H = 20;
	public static final int BUTTON_BORDER = 4;
	public static final int TAB_W = 88;
	public static final int TAB_H = 20;
	public static final int TAB_BORDER = 4;
	public static final int PROGRESS_W = 120;
	public static final int PROGRESS_H = 10;
	public static final int PROGRESS_INNER_W = 118;
	public static final int PROGRESS_INNER_H = 8;
	public static final int PROGRESS_CAP = 4;
	public static final int HEART_BIG_SIZE = 32;
	public static final int BADGE_SIZE = 16;

	/**
	 * Отрисовка участка текстуры. Координаты UV — в пикселях текстуры
	 * (texW × texH), нормализуются внутри.
	 */
	public static void region(GuiGraphicsExtractor g, Identifier tex,
			int x0, int y0, int x1, int y1,
			float u0, float v0, float u1, float v1, int texW, int texH) {
		g.blit(tex, x0, y0, x1, y1, u0 / texW, u1 / texW, v0 / texH, v1 / texH);
	}

	/** Отрисовка всей текстуры в прямоугольник произвольного размера. */
	public static void full(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, int h) {
		g.blit(tex, x, y, x + w, y + h, 0f, 1f, 0f, 1f);
	}

	/** 9-slice: углы 8 px, края и центр растягиваются. */
	public static void nineSlice(GuiGraphicsExtractor g, Identifier tex, int x, int y, int width, int height) {
		nineSlice(g, tex, x, y, width, height, PANEL_TEX_SIZE, PANEL_TEX_SIZE, PANEL_BORDER);
	}

	/** 9-slice для неквадратных текстур (кнопки, вкладки). */
	public static void nineSlice(GuiGraphicsExtractor g, Identifier tex, int x, int y, int width, int height,
			int texW, int texH, int border) {
		int r = border;
		if (width < r * 2 || height < r * 2) {
			full(g, tex, x, y, width, height);
			return;
		}
		region(g, tex, x + r, y + r, x + width - r, y + height - r, r, r, texW - r, texH - r, texW, texH);
		region(g, tex, x + r, y, x + width - r, y + r, r, 0, texW - r, r, texW, texH);
		region(g, tex, x + r, y + height - r, x + width - r, y + height, r, texH - r, texW - r, texH, texW, texH);
		region(g, tex, x, y + r, x + r, y + height - r, 0, r, r, texH - r, texW, texH);
		region(g, tex, x + width - r, y + r, x + width, y + height - r, texW - r, r, texW, texH - r, texW, texH);
		region(g, tex, x, y, x + r, y + r, 0, 0, r, r, texW, texH);
		region(g, tex, x + width - r, y, x + width, y + r, texW - r, 0, texW, r, texW, texH);
		region(g, tex, x, y + height - r, x + r, y + height, 0, texH - r, r, texH, texW, texH);
		region(g, tex, x + width - r, y + height - r, x + width, y + height, texW - r, texH - r, texW, texH, texW, texH);
	}

	/** Кнопка: 9-slice из button.png / button_hover.png. */
	public static void button(GuiGraphicsExtractor g, boolean hovered, int x, int y, int width, int height) {
		nineSlice(g, hovered ? BUTTON_HOVER : BUTTON, x, y, width, height, BUTTON_W, BUTTON_H, BUTTON_BORDER);
	}

	/** Вкладка: 9-slice из tab.png / tab_active.png. */
	public static void tab(GuiGraphicsExtractor g, boolean active, int x, int y, int width, int height) {
		nineSlice(g, active ? TAB_ACTIVE : TAB, x, y, width, height, TAB_W, TAB_H, TAB_BORDER);
	}

	/** Горизонтальное растяжение с фиксированными торцами (полосы прогресса). */
	public static void hStretch(GuiGraphicsExtractor g, Identifier tex, int x, int y, int width, int height,
			int texW, int texH, int cap) {
		if (width <= cap * 2) {
			region(g, tex, x, y, x + width, y + height, 0, 0, texW, texH, texW, texH);
			return;
		}
		region(g, tex, x, y, x + cap, y + height, 0, 0, cap, texH, texW, texH);
		region(g, tex, x + cap, y, x + width - cap, y + height, cap, 0, texW - cap, texH, texW, texH);
		region(g, tex, x + width - cap, y, x + width, y + height, texW - cap, 0, texW, texH, texW, texH);
	}

	private static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath(NineLives.MOD_ID, GUI + name);
	}

	private Textures() {
	}
}
