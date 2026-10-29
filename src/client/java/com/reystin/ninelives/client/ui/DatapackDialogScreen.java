package com.reystin.ninelives.client.ui;

import com.reystin.ninelives.client.ClientState;
import com.reystin.ninelives.client.hud.Textures;
import com.reystin.ninelives.config.NLClientConfig;
import com.reystin.ninelives.net.PayloadTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/**
 * Экран авто-установки BACAP: открывается сразу после выбора режима и
 * показывает подготовку, скачивание, проверку и итог. Вопросов и кнопок
 * подтверждения нет — выбор режима уже является согласием.
 */
public class DatapackDialogScreen extends Screen {
	private static volatile String lastErrorKey;
	private static volatile String lastErrorDetail;
	private static volatile boolean done;

	private final Screen parent;

	private Rect closeHit;
	private Rect linkHit;

	private DatapackDialogScreen(Screen parent) {
		super(Component.translatable("ninelives.datapack.title"));
		this.parent = parent;
	}

	/** Экран прогресса; текущая стадия и процент берутся из {@link ClientState}. */
	public static DatapackDialogScreen progressScreen(Screen parent) {
		lastErrorKey = null;
		lastErrorDetail = null;
		done = false;
		return new DatapackDialogScreen(parent);
	}

	/** Экран ошибки; ключ и подробности уже сохранены {@link #showError}. */
	public static DatapackDialogScreen errorScreen(Screen parent) {
		done = false;
		return new DatapackDialogScreen(parent);
	}

	/** Сброс между серверами/входами. */
	public static void resetState() {
		lastErrorKey = null;
		lastErrorDetail = null;
		done = false;
		ClientState.downloadStage = -1;
		ClientState.downloadPercent = 0;
	}

	public static void showError(String errorKey, String errorDetail) {
		lastErrorKey = errorKey == null || errorKey.isEmpty() ? "ninelives.bacap.fail_download" : errorKey;
		lastErrorDetail = errorDetail;
		ClientState.downloadStage = PayloadTypes.DownloadProgressPayload.STAGE_ERROR;
	}

	public static void markDone() {
		done = true;
	}

	private float uiScale() {
		return Math.max(0.5f, Math.min(2f, NLClientConfig.get().uiScale));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		Minecraft mc = this.minecraft;
		float scale = uiScale();
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);

		int w = Math.round(this.width / scale);
		int h = Math.round(this.height / scale);
		float mx = mouseX / scale;
		float my = mouseY / scale;
		int pw = Math.max(160, Math.min(380, w - 24));

		closeHit = null;
		linkHit = null;

		int stage = ClientState.downloadStage;
		if (stage == PayloadTypes.DownloadProgressPayload.STAGE_DONE || done) {
			drawDone(graphics, mc, w, h, pw, mx, my);
		} else if (stage == PayloadTypes.DownloadProgressPayload.STAGE_ERROR || lastErrorKey != null) {
			drawError(graphics, mc, w, h, pw, mx, my);
		} else {
			if (stage < 0) stage = PayloadTypes.DownloadProgressPayload.STAGE_PREPARING;
			drawProgress(graphics, mc, w, h, pw, stage);
		}

