package com.guessmarket.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class User {

    private final String name;
    private double balance;
    private boolean blocked;
    // Ex3: events are identified by name (schema v3 dropped the event id).
    private final Set<String> managedEventNames;
    // Every balance change, in order. Index-addressable so a client can
    // fetch only the lines it hasn't seen yet (delta fetch: "from index N").
    private final List<LedgerEntry> ledger;

    // Ex3: users register themselves at login with an empty account and top
    // up via deposit — there is no initial-cash from XML anymore.
    public User(String name) {
        this.name = name;
        this.balance = 0.0;
        this.blocked = false;
        this.managedEventNames = new HashSet<>();
        this.ledger = new ArrayList<>();
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

    public Set<String> getManagedEventNames() {
        return Collections.unmodifiableSet(managedEventNames);
    }

    public void addManagedEvent(String eventName) {
        managedEventNames.add(eventName);
    }

    public boolean isMarketMakerFor(String eventName) {
        return managedEventNames.contains(eventName);
    }

    public boolean isMarketMaker() {
        return !managedEventNames.isEmpty();
    }

    public List<LedgerEntry> getLedger() {
        return Collections.unmodifiableList(ledger);
    }

    /**
     * The ONLY way a balance changes — so the ledger can never miss a line.
     * The description says why, from this user's point of view.
     *
     * Blocking (spec, Ex2): an action that drives the balance negative is
     * allowed, but the user is blocked from then on. Ex3 adds deposits, so
     * a deposit that brings the balance back to >= 0 lifts the block.
     */
    public void adjustBalance(double amount, String description) {
        balance += amount;
        ledger.add(new LedgerEntry(System.currentTimeMillis(), description, amount, balance));
        blocked = balance < 0;
    }
}
