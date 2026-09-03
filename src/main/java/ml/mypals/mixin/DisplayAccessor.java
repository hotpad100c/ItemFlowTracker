package ml.mypals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.math.Transformation;

import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;

@Mixin(Display.class)
public interface DisplayAccessor {
	@Invoker("setTransformation")
	void itemflowtracker$setTransformation(Transformation transformation);

	@Invoker("setGlowColorOverride")
	void itemflowtracker$setGlowColorOverride(int color);

	@Invoker("setViewRange")
	void itemflowtracker$setViewRange(float viewRange);

	@Invoker("setBrightnessOverride")
	void itemflowtracker$setBrightnessOverride(Brightness brightness);
}
