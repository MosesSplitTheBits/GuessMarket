package com.guessmarket.engine.model;

/**
 * One line in a user's account history: a single change to their balance,
 * why it happened, and what the balance was right after it. Immutable —
 * a ledger line is a historical fact and never changes once written.
 *
 * Spec (Ex3): every change to the account is shown on its own line,
 * including changes caused by OTHER users' actions (e.g. commission an MM
 * earns when someone else buys in their event).
 */
public record LedgerEntry(long timestamp, String description, double amount, double balanceAfter) {
}
