package com.guessmarket.engine.model;

public class TradeRecord {
    private long timestamp;
    private String optionName;
    private int quantity;
    private double pricePaid;

    public TradeRecord(long timestamp, String optionName, int quantity, double pricePaid) {
        this.timestamp = timestamp;
        this.optionName = optionName;
        this.quantity = quantity;
        this.pricePaid = pricePaid;
    }

    public long getTimestamp() {
        return timestamp;
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
