package ml.mypals.ift.mixin.item;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ml.mypals.ift.track.Containers;
import ml.mypals.ift.track.HighlightManager;
import ml.mypals.ift.track.TrackMark;
import ml.mypals.ift.track.Tracking;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	@Unique
	@Nullable
	private TrackMark itemflowtracker$applied;

	@Unique
	private boolean itemflowtracker$hasApplied;

	@Inject(
			method = {
					"<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V",
					"<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;DDD)V"
			},
			at = @At("TAIL")
	)
	private void itemflowtracker$refundOnDrop(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;

		if (!self.level().isClientSide()) {
			Tracking.moved(self.getItem());
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void itemflowtracker$syncGlow(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;

		if (self.level().isClientSide()) {
			return;
		}

		TrackMark mark = Containers.findMarkInStack(self.getItem());

		if (this.itemflowtracker$hasApplied && Objects.equals(this.itemflowtracker$applied, mark)) {
			return;
		}

		if (mark != null) {
			HighlightManager.watchEntity(self);
		}

		this.itemflowtracker$applied = mark;
		this.itemflowtracker$hasApplied = true;
	}

	@Inject(method = "merge(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
	private static void itemflowtracker$merge(ItemStack destination, ItemStack source, int limit, CallbackInfoReturnable<ItemStack> cir) {
		TrackMark mark = Tracking.getRaw(source);

		if (mark != null) {
			ItemStack merged = cir.getReturnValue();
			Tracking.arrive(merged, mark, merged.getCount() - destination.getCount());
		}
	}
}
