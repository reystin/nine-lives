package com.reystin.ninelives.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reystin.ninelives.NineLives;
import com.reystin.ninelives.bacap.BacapInstaller;
import com.reystin.ninelives.core.LivesManager;
import com.reystin.ninelives.core.Mode;
import com.reystin.ninelives.core.ModeManager;
import com.reystin.ninelives.core.PlayerData;
import com.reystin.ninelives.core.ScreenData;
import com.reystin.ninelives.core.ServerMessages;
import com.reystin.ninelives.core.Net;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;

/**
 * Команды /ninelives:
 * - get|info [игрок]           — статус (чужой — только оператор);
 * - give <игрок> [кол-во]      — передача своих жизней (доступно любому игроку);
 * - set|add|reset <игрок> [n]  — управление (оператор);
 * - mode <vanilla|bc|clear>    — режим достижений (оператор);
 * - datapack status|install    — BACAP (оператор);
 * - config reload              — перечитать конфиг (оператор).
 */
public final class NinelivesCommand {
	private NinelivesCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ninelives");

			root.then(Commands.literal("get")
					.executes(ctx -> showInfo(ctx.getSource(), ctx.getSource().getPlayerOrException()))
					.then(Commands.argument("player", EntityArgument.player())
							.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
							.executes(ctx -> showInfo(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));

			root.then(Commands.literal("info")
					.executes(ctx -> showInfo(ctx.getSource(), ctx.getSource().getPlayerOrException()))
					.then(Commands.argument("player", EntityArgument.player())
							.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
							.executes(ctx -> showInfo(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));

			root.then(Commands.literal("give")
					.then(Commands.argument("player", EntityArgument.player())
							.executes(ctx -> give(ctx, 1))
							.then(Commands.argument("amount", IntegerArgumentType.integer(1))
									.executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))));

			root.then(Commands.literal("set")
					.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.argument("player", EntityArgument.player())
							.then(Commands.argument("lives", IntegerArgumentType.integer(0))
									.executes(ctx -> setLives(ctx, IntegerArgumentType.getInteger(ctx, "lives"))))));

			root.then(Commands.literal("add")
					.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.argument("player", EntityArgument.player())
							.then(Commands.argument("lives", IntegerArgumentType.integer(-1000, 1000))
									.executes(ctx -> addLives(ctx, IntegerArgumentType.getInteger(ctx, "lives"))))));

			root.then(Commands.literal("reset")
					.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.argument("player", EntityArgument.player())
							.executes(ctx -> reset(ctx))));

