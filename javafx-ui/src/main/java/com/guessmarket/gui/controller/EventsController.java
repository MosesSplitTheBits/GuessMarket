package com.guessmarket.gui.controller;

import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.engine.model.Event;
import com.guessmarket.engine.model.EventStatus;
import com.guessmarket.engine.model.TradingMethod;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for events-tab.fxml — the filter row + event list on the left,
 * plus the right-hand detail panel (event-detail.fxml, included here).
 */
public class EventsController {

    private EngineManager engine;

    // The full unfiltered list from the last load. Filters are always
    // re-applied against this, never against the already-filtered ListView
    // contents — otherwise loosening a filter couldn't bring events back.
    private List<Event> allEvents = new ArrayList<>();

    @FXML
    private ListView<Event> eventsListView;

    @FXML
    private ComboBox<String> typeFilterComboBox;

    @FXML
    private ComboBox<String> statusFilterComboBox;

    @FXML
    private ComboBox<String> commissionFilterComboBox;

    // Injected the same way MainController got eventsTabController: event-detail.fxml
    // is included here with fx:id="eventDetail".
    @FXML
    private EventDetailController eventDetailController;

    public EventDetailController getEventDetailController() {
        return eventDetailController;
    }

    @FXML
    private void initialize() {
        typeFilterComboBox.getItems().setAll("All", "LMSR", "Order Book");
        typeFilterComboBox.getSelectionModel().select("All");

        statusFilterComboBox.getItems().setAll("All", "Not Started", "Active", "Closed");
        statusFilterComboBox.getSelectionModel().select("All");

        commissionFilterComboBox.getItems().setAll("All", "On Purchase", "On Close");
        commissionFilterComboBox.getSelectionModel().select("All");

        typeFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        statusFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        commissionFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
    }

    public void setEngine(EngineManager engine) {
        this.engine = engine;

        //Cell factory to turn Event into text in the ListView — name plus
        //status and type (spec line 636-644); commission is only shown in
        //the detail panel, not repeated here.
        eventsListView.setCellFactory(listView -> new ListCell<Event>() {
            @Override
            protected void updateItem(Event event, boolean empty) {
                super.updateItem(event, empty);
                setText(empty || event == null ? null : String.format(
                        "%s  [%s | %s]", event.getName(), event.getStatus(), event.getMethod()));
            }
        });

        //Set listener to send selected event to EventDetailsController
        eventsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldEvent, newEvent) -> {
            eventDetailController.showEvent(newEvent);
        });



    }

    /** Called by MainController once a file finishes loading successfully. */
    public void refreshEvents() {
        allEvents = engine.getAllEvents();
        applyFilters();
    }

    /**
     * Re-renders the currently selected event's detail panel without
     * changing the selection — needed when the acting user changes (the
     * Activate/Buy/Close buttons' enabled state depends on who's acting, not
     * just on the event, so switching users must re-gate them immediately).
     */
    public void refreshSelectedEventDetail() {
        eventDetailController.showEvent(eventsListView.getSelectionModel().getSelectedItem());
    }

    private void applyFilters() {
        String typeFilter = typeFilterComboBox.getValue();
        String statusFilter = statusFilterComboBox.getValue();
        String commissionFilter = commissionFilterComboBox.getValue();

        List<Event> filtered = new ArrayList<>();
        for (Event event : allEvents) {
            if (matchesType(event, typeFilter) && matchesStatus(event, statusFilter) && matchesCommission(event, commissionFilter)) {
                filtered.add(event);
            }
        }

        eventsListView.getItems().setAll(filtered);
    }

    private boolean matchesType(Event event, String filter) {
        return switch (filter) {
            case "LMSR" -> event.getMethod() == TradingMethod.LMSR;
            case "Order Book" -> event.getMethod() == TradingMethod.ORDER_BOOK;
            default -> true; // "All"
        };
    }

    private boolean matchesStatus(Event event, String filter) {
        return switch (filter) {
            case "Not Started" -> event.getStatus() == EventStatus.NOT_STARTED;
            case "Active" -> event.getStatus() == EventStatus.ACTIVE;
            case "Closed" -> event.getStatus() == EventStatus.CLOSED;
            default -> true; // "All"
        };
    }

    private boolean matchesCommission(Event event, String filter) {
        return switch (filter) {
            case "On Purchase" -> event.getCommissionType().equals("on-purchase");
            case "On Close" -> event.getCommissionType().equals("on-close");
            default -> true; // "All"
        };
    }
}
