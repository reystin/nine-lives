package com.reystin.ninelives.gametest;

import com.reystin.ninelives.NineLives;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import com.reystin.ninelives.advancement.AdvancementCounter;
import com.reystin.ninelives.core.Attachments;
import com.reystin.ninelives.core.CommonEvents;
import com.reystin.ninelives.core.LivesManager;
import com.reystin.ninelives.core.ModeManager;
import com.reystin.ninelives.core.PlayerData;
import com.reystin.ninelives.core.ServerMessages;
import com.reystin.ninelives.core.WorldState;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.Objects;
import java.util.Set;

/**
 * Gametest-набор Nine Lives. Запуск: ./gradlew runGametestServer
 *
 * Покрывает: кламп здоровья (в т.ч. Прилив здоровья), жизнь за хард-достижение,
 * анти-двойной-подсчёт, периодические жизни, импорт старого прогресса,
 * смерть + copyOnDeath, тотем, жизни > 9, защиту после респавна,
 * снятие защиты атакой, выбытие в спектатор, передачу и её клампы.
 */
public class NineLivesGameTests {

	// ===== Хелперы =====

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		// Mock-игрок создаётся в CREATIVE и неуязвим — снимаем неуязвимость,
		// чтобы урон и смерть работали как у обычного игрока.
		player.getAbilities().invulnerable = false;
		return player;
	}

	private static void eq(GameTestHelper helper, Object expected, Object actual, String msg) {
		if (!Objects.equals(expected, actual)) {
			helper.fail(msg + ": expected " + expected + ", got " + actual);
		}
	}

	private static void isTrue(GameTestHelper helper, boolean value, String msg) {
		if (!value) helper.fail(msg);
	}

	private static void isFalse(GameTestHelper helper, boolean value, String msg) {
		if (value) helper.fail(msg);
	}

	private static AdvancementHolder holder(GameTestHelper helper, String id) {
		AdvancementHolder h = helper.getLevel().getServer().getAdvancements().get(Identifier.parse(id));
		if (h == null) helper.fail("advancement not found: " + id);
		return h;
	}

	private static void award(ServerPlayer player, AdvancementHolder holder) {
		String criterion = holder.value().criteria().keySet().iterator().next();
		player.getAdvancements().award(holder, criterion);
	}

	/**
	 * Mock-игрок неуязвим, пока соединение «не загрузилось»
	 * (clientLoadedTimeoutTimer = 60 тиков). Ждём и затем выполняем действие.
	 */
	private static net.minecraft.gametest.framework.GameTestSequence whenLoaded(GameTestHelper helper, ServerPlayer player) {
		return helper.startSequence().thenIdle(65);
	}

	private static net.minecraft.world.damagesource.DamageSource fatal(ServerPlayer player) {
		return player.level().damageSources().wither();
	}

	private static ServerPlayer respawn(ServerPlayer player) {
		return NineLives.serverOf(player).getPlayerList()
				.respawn(player, false, Entity.RemovalReason.KILLED);
	}

	// ===== 1. Максимум здоровья всегда 1.0 =====

	@GameTest
	public void healthIsAlwaysOneHeart(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> eq(helper, 1.0, p.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),
						"base max health"))
				.thenSucceed();
	}

	/** Эффект «Прилив здоровья» (HEALTH_BOOST) и его модификатор снимаются клампом. */
	@GameTest
	public void healthBoostEffectIsClamped(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		p.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 400, 1));
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> p.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 400, 1)))
				.thenIdle(3)
				.thenExecute(() -> {
					isTrue(helper, p.getAttribute(Attributes.MAX_HEALTH).getModifiers().isEmpty(),
							"MAX_HEALTH modifiers must be removed");
					eq(helper, 1.0, p.getAttributeValue(Attributes.MAX_HEALTH), "effective max health");
					isTrue(helper, p.getHealth() <= 1.0F, "health must be <= 1.0");
				})
				.thenSucceed();
	}

	// ===== 2. Жизни за достижения =====

	@GameTest
	public void hardAdvancementGrantsLife(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		int before = LivesManager.lives(p);
		award(p, holder(helper, "minecraft:end/kill_dragon"));
		eq(helper, before + 1, LivesManager.lives(p), "hard advancement must grant a life");
		helper.succeed();
	}

	/** Повторная выдача того же достижения (revoke + award) не даёт вторую жизнь. */
	@GameTest
	public void sameAdvancementNeverCountsTwice(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		AdvancementHolder h = holder(helper, "minecraft:end/kill_dragon");
		award(p, h);
		int afterFirst = LivesManager.lives(p);

		for (String criterion : h.value().criteria().keySet()) {
			p.getAdvancements().revoke(h, criterion);
		}
		award(p, h);
		eq(helper, afterFirst, LivesManager.lives(p), "counted advancement must not grant twice");
		helper.succeed();
	}

	/** Периодическая жизнь: каждые N достижений (в тесте N=2). */
	@GameTest
	public void periodicLifeFromAdvancements(GameTestHelper helper) {
		int saved = NineLives.config().advancementsPerLifeVanilla;
		NineLives.config().advancementsPerLifeVanilla = 2;
		try {
			ServerPlayer p = player(helper);
			int before = LivesManager.lives(p);
			award(p, holder(helper, "minecraft:husbandry/tame_an_animal"));
			eq(helper, before, LivesManager.lives(p), "no life before threshold");
			award(p, holder(helper, "minecraft:adventure/sleep_in_bed"));
			eq(helper, before + 1, LivesManager.lives(p), "life granted at threshold");
		} finally {
			NineLives.config().advancementsPerLifeVanilla = saved;
		}
		helper.succeed();
	}

	/** Импорт достижений, полученных до установки мода. */
	@GameTest
	public void preExistingAdvancementsImported(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		AdvancementHolder h = holder(helper, "minecraft:end/kill_dragon");
		award(p, h);
		// Стираем counted — имитируем «достижение получено до установки мода».
		p.setAttached(Attachments.PLAYER_DATA, new PlayerData(9, Set.of(), 0, 0, false, 0L, false));
		int imported = AdvancementCounter.importExisting(p);
		eq(helper, 1, imported, "one advancement imported");
		eq(helper, 10, LivesManager.lives(p), "imported hard advancement grants a life");
		helper.succeed();
	}

	// ===== 3. Смерть и данные =====

	/** Смерть тратит жизнь; жизни и counted переживают возрождение (copyOnDeath). */
	@GameTest(maxTicks = 400)
	public void deathSpendsLifeAndDataSurvivesRespawn(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		LivesManager.setLives(p, 5, false);
		award(p, holder(helper, "minecraft:end/kill_dragon"));
		int livesAfterAward = LivesManager.lives(p);

		whenLoaded(helper, p)
				.thenExecute(() -> {
					// Позитивный контроль: урон по mock-игроку вообще работает.
					isTrue(helper, p.hurtServer(p.level(), p.level().damageSources().wither(), 0.5F),
							"baseline damage must apply");
					p.hurtServer(p.level(), fatal(p), 1000.0F);
					isTrue(helper, p.isDeadOrDying(), "player must die");
					eq(helper, livesAfterAward - 1, LivesManager.lives(p), "life spent on death");
				})
				.thenExecute(() -> {
					ServerPlayer r = respawn(p);
					eq(helper, livesAfterAward - 1, LivesManager.lives(r), "lives survive respawn (copyOnDeath)");
					isTrue(helper, LivesManager.data(r).counted().contains("minecraft:end/kill_dragon"),
							"counted set survives respawn");
				})
				.thenSucceed();
	}

	/** Тотем спасает: смерти нет, жизнь не тратится, тотем израсходован. */
	@GameTest(maxTicks = 400)
	public void totemPreventsLifeLoss(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		LivesManager.setLives(p, 3, false);
		p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		whenLoaded(helper, p)
				.thenExecute(() -> {
					p.hurtServer(p.level(), fatal(p), 1000.0F);
					isFalse(helper, p.isDeadOrDying(), "totem must save the player");
					eq(helper, 3, LivesManager.lives(p), "no life spent when totem saves");
					isTrue(helper, p.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND).isEmpty(),
							"totem consumed");
				})
				.thenSucceed();
	}

	/** Жизней может быть больше 9. */
	@GameTest
	public void livesAboveNine(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		LivesManager.setLives(p, 25, false);
		eq(helper, 25, LivesManager.lives(p), "25 lives");
		helper.succeed();
	}

	// ===== 4. Защита после возрождения =====

	@GameTest(maxTicks = 400)
	public void respawnProtectionBlocksDamage(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		LivesManager.setLives(p, 4, false);
		whenLoaded(helper, p)
				.thenExecute(() -> {
					p.hurtServer(p.level(), fatal(p), 1000.0F);
					isTrue(helper, p.isDeadOrDying(), "player must die");
				})
				.thenExecute(() -> {
					ServerPlayer r = respawn(p);
					isTrue(helper, LivesManager.isProtected(r), "protection after respawn");
					float before = r.getHealth();
					isFalse(helper, r.hurtServer(r.level(), r.level().damageSources().wither(), 1.0F),
							"damage must be blocked during protection");
					eq(helper, (double) before, (double) r.getHealth(), "no damage while protected");
				})
				.thenSucceed();
	}

	/** Своя атака снимает защиту (через AFTER_DAMAGE). */
	@GameTest
	public void attackingEndsProtection(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		LivesManager.onRespawn(p);
		isTrue(helper, LivesManager.isProtected(p), "protection granted");

		EntityType<? extends net.minecraft.world.entity.Mob> zombieType =
				(EntityType<? extends net.minecraft.world.entity.Mob>)
						net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
								.getValue(Identifier.withDefaultNamespace("zombie"));
		var zombie = helper.spawnWithNoFreeWill(zombieType, helper.absolutePos(new BlockPos(1, 1, 1)));
		zombie.hurtServer(helper.getLevel(), p.level().damageSources().playerAttack(p), 2.0F);
		isFalse(helper, LivesManager.isProtected(p), "attacking must end protection");
		helper.killAllEntities();
		helper.succeed();
	}

	// ===== 5. Выбытие и передача =====

	/** 0 жизней -> спектатор; передача жизни -> возврат в выживание. */
	@GameTest(maxTicks = 400)
	public void eliminationAndRevival(GameTestHelper helper) {
		ServerPlayer eliminated = player(helper);
		ServerPlayer giver = player(helper);
		LivesManager.setLives(eliminated, 1, false);
		LivesManager.setLives(giver, 5, false);

		whenLoaded(helper, eliminated)
				.thenExecute(() -> {
					eliminated.hurtServer(eliminated.level(), fatal(eliminated), 1000.0F);
					isTrue(helper, eliminated.isDeadOrDying(), "player must die");
				})
				.thenExecute(() -> {
					ServerPlayer r = respawn(eliminated);
					eq(helper, 0, LivesManager.lives(r), "zero lives");
					isTrue(helper, r.isSpectator(), "eliminated player must be spectator");

					int transferred = LivesManager.transfer(giver, r, 1);
					eq(helper, 1, transferred, "one life transferred");
					eq(helper, 1, LivesManager.lives(r), "receiver has a life");
					isFalse(helper, r.isSpectator(), "revived player must be in survival");
					isFalse(helper, LivesManager.data(r).eliminated(), "eliminated flag cleared");
				})
				.thenSucceed();
	}

	/** У отдающего остаётся минимум 1 жизнь; избыток клампится. */
	@GameTest
	public void transferKeepsOneLife(GameTestHelper helper) {
		ServerPlayer giver = player(helper);
		ServerPlayer receiver = player(helper);
		LivesManager.setLives(giver, 3, false);
		LivesManager.setLives(receiver, 0, false);

		int transferred = LivesManager.transfer(giver, receiver, 10);
		eq(helper, 2, transferred, "clamped to lives-1");
		eq(helper, 1, LivesManager.lives(giver), "giver keeps 1");
		eq(helper, 2, LivesManager.lives(receiver), "receiver got 2");
		helper.succeed();
	}

	/** При 1 жизни передать нельзя. */
	@GameTest
	public void transferWithSingleLifeFails(GameTestHelper helper) {
		ServerPlayer giver = player(helper);
		ServerPlayer receiver = player(helper);
		LivesManager.setLives(giver, 1, false);
		LivesManager.setLives(receiver, 0, false);
		int transferred = LivesManager.transfer(giver, receiver, 1);
		eq(helper, -1, transferred, "transfer rejected");
		eq(helper, 1, LivesManager.lives(giver), "giver keeps life");
		eq(helper, 0, LivesManager.lives(receiver), "receiver unchanged");
		helper.succeed();
	}

	/** Передача самому себе запрещена и не меняет число жизней. */
	@GameTest
	public void transferToSelfRejected(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		LivesManager.setLives(p, 5, false);
		int transferred = LivesManager.transfer(p, p, 1);
		eq(helper, -1, transferred, "self transfer rejected");
		eq(helper, 5, LivesManager.lives(p), "lives unchanged after self transfer");
		helper.succeed();
	}

	/** Sync отдаёт прогресс текущего цикла, а не суммарный счётчик за всё время. */
	@GameTest
	public void syncProgressIsCycleRelative(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		p.setAttached(Attachments.PLAYER_DATA,
				new PlayerData(5, Set.of(), 1122, 1122, false, 0L, false));
		int perLife = Math.max(1, ModeManager.effectiveMode(helper.getLevel().getServer())
				.advancementsPerLife(NineLives.config()));
		var sync = ServerMessages.buildSync(p);
		eq(helper, perLife, sync.perLife(), "per-life threshold sent to client");
		eq(helper, ModeManager.currentPeriodic(p) % perLife, sync.progress(),
				"progress is relative to the current cycle");
		isTrue(helper, sync.progress() >= 0 && sync.progress() < perLife,
				"progress must stay below the threshold");
		helper.succeed();
	}

	/** Выбывший при перезаходе снова принудительно в спектаторе. */
	@GameTest
	public void eliminatedStaysSpectatorAfterRejoin(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		LivesManager.setLives(p, 0, false);
		LivesManager.onJoin(p);
		isTrue(helper, p.isSpectator() || LivesManager.data(p).eliminated(), "eliminated enforced on join");
		helper.succeed();
	}

	// ===== 6. BACAP =====

	/** Дерево ванили не считается супер-челленджем; ветвление детекта работает. */
	@GameTest
	public void superChallengeDetectionBranching(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		AdvancementHolder vanillaRoot = holder(helper, "minecraft:end/root");
		isFalse(helper, AdvancementCounter.isSuperChallenge(server, vanillaRoot),
				"vanilla root is not a super challenge");
		AdvancementHolder vanillaLeaf = holder(helper, "minecraft:end/kill_dragon");
		isFalse(helper, AdvancementCounter.isSuperChallenge(server, vanillaLeaf),
				"vanilla advancement is not a super challenge");
		helper.succeed();
	}

	// ===== 7. Исправления из аудита =====

	/** MODDED_CLIENT не сохраняется на диск, но обязан пережить респавн. */
	@GameTest(maxTicks = 400)
	public void moddedClientFlagSurvivesRespawn(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		LivesManager.setLives(p, 3, false);
		p.setAttached(Attachments.MODDED_CLIENT, Boolean.TRUE);
		whenLoaded(helper, p)
				.thenExecute(() -> {
					p.hurtServer(p.level(), fatal(p), 1000.0F);
					isTrue(helper, p.isDeadOrDying(), "player must die");
				})
				.thenExecute(() -> {
					ServerPlayer r = respawn(p);
					eq(helper, Boolean.TRUE, r.getAttached(Attachments.MODDED_CLIENT),
							"MODDED_CLIENT must survive respawn");
				})
				.thenSucceed();
	}

	/** Поглощение (золотое яблоко) не снимается клампом, в отличие от Прилива здоровья. */
	@GameTest
	public void absorptionSurvivesHealthEnforcer(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 3));
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					isTrue(helper, p.hasEffect(MobEffects.ABSORPTION), "absorption effect must stay");
					isTrue(helper, p.getAbsorptionAmount() > 0.0F, "absorption hearts must stay");
					isTrue(helper, p.getAttribute(Attributes.MAX_HEALTH).getModifiers().isEmpty(),
							"no MAX_HEALTH modifiers");
					eq(helper, 1.0, p.getAttributeValue(Attributes.MAX_HEALTH), "effective max health");
					isTrue(helper, p.getHealth() <= 1.0F, "health must be <= 1.0");
				})
				.thenSucceed();
	}

	/** Хард-достижение, закрывающее порог, даёт сразу две жизни. */
	@GameTest
	public void hardAdvancementAtThresholdGrantsBothLives(GameTestHelper helper) {
		int saved = NineLives.config().advancementsPerLifeVanilla;
		NineLives.config().advancementsPerLifeVanilla = 1;
		try {
			ServerPlayer p = player(helper);
			int before = LivesManager.lives(p);
			award(p, holder(helper, "minecraft:end/kill_dragon"));
			eq(helper, before + 2, LivesManager.lives(p), "hard + periodic life at threshold");
		} finally {
			NineLives.config().advancementsPerLifeVanilla = saved;
		}
		helper.succeed();
	}

	/** Все 13 курируемых хард-ID существуют в дереве достижений 26.3. */
	@GameTest
	public void allHardAdvancementIdsExist(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		eq(helper, 13, NineLives.config().hardAdvancements.size(), "curated hard list size");
		for (String id : NineLives.config().hardAdvancements) {
			isTrue(helper, server.getAdvancements().get(Identifier.parse(id)) != null,
					"hard advancement missing in 26.3: " + id);
		}
		helper.succeed();
	}

	/** Хост без OP вправе выбирать режим и подтверждать установку. */
	@GameTest
	public void hostWithoutOpIsPrompter(GameTestHelper helper) {
		isTrue(helper, ServerMessages.isPrompter(true, false), "host without OP");
		isTrue(helper, ServerMessages.isPrompter(false, true), "operator");
		isTrue(helper, ServerMessages.isPrompter(true, true), "host and operator");
		isFalse(helper, ServerMessages.isPrompter(false, false), "regular player");
		helper.succeed();
	}

	/** Открытие экрана жизней снимает пульс новой жизни. */
	@GameTest
	public void openScreenClearsNewLifePulse(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		p.setAttached(Attachments.PLAYER_DATA, new PlayerData(3, Set.of(), 0, 0, false, 0L, true));
		isTrue(helper, LivesManager.data(p).newLifePulse(), "pulse is set");
		CommonEvents.handleOpenScreen(p);
		isFalse(helper, LivesManager.data(p).newLifePulse(), "pulse cleared by open screen");
		helper.succeed();
	}

	/** Режим переживает codec round-trip SavedData. */
	@GameTest
	public void worldStatePersistsMode(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		WorldState live = ModeManager.WorldStateHolder.get(server);
		String oldMode = live.mode;
		try {
			live.setMode("bc");
			isTrue(helper, live.isDirty(), "setter must mark state dirty");
			var encoded = WorldState.TYPE.codec().encodeStart(JsonOps.INSTANCE, live).getOrThrow();
			WorldState decoded = WorldState.TYPE.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
			eq(helper, "bc", decoded.mode, "mode survives codec round-trip");
		} finally {
			live.setMode(oldMode);
		}
		helper.succeed();
	}
}
