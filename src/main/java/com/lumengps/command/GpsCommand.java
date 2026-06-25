package com.lumengps.command;

import com.lumengps.data.GpsConfig;
import com.lumengps.data.Waypoint;
import com.lumengps.data.WaypointManager;
import com.lumengps.pathfinding.PathResult;
import com.lumengps.pathfinding.Pathfinder;
import com.lumengps.ServerGpsManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class GpsCommand {

    private static final String PREFIX = "§b[LumenGPS]§r ";

    private GpsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess) {

        dispatcher.register(
            Commands.literal("gps")
                .executes(ctx -> {
                    sendHelp(ctx.getSource());
                    return 1;
                })
                .then(Commands.argument("shortcut_name", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        try {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            List<String> subCmds = List.of("help", "add", "addpos", "add_overwrite", "add_overwrite_cancel", "go", "remove", "remove_confirm", "share", "clear", "list", "config");
                            String rem = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                            WaypointManager.get(player.getUUID()).listNames().stream()
                                .filter(n -> !subCmds.contains(n.toLowerCase(java.util.Locale.ROOT)))
                                .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(rem))
                                .forEach(builder::suggest);
                        } catch (Exception e) {}
                        return builder.buildFuture();
                    })
                    .executes(ctx -> navigateTo(ctx.getSource(), StringArgumentType.getString(ctx, "shortcut_name"), "glow")))
                
                // /gps config
                .then(Commands.literal("config")
                    .executes(ctx -> {
                        sendConfigMenu(ctx.getSource());
                        return 1;
                    })
                    .then(Commands.literal("set")
                        .then(Commands.argument("key", StringArgumentType.word())
                            .then(Commands.argument("value", StringArgumentType.word())
                                .executes(ctx -> {
                                    String key = StringArgumentType.getString(ctx, "key");
                                    boolean val = Boolean.parseBoolean(StringArgumentType.getString(ctx, "value"));
                                    GpsConfig config = GpsConfig.getInstance();
                                    
                                    switch (key) {
                                        case "intelligentMode" -> config.intelligentMode = val;
                                        case "allowWater" -> config.allowWater = val;
                                        case "allowLava" -> config.allowLava = val;
                                        case "enableDeathWaypoint" -> config.enableDeathWaypoint = val;
                                        case "enableLightPillar" -> config.enableLightPillar = val;
                                        case "requireCompass" -> config.requireCompass = val;
                                        case "showHud" -> config.showHud = val;
                                        case "confirmOverwrite" -> config.confirmOverwrite = val;
                                    }
                                    config.save();
                                    sendConfigMenu(ctx.getSource());
                                    return 1;
                                })
                            )
                        )
                    )
                )

                // /gps help
                .then(Commands.literal("help")
                    .executes(ctx -> {
                        sendHelp(ctx.getSource());
                        return 1;
                    }))

                // /gps add <name>
                .then(Commands.literal("add")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            return addWaypoint(ctx.getSource(), StringArgumentType.getString(ctx, "name"), p.blockPosition(), p.level().dimension().identifier().toString(), "glow");
                        })
                        .then(Commands.argument("style", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                                List.of("glow", "fire", "soul", "end", "emerald").stream()
                                    .filter(s -> s.startsWith(prefix))
                                    .forEach(builder::suggest);
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                String style = StringArgumentType.getString(ctx, "style").toLowerCase(java.util.Locale.ROOT);
                                if (!List.of("glow", "fire", "soul", "end", "emerald").contains(style)) return 0;
                                return addWaypoint(ctx.getSource(), StringArgumentType.getString(ctx, "name"), p.blockPosition(), p.level().dimension().identifier().toString(), style);
                            })
                        )
                    )
                )

                // /gps go <name>
                .then(Commands.literal("go")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            try {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                                WaypointManager.get(player.getUUID()).listNames().stream()
                                    .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                                    .forEach(builder::suggest);
                            } catch (Exception e) {}
                            return builder.buildFuture();
                        })
                        .executes(ctx -> navigateTo(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "glow"))))

                // /gps clear
                .then(Commands.literal("clear")
                    .executes(ctx -> {
                        try {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ServerGpsManager.getInstance().clear(player.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal(PREFIX).append("Route cleared."), false);
                        } catch (Exception e) {}
                        return 1;
                    }))

                // /gps list
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        try {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            List<String> names = WaypointManager.get(player.getUUID()).listNames();
                            if (names.isEmpty()) {
                                ctx.getSource().sendSuccess(() -> Component.literal(PREFIX).append("No waypoints saved."), false);
                            } else {
                                ctx.getSource().sendSuccess(() -> Component.literal(PREFIX + "Saved waypoints: " + String.join(", ", names)), false);
                            }
                        } catch (Exception e) {}
                        return 1;
                    }))
        );
    }

    private static void sendConfigMenu(CommandSourceStack source) {
        GpsConfig c = GpsConfig.getInstance();
        MutableComponent text = Component.literal("\n§e=== LumenGPS Server Config ===§r\n");
        text.append(makeToggleRow("Intelligent Mode", "intelligentMode", c.intelligentMode));
        text.append(makeToggleRow("Allow Water", "allowWater", c.allowWater));
        text.append(makeToggleRow("Allow Lava", "allowLava", c.allowLava));
        text.append(makeToggleRow("Death Waypoint", "enableDeathWaypoint", c.enableDeathWaypoint));
        text.append(makeToggleRow("Light Pillar", "enableLightPillar", c.enableLightPillar));
        text.append(makeToggleRow("Require Compass", "requireCompass", c.requireCompass));
        text.append(makeToggleRow("Show HUD (Actionbar)", "showHud", c.showHud));
        text.append(makeToggleRow("Confirm Overwrite", "confirmOverwrite", c.confirmOverwrite));
        source.sendSuccess(() -> text, false);
    }

    private static Component makeToggleRow(String label, String key, boolean current) {
        String state = current ? "§aON " : "§cOFF";
        MutableComponent row = Component.literal("§7- " + label + ": " + state + " ");
        row.append(Component.literal("§e[Toggle]§r\n")
            .withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand("/gps config set " + key + " " + !current))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to toggle")))
            ));
        return row;
    }

    private static void sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§b=== LumenGPS Help ===§r\n" +
                "/gps add <name> [style] - Save waypoint\n" +
                "/gps go <name> - Navigate to waypoint\n" +
                "/gps clear - Clear active route\n" +
                "/gps list - List waypoints\n" +
                "/gps config - Open config menu"), false);
    }

    private static int addWaypoint(CommandSourceStack source, String name, BlockPos pos, String dim, String style) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            WaypointManager.get(player.getUUID()).add(name, pos, dim, style);
            source.sendSuccess(() -> Component.literal(PREFIX + "Waypoint '" + name + "' added at " + pos.toShortString()), false);
        } catch (Exception e) {}
        return 1;
    }

    private static int navigateTo(CommandSourceStack source, String name, String fallbackStyle) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            UUID pid = player.getUUID();
            WaypointManager wm = WaypointManager.get(pid);
            Optional<Waypoint> opt = wm.getWaypoint(name);

            if (opt.isEmpty()) {
                source.sendFailure(Component.literal(PREFIX + "Waypoint '" + name + "' not found."));
                return 0;
            }

            Waypoint wp = opt.get();
            if (!wp.dimension().equals(player.level().dimension().identifier().toString())) {
                source.sendFailure(Component.literal(PREFIX + "Waypoint is in a different dimension (" + wp.dimension() + ")."));
                return 0;
            }

            source.sendSuccess(() -> Component.literal(PREFIX + "Computing path..."), false);
            
            Pathfinder.computeAsync((ServerLevel) player.level(), player.blockPosition(), wp.pos(), false, result -> {
                if (result.points().isEmpty()) {
                    source.sendFailure(Component.literal(PREFIX + "Could not find a path."));
                } else {
                    ServerGpsManager.getInstance().setRoute(pid, result.points(), name, result.points().get(result.points().size() - 1), false, wp.style());
                    source.sendSuccess(() -> Component.literal(PREFIX + "Path calculated! Follow the particles."), false);
                }
            });

        } catch (Exception e) {}
        return 1;
    }
}
