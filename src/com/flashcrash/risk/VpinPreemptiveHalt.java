package com.flashcrash.risk;

import com.flashcrash.analytics.VPINCalculator;
import com.flashcrash.sim.SimulationContext;

import java.util.List;

/**
 * ALGORITHM 7b: Proactive, order-flow-toxicity-based circuit breaker.
 *
 * Rather than waiting for price to move (as LuldCircuitBreaker does), this
 * control recomputes VPIN on the trailing trade tape at every evaluation
 * step and halts trading pre-emptively once toxicity crosses a threshold.
 * This operationalizes the observation -- attributed to Easley, Lopez de
 * Prado & O'Hara's VPIN research and cited by Lawrence Berkeley National
 * Laboratory researchers -- that VPIN reached extreme levels before price
 * impact became visible on May 6, 2010, i.e. flow toxicity is a leading,
 * not lagging, indicator of fragility.
 */
public class VpinPreemptiveHalt implements RiskControl {

    private final double bucketVolume;
    private final int windowBuckets;
    private final double vpinThreshold;
    private final double haltDurationSeconds;
    private final double recomputeEverySeconds;

    private double lastComputeTime = -1;
    public int triggerCount = 0;
    public double lastVpin = 0.0;

    public VpinPreemptiveHalt(double bucketVolume, int windowBuckets, double vpinThreshold,
                              double haltDurationSeconds, double recomputeEverySeconds) {
        this.bucketVolume = bucketVolume;
        this.windowBuckets = windowBuckets;
        this.vpinThreshold = vpinThreshold;
        this.haltDurationSeconds = haltDurationSeconds;
        this.recomputeEverySeconds = recomputeEverySeconds;
    }

    @Override
    public void evaluate(SimulationContext ctx) {
        if (ctx.tradingHalted) return;
        if (ctx.now - lastComputeTime < recomputeEverySeconds) return;
        lastComputeTime = ctx.now;

        if (ctx.tradeLog.size() < 20) return;
        VPINCalculator calc = new VPINCalculator(bucketVolume, windowBuckets);
        List<VPINCalculator.VpinPoint> points = calc.compute(ctx.tradeLog);
        if (points.isEmpty()) return;
        lastVpin = points.get(points.size() - 1).vpin;

        if (lastVpin > vpinThreshold) {
            ctx.tradingHalted = true;
            ctx.haltUntil = ctx.now + haltDurationSeconds;
            triggerCount++;
        }
    }

    @Override public String name() { return "VPIN-PreemptiveHalt"; }
}
