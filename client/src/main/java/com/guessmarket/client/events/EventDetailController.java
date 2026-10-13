package com.guessmarket.client.events;

import com.guessmarket.client.http.HttpClientUtil;
import com.guessmarket.dto.ApiConstants;
import com.guessmarket.dto.EventDetailsDto;
import com.guessmarket.dto.OptionDto;
import com.guessmarket.dto.OrderBookDto;
import com.guessmarket.dto.OrderDto;
import com.guessmarket.dto.TradeDto;
import javafx.application.Platform;
import javafx.fxml.FXML;
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
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Controller for event-detail.fxml — the selected event's details and trading
 * controls. Ported from Ex2's EventDetailController with two big changes:
 *
 * 1. Data arrives as an EventDetailsDto from polling (showDetails), and every
 *    action is an HTTP POST whose answer is shown when it comes back.
 * 2. Ex2 rebuilt every control on each refresh. With a poll every 500ms that
 *    would wipe whatever the user is typing, so the option panes' controls are
 *    built ONCE per selected event, and each poll only updates labels and
 *    enabled/disabled state. An unchanged DTO (records compare by value)
 *    skips the update entirely.
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
    private Label marketMakerLabel;

    @FXML
    private Label accountStateLabel;

    @FXML
    private VBox participantsPane;

    @FXML
    private ListView<String> participantsListView;

    @FXML
    private ListView<TradeDto> tradeHistoryListView;

    private String username;              // the logged-in user — gates MM-only buttons
    private Runnable refreshNow = () -> { }; // set by MainController: fetch details immediately

    // The selected event (null = none). Written on the JavaFX thread, READ by
    // the refresher's Timer thread — volatile guarantees that thread sees the
    // latest value instead of a stale copy.
    private volatile String currentEventName;
    private EventDetailsDto currentDetails; // what's on screen right now (null = nothing yet)
    private final List<OptionView> optionViews = new ArrayList<>();

    public void setUsername(String username) {
        this.username = username;
    }

    public void setRefreshNow(Runnable refreshNow) {
        this.refreshNow = refreshNow;
    }

    /** The refresher asks this every tick: which event should I fetch? */
    public String getCurrentEventName() {
        return currentEventName;
    }

    @FXML
    private void initialize() {
        tradeHistoryListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(TradeDto trade, boolean empty) {
                super.updateItem(trade, empty);
                setText(empty || trade == null ? null : String.format(
                        "%s %s %s: %d shares, price: %.2f, commission: %.2f",
                        trade.username(), trade.side(), trade.optionName(), trade.quantity(),
                        trade.pricePaid(), trade.commission()));
            }
        });
        clear("Select an event");
    }

    /** Called by EventsController when the selection changes (null = none). */
    public void selectEvent(String eventName) {
        if (Objects.equals(eventName, currentEventName)) {
            return;
        }
        currentEventName = eventName;
        currentDetails = null;
        clear(eventName == null ? "Select an event" : eventName);
        if (eventName != null) {
            refreshNow.run(); // don't make the user wait for the next tick
        }
    }

    /** Called (on the JavaFX thread) with each poll's result for the selected event. */
    public void showDetails(EventDetailsDto details) {
        // A slow answer about a PREVIOUSLY selected event can arrive after the
        // user already clicked another one — ignore it.
        if (details == null || !details.name().equals(currentEventName)) {
            return;
        }
        if (details.equals(currentDetails)) {
            return; // nothing changed since the last poll — touch nothing
        }

        if (currentDetails == null) {
            buildOptionViews(details); // first data for this event: build the controls once
        }
        currentDetails = details;
        render(details);
    }

    // ---------------------------------------------------------------- rendering

    private void clear(String title) {
        eventTitleLabel.setText(title);
        eventStatusLabel.setText("");
        eventStatusLabel.setStyle("");
        activateButton.setDisable(true);
        optionViews.clear();
        optionOnePane.getChildren().clear();
        optionTwoPane.getChildren().clear();
        marketMakerLabel.setText("");
        accountStateLabel.setText("");
        participantsPane.setVisible(false);
        participantsPane.setManaged(false);
        participantsListView.getItems().clear();
        tradeHistoryListView.getItems().clear();
    }

    private void buildOptionViews(EventDetailsDto details) {
        optionViews.clear();
        VBox[] panes = {optionOnePane, optionTwoPane};
        for (int i = 0; i < panes.length; i++) {
            OptionView view = new OptionView(i, isOrderBook(details), details.baseValue());
            panes[i].getChildren().setAll(view.root);
            optionViews.add(view);
        }
    }

    private void render(EventDetailsDto details) {
        eventTitleLabel.setText(details.name());

        if (details.status().equals("CLOSED") && details.winningOptionIndex() != null) {
            String winner = details.options().get(details.winningOptionIndex()).name();
            eventStatusLabel.setText("Status: CLOSED — Winner: " + winner);
        } else {
            eventStatusLabel.setText("Status: " + details.status());
        }
        eventStatusLabel.setStyle(statusColorStyle(details.status()));

        boolean isMm = details.marketMaker().equals(username);
        activateButton.setDisable(!(details.status().equals("NOT_STARTED") && isMm));

        for (OptionView view : optionViews) {
            view.update(details, isMm);
        }

        marketMakerLabel.setText("Market maker: " + details.marketMaker() + (isMm ? " (you)" : ""));
        accountStateLabel.setText(String.format(
                "Event account balance: %.2f   |   Commission (%s, %d%%): %.2f collected",
                details.accountBalance(), details.commissionType(), details.commissionRate(),
                details.totalCommissions()));

        // Holdings only make sense for Order Book (LMSR never sells, so a
        // user's standing is just their trade list).
        boolean orderBook = isOrderBook(details);
        participantsPane.setVisible(orderBook);
        participantsPane.setManaged(orderBook);
        participantsListView.getItems().setAll(orderBook ? participantSummaries(details) : List.of());

        List<TradeDto> trades = new ArrayList<>(details.trades());
        Collections.reverse(trades); // newest first
        tradeHistoryListView.getItems().setAll(trades);
    }

    private List<String> participantSummaries(EventDetailsDto details) {
        List<OptionDto> options = details.options();
        Map<String, int[]> holdingsByUser = new TreeMap<>();
        for (int i = 0; i < options.size(); i++) {
            for (Map.Entry<String, Integer> entry : options.get(i).holdings().entrySet()) {
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
                // Mark-to-market estimate (mid, else last trade) — never d,
                // which a share is only worth if its option ends up winning.
                OrderBookDto book = options.get(i).orderBook();
                Double perShare = book.midPrice() != null ? book.midPrice() : book.lastTradePrice();
                String value = perShare == null ? "?" : String.format("%.2f", perShare * quantity);
                line.append(options.get(i).name()).append('=').append(quantity)
                        .append(" (~$").append(value).append(')');
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static boolean isOrderBook(EventDetailsDto details) {
        return details.method().equals("ORDER_BOOK");
    }

    private static String statusColorStyle(String status) {
        return switch (status) {
            case "NOT_STARTED" -> "-fx-text-fill: red; -fx-font-weight: bold;";
            case "ACTIVE" -> "-fx-text-fill: green; -fx-font-weight: bold;";
            default -> "-fx-text-fill: #444444; -fx-font-weight: bold;";
        };
    }

    private static String formatPrice(Double value) {
        return value == null ? "—" : String.format("%.2f", value);
    }

    // ---------------------------------------------------------------- actions

    @FXML
    private void handleActivate() {
        if (currentEventName != null) {
            postAction(ApiConstants.ACTIVATE, Map.of(ApiConstants.EVENT_NAME, currentEventName));
        }
    }

    /**
     * Sends one action and shows the server's answer (success message or the
     * engine's error text) when it arrives, then refreshes this panel at once.
     */
    private void postAction(String path, Map<String, String> params) {
        HttpClientUtil.postAsync(path, params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Platform.runLater(() -> showResult(false, "Cannot reach the server: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = HttpClientUtil.bodyOf(response);
                boolean ok = response.code() == 200;
                Platform.runLater(() -> {
                    showResult(ok, body);
                    refreshNow.run();
                });
            }
        });
    }

    private void showResult(boolean ok, String message) {
        Alert alert = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.show(); // non-blocking: polling keeps updating the screen behind it
    }

    /** An editable Spinner only commits typed text on Enter/focus loss — do it now. */
    private static Integer committedValue(Spinner<Integer> spinner) {
        try {
            int typed = Integer.parseInt(spinner.getEditor().getText().trim());
            spinner.getValueFactory().setValue(typed);
        } catch (NumberFormatException ignored) {
            spinner.getEditor().setText(String.valueOf(spinner.getValue())); // revert garbage
        }
        return spinner.getValue();
    }

    // ---------------------------------------------------------------- one option pane

    /**
     * One option's pane. Built once per selected event; update() refreshes
     * only the parts that change, so a half-typed quantity or price survives
     * every poll.
     */
    private final class OptionView {

        final VBox root = new VBox(6);
        private final int optionIndex;

        private final Label nameLabel = new Label();
        private final Label sharesLabel = new Label();
        private final Button closeButton = new Button("Close — this wins");

        // LMSR
        private Label priceLabel;
        private Spinner<Integer> buyQuantity;
        private Button buyButton;

        // Order Book
        private Label statsLine1;
        private Label statsLine2;
        private Label bidsLabel;
        private Label asksLabel;
        private Label holdingsLabel;
        private RadioButton buyRadio;
        private RadioButton sellRadio;
        private Spinner<Integer> orderQuantity;
        private TextField priceField;
        private Button placeOrderButton;

        OptionView(int optionIndex, boolean orderBook, int baseValue) {
            this.optionIndex = optionIndex;
            root.setStyle("-fx-padding: 8;");
            nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
            root.getChildren().addAll(nameLabel, sharesLabel);

            if (orderBook) {
                buildOrderBookControls(baseValue);
            } else {
                buildLmsrControls();
            }

            closeButton.setOnAction(e -> postAction(ApiConstants.CLOSE, Map.of(
                    ApiConstants.EVENT_NAME, currentEventName,
                    ApiConstants.OPTION_INDEX, String.valueOf(optionIndex))));
            root.getChildren().add(closeButton);
        }

        private void buildLmsrControls() {
            priceLabel = new Label();
            buyQuantity = new Spinner<>(1, 1_000_000, 1);
            buyQuantity.setEditable(true);
            buyQuantity.setPrefWidth(90);
            buyButton = new Button("Buy");
            buyButton.setOnAction(e -> postAction(ApiConstants.BUY, Map.of(
                    ApiConstants.EVENT_NAME, currentEventName,
                    ApiConstants.OPTION_INDEX, String.valueOf(optionIndex),
                    ApiConstants.QUANTITY, String.valueOf(committedValue(buyQuantity)))));
            root.getChildren().addAll(priceLabel, new HBox(5, buyQuantity, buyButton));
        }

        private void buildOrderBookControls(int baseValue) {
            // Stats on two short lines — one long line is too wide for a
            // narrow option pane and cramps the trade form below it.
            statsLine1 = new Label();
            statsLine2 = new Label();
            bidsLabel = new Label();
            asksLabel = new Label();
            holdingsLabel = new Label();

            ToggleGroup sideGroup = new ToggleGroup();
            buyRadio = new RadioButton("Buy");
            sellRadio = new RadioButton("Sell");
            buyRadio.setToggleGroup(sideGroup);
            sellRadio.setToggleGroup(sideGroup);
            buyRadio.setSelected(true);

            orderQuantity = new Spinner<>(1, 1_000_000, 1);
            orderQuantity.setEditable(true);
            orderQuantity.setPrefWidth(80);

            priceField = new TextField();
            priceField.setPromptText(String.format("0.01–%.2f", baseValue - 0.01));
            priceField.setPrefWidth(80);

            placeOrderButton = new Button("Place Order");
            placeOrderButton.setMaxWidth(Double.MAX_VALUE);
            placeOrderButton.setOnAction(e -> placeOrder());

            root.getChildren().addAll(statsLine1, statsLine2,
                    new Label("Bids (buy):"), bidsLabel,
                    new Label("Asks (sell):"), asksLabel,
                    holdingsLabel,
                    new VBox(5,
                            new HBox(10, buyRadio, sellRadio),
                            new HBox(5, new Label("Qty:"), orderQuantity, new Label("Price:"), priceField),
                            placeOrderButton));
        }

        private void placeOrder() {
            double price;
            try {
                price = Double.parseDouble(priceField.getText().trim());
            } catch (NumberFormatException e) {
                showResult(false, "Please enter a valid numeric price.");
                return;
            }
            postAction(ApiConstants.ORDER, Map.of(
                    ApiConstants.EVENT_NAME, currentEventName,
                    ApiConstants.OPTION_INDEX, String.valueOf(optionIndex),
                    ApiConstants.SIDE, buyRadio.isSelected() ? "BUY" : "SELL",
                    ApiConstants.QUANTITY, String.valueOf(committedValue(orderQuantity)),
                    ApiConstants.PRICE, String.valueOf(price)));
        }

        /** Labels + enabled state only — never touches what the user typed. */
        void update(EventDetailsDto details, boolean isMm) {
            OptionDto option = details.options().get(optionIndex);
            boolean active = details.status().equals("ACTIVE");

            nameLabel.setText(option.name());
            sharesLabel.setText("Shares outstanding: " + option.shares());
            closeButton.setDisable(!(active && isMm));

            if (priceLabel != null) { // LMSR
                priceLabel.setText(String.format("Current price: %.4f", option.lmsrPrice()));
                buyQuantity.setDisable(!active);
                buyButton.setDisable(!active);
            } else {                  // Order Book
                OrderBookDto book = option.orderBook();
                statsLine1.setText(String.format("Last: %s   Bid: %s   Ask: %s",
                        formatPrice(book.lastTradePrice()), formatPrice(book.bestBid()), formatPrice(book.bestAsk())));
                statsLine2.setText(String.format("Mid: %s   Spread: %s",
                        formatPrice(book.midPrice()), formatPrice(book.spread())));
                bidsLabel.setText(ordersText(book.bids()));
                asksLabel.setText(ordersText(book.asks()));
                holdingsLabel.setText("Your holdings: " + option.holdings().getOrDefault(username, 0));
                buyRadio.setDisable(!active);
                sellRadio.setDisable(!active);
                orderQuantity.setDisable(!active);
                priceField.setDisable(!active);
                placeOrderButton.setDisable(!active);
            }
        }

        private String ordersText(List<OrderDto> orders) {
            if (orders.isEmpty()) {
                return "  —";
            }
            StringBuilder text = new StringBuilder();
            for (OrderDto order : orders) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(String.format("  %s: %d @ %.2f", order.username(), order.remainingQuantity(), order.price()));
            }
            return text.toString();
        }
    }
}
