package ml.mypals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccessor {
	@Invoker("setItemStack")
	void itemflowtracker$setItemStack(ItemStack stack);

	@Invoker("setItemTransform")
	void itemflowtracker$setItemTransform(ItemDisplayContext context);
}
