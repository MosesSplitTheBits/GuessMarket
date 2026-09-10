package com.guessmarket.engine.api;

import com.guessmarket.engine.model.OrderSide;
import com.guessmarket.engine.model.User;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void onPurchaseCommission_matchesSimulation() {
        EngineManager engine = load("clob_sim_purchase.xml");
        replaySimulationOrders(engine);

        assertEquals(486.3055, balanceOf(engine, "Zoe"), DELTA);
        assertEquals(224.8520, balanceOf(engine, "Alice"), DELTA);
        assertEquals(198.5375, balanceOf(engine, "Bob"), DELTA);
        assertEquals(190.3050, balanceOf(engine, "Carol"), DELTA);
        assertEquals(0.7555, engine.getEventDetails(1).getTotalCommissionsCollected(), DELTA);
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
        assertEquals(1.3500, engine.getEventDetails(1).getTotalCommissionsCollected(), DELTA);
        assertMoneyConserved(engine);
    }

    @Test
    void outOfBoundsPrice_isRejectedBeforeTouchingTheBook() {
        EngineManager engine = load("clob_sim_purchase.xml");
        engine.activateEvent(1, "Zoe");

        String result = engine.placeOrder(1, YES, OrderSide.BUY, 10, 1.05, "Bob");

        assertTrue(result.contains("0.01") && result.contains("0.99"),
                "Expected a price-bounds rejection message, got: " + result);
        assertEquals(0, engine.getEventDetails(1).getOptions().get(YES).getOrderBook().getBids().size(),
                "A rejected order must never rest in the book");
    }

    /** The exact order sequence from the simulation's step-by-step walkthrough. */
    private void replaySimulationOrders(EngineManager engine) {
        engine.activateEvent(1, "Zoe");

        engine.placeOrder(1, YES, OrderSide.BUY, 20, 0.50, "Bob");
        engine.placeOrder(1, YES, OrderSide.BUY, 15, 0.48, "Carol");
        engine.placeOrder(1, YES, OrderSide.SELL, 25, 0.58, "Zoe");
        engine.placeOrder(1, YES, OrderSide.SELL, 15, 0.65, "Zoe");
        engine.placeOrder(1, YES, OrderSide.BUY, 25, 0.58, "Alice");   // matches Zoe's 25@0.58 ask
        engine.placeOrder(1, NO, OrderSide.SELL, 50, 0.45, "Zoe");
        engine.placeOrder(1, NO, OrderSide.BUY, 25, 0.45, "Bob");      // partial fill of Zoe's NO ask
        engine.placeOrder(1, YES, OrderSide.SELL, 30, 0.45, "Zoe");    // walks Bob's then Carol's bids
        engine.placeOrder(1, NO, OrderSide.BUY, 35, 0.42, "Carol");
        engine.placeOrder(1, YES, OrderSide.BUY, 40, 0.62, "Alice");   // peer mint with Carol, 5 left resting
        engine.placeOrder(1, YES, OrderSide.BUY, 10, 1.05, "Bob");     // rejected, price out of bounds
        engine.placeOrder(1, NO, OrderSide.SELL, 25, 0.15, "Bob");

        engine.closeEvent(1, YES, "Zoe");
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

    private EngineManager load(String resourceName) {
        EngineManager engine = new EngineManager();
        String result = engine.loadDataFromXml(resourcePath(resourceName));
        assertTrue(result.startsWith("XML loaded successfully"), "Fixture failed to load: " + result);
        return engine;
    }

    private String resourcePath(String resourceName) {
        URL resource = getClass().getClassLoader().getResource(resourceName);
        if (resource == null) {
            throw new IllegalStateException("Test fixture not found on classpath: " + resourceName);
        }
        try {
            return new File(resource.toURI()).getAbsolutePath();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
