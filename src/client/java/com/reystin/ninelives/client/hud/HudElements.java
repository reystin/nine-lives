package com.reystin.ninelives.client.hud;

import com.reystin.ninelives.NineLives;
import com.reystin.ninelives.client.ClientState;
import com.reystin.ninelives.config.NLClientConfig;
import com.reystin.ninelives.net.PayloadTypes;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * HUD-элементы полной версии:
 * - индикатор жизней у полоски здоровья (иконка сердца + число);
 * - индикатор защиты после возрождения;
 * - красное мерцание краёв экрана при потере жизни;
 * - экран наблюдателя (затемнение + подпись при 0 жизней);
 * - анимация летящего сердца при передаче.
 */
public final class HudElements {
	private static final Identifier LIVES_ID = Identifier.fromNamespaceAndPath(NineLives.MOD_ID, "lives_indicator");
	private static final Identifier PROTECTION_ID = Identifier.fromNamespaceAndPath(NineLives.MOD_ID, "protection");
	private static final Identifier OVERLAYS_ID = Identifier.fromNamespaceAndPath(NineLives.MOD_ID, "overlays");

	private HudElements() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR, LIVES_ID, HudElements::livesIndicator);
		HudElementRegistry.attachElementAfter(LIVES_ID, PROTECTION_ID, HudElements::protectionIndicator);
		HudElementRegistry.addLast(OVERLAYS_ID, HudElements::overlays);
	}

	// =====================================================================
	// Индикатор жизней
	// =====================================================================

	private static void livesIndicator(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		NLClientConfig config = NLClientConfig.get();
		if (!config.hudEnabled) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden()) return;
		PayloadTypes.SyncPayload sync = ClientState.sync;
		if (sync == null || sync.eliminated()) return;

		// Справа от хотбара: не перекрывается чатом (он в левом нижнем углу)
		// и не спорит с полоской здоровья/голода.
		String livesText = "×" + sync.lives();
		int textW = mc.font.width(livesText);
		int x = graphics.guiWidth() / 2 + 91 + 4;
		int y = graphics.guiHeight() - 39;
		if (x + 12 + textW > graphics.guiWidth() - 2) {
			x = graphics.guiWidth() - 14 - textW;
		}

		// Иконка сердца (лёгкий пульс при малом числе жизней).
		float scale = 1f;
		if (sync.lives() <= 2) {
			float t = (System.nanoTime() % 1_200_000_000L) / 1_200_000_000f;
			scale = 1f + 0.12f * (float) Math.sin(t * Math.PI * 2);
		}
		int size = Math.round(9 * scale);
		int iconX = x + (9 - size) / 2;
		int iconY = y + (9 - size) / 2;
		Textures.full(graphics, Textures.HEART_FULL, iconX, iconY, size, size);
		int textX = x + 12;
		graphics.fill(textX - 2, y, textX + textW + 1, y + 10, 0x66000000);
		graphics.text(mc.font, livesText, textX, y + 1, 0xFFFFFFFF, true);
	}

	// =====================================================================
	// Индикатор защиты
	// =====================================================================

	private static void protectionIndicator(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		NLClientConfig config = NLClientConfig.get();
		if (!config.hudEnabled) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden()) return;
		PayloadTypes.SyncPayload sync = ClientState.sync;
		if (sync == null || sync.protectionSeconds() <= 0) return;

		Component text = Component.translatable("ninelives.hud.protection", sync.protectionSeconds());
		int width = mc.font.width(text);
		int x = graphics.guiWidth() / 2 + 91 + 4;
		int y = graphics.guiHeight() - 26;
		if (x + width > graphics.guiWidth() - 2) {
			x = graphics.guiWidth() - 2 - width;
		}
		graphics.fill(x - 2, y - 2, x + width + 2, y + 10, 0x88000000);
		graphics.text(mc.font, text, x, y, 0xFF7FE8FF, true);
	}

	// =====================================================================
	// Оверлеи: виньетка, наблюдатель, летящие сердца
	// =====================================================================

	private static void overlays(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden()) return;

		int w = graphics.guiWidth();
		int h = graphics.guiHeight();

		// Красное мерцание краёв при потере жизни.
		float vignette = ClientState.vignetteAlpha();
		if (vignette > 0.01f) {
			int alpha = (int) (vignette * 255) << 24;
			int red = alpha | 0x00FF2222;
			graphics.fillGradient(0, 0, w, h / 6, red, 0);
			graphics.fillGradient(0, h - h / 6, w, h, 0, red);
			graphics.fill(0, 0, w / 8, h, red);
			graphics.fill(w - w / 8, 0, w, h, red);
		}

		// Экран наблюдателя: затемнение + подпись.
		PayloadTypes.SyncPayload sync = ClientState.sync;
		if (sync != null && sync.eliminated() && mc.player.isSpectator()) {
			graphics.fill(0, 0, w, h, 0xA0000000);
			Component title = Component.translatable("ninelives.observer.title");
			Component subtitle = Component.translatable("ninelives.observer.subtitle");
			graphics.centeredText(mc.font, title, w / 2, h / 2 - 16, 0xFFFF5555);
			graphics.centeredText(mc.font, subtitle, w / 2, h / 2 + 4, 0xFFCCCCCC);
		}

		// Летящее сердце при передаче жизней.
		if (NLClientConfig.get().animations) {
			var it = ClientState.flyingHearts.iterator();
			while (it.hasNext()) {
				ClientState.FlyingHeart heart = it.next();
				if (heart.done()) {
					it.remove();
					continue;
				}
				float p = heart.progress();
				float eased = 1f - (1f - p) * (1f - p); // easeOutQuad
				int x = (int) (w * 0.15f + (w * 0.7f) * eased);
				int y = (int) (h * 0.75f - h * 0.35f * eased - (float) Math.sin(p * Math.PI) * 40);
				float bounce = 1f + 0.25f * (float) Math.sin(p * Math.PI);
				int size = Math.round(18 * bounce);
				Textures.full(graphics, Textures.HEART_FULL_GOLD, x, y, size, size);
				if (heart.amount > 1) {
					String label = "+" + heart.amount;
					graphics.text(mc.font, label, x + size / 2 - mc.font.width(label) / 2,
							y - 10, 0xFFFFD980, true);
				}
			}
		}
	}
}
