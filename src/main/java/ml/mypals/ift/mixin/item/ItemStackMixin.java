package ml.mypals.ift.mixin.item;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ml.mypals.ift.track.TrackMark;
import ml.mypals.ift.track.TrackedStack;
import ml.mypals.ift.track.Tracking;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;


@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements TrackedStack {
	@Unique
	@Nullable
	private TrackMark itemflowtracker$mark;

	@Override
	@Nullable
	public TrackMark itemflowtracker$getMark() {
		return this.itemflowtracker$mark;
	}

	@Override
	public void itemflowtracker$setMark(@Nullable TrackMark mark) {
		this.itemflowtracker$mark = mark;
	}

	@Inject(method = "copy", at = @At("RETURN"))
	private void itemflowtracker$copy(CallbackInfoReturnable<ItemStack> cir) {
		Tracking.spread((ItemStack) (Object) this, cir.getReturnValue());
	}

	@Inject(method = "copyWithCount", at = @At("RETURN"))
	private void itemflowtracker$copyWithCount(int count, CallbackInfoReturnable<ItemStack> cir) {
		Tracking.spread((ItemStack) (Object) this, cir.getReturnValue());
	}

	@Inject(method = "copyAndClear", at = @At("RETURN"))
	private void itemflowtracker$copyAndClear(CallbackInfoReturnable<ItemStack> cir) {
		Tracking.spread((ItemStack) (Object) this, cir.getReturnValue());
	}


	@Inject(method = "split", at = @At("RETURN"))
	private void itemflowtracker$split(int count, CallbackInfoReturnable<ItemStack> cir) {
		Tracking.onSplit((ItemStack) (Object) this, cir.getReturnValue());
	}

	@Inject(method = "transmuteCopyIgnoreEmpty", at = @At("RETURN"))
	private void itemflowtracker$transmuteCopy(ItemLike item, int count, CallbackInfoReturnable<ItemStack> cir) {
		Tracking.spread((ItemStack) (Object) this, cir.getReturnValue());
	}
}
