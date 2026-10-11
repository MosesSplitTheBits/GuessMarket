package com.guessmarket.engine.api;

import com.guessmarket.engine.model.OrderSide;
import com.guessmarket.engine.model.User;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the exact scenario from docs/xml_tests/clob_simulation.html
 * (Zoe/Alice/Bob/Carol on a "Will it rain tomorrow?" Order Book market,
 * d=1, initial=100, allow-mint=true) through the real EngineManager, once
 * per commission mode, and checks the final balances against that
 * simulation's own computeState() output — hand-verified to 4 decimal
 * places when this test was written.
 *
 * Exercises: resting bids/asks, a simple match, a partial fill, a sell
 * order walking through two resting bid levels, a peer-to-peer mint with a
 * leftover partial rest, an out-of-bounds price rejection, and final
 * resolution — both on-purchase and on-close commission.
 */
class EngineManagerOrderBookTest {

    private static final int YES = 0;
    private static final int NO = 1;

    private static final double DELTA = 0.0001;

    private static final String EVENT = "Will it rain tomorrow?";

    @Test
    void onPurchaseCommission_matchesSimulation() {
        EngineManager engine = load("clob_sim_purchase.xml");
        replaySimulationOrders(engine);

        assertEquals(486.3055, balanceOf(engine, "Zoe"), DELTA);
        assertEquals(224.8520, balanceOf(engine, "Alice"), DELTA);
        assertEquals(198.5375, balanceOf(engine, "Bob"), DELTA);
        assertEquals(190.3050, balanceOf(engine, "Carol"), DELTA);
        assertEquals(0.7555, engine.getEventDetails(EVENT).getTotalCommissionsCollected(), DELTA);
        assertMoneyConserved(engine);
    }

    @Test
    void onCloseCommission_matchesSimulation() {
        EngineManager engine = load("clob_sim_close.xml");
        replaySimulationOrders(engine);

        assertEquals(486.4500, balanceOf(engine, "Zoe"), DELTA);
        assertEquals(224.6000, balanceOf(engine, "Alice"), DELTA);
        assertEquals(198.5500, balanceOf(engine, "Bob"), DELTA);
        assertEquals(190.4000, balanceOf(engine, "Carol"), DELTA);
        assertEquals(1.3500, engine.getEventDetails(EVENT).getTotalCommissionsCollected(), DELTA);
        assertMoneyConserved(engine);
    }

    @Test
    void outOfBoundsPrice_isRejectedBeforeTouchingTheBook() {
        EngineManager engine = load("clob_sim_purchase.xml");
        engine.activateEvent(EVENT, "Zoe");

        EngineException rejection = assertThrows(EngineException.class,
                () -> engine.placeOrder(EVENT, YES, OrderSide.BUY, 10, 1.05, "Bob"));

        assertTrue(rejection.getMessage().contains("0.01") && rejection.getMessage().contains("0.99"),
                "Expected a price-bounds rejection message, got: " + rejection.getMessage());
        assertEquals(0, engine.getEventDetails(EVENT).getOptions().get(YES).getOrderBook().getBids().size(),
                "A rejected order must never rest in the book");
    }

    /** The exact order sequence from the simulation's step-by-step walkthrough. */
    private void replaySimulationOrders(EngineManager engine) {
        engine.activateEvent(EVENT, "Zoe");

        engine.placeOrder(EVENT, YES, OrderSide.BUY, 20, 0.50, "Bob");
        engine.placeOrder(EVENT, YES, OrderSide.BUY, 15, 0.48, "Carol");
        engine.placeOrder(EVENT, YES, OrderSide.SELL, 25, 0.58, "Zoe");
        engine.placeOrder(EVENT, YES, OrderSide.SELL, 15, 0.65, "Zoe");
        engine.placeOrder(EVENT, YES, OrderSide.BUY, 25, 0.58, "Alice");   // matches Zoe's 25@0.58 ask
        engine.placeOrder(EVENT, NO, OrderSide.SELL, 50, 0.45, "Zoe");
        engine.placeOrder(EVENT, NO, OrderSide.BUY, 25, 0.45, "Bob");      // partial fill of Zoe's NO ask
        engine.placeOrder(EVENT, YES, OrderSide.SELL, 30, 0.45, "Zoe");    // walks Bob's then Carol's bids
        engine.placeOrder(EVENT, NO, OrderSide.BUY, 35, 0.42, "Carol");
        engine.placeOrder(EVENT, YES, OrderSide.BUY, 40, 0.62, "Alice");   // peer mint with Carol, 5 left resting
        assertThrows(EngineException.class,                                 // rejected, price out of bounds
                () -> engine.placeOrder(EVENT, YES, OrderSide.BUY, 10, 1.05, "Bob"));
        engine.placeOrder(EVENT, NO, OrderSide.SELL, 25, 0.15, "Bob");

        engine.closeEvent(EVENT, YES, "Zoe");
    }

    private double balanceOf(EngineManager engine, String username) {
        User user = engine.getUserDetails(username);
        return user.getBalance();
    }

    /** No trade can create or destroy money — everyone's cash must still sum to the four starting balances. */
    private void assertMoneyConserved(EngineManager engine) {
        double total = balanceOf(engine, "Zoe") + balanceOf(engine, "Alice")
                + balanceOf(engine, "Bob") + balanceOf(engine, "Carol");
        assertEquals(1100.0, total, DELTA);
    }

    /**
     * Ex3 setup: users are no longer in the XML — they register and deposit
     * their starting cash (the same amounts the Ex2 fixture's initial-cash
     * gave them), and Zoe uploads the file, which makes her the MM.
     */
    private EngineManager load(String resourceName) {
        EngineManager engine = new EngineManager();
        registerWithCash(engine, "Zoe", 500);
        registerWithCash(engine, "Alice", 200);
        registerWithCash(engine, "Bob", 200);
        registerWithCash(engine, "Carol", 200);
        try (InputStream xml = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (xml == null) {
                throw new IllegalStateException("Test fixture not found on classpath: " + resourceName);
            }
            engine.loadEventsFromXml(xml, "Zoe");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return engine;
    }

    private void registerWithCash(EngineManager engine, String name, double cash) {
        engine.registerUser(name);
        engine.deposit(name, cash);
    }
}
