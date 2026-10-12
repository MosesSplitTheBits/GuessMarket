package com.guessmarket.dto;

/** A resting order in a book. */
public record OrderDto(String username, double price, int remainingQuantity) {
}
