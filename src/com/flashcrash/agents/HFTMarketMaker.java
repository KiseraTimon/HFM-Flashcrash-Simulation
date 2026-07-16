package com.flashcrash.agents;

import com.flashcrash.core.MarketConstants;
import com.flashcrash.core.OrderBook;
import com.flashcrash.core.OrderSide;
import com.flashcrash.core.OrderType;
import com.flashcrash.sim.SimulationContext;

import java.util.Random;

/**
 * A high-frequency market maker implementing the
 * inventory-skewed quoting rule:
 *
 *      reservation price  r = mid - q * gamma * sigma^2 * horizon
 *      quoted half-spread = (gamma * sigma^2 * horizon)/2 + (1/gamma) * ln(1 + gamma/kappa)
 *      bid = r - halfSpread,  ask = r + halfSpread
 *
 *      source:
 *          Avellaneda & Stoikov (2008) "High-Frequency Trading in a Limit Order Book"
 *          (Quantitative Finance 8(3))
 *
 * where q is the agent's current signed inventory. As inventory grows the
 * reservation price skews away from mid, making the inventory-reducing side
 * of the quote more attractive and the inventory-increasing side less so —
 * this is the endogenous mechanism that produces mean-reverting inventory
 * without any explicit "target inventory" being hard-coded, matching
 * Kirilenko et al. (2017)'s empirical finding that HFT inventories
 * mean-reverted to zero with a half-life of about two minutes and rarely
 * exceeded roughly 3,000 contracts. When |inventory| exceeds a hard cap the
 * agent stops quoting on the inventory-increasing side entirely (a real
 * risk-limit behaviour), which is what ultimately causes HFTs to withdraw
 * liquidity and trade aggressively to flatten during a stress event, as
 * documented in the paper and the SEC-CFTC (2010) report.
 */
public class HFTMarketMaker implements Agent {
    private final String id;
    private final double lambda;      // quote refresh rate (Poisson), 1/sec
    private final double gamma;       // risk aversion
    private final double sigma;       // local volatility estimate (price units/sqrt(sec))
    private final double horizon;     // AS "time to terminal horizon" parameter (sec), kept fixed (rolling horizon)
    private final double kappa;       // order book liquidity/arrival-rate parameter
    private final int quoteSize;
    private final int hardInventoryCap;

    private long bidOrderId = -1, askOrderId = -1;

    public HFTMarketMaker(String id, double lambdaPerSecond, double gamma, double sigma,
                          double horizon, double kappa, int quoteSize, int hardInventoryCap) {
        this.id = id;
        this.lambda = lambdaPerSecond;
        this.gamma = gamma;
        this.sigma = sigma;
        this.horizon = horizon;
        this.kappa = kappa;
        this.quoteSize = quoteSize;
        this.hardInventoryCap = hardInventoryCap;
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
        OrderBook book = ctx.book;

        // cancel previous resting quotes (real HFTs cancel/replace on essentially every tick)
        if (bidOrderId >= 0) book.cancel(bidOrderId);
        if (askOrderId >= 0) book.cancel(askOrderId);
        bidOrderId = -1;
        askOrderId = -1;

        double mid = book.midPrice();
        int q = ctx.position(id);

        double reservation = mid - q * gamma * sigma * sigma * horizon;
        double halfSpread = (gamma * sigma * sigma * horizon) / 2.0
                + (1.0 / gamma) * Math.log(1.0 + gamma / kappa);
        halfSpread = Math.max(halfSpread, MarketConstants.TICK_SIZE / 2.0);

        /** Sanity floor:
         * a market maker's reservation price should never collapse
         * to (or below) zero regardless of inventory pressure -- real exchanges
         * enforce analogous protections (e.g. the stub-quote rules adopted after
         * the 2010 crash, which required quotes to stay within a reasonable band
         * of the going price rather than resting at $0.01 or $100,000).
        */
        reservation = Math.max(reservation, mid * 0.2);

        double bidPx = Math.max(reservation - halfSpread, MarketConstants.TICK_SIZE);
        double askPx = Math.max(reservation + halfSpread, bidPx + MarketConstants.TICK_SIZE);

        long bidTicks = MarketConstants.priceToTicks(bidPx);
        long askTicks = MarketConstants.priceToTicks(askPx);
        if (askTicks <= bidTicks) askTicks = bidTicks + 1; // never cross

        /** Hard inventory cap:
         * stop adding to a position beyond the risk limit.
         * I think this is what real market-making desks do and is the proximate cause
         * of HFT liquidity withdrawal documented in the paper.
        */
        boolean allowBuy = q < hardInventoryCap;
        boolean allowSell = q > -hardInventoryCap;

        if (allowBuy) {
            bidOrderId = book.submit(id, OrderSide.BUY, OrderType.LIMIT, bidTicks, quoteSize, now);
        }
        if (allowSell) {
            askOrderId = book.submit(id, OrderSide.SELL, OrderType.LIMIT, askTicks, quoteSize, now);
        }

        /**
         * If inventory has blown through the cap (e.g. during a fast one-sided
         * market), actively flatten with a small aggressive order instead of
         * just refusing to add — mirrors real-world emergency de-risking.
        */
        if (q >= hardInventoryCap) {
            book.submit(id, OrderSide.SELL, OrderType.MARKET, 0, Math.min(quoteSize, q), now);
        } else if (q <= -hardInventoryCap) {
            book.submit(id, OrderSide.BUY, OrderType.MARKET, 0, Math.min(quoteSize, -q), now);
        }

        return now + drawInterarrival(ctx.rng);
    }
}
