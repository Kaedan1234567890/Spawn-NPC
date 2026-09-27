package com.chillzone.npcs;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class ChillZoneNpcs implements ModInitializer {
    private static final NpcStore STORE = new NpcStore();
    private static final NpcManager MANAGER = new NpcManager(STORE);
    private static int tickCounter;
    private static int startupTicks;
    private static boolean runtimeReady;

    private static final SuggestionProvider<CommandSourceStack> NPC_IDS = (ctx, builder) -> {
        String typed = builder.getRemainingLowerCase();
        for (String id : STORE.ids()) if (id.startsWith(typed)) builder.suggest(id);
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> STYLES = (ctx, builder) -> {
        String typed = builder.getRemainingLowerCase();
        for (NpcNameStyle style : NpcNameStyle.values()) {
            if (style.id().startsWith(typed)) builder.suggest(style.id());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> COLORS = (ctx, builder) -> {
        String typed = builder.getRemainingLowerCase();
        for (NpcColor color : NpcColor.values()) {
            if (color.id().startsWith(typed)) builder.suggest(color.id());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> TEXT_FORMATS = (ctx, builder) -> {
        String typed = builder.getRemainingLowerCase();
        for (NpcTextFormat format : NpcTextFormat.values()) {
            if (format.id().startsWith(typed)) builder.suggest(format.id());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> PLAYER_NAMES = (ctx, builder) -> {
        String typed = builder.getRemainingLowerCase();
        for (String name : STORE.knownPlayers()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(name);
        }
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            String name = player.getGameProfile().name();
            if (name.toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(name);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> COMMANDS = (ctx, builder) -> {
        String input = ctx.getInput();
        int marker = input.lastIndexOf(" command ");
        String partial = marker >= 0 ? input.substring(marker + 9) : builder.getRemaining();
        int argumentStart = marker >= 0 ? marker + 9 : builder.getStart();

        try {
            var dispatcher = ctx.getSource().getServer().getCommands().getDispatcher();
            ParseResults<CommandSourceStack> parse = dispatcher.parse(partial, ctx.getSource());
            var nested = dispatcher.getCompletionSuggestions(parse).join();
            int replaceAt = argumentStart + nested.getRange().getStart();
            SuggestionsBuilder shifted = builder.createOffset(replaceAt);
            for (Suggestion suggestion : nested.getList()) shifted.suggest(suggestion.getText());

            String token = partial.substring(Math.max(0, nested.getRange().getStart()));
            String lower = token.toLowerCase(Locale.ROOT);
            for (String name : STORE.knownPlayers()) {
                if (name.toLowerCase(Locale.ROOT).startsWith(lower)) shifted.suggest(name);
            }
            return shifted.buildFuture();
        } catch (Exception ignored) {
            return builder.buildFuture();
        }
    };

    @Override
    public void onInitialize() {
        STORE.load();
        registerCommands();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Wait briefly before restoring so vanilla has time to load any persisted mannequin
            // entity with the UUID from our JSON. This is part of duplicate prevention.
            tickCounter = 0;
            startupTicks = 0;
            runtimeReady = false;
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            runtimeReady = false;
            // Our JSON is authoritative. Remove live mannequins before the world finishes
            // shutting down so they are not also persisted as a second independent copy.
            MANAGER.removeAllEntities(server);
            STORE.save();
        });

        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            STORE.rememberPlayer(listener.player.getGameProfile().name());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!runtimeReady) {
                if (++startupTicks >= 40) {
                    MANAGER.restoreAll(server);
                    runtimeReady = true;
                    tickCounter = 0;
                    System.out.println("[ChillZoneNPCs] Loaded " + STORE.ids().size() + " persistent NPC(s).");
                }
                return;
            }

            if (++tickCounter >= 2) {
                tickCounter = 0;
                MANAGER.tick(server);
            }
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            NpcRecord record = MANAGER.byEntity(entity);
            if (record == null) return InteractionResult.PASS;
            if (!record.enabled) return InteractionResult.SUCCESS;

            activate(serverPlayer, record);
            return InteractionResult.SUCCESS;
        });

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (MANAGER.byEntity(entity) != null) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });
    }

    private static void activate(ServerPlayer player, NpcRecord record) {
        if (record.commandAction != null && !record.commandAction.isBlank()) {
            player.level().getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack(), record.commandAction);
        }
        if (record.messageAction != null && !record.messageAction.isBlank()) {
            player.sendSystemMessage(NpcTextFormat.fromId(record.messageFormat).apply(
                    Component.literal(record.messageAction).withStyle(NpcColor.fromId(record.messageColor).formatting())));
        }
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = Commands.literal("npc").requires(Permissions::canAdmin);

            // Everything is custom now. Example: /npc create shop_main
            root.then(Commands.literal("create")
                    .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));

            root.then(Commands.literal("name")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                    .executes(ctx -> rename(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "id"),
                                            StringArgumentType.getString(ctx, "name"))))));

            root.then(Commands.literal("style")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .then(Commands.argument("style", StringArgumentType.word()).suggests(STYLES)
                                    .executes(ctx -> style(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "id"),
                                            StringArgumentType.getString(ctx, "style"))))));

            root.then(Commands.literal("nameformat")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .then(Commands.argument("format", StringArgumentType.word()).suggests(TEXT_FORMATS)
                                    .executes(ctx -> nameFormat(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "id"),
                                            StringArgumentType.getString(ctx, "format"))))));

            // Command and message are independent. Setting one no longer deletes the other.
            root.then(Commands.literal("action")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .then(Commands.literal("command")
                                    .then(Commands.argument("command", StringArgumentType.greedyString()).suggests(COMMANDS)
                                            .executes(ctx -> commandAction(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "id"),
                                                    StringArgumentType.getString(ctx, "command")))))
                            .then(Commands.literal("message")
                                    .then(Commands.argument("message", StringArgumentType.greedyString())
                                            .executes(ctx -> messageAction(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "id"),
                                                    StringArgumentType.getString(ctx, "message")))))
                            .then(Commands.literal("messagecolor")
                                    .then(Commands.argument("color", StringArgumentType.word()).suggests(COLORS)
                                            .executes(ctx -> messageColor(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "id"),
                                                    StringArgumentType.getString(ctx, "color")))))
                            .then(Commands.literal("messageformat")
                                    .then(Commands.argument("format", StringArgumentType.word()).suggests(TEXT_FORMATS)
                                            .executes(ctx -> messageFormat(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "id"),
                                                    StringArgumentType.getString(ctx, "format")))))
                            .then(Commands.literal("clear")
                                    .then(Commands.literal("command")
                                            .executes(ctx -> clearAction(ctx.getSource(), StringArgumentType.getString(ctx, "id"), "command")))
                                    .then(Commands.literal("message")
                                            .executes(ctx -> clearAction(ctx.getSource(), StringArgumentType.getString(ctx, "id"), "message")))
                                    .then(Commands.literal("all")
                                            .executes(ctx -> clearAction(ctx.getSource(), StringArgumentType.getString(ctx, "id"), "all"))))));

            root.then(Commands.literal("skin")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .then(Commands.argument("player", StringArgumentType.word()).suggests(PLAYER_NAMES)
                                    .executes(ctx -> skin(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "id"),
                                            StringArgumentType.getString(ctx, "player"))))
                            .then(Commands.literal("clear")
                                    .executes(ctx -> skin(ctx.getSource(), StringArgumentType.getString(ctx, "id"), "")))));

            root.then(Commands.literal("enable")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .executes(ctx -> enabled(ctx.getSource(), StringArgumentType.getString(ctx, "id"), true))));
            root.then(Commands.literal("disable")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .executes(ctx -> enabled(ctx.getSource(), StringArgumentType.getString(ctx, "id"), false))));

            root.then(Commands.literal("look")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .then(Commands.literal("on").executes(ctx -> look(ctx.getSource(), StringArgumentType.getString(ctx, "id"), true)))
                            .then(Commands.literal("off").executes(ctx -> look(ctx.getSource(), StringArgumentType.getString(ctx, "id"), false)))));

            root.then(Commands.literal("tracking")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .then(Commands.literal("on").executes(ctx -> look(ctx.getSource(), StringArgumentType.getString(ctx, "id"), true)))
                            .then(Commands.literal("off").executes(ctx -> look(ctx.getSource(), StringArgumentType.getString(ctx, "id"), false)))));

            root.then(Commands.literal("move")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .executes(ctx -> moveHere(ctx.getSource(), StringArgumentType.getString(ctx, "id")))
                            .then(Commands.argument("pos", Vec3Argument.vec3())
                                    .executes(ctx -> move(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "id"),
                                            Vec3Argument.getVec3(ctx, "pos"))))));

            root.then(Commands.literal("remove")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));

            root.then(Commands.literal("refresh")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .executes(ctx -> refresh(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));

            root.then(Commands.literal("info")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));

            root.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())));

            dispatcher.register(root);
        });
    }

    private static int create(CommandSourceStack source, String idRaw) {
        String id = NpcStore.normalize(idRaw);
        if (STORE.get(id) != null) {
            source.sendFailure(Component.literal("An NPC with ID '" + id + "' already exists."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        float yaw = source.getEntity() instanceof ServerPlayer p ? p.getYRot() : 0.0F;

        NpcRecord record = new NpcRecord();
        record.id = id;
        record.displayName = humanize(id);
        record.dimension = level.dimension().identifier().toString();
        record.x = pos.x;
        record.y = pos.y;
        record.z = pos.z;
        record.yaw = yaw;
        record.preset = "custom";
        STORE.put(record);
        MANAGER.ensureSpawned(source.getServer(), record);
        source.sendSuccess(() -> Component.literal("Created custom NPC '" + id + "'."), false);
        return 1;
    }

    private static String humanize(String id) {
        String[] words = id.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) out.append(word.substring(1));
        }
        return out.isEmpty() ? "NPC" : out.toString();
    }

    private static int rename(CommandSourceStack source, String id, String name) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        record.displayName = name;
        STORE.put(record);
        MANAGER.refresh(source.getServer(), record);
        source.sendSuccess(() -> Component.literal("NPC '" + record.id + "' is now named '" + name + "'."), false);
        return 1;
    }

    private static int style(CommandSourceStack source, String id, String styleRaw) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        NpcNameStyle chosen = null;
        for (NpcNameStyle style : NpcNameStyle.values()) {
            if (style.id().equalsIgnoreCase(styleRaw)) chosen = style;
        }
        if (chosen == null) {
            source.sendFailure(Component.literal("Unknown style. Use default, gold, yellow, aqua, green, red, purple, gray, or white."));
            return 0;
        }
        record.nameStyle = chosen.id();
        STORE.put(record);
        MANAGER.refresh(source.getServer(), record);
        NpcNameStyle finalChosen = chosen;
        source.sendSuccess(() -> Component.literal("NPC '" + record.id + "' name style is now " + finalChosen.id() + "."), false);
        return 1;
    }

    private static int nameFormat(CommandSourceStack source, String id, String formatRaw) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        NpcTextFormat chosen = null;
        for (NpcTextFormat format : NpcTextFormat.values()) {
            if (format.id().equalsIgnoreCase(formatRaw)) chosen = format;
        }
        if (chosen == null) {
            source.sendFailure(Component.literal("Unknown format. Use default, bold, italic, underline, bold_italic, or bold_underline."));
            return 0;
        }
        record.nameFormat = chosen.id();
        STORE.put(record);
        MANAGER.refresh(source.getServer(), record);
        NpcTextFormat finalChosen = chosen;
        source.sendSuccess(() -> Component.literal("NPC '" + record.id + "' name format is now " + finalChosen.id() + "."), false);
        return 1;
    }

    private static int skin(CommandSourceStack source, String id, String playerName) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        record.skinPlayer = playerName == null ? "" : playerName.trim();
        STORE.put(record);
        MANAGER.refresh(source.getServer(), record);
        if (record.skinPlayer.isBlank()) {
            source.sendSuccess(() -> Component.literal("Cleared custom skin for NPC '" + record.id + "'."), false);
        } else {
            source.sendSuccess(() -> Component.literal("NPC '" + record.id + "' will use the skin for '" + record.skinPlayer + "'."), false);
        }
        return 1;
    }

    private static int messageColor(CommandSourceStack source, String id, String colorRaw) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        NpcColor chosen = null;
        for (NpcColor color : NpcColor.values()) {
            if (color.id().equalsIgnoreCase(colorRaw)) chosen = color;
        }
        if (chosen == null) {
            source.sendFailure(Component.literal("Unknown message color."));
            return 0;
        }
        record.messageColor = chosen.id();
        STORE.put(record);
        NpcColor finalChosen = chosen;
        source.sendSuccess(() -> Component.literal("NPC '" + record.id + "' message color is now " + finalChosen.id() + "."), false);
        return 1;
    }

    private static int messageFormat(CommandSourceStack source, String id, String formatRaw) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        NpcTextFormat chosen = null;
        for (NpcTextFormat format : NpcTextFormat.values()) {
            if (format.id().equalsIgnoreCase(formatRaw)) chosen = format;
        }
        if (chosen == null) {
            source.sendFailure(Component.literal("Unknown message format."));
            return 0;
        }
        record.messageFormat = chosen.id();
        STORE.put(record);
        NpcTextFormat finalChosen = chosen;
        source.sendSuccess(() -> Component.literal("NPC '" + record.id + "' message format is now " + finalChosen.id() + "."), false);
        return 1;
    }

    private static int commandAction(CommandSourceStack source, String id, String value) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        record.commandAction = value.startsWith("/") ? value.substring(1) : value;
        STORE.put(record);
        source.sendSuccess(() -> Component.literal("Updated command action for NPC '" + record.id + "'."), false);
        return 1;
    }

    private static int messageAction(CommandSourceStack source, String id, String value) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        record.messageAction = value;
        STORE.put(record);
        source.sendSuccess(() -> Component.literal("Updated message action for NPC '" + record.id + "'."), false);
        return 1;
    }

    private static int clearAction(CommandSourceStack source, String id, String what) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        if (what.equals("command") || what.equals("all")) record.commandAction = "";
        if (what.equals("message") || what.equals("all")) record.messageAction = "";
        STORE.put(record);
        source.sendSuccess(() -> Component.literal("Cleared " + what + " action(s) for NPC '" + record.id + "'."), false);
        return 1;
    }

    private static int enabled(CommandSourceStack source, String id, boolean enabled) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        record.enabled = enabled;
        STORE.put(record);
        source.sendSuccess(() -> Component.literal("NPC '" + record.id + "' interaction is now " + (enabled ? "enabled" : "disabled") + "."), false);
        return 1;
    }

    private static int look(CommandSourceStack source, String id, boolean on) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        record.lookAtPlayers = on;
        if (!on) {
            ServerLevel level = MANAGER.findLevel(source.getServer(), record.dimension);
            if (level != null && record.entityUuid() != null) {
                Entity entity = level.getEntity(record.entityUuid());
                if (entity != null) record.yaw = entity.getYRot();
            }
        }
        STORE.put(record);
        source.sendSuccess(() -> Component.literal("NPC '" + record.id + "' look tracking is " + (on ? "ON" : "OFF") + "."), false);
        return 1;
    }

    private static int moveHere(CommandSourceStack source, String id) {
        return move(source, id, source.getPosition());
    }

    private static int move(CommandSourceStack source, String id, Vec3 pos) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        MANAGER.removeEntity(source.getServer(), record);
        record.dimension = source.getLevel().dimension().identifier().toString();
        record.x = pos.x;
        record.y = pos.y;
        record.z = pos.z;
        if (source.getEntity() instanceof ServerPlayer p) record.yaw = p.getYRot();
        record.entityUuid = null;
        STORE.put(record);
        MANAGER.ensureSpawned(source.getServer(), record);
        source.sendSuccess(() -> Component.literal("Moved NPC '" + record.id + "'."), false);
        return 1;
    }

    private static int remove(CommandSourceStack source, String id) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        MANAGER.removeEntity(source.getServer(), record);
        STORE.remove(record.id);
        source.sendSuccess(() -> Component.literal("Removed NPC '" + record.id + "'."), false);
        return 1;
    }

    private static int refresh(CommandSourceStack source, String id) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        MANAGER.refresh(source.getServer(), record);
        source.sendSuccess(() -> Component.literal("Refreshed NPC '" + record.id + "'."), false);
        return 1;
    }

    private static int info(CommandSourceStack source, String id) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        source.sendSuccess(() -> Component.literal(
                "NPC " + record.id
                        + " | name=" + record.displayName
                        + " | style=" + record.nameStyle
                        + "/" + record.nameFormat
                        + " | skin=" + (record.skinPlayer == null || record.skinPlayer.isBlank() ? "default" : record.skinPlayer)
                        + " | enabled=" + record.enabled
                        + " | look=" + record.lookAtPlayers
                        + " | command=" + (record.commandAction == null || record.commandAction.isBlank() ? "none" : record.commandAction)
                        + " | message=" + (record.messageAction == null || record.messageAction.isBlank() ? "none" : record.messageAction)
                        + " | messageStyle=" + record.messageColor + "/" + record.messageFormat), false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        if (STORE.ids().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Chill Zone NPCs exist yet."), false);
        } else {
            source.sendSuccess(() -> Component.literal("NPCs: " + String.join(", ", STORE.ids())), false);
        }
        return STORE.ids().size();
    }

    private static NpcRecord require(CommandSourceStack source, String id) {
        NpcRecord record = STORE.get(id);
        if (record == null) source.sendFailure(Component.literal("No NPC exists with ID '" + id + "'."));
        return record;
    }
}
