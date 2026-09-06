package com.guessmarket.engine.model;

import java.util.HashSet;
import java.util.Set;

public class User {

    private final String name;
    private double balance;
    private boolean blocked;
    private final Set<Integer> managedEventIds;

    public User(String name, double initialCash) {
        this.name = name;
        this.balance = initialCash;
        this.blocked = false;
        this.managedEventIds = new HashSet<>();
    }

    public String getName() {
        return name;
    }

    public double getBalance() {
        return balance;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public Set<Integer> getManagedEventIds() {
        return managedEventIds;
    }

    public void addManagedEvent(int eventId) {
        managedEventIds.add(eventId);
    }

    public boolean isMarketMakerFor(int eventId) {
        return managedEventIds.contains(eventId);
    }

    public void adjustBalance(double amount){
        balance += amount;
        if(balance < 0){
            blocked = true;
        }
}
}