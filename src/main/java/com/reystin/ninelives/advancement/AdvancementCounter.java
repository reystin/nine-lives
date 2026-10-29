package com.reystin.ninelives.advancement;

import com.reystin.ninelives.NineLives;
import com.reystin.ninelives.core.Attachments;
import com.reystin.ninelives.core.LivesManager;
import com.reystin.ninelives.core.Mode;
import com.reystin.ninelives.core.ModeManager;
import com.reystin.ninelives.core.Net;
import com.reystin.ninelives.core.PlayerData;
import com.reystin.ninelives.core.ServerMessages;
import com.reystin.ninelives.net.PayloadTypes;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Подсчёт достижений и выдача жизней.
 *
 * Анти-накрутка: каждое достижение учитывается ровно один раз за всё время
 * (набор counted хранится на игроке и переживает revoke/перезаход).
 * Считаются только достижения с display и не-корневые (рецепты и вкладки
 * отсекаются). Скрытые считаются после получения — они и так завершаются
 * стандартным путём.
 *
 * Хард-достижение (или супер-челлендж) даёт +1 жизнь и при этом продолжает
 * считаться обычным достижением: если оно закрывает очередные N достижений,
 * игрок получает сразу обе жизни.
 */
public final class AdvancementCounter {
	private AdvancementCounter() {
	}

	/** Достижение засчитывается? (display есть, корень — нет). */
	public static boolean isCountable(Advancement advancement) {
		return advancement.display().isPresent() && !advancement.isRoot();
	}

