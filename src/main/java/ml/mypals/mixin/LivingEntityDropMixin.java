package ml.mypals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ml.mypals.track.Tracking;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDropMixin {
	@Inject(
			method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
			at = @At("HEAD")
	)
	private void itemflowtracker$markThrownItem(ItemStack stack, boolean dropAround, boolean includeThrower, CallbackInfoReturnable<ItemEntity> cir) {
		if (!(((Object) this) instanceof Player self)) {return;}

		if (self.level().isClientSide() || stack.isEmpty() || !self.isAlive()) {
			return;
		}

		ItemStack offhand = self.getOffhandItem();

		if (stack == offhand) {
			return;
		}

		DyeColor dye = Tracking.dyeOf(offhand);

		if (dye != null && Tracking.get(stack) == null) {
			Tracking.set(stack, Tracking.newMark(dye, stack.getCount()));
		}
	}
}
