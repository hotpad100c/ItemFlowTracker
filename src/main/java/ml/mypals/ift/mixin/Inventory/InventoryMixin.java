package ml.mypals.ift.mixin.Inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ml.mypals.ift.track.TrackMark;
import ml.mypals.ift.track.Tracking;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

@Mixin(Inventory.class)
public abstract class InventoryMixin {
	@Inject(
			method = "addResource(ILnet/minecraft/world/item/ItemStack;)I",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;grow(I)V")
	)
	private void itemflowtracker$carryMarkIntoSlot(int slot, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		TrackMark mark = Tracking.getRaw(stack);

		if (mark == null) {
			return;
		}

		Inventory self = (Inventory) (Object) this;
		ItemStack destination = self.getItem(slot);
		int moved = Math.min(stack.getCount(), self.getMaxStackSize(destination) - destination.getCount());
		Tracking.arrive(destination, mark, moved);
	}
}
