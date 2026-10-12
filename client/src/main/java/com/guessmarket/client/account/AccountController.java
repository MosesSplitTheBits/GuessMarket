package com.guessmarket.client.account;

import com.guessmarket.client.http.HttpClientUtil;
import com.guessmarket.dto.ApiConstants;
import com.guessmarket.dto.EventDetailsDto;
import com.guessmarket.dto.LedgerEntryDto;
import com.guessmarket.dto.OptionDto;
import com.guessmarket.dto.TradeDto;
import com.guessmarket.dto.UserDetailsDto;
import com.guessmarket.dto.UserSummaryDto;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Controller for account-tab.fxml — the user's private screen (Ex3 sketch,
 * "Account"): file upload, the users table (spec: every other user's name,
 * balance and whether they're an MM), my balance + load funds, the events I
 * take part in / own with my involvement in the selected one (ported from
 * Ex2's UserDetailController), and the account history (ledger).
 *
 * Fed by refreshers (updateUsers / updateMe / appendLedger /
 * showEventDetails, always called on the JavaFX thread); actions are async
 * HTTP calls whose answer is shown in a label when it comes back.
 */
public class AccountController {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    @FXML private Button loadFileButton;
    @FXML private Label filePathLabel;
    @FXML private Label uploadResultLabel;

    @FXML private TableView<UserSummaryDto> usersTable;
    @FXML private TableColumn<UserSummaryDto, String> userNameColumn;
    @FXML private TableColumn<UserSummaryDto, String> userBalanceColumn;
    @FXML private TableColumn<UserSummaryDto, String> userMmColumn;

    @FXML private Label accountNameLabel;
    @FXML private Label accountBalanceLabel;
    @FXML private TextField depositField;
    @FXML private Button depositButton;
    @FXML private Label depositResultLabel;

    @FXML private ListView<String> myEventsListView;
    @FXML private Label involvementTitleLabel;
    @FXML private Label holdingsLabel;
    @FXML private ListView<TradeDto> myTradesListView;
    @FXML private Label closedSummaryLabel;

    @FXML private TableView<LedgerEntryDto> ledgerTable;
    @FXML private TableColumn<LedgerEntryDto, String> ledgerTimeColumn;
    @FXML private TableColumn<LedgerEntryDto, String> ledgerDescriptionColumn;
    @FXML private TableColumn<LedgerEntryDto, String> ledgerAmountColumn;
    @FXML private TableColumn<LedgerEntryDto, String> ledgerBalanceColumn;

    private String username;
    private Runnable refreshMeNow = () -> { };
    private Runnable refreshEventNow = () -> { };

    // Last data shown — unchanged polls are skipped (records compare by value).
    private List<UserSummaryDto> lastUsers = List.of();
    private UserDetailsDto lastMe;
    private EventDetailsDto lastEventDetails;

    // My event selected in the list — written on the JavaFX thread, read by
    // the event-detail refresher's Timer thread, hence volatile.
    private volatile String selectedEventName;

    public void setUsername(String username) {
        this.username = username;
        accountNameLabel.setText(username);
    }

    /** Set by MainController: fetch /me at once (after a deposit or upload). */
    public void setRefreshMeNow(Runnable refreshMeNow) {
        this.refreshMeNow = refreshMeNow;
    }

    /** Set by MainController: fetch the selected event's details at once. */
    public void setRefreshEventNow(Runnable refreshEventNow) {
        this.refreshEventNow = refreshEventNow;
    }

    /** Asked by the event-detail refresher every tick. */
    public String getSelectedEventName() {
        return selectedEventName;
    }

    @FXML
    private void initialize() {
        // Records have no getX() methods, so PropertyValueFactory can't read
        // them — each column says explicitly how to get its text.
        userNameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        userBalanceColumn.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%.2f", c.getValue().balance()) + (c.getValue().blocked() ? "  (BLOCKED)" : "")));
        userMmColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().marketMaker() ? "Yes" : "No"));
        usersTable.setPlaceholder(new Label("No other users yet"));

        ledgerTimeColumn.setCellValueFactory(c -> new SimpleStringProperty(
                TIME_FORMAT.format(Instant.ofEpochMilli(c.getValue().timestamp()))));
        ledgerDescriptionColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().description()));
        ledgerAmountColumn.setCellValueFactory(c -> new SimpleStringProperty(String.format("%+.2f", c.getValue().amount())));
        ledgerBalanceColumn.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().balanceAfter())));
        ledgerAmountColumn.setCellFactory(column -> new TableCell<>() { // green in, red out
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setText(empty ? null : text);
                setStyle(empty || text == null ? "" : text.startsWith("-") ? "-fx-text-fill: #c62828;" : "-fx-text-fill: #2e7d32;");
            }
        });
        ledgerTable.setPlaceholder(new Label("No account activity yet"));

        myTradesListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(TradeDto trade, boolean empty) {
                super.updateItem(trade, empty);
                setText(empty || trade == null ? null : String.format(
                        "%s %s — %d shares, price: %.2f, commission: %.2f",
                        trade.side(), trade.optionName(), trade.quantity(), trade.pricePaid(), trade.commission()));
            }
        });

        myEventsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            String name = newItem == null ? null : eventNameOf(newItem);
            if (!Objects.equals(name, selectedEventName)) {
                selectedEventName = name;
                lastEventDetails = null;
                clearInvolvement();
                if (name != null) {
                    refreshEventNow.run();
                }
            }
        });

        clearInvolvement();
    }

    // ---------------------------------------------------------------- polled data

    /** Spec: the user sees every OTHER user — so our own row is left out. */
    public void updateUsers(List<UserSummaryDto> users) {
        if (users.equals(lastUsers)) {
            return;
        }
        lastUsers = users;
        usersTable.getItems().setAll(users.stream().filter(u -> !u.name().equals(username)).toList());
    }

    public void updateMe(UserDetailsDto me) {
        if (me.equals(lastMe)) {
            return;
        }
        lastMe = me;
        accountBalanceLabel.setText(String.format("Balance: %.2f%s", me.balance(),
                me.blocked() ? "   (BLOCKED — deposit to unblock)" : ""));
        accountBalanceLabel.setStyle(me.blocked() ? "-fx-font-size: 16px; -fx-text-fill: #c62828;" : "-fx-font-size: 16px;");

        // One line per event, tagged with my role(s). Upload order is kept.
        Set<String> names = new LinkedHashSet<>(me.managedEvents());
        names.addAll(me.participatingEvents());
        List<String> rows = new ArrayList<>();
        for (String name : names) {
            boolean mm = me.managedEvents().contains(name);
            boolean participant = me.participatingEvents().contains(name);
            rows.add(name + "  [" + (mm && participant ? "MM, participant" : mm ? "MM" : "participant") + "]");
        }
        if (rows.equals(myEventsListView.getItems())) {
            return;
        }
        // Keep the selection across the update (by event name).
        String keep = selectedEventName;
        myEventsListView.getItems().setAll(rows);
        if (keep != null) {
            rows.stream().filter(row -> eventNameOf(row).equals(keep)).findFirst()
                    .ifPresent(row -> myEventsListView.getSelectionModel().select(row));
        }
    }

    /** New ledger lines only (delta fetch) — appended, newest at the bottom. */
    public void appendLedger(List<LedgerEntryDto> entries) {
        ledgerTable.getItems().addAll(entries);
        ledgerTable.scrollTo(ledgerTable.getItems().size() - 1);
    }

    /** My involvement in the selected event: holdings, my trades, final result. */
    public void showEventDetails(EventDetailsDto details) {
        if (details == null || !details.name().equals(selectedEventName) || details.equals(lastEventDetails)) {
            return; // stale answer for a previous selection, or nothing changed
        }
        lastEventDetails = details;

        involvementTitleLabel.setText("My trades in \"" + details.name() + "\" (" + details.status() + "):");

        if (details.method().equals("ORDER_BOOK")) {
            StringBuilder holdings = new StringBuilder("Current holdings: ");
            for (int i = 0; i < details.options().size(); i++) {
                OptionDto option = details.options().get(i);
                holdings.append(i > 0 ? ", " : "").append(option.name()).append('=')
                        .append(option.holdings().getOrDefault(username, 0));
            }
            holdingsLabel.setText(holdings.toString());
        } else {
            holdingsLabel.setText("");
        }

        List<TradeDto> mine = new ArrayList<>(details.trades().stream()
                .filter(trade -> trade.username().equals(username)).toList());
        Collections.reverse(mine); // newest first
        myTradesListView.getItems().setAll(mine);

        if (details.status().equals("CLOSED") && details.winningOptionIndex() != null) {
            StringBuilder summary = new StringBuilder("Event closed — final shares per option:\n");
            for (OptionDto option : details.options()) {
                summary.append(String.format("  %s: %d shares%n", option.name(), option.shares()));
            }
            summary.append("Winning option: ").append(details.options().get(details.winningOptionIndex()).name());
            closedSummaryLabel.setText(summary.toString());
        } else {
            closedSummaryLabel.setText("");
        }
    }

    // ---------------------------------------------------------------- actions

    @FXML
    private void handleLoadFile() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));
        File file = chooser.showOpenDialog(loadFileButton.getScene().getWindow());
        if (file == null) {
            return; // dialog cancelled
        }

        filePathLabel.setText(file.getAbsolutePath());
        showResult(uploadResultLabel, null, "Uploading...");
        loadFileButton.setDisable(true);

        // Spec: upload is asynchronous by nature now (it goes over the network),
        // so no artificial delay — the answer simply arrives when it arrives.
        HttpClientUtil.uploadAsync(file, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Platform.runLater(() -> {
                    showResult(uploadResultLabel, false, "Cannot reach the server: " + e.getMessage());
                    loadFileButton.setDisable(false);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = HttpClientUtil.bodyOf(response);
                boolean ok = response.code() == 200;
                Platform.runLater(() -> {
                    // On failure the server's message says WHY (bad commission,
                    // duplicate event name...), as the spec requires.
                    showResult(uploadResultLabel, ok, ok ? body : "File rejected: " + body);
                    loadFileButton.setDisable(false);
                    refreshMeNow.run();
                });
            }
        });
    }

    @FXML
    private void handleDeposit() {
        double amount;
        try {
            amount = Double.parseDouble(depositField.getText().trim());
        } catch (NumberFormatException e) {
            showResult(depositResultLabel, false, "Enter a number");
            return;
        }
        depositButton.setDisable(true);

        HttpClientUtil.postAsync(ApiConstants.DEPOSIT, Map.of(ApiConstants.AMOUNT, String.valueOf(amount)), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Platform.runLater(() -> {
                    showResult(depositResultLabel, false, "Cannot reach the server");
                    depositButton.setDisable(false);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = HttpClientUtil.bodyOf(response);
                boolean ok = response.code() == 200;
                Platform.runLater(() -> {
                    showResult(depositResultLabel, ok, body);
                    depositButton.setDisable(false);
                    if (ok) {
                        depositField.clear();
                        refreshMeNow.run();
                    }
                });
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    /** ok: true = green, false = red, null = neutral. */
    private static void showResult(Label label, Boolean ok, String message) {
        label.setText(message);
        label.setStyle(ok == null ? "" : ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
    }

    private void clearInvolvement() {
        involvementTitleLabel.setText("Select one of your events to see your involvement.");
        holdingsLabel.setText("");
        myTradesListView.getItems().clear();
        closedSummaryLabel.setText("");
    }

    /** "World Cap Winner  [MM]" -> "World Cap Winner". */
    private static String eventNameOf(String row) {
        int tag = row.lastIndexOf("  [");
        return tag < 0 ? row : row.substring(0, tag);
    }
}
