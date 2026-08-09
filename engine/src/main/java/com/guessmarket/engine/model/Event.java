package com.guessmarket.engine.model;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class Event {
    private int id;
    private String name;
    private String description;
    private int commissionRate; // 0 to 90
    private String commissionType; // "on-purchase" or "on-close"
    private double totalCommissionsCollected; //Track current commision
    private boolean isActive;

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

    public List<Option> getOptions() {
        return this.options;
    }

    public Object getCommissionType() {
        return this.commissionType;
    }

    public double getCommissionRate() {
        return this.commissionRate;
    }

    public void addTradeRecord(TradeRecord record) {
        this.tradeHistory.add(record);
    }

    public void setActive(boolean b) {
        this.isActive = b;
    }
}
