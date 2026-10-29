package com.reystin.ninelives.client;

import com.reystin.ninelives.NineLives;
import com.reystin.ninelives.client.ui.DatapackDialogScreen;
import com.reystin.ninelives.client.ui.LivesScreen;
import com.reystin.ninelives.client.ui.ModeSelectScreen;
import com.reystin.ninelives.net.PayloadTypes;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Dev-showcase: автоматический сценарий для скриншотов интерфейса
 * (активируется -Dninelives.showcase=true). В обычной работе не включается.
 *
 * Сценарий: создать тестовый мир → выдать жизни/достижения/поглощение →
 * дождаться данных → экран жизней (3 вкладки) → Tab → инвентарь → HUD.
 * Перед каждым кадром чат и тосты очищаются, Tab удерживается ≥20 тиков.
 */
public final class Showcase {
	private static final boolean ENABLED = Boolean.getBoolean("ninelives.showcase");
	/** Отдельный сценарий: выбор BACAP → реальная авто-установка с Modrinth. */
	private static final boolean AUTO_BACAP = Boolean.getBoolean("ninelives.showcase.bacap");
	private static int tick;
	private static int phase;
	private static boolean wasInLevel;
	private static final String WORLD = AUTO_BACAP
			? "nlbacap" + (System.currentTimeMillis() / 1000) : "nlshowcase";

	private Showcase() {
	}

	public static void init() {
		if (!ENABLED) return;
		NineLives.LOGGER.info("[Nine Lives] SHOWCASE mode enabled");
		ClientTickEvents.END_CLIENT_TICK.register(Showcase::tick);
	}

	private static void tick(Minecraft mc) {
		tick++;
		boolean inLevel = mc.level != null;
		if (inLevel && !wasInLevel) {
			tick = 0;
			phase = 0;
		}
		wasInLevel = inLevel;
		if (mc.level == null) {
			if (tick % 40 == 0) {
				var screen = mc.gui.screen();
				if (screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) {
					onboarding.onClose();
				} else if (screen instanceof TitleScreen) {
					NineLives.LOGGER.info("[Nine Lives] showcase: creating world {}", WORLD);
					mc.createWorldOpenFlows().createFreshLevel(WORLD,
							new LevelSettings(WORLD, GameType.SURVIVAL, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT),
							new WorldOptions("ninelives".hashCode(), true, false),
							WorldPresets::createNormalWorldDimensions,
							screen);
				}
			}
			return;
		}

		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null) return;

		// Не даём экрану выбора режима/паузе испортить кадры.
		var current = mc.gui.screen();
		if ((current instanceof ModeSelectScreen && phase < 8)
				|| (inLevel && current instanceof net.minecraft.client.gui.screens.PauseScreen)) {
			mc.gui.setScreen(null);
		}

		if (AUTO_BACAP) {
			tickAutoBacap(mc, server);
			return;
		}

