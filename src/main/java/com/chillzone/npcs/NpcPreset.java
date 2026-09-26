package com.chillzone.npcs;

import java.util.Locale;

public enum NpcPreset {
    SHOP("shop", "Shard Shop", NpcActionType.COMMAND, "shop"),
    HOMES("homes", "Homes", NpcActionType.COMMAND, "homes"),
    RTP("rtp", "Random Teleport", NpcActionType.COMMAND, "rtp"),
    BALTOP("baltop", "Richest Players", NpcActionType.COMMAND, "baltop"),
    HELP("help", "Server Help", NpcActionType.MESSAGE, "Right-click NPCs around spawn to access server features."),
    CUSTOM("custom", "NPC", NpcActionType.NONE, "");

    private final String id;
    private final String displayName;
    private final NpcActionType actionType;
    private final String action;

    NpcPreset(String id, String displayName, NpcActionType actionType, String action) {
        this.id = id;
        this.displayName = displayName;
        this.actionType = actionType;
        this.action = action;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public NpcActionType actionType() { return actionType; }
    public String action() { return action; }

    public static NpcPreset fromId(String raw) {
        String id = raw.toLowerCase(Locale.ROOT);
        for (NpcPreset preset : values()) if (preset.id.equals(id)) return preset;
        throw new IllegalArgumentException("Unknown NPC preset: " + raw);
    }
}
