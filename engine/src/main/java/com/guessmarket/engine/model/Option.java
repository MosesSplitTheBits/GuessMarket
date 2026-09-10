package com.guessmarket.engine.model;

import java.util.HashMap;
import java.util.Map;

public class Option {
    private String name;
    private int purchasedShares;

    // Order Book only: this option's own independent bid/ask book. Unused
    // (but harmless) for LMSR options.
    private final OrderBook orderBook = new OrderBook();

    // Order Book only: how many shares of THIS option each username
    // currently holds. LMSR doesn't need this — it derives a user's
    // holdings by summing their buy-only TradeRecord history instead,
    // since LMSR never lets anyone sell. Order Book allows selling, so we
    // need a running per-user balance instead.
    private final Map<String, Integer> holdings = new HashMap<>();

    public Option(String name) {
        this.name = name;
        this.purchasedShares = 0;
    }

    public OrderBook getOrderBook() {
        return orderBook;
    }

    public int getHolding(String username) {
        return holdings.getOrDefault(username, 0);
    }

    public void addHolding(String username, int quantity) {
        holdings.merge(username, quantity, Integer::sum); //Create new User key if missing
    }

    public void removeHolding(String username, int quantity) {
        holdings.put(username, holdings.get(username) - quantity);
    }

    /**
     * A read-only snapshot of every holder of this option and how many
     * shares they have — used for the participant-holdings display and to
     * find winners when an Order Book event closes.
     */
    public Map<String, Integer> getAllHoldings() {
        return java.util.Collections.unmodifiableMap(holdings);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPurchasedShares() {
        return purchasedShares;
    }

    public void setPurchasedShares(int purchasedShares) {
        this.purchasedShares = purchasedShares;
    }

    public int getShares() {
        return this.purchasedShares;
    }

    public void addShares(int amount) {
        this.purchasedShares += amount;
    }
}
