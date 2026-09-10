package com.guessmarket.engine.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

/**
 * The resting bids/asks for a single option. Bids are kept sorted
 * best-first (highest price, then earliest sequence); asks best-first
 * (lowest price, then earliest sequence) — standard price-time priority.
 */
public class OrderBook {

    private static final Comparator<Order> BID_ORDER =
            Comparator.comparingDouble(Order::getPrice).reversed()
                    .thenComparingLong(Order::getSequence);

    private static final Comparator<Order> ASK_ORDER =
            Comparator.comparingDouble(Order::getPrice)
                    .thenComparingLong(Order::getSequence);

    private final List<Order> bids = new ArrayList<>();
    private final List<Order> asks = new ArrayList<>();
    private Double lastTradePrice = null;

    public List<Order> getBids() {
        return Collections.unmodifiableList(bids);
    }

    public List<Order> getAsks() {
        return Collections.unmodifiableList(asks);
    }

    public void addBid(Order order) {
        bids.add(order);
        bids.sort(BID_ORDER);
    }

    public void addAsk(Order order) {
        asks.add(order);
        asks.sort(ASK_ORDER);
    }

    /** The best (highest) resting bid, or null if the bid side is empty. */
    public Order peekBestBid() {
        return bids.isEmpty() ? null : bids.get(0);
    }

    /** The best (lowest) resting ask, or null if the ask side is empty. */
    public Order peekBestAsk() {
        return asks.isEmpty() ? null : asks.get(0);
    }

    /** Drops any order on either side that has been fully filled. */
    public void removeFilledOrders() {
        bids.removeIf(Order::isFilled);
        asks.removeIf(Order::isFilled);
    }

    public Double getLastTradePrice() {
        return lastTradePrice;
    }

    public void setLastTradePrice(double price) {
        this.lastTradePrice = price;
    }

    public Double getBestBidPrice() {
        Order best = peekBestBid();
        return best == null ? null : best.getPrice();
    }

    public Double getBestAskPrice() {
        Order best = peekBestAsk();
        return best == null ? null : best.getPrice();
    }

    public Double getMidPrice() {
        Double bid = getBestBidPrice();
        Double ask = getBestAskPrice();
        if (bid == null || ask == null) {
            return null;
        }
        return (bid + ask) / 2.0;
    }

    public Double getSpread() {
        Double bid = getBestBidPrice();
        Double ask = getBestAskPrice();
        if (bid == null || ask == null) {
            return null;
        }
        return ask - bid;
    }

    /** Cancels every resting order on both sides — called when an event closes. */
    public void cancelAll() {
        bids.clear();
        asks.clear();
    }
}
