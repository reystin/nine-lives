package com.reystin.ninelives.client.ui;

import com.mojang.authlib.GameProfile;
import com.reystin.ninelives.client.ClientNet;
import com.reystin.ninelives.client.ClientState;
import com.reystin.ninelives.client.hud.Textures;
import com.reystin.ninelives.config.NLClientConfig;
import com.reystin.ninelives.net.PayloadTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Экран жизней — центральный интерфейс мода.
 * Тёмная стеклянная панель (9-slice), три вкладки:
 * Обзор (большое анимированное сердце, перекатывающиеся цифры, полоса
 * прогресса), Достижения (хард-список и ближайшие с прокруткой и описанием
 * по клику), Игроки (карточки с головами и передачей жизней).
 *
 * Эффекты (вспышка, трещина, частицы) приходят из {@link ClientState} и
 * считаются по времени, а не по кадрам.
 */
public class LivesScreen extends Screen {
	public static final int PANEL_W = 300;
	public static final int PANEL_H = 200;

	private static final int CONTENT_TOP = 54;
	private static final int CONTENT_BOTTOM = PANEL_H - 10;
	private static final int ROW_H = 17;
	private static final int ROW_GAP = 2;
	private static final int PLAYER_ROW_H = 38;

	private Tab tab = Tab.OVERVIEW;

	/** Dev-showcase: принудительная вкладка (0/1/2). Не используется в обычной работе. */
	public static int debugTab = -1;

	// Анимация числа жизней.
	private int shownLives = -1;
	private int rollFrom;
	private int rollTo;
	private long rollStartNanos;

	// Прогресс-бар и эффекты.
	private float progressDisplay = -1;
	private long lastFrameNanos;
	private float lastGainEffect;
	private float lastLossEffect;

	private final List<GuiParticle> particles = new ArrayList<>();

	// Прокрутка.
	private int advScroll;
	private int advMaxScroll;
	private int playerScroll;
	private int playerMaxScroll;

	// Выбранное достижение (модальная карточка).
	private PayloadTypes.ScreenDataPayload.AdvEntry selectedAdv;

	// Диалог передачи.
	private TransferDialog transferDialog;

	// Хиты последнего кадра (в координатах с учётом масштаба).
	private final List<AdvRowHit> advHits = new ArrayList<>();
	private final List<PlayerHit> playerHits = new ArrayList<>();
	private final Map<UUID, PlayerFaceWidget> faceCache = new HashMap<>();
	private int closeX;
	private int closeY;

	public LivesScreen() {
		super(Component.translatable("ninelives.screen.title"));
	}

	@Override
	protected void init() {
		shownLives = currentLives();
		rollFrom = shownLives;
		rollTo = shownLives;
		rollStartNanos = 0;
		progressDisplay = -1;
		lastFrameNanos = 0;
		advScroll = 0;
		playerScroll = 0;
		selectedAdv = null;
		transferDialog = null;
		particles.clear();
		if (debugTab >= 0) {
			tab = Tab.values()[Math.min(debugTab, Tab.values().length - 1)];
		}
	}

	private int currentLives() {
		PayloadTypes.SyncPayload sync = ClientState.sync;
		return sync != null ? sync.lives() : 0;
	}

	private float uiScale() {
		return Math.max(0.5f, Math.min(2f, NLClientConfig.get().uiScale));
	}

