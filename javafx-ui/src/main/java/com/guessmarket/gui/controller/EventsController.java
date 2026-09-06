package com.guessmarket.gui.controller;

import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.engine.model.Event;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import java.util.List;

/**
 * Controller for events-tab.fxml — the left-hand filterable event list, plus
 * the right-hand detail panel (event-detail.fxml, included here).
 */
public class EventsController {

    private EngineManager engine;

    @FXML
    private ListView<Event> eventsListView;

    // TODO: filter row (by event type / status / commission method, each with
    //       an "all" option via ToggleButtons) is deliberately deferred:
    //       - status needs a real not-started/active/closed tri-state on Event
    //         (right now it's just a boolean, always true from construction)
    //       - event type (LMSR vs Order Book) needs Event to record which
    //         mechanism it uses at all — doesn't exist until Order Book does
    //       Only commission method (Event.getCommissionType()) is buildable
    //       against today's model. Revisit once the Ex2 engine work lands.

    // Injected the same way MainController got eventsTabController: event-detail.fxml
    // is included here with fx:id="eventDetail".
    @FXML
    private EventDetailController eventDetailController;

    public void setEngine(EngineManager engine) {
        this.engine = engine;

        //Cell factory to turn Event into text in the ListView
        eventsListView.setCellFactory(listView -> new ListCell<Event>() {
            @Override
            protected void updateItem(Event event, boolean empty) {
                super.updateItem(event, empty);
                setText(empty || event == null ? null : event.getName());
            }
        });

        //Set listener to send selected event to EventDetailsController
        eventsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldEvent, newEvent) -> {
            eventDetailController.showEvent(newEvent);
        });



    }

    /** Called by MainController once a file finishes loading successfully. */
    public void refreshEvents() {
        List<Event> eventList = engine.getAllEvents();
        eventsListView.getItems().setAll(eventList);


    }
}
