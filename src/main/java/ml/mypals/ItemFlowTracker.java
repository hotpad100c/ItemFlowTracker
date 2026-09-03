package ml.mypals;

import java.util.function.IntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import ml.mypals.track.HighlightManager;
import ml.mypals.track.TrackMark;
import ml.mypals.track.Tracking;
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
		ServerTickEvents.END_SERVER_TICK.register(server -> Tracking.sweepExhaustedSessions());
		ServerTickEvents.END_LEVEL_TICK.register(HighlightManager::tick);

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> HighlightManager.onEntityLoad(entity));

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HighlightManager.clearAll(server);
			HighlightManager.sweepLoadedDisplays(server);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> registerCommands(dispatcher));

	}

	private static void registerCommands(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> mark = Commands.literal("mark");

		for (DyeColor color : DyeColor.values()) {
			mark.then(Commands.literal(color.getName())
					.executes(context -> markHeldItem(context.getSource(), dyeSession(color)))
					.then(Commands.argument("targets", EntityArgument.entities())
							.executes(context -> markEntities(
									context.getSource(),
									dyeSession(color),
									EntityArgument.getEntities(context, "targets")))));
		}

		mark.then(Commands.literal("hex")
				.then(Commands.argument("rgb", StringArgumentType.string())
						.executes(context -> markHeldItem(
								context.getSource(),
								hexSession(StringArgumentType.getString(context, "rgb"))))
						.then(Commands.argument("targets", EntityArgument.entities())
								.executes(context -> markEntities(
										context.getSource(),
										hexSession(StringArgumentType.getString(context, "rgb")),
										EntityArgument.getEntities(context, "targets"))))));

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

	private static IntFunction<TrackMark> dyeSession(DyeColor color) {
		return capacity -> Tracking.newMark(color, capacity);
	}

	private static IntFunction<TrackMark> hexSession(String raw) throws CommandSyntaxException {
		String digits = raw.startsWith("#") ? raw.substring(1)
				: raw.regionMatches(true, 0, "0x", 0, 2) ? raw.substring(2)
				: raw;

		if (digits.length() != 6 || digits.chars().anyMatch(c -> Character.digit(c, 16) < 0)) {
			throw BAD_HEX.create(raw);
		}

		int rgb = Integer.parseInt(digits, 16);
		return capacity -> Tracking.newMark(rgb, capacity);
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
