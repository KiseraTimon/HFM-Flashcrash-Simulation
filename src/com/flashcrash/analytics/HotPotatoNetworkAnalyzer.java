package com.flashcrash.analytics;

import com.flashcrash.core.Trade;

import java.util.*;

/**
 * ALGORITHM 6: Trade-flow network analysis.
 *
 * Kirilenko's central empirical finding is the "hot potato"
 * effect: HFTs traded enormous gross volume with each other while keeping
 * tiny net inventories, i.e. contracts were rapidly passed back and forth
 * within the HFT group rather than being absorbed. We detect this with two
 * classic graph techniques:
 *
 *  (a) Turnover ratio per trader = grossVolumeTraded / (1 + |netPositionChange|)
 *      over a sliding time window. A high ratio means "lots of trading,
 *      little net risk transfer" -- the hot-potato signature.
 *
 *  (b) Tarjan's Strongly Connected Components algorithm (Tarjan, 1972,
 *      "Depth-first search and linear graph algorithms", SIAM J. Computing)
 *      run on the directed graph where an edge seller -> buyer exists for
 *      every trade. A non-trivial SCC (size > 1) among the highest-volume
 *      nodes is direct evidence of contracts cycling within a closed group
 *      of counterparties; exactly the hot-potato pattern, made concrete
 *      as a graph-theoretic object instead of just a summary statistic.
 */
public class HotPotatoNetworkAnalyzer {

    public static class TurnoverResult {
        public final String traderId;
        public final int grossVolume;
        public final int netPositionChange;
        public final double turnoverRatio;
        public TurnoverResult(String id, int gross, int net, double ratio) {
            this.traderId = id; this.grossVolume = gross; this.netPositionChange = net; this.turnoverRatio = ratio;
        }
    }

    /** Turnover ratio for every trader active in [windowStart, windowEnd]. */
    public List<TurnoverResult> turnoverRatios(List<Trade> trades, double windowStart, double windowEnd) {
        Map<String, Integer> gross = new HashMap<>();
        Map<String, Integer> net = new HashMap<>();
        for (Trade t : trades) {
            if (t.timestamp < windowStart || t.timestamp > windowEnd) continue;
            gross.merge(t.buyTraderId, t.quantity, Integer::sum);
            gross.merge(t.sellTraderId, t.quantity, Integer::sum);
            net.merge(t.buyTraderId, t.quantity, Integer::sum);
            net.merge(t.sellTraderId, -t.quantity, Integer::sum);
        }
        List<TurnoverResult> results = new ArrayList<>();
        for (String trader : gross.keySet()) {
            int g = gross.get(trader);
            int nNet = net.getOrDefault(trader, 0);
            double ratio = g / (1.0 + Math.abs(nNet));
            results.add(new TurnoverResult(trader, g, nNet, ratio));
        }
        results.sort((a, b) -> Double.compare(b.turnoverRatio, a.turnoverRatio));
        return results;
    }

    // Tarjan's SCC algorithm

    private Map<String, List<String>> adjacency;
    private Map<String, Integer> index;
    private Map<String, Integer> lowlink;
    private Set<String> onStack;
    private Deque<String> stack;
    private int counter;
    private List<List<String>> sccs;

    public List<List<String>> stronglyConnectedComponents(List<Trade> trades, double windowStart, double windowEnd) {
        adjacency = new HashMap<>();
        for (Trade t : trades) {
            if (t.timestamp < windowStart || t.timestamp > windowEnd) continue;
            adjacency.computeIfAbsent(t.sellTraderId, k -> new ArrayList<>()).add(t.buyTraderId);
        }
        index = new HashMap<>();
        lowlink = new HashMap<>();
        onStack = new HashSet<>();
        stack = new ArrayDeque<>();
        counter = 0;
        sccs = new ArrayList<>();

        Set<String> nodes = new HashSet<>();
        for (Map.Entry<String, List<String>> e : adjacency.entrySet()) {
            nodes.add(e.getKey());
            nodes.addAll(e.getValue());
        }
        for (String node : nodes) {
            if (!index.containsKey(node)) strongConnect(node);
        }
        return sccs;
    }

    private void strongConnect(String v) {
        index.put(v, counter);
        lowlink.put(v, counter);
        counter++;
        stack.push(v);
        onStack.add(v);

        for (String w : adjacency.getOrDefault(v, Collections.emptyList())) {
            if (!index.containsKey(w)) {
                strongConnect(w);
                lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
            } else if (onStack.contains(w)) {
                lowlink.put(v, Math.min(lowlink.get(v), index.get(w)));
            }
        }

        if (lowlink.get(v).equals(index.get(v))) {
            List<String> component = new ArrayList<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                component.add(w);
            } while (!w.equals(v));
            sccs.add(component);
        }
    }
}
