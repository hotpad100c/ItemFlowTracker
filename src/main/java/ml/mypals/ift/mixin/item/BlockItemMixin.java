package ml.mypals.ift.mixin.item;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ml.mypals.ift.track.HighlightManager;
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

		TrackMark mark = Tracking.getRaw(context.getItemInHand());

		if (mark == null) {
			return;
		}

		BlockPos pos = context.getClickedPos();

		if (level.getBlockEntity(pos) instanceof Container) {
			HighlightManager.markBlock(level, pos, mark);
		}
	}
}
