package com.flashcrash.agents;

import com.flashcrash.core.OrderSide;
import com.flashcrash.core.OrderType;
import com.flashcrash.sim.SimulationContext;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * A short-term trend-follower using a moving-average crossover signal
 * (fast SMA vs slow SMA of mid-price). When the fast average pulls away
 * from the slow average beyond a threshold, the agent trades aggressively
 * in the direction of the move. This is the destabilizing "feedback loop"
 * / momentum-ignition mechanism that the SEC-CFTC (2010) report identifies
 * as amplifying the initial selling pressure into a cascade: momentum and
 * stop-loss algorithms sell into a falling market, deepening the decline
 * that triggered them.
 */
public class MomentumTrader implements Agent {
    private final String id;
    private final double lambda;
    private final int fastWindow, slowWindow;
    private final double triggerThresholdPct;
    private final int qty;

    private final ArrayDeque<Double> priceHistory = new ArrayDeque<>();

    public MomentumTrader(String id, double lambdaPerSecond, int fastWindow, int slowWindow,
                          double triggerThresholdPct, int qty) {
        this.id = id;
        this.lambda = lambdaPerSecond;
        this.fastWindow = fastWindow;
        this.slowWindow = slowWindow;
        this.triggerThresholdPct = triggerThresholdPct;
        this.qty = qty;
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
        priceHistory.addLast(mid);
        while (priceHistory.size() > slowWindow) priceHistory.pollFirst();

        if (priceHistory.size() >= slowWindow) {
            double fastSma = average(priceHistory, fastWindow);
            double slowSma = average(priceHistory, slowWindow);
            double deltaPct = (fastSma - slowSma) / slowSma * 100.0;

            if (deltaPct < -triggerThresholdPct) {
                // downtrend confirmed -> sell aggressively, amplifying the move
                ctx.book.submit(id, OrderSide.SELL, OrderType.MARKET, 0, qty, now);
            } else if (deltaPct > triggerThresholdPct) {
                ctx.book.submit(id, OrderSide.BUY, OrderType.MARKET, 0, qty, now);
            }
        }
        return now + drawInterarrival(ctx.rng);
    }

    private double average(ArrayDeque<Double> series, int window) {
        int n = Math.min(window, series.size());
        double sum = 0;
        int i = 0;

        // iterate last n elements (series ordered oldest..newest)
        int skip = series.size() - n;
        for (double v : series) {
            if (i++ < skip) continue;
            sum += v;
        }

        return sum / n;
    }
}
