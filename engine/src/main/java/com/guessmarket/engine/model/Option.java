package com.guessmarket.engine.model;

public class Option {
    private String name;
    private int purchasedShares;

    public Option(String name) {
        this.name = name;
        this.purchasedShares = 0;
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
