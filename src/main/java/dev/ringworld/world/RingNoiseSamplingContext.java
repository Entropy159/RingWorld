package dev.ringworld.world;

import net.minecraft.world.level.levelgen.NoiseRouter;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Carries the Overworld-only periodic router into vanilla's sampler constructor.
 */
public final class RingNoiseSamplingContext {
    private static final ThreadLocal<NoiseRouter> ROUTER = new ThreadLocal<>();

    @Nullable
    public static NoiseRouter currentRouter() {
        return ROUTER.get();
    }

    public static <T> T withRouter(@Nullable NoiseRouter router, Supplier<T> action) {
        NoiseRouter previous = ROUTER.get();
        if (router == null) ROUTER.remove();
        else ROUTER.set(router);
        try {
            return action.get();
        } finally {
            if (previous == null) ROUTER.remove();
            else ROUTER.set(previous);
        }
    }
}
