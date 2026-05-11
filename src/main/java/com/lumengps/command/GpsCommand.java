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
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
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
                // /gps <name> — shortcut for /gps go <name>
                .then(ClientCommands.argument("shortcut_name", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        // Only suggest waypoint names, not sub-command keywords
                        List<String> subCmds = List.of("help", "add", "addpos", "go", "remove", "remove_confirm", "share", "clear", "list");
                        String rem = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                        WaypointManager.getInstance().listNames().stream()
                            .filter(n -> !subCmds.contains(n.toLowerCase(java.util.Locale.ROOT)))
                            .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(rem))
                            .forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> navigateTo(ctx.getSource(), StringArgumentType.getString(ctx, "shortcut_name"), "glow")))
                // /gps config
                .then(ClientCommands.literal("config")
                    .executes(ctx -> {
                        Minecraft client = Minecraft.getInstance();
                        client.execute(() -> client.setScreen(new com.lumengps.gui.GpsConfigScreen(null)));
                        return 1;
                    }))

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
                            String dimension = source.getPlayer().level().dimension().identifier().toString();

                            WaypointManager.getInstance().add(name, pos, dimension, "glow");
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

                                String dimension = source.getPlayer().level().dimension().identifier().toString();

                                if (!List.of("glow", "fire", "soul", "end", "emerald").contains(style)) {
                                    source.sendError(Component.literal(PREFIX)
                                            .append(Component.translatable("lumengps.command.invalid_style", style)));
                                    return 0;
                                }

                                WaypointManager.getInstance().add(name, pos, dimension, style);
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
                                        String dimension = source.getPlayer().level().dimension().identifier().toString();

                                        WaypointManager.getInstance().add(name, pos, dimension, "glow");
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
                                            String dimension = source.getPlayer().level().dimension().identifier().toString();

                                            if (!List.of("glow", "fire", "soul", "end", "emerald").contains(style)) {
                                                source.sendError(Component.literal(PREFIX)
                                                        .append(Component.translatable("lumengps.command.invalid_style", style)));
                                                return 0;
                                            }

                                            WaypointManager.getInstance().add(name, pos, dimension, style);
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
                        .executes(ctx -> navigateTo(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "glow"))))

                // /gps remove <name> — shows a confirmation prompt
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

                            if (WaypointManager.getInstance().get(name).isEmpty()) {
                                source.sendError(Component.literal(PREFIX)
                                        .append(Component.translatable("lumengps.command.no_waypoint_found", name)));
                                return 0;
                            }

                            // Show confirmation prompt instead of deleting immediately
                            source.sendFeedback(buildRemoveConfirmation(name));
                            return 1;
                        })))

                // /gps remove_confirm <name> — internal command, called by the [✓ Yes] button
                .then(ClientCommands.literal("remove_confirm")
                    .then(ClientCommands.argument("name", StringArgumentType.word())
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

                // /gps share <name> — posts waypoint info in public chat
                .then(ClientCommands.literal("share")
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

                            BlockPos pos = waypointOpt.get().pos();
                            String style = waypointOpt.get().style();
                            // Send a public chat message that ALL players see (must be plain text, no § or server kicks)
                            String plainMsg = String.format("[LumenGPS] Shared Waypoint: '%s' at %d, %d, %d (Style: %s)",
                                    name, pos.getX(), pos.getY(), pos.getZ(), style);
                            source.getPlayer().connection.sendChat(plainMsg);
                            
                            // Send private confirmation
                            source.sendFeedback(Component.literal(PREFIX)
                                    .append(Component.translatable("lumengps.command.share.feedback", name)));
                            return 1;
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
                            String currentDim = source.getPlayer().level().dimension().identifier().toString();
                            source.sendFeedback(Component.literal(PREFIX)
                                    .append(Component.translatable("lumengps.command.saved_waypoints_list", String.valueOf(names.size()))));
                            names.forEach(n -> source.sendFeedback(buildWaypointListEntry(n, currentDim)));
                        }
                        return 1;
                    }))
        );
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static int goCommand(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        String style = GpsRenderer.getInstance().getActiveStyle();
        if (style == null) style = "glow";

        // Check Compass requirement
        if (com.lumengps.data.GpsConfig.getInstance().requireCompass) {
            boolean holdingCompass = source.getPlayer().getMainHandItem().is(net.minecraft.world.item.Items.COMPASS) ||
                                     source.getPlayer().getOffhandItem().is(net.minecraft.world.item.Items.COMPASS);
            if (!holdingCompass) {
                source.sendError(Component.literal(PREFIX)
                        .append(Component.translatable("lumengps.command.compass_required")));
                return 0;
            }
        }

        return navigateTo(source, name, style);
    }

    /**
     * Shared logic to navigate to a waypoint by name. Used by both
     * {@code /gps go <name>} and the {@code /gps <name>} shortcut.
     */
    private static int navigateTo(FabricClientCommandSource source, String name, String style) {
        Optional<Waypoint> waypointOpt = WaypointManager.getInstance().get(name);

        if (waypointOpt.isEmpty()) {
            source.sendError(Component.literal(PREFIX)
                    .append(Component.translatable("lumengps.command.no_waypoint_found", name)));
            return 0;
        }

        BlockPos goal  = waypointOpt.get().pos();
        String   targetDim = waypointOpt.get().dimension();
        BlockPos start = BlockPos.containing(source.getPlayer().position());
        Level    world = source.getPlayer().level();
        String   currentDim = world.dimension().identifier().toString();

        if (!currentDim.equals(targetDim)) {
            source.sendError(Component.literal(PREFIX)
                    .append(Component.translatable("lumengps.command.dimension_mismatch", name, formatDim(targetDim))));
            return 0;
        }

        // --- Elytra Detection ---
        // If the player is wearing an Elytra, we enable flight-mode pathfinding.
        boolean isElytraMode = source.getPlayer().getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.ELYTRA);
        String finalStyle = (isElytraMode && style.equals("glow")) ? "end" : style;

        source.sendFeedback(Component.literal(PREFIX)
                .append(Component.translatable("lumengps.command.calculating_route", name)));

        Pathfinder.computeAsync(world, start, goal, isElytraMode, (PathResult result) -> {
            if (result.isEmpty()) {
                source.sendError(Component.literal(PREFIX)
                        .append(Component.translatable("lumengps.command.route_not_found", name)));
                return;
            }
            GpsRenderer.getInstance().setRoute(result.points(), name, goal.getCenter(), isElytraMode, finalStyle);
            if (result.isFallback()) {
                source.sendFeedback(Component.literal(PREFIX)
                        .append(Component.translatable("lumengps.command.route_blocked_fallback", name, String.valueOf(result.points().size()))));
            } else {
                source.sendFeedback(Component.literal(PREFIX)
                        .append(Component.translatable("lumengps.command.route_found", String.valueOf(result.points().size()))));
            }
        });
        return 1;
    }

    /**
     * Builds a single interactive chat line for a waypoint in /gps list.
     * Format:  §e• name§r (Dim)  [▶ Go]  [📤 Share]  [✗ Remove]
     */
    private static MutableComponent buildWaypointListEntry(String name, String currentDim) {
        Optional<Waypoint> waypointOpt = WaypointManager.getInstance().get(name);
        if (waypointOpt.isEmpty()) return Component.literal("  §e• §f" + name);

        Waypoint wp = waypointOpt.get();
        MutableComponent entry = Component.literal("  §e• §f" + name + "§r ");

        // Add dimension tag if different
        if (!wp.dimension().equals(currentDim)) {
            entry.append("§7[" + formatDim(wp.dimension()) + "]§r ");
        }

        // [▶ Go] — green
        MutableComponent goBtn = Component.literal("§a[▶ Go]§r")
                .withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand("/gps go " + name))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("lumengps.command.list.go_hint", name))));

        // [📤 Share] — aqua
        MutableComponent shareBtn = Component.literal(" §b[📤 Share]§r")
                .withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand("/gps share " + name))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("lumengps.command.list.share_hint", name))));

        // [✗ Remove] — red, now triggers confirmation
        MutableComponent removeBtn = Component.literal(" §c[✗ Remove]§r")
                .withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand("/gps remove " + name))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("lumengps.command.list.remove_hint", name))));

        return entry.append(goBtn).append(shareBtn).append(removeBtn);
    }

    /**
     * Builds the inline confirmation prompt shown when clicking [✗ Remove].
     * Format:  §b[LumenGPS]§r Remove 'name'? [✓ Yes] [✗ Cancel]
     */
    private static MutableComponent buildRemoveConfirmation(String name) {
        MutableComponent msg = Component.literal(PREFIX)
                .append(Component.translatable("lumengps.command.remove_confirm_prompt", name))
                .append(Component.literal("  "));

        // [✓ Yes] — green, actually deletes
        MutableComponent yes = Component.literal("§a[✓ Yes]§r")
                .withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand("/gps remove_confirm " + name))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("lumengps.command.remove_confirm_yes_hint", name))));

        // [✗ Cancel] — grey, does nothing (suggest harmless no-op)
        MutableComponent cancel = Component.literal(" §7[✗ Cancel]§r")
                .withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand("/gps list"))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("lumengps.command.remove_confirm_cancel_hint"))));

        return msg.append(yes).append(cancel);
    }

    /**
     * Builds the private feedback message sent to the sharer after /gps share.
     * Contains a clickable [+ Add Waypoint] button that pre-fills /gps addpos in chat.
     */
    private static MutableComponent buildShareFeedback(String name, BlockPos pos, String dimension, String style) {
        String addCmd = "/gps addpos " + name + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + style;

        MutableComponent addBtn = Component.literal("§a[+ Add Waypoint]§r")
                .withStyle(s -> s
                        // SuggestCommand fills the command bar so the user can review before running
                        .withClickEvent(new ClickEvent.SuggestCommand(addCmd))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("lumengps.command.share.add_hint", name))));

        return Component.literal(PREFIX)
                .append(Component.translatable("lumengps.command.share.feedback", name))
                .append(" ")
                .append(addBtn);
    }

    private static String formatDim(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End";
            default -> dimension.replace("minecraft:", "");
        };
    }

    private static void sendHelp(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("lumengps.command.help.header"));
        source.sendFeedback(Component.translatable("lumengps.command.help.add"));
        source.sendFeedback(Component.translatable("lumengps.command.help.addpos"));
        source.sendFeedback(Component.translatable("lumengps.command.help.go"));
        source.sendFeedback(Component.translatable("lumengps.command.help.list"));
        source.sendFeedback(Component.translatable("lumengps.command.help.share"));
        source.sendFeedback(Component.translatable("lumengps.command.help.remove"));
        source.sendFeedback(Component.translatable("lumengps.command.help.clear"));
    }

    private static String formatPos(BlockPos pos) {
        return "§7(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")§r";
    }
}
