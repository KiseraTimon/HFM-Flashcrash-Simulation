package com.flashcrash.agents;

import com.flashcrash.core.OrderSide;
import com.flashcrash.core.OrderType;
import com.flashcrash.sim.SimulationContext;

import java.util.Random;

/**
 * A Percentage-of-Volume (POV) execution algorithm:
 *      a standard institutional order-slicing strategy
 *      (still in wide use today optimal-execution framework)
 *      that participates as a fixed fraction of ongoing
 *      market volume, ignoring price.
 *
 * This directly replicates the trigger identified in the SEC-CFTC (2010)
 * report and in Kirilenko's paper:
 *       a mutual fund complex used an automated sell algorithm to execute
 *       a 75,000-contract E-mini S&P 500 sell program (~$4.1B notional)
 *       targeting a fixed participation rate of market volume "disregarding
 *       price," completing in about 20 minutes and representing roughly 1.3%
 *       of the day's total volume but under 9% of volume during its own
 *       execution window.
 */
public class LargeSeller implements Agent {
    private final String id;
    private final int totalQty;
    private final double targetParticipation;
    private final double startTime;
    private final int maxChunk;
    private final double pollLambda; // check-in rate, 1/sec

    private int remainingQty;
    private int executedQty = 0;
    private int tradeLogPointer = 0;
    private int marketVolumeSinceStart = 0;

    public LargeSeller(String id, int totalQty, double targetParticipation, double startTime,
                       int maxChunk, double pollLambda) {
        this.id = id;
        this.totalQty = totalQty;
        this.remainingQty = totalQty;
        this.targetParticipation = targetParticipation;
        this.startTime = startTime;
        this.maxChunk = maxChunk;
        this.pollLambda = pollLambda;
    }

    @Override public String getId() { return id; }

    @Override
    public double initialWakeTime(Random rng) {
        return startTime;
    }

    @Override
    public double act(double now, SimulationContext ctx) {
        if (remainingQty <= 0) return Double.POSITIVE_INFINITY;

        // incrementally accumulate total market volume traded since program start
        while (tradeLogPointer < ctx.tradeLog.size()) {
            var t = ctx.tradeLog.get(tradeLogPointer++);
            if (t.timestamp >= startTime) marketVolumeSinceStart += t.quantity;
        }

        int targetExecuted = (int) Math.round(targetParticipation * marketVolumeSinceStart);
        int toSell = Math.max(0, targetExecuted - executedQty);
        toSell = Math.min(toSell, remainingQty);
        toSell = Math.min(toSell, maxChunk);

        if (toSell > 0) {
            ctx.book.submit(id, OrderSide.SELL, OrderType.MARKET, 0, toSell, now);
            executedQty += toSell;
            remainingQty -= toSell;
        }

        if (remainingQty <= 0) return Double.POSITIVE_INFINITY;

        double u = Math.max(ctx.rng.nextDouble(), 1e-12);
        return now + (-Math.log(u) / pollLambda);
    }

    public int getExecutedQty() { return executedQty; }
    public int getRemainingQty() { return remainingQty; }
}
