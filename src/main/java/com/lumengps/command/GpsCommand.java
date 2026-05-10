package com.lumengps.command;

import com.lumengps.data.Waypoint;
import com.lumengps.data.WaypointManager;
import com.lumengps.pathfinding.PathResult;
import com.lumengps.pathfinding.Pathfinder;
import com.lumengps.renderer.GpsRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Registers all {@code /gps} sub-commands using the Fabric client command API.
 *
 * <p>These are <em>client-side</em> commands — they work on any server (vanilla
 * or modded) without the mod being installed server-side. All world access is
 * performed on the client world.</p>
 *
 * <h3>Commands</h3>
 * <pre>
 *   /gps add &lt;name&gt;   — Save current position as a named waypoint.
 *   /gps go  &lt;name&gt;   — Compute and display a glowing route to the waypoint.
 *   /gps clear        — Remove the active particle route.
 *   /gps list         — List all saved waypoint names.
 * </pre>
 */
@Environment(EnvType.CLIENT)
public final class GpsCommand {

    // Chat prefix used in all feedback messages.
    private static final String PREFIX = "§b[LumenGPS]§r ";

    private GpsCommand() {}

    /**
     * Called by {@link com.lumengps.LumenGPSClient} during
     * {@link net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback}.
     */
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                CommandBuildContext registryAccess) {

        dispatcher.register(
            ClientCommands.literal("gps")
                .executes(ctx -> {
                    sendHelp(ctx.getSource());
                    return 1;
                })
                // /gps help
                .then(ClientCommands.literal("help")
                    .executes(ctx -> {
                        sendHelp(ctx.getSource());
                        return 1;
                    }))
                // /gps add <name>
                .then(ClientCommands.literal("add")
                    .then(ClientCommands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            FabricClientCommandSource source = ctx.getSource();
                            BlockPos pos = BlockPos.containing(source.getPlayer().position());

                            WaypointManager.getInstance().add(name, pos, "glow");
                            source.sendFeedback(Component.literal(PREFIX)
                                    .append(Component.translatable("lumengps.command.waypoint_saved", name, formatPos(pos))));
                            return 1;
                        })
                        .then(ClientCommands.argument("style", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                                List.of("glow", "fire", "soul", "end", "emerald").stream()
                                    .filter(s -> s.startsWith(prefix))
                                    .forEach(builder::suggest);
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "name");
                                String style = StringArgumentType.getString(ctx, "style").toLowerCase(java.util.Locale.ROOT);
                                FabricClientCommandSource source = ctx.getSource();
                                BlockPos pos = BlockPos.containing(source.getPlayer().position());

                                if (!List.of("glow", "fire", "soul", "end", "emerald").contains(style)) {
                                    source.sendError(Component.literal(PREFIX)
                                            .append(Component.translatable("lumengps.command.invalid_style", style)));
                                    return 0;
                                }

                                WaypointManager.getInstance().add(name, pos, style);
                                source.sendFeedback(Component.literal(PREFIX)
                                        .append(Component.translatable("lumengps.command.waypoint_saved", name, formatPos(pos))));
                                return 1;
                            }))))

                // /gps addpos <name> <x> <y> <z> [style]
                .then(ClientCommands.literal("addpos")
                    .then(ClientCommands.argument("name", StringArgumentType.word())
                        .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                            .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");
                                        int x = IntegerArgumentType.getInteger(ctx, "x");
                                        int y = IntegerArgumentType.getInteger(ctx, "y");
                                        int z = IntegerArgumentType.getInteger(ctx, "z");
                                        FabricClientCommandSource source = ctx.getSource();
                                        BlockPos pos = new BlockPos(x, y, z);

                                        WaypointManager.getInstance().add(name, pos, "glow");
                                        source.sendFeedback(Component.literal(PREFIX)
                                                .append(Component.translatable("lumengps.command.waypoint_saved", name, formatPos(pos))));
                                        return 1;
                                    })
                                    .then(ClientCommands.argument("style", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                                            List.of("glow", "fire", "soul", "end", "emerald").stream()
                                                .filter(s -> s.startsWith(prefix))
                                                .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            int x = IntegerArgumentType.getInteger(ctx, "x");
                                            int y = IntegerArgumentType.getInteger(ctx, "y");
                                            int z = IntegerArgumentType.getInteger(ctx, "z");
                                            String style = StringArgumentType.getString(ctx, "style").toLowerCase(java.util.Locale.ROOT);
                                            FabricClientCommandSource source = ctx.getSource();
                                            BlockPos pos = new BlockPos(x, y, z);

                                            if (!List.of("glow", "fire", "soul", "end", "emerald").contains(style)) {
                                                source.sendError(Component.literal(PREFIX)
                                                        .append(Component.translatable("lumengps.command.invalid_style", style)));
                                                return 0;
                                            }

                                            WaypointManager.getInstance().add(name, pos, style);
                                            source.sendFeedback(Component.literal(PREFIX)
                                                    .append(Component.translatable("lumengps.command.waypoint_saved", name, formatPos(pos))));
                                            return 1;
                                        })))))))

                // /gps go <name>
                .then(ClientCommands.literal("go")
                    .then(ClientCommands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                            WaypointManager.getInstance().listNames().stream()
                                .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                                .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            FabricClientCommandSource source = ctx.getSource();

                            Optional<Waypoint> waypointOpt = WaypointManager.getInstance().get(name);

                            if (waypointOpt.isEmpty()) {
                                source.sendError(Component.literal(PREFIX)
                                        .append(Component.translatable("lumengps.command.no_waypoint_found", name)));
                                return 0;
                            }

                            BlockPos goal = waypointOpt.get().pos();
                            String style = waypointOpt.get().style();
                            BlockPos start = BlockPos.containing(source.getPlayer().position());
                            Level world = source.getPlayer().level();

                            source.sendFeedback(Component.literal(PREFIX)
                                    .append(Component.translatable("lumengps.command.calculating_route", name)));

                            // Run A* asynchronously; callback runs on the main thread.
                            Pathfinder.computeAsync(world, start, goal, (PathResult result) -> {
                                if (result.isEmpty()) {
                                    source.sendError(Component.literal(PREFIX)
                                            .append(Component.translatable("lumengps.command.route_not_found", name)));
                                    return;
                                }
                                GpsRenderer.getInstance().setRoute(result.points(), style);
                                if (result.isFallback()) {
                                    // A* failed — crow-fly trail floating above terrain.
                                    source.sendFeedback(Component.literal(PREFIX)
                                            .append(Component.translatable("lumengps.command.route_blocked_fallback", name, String.valueOf(result.points().size()))));
                                } else {
                                    source.sendFeedback(Component.literal(PREFIX)
                                            .append(Component.translatable("lumengps.command.route_found", String.valueOf(result.points().size()))));
                                }
                            });

                            return 1;
                        })))

                // /gps remove <name>
                .then(ClientCommands.literal("remove")
                    .then(ClientCommands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                            WaypointManager.getInstance().listNames().stream()
                                .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                                .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            FabricClientCommandSource source = ctx.getSource();

                            if (WaypointManager.getInstance().remove(name)) {
                                source.sendFeedback(Component.literal(PREFIX)
                                        .append(Component.translatable("lumengps.command.waypoint_removed", name)));
                                return 1;
                            } else {
                                source.sendError(Component.literal(PREFIX)
                                        .append(Component.translatable("lumengps.command.no_waypoint_found", name)));
                                return 0;
                            }
                        })))

                // /gps clear
                .then(ClientCommands.literal("clear")
                    .executes(ctx -> {
                        GpsRenderer.getInstance().clear();
                        ctx.getSource().sendFeedback(Component.literal(PREFIX)
                                .append(Component.translatable("lumengps.command.route_cleared")));
                        return 1;
                    }))

                // /gps list
                .then(ClientCommands.literal("list")
                    .executes(ctx -> {
                        List<String> names = WaypointManager.getInstance().listNames();
                        FabricClientCommandSource source = ctx.getSource();

                        if (names.isEmpty()) {
                            source.sendFeedback(Component.literal(PREFIX)
                                    .append(Component.translatable("lumengps.command.no_waypoints_saved")));
                        } else {
                            source.sendFeedback(Component.literal(PREFIX)
                                    .append(Component.translatable("lumengps.command.saved_waypoints_list", String.valueOf(names.size()))));
                            names.forEach(n -> source.sendFeedback(Component.translatable("lumengps.command.waypoint_list_item", n)));
                        }
                        return 1;
                    }))
        );
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static void sendHelp(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("lumengps.command.help.header"));
        source.sendFeedback(Component.translatable("lumengps.command.help.add"));
        source.sendFeedback(Component.translatable("lumengps.command.help.addpos"));
        source.sendFeedback(Component.translatable("lumengps.command.help.go"));
        source.sendFeedback(Component.translatable("lumengps.command.help.list"));
        source.sendFeedback(Component.translatable("lumengps.command.help.remove"));
        source.sendFeedback(Component.translatable("lumengps.command.help.clear"));
    }

    private static String formatPos(BlockPos pos) {
        return "§7(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")§r";
    }
}
