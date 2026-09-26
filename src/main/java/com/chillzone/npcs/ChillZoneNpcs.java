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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class ChillZoneNpcs implements ModInitializer {
    private static final NpcStore STORE = new NpcStore();
    private static final NpcManager MANAGER = new NpcManager(STORE);
    private static int tickCounter;

    private static final SuggestionProvider<CommandSourceStack> PRESETS = (ctx, builder) -> {
        String typed = builder.getRemainingLowerCase();
        for (NpcPreset preset : NpcPreset.values()) {
            if (preset.id().startsWith(typed)) builder.suggest(preset.id());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> NPC_IDS = (ctx, builder) -> {
        String typed = builder.getRemainingLowerCase();
        for (String id : STORE.ids()) if (id.startsWith(typed)) builder.suggest(id);
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

            // Remembered/offline players are also offered as completions. This is deliberately
            // permissive so custom NPC commands are not limited to currently-online names.
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
            MANAGER.restoreAll(server);
            tickCounter = 0;
            System.out.println("[ChillZoneNPCs] Loaded " + STORE.ids().size() + " persistent NPC(s).");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> STORE.save());

        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            STORE.rememberPlayer(listener.player.getGameProfile().name());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // 10 times/second is smooth enough for horizontal look tracking without needless work.
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
        switch (record.actionType()) {
            case MESSAGE -> {
                if (!record.action.isBlank()) player.sendSystemMessage(Component.literal(record.action));
            }
            case COMMAND -> {
                if (record.action.isBlank()) return;
                // Run as the player who clicked. This makes player-facing commands such as
                // /shop, /homes and /rtp behave naturally. Vanilla permission checks still apply.
                // Selectors such as @p are supported by the stored command itself.
                player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), record.action);
            }
            case NONE -> { }
        }
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = Commands.literal("npc").requires(Permissions::canAdmin);

            root.then(Commands.literal("create")
                    .then(Commands.argument("preset", StringArgumentType.word()).suggests(PRESETS)
                            .then(Commands.argument("id", StringArgumentType.word())
                                    .executes(ctx -> create(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "preset"),
                                            StringArgumentType.getString(ctx, "id"))))));

            root.then(Commands.literal("name")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                    .executes(ctx -> rename(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "id"),
                                            StringArgumentType.getString(ctx, "name"))))));

            root.then(Commands.literal("action")
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(NPC_IDS)
                            .then(Commands.literal("command")
                                    .then(Commands.argument("command", StringArgumentType.greedyString()).suggests(COMMANDS)
                                            .executes(ctx -> action(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "id"),
                                                    NpcActionType.COMMAND,
                                                    StringArgumentType.getString(ctx, "command")))))
                            .then(Commands.literal("message")
                                    .then(Commands.argument("message", StringArgumentType.greedyString())
                                            .executes(ctx -> action(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "id"),
                                                    NpcActionType.MESSAGE,
                                                    StringArgumentType.getString(ctx, "message")))))
                            .then(Commands.literal("clear")
                                    .executes(ctx -> action(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "id"),
                                            NpcActionType.NONE, "")))));

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

    private static int create(CommandSourceStack source, String presetRaw, String idRaw) {
        NpcPreset preset;
        try {
            preset = NpcPreset.fromId(presetRaw);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown preset. Use shop, homes, rtp, baltop, help, or custom."));
            return 0;
        }

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
        record.preset = preset.id();
        record.displayName = preset.displayName();
        record.dimension = level.dimension().identifier().toString();
        record.x = pos.x;
        record.y = pos.y;
        record.z = pos.z;
        record.yaw = yaw;
        record.actionType = preset.actionType().name();
        record.action = preset.action();
        STORE.put(record);
        MANAGER.ensureSpawned(source.getServer(), record);
        source.sendSuccess(() -> Component.literal("Created NPC '" + id + "' (" + preset.id() + ")."), false);
        return 1;
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

    private static int action(CommandSourceStack source, String id, NpcActionType type, String value) {
        NpcRecord record = require(source, id);
        if (record == null) return 0;
        record.actionType = type.name();
        record.action = value.startsWith("/") ? value.substring(1) : value;
        STORE.put(record);
        source.sendSuccess(() -> Component.literal("Updated action for NPC '" + record.id + "'."), false);
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
            Entity entity = MANAGER.findLevel(source.getServer(), record.dimension).getEntity(record.entityUuid());
            if (entity != null) record.yaw = entity.getYRot();
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
                "NPC " + record.id + " | name=" + record.displayName
                        + " | preset=" + record.preset
                        + " | enabled=" + record.enabled
                        + " | look=" + record.lookAtPlayers
                        + " | action=" + record.actionType + ":" + record.action), false);
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
