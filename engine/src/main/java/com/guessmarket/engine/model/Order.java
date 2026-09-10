package com.guessmarket.engine.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A single resting Order Book instruction (bid or ask) for one option.
 * Price is fixed for the life of the order; remainingQuantity shrinks as it
 * gets filled (by matching or minting) until it reaches 0 and is dropped
 * from the book.
 */
public class Order {
    // Strictly increasing regardless of wall-clock resolution, so two
    // orders placed in the same millisecond still get an unambiguous
    // price-time-priority tiebreak (earlier sequence wins).
    private static final AtomicLong SEQUENCE_GENERATOR = new AtomicLong(0);

    private final String username;
    private final OrderSide side;
    private final double price;
    private int remainingQuantity;
    private final long sequence;

    public Order(String username, OrderSide side, double price, int quantity) {
        this.username = username;
        this.side = side;
        this.price = price;
        this.remainingQuantity = quantity;
        this.sequence = SEQUENCE_GENERATOR.incrementAndGet();
    }

    public String getUsername() {
        return username;
    }

    public OrderSide getSide() {
        return side;
    }

    public double getPrice() {
        return price;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    public long getSequence() {
        return sequence;
    }

    public boolean isFilled() {
        return remainingQuantity <= 0;
    }

    /** Reduces the remaining quantity by a completed fill (match or mint). */
    public void reduceQuantity(int filledQuantity) {
        remainingQuantity -= filledQuantity;
    }
}
