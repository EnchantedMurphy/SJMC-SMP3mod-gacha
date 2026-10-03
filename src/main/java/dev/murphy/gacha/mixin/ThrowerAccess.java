package dev.murphy.gacha.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** getOwner resolves only loaded entities; retain the vanilla UUID for a disconnected thrower. */
@Mixin(ItemEntity.class)
public interface ThrowerAccess {
    @Accessor("thrower") EntityReference<Entity> gacha$thrower();
}
