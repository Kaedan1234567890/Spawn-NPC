package com.chillzone.npcs;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.UUID;

public final class NpcManager {
    private static final double LOOK_RANGE = 10.0;
    private final NpcStore store;

    public NpcManager(NpcStore store) {
        this.store = store;
    }

    public void restoreAll(MinecraftServer server) {
        for (NpcRecord record : store.all()) ensureSpawned(server, record);
    }

    public Mannequin ensureSpawned(MinecraftServer server, NpcRecord record) {
        ServerLevel level = findLevel(server, record.dimension);
        if (level == null) return null;

        UUID uuid = record.entityUuid();
        if (uuid != null) {
            Entity existing = level.getEntity(uuid);
            if (existing instanceof Mannequin mannequin && !existing.isRemoved()) {
                applyProperties(mannequin, record);
                return mannequin;
            }
        }

        Mannequin mannequin = new Mannequin(EntityTypes.MANNEQUIN, level);
        mannequin.setPos(record.x, record.y, record.z);
        mannequin.setYRot(record.yaw);
        mannequin.setXRot(0.0F);
        applyProperties(mannequin, record);
        level.addFreshEntity(mannequin);
        record.entityUuid = mannequin.getUUID().toString();
        store.put(record);
        return mannequin;
    }

    public void applyProperties(Mannequin mannequin, NpcRecord record) {
        mannequin.setCustomName(Component.literal(record.displayName));
        mannequin.setCustomNameVisible(true);
        mannequin.setInvulnerable(true);
        mannequin.setNoGravity(true);
        mannequin.setSilent(true);
        mannequin.setDeltaMovement(Vec3.ZERO);
        mannequin.setXRot(0.0F);
        trySetImmovable(mannequin);
    }

    private void trySetImmovable(Mannequin mannequin) {
        try {
            Method method = Mannequin.class.getDeclaredMethod("setImmovable", boolean.class);
            method.setAccessible(true);
            method.invoke(mannequin, true);
        } catch (ReflectiveOperationException ignored) {
            // 26.2 currently has this field/method. Position locking below is a fallback.
        }
    }

    public boolean removeEntity(MinecraftServer server, NpcRecord record) {
        for (ServerLevel level : server.getAllLevels()) {
            UUID uuid = record.entityUuid();
            if (uuid == null) return false;
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                entity.discard();
                return true;
            }
        }
        return false;
    }

    public void refresh(MinecraftServer server, NpcRecord record) {
        Mannequin mannequin = ensureSpawned(server, record);
        if (mannequin != null) applyProperties(mannequin, record);
    }

    public void tick(MinecraftServer server) {
        for (NpcRecord record : store.all()) {
            ServerLevel level = findLevel(server, record.dimension);
            if (level == null) continue;
            Mannequin mannequin = ensureSpawned(server, record);
            if (mannequin == null) continue;

            // Hard position lock: NPCs cannot be pushed, wander, fall, or drift.
            mannequin.setDeltaMovement(Vec3.ZERO);
            if (mannequin.getX() != record.x || mannequin.getY() != record.y || mannequin.getZ() != record.z) {
                mannequin.setPos(record.x, record.y, record.z);
            }
            mannequin.setXRot(0.0F);

            if (record.lookAtPlayers) {
                ServerPlayer nearest = level.players().stream()
                        .filter(p -> !p.isSpectator())
                        .filter(p -> p.distanceToSqr(mannequin) <= LOOK_RANGE * LOOK_RANGE)
                        .min(Comparator.comparingDouble(p -> p.distanceToSqr(mannequin)))
                        .orElse(null);
                if (nearest != null) {
                    double dx = nearest.getX() - mannequin.getX();
                    double dz = nearest.getZ() - mannequin.getZ();
                    float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                    mannequin.setYRot(yaw);
                    mannequin.setYHeadRot(yaw);
                }
            } else {
                mannequin.setYRot(record.yaw);
                mannequin.setYHeadRot(record.yaw);
            }
        }
    }

    public NpcRecord byEntity(Entity entity) {
        String uuid = entity.getUUID().toString();
        for (NpcRecord record : store.all()) {
            if (uuid.equals(record.entityUuid)) return record;
        }
        return null;
    }

    public ServerLevel findLevel(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) return level;
        }
        return null;
    }
}
