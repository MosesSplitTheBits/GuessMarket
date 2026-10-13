package com.guessmarket.engine.api;

import com.guessmarket.engine.model.User;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Simulates what Tomcat does on the server: many request threads calling
 * into the SAME EngineManager at the same time.
 */
class EngineManagerConcurrencyTest {

    private static final int THREADS = 8;
    private static final int DEPOSITS_PER_THREAD = 10_000;

    @Test
    void concurrentDeposits_areNeverLost() throws Exception {
        EngineManager engine = new EngineManager();
        engine.registerUser("dan");

        // 8 threads, each depositing $1 ten thousand times — like 8 clients
        // hammering the deposit button at once.
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> results = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            results.add(pool.submit(() -> {
                for (int i = 0; i < DEPOSITS_PER_THREAD; i++) {
                    engine.deposit("dan", 1);
                }
            }));
        }
        for (Future<?> result : results) {
            result.get(); // re-throws anything that blew up inside a thread
        }
        pool.shutdown();

        User dan = engine.getUserDetails("dan");
        int expected = THREADS * DEPOSITS_PER_THREAD;
        assertEquals(expected, dan.getBalance(), 0.0001, "money was lost");
        assertEquals(expected, dan.getLedger().size(), "ledger lines were lost");
    }
}
