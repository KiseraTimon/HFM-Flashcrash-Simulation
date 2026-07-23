package com.flashcrash.risk;

import com.flashcrash.sim.SimulationContext;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * ALGORITHM 7a: Reactive, price-based circuit breaker.
 *
 * Models the Limit Up-Limit Down (LULD) mechanism the SEC adopted after the
 * 2010 Flash Crash: a reference price is the average trade price over a
 * trailing window; if the current price strays beyond +/- thresholdPct of
 * that reference, trading is halted for haltDurationSeconds. This is a
 * simple bang-bang (on/off) control law -- a rolling-average reference with
 * hysteresis, the same structural idea as the real LULD bands.
 */
public class LuldCircuitBreaker implements RiskControl {

    private final double thresholdPct;
    private final double haltDurationSeconds;
    private final double windowSeconds;

    private final Deque<double[]> history = new ArrayDeque<>(); // [time, price]

    public LuldCircuitBreaker(double thresholdPct, double haltDurationSeconds, double windowSeconds) {
        this.thresholdPct = thresholdPct;
        this.haltDurationSeconds = haltDurationSeconds;
        this.windowSeconds = windowSeconds;
    }

    public int triggerCount = 0;

    @Override
    public void evaluate(SimulationContext ctx) {
        double price = ctx.book.midPrice();
        history.addLast(new double[]{ctx.now, price});
        while (!history.isEmpty() && ctx.now - history.peekFirst()[0] > windowSeconds) {
            history.pollFirst();
        }
        if (ctx.tradingHalted) return;

        double sum = 0;
        for (double[] p : history) sum += p[1];
        double referencePrice = sum / history.size();
        if (referencePrice <= 0) return;

        double deviationPct = Math.abs(price - referencePrice) / referencePrice * 100.0;
        if (deviationPct > thresholdPct) {
            ctx.tradingHalted = true;
            ctx.haltUntil = ctx.now + haltDurationSeconds;
            triggerCount++;
        }
    }

    @Override public String name() { return "LULD-CircuitBreaker"; }
}
