package dev.ringworld.server.atlas.pregen;

import dev.ringworld.world.atlas.pregen.AtlasPregenerationAction;
import dev.ringworld.world.atlas.pregen.AtlasPregenerationState;

import java.util.Objects;

/**
 * Pure command-state policy shared by the adapter and unit tests.
 */
public final class RingAtlasCommandPolicy {
    public enum Outcome {
        START, PAUSE, RESUME, ALREADY_COMPLETE, ALREADY_ACTIVE, NOT_RUNNING, NOT_PAUSED, UNSUPPORTED
    }

    public static Outcome decide(AtlasPregenerationAction action, AtlasPregenerationState state) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(state, "state");
        return switch (action) {
            case START -> switch (state) {
                case COMPLETE -> Outcome.ALREADY_COMPLETE;
                case RUNNING, PAUSED, SAVING -> Outcome.ALREADY_ACTIVE;
                case IDLE, CANCELLED, FAILED -> Outcome.START;
            };
            case PAUSE -> state == AtlasPregenerationState.RUNNING ? Outcome.PAUSE : Outcome.NOT_RUNNING;
            case RESUME -> switch (state) {
                case IDLE, CANCELLED, FAILED -> Outcome.START;
                case PAUSED -> Outcome.RESUME;
                case RUNNING, SAVING, COMPLETE -> Outcome.NOT_PAUSED;
            };
            case CANCEL -> Outcome.UNSUPPORTED;
        };
    }
}
