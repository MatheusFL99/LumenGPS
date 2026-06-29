package com.lumengps.command;

import com.lumengps.data.GpsConfig;
import com.lumengps.data.Waypoint;
import com.lumengps.data.WaypointManager;
import com.lumengps.data.ServerWaypointManager;
import com.lumengps.pathfinding.PathResult;
import com.lumengps.pathfinding.Pathfinder;
import com.lumengps.ServerGpsManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
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
                .then(Commands.argument("shortcut_name", StringArgumentType.string())
                    .suggests((ctx, builder) -> {
                        try {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            List<String> subCmds = List.of("help", "add", "addcord", "add_overwrite", "add_overwrite_cancel", "go", "remove", "remove_confirm", "share", "clear", "list", "config", "server");
                            String rem = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                            WaypointManager.get(player.getUUID()).listNames().stream()
                                .filter(n -> !subCmds.contains(n.toLowerCase(java.util.Locale.ROOT)))
                                .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(rem))
                                .forEach(builder::suggest);
                            ServerWaypointManager.getInstance().listNames().stream()
                                .filter(n -> !subCmds.contains(n.toLowerCase(java.util.Locale.ROOT)))
                                .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(rem))
                                .forEach(builder::suggest);
                        } catch (Exception e) {}
                        return builder.buildFuture();
                    })
                    .executes(ctx -> navigateTo(ctx.getSource(), StringArgumentType.getString(ctx, "shortcut_name"), null)))                
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
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            return addWaypoint(ctx.getSource(), StringArgumentType.getString(ctx, "name"), p.blockPosition(), p.level().dimension().identifier().toString(), "glow");
                        })
                    )
                )
                // /gps addcord <name> <coordinates>
                .then(Commands.literal("addcord")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .then(Commands.argument("coordinates", BlockPosArgument.blockPos())
                            .executes(ctx -> {
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                String n = StringArgumentType.getString(ctx, "name");
                                BlockPos pos = BlockPosArgument.getBlockPos(ctx, "coordinates");
                                return addWaypoint(ctx.getSource(), n, pos, p.level().dimension().identifier().toString(), "glow");
                            })
                        )
                    )
                )
                // /gps add_overwrite <name> <x> <y> <z> <dim> <style>
                .then(Commands.literal("add_overwrite")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                    .then(Commands.argument("dim", StringArgumentType.string())
                                        .then(Commands.argument("style", StringArgumentType.word())
                                            .executes(ctx -> {
                                                String n = StringArgumentType.getString(ctx, "name");
                                                int x = IntegerArgumentType.getInteger(ctx, "x");
                                                int y = IntegerArgumentType.getInteger(ctx, "y");
                                                int z = IntegerArgumentType.getInteger(ctx, "z");
                                                String dim = StringArgumentType.getString(ctx, "dim");
                                                String style = StringArgumentType.getString(ctx, "style");
                                                addWaypointDirectly(ctx.getSource(), n, new BlockPos(x, y, z), dim, style);
                                                return 1;
                                            })
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
                // /gps add_overwrite_cancel <name>
                .then(Commands.literal("add_overwrite_cancel")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> {
                            String n = StringArgumentType.getString(ctx, "name");
                            ctx.getSource().sendSuccess(() -> Component.literal(PREFIX + "Sobrescrita de '" + n + "' cancelada."), false);
                            return 1;
                        })
                    )
                )
                // /gps go <name> [scope]
                .then(Commands.literal("go")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            try {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                                WaypointManager.get(player.getUUID()).listNames().stream()
                                    .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                                    .forEach(builder::suggest);
                                ServerWaypointManager.getInstance().listNames().stream()
                                    .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                                    .forEach(builder::suggest);
                            } catch (Exception e) {}
                            return builder.buildFuture();
                        })
                        .executes(ctx -> navigateTo(ctx.getSource(), StringArgumentType.getString(ctx, "name"), null))
                        .then(Commands.argument("scope", StringArgumentType.word())
                            .executes(ctx -> navigateTo(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "scope"))))
                    )
                )

                // /gps share <name> [scope]
                .then(Commands.literal("share")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            try {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                                WaypointManager.get(player.getUUID()).listNames().stream()
                                    .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                                    .forEach(builder::suggest);
                                ServerWaypointManager.getInstance().listNames().stream()
                                    .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                                    .forEach(builder::suggest);
                            } catch (Exception e) {}
                            return builder.buildFuture();
                        })
                        .executes(ctx -> shareWaypoint(ctx.getSource(), StringArgumentType.getString(ctx, "name"), null))
                        .then(Commands.argument("scope", StringArgumentType.word())
                            .executes(ctx -> shareWaypoint(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "scope"))))
                    )
                )

                // /gps remove <name>
                .then(Commands.literal("remove")
                    .then(Commands.argument("name", StringArgumentType.string())
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
                        .executes(ctx -> {
                            try {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                String name = StringArgumentType.getString(ctx, "name");
                                if (WaypointManager.get(player.getUUID()).getWaypoint(name).isEmpty()) {
                                    ctx.getSource().sendFailure(Component.literal(PREFIX + "Waypoint '" + name + "' não encontrado."));
                                    return 0;
                                }
                                
                                MutableComponent msg = Component.literal(PREFIX + "Apagar '" + name + "'? ");
                                msg.append(Component.literal("§a[Sim]§r")
                                    .withStyle(s -> s
                                        .withClickEvent(new ClickEvent.RunCommand("/gps remove_confirm \"" + name.replace("\"", "\\\"") + "\""))
                                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Confirmar exclusão")))));
                                msg.append(Component.literal(" §7[Cancelar]§r")
                                    .withStyle(s -> s
                                        .withClickEvent(new ClickEvent.RunCommand("/gps list"))
                                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Cancelar")))));
                                        
                                ctx.getSource().sendSuccess(() -> msg, false);
                            } catch (Exception e) {}
                            return 1;
                        })
                    )
                )

                // /gps remove_confirm <name>
                .then(Commands.literal("remove_confirm")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> {
                            try {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                String name = StringArgumentType.getString(ctx, "name");
                                WaypointManager.get(player.getUUID()).remove(name);
                                ctx.getSource().sendSuccess(() -> Component.literal(PREFIX + "Waypoint '" + name + "' removido."), false);
                            } catch (Exception e) {}
                            return 1;
                        })
                    )
                )

                // /gps clear
                .then(Commands.literal("clear")
                    .executes(ctx -> {
                        try {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ServerGpsManager.getInstance().clear(player.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal(PREFIX).append("Rota limpa."), false);
                        } catch (Exception e) {}
                        return 1;
                    }))

                // /gps list
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        try {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            List<String> names = WaypointManager.get(player.getUUID()).listNames();
                            List<String> serverNames = ServerWaypointManager.getInstance().listNames();
                            
                            if (names.isEmpty() && serverNames.isEmpty()) {
                                ctx.getSource().sendSuccess(() -> Component.literal(PREFIX).append("Nenhum waypoint salvo."), false);
                            } else {
                                if (!names.isEmpty()) {
                                    ctx.getSource().sendSuccess(() -> Component.literal("§b=== Seus Waypoints ===§r\n"), false);
                                    for (String name : names) {
                                        MutableComponent line = Component.literal("§7- §e" + name + "§r ");
                                        
                                        MutableComponent goBtn = Component.literal("§a[Ir]§r")
                                            .withStyle(s -> s
                                                .withClickEvent(new ClickEvent.RunCommand("/gps go \"" + name.replace("\"", "\\\"") + "\""))
                                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Navegar até " + name))));
                                                
                                        MutableComponent shareBtn = Component.literal(" §b[Compartilhar]§r")
                                            .withStyle(s -> s
                                                .withClickEvent(new ClickEvent.RunCommand("/gps share \"" + name.replace("\"", "\\\"") + "\""))
                                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Compartilhar " + name + " no chat"))));
                                                
                                        MutableComponent removeBtn = Component.literal(" §c[Remover]§r")
                                            .withStyle(s -> s
                                                .withClickEvent(new ClickEvent.RunCommand("/gps remove \"" + name.replace("\"", "\\\"") + "\""))
                                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Apagar " + name))));
                                                
                                        line.append(goBtn).append(shareBtn).append(removeBtn);
                                        ctx.getSource().sendSuccess(() -> line, false);
                                    }
                                }
                                
                                if (!serverNames.isEmpty()) {
                                    ctx.getSource().sendSuccess(() -> Component.literal("\n§d=== Waypoints do Servidor ===§r\n"), false);
                                    boolean isOp = isOp(ctx.getSource());
                                    for (String name : serverNames) {
                                        MutableComponent line = Component.literal("§7- §d" + name + "§r ");
                                        
                                        MutableComponent goBtn = Component.literal("§a[Ir]§r")
                                            .withStyle(s -> s
                                                .withClickEvent(new ClickEvent.RunCommand("/gps go \"" + name.replace("\"", "\\\"") + "\""))
                                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Navegar até " + name))));
                                                
                                        MutableComponent shareBtn = Component.literal(" §b[Compartilhar]§r")
                                            .withStyle(s -> s
                                                .withClickEvent(new ClickEvent.RunCommand("/gps share \"" + name.replace("\"", "\\\"") + "\""))
                                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Compartilhar " + name + " no chat"))));
                                                
                                        line.append(goBtn).append(shareBtn);
                                        
                                        if (isOp) {
                                            MutableComponent removeBtn = Component.literal(" §c[Remover]§r")
                                                .withStyle(s -> s
                                                    .withClickEvent(new ClickEvent.RunCommand("/gps server remove \"" + name.replace("\"", "\\\"") + "\""))
                                                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Apagar do servidor: " + name))));
                                            line.append(removeBtn);
                                        }
                                        ctx.getSource().sendSuccess(() -> line, false);
                                    }
                                }
                            }
                        } catch (Exception e) {}
                        return 1;
                    }))

                // /gps server
                .then(Commands.literal("server")
                    .then(Commands.literal("list")
                        .executes(ctx -> {
                            try {
                                List<String> names = ServerWaypointManager.getInstance().listNames();
                                if (names.isEmpty()) {
                                    ctx.getSource().sendSuccess(() -> Component.literal(PREFIX).append("Nenhum waypoint de servidor salvo."), false);
                                } else {
                                    ctx.getSource().sendSuccess(() -> Component.literal("§b=== Waypoints do Servidor ===§r\n"), false);
                                    boolean isOp = isOp(ctx.getSource());
                                    for (String name : names) {
                                        MutableComponent line = Component.literal("§7- §e" + name + "§r ");
                                        
                                        MutableComponent goBtn = Component.literal("§a[Ir]§r")
                                            .withStyle(s -> s
                                                .withClickEvent(new ClickEvent.RunCommand("/gps go \"" + name.replace("\"", "\\\"") + "\""))
                                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Navegar até " + name))));
                                                
                                        MutableComponent shareBtn = Component.literal(" §b[Compartilhar]§r")
                                            .withStyle(s -> s
                                                .withClickEvent(new ClickEvent.RunCommand("/gps share \"" + name.replace("\"", "\\\"") + "\""))
                                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Compartilhar " + name + " no chat"))));
                                                
                                        line.append(goBtn).append(shareBtn);
                                        
                                        if (isOp) {
                                            MutableComponent removeBtn = Component.literal(" §c[Remover]§r")
                                                .withStyle(s -> s
                                                    .withClickEvent(new ClickEvent.RunCommand("/gps server remove \"" + name.replace("\"", "\\\"") + "\""))
                                                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Apagar do servidor: " + name))));
                                            line.append(removeBtn);
                                        }
                                        
                                        ctx.getSource().sendSuccess(() -> line, false);
                                    }
                                }
                            } catch (Exception e) {}
                            return 1;
                        })
                    )
                    .then(Commands.literal("add").requires(src -> isOp(src))
                        .then(Commands.argument("name", StringArgumentType.string())
                            .executes(ctx -> {
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                return addServerWaypoint(ctx.getSource(), StringArgumentType.getString(ctx, "name"), p.blockPosition(), p.level().dimension().identifier().toString(), "glow");
                            })
                        )
                    )
                    .then(Commands.literal("addcord").requires(src -> isOp(src))
                        .then(Commands.argument("name", StringArgumentType.string())
                            .then(Commands.argument("coordinates", BlockPosArgument.blockPos())
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String n = StringArgumentType.getString(ctx, "name");
                                    BlockPos pos = BlockPosArgument.getBlockPos(ctx, "coordinates");
                                    return addServerWaypoint(ctx.getSource(), n, pos, p.level().dimension().identifier().toString(), "glow");
                                })
                            )
                        )
                    )
                    .then(Commands.literal("add_overwrite").requires(src -> isOp(src))
                        .then(Commands.argument("name", StringArgumentType.string())
                            .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                    .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .then(Commands.argument("dim", StringArgumentType.string())
                                            .then(Commands.argument("style", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String n = StringArgumentType.getString(ctx, "name");
                                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                                    String dim = StringArgumentType.getString(ctx, "dim");
                                                    String style = StringArgumentType.getString(ctx, "style");
                                                    addServerWaypointDirectly(ctx.getSource(), n, new BlockPos(x, y, z), dim, style);
                                                    return 1;
                                                })
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("add_overwrite_cancel").requires(src -> isOp(src))
                        .then(Commands.argument("name", StringArgumentType.string())
                            .executes(ctx -> {
                                String n = StringArgumentType.getString(ctx, "name");
                                ctx.getSource().sendSuccess(() -> Component.literal(PREFIX + "Sobrescrita do waypoint do servidor '" + n + "' cancelada."), false);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("remove").requires(src -> isOp(src))
                        .then(Commands.argument("name", StringArgumentType.string())
                            .suggests((ctx, builder) -> {
                                try {
                                    String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                                    ServerWaypointManager.getInstance().listNames().stream()
                                        .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                                        .forEach(builder::suggest);
                                } catch (Exception e) {}
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                try {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    if (ServerWaypointManager.getInstance().getWaypoint(name).isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal(PREFIX + "Waypoint do servidor '" + name + "' não encontrado."));
                                        return 0;
                                    }
                                    
                                    MutableComponent msg = Component.literal(PREFIX + "Apagar waypoint de servidor '" + name + "'? ");
                                    msg.append(Component.literal("§a[Sim]§r")
                                        .withStyle(s -> s
                                            .withClickEvent(new ClickEvent.RunCommand("/gps server remove_confirm \"" + name.replace("\"", "\\\"") + "\""))
                                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Confirmar exclusão global")))));
                                    msg.append(Component.literal(" §7[Cancelar]§r")
                                        .withStyle(s -> s
                                            .withClickEvent(new ClickEvent.RunCommand("/gps server list"))
                                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Cancelar")))));
                                            
                                    ctx.getSource().sendSuccess(() -> msg, false);
                                } catch (Exception e) {}
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("remove_confirm").requires(src -> isOp(src))
                        .then(Commands.argument("name", StringArgumentType.string())
                            .executes(ctx -> {
                                try {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    ServerWaypointManager.getInstance().remove(name);
                                    ctx.getSource().sendSuccess(() -> Component.literal(PREFIX + "Waypoint do servidor '" + name + "' removido."), false);
                                } catch (Exception e) {}
                                return 1;
                            })
                        )
                    )
                )
        );
    }

    private static void sendConfigMenu(CommandSourceStack source) {
        GpsConfig c = GpsConfig.getInstance();
        MutableComponent text = Component.literal("\n§e=== Configurações do LumenGPS ===§r\n");
        text.append(makeToggleRow("Modo Inteligente", "intelligentMode", c.intelligentMode));
        text.append(makeToggleRow("Permitir Água", "allowWater", c.allowWater));
        text.append(makeToggleRow("Permitir Lava", "allowLava", c.allowLava));
        text.append(makeToggleRow("Waypoint de Morte", "enableDeathWaypoint", c.enableDeathWaypoint));
        text.append(makeToggleRow("Pilar de Luz", "enableLightPillar", c.enableLightPillar));
        text.append(makeToggleRow("Requer Bússola", "requireCompass", c.requireCompass));
        text.append(makeToggleRow("Mostrar HUD (Actionbar)", "showHud", c.showHud));
        text.append(makeToggleRow("Confirmar Sobrescrita", "confirmOverwrite", c.confirmOverwrite));
        source.sendSuccess(() -> text, false);
    }

    private static Component makeToggleRow(String label, String key, boolean current) {
        String state = current ? "§aLIGADO " : "§cDESLIGADO ";
        MutableComponent row = Component.literal("§7- " + label + ": " + state + " ");
        row.append(Component.literal("§e[Alternar]§r\n")
            .withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand("/gps config set " + key + " " + !current))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Clique para alternar")))
            ));
        return row;
    }

    private static void sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§b=== Ajuda do LumenGPS ===§r\n" +
                "/gps add <nome> - Salvar waypoint na posição atual\n" +
                "/gps addcord <nome> <x y z> - Salvar coordenadas específicas\n" +
                "/gps go <nome> - Navegar até waypoint\n" +
                "/gps clear - Limpar rota atual\n" +
                "/gps list - Listar waypoints\n" +
                "/gps config - Abrir menu de configurações"), false);
    }

    private static int addWaypoint(CommandSourceStack source, String name, BlockPos pos, String dim, String style) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            WaypointManager wm = WaypointManager.get(player.getUUID());
            if (GpsConfig.getInstance().confirmOverwrite && wm.getWaypoint(name).isPresent()) {
                MutableComponent msg = Component.literal(PREFIX + "Waypoint '" + name + "' já existe. Deseja sobrescrever? ");
                
                String cmdOk = String.format("/gps add_overwrite \"%s\" %d %d %d \"%s\" %s",
                        name.replace("\"", "\\\""), pos.getX(), pos.getY(), pos.getZ(), dim, style);
                String cmdCancel = String.format("/gps add_overwrite_cancel \"%s\"",
                        name.replace("\"", "\\\""));
                
                msg.append(Component.literal("§a[Sim]§r")
                    .withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand(cmdOk))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Sobrescrever waypoint existente")))));
                
                msg.append(Component.literal(" §c[Cancelar]§r")
                    .withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand(cmdCancel))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Cancelar sobrescrita")))));
                
                source.sendSuccess(() -> msg, false);
            } else {
                addWaypointDirectly(source, name, pos, dim, style);
            }
        } catch (Exception e) {}
        return 1;
    }

    private static void addWaypointDirectly(CommandSourceStack source, String name, BlockPos pos, String dim, String style) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            WaypointManager.get(player.getUUID()).add(name, pos, dim, style);
            source.sendSuccess(() -> Component.literal(PREFIX + "Waypoint '" + name + "' salvo em " + pos.toShortString()), false);
        } catch (Exception e) {}
    }

    private static int navigateTo(CommandSourceStack source, String name, String scope) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            UUID pid = player.getUUID();
            
            WaypointManager wm = WaypointManager.get(pid);
            ServerWaypointManager swm = ServerWaypointManager.getInstance();
            
            Optional<Waypoint> personalOpt = wm.getWaypoint(name);
            Optional<Waypoint> serverOpt = swm.getWaypoint(name);
            
            Optional<Waypoint> targetOpt = Optional.empty();
            
            if (scope == null) {
                if (personalOpt.isPresent() && serverOpt.isPresent()) {
                    // Conflict found: both exist
                    MutableComponent msg = Component.literal(PREFIX + "Existem dois waypoints com o nome '" + name + "'. Qual deseja seguir? ");
                    
                    String cmdPersonal = String.format("/gps go \"%s\" personal", name.replace("\"", "\\\""));
                    String cmdServer = String.format("/gps go \"%s\" server", name.replace("\"", "\\\""));
                    
                    msg.append(Component.literal("§a[Pessoal]§r")
                        .withStyle(s -> s
                            .withClickEvent(new ClickEvent.RunCommand(cmdPersonal))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Seguir waypoint pessoal")))));
                    
                    msg.append(Component.literal(" §d[Servidor]§r")
                        .withStyle(s -> s
                            .withClickEvent(new ClickEvent.RunCommand(cmdServer))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Seguir waypoint do servidor")))));
                    
                    source.sendSuccess(() -> msg, false);
                    return 1;
                } else if (personalOpt.isPresent()) {
                    targetOpt = personalOpt;
                } else {
                    targetOpt = serverOpt;
                }
            } else if ("personal".equalsIgnoreCase(scope)) {
                targetOpt = personalOpt;
            } else if ("server".equalsIgnoreCase(scope)) {
                targetOpt = serverOpt;
            }
            
            if (targetOpt.isEmpty()) {
                source.sendFailure(Component.literal(PREFIX + "Waypoint '" + name + "' não encontrado."));
                return 0;
            }

            Waypoint wp = targetOpt.get();
            if (!wp.dimension().equals(player.level().dimension().identifier().toString())) {
                source.sendFailure(Component.literal(PREFIX + "Waypoint está em uma dimensão diferente (" + formatDim(wp.dimension()) + ")."));
                return 0;
            }

            source.sendSuccess(() -> Component.literal(PREFIX + "Calculando rota..."), false);
            
            Pathfinder.computeAsync((ServerLevel) player.level(), player.blockPosition(), wp.pos(), false, result -> {
                if (result.points().isEmpty()) {
                    source.sendFailure(Component.literal(PREFIX + "Não foi possível encontrar um caminho."));
                } else {
                    ServerGpsManager.getInstance().setRoute(pid, result.points(), name, result.points().get(result.points().size() - 1), false, wp.style());
                    source.sendSuccess(() -> Component.literal(PREFIX + "Rota calculada! Siga as partículas."), false);
                }
            });

        } catch (Exception e) {}
        return 1;
    }

    private static int shareWaypoint(CommandSourceStack source, String name, String scope) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            
            WaypointManager wm = WaypointManager.get(player.getUUID());
            ServerWaypointManager swm = ServerWaypointManager.getInstance();
            
            Optional<Waypoint> personalOpt = wm.getWaypoint(name);
            Optional<Waypoint> serverOpt = swm.getWaypoint(name);
            
            Optional<Waypoint> targetOpt = Optional.empty();
            
            if (scope == null) {
                if (personalOpt.isPresent() && serverOpt.isPresent()) {
                    // Conflict found: both exist
                    MutableComponent msg = Component.literal(PREFIX + "Existem dois waypoints com o nome '" + name + "'. Qual deseja compartilhar? ");
                    
                    String cmdPersonal = String.format("/gps share \"%s\" personal", name.replace("\"", "\\\""));
                    String cmdServer = String.format("/gps share \"%s\" server", name.replace("\"", "\\\""));
                    
                    msg.append(Component.literal("§a[Pessoal]§r")
                        .withStyle(s -> s
                            .withClickEvent(new ClickEvent.RunCommand(cmdPersonal))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Compartilhar waypoint pessoal")))));
                    
                    msg.append(Component.literal(" §d[Servidor]§r")
                        .withStyle(s -> s
                            .withClickEvent(new ClickEvent.RunCommand(cmdServer))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Compartilhar waypoint do servidor")))));
                    
                    source.sendSuccess(() -> msg, false);
                    return 1;
                } else if (personalOpt.isPresent()) {
                    targetOpt = personalOpt;
                } else {
                    targetOpt = serverOpt;
                }
            } else if ("personal".equalsIgnoreCase(scope)) {
                targetOpt = personalOpt;
            } else if ("server".equalsIgnoreCase(scope)) {
                targetOpt = serverOpt;
            }
            
            if (targetOpt.isEmpty()) {
                source.sendFailure(Component.literal(PREFIX + "Waypoint '" + name + "' não encontrado."));
                return 0;
            }
            
            Waypoint wp = targetOpt.get();
            String playerName = player.getScoreboardName();
            
            MutableComponent msg = Component.literal(PREFIX + playerName + " compartilhou o Waypoint '§e" + name + "§r' em " + 
                                                   wp.pos().toShortString() + " na dimensão " + formatDim(wp.dimension()) + " ");
            
            // Build the command that other players will click to add this waypoint.
            // name and dim use StringArgumentType.string(), so they need to be quoted.
            // style uses StringArgumentType.word(), so no quotes needed.
            String cmd = String.format("/gps add_overwrite \"%s\" %d %d %d \"%s\" %s",
                    name.replace("\"", "\\\""), // escape any quotes in the name
                    wp.pos().getX(), wp.pos().getY(), wp.pos().getZ(),
                    wp.dimension(), wp.style());
                    
            msg.append(Component.literal("§b[Adicionar]§r")
                .withStyle(s -> s
                    .withClickEvent(new ClickEvent.RunCommand(cmd))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Clique para adicionar este waypoint ao seu GPS")))));
                    
            source.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        } catch (Exception e) {}
        return 1;
    }

    private static int addServerWaypoint(CommandSourceStack source, String name, BlockPos pos, String dim, String style) {
        try {
            ServerWaypointManager swm = ServerWaypointManager.getInstance();
            if (GpsConfig.getInstance().confirmOverwrite && swm.getWaypoint(name).isPresent()) {
                MutableComponent msg = Component.literal(PREFIX + "Waypoint do servidor '" + name + "' já existe. Deseja sobrescrever? ");
                
                String cmdOk = String.format("/gps server add_overwrite \"%s\" %d %d %d \"%s\" %s",
                        name.replace("\"", "\\\""), pos.getX(), pos.getY(), pos.getZ(), dim, style);
                String cmdCancel = String.format("/gps server add_overwrite_cancel \"%s\"",
                        name.replace("\"", "\\\""));
                
                msg.append(Component.literal("§a[Sim]§r")
                    .withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand(cmdOk))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Sobrescrever waypoint do servidor existente")))));
                
                msg.append(Component.literal(" §c[Cancelar]§r")
                    .withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand(cmdCancel))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Cancelar sobrescrita")))));
                
                source.sendSuccess(() -> msg, false);
            } else {
                addServerWaypointDirectly(source, name, pos, dim, style);
            }
        } catch (Exception e) {}
        return 1;
    }

    private static void addServerWaypointDirectly(CommandSourceStack source, String name, BlockPos pos, String dim, String style) {
        try {
            ServerWaypointManager.getInstance().add(name, pos, dim, style);
            source.sendSuccess(() -> Component.literal(PREFIX + "Waypoint do servidor '" + name + "' salvo em " + pos.toShortString()), false);
        } catch (Exception e) {}
    }

    private static boolean isOp(CommandSourceStack source) {
        return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    private static String formatDim(String dim) {
        return dim.contains(":") ? dim.split(":")[1] : dim;
    }
}
