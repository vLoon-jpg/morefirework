package com.morefirework.component;

import com.morefirework.MoreFirework;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static registry for per-entity FireworkEffectComponent.
 * No external dependencies — plain Java.
 */
public class ModComponents {
    private static final Map<UUID, FireworkEffectComponent> DATA = new ConcurrentHashMap<>();

    public static FireworkEffectComponent get(Entity entity) {
        return DATA.computeIfAbsent(entity.getUuid(), id -> new FireworkEffectComponent());
    }

    public static void remove(Entity entity) {
        DATA.remove(entity.getUuid());
    }

    /**
     * Cleans up effect data for entities that no longer exist in any loaded world.
     * Call periodically to prevent memory leaks from despawned mobs, etc.
     */
    public static void purgeStaleEntries(ServerWorld world, Set<UUID> activeThisTick) {
        DATA.keySet().removeIf(uuid -> !activeThisTick.contains(uuid));
    }

    public static NbtCompound writeNbt(Entity entity) {
        return get(entity).write(new NbtCompound());
    }

    public static void readNbt(Entity entity, NbtCompound tag) {
        get(entity).read(tag);
    }

    public static void register() {
        MoreFirework.LOGGER.info("More Firework components registered.");
    }
}
