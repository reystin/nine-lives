package com.reystin.ninelives.client;

import com.reystin.ninelives.net.PayloadTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Клиентское состояние полной версии: последние данные от сервера,
 * таймеры эффектов, активные анимации. Эффекты считаются по времени
 * (System.nanoTime), поэтому не зависят от FPS.
 */
public final class ClientState {
	private ClientState() {
	}

	/** Последний sync от сервера (null — ещё нет данных). */
	public static volatile PayloadTypes.SyncPayload sync;

	/** Последние данные экрана жизней. */
	public static volatile PayloadTypes.ScreenDataPayload screenData;

	/** Конец красного мерцания краёв (мс, System.nanoTime). */
	public static long vignetteUntilNanos;

	/** Активные летящие сердца (анимация передачи). */
	public static final List<FlyingHeart> flyingHearts = new ArrayList<>();

	/** Диалог загрузки BACAP: текущий этап и процент. */
	public static volatile int downloadStage = -1;
	public static volatile int downloadPercent;

	/** Ждём данные экрана. */
	public static volatile boolean awaitingScreenData;

	/** Колбэк после обновления данных экрана (когда экран ждёт). */
	public static Consumer<PayloadTypes.ScreenDataPayload> screenDataListener;

	// Эффекты получения/потери/завершения прогресса.
	private static long gainAtNanos;
	private static long lossAtNanos;
	private static long progressFlashAtNanos;
	private static int lastLives = -1;
	private static int lastProgress = -1;

	private static final long GAIN_DURATION = 900_000_000L;
	private static final long LOSS_DURATION = 1_100_000_000L;
	private static final long PROGRESS_DURATION = 800_000_000L;

	public static synchronized void onSync(PayloadTypes.SyncPayload payload) {
		if (lastLives >= 0) {
			if (payload.lives() > lastLives) {
				gainAtNanos = System.nanoTime();
			} else if (payload.lives() < lastLives) {
				lossAtNanos = System.nanoTime();
			} else if (payload.progress() < lastProgress) {
				// Прогресс перескочил через порог, но жизнь ещё не пришла в этом пакете.
				progressFlashAtNanos = System.nanoTime();
			}
		}
		lastLives = payload.lives();
		lastProgress = payload.progress();
		sync = payload;
	}

	public static synchronized void triggerGain() {
		gainAtNanos = System.nanoTime();
	}

	public static synchronized void triggerLoss() {
		lossAtNanos = System.nanoTime();
	}

	public static synchronized void triggerProgressFlash() {
		progressFlashAtNanos = System.nanoTime();
	}

	/** 1 в момент события, плавно к 0. */
	public static float gainEffect() {
		return decay(gainAtNanos, GAIN_DURATION);
	}

	public static float lossEffect() {
		return decay(lossAtNanos, LOSS_DURATION);
	}

	public static float progressEffect() {
		return decay(progressFlashAtNanos, PROGRESS_DURATION);
	}

	private static float decay(long startNanos, long durationNanos) {
		if (startNanos == 0) return 0f;
		long elapsed = System.nanoTime() - startNanos;
		if (elapsed < 0 || elapsed >= durationNanos) return 0f;
		float t = elapsed / (float) durationNanos;
		return (1f - t) * (1f - t);
	}

	public static void triggerVignette(long durationNanos) {
		vignetteUntilNanos = System.nanoTime() + durationNanos;
	}

	public static float vignetteAlpha() {
		long now = System.nanoTime();
		if (now >= vignetteUntilNanos) return 0f;
		long left = vignetteUntilNanos - now;
		double seconds = left / 1_000_000_000.0;
		// Пульсирующее затухание: 3 вспышки за ~2 секунды.
		double pulse = 0.55 + 0.45 * Math.sin(seconds * 12.0);
		double fade = Math.min(1.0, seconds / 2.5);
		return (float) (0.45 * pulse * fade);
	}

	/** Новый вход/выход: данные предыдущего сервера не должны просачиваться. */
	public static synchronized void reset() {
		sync = null;
		screenData = null;
		lastLives = -1;
		lastProgress = -1;
		gainAtNanos = 0;
		lossAtNanos = 0;
		progressFlashAtNanos = 0;
		vignetteUntilNanos = 0;
		flyingHearts.clear();
		downloadStage = -1;
		downloadPercent = 0;
		awaitingScreenData = false;
		screenDataListener = null;
	}

	/** Летящее сердце при передаче. */
	public static final class FlyingHeart {
		public final String from;
		public final String to;
		public final int amount;
		public final long startNanos;
		public final long durationNanos = 1_400_000_000L;

		public FlyingHeart(String from, String to, int amount) {
			this.from = from;
			this.to = to;
			this.amount = amount;
			this.startNanos = System.nanoTime();
		}

		public float progress() {
			return Math.min(1f, (System.nanoTime() - startNanos) / (float) durationNanos);
		}

		public boolean done() {
			return progress() >= 1f;
		}
	}
}
