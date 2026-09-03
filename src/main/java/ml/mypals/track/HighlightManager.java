package ml.mypals.track;

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

import ml.mypals.mixin.BlockDisplayAccessor;
import ml.mypals.mixin.DisplayAccessor;
import ml.mypals.mixin.ItemDisplayAccessor;
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

public final class HighlightManager {
	public static final String DISPLAY_TAG = "itemflowtracker.display";

	private static final int VERIFY_INTERVAL_TICKS = 5;
	private static final float OUTLINE_SCALE = 1.02F;

	private static final Block ENTITY_OUTLINE_BLOCK = Blocks.STAINED_GLASS.white();
	private static final double ENTITY_OUTLINE_PADDING = 0.02;

	private static final Map<ResourceKey<Level>, Map<BlockPos, Highlight>> BLOCKS = new HashMap<>();
	private static final Map<ResourceKey<Level>, Map<Integer, Highlight>> ENTITIES = new HashMap<>();

	private static final Set<UUID> OWNED = new HashSet<>();

	private HighlightManager() {
	}

	private static final class Highlight {
		@Nullable
		TrackMark mark;
		@Nullable
		Display display;
	}

	public static void onEnterContainer(@Nullable Container container) {
		Containers.forEachLeaf(container, leaf -> {
			if (Containers.findMark(leaf) == null) {
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

	public static void watchEntity(Entity entity) {
		ENTITIES.computeIfAbsent(entity.level().dimension(), key -> new HashMap<>())
				.computeIfAbsent(entity.getId(), key -> new Highlight());
	}

	public static void tick(ServerLevel level) {
		if (level.getGameTime() % VERIFY_INTERVAL_TICKS != 0) {
			return;
		}

		tickBlocks(level);
		tickEntities(level);
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

			TrackMark mark = level.getBlockEntity(pos) instanceof Container container
					? Containers.findMark(container)
					: null;

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

			// A discarded vehicle only ejects its passengers, so a lost ride means the display is
			// floating free and has to be rebuilt on whatever entity survived.
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
	private static TrackMark markOf(Entity entity) {
		if (entity instanceof ItemEntity item) {
			return Tracking.get(item.getItem());
		}

		return entity instanceof Container container ? Containers.findMark(container) : null;
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

		BLOCKS.clear();
		ENTITIES.clear();
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
