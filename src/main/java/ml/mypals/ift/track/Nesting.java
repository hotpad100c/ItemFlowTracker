package ml.mypals.ift.track;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;


public final class Nesting {
	private static final int MAX_DEPTH = 4;

	private Nesting() {
	}

	@Nullable
	public static TrackMark inStack(@Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}

		TrackMark mark = Tracking.get(stack);

		if (mark != null) {
			return mark;
		}

		return inContents(
				stack.get(DataComponents.CONTAINER),
				stack.get(DataComponents.BUNDLE_CONTENTS),
				0);
	}

	public static boolean carriesMark(@Nullable ItemStack stack) {
		return inStack(stack) != null;
	}

	@Nullable
	public static TrackMark inContainer(Container container) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			TrackMark mark = inStack(container.getItem(slot));

			if (mark != null) {
				return mark;
			}
		}

		return null;
	}

	@Nullable
	public static TrackMark inEntity(Entity entity) {
		if (entity instanceof ItemEntity item) {
			return inStack(item.getItem());
		}

		return entity instanceof Container container ? inContainer(container) : null;
	}


	@Nullable
	private static TrackMark inTemplate(ItemStackTemplate template, int depth) {
		TrackMark mark = Tracking.markOn((TrackedStack) (Object) template);

		if (mark != null || depth >= MAX_DEPTH) {
			return mark;
		}

		return inContents(
				template.get(DataComponents.CONTAINER),
				template.get(DataComponents.BUNDLE_CONTENTS),
				depth);
	}

	@Nullable
	private static TrackMark inContents(
			@Nullable ItemContainerContents container,
			@Nullable BundleContents bundle,
			int depth) {
		if (container != null) {
			for (ItemStackTemplate nested : container.nonEmptyItems()) {
				TrackMark mark = inTemplate(nested, depth + 1);

				if (mark != null) {
					return mark;
				}
			}
		}

		if (bundle != null) {
			for (ItemStackTemplate nested : bundle.items()) {
				TrackMark mark = inTemplate(nested, depth + 1);

				if (mark != null) {
					return mark;
				}
			}
		}

		return null;
	}
}
