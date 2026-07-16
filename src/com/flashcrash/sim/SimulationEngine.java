package com.flashcrash.sim;

import com.flashcrash.agents.Agent;
import com.flashcrash.risk.RiskControl;

import java.util.*;

/**
 * ALGORITHM 2: Discrete-Event Simulation (DES).
 *
 * Rather than stepping through fixed time slices, we maintain a min-heap
 * (PriorityQueue) of future "wake" events, one per agent, ordered by time.
 * This is the standard technique used in queueing-theoretic / market
 * microstructure simulation (see e.g. Cont, Stoikov & Talreja (2010),
 * "A stochastic model for order book dynamics", Operations Research 58(3))
 * and lets heterogeneous agents (HFTs waking every few milliseconds, noise
 * traders waking every few seconds) coexist efficiently: complexity is
 * O(E log A) for E total events and A agents, instead of O(T/dt * A) for a
 * fixed-time-step simulation.
 */
public class SimulationEngine {

    private static class WakeEvent implements Comparable<WakeEvent> {
        final double time;
        final Agent agent;
        WakeEvent(double time, Agent agent) { this.time = time; this.agent = agent; }
        @Override public int compareTo(WakeEvent o) { return Double.compare(time, o.time); }
    }

    private final List<RiskControl> riskControls = new ArrayList<>();

    public void addRiskControl(RiskControl rc) {
        riskControls.add(rc);
    }

    /**
     * Runs the simulation from t=0 to t=endTime.
     *
     * @param agents           all market participants
     * @param endTime          simulated horizon, in seconds
     * @param snapshotInterval how often (seconds) to record a market snapshot for analytics
     */
    public void run(List<Agent> agents, double endTime, double snapshotInterval, SimulationContext ctx) {
        PriorityQueue<WakeEvent> queue = new PriorityQueue<>();
        for (Agent a : agents) {
            double t0 = a.initialWakeTime(ctx.rng);
            if (t0 < endTime) queue.add(new WakeEvent(t0, a));
        }

        double nextSnapshot = 0.0;

        while (!queue.isEmpty()) {
            WakeEvent ev = queue.poll();
            if (ev.time > endTime) break;
            ctx.now = ev.time;

            // fire any snapshots due before or at this event's time
            while (nextSnapshot <= ctx.now && nextSnapshot <= endTime) {
                double savedNow = ctx.now;
                ctx.now = nextSnapshot;
                ctx.recordSnapshot();
                for (RiskControl rc : riskControls) rc.evaluate(ctx);
                ctx.now = savedNow;
                nextSnapshot += snapshotInterval;
            }

            boolean halted = ctx.tradingHalted && ctx.now < ctx.haltUntil;
            if (ctx.tradingHalted && ctx.now >= ctx.haltUntil) {
                ctx.tradingHalted = false;
            }

            double next;
            if (halted) {
                // agent does not get to act during a halt, but stays scheduled shortly after
                next = ctx.now + 0.05;
            } else {
                next = ev.agent.act(ctx.now, ctx);
            }

            if (Double.isFinite(next) && next < endTime) {
                queue.add(new WakeEvent(next, ev.agent));
            }
        }

        // final snapshot
        ctx.now = endTime;
        ctx.recordSnapshot();
    }
}
