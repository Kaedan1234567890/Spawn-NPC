package com.chillzone.npcs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NpcStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file = FabricLoader.getInstance().getConfigDir()
            .resolve("chillzone-npcs").resolve("npcs.json");
    private State state = new State();

    public static final class State {
        public Map<String, NpcRecord> npcs = new LinkedHashMap<>();
        public Set<String> knownPlayers = new LinkedHashSet<>();
    }

    public synchronized void load() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(file)) {
                State loaded = GSON.fromJson(reader, State.class);
                if (loaded != null) state = loaded;
            }
            if (state.npcs == null) state.npcs = new LinkedHashMap<>();
            if (state.knownPlayers == null) state.knownPlayers = new LinkedHashSet<>();

            boolean migrated = false;
            for (NpcRecord record : state.npcs.values()) {
                if (record.commandAction == null) record.commandAction = "";
                if (record.messageAction == null) record.messageAction = "";
                if (record.nameStyle == null || record.nameStyle.isBlank()) record.nameStyle = "gold";
                if (record.nameFormat == null || record.nameFormat.isBlank()) record.nameFormat = "bold";
                if (record.skinPlayer == null) record.skinPlayer = "";
                if (record.messageColor == null || record.messageColor.isBlank()) record.messageColor = "white";
                if (record.messageFormat == null || record.messageFormat.isBlank()) record.messageFormat = "default";
                migrated |= record.migrateLegacyAction();
            }
            if (migrated) save();
        } catch (Exception e) {
            System.err.println("[ChillZoneNPCs] Failed to load NPC data: " + e.getMessage());
            state = new State();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(state, writer);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[ChillZoneNPCs] Failed to save NPC data: " + e.getMessage());
        }
    }

    public synchronized NpcRecord get(String id) {
        return state.npcs.get(normalize(id));
    }

    public synchronized void put(NpcRecord record) {
        record.id = normalize(record.id);
        state.npcs.put(record.id, record);
        save();
    }

    public synchronized NpcRecord remove(String id) {
        NpcRecord removed = state.npcs.remove(normalize(id));
        save();
        return removed;
    }

    public synchronized List<NpcRecord> all() {
        return new ArrayList<>(state.npcs.values());
    }

    public synchronized List<String> ids() {
        return new ArrayList<>(state.npcs.keySet());
    }

    public synchronized void rememberPlayer(String name) {
        if (name != null && !name.isBlank()) {
            boolean exists = state.knownPlayers.stream().anyMatch(n -> n.equalsIgnoreCase(name));
            if (!exists) {
                state.knownPlayers.add(name);
                save();
            }
        }
    }

    public synchronized List<String> knownPlayers() {
        return new ArrayList<>(state.knownPlayers);
    }

    public static String normalize(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }
}
