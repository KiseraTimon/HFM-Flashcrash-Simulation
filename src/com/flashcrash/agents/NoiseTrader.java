package com.flashcrash.agents;

import com.flashcrash.core.MarketConstants;
import com.flashcrash.core.OrderBook;
import com.flashcrash.core.OrderSide;
import com.flashcrash.core.OrderType;
import com.flashcrash.sim.SimulationContext;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * Baseline liquidity: arrivals follow a homogeneous Poisson process with
 * rate {@code lambda} (arrivals/second), which is the canonical model for
 * order flow in market microstructure theory.
 * Interarrival times are drawn as Exp(lambda) via inverse-CDF sampling:
 * t = -ln(U)/lambda, U ~ Uniform(0,1).
 *
 * 70% of wake-ups post a small passive limit order near the touch (adds
 * depth); 30% cross the spread with a small aggressive order (generates
 * the background trade tape / realized volatility).
 */
public class NoiseTrader implements Agent {
    private final String id;
    private final double lambda;
    private final int maxQty;
    private final int maxTicksFromMid;
    private final int maxRestingOrders;
    private final ArrayDeque<Long> ownRestingOrders = new ArrayDeque<>();

    public NoiseTrader(String id, double lambdaPerSecond, int maxQty, int maxTicksFromMid) {
        this(id, lambdaPerSecond, maxQty, maxTicksFromMid, 4);
    }

    public NoiseTrader(String id, double lambdaPerSecond, int maxQty, int maxTicksFromMid, int maxRestingOrders) {
        this.id = id;
        this.lambda = lambdaPerSecond;
        this.maxQty = maxQty;
        this.maxTicksFromMid = maxTicksFromMid;
        this.maxRestingOrders = maxRestingOrders;
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
        Random rng = ctx.rng;
        OrderBook book = ctx.book;
        double mid = book.midPrice();
        long midTicks = MarketConstants.priceToTicks(mid);

        OrderSide side = rng.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
        int qty = 1 + rng.nextInt(maxQty);

        if (rng.nextDouble() < 0.30) {
            // aggressive: cross the spread
            book.submit(id, side, OrderType.MARKET, 0, qty, now);
        } else {
            // passive: rest an order somewhere within maxTicksFromMid of the touch.
            // Bound the number of simultaneously resting orders per agent (cancel the
            // oldest first) so that total book depth stays finite instead of growing
            // without limit -- real liquidity is finite and time-limited (orders get
            // cancelled/replaced), which is exactly what lets a large sell program
            // move price instead of being absorbed by an infinitely deep book.
            while (ownRestingOrders.size() >= maxRestingOrders) {
                long oldId = ownRestingOrders.pollFirst();
                book.cancel(oldId);
            }
            int offset = 1 + rng.nextInt(maxTicksFromMid);
            long priceTicks = (side == OrderSide.BUY) ? midTicks - offset : midTicks + offset;
            long orderId = book.submit(id, side, OrderType.LIMIT, priceTicks, qty, now);
            ownRestingOrders.addLast(orderId);
        }
        return now + drawInterarrival(rng);
    }
}
