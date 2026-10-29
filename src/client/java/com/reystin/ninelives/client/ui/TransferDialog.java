package com.reystin.ninelives.client.ui;

import com.reystin.ninelives.client.hud.Textures;
import com.reystin.ninelives.net.PayloadTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Модальный диалог передачи жизней: выбор количества −/+, подтверждение.
 * Ввод приходит в координатах панели экрана (с учётом uiScale).
 */
public class TransferDialog {
	private static final int W = 180;
	private static final int H = 104;
	private static final int STEP = 18;

	private final PayloadTypes.ScreenDataPayload.PlayerEntry target;
	private final int maxAmount;
	private int amount;
	private boolean confirmed;
	private boolean close;

	public TransferDialog(PayloadTypes.ScreenDataPayload.PlayerEntry target, int maxAmount) {
		this.target = target;
		this.maxAmount = Math.max(0, maxAmount);
		this.amount = this.maxAmount > 0 ? 1 : 0;
	}

	public PayloadTypes.ScreenDataPayload.PlayerEntry target() {
		return target;
	}

	public int amount() {
		return amount;
	}

	public boolean confirmed() {
		return confirmed;
	}

	public boolean shouldClose() {
		return close;
	}

	public void draw(GuiGraphicsExtractor graphics, int panelX, int panelY, int panelW, int panelH,
			float mouseX, float mouseY) {
		Minecraft mc = Minecraft.getInstance();
		int x = panelX + (panelW - W) / 2;
		int y = panelY + (panelH - H) / 2;

		graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0x77000000);
		LivesScreen.drawPanel(graphics, x, y, W, H);

		graphics.centeredText(mc.font, Component.translatable("ninelives.screen.give_to", target.name()),
				x + W / 2, y + 10, 0xFFFFD980);

		Component availability = maxAmount > 0
				? Component.translatable("ninelives.screen.give_available", maxAmount)
				: Component.translatable("ninelives.msg.give_not_enough");
		graphics.centeredText(mc.font, availability, x + W / 2, y + 26,
				maxAmount > 0 ? 0xFFB9AAB0 : 0xFFFF7070);

		// [−] N [+]
		int midY = y + 42;
		boolean minusHover = drawStepButton(graphics, x + 34, midY, "−", mouseX, mouseY, amount > 0);
		boolean plusHover = drawStepButton(graphics, x + W - 34 - STEP, midY, "+", mouseX, mouseY, amount < maxAmount);
		graphics.centeredText(mc.font, Component.translatable("ninelives.screen.give_amount", amount),
				x + W / 2, midY + 6, 0xFFFFD980);

		// Подтверждение и отмена.
		int btnY = y + H - 26;
		boolean confirmHover = inside(mouseX, mouseY, x + 14, btnY, 72, 18);
		boolean confirmEnabled = amount > 0 && maxAmount > 0;
		Textures.button(graphics, confirmHover && confirmEnabled, x + 14, btnY, 72, 18);
		graphics.centeredText(mc.font, Component.translatable("ninelives.screen.give_confirm"),
				x + 50, btnY + 5, confirmEnabled ? (confirmHover ? 0xFFFFFFFF : 0xFFE0D6DA) : 0xFF7A7076);

		boolean cancelHover = inside(mouseX, mouseY, x + W - 86, btnY, 72, 18);
		Textures.button(graphics, cancelHover, x + W - 86, btnY, 72, 18);
		graphics.centeredText(mc.font, Component.translatable("ninelives.screen.close"),
				x + W - 50, btnY + 5, cancelHover ? 0xFFFFFFFF : 0xFFE0D6DA);
	}

	private boolean drawStepButton(GuiGraphicsExtractor graphics, int x, int y, String label,
			float mouseX, float mouseY, boolean enabled) {
		boolean hovered = enabled && inside(mouseX, mouseY, x, y, STEP, STEP);
		Textures.button(graphics, hovered, x, y, STEP, STEP);
		graphics.centeredText(Minecraft.getInstance().font, Component.literal(label),
				x + STEP / 2, y + 5, enabled ? (hovered ? 0xFFFFFFFF : 0xFFE0D6DA) : 0xFF6E6469);
		return hovered;
	}

	/** @return true — клик обработан диалогом (в т.ч. поглощён). */
	public boolean mouseClicked(float mouseX, float mouseY, int panelX, int panelY) {
		int x = panelX + (LivesScreen.PANEL_W - W) / 2;
		int y = panelY + (LivesScreen.PANEL_H - H) / 2;
		int midY = y + 42;
		int btnY = y + H - 26;

		if (maxAmount > 0 && inside(mouseX, mouseY, x + 34, midY, STEP, STEP)) {
			amount = Math.max(1, amount - 1);
		}
		if (inside(mouseX, mouseY, x + W - 34 - STEP, midY, STEP, STEP)) {
			amount = Math.min(maxAmount, amount + 1);
		}
		if (inside(mouseX, mouseY, x + 14, btnY, 72, 18) && amount > 0 && maxAmount > 0) {
			confirmed = true;
			close = true;
		} else if (inside(mouseX, mouseY, x + W - 86, btnY, 72, 18)) {
			close = true;
		}
		return true;
	}

	public void mouseScrolled(double scrollY) {
		if (maxAmount <= 0) return;
		int delta = (int) Math.round(scrollY);
		amount = Math.max(1, Math.min(maxAmount, amount + delta));
	}

	private static boolean inside(float mouseX, float mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}
}
