package ml.mypals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ml.mypals.track.HighlightManager;
import ml.mypals.track.TrackMark;
import ml.mypals.track.Tracking;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
	@Inject(
			method = "tryMoveInItem",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Container;setItem(ILnet/minecraft/world/item/ItemStack;)V", shift = At.Shift.AFTER)
	)
	private static void itemflowtracker$movedWholeStack(Container source, Container target, ItemStack stack, int slot, Direction direction, CallbackInfoReturnable<ItemStack> cir) {
		if (Tracking.getRaw(stack) != null) {
			Tracking.moved(stack);
			HighlightManager.onEnterContainer(target);
		}
	}

	@Inject(
			method = "tryMoveInItem",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V")
	)
	private static void itemflowtracker$mergedIntoSlot(Container source, Container target, ItemStack stack, int slot, Direction direction, CallbackInfoReturnable<ItemStack> cir) {
		TrackMark mark = Tracking.getRaw(stack);

		if (mark == null) {
			return;
		}

		ItemStack destination = target.getItem(slot);
		int moved = Math.min(stack.getCount(), stack.getMaxStackSize() - destination.getCount());
		Tracking.arrive(destination, mark, moved);
		HighlightManager.onEnterContainer(target);
	}
}