		switch (phase) {
			case 0 -> {
				if (tick == 10) {
					cmd(server, "ninelives mode vanilla");
					asPlayer(mc, "advancement grant @s only minecraft:end/kill_dragon");
					asPlayer(mc, "ninelives set @s 12");
					asPlayer(mc, "effect give @s minecraft:absorption 99999 3 true");
					ClientNet.requestScreenData();
					log("world ready, data requested");
				}
				if (tick >= 20) {
					if (ClientState.screenData == null) {
						if (tick % 20 == 0) ClientNet.requestScreenData();
					} else {
						next();
					}
				}
			}
			case 1 -> {
				if (tick >= 15) {
					LivesScreen.debugTab = 0;
					mc.gui.setScreen(new LivesScreen());
					log("screen: overview");
					next();
				}
			}
			case 2 -> {
				if (tick >= 10) {
					cleanOverlays(mc);
					parkCursor(mc);
				}
				if (tick >= 40) {
					snap(mc, "ninelives_screen_overview");
					LivesScreen.debugTab = 1;
					mc.gui.setScreen(new LivesScreen());
					log("screen: advancements");
					next();
				}
			}
			case 3 -> {
				if (tick >= 10) {
					cleanOverlays(mc);
					parkCursor(mc);
				}
				if (tick >= 40) {
					snap(mc, "ninelives_screen_advancements");
					LivesScreen.debugTab = 2;
					mc.gui.setScreen(new LivesScreen());
					log("screen: players");
					next();
				}
			}
			case 4 -> {
				if (tick >= 10) {
					cleanOverlays(mc);
					parkCursor(mc);
				}
				if (tick >= 40) {
					snap(mc, "ninelives_screen_players");
					mc.gui.setScreen(null);
					LivesScreen.debugTab = -1;
					cmd(server, "scoreboard objectives add nl dummy");
					cmd(server, "scoreboard objectives setdisplay list nl");
					mc.options.keyPlayerList.setDown(true);
					log("tab list (holding key)");
					next();
				}
			}
			case 5 -> {
				if (tick >= 10) {
					cleanOverlays(mc);
					parkCursor(mc);
				}
				if (tick >= 30) {
					snap(mc, "ninelives_tab");
					mc.options.keyPlayerList.setDown(false);
					mc.gui.setScreen(new InventoryScreen(mc.player));
					log("inventory button");
					next();
				}
			}
			case 6 -> {
				if (tick >= 10) {
					cleanOverlays(mc);
					parkCursor(mc);
				}
				if (tick >= 30) {
					snap(mc, "ninelives_inventory_button");
					mc.gui.setScreen(null);
					log("hud");
					next();
				}
			}
			case 7 -> {
				if (tick >= 8) {
					cleanOverlays(mc);
					parkCursor(mc);
				}
				if (tick >= 25) {
					snap(mc, "ninelives_hud");
					mc.gui.setScreen(new ModeSelectScreen(10, 50));
					log("mode select");
					next();
				}
			}
			case 8 -> {
				if (tick >= 8) {
					cleanOverlays(mc);
					parkCursor(mc);
				}
				if (tick >= 30) {
					snap(mc, "ninelives_mode_select");
					ClientState.downloadStage = PayloadTypes.DownloadProgressPayload.STAGE_DOWNLOADING;
					ClientState.downloadPercent = 47;
					mc.gui.setScreen(DatapackDialogScreen.progressScreen(null));
					log("datapack progress");
					next();
				}
			}
			case 9 -> {
				if (tick >= 8) {
					cleanOverlays(mc);
					parkCursor(mc);
				}
				if (tick >= 30) {
					snap(mc, "ninelives_datapack_progress");
					log("done, stopping");
					mc.stop();
				}
			}
		}
	}

	/** Сценарий авто-установки: выбор BACAP пакетом, ожидание загрузки, проверка экрана. */
	private static void tickAutoBacap(Minecraft mc, MinecraftServer server) {
		switch (phase) {
			case 0 -> {
				if (tick == 10) {
					cmd(server, "ninelives mode clear");
					ClientPlayNetworking.send(new PayloadTypes.ModeSelectPayload(1));
					log("auto-bacap: BACAP mode select sent");
				}
				if (tick >= 20) {
					if (ClientState.downloadStage >= 0
							|| ClientState.downloadStage == PayloadTypes.DownloadProgressPayload.STAGE_ERROR) {
						log("auto-bacap: install stage " + ClientState.downloadStage);
						next();
					} else if (tick > 300) {
						log("auto-bacap: install did not start");
						mc.stop();
					}
				}
			}
			case 1 -> {
				if (tick >= 5) {
					cleanOverlays(mc);
					parkCursor(mc);
				}
				int stage = ClientState.downloadStage;
				boolean finished = stage == PayloadTypes.DownloadProgressPayload.STAGE_DONE
						|| stage == PayloadTypes.DownloadProgressPayload.STAGE_ERROR;
				if (finished && tick >= 10) {
					if (mc.gui.screen() instanceof DatapackDialogScreen) {
						snap(mc, stage == PayloadTypes.DownloadProgressPayload.STAGE_DONE
								? "ninelives_bacap_done" : "ninelives_bacap_error");
					}
					log("auto-bacap: finished at stage " + stage
							+ " percent " + ClientState.downloadPercent);
					next();
				} else if (tick > 2400) {
					log("auto-bacap: timeout at stage " + stage);
					mc.stop();
				}
			}
			case 2 -> {
				if (tick == 1) {
					mc.gui.setScreen(null);
					ClientNet.requestScreenData();
				}
				if (tick >= 20 && ClientState.screenData != null) {
					var self = ClientState.screenData.self();
					log("auto-bacap: mode=" + self.mode() + " adv=" + self.advCount()
							+ " hard=" + ClientState.screenData.hard().size()
							+ " nearest=" + ClientState.screenData.nearest().size());
					LivesScreen.debugTab = 1;
					mc.gui.setScreen(new LivesScreen());
					next();
				} else if (tick > 200) {
					log("auto-bacap: no screen data");
					mc.stop();
				}
			}
			case 3 -> {
				if (tick >= 5) {
					cleanOverlays(mc);
					parkCursor(mc);
				}
				if (tick >= 30) {
					snap(mc, "ninelives_bacap_advancements");
					log("auto-bacap: done, stopping");
					mc.stop();
				}
			}
			default -> mc.stop();
		}
	}

	/** Убрать чат и тосты, чтобы они не перекрывали HUD/экраны. */
	private static void cleanOverlays(Minecraft mc) {
		mc.gui.hud.getChat().clearMessages(true);
		mc.gui.toastManager().clear();
	}

	/** Курсор в угол окна: иначе случайный hover рисует тултип поверх кадра. */
	private static void parkCursor(Minecraft mc) {
		var window = mc.getWindow();
		mc.mouseHandler.onMove(window.handle(), 8, window.getHeight() - 8, 0, 0);
	}

	private static void next() {
		phase++;
		tick = 0;
	}

	/** Команда от имени игрока (op в одиночной игре с читами). */
	private static void asPlayer(Minecraft mc, String command) {
		mc.player.connection.sendCommand(command);
	}

	/** Команда на интегрированном сервере от имени консоли (уровень 4). */
	private static void cmd(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(
				server.createCommandSourceStack()
						.withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER),
				command);
	}

	private static void snap(Minecraft mc, String name) {
		// 26.3 пишет имя файла дословно, расширение не добавляется автоматически.
		Screenshot.grab(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toFile(), name + ".png",
				mc.gameRenderer.mainRenderTarget(), 1,
				component -> NineLives.LOGGER.info("[Nine Lives] screenshot {}: {}", name, component.getString()));
	}

	private static void log(String message) {
		NineLives.LOGGER.info("[Nine Lives] showcase: {}", message);
	}
}
