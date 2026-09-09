package com.guessmarket.gui.controller;

import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.engine.model.Event;
import com.guessmarket.engine.model.EventStatus;
import com.guessmarket.engine.model.Option;
import com.guessmarket.engine.model.TradeRecord;
import com.guessmarket.engine.model.TradingMethod;
import com.guessmarket.engine.model.User;
import com.guessmarket.engine.util.LmsrCalculator;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Controller for event-detail.fxml — the right-hand panel from the sketch:
 * title/status/Activate button, the two option panes side by side (each with
 * a buy-quantity Spinner + Buy button, and a "this wins" close button), and
 * the event-wide account state + trade history below.
 */
public class EventDetailController {

    @FXML
    private Label eventTitleLabel;

    @FXML
    private Label eventStatusLabel;

    @FXML
    private Button activateButton;

    @FXML
    private VBox optionOnePane;

    @FXML
    private VBox optionTwoPane;

    @FXML
    private Label accountStateLabel;

    @FXML
    private ListView<TradeRecord> tradeHistoryListView;

    private EngineManager engine;

    // Evaluated fresh on every button click rather than kept in sync —
    // MainController hands this down as a method reference to its own
    // getCurrentUser().
    private Supplier<User> currentUserSupplier;

    // Run after any successful activate/buy/close — MainController wires
    // this to refresh the events list and the Users tab, since balances and
    // event state change on both sides of a trade.
    private Runnable onActionCompleted;

    // The event currently shown — needed by the button handlers, which fire
    // later than showEvent() and can't rely on a method parameter.
    private Event currentEvent;

    public void setEngine(EngineManager engine) {
        this.engine = engine;
    }

    public void setCurrentUserSupplier(Supplier<User> currentUserSupplier) {
        this.currentUserSupplier = currentUserSupplier;
    }

    public void setOnActionCompleted(Runnable onActionCompleted) {
        this.onActionCompleted = onActionCompleted;
    }

    @FXML
    private void initialize() {
        tradeHistoryListView.setCellFactory(listView -> new ListCell<TradeRecord>() {
            @Override
            protected void updateItem(TradeRecord trade, boolean empty) {
                super.updateItem(trade, empty);
                setText(empty || trade == null ? null : String.format(
                        "%s — %s: %d shares, price paid: %.2f, commission paid: %.2f",
                        trade.getUsername(), trade.getOptionName(), trade.getQuantity(),
                        trade.getPricePaid(), trade.getCommissionAmount()));
            }
        });
    }

    /** Called by EventsController whenever the selected event changes. */
    public void showEvent(Event event) {
        this.currentEvent = event;

        if (event == null) {
            eventTitleLabel.setText("Select Event");
            eventStatusLabel.setText("");
            activateButton.setDisable(true);
            optionOnePane.getChildren().clear();
            optionTwoPane.getChildren().clear();
            accountStateLabel.setText("");
            tradeHistoryListView.getItems().clear();
            return;
        }

        eventTitleLabel.setText(event.getName());

        if (event.getStatus() == EventStatus.CLOSED) {
            String winnerName = event.getOptions().get(event.getWinningOptionIndex()).getName();
            eventStatusLabel.setText("Status: CLOSED — Winner: " + winnerName);
        } else {
            eventStatusLabel.setText("Status: " + event.getStatus());
        }

        User actingUser = currentUserSupplier != null ? currentUserSupplier.get() : null;
        boolean canActivate = event.getStatus() == EventStatus.NOT_STARTED
                && actingUser != null && actingUser.isMarketMakerFor(event.getId());
        activateButton.setDisable(!canActivate);

        showOptionPane(optionOnePane, event, 0);
        showOptionPane(optionTwoPane, event, 1);

        accountStateLabel.setText(String.format(
                "Event account balance: %.2f   |   Commission (%s, %.0f%%): %.2f collected",
                event.getAccountBalance(), event.getCommissionType(), event.getCommissionRate(),
                event.getTotalCommissionsCollected()));

        // Full event-wide trade history (every user), newest first — the
        // per-user filtered version lives on the Users tab instead.
        List<TradeRecord> trades = new ArrayList<>(event.getTradeHistory());
        Collections.reverse(trades);
        tradeHistoryListView.getItems().setAll(trades);
    }

    private void showOptionPane(VBox pane, Event event, int optionIndex) {
        Option option = event.getOptions().get(optionIndex);

        List<Node> nodes = new ArrayList<>();
        nodes.add(new Label(option.getName()));
        nodes.add(new Label("Shares: " + option.getShares()));

        if (event.getMethod() == TradingMethod.LMSR) {
            List<Integer> allQuantities = new ArrayList<>();
            for (Option opt : event.getOptions()) {
                allQuantities.add(opt.getShares());
            }
            double price = LmsrCalculator.calculateOptionPrice(option.getShares(), allQuantities, event.getbParameter());
            nodes.add(new Label(String.format("Current price: %.4f", price)));
        } else {
            // TODO: Order Book pricing (bid/ask book) once that mechanism exists.
            nodes.add(new Label("Order Book pricing not yet supported"));
        }

        User actingUser = currentUserSupplier != null ? currentUserSupplier.get() : null;

        Spinner<Integer> quantitySpinner = new Spinner<>(1, 1_000_000, 1);
        quantitySpinner.setEditable(true);
        quantitySpinner.setPrefWidth(80);

        Button buyButton = new Button("Buy");
        boolean canBuy = event.getStatus() == EventStatus.ACTIVE && actingUser != null && !actingUser.isBlocked();
        quantitySpinner.setDisable(!canBuy);
        buyButton.setDisable(!canBuy);
        buyButton.setOnAction(actionEvent -> {
            User user = currentUserSupplier.get();
            int amount = quantitySpinner.getValue();
            String result = engine.buyShares(event.getId(), optionIndex, amount, user.getName());
            handleActionResult(result);
        });

        Button closeButton = new Button("Close — this wins");
        boolean canClose = event.getStatus() == EventStatus.ACTIVE
                && actingUser != null && actingUser.isMarketMakerFor(event.getId());
        closeButton.setDisable(!canClose);
        closeButton.setOnAction(actionEvent -> {
            User user = currentUserSupplier.get();
            String result = engine.closeEvent(event.getId(), optionIndex, user.getName());
            handleActionResult(result);
        });

        nodes.add(new HBox(5, quantitySpinner, buyButton));
        nodes.add(closeButton);

        pane.getChildren().setAll(nodes);
    }

    @FXML
    private void handleActivate() {
        if (currentEvent == null || currentUserSupplier == null) {
            return;
        }
        User user = currentUserSupplier.get();
        if (user == null) {
            return;
        }
        String result = engine.activateEvent(currentEvent.getId(), user.getName());
        handleActionResult(result);
    }

    // Shows the engine's result message, then refreshes this panel (the
    // event's own state just changed) and notifies MainController so the
    // events list and Users tab pick up the same change.
    private void handleActionResult(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.showAndWait();

        showEvent(currentEvent);

        if (onActionCompleted != null) {
            onActionCompleted.run();
        }
    }
}
