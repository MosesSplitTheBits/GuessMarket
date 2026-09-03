package com.guessmarket.gui.controller;

import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.engine.model.Event;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

/**
 * Controller for events-tab.fxml — the left-hand filterable event list, plus
 * the right-hand detail panel (event-detail.fxml, included here).
 */
public class EventsController {

    private EngineManager engine;

    @FXML
    private ListView<Event> eventsListView;

    // TODO: add fields + @FXML handler methods for the three filter controls
    //       once you've decided how to build them (spec hints at ToggleButtons:
    //       by event method / by status / by commission method, each allowing
    //       "show all").

    // Injected the same way MainController got eventsTabController: event-detail.fxml
    // is included here with fx:id="eventDetail".
    @FXML
    private EventDetailController eventDetailController;

    public void setEngine(EngineManager engine) {
        this.engine = engine;
        // Nothing to show yet — the list stays empty until a file is loaded.
    }

    /** Called by MainController once a file finishes loading successfully. */
    public void refreshEvents() {
        // TODO: pull engine.getAllEvents(), push into eventsListView's items
        //       (eventsListView.getItems().setAll(...)).
        // TODO: wire eventsListView's selection model listener so that
        //       selecting an event calls eventDetailController.showEvent(event).
    }
}
