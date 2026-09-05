package ml.mypals.ift;

import java.util.function.IntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import ml.mypals.ift.track.HighlightManager;
import ml.mypals.ift.track.TrackMark;
import ml.mypals.ift.track.Tracking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public class ItemFlowTracker implements ModInitializer {
	public static final String MOD_ID = "itemflowtracker";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final DynamicCommandExceptionType BAD_HEX = new DynamicCommandExceptionType(
			value -> Component.literal("Expected a colour like ff8800 or 0xff8800 (quote it to use #), got '" + value + "'"));

	@Override
	public void onInitialize() {
		register();
	}
	//TODO 所有的注册项！
	private static void register(){

		ServerTickEvents.END_SERVER_TICK.register(server -> Tracking.sweepExhaustedSessions());
		ServerTickEvents.END_LEVEL_TICK.register(HighlightManager::tick);

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> HighlightManager.onEntityLoad(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> HighlightManager.onEntityUnload(entity));

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HighlightManager.clearAll(server);
			HighlightManager.sweepLoadedDisplays(server);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> registerCommands(dispatcher));

	}
	/**
	 * /ift mark <color>         <target_item>               <track_path>          = 标记手持的/指定的物品
	 *            颜色    附近的目标物品（其它实体无效；可选）    路径追踪间隔（可选）
	 * */

	private static void registerCommands(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> mark = Commands.literal("mark");

		for (DyeColor color : DyeColor.values()) {
			LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(color.getName());
			addMarkVariants(node, (context, pathInterval) -> capacity -> Tracking.newMark(color, capacity, pathInterval));
			mark.then(node);
		}

		mark.then(Commands.literal("hex")
				.then(addMarkVariants(
						Commands.argument("rgb", StringArgumentType.string()),
						(context, pathInterval) -> {
							int rgb = parseHex(StringArgumentType.getString(context, "rgb"));
							return capacity -> Tracking.newMark(rgb, capacity, pathInterval);
						})));

		dispatcher.register(Commands.literal("ift")
				.then(mark)
				.then(Commands.literal("status").executes(context -> {
					java.util.List<TrackMark> sessions = Tracking.activeSessions();

					if (sessions.isEmpty()) {
						context.getSource().sendSuccess(() -> Component.literal("No active tracking sessions"), false);
						return 0;
					}

					for (TrackMark session : sessions) {
						String name = session.label().startsWith("#")
								? session.label()
								: String.format("%s #%06X", session.label(), session.rgb());

						context.getSource().sendSuccess(
								() -> Component.literal(String.format("  %s budget %d/%d",
										name,
										session.budget(),
										session.capacity())),
								false);
					}

					return sessions.size();
				}))
				.then(Commands.literal("clear")
						.requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
						.executes(context -> {
							Tracking.clearAll();
							HighlightManager.clearAll(context.getSource().getServer());
							context.getSource().sendSuccess(() -> Component.literal("Cleared all item tracking"), true);
							return 1;
						})));
	}

	@FunctionalInterface
	private interface SessionFactory {
		IntFunction<TrackMark> create(CommandContext<CommandSourceStack> context, int pathInterval) throws CommandSyntaxException;
	}

	private static <T extends ArgumentBuilder<CommandSourceStack, T>> T addMarkVariants(T node, SessionFactory factory) {
		node.executes(context -> markHeldItem(context.getSource(), factory.create(context, 0)));

		node.then(Commands.argument("track_path", IntegerArgumentType.integer(0))
				.executes(context -> markHeldItem(
						context.getSource(),
						factory.create(context, IntegerArgumentType.getInteger(context, "track_path")))));

		node.then(Commands.argument("targets", EntityArgument.entities())
				.executes(context -> markEntities(
						context.getSource(),
						factory.create(context, 0),
						EntityArgument.getEntities(context, "targets")))
				.then(Commands.argument("track_path", IntegerArgumentType.integer(0))
						.executes(context -> markEntities(
								context.getSource(),
								factory.create(context, IntegerArgumentType.getInteger(context, "track_path")),
								EntityArgument.getEntities(context, "targets")))));

		return node;
	}

	private static int parseHex(String raw) throws CommandSyntaxException {
		String digits = raw.startsWith("#") ? raw.substring(1)
				: raw.regionMatches(true, 0, "0x", 0, 2) ? raw.substring(2)
				: raw;

		if (digits.length() != 6 || digits.chars().anyMatch(c -> Character.digit(c, 16) < 0)) {
			throw BAD_HEX.create(raw);
		}

		return Integer.parseInt(digits, 16);
	}

	private static int markHeldItem(CommandSourceStack source, IntFunction<TrackMark> session) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack stack = player.getMainHandItem();

		if (stack.isEmpty()) {
			source.sendFailure(Component.literal("Hold the item you want to track in your main hand"));
			return 0;
		}

		TrackMark mark = session.apply(stack.getCount());
		Tracking.set(stack, mark);
		source.sendSuccess(() -> Component.literal(
				"Now tracking " + stack.getHoverName().getString() + " in " + mark.label()), false);
		return 1;
	}

	private static int markEntities(CommandSourceStack source, IntFunction<TrackMark> session, java.util.Collection<? extends Entity> targets) {
		int marked = 0;
		TrackMark last = null;

		for (Entity entity : targets) {
			if (entity instanceof ItemEntity item && !item.getItem().isEmpty()) {
				last = session.apply(item.getItem().getCount());
				Tracking.set(item.getItem(), last);
				marked++;
			}
		}

		int count = marked;
		String label = last == null ? "-" : last.label();
		source.sendSuccess(() -> Component.literal("Now tracking " + count + " dropped item(s) in " + label), false);
		return count;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
