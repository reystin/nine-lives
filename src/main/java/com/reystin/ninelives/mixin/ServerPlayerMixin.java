package com.reystin.ninelives.mixin;

import com.reystin.ninelives.core.ServerMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Имя игрока в Tab-списке: «♥12 · ★45 Ник» с цветами по числу жизней.
 * Ванильный протокол (UPDATE_DISPLAY_NAME) доставляет его и обычным клиентам,
 * поэтому работает в обеих версиях мода.
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
	@Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
	private void ninelives$tabName(CallbackInfoReturnable<Component> cir) {
		cir.setReturnValue(ServerMessages.tabName((ServerPlayer) (Object) this));
	}
}
