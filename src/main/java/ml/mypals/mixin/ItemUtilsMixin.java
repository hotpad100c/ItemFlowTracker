package ml.mypals.mixin;

import java.util.List;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import ml.mypals.track.TrackMark;
import ml.mypals.track.Tracking;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;


@Mixin(ItemUtils.class)
public abstract class ItemUtilsMixin {
	@ModifyVariable(method = "onContainerDestroyed", at = @At("HEAD"), argsOnly = true, name = "contents")
	private static Stream<ItemStack> itemflowtracker$markSpilledContents(Stream<ItemStack> contents, ItemEntity source) {
		TrackMark container = Tracking.get(source.getItem());

		if (container == null) {
			return contents;
		}
		List<ItemStack> spilled = contents.toList();
		int total = spilled.stream().mapToInt(ItemStack::getCount).sum();

		if (total <= 0) {
			return spilled.stream();
		}

		TrackMark mark = Tracking.handOver(container, total);
		spilled.forEach(stack -> Tracking.setIfAbsent(stack, mark));
		return spilled.stream();
	}
}
