package com.flashcrash.core;

public enum OrderSide {
    BUY, SELL;

    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
