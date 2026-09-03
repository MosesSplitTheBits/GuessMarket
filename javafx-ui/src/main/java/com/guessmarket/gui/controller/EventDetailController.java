package com.guessmarket.gui.controller;

import com.guessmarket.engine.model.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Controller for event-detail.fxml — the right-hand panel from the sketch:
 * title, the two option order-book panes side by side, and the participations
 * panel below.
 */
public class EventDetailController {

    @FXML
    private Label eventTitleLabel;

    @FXML
    private VBox optionOnePane;

    @FXML
    private VBox optionTwoPane;

    @FXML
    private VBox participationsPane;

    /** Called by EventsController whenever the selected event changes. */
    public void showEvent(Event event) {
        // TODO: populate eventTitleLabel from event.getName() (or similar).
        // TODO: for now (LMSR only, Exercise 1 parity) show each option's
        //       current share count in its pane — that's command 3's content
        //       from Exercise 1, just rendered as labels instead of printed.
        //       Order Book-specific rendering (bid/ask tables, LAST/BID/ASK/
        //       MID/SPREAD stats) comes later once the engine supports it.
        // TODO: populate participationsPane once the engine tracks
        //       per-user participation (that's Exercise 2's user work).
    }
}
