package com.reystin.ninelives.command;

import com.reystin.ninelives.core.Mode;
import com.reystin.ninelives.core.ServerMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Кликабельные сообщения для ванильных клиентов: выбор режима.
 * Клик подставляет команду в чат (SUGGEST_COMMAND) — игроку остаётся
 * нажать Enter, это безопаснее мгновенного исполнения.
 */
public final class Clickables {
	private Clickables() {
	}

	/** Кнопки выбора режима: [Ванильные достижения] [Blaze and Caves]. */
	public static Component modeButtons() {
		return button("ninelives.mode.vanilla", "/ninelives mode vanilla", ChatFormatting.GREEN)
				.append(Component.literal("  ").withStyle(ChatFormatting.RESET))
				.append(button("ninelives.mode.bc", "/ninelives mode bc", ChatFormatting.GOLD));
	}

	/** Кнопка «дать жизнь» в выводе /ninelives info (подставляет команду в чат). */
	public static Component giveButton(String playerName) {
		return button("ninelives.cmd.info.give_button", "/ninelives give " + playerName + " 1", ChatFormatting.GOLD);
	}

	private static MutableComponent button(String key, String command, ChatFormatting color) {
		return Component.literal("  ").withStyle(ChatFormatting.BLACK)
				.append(ServerMessages.tr(key).copy()
						.withStyle(s -> s.withColor(color)
								.withClickEvent(new ClickEvent.SuggestCommand(command))
								.withUnderlined(true)));
	}
}
