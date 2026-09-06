package ml.mypals.ift.track;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;


public class Tracking {
	private static final List<TrackMark> ACTIVE = new ArrayList<>();
	private static final int MAX_ACTIVE_SESSIONS = 512; 		//TODO 包裹一个地毯规则来决定最大的追踪数量
	private static final int EXHAUSTED_GRACE_TICKS = 40;
	private static int generation;

	/** Smallest pathInterval among live sessions, 0 when nothing is drawing a trail. */
	private static int fastestPathInterval;

	public static void clearAll() {
		generation++;
		ACTIVE.clear();
	}

	public static void sweepExhaustedSessions() {
		Iterator<TrackMark> it = ACTIVE.iterator();
		int fastest = 0;

		while (it.hasNext()) {
			TrackMark mark = it.next();

			if (mark.generation() != generation || mark.tickExhausted() > EXHAUSTED_GRACE_TICKS) {
				mark.retire();
				it.remove();
				continue;
			}

			int path = mark.pathInterval();

			if (path > 0 && (fastest == 0 || path < fastest)) {
				fastest = path;
			}
		}

		// Piggybacks on the sweep, which already walks the list every tick.
		fastestPathInterval = fastest;
	}

	public static TrackMark newMark(DyeColor color, int capacity) {
		return newMark(color, capacity, 0);
	}

	public static TrackMark newMark(DyeColor color, int capacity, int pathInterval) {
		return register(new TrackMark(
				color.getTextureDiffuseColor(),
				color.getName(),
				generation,
				capacity,
				pathInterval));
	}

	public static TrackMark handOver(TrackMark parent, int capacity) {
		parent.spend(parent.capacity());
		return derive(parent, capacity);
	}

	public static TrackMark derive(TrackMark parent, int capacity) {
		return register(new TrackMark(parent.rgb(), parent.label(), generation, capacity, parent.pathInterval()));
	}

	public static TrackMark newMark(int rgb, int capacity, int pathInterval) {
		return register(new TrackMark(
				rgb,
				String.format("#%06X", rgb & 0xFFFFFF),
				generation,
				capacity,
				pathInterval));
	}

	private static TrackMark register(TrackMark mark) {

		//TODO 包裹一个地毯规则来决定是否开启功能，这里因该可以做总控制

		if (ACTIVE.size() >= MAX_ACTIVE_SESSIONS) {
			ACTIVE.removeFirst().retire();
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
		return stack == null || stack == ItemStack.EMPTY ? null : markOn((TrackedStack) (Object) stack);
	}

	public static boolean isLive(@Nullable TrackMark mark) {
		return mark != null && mark.generation() == generation && !mark.isRetired();
	}

	@Nullable
	public static TrackMark markOn(@Nullable TrackedStack holder) {
		if (holder == null) {
			return null;
		}

		TrackMark mark = holder.itemflowtracker$getMark();
		return isLive(mark) ? mark : null;
	}

	public static void transfer(@Nullable TrackedStack from, @Nullable TrackedStack to) {
		TrackMark mark = markOn(from);

		if (mark != null && to != null && markOn(to) == null) {
			to.itemflowtracker$setMark(mark);
		}
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

	/**
	 * Items that were split off for a transfer and then handed straight back because the destination
	 * would not take them. Nothing left the session, so the split has to be refunded - otherwise a
	 * hopper pointed at a container that cannot accept the item bleeds the budget dry, one item every
	 * eight ticks, and the whole session quietly retires.
	 */
	public static void returned(ItemStack stack) {
		moved(stack);
	}

	public static void moved(ItemStack stack) {
		TrackMark mark = getRaw(stack);

		if (mark != null) {
			mark.refund(stack.getCount());
		}
	}

	public static int fastestPathInterval() {
		return fastestPathInterval;
	}

	public static List<TrackMark> activeSessions() {
		return java.util.Collections.unmodifiableList(ACTIVE);
	}

	@Nullable
	public static DyeColor dyeOf(@Nullable ItemStack stack) {
		return stack == null || stack.isEmpty() ? null : stack.get(DataComponents.DYE);
	}
}