	/** Вызывается из миксина, когда достижение только что завершено. */
	public static void onAward(ServerPlayer player, AdvancementHolder holder) {
		Advancement advancement = holder.value();
		if (!isCountable(advancement)) return;
		MinecraftServer server = NineLives.serverOf(player);
		Mode mode = ModeManager.effectiveMode(server);
		Identifier id = holder.id();

		PlayerData data = LivesManager.data(player);
		String idStr = id.toString();
		if (data.counted().contains(idStr)) {
			// Уже засчитано ранее (revoke + получить снова — жизнь не удваивается).
			return;
		}
		if (!namespaceMatches(mode, id)) {
			// В режиме BACAP учитывается только пространство имён датапака и наоборот.
			return;
		}

		Set<String> counted = new HashSet<>(data.counted());
		counted.add(idStr);
		boolean superChallenge = mode == Mode.BACAP && isSuperChallenge(server, holder);
		boolean hard = mode == Mode.VANILLA && NineLives.config().hardAdvancements.contains(idStr);
		boolean bonusLife = superChallenge || hard;

		int perLife = Math.max(1, mode.advancementsPerLife(NineLives.config()));
		int periodic = (mode == Mode.BACAP ? data.periodicBacap() : data.periodicVanilla()) + 1;
		boolean periodicLife = periodic % perLife == 0;
		int gained = (bonusLife ? 1 : 0) + (periodicLife ? 1 : 0);

		player.modifyAttached(Attachments.PLAYER_DATA, d -> new PlayerData(
				d.lives(), counted,
				mode == Mode.BACAP ? d.periodicVanilla() : periodic,
				mode == Mode.BACAP ? periodic : d.periodicBacap(),
				d.eliminated(), d.protectionUntil(),
				d.newLifePulse() || gained > 0));

		if (gained > 0) {
			int lives = LivesManager.lives(player) + gained;
			LivesManager.setLives(player, lives, false);
			if (bonusLife) {
				ServerMessages.chat(player, "ninelives.msg.life_gained", entryTitle(holder), lives);
			}
			if (periodicLife) {
				ServerMessages.chat(player, "ninelives.msg.life_gained_progress", perLife, lives);
			}
			Net.sendIfModded(player, new PayloadTypes.LifeEventPayload(0, java.util.Optional.of(idStr)));
			ServerMessages.notifySound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.4f);
			NineLives.LOGGER.info("[Nine Lives] {} earned {} life(s) from {} ({}; periodic={})",
					player.nameAndId().name(), gained, idStr,
					superChallenge ? "super challenge" : hard ? "hard advancement" : "advancement",
					periodicLife);
		}
		ServerMessages.updateTabName(player);
		Net.syncTo(player);
	}

	/** Namespace достижений, который учитывается в режиме. */
	public static boolean namespaceMatches(Mode mode, Identifier id) {
		if (mode == Mode.BACAP) {
			return NineLives.config().bacapNamespaces.contains(id.getNamespace());
		}
		return "minecraft".equals(id.getNamespace());
	}

	// =====================================================================
	// Импорт достижений, полученных до установки мода
	// =====================================================================

	/**
	 * Импортирует уже полученные достижения игрока (при первом входе после
	 * установки мода или после включения BACAP). Возвращает число новых
	 * засчитанных достижений.
	 */
	public static int importExisting(ServerPlayer player) {
		MinecraftServer server = NineLives.serverOf(player);
		Mode mode = ModeManager.effectiveMode(server);
		PlayerData data = LivesManager.data(player);
		Set<String> counted = new HashSet<>(data.counted());
		int periodicVanilla = data.periodicVanilla();
		int periodicBacap = data.periodicBacap();
		int imported = 0;
		int livesBefore = data.lives();

		for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
			Identifier id = holder.id();
			if (counted.contains(id.toString())) continue;
			if (!isCountable(holder.value())) continue;
			if (!namespaceMatches(mode, id)) continue;
			AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
			if (!progress.isDone()) continue;

			counted.add(id.toString());
			if (mode == Mode.BACAP) {
				periodicBacap++;
				if (isSuperChallenge(server, holder)) {
					LivesManager.setLives(player, LivesManager.lives(player) + 1, false);
				}
			} else {
				periodicVanilla++;
				if (NineLives.config().hardAdvancements.contains(id.toString())) {
					LivesManager.setLives(player, LivesManager.lives(player) + 1, false);
				}
			}
			imported++;
		}

		if (imported > 0) {
			int perLife = Math.max(1, mode.advancementsPerLife(NineLives.config()));
			int periodic = mode == Mode.BACAP ? periodicBacap : periodicVanilla;
			int livesFromPeriodic = periodic / perLife;
			int oldPeriodic = mode == Mode.BACAP ? data.periodicBacap() : data.periodicVanilla();
			int oldLives = oldPeriodic / perLife;
			if (livesFromPeriodic > oldLives) {
				LivesManager.setLives(player, LivesManager.lives(player) + (livesFromPeriodic - oldLives), false);
			}
			final int fPv = periodicVanilla;
			final int fPb = periodicBacap;
			final boolean pulse = LivesManager.lives(player) > livesBefore;
			player.modifyAttached(Attachments.PLAYER_DATA, d -> new PlayerData(
					d.lives(), counted, fPv, fPb, d.eliminated(), d.protectionUntil(),
					d.newLifePulse() || pulse));
			ServerMessages.chat(player, "ninelives.msg.imported", imported);
			Net.syncTo(player);
			ServerMessages.updateTabName(player);
		}
		return imported;
	}

	/** Пересчитать прогресс всех онлайн-игроков (после установки BACAP / смены режима). */
	public static void recalculateAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			importExisting(player);
		}
	}

	// =====================================================================
	// BACAP: супер-челленджи
	// =====================================================================

	/**
	 * Супер-челлендж BACAP? Детект по дереву: корень вкладки, к которой ведёт
	 * цепочка родителей, должен лежать во вкладке супер-челленджей; плюс
	 * запасной список точных id из конфига.
	 */
	public static boolean isSuperChallenge(MinecraftServer server, AdvancementHolder holder) {
		if (NineLives.config().superChallenges.contains(holder.id().toString())) {
			return true;
		}
		// Идём по цепочке родителей до корня вкладки.
		AdvancementHolder current = holder;
		int depth = 0;
		while (current != null && depth++ < 16) {
			var parent = current.value().parent();
			if (parent.isEmpty()) break;
			current = server.getAdvancements().get(parent.get());
		}
		if (current == null) return false;
		// Корень вкладки супер-челленджей (подтверждено по реальному паку BACAP:
		// вкладка challenges/root называется "Super Challenges").
		return NineLives.config().bacapSuperRoots.contains(current.id().toString());
	}

	/** Список id ближайших к завершению достижений и хард-списка — для экрана. */
	public static List<PayloadTypes.ScreenDataPayload.AdvEntry> nearestAdvancements(ServerPlayer player, int limit) {
		record Candidate(AdvancementHolder holder, boolean done, double percent, boolean hard) {
		}
		MinecraftServer server = NineLives.serverOf(player);
		Mode mode = ModeManager.effectiveMode(server);
		List<Candidate> candidates = new ArrayList<>();
		for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
			if (!isCountable(holder.value())) continue;
			if (!namespaceMatches(mode, holder.id())) continue;
			AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
			boolean done = progress.isDone();
			if (done) continue; // ближайшие — только незавершённые
			int completed = countOf(progress.getCompletedCriteria());
			int remaining = countOf(progress.getRemainingCriteria());
			int total = completed + remaining;
			double percent = total == 0 ? 0 : 100.0 * completed / total;
			boolean hard = mode == Mode.VANILLA
					? NineLives.config().hardAdvancements.contains(holder.id().toString())
					: isSuperChallenge(server, holder);
			candidates.add(new Candidate(holder, false, percent, hard));
		}
		// Сначала хард-список, потом по убыванию прогресса.
		candidates.sort((a, b) -> {
			if (a.hard() != b.hard()) return a.hard() ? -1 : 1;
			return Double.compare(b.percent(), a.percent());
		});
		List<PayloadTypes.ScreenDataPayload.AdvEntry> result = new ArrayList<>();
		for (Candidate c : candidates.subList(0, Math.min(limit, candidates.size()))) {
			result.add(entry(c.holder(), c.done(), (int) c.percent()));
		}
		return result;
	}

	/** Хард-список (или супер-челленджи) с отметками выполнено/нет — для экрана. */
	public static List<PayloadTypes.ScreenDataPayload.AdvEntry> hardListFor(ServerPlayer player) {
		MinecraftServer server = NineLives.serverOf(player);
		Mode mode = ModeManager.effectiveMode(server);
		List<PayloadTypes.ScreenDataPayload.AdvEntry> result = new ArrayList<>();
		if (mode == Mode.BACAP) {
			for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
				if (isSuperChallenge(server, holder) && isCountable(holder.value())) {
					boolean done = player.getAdvancements().getOrStartProgress(holder).isDone();
					result.add(entry(holder, done, done ? 100 : 0));
				}
			}
		} else {
			for (String idStr : NineLives.config().hardAdvancements) {
				AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(idStr));
				boolean done = holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
				AdvancementProgress progress = holder != null
						? player.getAdvancements().getOrStartProgress(holder) : null;
				int percent = done ? 100 : progress == null ? 0 : progressPercent(progress);
				result.add(holder != null
						? entry(holder, done, percent)
						: new PayloadTypes.ScreenDataPayload.AdvEntry(
								idStr, Component.literal(idStr), Component.empty(), false, 0));
			}
		}
		return result;
	}

	private static PayloadTypes.ScreenDataPayload.AdvEntry entry(AdvancementHolder holder, boolean done, int percent) {
		Component title = entryTitle(holder);
		Component description = holder.value().display()
				.map(DisplayInfo::description)
				.orElse(Component.empty());
		return new PayloadTypes.ScreenDataPayload.AdvEntry(
				holder.id().toString(), title, description, done, percent);
	}

	/** Чистое имя достижения без скобок и стилей Advancement.name(). */
	private static Component entryTitle(AdvancementHolder holder) {
		return holder.value().display()
				.map(DisplayInfo::title)
				.orElseGet(() -> Advancement.name(holder));
	}

	private static int progressPercent(AdvancementProgress progress) {
		int completed = countOf(progress.getCompletedCriteria());
		int remaining = countOf(progress.getRemainingCriteria());
		int total = completed + remaining;
		return total == 0 ? 0 : (int) (100.0 * completed / total);
	}

	/** Размер Iterable<String> (критерии прогресса возвращаются как Iterable). */
	private static int countOf(Iterable<String> criteria) {
		int count = 0;
		for (String ignored : criteria) count++;
		return count;
	}
}
