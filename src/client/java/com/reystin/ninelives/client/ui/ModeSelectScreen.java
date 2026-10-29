package com.reystin.ninelives.client.ui;

import com.reystin.ninelives.client.hud.Textures;
import com.reystin.ninelives.config.NLClientConfig;
import com.reystin.ninelives.net.PayloadTypes;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Экран выбора режима достижений — показывается хосту (в одиночной игре) или
 * оператору при первом входе в мир, где режим ещё не выбран.
 */
public class ModeSelectScreen extends Screen {
	private final int vanillaPerLife;
	private final int bacapPerLife;

	private CardHit vanillaHit;
	private CardHit bacapHit;

	public ModeSelectScreen(int vanillaPerLife, int bacapPerLife) {
		super(Component.translatable("ninelives.mode_select.title"));
		this.vanillaPerLife = vanillaPerLife;
		this.bacapPerLife = bacapPerLife;
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

		Component title = Component.translatable("ninelives.mode_select.title");
		Component subtitle = Component.translatable("ninelives.mode_select.subtitle");
		Component vanillaTitle = Component.translatable("ninelives.mode_select.vanilla");
		Component vanillaNote = Component.translatable("ninelives.mode_select.vanilla.note", vanillaPerLife);
		Component bacapTitle = Component.translatable("ninelives.mode_select.bc");
		Component bacapNote = Component.translatable("ninelives.mode_select.bc.note", bacapPerLife);
		Component requirement = Component.translatable("ninelives.mode_select.bc.requirement");

		int pw = Math.max(220, Math.min(380, w - 32));
		int cardGap = 10;
		int cardW = Math.max(80, (pw - 24 - cardGap) / 2);
		int textW = cardW - 16;

		var titleLines = mc.font.split(title, pw - 32);
		var subtitleLines = mc.font.split(subtitle, pw - 32);
		int cardTitleLines = Math.max(mc.font.split(vanillaTitle, textW).size(),
				mc.font.split(bacapTitle, textW).size());
		int cardNoteLines = Math.max(mc.font.split(vanillaNote, textW).size(),
				mc.font.split(bacapNote, textW).size());
		int cardH = 10 + cardTitleLines * 10 + 4 + cardNoteLines * 10 + 8 + 10 + 8;
		var requirementLines = mc.font.split(requirement, pw - 24);

		int headerH = titleLines.size() * 10 + 2 + subtitleLines.size() * 10;
		int cardYOffset = 12 + headerH + 10;
		int requirementYOffset = cardYOffset + cardH + 10;
		int ph = requirementYOffset + requirementLines.size() * 10 + 12;
		int px = (w - pw) / 2;
		int py = (h - ph) / 2;

		graphics.fill(0, 0, w, h, 0x99000000);
		LivesScreen.drawPanel(graphics, px, py, pw, ph);

		int lineY = py + 12;
		for (var line : titleLines) {
			graphics.centeredText(mc.font, line, px + pw / 2, lineY, 0xFFFFD980);
			lineY += 10;
		}
		lineY += 2;
		for (var line : subtitleLines) {
			graphics.centeredText(mc.font, line, px + pw / 2, lineY, 0xFF9E9398);
			lineY += 10;
		}

		int cardY = py + cardYOffset;
		int vanillaX = px + 12;
		int bacapX = px + pw - 12 - cardW;
		drawModeCard(graphics, mc, vanillaX, cardY, cardW, cardH, mx, my,
				vanillaTitle, vanillaNote, Textures.HEART_FULL);
		drawModeCard(graphics, mc, bacapX, cardY, cardW, cardH, mx, my,
				bacapTitle, bacapNote, Textures.HEART_FULL_GOLD);
		vanillaHit = new CardHit(vanillaX, cardY, cardW, cardH, 0);
		bacapHit = new CardHit(bacapX, cardY, cardW, cardH, 1);

		lineY = py + requirementYOffset;
		for (var line : requirementLines) {
			graphics.centeredText(mc.font, line, px + pw / 2, lineY, 0xFF8A7F84);
			lineY += 10;
		}

		graphics.pose().popMatrix();
	}

	private void drawModeCard(GuiGraphicsExtractor graphics, Minecraft mc, int x, int y, int w, int h,
			float mouseX, float mouseY, Component title, Component note, net.minecraft.resources.Identifier icon) {
		boolean hovered = inside(mouseX, mouseY, x, y, w, h);
		Textures.nineSlice(graphics, hovered ? Textures.PANEL_HIGHLIGHT : Textures.PANEL, x, y, w, h);
		int textW = w - 16;
		int lineY = y + 10;
		for (var line : mc.font.split(title, textW)) {
			graphics.centeredText(mc.font, line, x + w / 2, lineY, hovered ? 0xFFFFD980 : 0xFFE0D6DA);
			lineY += 10;
		}
		lineY += 4;
		for (var line : mc.font.split(note, textW)) {
			graphics.centeredText(mc.font, line, x + w / 2, lineY, 0xFFB9AAB0);
			lineY += 10;
		}
		Textures.full(graphics, icon, x + w / 2 - 5, y + h - 20, 10, 10);
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
		float scale = uiScale();
		float mx = (float) (event.x() / scale);
		float my = (float) (event.y() / scale);
		if (vanillaHit != null && vanillaHit.contains(mx, my)) {
			select(vanillaHit.mode());
			return true;
		}
		if (bacapHit != null && bacapHit.contains(mx, my)) {
			select(bacapHit.mode());
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	private void select(int mode) {
		ClientPlayNetworking.send(new PayloadTypes.ModeSelectPayload(mode));
		this.onClose();
	}

	private static boolean inside(float mouseX, float mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private record CardHit(int x, int y, int width, int height, int mode) {
		boolean contains(float mouseX, float mouseY) {
			return inside(mouseX, mouseY, x, y, width, height);
		}
	}
}
