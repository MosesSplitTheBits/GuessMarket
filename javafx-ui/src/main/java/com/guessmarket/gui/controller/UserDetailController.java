package com.guessmarket.gui.controller;

import com.guessmarket.engine.model.Event;
import com.guessmarket.engine.model.EventStatus;
import com.guessmarket.engine.model.Option;
import com.guessmarket.engine.model.TradeRecord;
import com.guessmarket.engine.model.TradingMethod;
import com.guessmarket.engine.model.User;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Controller for user-detail.fxml — the right-hand panel from the Users tab:
 * selected user's name/balance, their active events, and (once one of those
 * is picked) that user's own trade rows in it plus a closed-event summary.
 */
public class UserDetailController {

    @FXML
    private Label userNameLabel;

    @FXML
    private Label userBalanceLabel;

    @FXML
    private ListView<Event> activeEventsListView;

    @FXML
    private Label eventInvolvementTitleLabel;

    @FXML
    private Label holdingsLabel;

    @FXML
    private ListView<TradeRecord> tradeHistoryListView;

    @FXML
    private Label closedSummaryLabel;

    // The currently displayed user — needed by the active-events selection
    // listener to filter trade rows down to just their own.
    private User currentUser;

    @FXML
    private void initialize() {
        activeEventsListView.setCellFactory(listView -> new ListCell<Event>() {
            @Override
            protected void updateItem(Event event, boolean empty) {
                super.updateItem(event, empty);
                setText(empty || event == null ? null : event.getName());
            }
        });

        tradeHistoryListView.setCellFactory(listView -> new ListCell<TradeRecord>() {
            @Override
            protected void updateItem(TradeRecord trade, boolean empty) {
                super.updateItem(trade, empty);
                setText(empty || trade == null ? null : String.format(
                        "%s %s — %d shares, price: %.2f, commission: %.2f",
                        trade.getSide(), trade.getOptionName(), trade.getQuantity(), trade.getPricePaid(), trade.getCommissionAmount()));
            }
        });

        activeEventsListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldEvent, newEvent) -> showEventInvolvement(newEvent));
    }

    /**
     * Called by UsersController whenever the selected user changes.
     * allEvents is every event currently loaded — needed to find which ones
     * this user has actually traded in (their "active events").
     */
    public void showUser(User user, List<Event> allEvents) {
        this.currentUser = user;

        if (user == null) {
            userNameLabel.setText("Select a user");
            userBalanceLabel.setText("");
            activeEventsListView.getItems().clear();
            clearEventInvolvement();
            return;
        }

        userNameLabel.setText(user.getName());
        userBalanceLabel.setText(String.format("Balance: %.2f%s",
                user.getBalance(), user.isBlocked() ? "  (BLOCKED)" : ""));

        // "Active events" = events this user has actually traded in — being
        // assigned as MM doesn't count on its own (spec: participation
        // counts from the user's first trade action).
        List<Event> activeEvents = new ArrayList<>();
        for (Event event : allEvents) {
            for (TradeRecord trade : event.getTradeHistory()) {
                if (trade.getUsername().equals(user.getName())) {
                    activeEvents.add(event);
                    break;
                }
            }
        }
        activeEventsListView.getItems().setAll(activeEvents);
        clearEventInvolvement();
    }

    private void showEventInvolvement(Event event) {
        if (event == null || currentUser == null) {
            clearEventInvolvement();
            return;
        }

        eventInvolvementTitleLabel.setText("Your trades in \"" + event.getName() + "\":");

        if (event.getMethod() == TradingMethod.ORDER_BOOK) {
            StringBuilder holdings = new StringBuilder("Current holdings: ");
            for (int i = 0; i < event.getOptions().size(); i++) {
                if (i > 0) {
                    holdings.append(", ");
                }
                Option option = event.getOptions().get(i);
                holdings.append(option.getName()).append('=').append(option.getHolding(currentUser.getName()));
            }
            holdingsLabel.setText(holdings.toString());
        } else {
            holdingsLabel.setText("");
        }

        // Only this user's own rows, newest first — event.getTradeHistory()
        // is append-order (oldest first), so reverse the filtered copy.
        List<TradeRecord> ownTrades = new ArrayList<>();
        for (TradeRecord trade : event.getTradeHistory()) {
            if (trade.getUsername().equals(currentUser.getName())) {
                ownTrades.add(trade);
            }
        }
        Collections.reverse(ownTrades);
        tradeHistoryListView.getItems().setAll(ownTrades);

        if (event.getStatus() == EventStatus.CLOSED) {
            StringBuilder summary = new StringBuilder("Event closed — final shares per option:\n");
            for (Option option : event.getOptions()) {
                summary.append(String.format("  %s: %d shares%n", option.getName(), option.getShares()));
            }
            String winningOption = event.getOptions().get(event.getWinningOptionIndex()).getName();
            summary.append("Winning option: ").append(winningOption);
            closedSummaryLabel.setText(summary.toString());
        } else {
            closedSummaryLabel.setText("");
        }
    }

    private void clearEventInvolvement() {
        eventInvolvementTitleLabel.setText("");
        holdingsLabel.setText("");
        tradeHistoryListView.getItems().clear();
        closedSummaryLabel.setText("");
    }
}
