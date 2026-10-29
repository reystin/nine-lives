package com.reystin.ninelives.client;

import com.reystin.ninelives.client.ui.DatapackDialogScreen;
import com.reystin.ninelives.client.ui.ModeSelectScreen;
import com.reystin.ninelives.client.ui.NineLivesToast;
import com.reystin.ninelives.config.NLClientConfig;
import com.reystin.ninelives.net.PayloadTypes;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Приёмники S2C и отправка C2S. Регистрируется только на клиентах с модом;
 * сервер не шлёт каналы клиентам без handshake, поэтому здесь безопасно.
 *
 * Сетевые пакеты обрабатываются в клиентском потоке. Запросы выбора режима и
 * установки датапака ставятся в очередь: если в этот момент открыт другой
 * экран, они покажутся сразу после его закрытия.
 */
public final class ClientNet {
	private static PayloadTypes.ModePromptPayload pendingModePrompt;
	private static boolean pendingInstallDialog;

	private ClientNet() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PayloadTypes.SyncPayload.TYPE, (payload, context) ->
				Minecraft.getInstance().execute(() -> {
					ClientState.onSync(payload);
					if (payload.mode() >= 0) {
						// Режим уже выбран (возможно, другим оператором): запрос устарел.
						pendingModePrompt = null;
						Minecraft mc = Minecraft.getInstance();
						if (mc.gui.screen() instanceof ModeSelectScreen) {
							mc.gui.setScreen(null);
						}
					}
				}));

		ClientPlayNetworking.registerGlobalReceiver(PayloadTypes.ScreenDataPayload.TYPE, (payload, context) ->
				Minecraft.getInstance().execute(() -> {
					ClientState.onSync(payload.self());
					ClientState.screenData = payload;
					ClientState.awaitingScreenData = false;
					var listener = ClientState.screenDataListener;
					if (listener != null) listener.accept(payload);
				}));

		ClientPlayNetworking.registerGlobalReceiver(PayloadTypes.LifeEventPayload.TYPE, (payload, context) ->
				Minecraft.getInstance().execute(() -> onLifeEvent(payload)));

		ClientPlayNetworking.registerGlobalReceiver(PayloadTypes.TransferFxPayload.TYPE, (payload, context) ->
				Minecraft.getInstance().execute(() -> {
					ClientState.flyingHearts.add(new ClientState.FlyingHeart(payload.from(), payload.to(), payload.amount()));
					playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
				}));

		ClientPlayNetworking.registerGlobalReceiver(PayloadTypes.DownloadProgressPayload.TYPE, (payload, context) ->
				Minecraft.getInstance().execute(() -> {
					ClientState.downloadStage = payload.stage();
					ClientState.downloadPercent = payload.percent();
					if (payload.stage() == PayloadTypes.DownloadProgressPayload.STAGE_ERROR) {
						DatapackDialogScreen.showError(payload.errorKey(), payload.errorDetail());
					} else if (payload.stage() == PayloadTypes.DownloadProgressPayload.STAGE_DONE) {
						DatapackDialogScreen.markDone();
					}
					if (payload.stage() != PayloadTypes.DownloadProgressPayload.STAGE_DONE) {
						queueInstallDialog();
					}
				}));

		ClientPlayNetworking.registerGlobalReceiver(PayloadTypes.ModePromptPayload.TYPE, (payload, context) ->
				Minecraft.getInstance().execute(() -> {
					pendingModePrompt = payload;
					showPendingPrompts();
				}));

		// Handshake сразу после входа в мир.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
			ClientState.reset();
			pendingModePrompt = null;
			pendingInstallDialog = false;
			DatapackDialogScreen.resetState();
			if (ClientPlayNetworking.canSend(PayloadTypes.HelloPayload.TYPE)) {
				ClientPlayNetworking.send(new PayloadTypes.HelloPayload());
			}
		}));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientState.reset();
			pendingModePrompt = null;
			pendingInstallDialog = false;
			DatapackDialogScreen.resetState();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> showPendingPrompts());
	}

	/** Показать отложенный запрос/прогресс, когда закрыт текущий экран. */
	private static void showPendingPrompts() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != null) return;
		if (pendingModePrompt != null) {
			if (ClientState.sync != null && ClientState.sync.mode() >= 0) {
				pendingModePrompt = null;
			} else {
				var prompt = pendingModePrompt;
				pendingModePrompt = null;
				mc.gui.setScreen(new ModeSelectScreen(prompt.vanillaPerLife(), prompt.bacapPerLife()));
				return;
			}
		}
		if (pendingInstallDialog) {
			openInstallDialog(mc);
		}
	}

	/** Открыть прогресс установки сразу или после закрытия текущего экрана. */
	private static void queueInstallDialog() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() instanceof DatapackDialogScreen) return;
		if (mc.gui.screen() != null) {
			pendingInstallDialog = true;
			return;
		}
		openInstallDialog(mc);
	}

	private static void openInstallDialog(Minecraft mc) {
		pendingInstallDialog = false;
		int stage = ClientState.downloadStage;
		if (stage < 0 || stage == PayloadTypes.DownloadProgressPayload.STAGE_DONE) return;
		mc.gui.setScreen(stage == PayloadTypes.DownloadProgressPayload.STAGE_ERROR
				? DatapackDialogScreen.errorScreen(null)
				: DatapackDialogScreen.progressScreen(null));
	}

	/** Запросить данные экрана жизней у сервера. */
	public static void requestScreenData() {
		if (!ClientPlayNetworking.canSend(PayloadTypes.OpenScreenPayload.TYPE)) return;
		ClientState.awaitingScreenData = true;
		ClientPlayNetworking.send(new PayloadTypes.OpenScreenPayload());
	}

	private static void onLifeEvent(PayloadTypes.LifeEventPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		NLClientConfig config = NLClientConfig.get();
		switch (payload.kind()) {
			case 0 -> { // жизнь получена
				ClientState.triggerGain();
				ClientState.triggerProgressFlash();
				Component advTitle = payload.advancement().isPresent()
						? resolveAdvancementTitle(payload.advancement().get()) : null;
				if (config.sounds) {
					playSound(SoundEvents.PLAYER_LEVELUP, 0.8f, 1.4f);
				}
				mc.gui.toastManager().addToast(new NineLivesToast(
						Component.translatable("ninelives.toast.life_gained"), advTitle, NineLivesToast.Kind.GOLD));
			}
			case 1 -> { // жизнь потеряна
				ClientState.triggerLoss();
				if (config.sounds) {
					playSound(SoundEvents.WITHER_HURT, 0.6f, 0.7f);
				}
				if (config.particles) {
					ClientState.triggerVignette(2_500_000_000L);
				}
				mc.gui.toastManager().addToast(new NineLivesToast(
						Component.translatable("ninelives.toast.life_lost"), null, NineLivesToast.Kind.RED));
			}
			case 2 -> { // выбывание
				ClientState.triggerLoss();
				if (config.sounds) {
					playSound(SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.7f);
				}
				mc.gui.toastManager().addToast(new NineLivesToast(
						Component.translatable("ninelives.toast.eliminated"), null, NineLivesToast.Kind.RED));
			}
			case 3 -> { // возврат в игру
				ClientState.triggerGain();
				if (config.sounds) {
					playSound(SoundEvents.TOTEM_USE, 0.9f, 1.1f);
				}
				mc.gui.toastManager().addToast(new NineLivesToast(
						Component.translatable("ninelives.toast.revived"), null, NineLivesToast.Kind.GOLD));
			}
			default -> {
			}
		}
	}

	/** Заголовок достижения из синхронизированного клиентского дерева. */
	public static Component resolveAdvancementTitle(String id) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.getConnection() != null) {
				var holder = mc.getConnection().getAdvancements().get(net.minecraft.resources.Identifier.parse(id));
				if (holder != null && holder.value().display().isPresent()) {
					return holder.value().display().get().title();
				}
			}
		} catch (Exception ignored) {
		}
		return Component.literal(id);
	}

	public static void playSound(net.minecraft.sounds.SoundEvent event, float volume, float pitch) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.playSound(event, volume, pitch);
		}
	}
}
