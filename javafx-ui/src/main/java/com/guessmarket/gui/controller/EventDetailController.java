package com.guessmarket.gui.controller;

import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.engine.model.Event;
import com.guessmarket.engine.model.EventStatus;
import com.guessmarket.engine.model.Option;
import com.guessmarket.engine.model.Order;
import com.guessmarket.engine.model.OrderBook;
import com.guessmarket.engine.model.OrderSide;
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
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
    private VBox participantsPane;

    @FXML
    private ListView<String> participantsListView;

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
                        "%s %s %s: %d shares, price: %.2f, commission: %.2f",
                        trade.getUsername(), trade.getSide(), trade.getOptionName(), trade.getQuantity(),
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
            eventStatusLabel.setStyle("");
            activateButton.setDisable(true);
            optionOnePane.getChildren().clear();
            optionTwoPane.getChildren().clear();
            accountStateLabel.setText("");
            participantsPane.setVisible(false);
            participantsPane.setManaged(false);
            participantsListView.getItems().clear();
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
        // Explicitly set on every status (not just NOT_STARTED/ACTIVE) so a
        // color from a previously-selected event's status never lingers —
        // setStyle persists on the Label until something overwrites it.
        eventStatusLabel.setStyle(statusColorStyle(event.getStatus()));

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

        // Participant holdings only make sense for Order Book — LMSR's own
        // per-user standing is already visible via the Users tab's trade
        // history, and LMSR never tracks a live per-user share balance.
        boolean isOrderBook = event.getMethod() == TradingMethod.ORDER_BOOK;
        participantsPane.setVisible(isOrderBook);
        participantsPane.setManaged(isOrderBook);
        participantsListView.getItems().setAll(isOrderBook ? buildParticipantSummaries(event) : List.of());

        // Full event-wide trade history (every user), newest first — the
        // per-user filtered version lives on the Users tab instead.
        List<TradeRecord> trades = new ArrayList<>(event.getTradeHistory());
        Collections.reverse(trades);
        tradeHistoryListView.getItems().setAll(trades);
    }

    private String statusColorStyle(EventStatus status) {
        return switch (status) {
            case NOT_STARTED -> "-fx-text-fill: red; -fx-font-weight: bold;";
            case ACTIVE -> "-fx-text-fill: green; -fx-font-weight: bold;";
            case CLOSED -> "-fx-text-fill: #444444; -fx-font-weight: bold;";
        };
    }

    private void showOptionPane(VBox pane, Event event, int optionIndex) {
        Option option = event.getOptions().get(optionIndex);

        List<Node> nodes = new ArrayList<>();
        nodes.add(new Label(option.getName()));
        nodes.add(new Label("Shares outstanding: " + option.getShares()));

        User actingUser = currentUserSupplier != null ? currentUserSupplier.get() : null;
        boolean canTrade = event.getStatus() == EventStatus.ACTIVE && actingUser != null && !actingUser.isBlocked();

        if (event.getMethod() == TradingMethod.LMSR) {
            List<Integer> allQuantities = new ArrayList<>();
            for (Option opt : event.getOptions()) {
                allQuantities.add(opt.getShares());
            }
            double price = LmsrCalculator.calculateOptionPrice(option.getShares(), allQuantities, event.getbParameter());
            nodes.add(new Label(String.format("Current price: %.4f", price)));
            nodes.add(buildLmsrBuyControls(event, optionIndex, canTrade));
        } else {
            nodes.addAll(buildOrderBookNodes(event, option, optionIndex, actingUser, canTrade));
        }

        Button closeButton = new Button("Close — this wins");
        boolean canClose = event.getStatus() == EventStatus.ACTIVE
                && actingUser != null && actingUser.isMarketMakerFor(event.getId());
        closeButton.setDisable(!canClose);
        closeButton.setOnAction(actionEvent -> {
            User user = currentUserSupplier.get();
            String result = engine.closeEvent(event.getId(), optionIndex, user.getName());
            handleActionResult(result);
        });
        nodes.add(closeButton);

        pane.getChildren().setAll(nodes);
    }

    private HBox buildLmsrBuyControls(Event event, int optionIndex, boolean canBuy) {
        Spinner<Integer> quantitySpinner = new Spinner<>(1, 1_000_000, 1);
        quantitySpinner.setEditable(true);
        quantitySpinner.setPrefWidth(80);

        Button buyButton = new Button("Buy");
        quantitySpinner.setDisable(!canBuy);
        buyButton.setDisable(!canBuy);
        buyButton.setOnAction(actionEvent -> {
            User user = currentUserSupplier.get();
            int amount = quantitySpinner.getValue();
            String result = engine.buyShares(event.getId(), optionIndex, amount, user.getName());
            handleActionResult(result);
        });

        return new HBox(5, quantitySpinner, buyButton);
    }

    /**
     * Order Book pane: live book stats (last/bid/ask/mid/spread), the
     * resting bids/asks themselves, the acting user's current holdings, and
     * a BUY/SELL order-entry form wired to EngineManager.placeOrder.
     */
    private List<Node> buildOrderBookNodes(Event event, Option option, int optionIndex, User actingUser, boolean canTrade) {
        List<Node> nodes = new ArrayList<>();
        OrderBook book = option.getOrderBook();

        // Split across two short lines rather than one long one — a single
        // line with all five stats is wider than the option pane's minWidth
        // comfortably allows, which is exactly the kind of cramping that
        // made the Buy/Sell controls further down unreadable at the app's
        // old (unset) default window size.
        nodes.add(new Label(String.format("Last: %s   Bid: %s   Ask: %s",
                formatPrice(book.getLastTradePrice()), formatPrice(book.getBestBidPrice()), formatPrice(book.getBestAskPrice()))));
        nodes.add(new Label(String.format("Mid: %s   Spread: %s",
                formatPrice(book.getMidPrice()), formatPrice(book.getSpread()))));

        nodes.add(new Label("Bids (buy):"));
        nodes.add(buildOrderListLabel(book.getBids()));
        nodes.add(new Label("Asks (sell):"));
        nodes.add(buildOrderListLabel(book.getAsks()));

        if (actingUser != null) {
            nodes.add(new Label("Your holdings: " + option.getHolding(actingUser.getName())));
        }

        ToggleGroup sideGroup = new ToggleGroup();
        RadioButton buyRadio = new RadioButton("Buy");
        RadioButton sellRadio = new RadioButton("Sell");
        buyRadio.setToggleGroup(sideGroup);
        sellRadio.setToggleGroup(sideGroup);
        buyRadio.setSelected(true);

        Spinner<Integer> quantitySpinner = new Spinner<>(1, 1_000_000, 1);
        quantitySpinner.setEditable(true);
        quantitySpinner.setPrefWidth(65);

        TextField priceField = new TextField();
        priceField.setPromptText(String.format("0.01–%.2f", event.getBaseValue() - 0.01));
        priceField.setPrefWidth(80);

        Button placeOrderButton = new Button("Place Order");
        placeOrderButton.setMaxWidth(Double.MAX_VALUE);

        buyRadio.setDisable(!canTrade);
        sellRadio.setDisable(!canTrade);
        quantitySpinner.setDisable(!canTrade);
        priceField.setDisable(!canTrade);
        placeOrderButton.setDisable(!canTrade);

        placeOrderButton.setOnAction(actionEvent -> {
            User user = currentUserSupplier.get();
            int quantity = quantitySpinner.getValue();
            OrderSide side = buyRadio.isSelected() ? OrderSide.BUY : OrderSide.SELL;
            double price;
            try {
                price = Double.parseDouble(priceField.getText().trim());
            } catch (NumberFormatException e) {
                handleActionResult("Please enter a valid numeric price.");
                return;
            }
            String result = engine.placeOrder(event.getId(), optionIndex, side, quantity, price, user.getName());
            handleActionResult(result);
        });

        // Split across three short rows instead of one long one — a single
        // HBox with both radios, a spinner, a text field, and a button needs
        // more width than a ~260px option pane comfortably gives it, which
        // was exactly why "Buy"/"Sell" text was getting clipped before the
        // window was manually enlarged.
        HBox sideRow = new HBox(10, buyRadio, sellRadio);
        HBox amountRow = new HBox(5, new Label("Qty:"), quantitySpinner, new Label("Price:"), priceField);
        VBox tradeForm = new VBox(5, sideRow, amountRow, placeOrderButton);
        nodes.add(tradeForm);
        return nodes;
    }

    private Label buildOrderListLabel(List<Order> orders) {
        if (orders.isEmpty()) {
            return new Label("  —");
        }
        StringBuilder text = new StringBuilder();
        for (Order order : orders) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(String.format("  %s: %d @ %.2f", order.getUsername(), order.getRemainingQuantity(), order.getPrice()));
        }
        return new Label(text.toString());
    }

    private String formatPrice(Double value) {
        return value == null ? "—" : String.format("%.2f", value);
    }

    private List<String> buildParticipantSummaries(Event event) {
        List<Option> options = event.getOptions();
        Map<String, int[]> holdingsByUser = new TreeMap<>();

        for (int i = 0; i < options.size(); i++) {
            for (Map.Entry<String, Integer> entry : options.get(i).getAllHoldings().entrySet()) {
                holdingsByUser.computeIfAbsent(entry.getKey(), k -> new int[options.size()])[i] = entry.getValue();
            }
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : holdingsByUser.entrySet()) {
            StringBuilder line = new StringBuilder(entry.getKey()).append(": ");
            for (int i = 0; i < options.size(); i++) {
                if (i > 0) {
                    line.append(", ");
                }
                int quantity = entry.getValue()[i];
                // "Value" here is a mark-to-market estimate (mid-price, or
                // last trade if the book has no live quotes right now) —
                // never the base value d, since a share is only guaranteed
                // to be worth d if it turns out to be the winning option.
                OrderBook book = options.get(i).getOrderBook();
                Double perShare = book.getMidPrice() != null ? book.getMidPrice() : book.getLastTradePrice();
                String valueText = perShare == null ? "?" : String.format("%.2f", perShare * quantity);
                line.append(options.get(i).getName()).append('=').append(quantity)
                        .append(" (~$").append(valueText).append(')');
            }
            lines.add(line.toString());
        }
        return lines;
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
