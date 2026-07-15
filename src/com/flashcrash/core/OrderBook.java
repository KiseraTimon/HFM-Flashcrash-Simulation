package com.flashcrash.core;

import java.util.*;
import java.util.function.Consumer;

/**
 * ALGORITHM 1: Price-Time Priority Continuous Double Auction.
 *
 * This is the matching algorithm that runs essentially every modern electronic
 * exchange (CME Globex, Nasdaq INET, NYSE Arca, ...). Orders are matched
 * strictly by best price first; ties at the same price are broken by arrival
 * time (FIFO). We implement it with two TreeMaps keyed by integer tick price
 * so that best-bid/best-ask lookups and price-level iteration are O(log n),
 * and a FIFO queue (ArrayDeque) at each price level so time priority is O(1).
 *
 * This mirrors the CME Globex "price priority-time priority" rule described
 * in Kirilenko et al. (2017), Section II.A.
 */
public class OrderBook {

    // Bids: highest price first (reverse natural order)
    private final TreeMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());

    // Asks: lowest price first (natural order)
    private final TreeMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();
    private final Map<Long, Order> ordersById = new HashMap<>();
    private final Map<Long, OrderSide> sideById = new HashMap<>();

    private long nextOrderId = 1;

    private Consumer<Trade> tradeListener = t -> {};
    private double lastTradePrice = MarketConstants.OPENING_PRICE;

    public void setTradeListener(Consumer<Trade> listener) {
        this.tradeListener = listener;
    }

    public double lastTradePrice() {
        return lastTradePrice;
    }

    /** Submits a new limit or market order and runs the matching algorithm. Returns the order id. */
    public long submit(String traderId, OrderSide side, OrderType type, long priceTicks, int qty, double timestamp) {
        long id = nextOrderId++;
        Order order = new Order(id, traderId, side, type, priceTicks, qty, timestamp);
        match(order);
        if (!order.isFilled() && order.type == OrderType.LIMIT) {
            rest(order);
        }
        return id;
    }

    private void match(Order incoming) {
        TreeMap<Long, ArrayDeque<Order>> opposite = (incoming.side == OrderSide.BUY) ? asks : bids;

        while (incoming.remainingQty > 0 && !opposite.isEmpty()) {
            Map.Entry<Long, ArrayDeque<Order>> bestLevel = opposite.firstEntry();
            long levelPrice = bestLevel.getKey();

            boolean priceCrosses;
            if (incoming.type == OrderType.MARKET) {
                priceCrosses = true;
            } else if (incoming.side == OrderSide.BUY) {
                priceCrosses = incoming.priceTicks >= levelPrice;
            } else {
                priceCrosses = incoming.priceTicks <= levelPrice;
            }
            if (!priceCrosses) break;

            ArrayDeque<Order> queue = bestLevel.getValue();
            while (incoming.remainingQty > 0 && !queue.isEmpty()) {
                Order resting = queue.peekFirst();
                int tradeQty = Math.min(incoming.remainingQty, resting.remainingQty);

                boolean buyIsAggressor = incoming.side == OrderSide.BUY;
                long buyId = buyIsAggressor ? incoming.id : resting.id;
                long sellId = buyIsAggressor ? resting.id : incoming.id;
                String buyTrader = buyIsAggressor ? incoming.traderId : resting.traderId;
                String sellTrader = buyIsAggressor ? resting.traderId : incoming.traderId;

                Trade trade = new Trade(buyId, sellId, buyTrader, sellTrader, levelPrice, tradeQty,
                        incoming.timestamp, buyIsAggressor);
                lastTradePrice = trade.price();
                tradeListener.accept(trade);

                incoming.remainingQty -= tradeQty;
                resting.remainingQty -= tradeQty;

                if (resting.isFilled()) {
                    queue.pollFirst();
                    ordersById.remove(resting.id);
                    sideById.remove(resting.id);
                }
            }
            if (queue.isEmpty()) {
                opposite.remove(levelPrice);
            }
        }
    }

    private void rest(Order order) {
        TreeMap<Long, ArrayDeque<Order>> book = (order.side == OrderSide.BUY) ? bids : asks;
        book.computeIfAbsent(order.priceTicks, k -> new ArrayDeque<>()).addLast(order);
        ordersById.put(order.id, order);
        sideById.put(order.id, order.side);
    }
}
