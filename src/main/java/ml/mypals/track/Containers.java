package ml.mypals.track;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import ml.mypals.mixin.CompoundContainerAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

public class Containers {
	private static final int MAX_NESTING = 4;

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
			TrackMark mark = findMarkInStack(container.getItem(slot));

			if (mark != null) {return mark;}
		}
		return null;
	}

	@Nullable
	public static TrackMark findMarkInStack(@Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}

		TrackMark mark = Tracking.get(stack);

		if (mark != null) {
			return mark;
		}

		return findMarkInContents(
				stack.get(DataComponents.CONTAINER),
				stack.get(DataComponents.BUNDLE_CONTENTS),
				0);
	}

	@Nullable
	private static TrackMark findMarkInTemplate(ItemStackTemplate template, int depth) {
		TrackMark mark = Tracking.markOn((TrackedStack) (Object) template);

		if (mark != null || depth >= MAX_NESTING) {
			return mark;
		}

		return findMarkInContents(
				template.get(DataComponents.CONTAINER),
				template.get(DataComponents.BUNDLE_CONTENTS),
				depth);
	}

	@Nullable
	private static TrackMark findMarkInContents(
			@Nullable ItemContainerContents container,
			@Nullable BundleContents bundle,
			int depth) {
		if (container != null) {
			for (ItemStackTemplate nested : container.nonEmptyItems()) {
				TrackMark mark = findMarkInTemplate(nested, depth + 1);

				if (mark != null) {
					return mark;
				}
			}
		}

		if (bundle != null) {
			for (ItemStackTemplate nested : bundle.items()) {
				TrackMark mark = findMarkInTemplate(nested, depth + 1);

				if (mark != null) {
					return mark;
				}
			}
		}

		return null;
	}
}
