package com.flashcrash.core;

/**
 * A single resting or aggressive order submitted to the limit order book.
 * The {id} field is monotonically increasing at submission time and
 * doubles as the FIFO tie-breaker for time priority within a price level,
 * exactly as real matching engines (e.g. CME Globex, Nasdaq INET) use
 * sequence numbers for time priority.
 */
public class Order {
    public final long id;
    public final String traderId;
    public final OrderSide side;
    public final OrderType type;
    public final long priceTicks;   // meaningless for MARKET orders
    public final int originalQty;
    public int remainingQty;
    public final double timestamp;

    // constructor
    public Order(long id, String traderId, OrderSide side, OrderType type,
                 long priceTicks, int qty, double timestamp) {
        this.id = id;
        this.traderId = traderId;
        this.side = side;
        this.type = type;
        this.priceTicks = priceTicks;
        this.originalQty = qty;
        this.remainingQty = qty;
        this.timestamp = timestamp;
    }

    public boolean isFilled() {
        return remainingQty <= 0;
    }

    @Override
    public String toString() {
        return String.format("Order#%d[%s %s %s px=%.2f qty=%d/%d t=%.3f]",
                id, traderId, side, type, MarketConstants.ticksToPrice(priceTicks),
                remainingQty, originalQty, timestamp);
    }
}
