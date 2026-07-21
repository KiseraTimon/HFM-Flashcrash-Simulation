package com.flashcrash.analytics;

import java.util.List;

/**
 * ALGORITHM 5: AR(1) / Ordinary-Least-Squares mean-reversion half-life estimator.
 *
 * Fits I_t = c + phi * I_{t-1} + eps_t by OLS to the sampled aggregate HFT
 * inventory series, then converts the AR(1) coefficient phi into a
 * half-life: the time for a unit deviation from the long-run mean to decay
 * by 50%, H = ln(0.5) / ln(phi), expressed in units of the sampling
 * interval used to build the series.
 *
 * This is the standard econometric technique for measuring mean reversion
 * speed (used for interest rates, volatility, and inventory processes
 * alike; it is the discrete-time analogue of estimating the reversion
 * speed theta of an Ornstein-Uhlenbeck process). We use it here to check
 * our simulated market makers' emergent inventory dynamics against
 * Kirilenko's empirical finding that HFT inventories
 * mean-reverted to zero with a half-life of roughly two minutes.
 */
public class InventoryHalfLifeEstimator {

    public static class Result {
        public final double phi;
        public final double intercept;
        public final double halfLifeSamples;
        public final double halfLifeSeconds;
        public Result(double phi, double intercept, double halfLifeSamples, double halfLifeSeconds) {
            this.phi = phi; this.intercept = intercept;
            this.halfLifeSamples = halfLifeSamples; this.halfLifeSeconds = halfLifeSeconds;
        }
    }

    /**
     * @param series          time series of aggregate signed HFT inventory
     * @param samplingIntervalSeconds seconds between consecutive samples
     */
    public Result estimate(List<Double> series, double samplingIntervalSeconds) {
        int n = series.size();
        if (n < 3) return new Result(Double.NaN, Double.NaN, Double.NaN, Double.NaN);

        // OLS regression of x_t on x_{t-1} with intercept
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        int m = n - 1;
        for (int t = 1; t < n; t++) {
            double x = series.get(t - 1); // predictor: previous value
            double y = series.get(t);     // response: current value
            sumX += x; sumY += y; sumXY += x * y; sumXX += x * x;
        }
        double meanX = sumX / m, meanY = sumY / m;
        double covXY = sumXY / m - meanX * meanY;
        double varX = sumXX / m - meanX * meanX;
        double phi = varX < 1e-9 ? 0.0 : covXY / varX;
        double intercept = meanY - phi * meanX;

        double halfLifeSamples;
        if (phi <= 0 || phi >= 1) {
            halfLifeSamples = Double.NaN; // no stable mean reversion detected at this sampling rate
        } else {
            halfLifeSamples = Math.log(0.5) / Math.log(phi);
        }
        double halfLifeSeconds = halfLifeSamples * samplingIntervalSeconds;
        return new Result(phi, intercept, halfLifeSamples, halfLifeSeconds);
    }
}
