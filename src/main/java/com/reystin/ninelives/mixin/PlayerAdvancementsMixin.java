package com.reystin.ninelives.mixin;

import com.reystin.ninelives.advancement.AdvancementCounter;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Хук завершения достижения. Vanilla Fabric API не даёт события «достижение
 * завершено», поэтому инжектимся в PlayerAdvancements#award:
 * result == true означает, что критерий выдан; если при этом прогресс стал
 * done — достижение завершено только что (см. AdvancementCounter.onAward).
 */
@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	@Inject(method = "award", at = @At("RETURN"))
	private void ninelives$onAward(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() || player == null) return;
		AdvancementProgress progress = ((PlayerAdvancements) (Object) this).getOrStartProgress(holder);
		if (progress.isDone()) {
			AdvancementCounter.onAward(player, holder);
		}
	}
}
