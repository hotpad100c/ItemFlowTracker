package ml.mypals.track;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import ml.mypals.mixin.CompoundContainerAccessor;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class Containers {
	private Containers() {
	}


	public static void forEachLeaf(@Nullable Container container, Consumer<Container> sink) {
		if (container == null) {
			return;
		}

		if (container instanceof CompoundContainer compound) {
			CompoundContainerAccessor accessor = (CompoundContainerAccessor) compound;
			forEachLeaf(accessor.itemflowtracker$container1(), sink);
			forEachLeaf(accessor.itemflowtracker$container2(), sink);
			return;
		}

		sink.accept(container);
	}

	@Nullable
	public static TrackMark findMark(Container container) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			TrackMark mark = Tracking.get(stack);

			if (mark != null) {
				return mark;
			}
		}

		return null;
	}
}
