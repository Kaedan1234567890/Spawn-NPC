package com.chillzone.npcs;

import java.util.UUID;

public final class NpcRecord {
    public String id;
    public String preset = "custom";
    public String displayName = "NPC";
    public String dimension = "minecraft:overworld";
    public double x;
    public double y;
    public double z;
    public float yaw;
    public boolean lookAtPlayers = true;
    public boolean enabled = true;
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

    public NpcActionType actionType() {
        try {
            return NpcActionType.valueOf(actionType);
        } catch (Exception ignored) {
            return NpcActionType.NONE;
        }
    }
}
