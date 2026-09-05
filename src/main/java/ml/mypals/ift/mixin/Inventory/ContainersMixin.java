package ml.mypals.ift.mixin.Inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ml.mypals.ift.track.HighlightManager;
import ml.mypals.ift.track.TrackMark;
import ml.mypals.ift.track.Tracking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;


@Mixin(Containers.class)
public abstract class ContainersMixin {
	@Inject(
			method = "dropContents(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/Container;)V",
			at = @At("HEAD")
	)
	private static void itemflowtracker$markSpilledContents(Level level, BlockPos pos, Container container, CallbackInfo ci) {
		TrackMark blockMark = HighlightManager.peekBlockMark(level, pos);

		if (blockMark == null) {
			return;
		}

		int total = 0;

		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			total += container.getItem(slot).getCount();
		}

		if (total > 0) {
			TrackMark spilled = Tracking.derive(blockMark, total);

			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);

				if (!stack.isEmpty()) {
					Tracking.setIfAbsent(stack, spilled);
				}
			}
		}

		HighlightManager.clearBlockMark(level, pos);
	}
}
