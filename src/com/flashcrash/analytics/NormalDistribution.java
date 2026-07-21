package com.flashcrash.analytics;

/** Standard normal CDF
 * Source:
 *      Abramowitz & Stegun (1964) rational approximation 7.1.26 (max error ~1.5e-7).
 */
public final class NormalDistribution {
    private NormalDistribution() {}

    public static double cdf(double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        x = Math.abs(x) / Math.sqrt(2.0);

        double a1 = 0.254829592, a2 = -0.284496736, a3 = 1.421413741;
        double a4 = -1.453152027, a5 = 1.061405429, p = 0.3275911;

        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        return 0.5 * (1.0 + sign * y);
    }
}