	// =====================================================================
	// Отрисовка
	// =====================================================================

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		float scale = uiScale();
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);

		int w = Math.round(this.width / scale);
		int h = Math.round(this.height / scale);
		float mx = mouseX / scale;
		float my = mouseY / scale;
		int panelX = (w - PANEL_W) / 2;
		int panelY = (h - PANEL_H) / 2;

		graphics.fill(0, 0, w, h, 0x7A000000);

		advHits.clear();
		playerHits.clear();

		drawPanel(graphics, panelX, panelY, PANEL_W, PANEL_H);
		drawHeader(graphics, panelX, panelY, panelX + PANEL_W - 26, panelY + 8, mx, my);
		drawTabs(graphics, panelX, panelY, mx, my);

		switch (tab) {
			case OVERVIEW -> drawOverview(graphics, panelX, panelY);
			case ADVANCEMENTS -> drawAdvancements(graphics, panelX, panelY, mx, my);
			case PLAYERS -> drawPlayers(graphics, panelX, panelY, mx, my);
		}

		if (selectedAdv != null) {
			drawAdvDetail(graphics, panelX, panelY, mx, my);
		}
		if (transferDialog != null) {
			transferDialog.draw(graphics, panelX, panelY, PANEL_W, PANEL_H, mx, my);
		}

		graphics.pose().popMatrix();
	}

	/** Тёмная стеклянная панель: 9-slice + тень. */
	public static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		graphics.fill(x + 3, y + 4, x + width + 3, y + height + 5, 0x66000000);
		Textures.nineSlice(graphics, Textures.PANEL, x, y, width, height);
	}

	private void drawHeader(GuiGraphicsExtractor graphics, int panelX, int panelY, int closeX, int closeY,
			float mouseX, float mouseY) {
		this.closeX = closeX;
		this.closeY = closeY;
		Textures.full(graphics, Textures.HEART_FULL, panelX + 12, panelY + 12, 9, 9);
		graphics.text(this.font, Component.translatable("ninelives.screen.title"),
				panelX + 25, panelY + 12, 0xFFFFD980, true);

		boolean hover = inside(mouseX, mouseY, closeX, closeY, 14, 14);
		graphics.fill(closeX, closeY, closeX + 14, closeY + 14, hover ? 0x66A03048 : 0x331A0A10);
		graphics.centeredText(this.font, Component.literal("×"), closeX + 7, closeY + 3,
				hover ? 0xFFFFFFFF : 0xFFC9BFC4);
	}

	private void drawTabs(GuiGraphicsExtractor graphics, int panelX, int panelY, float mouseX, float mouseY) {
		int x = panelX + 14;
		int y = panelY + 28;
		for (Tab value : Tab.values()) {
			boolean active = value == tab;
			boolean hovered = inside(mouseX, mouseY, x, y, Textures.TAB_W, Textures.TAB_H);
			Textures.tab(graphics, active, x, y, Textures.TAB_W, Textures.TAB_H);
			int color = active ? 0xFFFFD980 : hovered ? 0xFFFFFFFF : 0xFFC9BFC4;
			graphics.centeredText(this.font, value.title(), x + Textures.TAB_W / 2, y + 6, color);
			x += Textures.TAB_W + 4;
		}
	}

	// =====================================================================
	// Вкладка «Обзор»
	// =====================================================================

	private void drawOverview(GuiGraphicsExtractor graphics, int panelX, int panelY) {
		Minecraft mc = this.minecraft;
		PayloadTypes.SyncPayload sync = ClientState.sync;
		int cx = panelX + PANEL_W / 2;

		if (sync == null) {
			graphics.centeredText(mc.font, Component.translatable("ninelives.screen.waiting"),
					cx, panelY + 100, 0xFFAAAAAA);
			return;
		}

		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
		lastFrameNanos = now;
		updateRoll(sync.lives(), now);
		updateProgress(sync.progress(), sync.perLife(), dt);

		// Большое сердце: пульс зависит от числа жизней, вспышка/трещина — от событий.
		float gain = ClientState.gainEffect();
		float loss = ClientState.lossEffect();
		float progressFlash = ClientState.progressEffect();
		int heartCy = panelY + 86;
		spawnParticles(cx, heartCy, gain, loss);

		float baseScale = 2.2f;
		boolean lowLives = sync.lives() <= 2;
		float pulseSpeed = lowLives ? 1.6f : sync.lives() <= 5 ? 1.15f : 0.85f;
		float t = (now % 1_600_000_000L) / 1_600_000_000f;
		float pulse = NLClientConfig.get().animations
				? 1f + 0.06f * (float) Math.sin(t * Math.PI * 2 * pulseSpeed) : 1f;
		float scale = baseScale * pulse + gain * 0.6f;
		int heatSize = Math.round(Textures.HEART_BIG_SIZE * scale);

		Identifier heartTex = loss > 0.15f ? Textures.HEART_BIG_CRACKED
				: gain > 0.1f ? Textures.HEART_BIG_GOLD : Textures.HEART_BIG_FULL;
		// Золотая подсветка при получении жизни.
		if (gain > 0.02f) {
			int glow = (int) (90 * gain);
			graphics.fill(cx - heatSize / 2 - 6, heartCy - heatSize / 2 - 6,
					cx + heatSize / 2 + 6, heartCy + heatSize / 2 + 6,
					(glow << 24) | 0x00FFC84A);
		}
		Textures.full(graphics, heartTex, cx - heatSize / 2, heartCy - heatSize / 2, heatSize, heatSize);

		drawRollingNumber(graphics, cx, panelY + 78);
		graphics.centeredText(mc.font, Component.translatable("ninelives.screen.lives"),
				cx, panelY + 122, 0xFFB9AAB0);

		// Полоса прогресса до следующей жизни.
		if (sync.perLife() > 0) {
			int barW = PANEL_W - 90;
			int barX = panelX + 45;
			int barY = panelY + 138;
			Textures.hStretch(graphics, Textures.PROGRESS_FRAME, barX, barY, barW, Textures.PROGRESS_H,
					Textures.PROGRESS_W, Textures.PROGRESS_H, Textures.PROGRESS_CAP);
			float fill = Math.max(0f, Math.min(1f, progressDisplay / sync.perLife()));
			int innerW = barW - 2;
			int fillW = Math.round(innerW * fill);
			if (progressFlash > 0.02f) {
				graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + Textures.PROGRESS_H + 1,
						(int) (120 * progressFlash) << 24 | 0x00FFD980);
			}
			if (fillW > 0) {
				Textures.full(graphics, Textures.PROGRESS_FILL, barX + 1, barY + 1,
						fillW, Textures.PROGRESS_INNER_H);
			}
			graphics.centeredText(mc.font, Component.translatable("ninelives.screen.progress_next",
					sync.progress(), sync.perLife()), cx, barY + 14, 0xFFC9BFC4);
		}

		// Режим и достижения.
		String modeKey = sync.mode() == 1 ? "ninelives.mode.bc"
				: sync.mode() == 0 ? "ninelives.mode.vanilla" : "ninelives.screen.mode_unset";
		Component stats = Component.translatable("ninelives.screen.mode").append(": ")
				.append(Component.translatable(modeKey))
				.append(Component.literal("  ·  "))
				.append(Component.translatable("ninelives.screen.adv_count", sync.advCount()));
		graphics.centeredText(mc.font, stats, cx, panelY + 166, 0xFFB9AAB0);

		// Статус защиты/выбывания.
		if (sync.protectionSeconds() > 0) {
			graphics.centeredText(mc.font, Component.translatable("ninelives.screen.protection", sync.protectionSeconds()),
					cx, panelY + 181, 0xFF7FE8FF);
		} else if (sync.eliminated()) {
			graphics.centeredText(mc.font, Component.translatable("ninelives.screen.eliminated_note"),
					cx, panelY + 181, 0xFFFF5555);
		}

		updateParticles(graphics, dt);
	}

	private void drawRollingNumber(GuiGraphicsExtractor graphics, int cx, int y) {
		float roll = 1f;
		int dir = 1;
		if (rollStartNanos != 0) {
			float t = Math.min(1f, (System.nanoTime() - rollStartNanos) / 280_000_000f);
			roll = easeOutCubic(t);
			dir = rollTo >= rollFrom ? 1 : -1;
			if (t >= 1f) {
				shownLives = rollTo;
				rollStartNanos = 0;
			}
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, y);
		graphics.pose().scale(2f, 2f);
		if (rollStartNanos != 0) {
			int oldOffset = Math.round(-dir * 9 * roll);
			int newOffset = Math.round(dir * 9 * (1f - roll));
			int oldAlpha = (int) (0x88 * (1f - roll)) << 24;
			int newAlpha = (int) (0xFF * roll) << 24;
			graphics.centeredText(this.font, Component.literal(String.valueOf(rollFrom)), 0, oldOffset,
					oldAlpha | 0x00FFFFFF);
			graphics.centeredText(this.font, Component.literal(String.valueOf(rollTo)), 0, newOffset,
					newAlpha | 0x00FFFFFF);
		} else {
			graphics.centeredText(this.font, Component.literal(String.valueOf(shownLives)), 0, 0, 0xFFFFFFFF);
		}
		graphics.pose().popMatrix();
	}

	private void updateRoll(int target, long now) {
		if (shownLives < 0) {
			shownLives = target;
			rollFrom = target;
			rollTo = target;
			rollStartNanos = 0;
			return;
		}
		if (target != rollTo) {
			rollFrom = shownLives;
			rollTo = target;
			rollStartNanos = now;
		}
	}

	private void updateProgress(int progress, int perLife, float dt) {
		float target = perLife > 0 ? Math.min(perLife, progress) : 0;
		if (progressDisplay < 0) {
			progressDisplay = target;
			return;
		}
		progressDisplay += (target - progressDisplay) * Math.min(1f, dt * 14f);
		if (Math.abs(target - progressDisplay) < 0.01f) progressDisplay = target;
	}

	// =====================================================================
	// Частицы внутри GUI
	// =====================================================================

	private void spawnParticles(int cx, int cy, float gain, float loss) {
		if (!NLClientConfig.get().particles) {
			lastGainEffect = gain;
			lastLossEffect = loss;
			return;
		}
		if (gain > 0.9f && lastGainEffect <= 0.9f) {
			for (int i = 0; i < 14; i++) {
				double angle = Math.random() * Math.PI * 2;
				particles.add(new GuiParticle(cx, cy, (float) Math.cos(angle) * 34f,
						(float) Math.sin(angle) * 34f - 12f, 700, 0xFFFFD980, 2));
			}
		}
		if (loss > 0.9f && lastLossEffect <= 0.9f) {
			for (int i = 0; i < 16; i++) {
				double angle = -Math.PI / 2 + (Math.random() - 0.5) * Math.PI * 1.4;
				particles.add(new GuiParticle(cx, cy, (float) Math.cos(angle) * 40f,
						(float) Math.sin(angle) * 40f, 900, 0xFFFF4444, 2));
			}
		}
		lastGainEffect = gain;
		lastLossEffect = loss;
	}

	private void updateParticles(GuiGraphicsExtractor graphics, float dt) {
		if (particles.isEmpty()) return;
		Iterator<GuiParticle> it = particles.iterator();
		long now = System.nanoTime();
		while (it.hasNext()) {
			GuiParticle p = it.next();
			float t = (now - p.startNanos) / (float) p.lifeNanos;
			if (t >= 1f) {
				it.remove();
				continue;
			}
			float x = p.x + p.vx * t;
			float y = p.y + p.vy * t + 24f * t * t;
			int alpha = (int) (255 * (1f - t));
			int color = (alpha << 24) | (p.color & 0x00FFFFFF);
			graphics.fill(Math.round(x), Math.round(y), Math.round(x) + p.size, Math.round(y) + p.size, color);
		}
	}

	private record GuiParticle(float x, float y, float vx, float vy, long lifeNanos,
			int color, int size, long startNanos) {
		GuiParticle(float x, float y, float vx, float vy, long lifeNanos, int color, int size) {
			this(x, y, vx, vy, lifeNanos * 1_000_000L, color, size, System.nanoTime());
		}
	}

	// =====================================================================
	// Вкладка «Достижения»
	// =====================================================================

	private void drawAdvancements(GuiGraphicsExtractor graphics, int panelX, int panelY, float mouseX, float mouseY) {
		PayloadTypes.ScreenDataPayload data = ClientState.screenData;
		int x = panelX + 12;
		int width = PANEL_W - 24;
		int top = panelY + CONTENT_TOP + 2;
		int bottom = panelY + CONTENT_BOTTOM;
		int viewport = bottom - top;

		if (data == null) {
			graphics.centeredText(this.font, Component.translatable("ninelives.screen.waiting"),
					panelX + PANEL_W / 2, panelY + 100, 0xFFAAAAAA);
			return;
		}

		int cx = panelX + PANEL_W / 2;
		if (data.hard().isEmpty() && data.nearest().isEmpty()) {
			// Один пустой статус вместо двух секций: иначе заголовок «Ближайшие»
			// накладывался на поцентрированное сообщение.
			advMaxScroll = 0;
			advScroll = 0;
			graphics.centeredText(this.font, Component.translatable("ninelives.screen.no_advancements"),
					cx, panelY + 96, 0xFFAAAAAA);
			if (ClientState.sync != null && ClientState.sync.mode() == 1) {
				graphics.centeredText(this.font, Component.translatable("ninelives.screen.bacap_missing"),
						cx, panelY + 110, 0xFF7A7076);
			}
			return;
		}

		int contentHeight = 14 + data.hard().size() * (ROW_H + ROW_GAP)
				+ 8 + 14 + data.nearest().size() * (ROW_H + ROW_GAP);
		advMaxScroll = Math.max(0, contentHeight - viewport);
		advScroll = Math.max(0, Math.min(advScroll, advMaxScroll));

		graphics.enableScissor(panelX + 10, top, panelX + PANEL_W - 10, bottom);
		int y = top - advScroll;
		y = drawSection(graphics, Component.translatable(
				ClientState.sync != null && ClientState.sync.mode() == 1
						? "ninelives.screen.super_list" : "ninelives.screen.hard_list"),
				data.hard(), x, y, width, mouseX, mouseY, top, bottom);
		y += 8;
		y = drawSection(graphics, Component.translatable("ninelives.screen.nearest"),
				data.nearest(), x, y, width, mouseX, mouseY, top, bottom);
		graphics.disableScissor();

		drawScrollbar(graphics, panelX + PANEL_W - 8, top, viewport,
				contentHeight, advScroll, advMaxScroll);
	}

	private int drawSection(GuiGraphicsExtractor graphics, Component title,
			List<PayloadTypes.ScreenDataPayload.AdvEntry> entries,
			int x, int y, int width, float mouseX, float mouseY, int hitTop, int hitBottom) {
		graphics.text(this.font, title, x, y, 0xFF9E9398, false);
		y += 14;
		if (entries.isEmpty()) {
			graphics.text(this.font, Component.translatable("ninelives.screen.no_advancements"),
					x + 2, y, 0xFF7A7076, false);
			return y + ROW_H + ROW_GAP;
		}
		for (PayloadTypes.ScreenDataPayload.AdvEntry entry : entries) {
			boolean hovered = inside(mouseX, mouseY, x, y, width, ROW_H)
					&& mouseY >= hitTop && mouseY < hitBottom;
			if (hovered) {
				graphics.fill(x, y, x + width, y + ROW_H, 0x26FFD980);
			}
			Identifier icon = entry.done() ? Textures.HEART_FULL_GOLD : Textures.HEART_CRACKED;
			Textures.full(graphics, icon, x + 2, y + 4, 9, 9);
			int color = entry.done() ? 0xFFFFD980 : 0xFFE0D6DA;
			graphics.text(this.font, clipText(plain(entry.title()), width - 52), x + 15, y + 4, color, true);
			if (!entry.done() && entry.percent() > 0) {
				String percent = entry.percent() + "%";
				graphics.text(this.font, percent, x + width - this.font.width(percent) - 3,
						y + 4, 0xFF9E9398, false);
			}
			advHits.add(new AdvRowHit(entry, x, y, width, ROW_H));
			if (hovered) {
				Component status = Component.translatable(entry.done()
						? "ninelives.screen.done" : "ninelives.screen.not_done");
				graphics.setTooltipForNextFrame(
						Component.empty().append(plain(entry.title())).append(Component.literal(" — ")).append(status),
						(int) mouseX, (int) mouseY);
			}
			y += ROW_H + ROW_GAP;
		}
		return y;
	}

	/** Снимает серверные стили и скобки — цвет задаёт тема экрана. */
	private static Component plain(Component component) {
		return Component.literal(component.getString());
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int y, int viewport,
			int contentHeight, int scroll, int maxScroll) {
		if (maxScroll <= 0) return;
		graphics.fill(x, y, x + 3, y + viewport, 0x33FFFFFF);
		int thumbH = Math.max(18, viewport * viewport / contentHeight);
		int thumbY = y + (viewport - thumbH) * scroll / maxScroll;
		graphics.fill(x, thumbY, x + 3, thumbY + thumbH, 0xCCFFD980);
	}

	private void drawAdvDetail(GuiGraphicsExtractor graphics, int panelX, int panelY, float mouseX, float mouseY) {
		int w = 240;
		int h = 120;
		int x = panelX + (PANEL_W - w) / 2;
		int y = panelY + (PANEL_H - h) / 2;
		graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0x77000000);
		drawPanel(graphics, x, y, w, h);

		Identifier icon = selectedAdv.done() ? Textures.HEART_FULL_GOLD : Textures.HEART_CRACKED;
		Textures.full(graphics, icon, x + 12, y + 12, 9, 9);
		graphics.text(this.font, clipText(plain(selectedAdv.title()), w - 48), x + 25, y + 12, 0xFFFFD980, true);

		var lines = this.font.split(selectedAdv.description(), w - 28);
		int lineY = y + 30;
		int maxLines = 5;
		for (int i = 0; i < lines.size() && i < maxLines; i++) {
			graphics.text(this.font, lines.get(i), x + 14, lineY, 0xFFB9AAB0, false);
			lineY += 10;
		}
		if (lines.size() > maxLines) {
			graphics.text(this.font, Component.literal("…"), x + 14, lineY, 0xFF7A7076, false);
		}

		Component status = Component.translatable(selectedAdv.done()
				? "ninelives.screen.done" : "ninelives.screen.not_done");
		Component line = Component.translatable("ninelives.screen.detail_status", status)
				.append(Component.literal("  ·  "))
				.append(Component.translatable("ninelives.screen.detail_progress", selectedAdv.percent()));
		graphics.text(this.font, line, x + 14, y + h - 38, 0xFFE0D6DA, false);

		int btnW = 84;
		int btnX = x + (w - btnW) / 2;
		int btnY = y + h - 24;
		boolean hover = inside(mouseX, mouseY, btnX, btnY, btnW, 16);
		Textures.button(graphics, hover, btnX, btnY, btnW, 16);
		graphics.centeredText(this.font, Component.translatable("ninelives.screen.close"),
				btnX + btnW / 2, btnY + 4, hover ? 0xFFFFFFFF : 0xFFE0D6DA);
	}

	// =====================================================================
	// Вкладка «Игроки»
	// =====================================================================

	private void drawPlayers(GuiGraphicsExtractor graphics, int panelX, int panelY, float mouseX, float mouseY) {
		PayloadTypes.ScreenDataPayload data = ClientState.screenData;
		int x = panelX + 12;
		int width = PANEL_W - 24;
		int top = panelY + CONTENT_TOP + 2;
		int bottom = panelY + CONTENT_BOTTOM;
		int viewport = bottom - top;

		if (data == null) {
			graphics.centeredText(this.font, Component.translatable("ninelives.screen.waiting"),
					panelX + PANEL_W / 2, panelY + 100, 0xFFAAAAAA);
			return;
		}
		int rowStep = PLAYER_ROW_H + 4;
		int contentHeight = data.players().size() * rowStep;
		playerMaxScroll = Math.max(0, contentHeight - viewport);
		playerScroll = Math.max(0, Math.min(playerScroll, playerMaxScroll));

		UUID selfId = this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
		graphics.enableScissor(panelX + 10, top, panelX + PANEL_W - 10, bottom);
		int y = top - playerScroll;
		for (PayloadTypes.ScreenDataPayload.PlayerEntry entry : data.players()) {
			boolean hovered = inside(mouseX, mouseY, x, y, width, PLAYER_ROW_H);
			graphics.fill(x, y, x + width, y + PLAYER_ROW_H, hovered ? 0x26FFD980 : 0x1AFFFFFF);
			graphics.fill(x, y, x + width, y + 1, 0x33FFD980);

			try {
				PlayerFaceWidget face = faceCache.computeIfAbsent(entry.uuid(), uuid -> {
					var profile = ResolvableProfile.createResolved(new GameProfile(uuid, entry.name()));
					return new PlayerFaceWidget(26, profile);
				});
				face.setX(x + 5);
				face.setY(y + 6);
				face.setWidth(26);
				face.setHeight(26);
				face.extractRenderState(graphics, (int) mouseX, (int) mouseY, 1f);
			} catch (Exception e) {
				graphics.fill(x + 5, y + 6, x + 31, y + 32, 0xFF3A2A30);
			}

			int nameColor = entry.spectator() ? 0xFF9E9398 : 0xFFFFFFFF;
			graphics.text(this.font, entry.name(), x + 38, y + 7, nameColor, true);
			String stats = "♥" + entry.lives() + "   ★" + entry.advCount();
			int statsColor = entry.spectator() ? 0xFF9E9398
					: entry.lives() <= 2 ? 0xFFFF5555 : entry.lives() >= 15 ? 0xFFFFD980 : 0xFFE0D6DA;
			graphics.text(this.font, stats, x + 38, y + 20, statsColor, true);

			// Себе жизнь не передать: у своей карточки кнопки нет.
			if (selfId != null && selfId.equals(entry.uuid())) {
				Component you = Component.translatable("ninelives.screen.you");
				graphics.text(this.font, you, x + width - 6 - this.font.width(you), y + 7,
						0xFF9E9398, false);
			} else {
				Component giveLabel = Component.translatable("ninelives.screen.give");
				int btnW = Math.min(width - 60, Math.max(78, this.font.width(giveLabel) + 16));
				int btnH = 16;
				int btnX = x + width - btnW - 6;
				int btnY = y + (PLAYER_ROW_H - btnH) / 2;
				boolean btnHovered = inside(mouseX, mouseY, btnX, btnY, btnW, btnH);
				Textures.button(graphics, btnHovered, btnX, btnY, btnW, btnH);
				graphics.centeredText(this.font, giveLabel,
						btnX + btnW / 2, btnY + 4, btnHovered ? 0xFFFFFFFF : 0xFFE0D6DA);

				playerHits.add(new PlayerHit(entry, btnX, btnY, btnW, btnH));
			}
			y += rowStep;
		}
		graphics.disableScissor();
		drawScrollbar(graphics, panelX + PANEL_W - 8, top, viewport,
				contentHeight, playerScroll, playerMaxScroll);
	}

	// =====================================================================
	// Ввод
	// =====================================================================

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
		float scale = uiScale();
		float mx = (float) (event.x() / scale);
		float my = (float) (event.y() / scale);
		int w = Math.round(this.width / scale);
		int h = Math.round(this.height / scale);
		int panelX = (w - PANEL_W) / 2;
		int panelY = (h - PANEL_H) / 2;

		if (transferDialog != null) {
			if (transferDialog.mouseClicked(mx, my, panelX, panelY)) {
				if (transferDialog.shouldClose()) {
					if (transferDialog.confirmed() && transferDialog.amount() > 0) {
						ClientPlayBridge.give(transferDialog.target().uuid(), transferDialog.amount());
					}
					transferDialog = null;
				}
				return true;
			}
			return true;
		}

		if (selectedAdv != null) {
			int dw = 240;
			int dh = 120;
			int dx = panelX + (PANEL_W - dw) / 2;
			int dy = panelY + (PANEL_H - dh) / 2;
			int btnW = 84;
			int btnX = dx + (dw - btnW) / 2;
			int btnY = dy + dh - 24;
			if (inside(mx, my, btnX, btnY, btnW, 16) || !inside(mx, my, dx, dy, dw, dh)) {
				selectedAdv = null;
			}
			return true;
		}

		if (inside(mx, my, closeX, closeY, 14, 14)) {
			this.onClose();
			return true;
		}

		int tabX = panelX + 14;
		int tabY = panelY + 28;
		for (Tab value : Tab.values()) {
			if (inside(mx, my, tabX, tabY, Textures.TAB_W, Textures.TAB_H)) {
				if (tab != value) {
					tab = value;
					advScroll = 0;
					playerScroll = 0;
					ClientNet.requestScreenData();
				}
				return true;
			}
			tabX += Textures.TAB_W + 4;
		}

		if (tab == Tab.ADVANCEMENTS) {
			for (AdvRowHit hit : advHits) {
				if (inside(mx, my, hit.x(), hit.y(), hit.width(), hit.height())) {
					selectedAdv = hit.entry();
					return true;
				}
			}
		}

		if (tab == Tab.PLAYERS) {
			UUID selfId = this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
			for (PlayerHit hit : playerHits) {
				if (selfId != null && selfId.equals(hit.entry().uuid())) continue;
				if (inside(mx, my, hit.x(), hit.y(), hit.width(), hit.height())) {
					int max = Math.max(0, currentLives() - 1);
					transferDialog = new TransferDialog(hit.entry(), max);
					return true;
				}
			}
		}

		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		float scale = uiScale();
		float mx = (float) (mouseX / scale);
		float my = (float) (mouseY / scale);
		int w = Math.round(this.width / scale);
		int h = Math.round(this.height / scale);
		int panelX = (w - PANEL_W) / 2;
		int panelY = (h - PANEL_H) / 2;
		if (transferDialog != null) {
			transferDialog.mouseScrolled(scrollY);
			return true;
		}
		if (selectedAdv != null) return true;
		if (!inside(mx, my, panelX + 10, panelY + CONTENT_TOP, PANEL_W - 20, CONTENT_BOTTOM - CONTENT_TOP)) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		int delta = (int) Math.round(scrollY * 20);
		if (tab == Tab.ADVANCEMENTS) {
			advScroll = Math.max(0, Math.min(advMaxScroll, advScroll - delta));
			return true;
		}
		if (tab == Tab.PLAYERS) {
			playerScroll = Math.max(0, Math.min(playerMaxScroll, playerScroll - delta));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private static boolean inside(float mouseX, float mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private Component clipText(Component text, int maxWidth) {
		if (this.font.width(text) <= maxWidth) return text;
		String plain = text.getString();
		while (!plain.isEmpty() && this.font.width(plain + "…") > maxWidth) {
			plain = plain.substring(0, plain.length() - 1);
		}
		return Component.literal(plain + "…");
	}

	private static float easeOutCubic(float t) {
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	private record AdvRowHit(PayloadTypes.ScreenDataPayload.AdvEntry entry, int x, int y, int width, int height) {
	}

	private record PlayerHit(PayloadTypes.ScreenDataPayload.PlayerEntry entry, int x, int y, int width, int height) {
	}

	// =====================================================================

	/** Вкладки экрана. */
	private enum Tab {
		OVERVIEW("ninelives.screen.tab.overview"),
		ADVANCEMENTS("ninelives.screen.tab.advancements"),
		PLAYERS("ninelives.screen.tab.players");

		private final String key;

		Tab(String key) {
			this.key = key;
		}

		public Component title() {
			return Component.translatable(key);
		}
	}
}
