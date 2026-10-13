package com.guessmarket.engine.api;

import com.guessmarket.dto.EventDetailsDto;
import com.guessmarket.dto.LedgerDeltaDto;
import com.guessmarket.engine.model.LedgerEntry;
import com.guessmarket.engine.model.User;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ex3 engine behaviour: login-style user registration, deposits, the
 * per-user ledger, accumulating rollback-safe uploads (schema v3), and
 * DTO snapshots. Fixtures live in src/test/resources/ex3/ —
 * smallEX3.xml and multipleEX3.xml are the official Mama samples.
 */
class EngineManagerEx3Test {

    private static final double DELTA = 0.0001;

    // ---------- users ----------

    @Test
    void registerUser_trimsName_andRejectsDuplicatesAndEmpty() {
        EngineManager engine = new EngineManager();
        engine.registerUser("  dan ");

        assertNotNull(engine.getUserDetails("dan"));
        assertThrows(EngineException.class, () -> engine.registerUser("dan"));
        assertThrows(EngineException.class, () -> engine.registerUser(" dan"));
        assertThrows(EngineException.class, () -> engine.registerUser("   "));
        assertThrows(EngineException.class, () -> engine.registerUser(null));
        assertEquals(1, engine.getAllUsers().size());
    }

    @Test
    void newUser_startsEmpty_andDepositMustBePositive() {
        EngineManager engine = new EngineManager();
        engine.registerUser("dan");

        assertEquals(0.0, engine.getUserDetails("dan").getBalance(), DELTA);
        assertThrows(EngineException.class, () -> engine.deposit("dan", 0));
        assertThrows(EngineException.class, () -> engine.deposit("dan", -5));
        assertThrows(EngineException.class, () -> engine.deposit("dan", Double.NaN));
        assertThrows(EngineException.class, () -> engine.deposit("ghost", 10));

        engine.deposit("dan", 250);
        assertEquals(250.0, engine.getUserDetails("dan").getBalance(), DELTA);
    }

    // ---------- loading ----------

    @Test
    void officialSamples_accumulate_andUploaderBecomesMm() {
        EngineManager engine = engineWithUsers("alice", "bob");

        engine.loadEventsFromXml(resource("ex3/smallEX3.xml"), "alice");
        engine.loadEventsFromXml(resource("ex3/multipleEX3.xml"), "bob");

        assertEquals(4, engine.getAllEvents().size(), "second upload must ADD events, not replace them");
        assertEquals("alice", engine.getEventDetails("Mujtaba is Dead").getOwnerUsername());
        assertEquals("bob", engine.getEventDetails("World Cap Winner").getOwnerUsername());
        assertTrue(engine.getUserDetails("alice").isMarketMakerFor("Mujtaba is Dead"));
        assertEquals(3, engine.getUserDetails("bob").getManagedEventNames().size());
    }

    @Test
    void fileClashingWithExistingEvent_isRejectedWhole_andChangesNothing() {
        EngineManager engine = engineWithUsers("alice", "bob");
        engine.loadEventsFromXml(resource("ex3/multipleEX3.xml"), "alice");

        // Contains one brand-new event AND one name that already exists.
        EngineException e = assertThrows(EngineException.class,
                () -> engine.loadEventsFromXml(resource("ex3/clash_with_multiple.xml"), "bob"));

        assertTrue(e.getMessage().contains("World Cap Winner"), e.getMessage());
        assertEquals(3, engine.getAllEvents().size());
        assertNull(engine.getEventDetails("Brand New Event"), "the valid event in a bad file must not sneak in");
        assertFalse(engine.getUserDetails("bob").isMarketMaker());
        assertEquals("alice", engine.getEventDetails("World Cap Winner").getOwnerUsername());
    }

    @Test
    void duplicateNameInsideOneFile_isRejected() {
        EngineManager engine = engineWithUsers("alice");
        assertThrows(EngineException.class,
                () -> engine.loadEventsFromXml(resource("ex3/duplicate_name_in_file.xml"), "alice"));
        assertTrue(engine.getAllEvents().isEmpty());
    }

    @Test
    void invalidEventLaterInFile_rollsBackEarlierValidOnes() {
        EngineManager engine = engineWithUsers("alice");
        assertThrows(EngineException.class,
                () -> engine.loadEventsFromXml(resource("ex3/commission_out_of_range.xml"), "alice"));
        assertNull(engine.getEventDetails("Fine Event"));
    }

    @Test
    void nonXmlContent_andUnknownUploader_areRejected() {
        EngineManager engine = engineWithUsers("alice");
        InputStream garbage = new ByteArrayInputStream("definitely not xml".getBytes(StandardCharsets.UTF_8));

        assertThrows(EngineException.class, () -> engine.loadEventsFromXml(garbage, "alice"));
        assertThrows(EngineException.class,
                () -> engine.loadEventsFromXml(resource("ex3/smallEX3.xml"), "nobody"));
        assertTrue(engine.getAllEvents().isEmpty());
    }

    // ---------- ledger ----------

