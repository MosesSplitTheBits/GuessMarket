package com.guessmarket.engine.model;

public class TradeRecord {
    private long timestamp;
    private String optionName;
    private int quantity;
    private double pricePaid; // cost of the shares themselves — commission excluded
    private double commissionAmount;
    private String username;

    public TradeRecord(long timestamp, String optionName, int quantity, double pricePaid, double commissionAmount, String username) {
        this.timestamp = timestamp;
        this.optionName = optionName;
        this.quantity = quantity;
        this.pricePaid = pricePaid;
        this.commissionAmount = commissionAmount;
        this.username = username;
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

    public double getCommissionAmount() {
        return commissionAmount;
    }

    public String getUsername() {
        return username;
    }
}
