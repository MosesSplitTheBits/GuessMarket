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
    private EventStatus status;
    private String ownerUsername;
    private Integer winningOptionIndex; // null until the event is closed
    private TradingMethod method;

    // LMSR specific
    private int bParameter;

    // The collection of options (Yes / No)
    private List<Option> options;

    // The trade's history list
    private List<TradeRecord> tradeHistory;


    public Event(int id, String name, String description, int commissionRate, List<Option> options, String commissionType, int bParameter, TradingMethod method) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionRate = commissionRate;
        this.commissionType = commissionType;
        this.bParameter = bParameter;
        this.method = method;
        this.options = options;
        this.tradeHistory = new ArrayList<>();
        this.totalCommissionsCollected = 0.0;
        this.status = EventStatus.NOT_STARTED;
        this.winningOptionIndex = null;
    }

    public boolean activate() {
        if(status == EventStatus.NOT_STARTED) {
            status = EventStatus.ACTIVE;
            return true;
        }
        else {return false;}

    }

    public boolean close(int winningOptionIndex) {

        if(status == EventStatus.ACTIVE) {
            this.winningOptionIndex = winningOptionIndex;
            this.status = EventStatus.CLOSED;
            return true;
        }
        else {return false;}
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

    public Integer getWinningOptionIndex() {
        return this.winningOptionIndex;
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

    public EventStatus getStatus() {return status;}

    public TradingMethod getMethod() {return method;}

    public String getOwnerUsername() {return ownerUsername;}

    public void setOwnerUsername(String ownerUsername) {this.ownerUsername = ownerUsername;}
}
