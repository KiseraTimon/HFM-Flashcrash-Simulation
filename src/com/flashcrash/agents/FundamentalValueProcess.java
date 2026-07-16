package com.flashcrash.agents;

import com.flashcrash.core.MarketConstants;
import com.flashcrash.sim.SimulationContext;

import java.util.Random;

/**
 * ALGORITHM #-: Euler-Maruyama numerical SDE integration.
 *
 * Evolves a slowly-varying "fundamental value" F(t) as an Ornstein-Uhlenbeck
 * process,
 *
 *      dF = theta * (F0 - F) dt + sigma * F dW
 *
 * discretized with the Euler-Maruyama scheme (the standard first-order
 * numerical method for SDEs):
 *
 *      F_{t+dt} = F_t + theta * (F0 - F_t) * dt + sigma * F_t * sqrt(dt) * Z,
 *      Z ~ N(0,1)
 *
 * This does not represent any particular event in the paper; it is the
 * standard mechanism (used throughout market-microstructure and asset-pricing models)
 * for giving the market a "true value" that is independent
 * of the order flow itself. Without it, nothing anchors price and a large
 * one-sided liquidity shock has no reason to ever stop falling. ValueTrader
 * agents observe F(t) and buy/sell toward it, which is what allows the
 * simulated crash to find a bottom and partially recover, mirroring the
 * real event's rebound within about twenty minutes.
 */
public class FundamentalValueProcess implements Agent {
    private final String id = "FUNDAMENTAL";
    private final double dt;
    private final double theta;   // mean-reversion speed
    private final double sigma;   // volatility
    private final double f0;      // long-run mean

    public FundamentalValueProcess(double dt, double theta, double sigma, double f0) {
        this.dt = dt;
        this.theta = theta;
        this.sigma = sigma;
        this.f0 = f0;
    }

    @Override public String getId() { return id; }

    @Override
    public double initialWakeTime(Random rng) {
        return dt;
    }

    @Override
    public double act(double now, SimulationContext ctx) {
        double f = ctx.fundamentalValue;
        double z = ctx.rng.nextGaussian();
        double next = f + theta * (f0 - f) * dt + sigma * f * Math.sqrt(dt) * z;
        ctx.fundamentalValue = Math.max(next, MarketConstants.TICK_SIZE);
        return now + dt;
    }
}
