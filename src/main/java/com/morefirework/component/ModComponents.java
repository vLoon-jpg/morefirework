package com.morefirework.component;

import com.morefirework.MoreFirework;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import java.util.Map;
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
