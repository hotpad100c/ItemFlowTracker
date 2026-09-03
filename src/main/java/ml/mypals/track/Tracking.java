package ml.mypals.track;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;


public final class Tracking {
	private static final List<TrackMark> ACTIVE = new ArrayList<>();
	private static final int MAX_ACTIVE_SESSIONS = 512;
	private static final int EXHAUSTED_GRACE_TICKS = 40;
	private static int generation;

	private Tracking() {
	}

	public static void clearAll() {
		generation++;
		ACTIVE.clear();
	}

	public static void sweepExhaustedSessions() {
		Iterator<TrackMark> it = ACTIVE.iterator();

		while (it.hasNext()) {
			TrackMark mark = it.next();

			if (mark.generation() != generation || mark.tickExhausted() > EXHAUSTED_GRACE_TICKS) {
				mark.retire();
				it.remove();
			}
		}
	}

	public static TrackMark newMark(DyeColor color, int capacity) {
		return register(new TrackMark(
				color.getTextureDiffuseColor(),
				color.getName(),
				generation,
				capacity));
	}

	public static TrackMark newMark(int rgb, int capacity) {
		return register(new TrackMark(
				rgb,
				String.format("#%06X", rgb & 0xFFFFFF),
				generation,
				capacity));
	}

	private static TrackMark register(TrackMark mark) {
		if (ACTIVE.size() >= MAX_ACTIVE_SESSIONS) {
			ACTIVE.remove(0).retire();
		}

		ACTIVE.add(mark);
		return mark;
	}

	@Nullable
	public static TrackMark get(@Nullable ItemStack stack) {
		return stack == null || stack.isEmpty() ? null : getRaw(stack);
	}

	@Nullable
	public static TrackMark getRaw(@Nullable ItemStack stack) {
		if (stack == null || stack == ItemStack.EMPTY) {
			return null;
		}

		TrackMark mark = ((TrackedStack) (Object) stack).itemflowtracker$getMark();
		return mark != null && mark.generation() == generation && !mark.isRetired() ? mark : null;
	}

	public static boolean isMarked(@Nullable ItemStack stack) {
		return get(stack) != null;
	}

	public static void set(ItemStack stack, @Nullable TrackMark mark) {
		if (stack != ItemStack.EMPTY) {
			((TrackedStack) (Object) stack).itemflowtracker$setMark(mark);
		}
	}

	public static void setIfAbsent(ItemStack stack, TrackMark mark) {
		if (getRaw(stack) == null) {
			set(stack, mark);
		}
	}

	public static void spread(@Nullable ItemStack from, @Nullable ItemStack to) {
		TrackMark mark = getRaw(from);

		if (mark != null && to != null) {
			setIfAbsent(to, mark);
		}
	}

	public static void onSplit(ItemStack source, ItemStack taken) {
		TrackMark mark = getRaw(source);

		if (mark == null) {
			return;
		}

		setIfAbsent(taken, mark);
		mark.spend(taken.getCount());
	}

	public static void arrive(ItemStack destination, TrackMark incoming, int amount) {
		TrackMark current = getRaw(destination);

		if (current == incoming) {
			incoming.refund(amount);
		} else if (current == null) {
			set(destination, incoming);
		}
	}

	public static void moved(ItemStack stack) {
		TrackMark mark = getRaw(stack);

		if (mark != null) {
			mark.refund(stack.getCount());
		}
	}

	public static List<TrackMark> activeSessions() {
		return java.util.Collections.unmodifiableList(ACTIVE);
	}

	@Nullable
	public static DyeColor dyeOf(@Nullable ItemStack stack) {
		return stack == null || stack.isEmpty() ? null : stack.get(DataComponents.DYE);
	}
}
