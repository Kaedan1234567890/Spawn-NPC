package com.chillzone.npcs;

import java.util.UUID;

public final class NpcRecord {
    public String id;
    public String displayName = "NPC";
    public String nameStyle = "gold";
    public String dimension = "minecraft:overworld";
    public double x;
    public double y;
    public double z;
    public float yaw;
    public boolean lookAtPlayers = true;
    public boolean enabled = true;

    // NPCs can have BOTH a command action and a message action.
    public String commandAction = "";
    public String messageAction = "";

    // Legacy 0.1.x fields are kept only so old npcs.json files migrate automatically.
    public String preset = "custom";
    public String actionType = NpcActionType.NONE.name();
    public String action = "";

    public String entityUuid;

    public UUID entityUuid() {
        try {
            return entityUuid == null || entityUuid.isBlank() ? null : UUID.fromString(entityUuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean migrateLegacyAction() {
        if (action == null || action.isBlank()) return false;
        if ((commandAction != null && !commandAction.isBlank()) || (messageAction != null && !messageAction.isBlank())) return false;

        try {
            NpcActionType type = NpcActionType.valueOf(actionType);
            if (type == NpcActionType.COMMAND) commandAction = action;
            if (type == NpcActionType.MESSAGE) messageAction = action;
        } catch (Exception ignored) {
            return false;
        }
        return true;
    }
}