			root.then(Commands.literal("mode")
					.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.literal("vanilla").executes(ctx -> setMode(ctx, Mode.VANILLA)))
					.then(Commands.literal("bc").executes(ctx -> setMode(ctx, Mode.BACAP)))
					.then(Commands.literal("clear").executes(NinelivesCommand::clearMode)));

			root.then(Commands.literal("datapack")
					.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.literal("status").executes(NinelivesCommand::datapackStatus))
					.then(Commands.literal("install").executes(NinelivesCommand::datapackInstall)));

			root.then(Commands.literal("config")
					.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.literal("reload").executes(NinelivesCommand::configReload)));

			dispatcher.register(root);
		});
	}

	// =====================================================================

	private static int showInfo(CommandSourceStack source, ServerPlayer target) {
		PlayerData data = LivesManager.data(target);
		int perLife = ModeManager.effectiveMode(source.getServer()).advancementsPerLife(NineLives.config());
		int periodic = ModeManager.currentPeriodic(target);
		source.sendSuccess(() -> ServerMessages.tr("ninelives.cmd.info.header"), false);
		source.sendSuccess(() -> ServerMessages.tr("ninelives.cmd.info.lives", data.lives()), false);
		source.sendSuccess(() -> ServerMessages.tr("ninelives.cmd.info.advancements", data.counted().size()), false);
		source.sendSuccess(() -> ServerMessages.tr("ninelives.cmd.info.progress", periodic % perLife, perLife), false);
		String modeKey = ModeManager.isModeChosen(source.getServer())
				? ModeManager.effectiveMode(source.getServer()).translationKey()
				: "ninelives.screen.mode_unset";
		source.sendSuccess(() -> ServerMessages.tr("ninelives.cmd.info.mode",
				Component.translatableWithFallback(modeKey, com.reystin.ninelives.config.L10n.fallback(modeKey))), false);
		if (data.eliminated()) {
			source.sendSuccess(() -> ServerMessages.tr("ninelives.cmd.info.eliminated"), false);
		}
		ServerPlayer viewer = source.getPlayer();
		if (viewer != null && !viewer.getUUID().equals(target.getUUID()) && ServerMessages.isPrompter(viewer)) {
			source.sendSuccess(() -> Component.translatableWithFallback(
					"ninelives.cmd.info.give_hint", com.reystin.ninelives.config.L10n.fallback("ninelives.cmd.info.give_hint"),
					Clickables.giveButton(target.nameAndId().name())), false);
		}
		return 1;
	}

	private static int give(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer from = source.getPlayer();
		if (from == null) {
			source.sendFailure(ServerMessages.tr("ninelives.cmd.players_only"));
			return 0;
		}
		ServerPlayer to = EntityArgument.getPlayer(ctx, "player");
		int transferred = LivesManager.transfer(from, to, amount);
		if (transferred > 0) {
			Net.sendIfModded(from, ScreenData.build(from));
		}
		return transferred > 0 ? 1 : 0;
	}

	private static int setLives(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		LivesManager.setLives(target, value, false);
		ctx.getSource().sendSuccess(() -> ServerMessages.tr("ninelives.cmd.set",
				target.nameAndId().name(), value), true);
		return 1;
	}

	private static int addLives(CommandContext<CommandSourceStack> ctx, int delta) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		int before = LivesManager.lives(target);
		LivesManager.setLives(target, before + delta, false);
		int after = LivesManager.lives(target);
		ctx.getSource().sendSuccess(() -> ServerMessages.tr("ninelives.cmd.add",
				delta, target.nameAndId().name(), after), true);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		LivesManager.setLives(target, NineLives.config().startingLives, true);
		ctx.getSource().sendSuccess(() -> ServerMessages.tr("ninelives.cmd.reset",
				target.nameAndId().name(), NineLives.config().startingLives), true);
		return 1;
	}

	private static int setMode(CommandContext<CommandSourceStack> ctx, Mode mode) {
		ModeManager.setMode(ctx.getSource().getServer(), mode);
		ctx.getSource().sendSuccess(() -> ServerMessages.tr("ninelives.cmd.mode_set",
				Component.translatableWithFallback(mode.translationKey(),
						com.reystin.ninelives.config.L10n.fallback(mode.translationKey()))), true);
		return 1;
	}

	private static int clearMode(CommandContext<CommandSourceStack> ctx) {
		ModeManager.clearMode(ctx.getSource().getServer());
		ctx.getSource().sendSuccess(() -> ServerMessages.tr("ninelives.msg.mode_reset"), true);
		return 1;
	}

	private static int datapackStatus(CommandContext<CommandSourceStack> ctx) {
		boolean present = BacapInstaller.isPackPresent(ctx.getSource().getServer());
		ctx.getSource().sendSuccess(() -> ServerMessages.tr(
				present ? "ninelives.cmd.datapack_present" : "ninelives.cmd.datapack_missing"), false);
		return 1;
	}

	private static int datapackInstall(CommandContext<CommandSourceStack> ctx) {
		BacapInstaller.startInstall(ctx.getSource().getServer(), ctx.getSource().getPlayer());
		return 1;
	}

	private static int configReload(CommandContext<CommandSourceStack> ctx) {
		NineLives.reloadConfig();
		ctx.getSource().sendSuccess(() -> ServerMessages.tr("ninelives.cmd.config_reload"), true);
		return 1;
	}
}
