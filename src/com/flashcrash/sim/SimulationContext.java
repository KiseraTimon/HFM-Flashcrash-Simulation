package com.flashcrash.sim;

import com.flashcrash.core.*;

import java.util.*;

/**
 * Shared mutable state visible to every agent during the simulation:
 * the order book, the RNG (single stream -> reproducible with a seed),
 * running trader inventories (positions), and time-series recorders used
 * later by the analytics package (VPIN, inventory half-life, hot-potato
 * network analysis).
 */
public class SimulationContext {
    public final OrderBook book = new OrderBook();
    public final Random rng;
    public double now = 0.0;

    // trader -> net position (contracts, signed: + long, - short)
    public final Map<String, Integer> positions = new HashMap<>();
    public final List<Trade> tradeLog = new ArrayList<>();

    // Time series recorders (sampled every trade and at fixed intervals)
    public final List<Double> sampleTimes = new ArrayList<>();
    public final List<Double> midPriceSeries = new ArrayList<>();
    public final List<Double> bestBidSeries = new ArrayList<>();
    public final List<Double> bestAskSeries = new ArrayList<>();
    public final List<Integer> hftAggregateInventorySeries = new ArrayList<>();
    public final List<Double> imbalanceSeries = new ArrayList<>();

    // trade-flow adjacency for hot-potato network analysis: buyer -> (seller -> volume)
    public final Map<String, Map<String, Integer>> tradeGraph = new HashMap<>();

    public final Set<String> hftTraderIds = new HashSet<>();

    // Trading halt flag, set/cleared by risk controls (circuit breaker)
    public boolean tradingHalted = false;
    public double haltUntil = -1;

    /**
     * Slowly-evolving "fundamental" value, updated via
     * Euler-Maruyama integration of an Ornstein-Uhlenbeck SDE. Value traders use
     * this as a mean-reversion anchor, which is what stops a cascade from
     * spiralling to zero and produces a realistic partial recovery.
     */
    public double fundamentalValue;

    public SimulationContext(long seed) {
        this.rng = new Random(seed);
        this.fundamentalValue = MarketConstants.OPENING_PRICE;
        book.setTradeListener(this::onTrade);
    }

    private void onTrade(Trade t) {
        tradeLog.add(t);
        positions.merge(t.buyTraderId, t.quantity, Integer::sum);
        positions.merge(t.sellTraderId, -t.quantity, Integer::sum);

        tradeGraph.computeIfAbsent(t.buyTraderId, k -> new HashMap<>())
                .merge(t.sellTraderId, t.quantity, Integer::sum);
        tradeGraph.computeIfAbsent(t.sellTraderId, k -> new HashMap<>())
                .merge(t.buyTraderId, t.quantity, Integer::sum);
    }

    public int position(String traderId) {
        return positions.getOrDefault(traderId, 0);
    }

    public int aggregateHftInventory() {
        int sum = 0;
        for (String id : hftTraderIds) sum += Math.abs(position(id));
        return sum;
    }

    public int netHftInventory() {
        int sum = 0;
        for (String id : hftTraderIds) sum += position(id);
        return sum;
    }

    /** Records a market snapshot; called by the simulation engine at fixed sampling intervals. */
    public void recordSnapshot() {
        sampleTimes.add(now);
        midPriceSeries.add(book.midPrice());
        Double bb = book.bestBid();
        Double ba = book.bestAsk();
        bestBidSeries.add(bb == null ? Double.NaN : bb);
        bestAskSeries.add(ba == null ? Double.NaN : ba);
        hftAggregateInventorySeries.add(aggregateHftInventory());
        imbalanceSeries.add(book.imbalance(5));
    }
}
