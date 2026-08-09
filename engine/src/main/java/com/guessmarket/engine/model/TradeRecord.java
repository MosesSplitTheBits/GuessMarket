package com.guessmarket.engine.model;

public class TradeRecord {
    private String optionName;
    private int quantity;
    private double pricePaid;

    public TradeRecord(String optionName, int quantity, double pricePaid) {
        this.optionName = optionName;
        this.quantity = quantity;
        this.pricePaid = pricePaid;
    }

    public String getOptionName() {
        return optionName;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPricePaid() {
        return pricePaid;
    }
}
