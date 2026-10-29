package dev.ringworld.world;

import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Translate the entire interpolation path together, retaining step timings.
 */
public final class RingPositionPaths {
    public static PositionPath shift(PositionPath path, double x) {
        Vec3 delta = new Vec3(x, 0, 0);
        if (path instanceof PositionPath.Linear(Vec3 endPosition))
            return new PositionPath.Linear(endPosition.add(delta));
        if (path instanceof PositionPath.Stepped(
                Vec3 endPosition, List<PositionStep> steps
        ))
            return new PositionPath.Stepped(endPosition.add(delta), steps.stream().map(step -> step.addDelta(delta)).toList());
        throw new IllegalArgumentException("Unknown movement path: " + path.type());
    }
}
