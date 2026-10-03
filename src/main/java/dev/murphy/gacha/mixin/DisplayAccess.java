package dev.murphy.gacha.mixin;

import com.mojang.math.Transformation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla exposes no public setters for these display properties in 26.3. */
@Mixin(Display.class)
public interface DisplayAccess {
    @Invoker("setTransformation") void gacha$transform(Transformation transformation);
    @Invoker("setBrightnessOverride") void gacha$brightness(Brightness brightness);
}
