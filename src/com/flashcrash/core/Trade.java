package com.flashcrash.core;

public class Trade {
    public final long buyOrderId, sellOrderId;
    public final String buyTraderId, sellTraderId;
    public final long priceTicks;
    public final int quantity;
    public final double timestamp;

    // true if the buy side was the incoming aggressive (liquidity-taking) order
    public final boolean buyIsAggressor;

    public Trade(long buyOrderId, long sellOrderId, String buyTraderId, String sellTraderId,
                 long priceTicks, int quantity, double timestamp, boolean buyIsAggressor) {
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.buyTraderId = buyTraderId;
        this.sellTraderId = sellTraderId;
        this.priceTicks = priceTicks;
        this.quantity = quantity;
        this.timestamp = timestamp;
        this.buyIsAggressor = buyIsAggressor;
    }

    public double price() {
        return MarketConstants.ticksToPrice(priceTicks);
    }
}
