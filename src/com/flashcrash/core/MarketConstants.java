package com.flashcrash.core;

/**
 * Calibration constants taken from the E-mini S&P 500 futures contract
 * specification
 */
public final class MarketConstants {
    private MarketConstants() {}

    // Minimum price increment of the E-mini S&P 500 contract: 0.25 index points.
    public static final double TICK_SIZE = 0.25;

    // Approximate E-mini price level on May 3-6, 2010 (index points).
    public static final double OPENING_PRICE = 1165.00;

    // Conversions btw a price & ticks (integer lattice used internally by the book)
    public static double ticksToPrice(long ticks) {
        return ticks * TICK_SIZE;
    }

    public static long priceToTicks(double price) {
        return Math.round(price / TICK_SIZE);
    }
}
