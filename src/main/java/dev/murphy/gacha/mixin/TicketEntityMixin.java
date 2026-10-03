package dev.murphy.gacha.mixin;

import dev.murphy.gacha.Tickets;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevent differently owned drops merging and losing their throwing player. Fabric has no merge veto. */
@Mixin(ItemEntity.class)
public abstract class TicketEntityMixin {
    @Inject(method = "isMergable", at = @At("HEAD"), cancellable = true)
    private void gacha$keepThrower(CallbackInfoReturnable<Boolean> callback) {
        if (Tickets.draws(((ItemEntity) (Object) this).getItem()) != 0) callback.setReturnValue(false);
    }
}
