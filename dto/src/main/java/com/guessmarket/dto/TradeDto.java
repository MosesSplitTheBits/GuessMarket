package com.guessmarket.dto;

/** One executed trade line in an event's history. */
public record TradeDto(
        long timestamp,
        String username,
        String side,          // "BUY" | "SELL"
        String optionName,
        int quantity,
        double pricePaid,
        double commission) {
}
