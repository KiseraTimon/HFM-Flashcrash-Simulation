package com.flashcrash.analytics;

import com.flashcrash.core.Trade;

import java.util.ArrayList;
import java.util.List;

/**
 * ALGORITHM 4: Volume-Synchronized Probability of Informed Trading (VPIN).
 *
 * Implements the Bulk Volume Classification (BVC) procedure
 * Source:
 *      Easley, Lopez de Prado & O'Hara (2012), "Flow Toxicity and Liquidity in a
 *      High-Frequency World",
 *      Review of Financial Studies 25(5), 1457-1493
 *
 * the same measure the CFTC's own economists and Lawrence Berkeley National
 * Laboratory researchers found spiked to unusually high levels in the hour
 * before the May 6, 2010 Flash Crash.
 *
 * Trades are grouped into buckets of a FIXED VOLUME V (not fixed time;
 * this is what makes VPIN "volume-synchronized" and robust to bursts of
 * activity). Within each bucket, volume is probabilistically classified as
 * buy- or sell-initiated using the standardized price change over the
 * bucket:
 *
 *      Z = (P_end - P_start) / sigma_deltaP
 *      buyVolume  = V * Phi(Z)
 *      sellVolume = V * (1 - Phi(Z))
 *
 * VPIN over a rolling window of n buckets is:
 *
 *      VPIN = ( sum_{i=1..n} |buyVolume_i - sellVolume_i| ) / (n * V)
 */
public class VPINCalculator {

    public static class VpinPoint {
        public final double time;
        public final double vpin;
        public VpinPoint(double time, double vpin) { this.time = time; this.vpin = vpin; }
    }

    private final double bucketVolume;
    private final int windowBuckets;

    public VPINCalculator(double bucketVolume, int windowBuckets) {
        this.bucketVolume = bucketVolume;
        this.windowBuckets = windowBuckets;
    }

    public List<VpinPoint> compute(List<Trade> trades) {
        List<VpinPoint> out = new ArrayList<>();
        if (trades.isEmpty()) return out;

        // First pass: estimate sigma of per-trade price changes for standardization.
        double sumSq = 0;
        int n = 0;
        double prevPrice = trades.get(0).price();
        for (Trade t : trades) {
            double dp = t.price() - prevPrice;
            sumSq += dp * dp;
            n++;
            prevPrice = t.price();
        }
        double sigmaDeltaP = Math.sqrt(sumSq / Math.max(1, n));
        if (sigmaDeltaP < 1e-9) sigmaDeltaP = 1e-9;

        List<Double> bucketImbalances = new ArrayList<>(); // |buy-sell| per bucket
        List<Double> bucketTimes = new ArrayList<>();

        double bucketStartPrice = trades.get(0).price();
        double accumulatedVolume = 0;
        double lastTradePrice = bucketStartPrice;
        double lastTradeTime = trades.get(0).timestamp;

        for (Trade t : trades) {
            accumulatedVolume += t.quantity;
            lastTradePrice = t.price();
            lastTradeTime = t.timestamp;

            if (accumulatedVolume >= bucketVolume) {
                double z = (lastTradePrice - bucketStartPrice) / sigmaDeltaP;
                double buyFrac = NormalDistribution.cdf(z);
                double buyVol = bucketVolume * buyFrac;
                double sellVol = bucketVolume * (1.0 - buyFrac);
                bucketImbalances.add(Math.abs(buyVol - sellVol));
                bucketTimes.add(lastTradeTime);

                if (bucketImbalances.size() >= windowBuckets) {
                    double sum = 0;
                    int start = bucketImbalances.size() - windowBuckets;
                    for (int i = start; i < bucketImbalances.size(); i++) sum += bucketImbalances.get(i);
                    double vpin = sum / (windowBuckets * bucketVolume);
                    out.add(new VpinPoint(lastTradeTime, vpin));
                }

                // reset bucket, carrying over any excess volume as the next bucket's start
                accumulatedVolume = 0;
                bucketStartPrice = lastTradePrice;
            }
        }
        return out;
    }
}
