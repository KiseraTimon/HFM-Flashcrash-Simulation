package com.flashcrash.agents;

import com.flashcrash.core.OrderSide;
import com.flashcrash.core.OrderType;
import com.flashcrash.sim.SimulationContext;

import java.util.Random;

/**
 * A "fundamental" or value-driven trader: compares the current book mid-price
 * to the slowly-evolving fundamental value (see FundamentalValueProcess) and
 * trades to close the gap, with size scaling with the size of the mispricing.
 * This is the restoring force that eventually halts a cascade;
 * economically, these are the "buyers who step back in when prices look irrationally
 * cheap", which is exactly what the real May 6, 2010 event exhibited (a rapid rebound
 * within roughly twenty minutes of the low).
 */
public class ValueTrader implements Agent {
    private final String id;
    private final double lambda;
    private final double thresholdPct;
    private final double sizePerPct;
    private final int maxQty;

    public ValueTrader(String id, double lambdaPerSecond, double thresholdPct, double sizePerPct, int maxQty) {
        this.id = id;
        this.lambda = lambdaPerSecond;
        this.thresholdPct = thresholdPct;
        this.sizePerPct = sizePerPct;
        this.maxQty = maxQty;
    }

    @Override public String getId() { return id; }

    private double drawInterarrival(Random rng) {
        double u = Math.max(rng.nextDouble(), 1e-12);
        return -Math.log(u) / lambda;
    }

    @Override
    public double initialWakeTime(Random rng) {
        return drawInterarrival(rng);
    }

    @Override
    public double act(double now, SimulationContext ctx) {
        double mid = ctx.book.midPrice();
        double fundamental = ctx.fundamentalValue;
        double gapPct = (fundamental - mid) / fundamental * 100.0;

        if (Math.abs(gapPct) > thresholdPct) {
            int qty = (int) Math.min(maxQty, Math.round(Math.abs(gapPct) * sizePerPct));
            qty = Math.max(1, qty);
            OrderSide side = gapPct > 0 ? OrderSide.BUY : OrderSide.SELL; // price below fundamental -> buy
            ctx.book.submit(id, side, OrderType.MARKET, 0, qty, now);
        }
        return now + drawInterarrival(ctx.rng);
    }
}
