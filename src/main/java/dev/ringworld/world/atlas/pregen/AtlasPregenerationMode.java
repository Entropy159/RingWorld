package dev.ringworld.world.atlas.pregen;

/**
 * Scheduling intent for a terrain-atlas pregeneration job. Platform adapters
 * decide how each intent is executed; this shared model contains no loader
 * lifecycle or server-thread dependency.
 */
public enum AtlasPregenerationMode {
    BACKGROUND, INTERACTIVE, HEADLESS_PREWARM
}
