package com.guessmarket.client.events;

import com.guessmarket.dto.EventSummaryDto;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Controller for events-tab.fxml — the filter row + event list on the left,
 * plus the right-hand detail panel (event-detail.fxml, included here).
 * Ported from Ex2: same filters and list, but fed by polling (updateEvents)
 * with DTOs instead of reading the engine directly.
 */
public class EventsController {

    // The full unfiltered list from the last poll. Filters are always
    // re-applied against this, never against the already-filtered ListView
    // contents — otherwise loosening a filter couldn't bring events back.
    private List<EventSummaryDto> allEvents = new ArrayList<>();

    // True while we replace the ListView's items ourselves — the selection
    // listener ignores the temporary "nothing selected" this causes.
    private boolean updatingList;

    @FXML
    private ListView<EventSummaryDto> eventsListView;

    @FXML
    private ComboBox<String> typeFilterComboBox;

    @FXML
    private ComboBox<String> statusFilterComboBox;

    @FXML
    private ComboBox<String> commissionFilterComboBox;

    // Injected by FXMLLoader: event-detail.fxml is included with fx:id="eventDetail".
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

        // Name plus status, type and MM in the list; everything else lives in
        // the detail panel.
        eventsListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(EventSummaryDto event, boolean empty) {
                super.updateItem(event, empty);
                setText(empty || event == null ? null : String.format("%s  [%s | %s | MM: %s]",
                        event.name(), event.status(), event.method(), event.marketMaker()));
            }
        });

        eventsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldEvent, newEvent) -> {
            if (!updatingList) {
                eventDetailController.selectEvent(newEvent == null ? null : newEvent.name());
            }
        });
    }

    /**
     * Called (on the JavaFX thread) with every poll's result. Records compare by
     * value, so an unchanged list is detected with a single equals() and the
     * screen isn't touched at all — no flicker, no lost selection.
     */
    public void updateEvents(List<EventSummaryDto> events) {
        if (events.equals(allEvents)) {
            return;
        }
        allEvents = events;
        applyFilters();
    }

    private void applyFilters() {
        String typeFilter = typeFilterComboBox.getValue();
        String statusFilter = statusFilterComboBox.getValue();
        String commissionFilter = commissionFilterComboBox.getValue();

        List<EventSummaryDto> filtered = new ArrayList<>();
        for (EventSummaryDto event : allEvents) {
            if (matchesType(event, typeFilter) && matchesStatus(event, statusFilter)
                    && matchesCommission(event, commissionFilter)) {
                filtered.add(event);
            }
        }
        if (filtered.equals(eventsListView.getItems())) {
            return;
        }

        // Replacing the items clears the selection; put it back (by NAME — the
        // DTO objects are new each poll) so the detail panel isn't reset under
        // the user's hands. Only tell the detail panel if the selected event
        // really changed (e.g. a filter hid it).
        String selectedBefore = selectedName();
        updatingList = true;
        eventsListView.getItems().setAll(filtered);
        if (selectedBefore != null) {
            filtered.stream()
                    .filter(event -> event.name().equals(selectedBefore))
                    .findFirst()
                    .ifPresent(event -> eventsListView.getSelectionModel().select(event));
        }
        updatingList = false;

        String selectedAfter = selectedName();
        if (!Objects.equals(selectedBefore, selectedAfter)) {
            eventDetailController.selectEvent(selectedAfter);
        }
    }

    private String selectedName() {
        EventSummaryDto selected = eventsListView.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.name();
    }

    private boolean matchesType(EventSummaryDto event, String filter) {
        return switch (filter) {
            case "LMSR" -> event.method().equals("LMSR");
            case "Order Book" -> event.method().equals("ORDER_BOOK");
            default -> true; // "All"
        };
    }

    private boolean matchesStatus(EventSummaryDto event, String filter) {
        return switch (filter) {
            case "Not Started" -> event.status().equals("NOT_STARTED");
            case "Active" -> event.status().equals("ACTIVE");
            case "Closed" -> event.status().equals("CLOSED");
            default -> true; // "All"
        };
    }

    private boolean matchesCommission(EventSummaryDto event, String filter) {
        return switch (filter) {
            case "On Purchase" -> event.commissionType().equals("on-purchase");
            case "On Close" -> event.commissionType().equals("on-close");
            default -> true; // "All"
        };
    }
}
