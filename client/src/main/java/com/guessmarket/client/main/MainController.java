package com.guessmarket.client.main;

import com.guessmarket.client.ClientConstants;
import com.guessmarket.client.account.AccountController;
import com.guessmarket.client.events.EventDetailController;
import com.guessmarket.client.events.EventsController;
import com.guessmarket.client.refresh.EventDetailRefresher;
import com.guessmarket.client.refresh.EventsRefresher;
import com.guessmarket.client.refresh.LedgerRefresher;
import com.guessmarket.client.refresh.MeRefresher;
import com.guessmarket.client.refresh.UsersRefresher;
import com.guessmarket.dto.UserDetailsDto;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.util.Timer;
import java.util.TimerTask;

/**
 * The main window after login: a header (name + live balance) plus the two
 * tabs from the Ex3 sketch — Events (all events, details, trading) and
 * Account (upload, users, my balance + load funds, my events, ledger).
 *
 * Owns the polling: one java.util.Timer runs every refresher (TimerTask)
 * every REFRESH_RATE_MS; close() stops it when the window closes.
 *
 * Pull strategy (spec: "all or delta fetching, case by case"):
 *   - full fetch: events list, selected event(s), users, /me — small, and a
 *     full snapshot is simplest to keep correct;
 *   - delta fetch: the ledger — it only grows, so only new lines are sent.
 */
public class MainController {

    @FXML
    private Label helloLabel;

    @FXML
    private Label headerBalanceLabel;

    // Injected by FXMLLoader from the <fx:include fx:id="eventsTab"/"accountTab">
    // elements in main-view.fxml (fx:id + "Controller").
    @FXML
    private EventsController eventsTabController;

    @FXML
    private AccountController accountTabController;

    // true = daemon thread: it never keeps the JVM alive on its own.
    private final Timer timer = new Timer("refreshers", true);

    /** Called once by ClientApp right after this view is loaded. */
    public void start(String username) {
        helloLabel.setText("Hello, " + username);

        // --- Events tab
        EventDetailController eventDetail = eventsTabController.getEventDetailController();
        eventDetail.setUsername(username);
        EventsRefresher eventsRefresher = new EventsRefresher(eventsTabController::updateEvents);
        EventDetailRefresher eventDetailRefresher =
                new EventDetailRefresher(eventDetail::getCurrentEventName, eventDetail::showDetails);
        // Selecting an event / finishing an action fetches at once instead of
        // waiting up to one tick. run() only fires an async request, so calling
        // it from the JavaFX thread is fine.
        eventDetail.setRefreshNow(eventDetailRefresher::run);

        // --- Account tab
        accountTabController.setUsername(username);
        MeRefresher meRefresher = new MeRefresher(this::updateMe);
        UsersRefresher usersRefresher = new UsersRefresher(accountTabController::updateUsers);
        LedgerRefresher ledgerRefresher = new LedgerRefresher(accountTabController::appendLedger);
        // Same refresher class as the Events tab, pointed at the Account tab's selection.
        EventDetailRefresher myEventRefresher =
                new EventDetailRefresher(accountTabController::getSelectedEventName, accountTabController::showEventDetails);
        accountTabController.setRefreshMeNow(meRefresher::run);
        accountTabController.setRefreshEventNow(myEventRefresher::run);

        for (TimerTask refresher : new TimerTask[]{
                eventsRefresher, eventDetailRefresher, meRefresher, usersRefresher, ledgerRefresher, myEventRefresher}) {
            timer.schedule(refresher, 0, ClientConstants.REFRESH_RATE_MS);
        }
    }

    // The header shows the live balance on both tabs; the Account tab shows the rest.
    private void updateMe(UserDetailsDto me) {
        headerBalanceLabel.setText(String.format("|   Balance: %.2f%s", me.balance(), me.blocked() ? " (BLOCKED)" : ""));
        accountTabController.updateMe(me);
    }

    /** Window is closing: stop every polling task. */
    public void close() {
        timer.cancel();
    }
}