		graphics.pose().popMatrix();
	}

	private void drawDone(GuiGraphicsExtractor graphics, Minecraft mc, int w, int h, int pw,
			float mx, float my) {
		Component title = Component.translatable("ninelives.datapack.done");
		Component sub = Component.translatable("ninelives.datapack.done_sub");
		Component close = Component.translatable("ninelives.screen.close");
		int contentW = pw - 32;
		var titleLines = mc.font.split(title, contentW);
		var subLines = mc.font.split(sub, contentW);
		int closeW = Math.min(pw - 28, Math.max(96, mc.font.width(close) + 20));

		int contentH = 16 + titleLines.size() * 10 + 6 + subLines.size() * 10 + 12 + 20;
		int ph = contentH + 12 + 20 + 14;
		int px = (w - pw) / 2;
		int py = (h - ph) / 2;

		graphics.fill(0, 0, w, h, 0x99000000);
		LivesScreen.drawPanel(graphics, px, py, pw, ph);

		int y = py + 16;
		for (var line : titleLines) {
			graphics.centeredText(mc.font, line, px + pw / 2, y, 0xFFFFD980);
			y += 10;
		}
		y += 6;
		for (var line : subLines) {
			graphics.centeredText(mc.font, line, px + pw / 2, y, 0xFFB9AAB0);
			y += 10;
		}
		y += 12;
		Textures.full(graphics, Textures.HEART_FULL_GOLD, px + pw / 2 - 10, y, 20, 20);

		int closeX = px + (pw - closeW) / 2;
		int closeY = py + ph - 14 - 20;
		drawButton(graphics, closeX, closeY, closeW, 20, close, mx, my);
		closeHit = new Rect(closeX, closeY, closeW, 20);
	}

	private void drawError(GuiGraphicsExtractor graphics, Minecraft mc, int w, int h, int pw,
			float mx, float my) {
		String key = lastErrorKey != null ? lastErrorKey : "ninelives.bacap.fail_download";
		Component title = Component.translatable("ninelives.datapack.failed");
		Component message = errorMessage(key);
		Component close = Component.translatable("ninelives.screen.close");
		String url = com.reystin.ninelives.NineLives.config().bacapManualUrl;
		Component link = Component.translatable("ninelives.datapack.open_page")
				.withStyle(s -> s.withColor(0xFF7FE8FF).withUnderlined(true)
						.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url))));
		int contentW = pw - 32;
		var titleLines = mc.font.split(title, contentW);
		var messageLines = mc.font.split(message, contentW);
		int closeW = Math.min(pw - 28, Math.max(96, mc.font.width(close) + 20));
		int linkW = Math.min(contentW, mc.font.width(link));

		int contentH = 16 + titleLines.size() * 10 + 6 + messageLines.size() * 10 + 8 + 10;
		int ph = contentH + 12 + 20 + 14;
		int px = (w - pw) / 2;
		int py = (h - ph) / 2;

		graphics.fill(0, 0, w, h, 0x99000000);
		LivesScreen.drawPanel(graphics, px, py, pw, ph);

		int y = py + 16;
		for (var line : titleLines) {
			graphics.centeredText(mc.font, line, px + pw / 2, y, 0xFFFF5555);
			y += 10;
		}
		y += 6;
		for (var line : messageLines) {
			graphics.centeredText(mc.font, line, px + pw / 2, y, 0xFFB9AAB0);
			y += 10;
		}
		y += 8;
		boolean hover = inside(mx, my, px + (pw - linkW) / 2, y, linkW, 10);
		graphics.centeredText(mc.font, link, px + pw / 2, y, hover ? 0xFFFFFFFF : 0xFF7FE8FF);
		linkHit = new Rect(px + (pw - linkW) / 2, y, linkW, 10);

		int closeX = px + (pw - closeW) / 2;
		int closeY = py + ph - 14 - 20;
		drawButton(graphics, closeX, closeY, closeW, 20, close, mx, my);
		closeHit = new Rect(closeX, closeY, closeW, 20);
	}

	private void drawProgress(GuiGraphicsExtractor graphics, Minecraft mc, int w, int h, int pw, int stage) {
		String textKey = switch (stage) {
			case PayloadTypes.DownloadProgressPayload.STAGE_PREPARING -> "ninelives.datapack.preparing";
			case PayloadTypes.DownloadProgressPayload.STAGE_DOWNLOADING -> "ninelives.datapack.downloading";
			case PayloadTypes.DownloadProgressPayload.STAGE_VERIFYING -> "ninelives.datapack.verifying";
			default -> "ninelives.datapack.reloading";
		};
		int percent = stage == PayloadTypes.DownloadProgressPayload.STAGE_DOWNLOADING
				? ClientState.downloadPercent : 100;
		Component label = stage == PayloadTypes.DownloadProgressPayload.STAGE_DOWNLOADING
				? Component.translatable(textKey, percent)
				: Component.translatable(textKey);

		var lines = mc.font.split(label, pw - 32);
		int ph = 16 + lines.size() * 10 + 14 + Textures.PROGRESS_H + 12 + 9 + 16;
		int px = (w - pw) / 2;
		int py = (h - ph) / 2;

		graphics.fill(0, 0, w, h, 0x99000000);
		LivesScreen.drawPanel(graphics, px, py, pw, ph);

		int y = py + 16;
		for (var line : lines) {
			graphics.centeredText(mc.font, line, px + pw / 2, y, 0xFFE0D6DA);
			y += 10;
		}

		int barW = pw - 60;
		int barX = px + 30;
		int barY = y + 14;
		Textures.hStretch(graphics, Textures.PROGRESS_FRAME, barX, barY, barW, Textures.PROGRESS_H,
				Textures.PROGRESS_W, Textures.PROGRESS_H, Textures.PROGRESS_CAP);
		int shownPercent = stage == PayloadTypes.DownloadProgressPayload.STAGE_PREPARING ? 0 : percent;
		int fillW = Math.round((barW - 2) * Math.max(0, Math.min(100, shownPercent)) / 100f);
		if (fillW > 0) {
			Textures.full(graphics, Textures.PROGRESS_FILL, barX + 1, barY + 1,
					fillW, Textures.PROGRESS_INNER_H);
		}
		Textures.full(graphics, Textures.HEART_FULL, px + pw / 2 - 5, barY + Textures.PROGRESS_H + 12, 9, 9);
	}

	private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
			Component text, float mouseX, float mouseY) {
		boolean hover = inside(mouseX, mouseY, x, y, w, h);
		Textures.button(graphics, hover, x, y, w, h);
		graphics.centeredText(this.minecraft.font, text, x + w / 2, y + 6,
				hover ? 0xFFFFFFFF : 0xFFE0D6DA);
	}

	/** Тот же набор аргументов, что у серверного errorArgs. */
	private static Component errorMessage(String key) {
		String url = com.reystin.ninelives.NineLives.config().bacapManualUrl;
		String detail = lastErrorDetail == null || lastErrorDetail.isEmpty() ? "?" : lastErrorDetail;
		return switch (key) {
			case "ninelives.bacap.fail_no_version" -> Component.translatable(key,
					net.minecraft.SharedConstants.getCurrentVersion().name(), url);
			case "ninelives.bacap.fail_download" -> Component.translatable(key, detail, url);
			default -> Component.translatable(key, url);
		};
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
		float scale = uiScale();
		float mx = (float) (event.x() / scale);
		float my = (float) (event.y() / scale);
		int stage = ClientState.downloadStage;

		if (stage == PayloadTypes.DownloadProgressPayload.STAGE_DONE || done) {
			if (closeHit != null && closeHit.contains(mx, my)) {
				this.onClose();
			}
			return true;
		}
		if (stage == PayloadTypes.DownloadProgressPayload.STAGE_ERROR || lastErrorKey != null) {
			if (linkHit != null && linkHit.contains(mx, my)) {
				openManualPage();
				return true;
			}
			if (closeHit != null && closeHit.contains(mx, my)) {
				this.onClose();
			}
			return true;
		}
		return true;
	}

	private void openManualPage() {
		try {
			net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this,
					java.net.URI.create(com.reystin.ninelives.NineLives.config().bacapManualUrl));
		} catch (Exception ignored) {
		}
	}

	private static boolean inside(float mouseX, float mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	private record Rect(int x, int y, int width, int height) {
		boolean contains(float mouseX, float mouseY) {
			return inside(mouseX, mouseY, x, y, width, height);
		}
	}
}
