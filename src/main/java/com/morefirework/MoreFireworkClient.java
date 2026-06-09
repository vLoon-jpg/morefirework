package com.morefirework;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.particle.ParticleTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoreFireworkClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("morefirework-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("More Firework client initialized.");
        // Particle registrations, entity renderers, etc. will go here
    }
}
