package ml.mypals.ift.track;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import ml.mypals.ift.mixin.accessors.BlockDisplayAccessor;
import ml.mypals.ift.mixin.accessors.DisplayAccessor;
import ml.mypals.ift.mixin.accessors.ItemDisplayAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HighlightManager {
	public static final String DISPLAY_TAG = "itemflowtracker.display";

	private static final int VERIFY_INTERVAL_TICKS = 5;
	private static final float OUTLINE_SCALE = 1.02F;

	private static final Block ENTITY_OUTLINE_BLOCK = Blocks.STAINED_GLASS.white();
	private static final double ENTITY_OUTLINE_PADDING = 0.02;

	private static final Map<ResourceKey<Level>, Map<BlockPos, Highlight>> BLOCKS = new HashMap<>();
	private static final Map<ResourceKey<Level>, Map<Integer, Highlight>> ENTITIES = new HashMap<>();

	private static final Set<UUID> OWNED = new HashSet<>();

	private static final Block PATH_MARKER_BLOCK = Blocks.STAINED_GLASS.black();

	private static final float PATH_LINE_THICKNESS = 0.06F;

	private static final double PATH_MAX_SEGMENT = 8.0;

	/**
	 * Below this, the item has not really moved and no segment is drawn - but the last sample is kept
	 * so slow drift still accumulates into one segment instead of being thrown away every tick.
	 */
	private static final double PATH_MIN_SEGMENT = 0.05;

	private static final float PATH_CULLING_SIZE = 64.0F;

	private static final Map<TrackMark, Trail> TRAILS = new HashMap<>();

	private static final class Trail {
		final List<Display> markers = new ArrayList<>();
	}

	private static final class Highlight {
		@Nullable
		TrackMark mark;
		@Nullable
		Display display;
		@Nullable
		TrackMark blockMark;
		@Nullable
		Block blockMarkOwner;

		@Nullable
		Vec3 trailLast;
	}

	public static void onEnterContainer(@Nullable Container container) {
		Containers.forEachLeaf(container, leaf -> {
			if (Nesting.inContainer(leaf) == null) {
				return;
			}

			if (leaf instanceof BlockEntity blockEntity) {
				if (blockEntity.getLevel() instanceof ServerLevel level) {
					watchBlock(level, blockEntity.getBlockPos());
				}
			} else if (leaf instanceof Entity entity && !entity.level().isClientSide()) {
				watchEntity(entity);
			}
		});
	}

	public static void watchBlock(ServerLevel level, BlockPos pos) {
		BLOCKS.computeIfAbsent(level.dimension(), key -> new HashMap<>())
				.computeIfAbsent(pos.immutable(), key -> new Highlight());
	}

	public static void markBlock(ServerLevel level, BlockPos pos, TrackMark mark) {
		Highlight highlight = BLOCKS.computeIfAbsent(level.dimension(), key -> new HashMap<>())
				.computeIfAbsent(pos.immutable(), key -> new Highlight());
		highlight.blockMark = mark;
		highlight.blockMarkOwner = level.getBlockState(pos).getBlock();
	}

	public static void takeBlockMark(Level level, BlockPos pos, ItemStack stack) {
		if (!(level instanceof ServerLevel serverLevel) || stack.isEmpty()) {
			return;
		}

		Map<BlockPos, Highlight> tracked = BLOCKS.get(serverLevel.dimension());
		Highlight highlight = tracked == null ? null : tracked.get(pos);

		if (highlight == null || !Tracking.isLive(highlight.blockMark) || highlight.blockMarkOwner == null) {
			return;
		}

		if (stack.getItem() == highlight.blockMarkOwner.asItem()) {
			Tracking.setIfAbsent(stack, highlight.blockMark);
		}
	}

	@Nullable
	public static TrackMark peekBlockMark(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return null;
		}

		Map<BlockPos, Highlight> tracked = BLOCKS.get(serverLevel.dimension());
		Highlight highlight = tracked == null ? null : tracked.get(pos);
		return highlight != null && Tracking.isLive(highlight.blockMark) ? highlight.blockMark : null;
	}

	public static void clearBlockMark(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		Map<BlockPos, Highlight> tracked = BLOCKS.get(serverLevel.dimension());
		Highlight highlight = tracked == null ? null : tracked.get(pos);

		if (highlight != null) {
			highlight.blockMark = null;
			highlight.blockMarkOwner = null;
		}
	}

	public static void watchEntity(Entity entity) {
		ENTITIES.computeIfAbsent(entity.level().dimension(), key -> new HashMap<>())
				.computeIfAbsent(entity.getId(), key -> new Highlight());
	}

	public static void tick(ServerLevel level) {
		tickPath(level);

		if (level.getGameTime() % verifyInterval() != 0) {
			return;
		}

		tickBlocks(level);
		tickEntities(level);
	}


	private static int verifyInterval() {
		int fastest = Tracking.fastestPathInterval();
		return fastest > 0 ? Math.min(VERIFY_INTERVAL_TICKS, fastest) : VERIFY_INTERVAL_TICKS;
	}

	private static void tickPath(ServerLevel level) {
		long time = level.getGameTime();
		Map<Integer, Highlight> entities = ENTITIES.get(level.dimension());

		if (entities != null) {
			for (Map.Entry<Integer, Highlight> entry : entities.entrySet()) {
				Highlight highlight = entry.getValue();

				if (wantsStamp(highlight.mark, time)) {
					Entity entity = level.getEntity(entry.getKey());

					if (entity != null && !entity.isRemoved()) {
						stamp(level, highlight, entity.position());
					}
				}
			}
		}

		Map<BlockPos, Highlight> blocks = BLOCKS.get(level.dimension());

		if (blocks != null) {
			for (Map.Entry<BlockPos, Highlight> entry : blocks.entrySet()) {
				Highlight highlight = entry.getValue();

				if (wantsStamp(highlight.mark, time) && level.isLoaded(entry.getKey())) {
					stamp(level, highlight, Vec3.atCenterOf(entry.getKey()));
				}
			}
		}
	}

	private static boolean wantsStamp(@Nullable TrackMark mark, long time) {
		return mark != null
				&& mark.pathInterval() > 0
				&& Tracking.isLive(mark)
				&& time % mark.pathInterval() == 0;
	}


	private static void stamp(ServerLevel level, Highlight highlight, Vec3 at) {
		TrackMark mark = highlight.mark;

		if (mark == null) {
			return;
		}

		Vec3 previous = highlight.trailLast != null ? highlight.trailLast : inheritCursor(level, mark, at);

		if (previous != null) {
			double distance = previous.distanceTo(at);

			if (distance < PATH_MIN_SEGMENT) {
				return;
			}

			if (distance <= PATH_MAX_SEGMENT) {
				spawnSegment(level, mark, previous, at, TRAILS.computeIfAbsent(mark, ignored -> new Trail()));
			}
		}

		highlight.trailLast = at;
	}


	@Nullable
	private static Vec3 inheritCursor(ServerLevel level, TrackMark mark, Vec3 at) {
		Vec3 nearest = null;
		double best = PATH_MAX_SEGMENT;

		for (Highlight other : allHighlights(level)) {
			if (other.mark != mark || other.trailLast == null) {
				continue;
			}

			double distance = other.trailLast.distanceTo(at);

			if (distance <= best) {
				best = distance;
				nearest = other.trailLast;
			}
		}

		return nearest;
	}

	private static List<Highlight> allHighlights(ServerLevel level) {
		List<Highlight> all = new ArrayList<>();
		Map<Integer, Highlight> entities = ENTITIES.get(level.dimension());
		Map<BlockPos, Highlight> blocks = BLOCKS.get(level.dimension());

		if (entities != null) {
			all.addAll(entities.values());
		}

		if (blocks != null) {
			all.addAll(blocks.values());
		}

		return all;
	}

		private static void spawnSegment(ServerLevel level, TrackMark mark, Vec3 start, Vec3 end, Trail trail) {
		Vec3 direction = end.subtract(start);
		float length = (float) direction.length();

		Quaternionf rotation = new Quaternionf().rotateTo(
				new Vector3f(0.0F, 0.0F, 1.0F),
				new Vector3f((float) direction.x, (float) direction.y, (float) direction.z).normalize());
		Vector3f offset = rotation.transform(
				new Vector3f(-PATH_LINE_THICKNESS / 2.0F, -PATH_LINE_THICKNESS / 2.0F, 0.0F));

		Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
		display.setPos(start.x, start.y, start.z);
		((BlockDisplayAccessor) display).itemflowtracker$setBlockState(PATH_MARKER_BLOCK.defaultBlockState());
		prepare(display, mark);

		DisplayAccessor accessor = (DisplayAccessor) display;
		accessor.itemflowtracker$setTransformation(new Transformation(
				offset,
				rotation,
				new Vector3f(PATH_LINE_THICKNESS, PATH_LINE_THICKNESS, length),
				new Quaternionf()));
		accessor.itemflowtracker$setWidth(PATH_CULLING_SIZE);
		accessor.itemflowtracker$setHeight(PATH_CULLING_SIZE);

		if (spawn(level, display)) {
			trail.markers.add(display);
		}
	}

	private static void tickBlocks(ServerLevel level) {
		Map<BlockPos, Highlight> tracked = BLOCKS.get(level.dimension());

		if (tracked == null || tracked.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<BlockPos, Highlight>> it = tracked.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<BlockPos, Highlight> entry = it.next();
			BlockPos pos = entry.getKey();
			Highlight highlight = entry.getValue();

			if (!level.isLoaded(pos)) {
				disown(highlight);
				continue;
			}

			TrackMark mark = blockMarkOf(level, pos, highlight);

			if (mark == null && level.getBlockEntity(pos) instanceof Container container) {
				mark = Nesting.inContainer(container);
			}

			if (mark == null) {
				removeDisplay(highlight);
				it.remove();
				continue;
			}

			boolean stale = highlight.display == null
					|| highlight.display.isRemoved()
					|| !mark.equals(highlight.mark);

			if (stale) {
				removeDisplay(highlight);
				highlight.mark = mark;
				highlight.display = spawnBlockHighlight(level, pos, mark);
			}
		}
	}

	private static void tickEntities(ServerLevel level) {
		Map<Integer, Highlight> tracked = ENTITIES.get(level.dimension());

		if (tracked == null || tracked.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<Integer, Highlight>> it = tracked.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Highlight> entry = it.next();
			Entity entity = level.getEntity(entry.getKey());
			Highlight highlight = entry.getValue();

			if (entity == null || entity.isRemoved()) {
				removeDisplay(highlight);
				it.remove();
				continue;
			}

			TrackMark mark = markOf(entity);

			if (mark == null) {
				removeDisplay(highlight);
				it.remove();
				continue;
			}

			boolean stale = highlight.display == null
					|| highlight.display.isRemoved()
					|| highlight.display.getVehicle() != entity
					|| !mark.equals(highlight.mark);

			if (stale) {
				removeDisplay(highlight);
				highlight.mark = mark;
				highlight.display = spawnEntityHighlight(level, entity, mark);
			}
		}
	}

	@Nullable
	private static TrackMark blockMarkOf(ServerLevel level, BlockPos pos, Highlight highlight) {
		if (highlight.blockMark == null) {
			return null;
		}

		if (!Tracking.isLive(highlight.blockMark) || level.getBlockState(pos).getBlock() != highlight.blockMarkOwner) {
			highlight.blockMark = null;
			highlight.blockMarkOwner = null;
			return null;
		}

		return highlight.blockMark;
	}

	@Nullable
	private static TrackMark markOf(Entity entity) {
		return Nesting.inEntity(entity);
	}


	@Nullable
	private static Display spawnEntityHighlight(ServerLevel level, Entity vehicle, TrackMark mark) {
		Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
		display.setPos(vehicle.getX(), vehicle.getY(), vehicle.getZ());
		((BlockDisplayAccessor) display).itemflowtracker$setBlockState(ENTITY_OUTLINE_BLOCK.defaultBlockState());
		prepare(display, mark);

		if (!spawn(level, display)) {
			return null;
		}

		if (!display.startRiding(vehicle, true, false)) {
			discard(display);
			return null;
		}

		vehicle.positionRider(display);
		fitToBoundingBox(display, vehicle);
		return display;
	}

	private static void fitToBoundingBox(Display display, Entity vehicle) {
		AABB box = vehicle.getBoundingBox().inflate(ENTITY_OUTLINE_PADDING);
		Vec3 at = display.position();

		((DisplayAccessor) display).itemflowtracker$setTransformation(new Transformation(
				new Vector3f((float) (box.minX - at.x), (float) (box.minY - at.y), (float) (box.minZ - at.z)),
				new Quaternionf(),
				new Vector3f((float) box.getXsize(), (float) box.getYsize(), (float) box.getZsize()),
				new Quaternionf()));
	}

	@Nullable
	private static Display spawnBlockHighlight(ServerLevel level, BlockPos pos, TrackMark mark) {
		BlockState state = level.getBlockState(pos);
		Display display = usesSpecialRenderer(state)
				? createItemDisplay(level, pos, state)
				: createBlockDisplay(level, pos, state);

		if (display == null) {
			return null;
		}

		prepare(display, mark);
		return spawn(level, display) ? display : null;
	}

	private static Display createBlockDisplay(ServerLevel level, BlockPos pos, BlockState state) {
		Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
		display.setPos(pos.getX(), pos.getY(), pos.getZ());
		((BlockDisplayAccessor) display).itemflowtracker$setBlockState(state);

		float offset = (OUTLINE_SCALE - 1.0F) / 2.0F;
		((DisplayAccessor) display).itemflowtracker$setTransformation(new Transformation(
				new Vector3f(-offset, -offset, -offset),
				new Quaternionf(),
				new Vector3f(OUTLINE_SCALE, OUTLINE_SCALE, OUTLINE_SCALE),
				new Quaternionf()));

		return display;
	}

	@Nullable
	private static Display createItemDisplay(ServerLevel level, BlockPos pos, BlockState state) {
		ItemStack stack = new ItemStack(state.getBlock());

		if (stack.isEmpty()) {
			return null;
		}

		Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
		display.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

		ItemDisplayAccessor itemAccessor = (ItemDisplayAccessor) display;
		itemAccessor.itemflowtracker$setItemStack(stack);
		itemAccessor.itemflowtracker$setItemTransform(ItemDisplayContext.NONE);

		Quaternionf rotation = new Quaternionf();

		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
			rotation.rotateY((float) Math.toRadians(-facing.toYRot()));
		}

		((DisplayAccessor) display).itemflowtracker$setTransformation(new Transformation(
				new Vector3f(),
				rotation,
				new Vector3f(OUTLINE_SCALE, OUTLINE_SCALE, OUTLINE_SCALE),
				new Quaternionf()));

		return display;
	}

	private static boolean usesSpecialRenderer(BlockState state) {
		return state.getBlock() instanceof AbstractChestBlock<?>
				|| state.getBlock() instanceof ShulkerBoxBlock
				|| state.getBlock() instanceof DecoratedPotBlock;
	}


	private static void prepare(Display display, TrackMark mark) {
		DisplayAccessor accessor = (DisplayAccessor) display;
		accessor.itemflowtracker$setGlowColorOverride(mark.rgb());
		accessor.itemflowtracker$setBrightnessOverride(Brightness.FULL_BRIGHT);
		display.setGlowingTag(true);
		display.setNoGravity(true);
		display.setInvulnerable(true);
		display.addTag(DISPLAY_TAG);
	}

	private static boolean spawn(ServerLevel level, Display display) {
		OWNED.add(display.getUUID());

		if (!level.addFreshEntity(display)) {
			OWNED.remove(display.getUUID());
			return false;
		}

		return true;
	}

	private static void discard(Display display) {
		OWNED.remove(display.getUUID());
		display.discard();
	}

	private static void removeDisplay(Highlight highlight) {
		if (highlight.display != null) {
			discard(highlight.display);
			highlight.display = null;
		}
	}

	private static void disown(Highlight highlight) {
		if (highlight.display != null) {
			OWNED.remove(highlight.display.getUUID());
			highlight.display = null;
		}
	}

	public static void onEntityUnload(Entity entity) {
		if (entity.entityTags().contains(DISPLAY_TAG)) {
			OWNED.remove(entity.getUUID());
		}
	}

	public static void onEntityLoad(Entity entity) {
		if (entity.entityTags().contains(DISPLAY_TAG) && !OWNED.contains(entity.getUUID())) {
			entity.discard();
		}
	}

	public static void clearAll(MinecraftServer server) {
		for (Map<BlockPos, Highlight> tracked : BLOCKS.values()) {
			for (Highlight highlight : tracked.values()) {
				removeDisplay(highlight);
			}
		}

		for (Map<Integer, Highlight> tracked : ENTITIES.values()) {
			for (Highlight highlight : tracked.values()) {
				removeDisplay(highlight);
			}
		}

		for (Trail trail : TRAILS.values()) {
			trail.markers.forEach(HighlightManager::discard);
		}

		BLOCKS.clear();
		ENTITIES.clear();
		TRAILS.clear();
		OWNED.clear();
	}

	public static void sweepLoadedDisplays(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			List<Entity> stale = new ArrayList<>();

			for (Entity entity : level.getAllEntities()) {
				if (entity.entityTags().contains(DISPLAY_TAG)) {
					stale.add(entity);
				}
			}

			stale.forEach(Entity::discard);
		}
	}
}
