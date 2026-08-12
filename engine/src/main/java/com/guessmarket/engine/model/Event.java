package com.guessmarket.engine.model;

import java.util.List;
import java.util.ArrayList;

public class Event {
    private int id;
    private String name;
    private String description;
    private int commissionRate; // 0 to 90
    private String commissionType; // "on-purchase" or "on-close"
    private double totalCommissionsCollected;
    private boolean isActive;
    private Integer winningOptionIndex; // null until the event is closed

    // LMSR specific
    private int bParameter;

    // The collection of options (Yes / No)
    private List<Option> options;

    // The trade's history list
    private List<TradeRecord> tradeHistory;


    public Event(int id, String name, String description, int commissionRate, List<Option> options, String commissionType, int bParameter) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionRate = commissionRate;
        this.commissionType = commissionType;
        this.bParameter = bParameter;
        this.options = options;
        this.tradeHistory = new ArrayList<>();
        this.totalCommissionsCollected = 0.0;
        this.isActive = true;
        this.winningOptionIndex = null;
    }

    public int getbParameter() {
        return this.bParameter;
    }

    public Integer getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public List<Option> getOptions() {
        return this.options;
    }

    public String getCommissionType() {
        return this.commissionType;
    }

    public double getCommissionRate() {
        return this.commissionRate;
    }

    public List<TradeRecord> getTradeHistory() {
        return this.tradeHistory;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public Integer getWinningOptionIndex() {
        return this.winningOptionIndex;
    }

    public void setWinningOptionIndex(int index) {
        this.winningOptionIndex = index;
    }

    public double getTotalCommissionsCollected() {
        return this.totalCommissionsCollected;
    }

    public void addCommission(double amount) {
        this.totalCommissionsCollected += amount;
    }

    public void addTradeRecord(TradeRecord record) {
        this.tradeHistory.add(record);
    }

    public void setActive(boolean b) {
        this.isActive = b;
    }
}
