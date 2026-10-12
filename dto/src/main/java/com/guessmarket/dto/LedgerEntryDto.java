package com.guessmarket.dto;

/** One line of a user's account history. */
public record LedgerEntryDto(long timestamp, String description, double amount, double balanceAfter) {
}
