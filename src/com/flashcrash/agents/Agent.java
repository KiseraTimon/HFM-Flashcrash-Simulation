package com.flashcrash.agents;

import com.flashcrash.sim.SimulationContext;

/**
 * A market participant. Agents are woken by the discrete-event simulation
 * engine at self-scheduled times (typically drawn from a Poisson process),
 * take an action (submit/cancel orders), and return the time of their next
 * wake-up.
 */
public interface Agent {
    String getId();

    /** First wake-up time, scheduled at t=0. */
    double initialWakeTime(java.util.Random rng);

    /**
     * Called when this agent is woken at time {@code now}. May submit or
     * cancel orders via {@code ctx}. Returns the next wake-up time
     * (strictly greater than {@code now}), or Double.POSITIVE_INFINITY to
     * deactivate the agent permanently (e.g. an execution algo that has
     * finished its parent order).
     */
    double act(double now, SimulationContext ctx);
}
