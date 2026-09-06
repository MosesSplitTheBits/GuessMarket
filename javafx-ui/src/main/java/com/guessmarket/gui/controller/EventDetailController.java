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
        if(event != null) {
            eventTitleLabel.setText(event.getName());

            optionOnePane.getChildren().setAll(
                    new Label(event.getOptions().get(0).getName()),
                    new Label("Shares: " + event.getOptions().get(0).getShares())
            );

            optionTwoPane.getChildren().setAll(
                    new Label(event.getOptions().get(1).getName()),
                    new Label("Shares: " + event.getOptions().get(1).getShares())
            );
        }
        else{
            eventTitleLabel.setText("Select Event");
            optionOnePane.getChildren().clear();
            optionTwoPane.getChildren().clear();
        }



    }
}
