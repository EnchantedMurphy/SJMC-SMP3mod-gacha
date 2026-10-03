package dev.murphy.gacha.mixin;

import dev.murphy.gacha.GachaMod;
import dev.murphy.gacha.UiActions;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Fabric receiver exists for vanilla custom clicks; inject after vanilla's thread handoff. */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class CustomClickMixin {
    @Inject(method = "handleCustomClickAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/Identifier;Ljava/util/Optional;)V"), cancellable = true)
    private void gacha$handleUiClick(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl listener && UiActions.isOurs(packet.id())) {
            GachaMod.handleUiClick(listener.player, packet);
            ci.cancel();
        }
    }
}