    @Test
    void ledger_recordsEveryChange_includingCommissionCausedByOthers() {
        EngineManager engine = engineWithUsers("mm", "trader");
        engine.deposit("mm", 1000);
        engine.deposit("trader", 500);
        engine.loadEventsFromXml(resource("ex3/lmsr_on_purchase.xml"), "mm"); // on-purchase 10%
        engine.activateEvent("Who wins the league", "mm");

        engine.buyShares("Who wins the league", 0, 10, "trader");
        engine.buyShares("Who wins the league", 1, 5, "trader");

        User mm = engine.getUserDetails("mm");
        assertTrue(mm.getLedger().stream().anyMatch(line -> line.description().contains("Commission earned from trader")),
                "the MM's ledger must show commission paid by another user");

        for (User user : engine.getAllUsers()) {
            assertLedgerMatchesBalance(user);
        }
    }

    @Test
    void getLedger_returnsOnlyLinesFromTheRequestedIndex() {
        EngineManager engine = engineWithUsers("dan");
        engine.deposit("dan", 10);
        engine.deposit("dan", 20);
        engine.deposit("dan", 30);

        List<LedgerEntry> delta = engine.getLedger("dan", 1);

        assertEquals(2, delta.size());
        assertEquals(20.0, delta.get(0).amount(), DELTA);
        assertEquals(60.0, delta.get(1).balanceAfter(), DELTA);
        assertEquals(0, engine.getLedger("dan", 3).size(), "nothing new since the last fetch");
    }

    // ---------- options ----------

    @Test
    void eventWithThreeOptions_isRejected() {
        // Grader: multi-option events are not required -> exactly 2 options.
        EngineManager engine = engineWithUsers("alice");
        EngineException e = assertThrows(EngineException.class,
                () -> engine.loadEventsFromXml(resource("ex3/three_options.xml"), "alice"));
        assertTrue(e.getMessage().contains("exactly 2 options"), e.getMessage());
        assertTrue(engine.getAllEvents().isEmpty());
    }

    @Test
    void lmsr_rejectsOutOfRangeOptionIndex_andPaysTheWinner() {
        EngineManager engine = engineWithUsers("mm", "trader");
        engine.deposit("mm", 1000);
        engine.deposit("trader", 500);
        engine.loadEventsFromXml(resource("ex3/lmsr_on_purchase.xml"), "mm");
        String event = "Who wins the league";
        engine.activateEvent(event, "mm");

        engine.buyShares(event, 1, 10, "trader");
        // The option index now arrives over HTTP — anything but 0/1 must be refused.
        assertThrows(EngineException.class, () -> engine.buyShares(event, 2, 1, "trader"));
        assertThrows(EngineException.class, () -> engine.buyShares(event, -1, 1, "trader"));

        double before = engine.getUserDetails("trader").getBalance();
        engine.closeEvent(event, 1, "mm");
        assertTrue(engine.getUserDetails("trader").getBalance() > before, "the winner gets paid");
        assertLedgerMatchesBalance(engine.getUserDetails("trader"));
    }

    // ---------- DTO snapshots ----------

    @Test
    void snapshots_areFrozenPhotos_notLiveViews() {
        EngineManager engine = engineWithUsers("mm", "trader");
        engine.deposit("mm", 1000);
        engine.deposit("trader", 500);
        engine.loadEventsFromXml(resource("ex3/lmsr_on_purchase.xml"), "mm");
        String event = "Who wins the league";
        engine.activateEvent(event, "mm");

        EventDetailsDto before = engine.getEventSnapshot(event);
        engine.buyShares(event, 1, 10, "trader");

        assertEquals(0, before.trades().size(), "a snapshot must not see trades made after it was taken");
        assertEquals(0, before.options().get(1).shares());
        assertThrows(UnsupportedOperationException.class, () -> before.trades().add(null),
                "snapshot lists must be read-only");

        EventDetailsDto after = engine.getEventSnapshot(event);
        assertEquals(1, after.trades().size());
        assertTrue(after.options().get(1).lmsrPrice() > after.options().get(0).lmsrPrice());
        assertEquals(List.of(event), engine.getUserSnapshot("trader").participatingEvents());
        assertEquals(List.of(event), engine.getUserSnapshot("mm").managedEvents());
        assertThrows(EngineException.class, () -> engine.getEventSnapshot("no such event"));
    }

    @Test
    void ledgerDelta_versionLetsTheClientAskOnlyForNewLines() {
        EngineManager engine = engineWithUsers("dan");
        engine.deposit("dan", 10);
        engine.deposit("dan", 20);

        LedgerDeltaDto first = engine.getLedgerDelta("dan", 0);
        assertEquals(2, first.entries().size());
        assertEquals(2, first.version());

        engine.deposit("dan", 30);
        LedgerDeltaDto second = engine.getLedgerDelta("dan", first.version());
        assertEquals(1, second.entries().size());
        assertEquals(30.0, second.entries().get(0).amount(), DELTA);
        assertEquals(3, second.version());
    }

    // ---------- helpers ----------

    private static void assertLedgerMatchesBalance(User user) {
        List<LedgerEntry> ledger = user.getLedger();
        double sum = ledger.stream().mapToDouble(LedgerEntry::amount).sum();
        assertEquals(user.getBalance(), sum, DELTA, user.getName() + ": ledger lines must add up to the balance");
        assertEquals(user.getBalance(), ledger.get(ledger.size() - 1).balanceAfter(), DELTA);
    }

    private EngineManager engineWithUsers(String... names) {
        EngineManager engine = new EngineManager();
        for (String name : names) {
            engine.registerUser(name);
        }
        return engine;
    }

    private InputStream resource(String path) {
        InputStream in = getClass().getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new UncheckedIOException(new IOException("Test fixture not found on classpath: " + path));
        }
        return in;
    }
}
