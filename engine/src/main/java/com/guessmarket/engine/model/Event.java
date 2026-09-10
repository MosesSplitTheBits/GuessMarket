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

    // The event's own pooled cash: funded by the MM's subsidy on activation,
    // topped up by trade proceeds on each buy, drawn down to pay winners on
    // close. Kept separate from the owning MM's personal User.balance.
    private double accountBalance;

    // LMSR specific
    private int bParameter;

    // Order Book specific (GM-order-book: allow-mint, initial, d)
    private boolean allowMint;
    private int initialInvestment;
    private int baseValue; // "d" — value paid per share of the winning option at close

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
        this.accountBalance = 0.0;
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

    /**
     * Sets this event's Order Book configuration, parsed from GM-order-book.
     * Called from XmlParser.mapToEvent for ORDER_BOOK events (mirrors how
     * bParameter is set directly in the constructor for LMSR events).
     */
    public void setOrderBookConfig(boolean allowMint, int initialInvestment, int baseValue) {
        this.allowMint = allowMint;
        this.initialInvestment = initialInvestment;
        this.baseValue = baseValue;
    }

    public boolean isAllowMint() {
        return allowMint;
    }

    public int getInitialInvestment() {
        return initialInvestment;
    }

    public int getBaseValue() {
        return baseValue;
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

    public double getAccountBalance() {
        return accountBalance;
    }

    public void adjustAccountBalance(double amount) {
        accountBalance += amount;
    }

    public EventStatus getStatus() {return status;}

    public TradingMethod getMethod() {return method;}

    public String getOwnerUsername() {return ownerUsername;}

    public void setOwnerUsername(String ownerUsername) {this.ownerUsername = ownerUsername;}
}
