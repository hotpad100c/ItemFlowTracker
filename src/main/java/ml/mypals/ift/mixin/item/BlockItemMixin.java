package ml.mypals.ift.mixin.item;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ml.mypals.ift.track.HighlightManager;
import ml.mypals.ift.track.Nesting;
import net.minecraft.world.item.ItemStack;
import ml.mypals.ift.track.TrackMark;
import ml.mypals.ift.track.Tracking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(
			method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
			at = @At("RETURN")
	)
	private void itemflowtracker$carryMarkIntoBlock(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (!cir.getReturnValue().consumesAction() || !(context.getLevel() instanceof ServerLevel level)) {
			return;
		}

		ItemStack placed = context.getItemInHand();
		BlockPos pos = context.getClickedPos();

		if (!(level.getBlockEntity(pos) instanceof Container)) {
			return;
		}

		TrackMark mark = Tracking.getRaw(placed);

		if (mark != null) {
			HighlightManager.markBlock(level, pos, mark);
		} else if (Nesting.carriesMark(placed)) {
			HighlightManager.watchBlock(level, pos);
		}
	}
}
