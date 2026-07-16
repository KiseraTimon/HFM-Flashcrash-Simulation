package com.flashcrash.risk;

import com.flashcrash.sim.SimulationContext;

/** A real-time market safeguard evaluated by the simulation engine on every tick. */
public interface RiskControl {
    /** Called after each snapshot; may mutate ctx.tradingHalted / ctx.haltUntil. */
    void evaluate(SimulationContext ctx);

    String name();
}
