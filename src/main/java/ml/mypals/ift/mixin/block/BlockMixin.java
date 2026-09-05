package ml.mypals.ift.mixin.block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ml.mypals.ift.track.HighlightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;


@Mixin(Block.class)
public abstract class BlockMixin {
	@Inject(
			method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD")
	)
	private static void itemflowtracker$carryMarkOutOfBlock(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
		HighlightManager.takeBlockMark(level, pos, stack);
	}
}
