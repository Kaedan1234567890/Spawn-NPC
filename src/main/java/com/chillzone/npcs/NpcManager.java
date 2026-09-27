package com.chillzone.npcs;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.component.ResolvableProfile;

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

        // Recovery path for a clean restart where vanilla saved the mannequin before our
        // shutdown callback discarded it. Adopt the matching mannequin at the stored position
        // instead of spawning a second copy, and remove any extra duplicates found there.
        AABB box = new AABB(record.x - 0.35, record.y - 0.35, record.z - 0.35,
                record.x + 0.35, record.y + 2.2, record.z + 0.35);
        java.util.List<Mannequin> nearby = level.getEntitiesOfClass(Mannequin.class, box, candidate -> {
            if (candidate.isRemoved()) return false;
            if (candidate.getCustomName() == null) return false;
            return candidate.getCustomName().getString().equals(record.displayName);
        });
        if (!nearby.isEmpty()) {
            Mannequin adopted = nearby.getFirst();
            for (int i = 1; i < nearby.size(); i++) nearby.get(i).discard();
            record.entityUuid = adopted.getUUID().toString();
            store.put(record);
            applyProperties(adopted, record);
            return adopted;
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
        mannequin.setCustomName(NpcNameStyle.fromId(record.nameStyle).format(record.displayName, record.nameFormat));
        mannequin.setCustomNameVisible(true);
        mannequin.setInvulnerable(true);
        mannequin.setNoGravity(true);
        mannequin.setSilent(true);
        mannequin.setDeltaMovement(Vec3.ZERO);
        mannequin.setXRot(0.0F);
        trySetImmovable(mannequin);
        tryHideDefaultDescription(mannequin);
        tryApplySkin(mannequin, record);
    }

    private void trySetImmovable(Mannequin mannequin) {
        try {
            Method method = Mannequin.class.getDeclaredMethod("setImmovable", boolean.class);
            method.setAccessible(true);
            method.invoke(mannequin, true);
        } catch (ReflectiveOperationException ignored) {
            // Position locking in tick() remains the fallback.
        }
    }

    private void tryHideDefaultDescription(Mannequin mannequin) {
        // Vanilla mannequins display a second "NPC"/description line. Hide it so Chill Zone
        // controls the visible label cleanly. Reflection keeps this resilient to mapping visibility.
        try {
            Method method = Mannequin.class.getDeclaredMethod("setHideDescription", boolean.class);
            method.setAccessible(true);
            method.invoke(mannequin, true);
        } catch (ReflectiveOperationException ignored) {
            // If Mojang changes the private method, the NPC still functions; only that line may show.
        }
    }


    private void tryApplySkin(Mannequin mannequin, NpcRecord record) {
        try {
            Object profile;
            if (record.skinPlayer == null || record.skinPlayer.isBlank()) {
                var defaultField = Mannequin.class.getDeclaredField("DEFAULT_PROFILE");
                defaultField.setAccessible(true);
                profile = defaultField.get(null);
            } else {
                Method create = ResolvableProfile.class.getDeclaredMethod("createUnresolved", String.class);
                create.setAccessible(true);
                profile = create.invoke(null, record.skinPlayer);
            }
            Method setProfile = Mannequin.class.getDeclaredMethod("setProfile", ResolvableProfile.class);
            setProfile.setAccessible(true);
            setProfile.invoke(mannequin, profile);
        } catch (ReflectiveOperationException ignored) {
            // NPC remains usable if Mojang changes the internal profile method.
        }
    }

    public boolean removeEntity(MinecraftServer server, NpcRecord record) {
        UUID uuid = record.entityUuid();
        if (uuid == null) return false;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                entity.discard();
                return true;
            }
        }
        return false;
    }

    public void removeAllEntities(MinecraftServer server) {
        // Stored NPC records remain intact; only live mannequin entities are discarded.
        // They are recreated from JSON after the next startup. This prevents vanilla world
        // persistence plus our own persistence from creating duplicates after a clean restart.
        for (NpcRecord record : store.all()) {
            removeEntity(server, record);
            record.entityUuid = null;
            store.put(record);
        }
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
