package ml.mypals.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ml.mypals.track.TrackMark;
import ml.mypals.track.TrackedStack;
import ml.mypals.track.Tracking;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

@Mixin(ItemStackTemplate.class)
public abstract class ItemStackTemplateMixin implements TrackedStack {
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

	@Inject(method = "fromStack", at = @At("RETURN"))
	private static void itemflowtracker$fromStack(ItemStack itemStack, CallbackInfoReturnable<ItemStackTemplate> cir) {
		Tracking.transfer((TrackedStack) (Object) itemStack, (TrackedStack) (Object) cir.getReturnValue());
	}

	@Inject(method = "validate", at = @At("RETURN"))
	private void itemflowtracker$validate(ItemStack candidate, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack created = cir.getReturnValue();

		if (created != null && !created.isEmpty()) {
			Tracking.transfer(this, (TrackedStack) (Object) created);
		}
	}

	@Inject(method = "withCount", at = @At("RETURN"))
	private void itemflowtracker$withCount(int count, CallbackInfoReturnable<ItemStackTemplate> cir) {
		Tracking.transfer(this, (TrackedStack) (Object) cir.getReturnValue());
	}
}
