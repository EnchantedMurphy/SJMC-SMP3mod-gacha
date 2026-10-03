package dev.murphy.gacha.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Changing vanilla display metadata needs this private setter; no renderer is registered. */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccess {
    @Invoker("setBlockState") void gacha$block(BlockState state);
    @Invoker("getBlockState") BlockState gacha$state();
}
