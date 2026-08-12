package com.guessmarket.consoleui;

import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.engine.model.Event;
import com.guessmarket.engine.model.Option;
import com.guessmarket.engine.model.TradeRecord;
import com.guessmarket.engine.util.LmsrCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * The console UI. This is the ONLY class in the whole project allowed to
 * print to the console or read from Scanner — it's the "active" module
 * that drives the engine, which stays passive and just answers requests.
 */
public class Main {

    private static final String SEPARATOR = "==================================================";
    private static final String DIVIDER   = "--------------------------------------------------";

    private final EngineManager engine = new EngineManager();
    private final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Enter your choice: ");
            System.out.println();

            switch (choice) {
                case 1 -> handleLoadFile();
                case 2 -> handleDisplayEvents();
                case 3 -> handleEventStatus();
                case 4 -> handleParticipate();
                case 5 -> handleCloseEvent();
                case 6 -> {
                    running = false;
                    System.out.println("Goodbye!");
                }
                default -> System.out.println("Invalid choice. Please enter a number between 1 and 6.");
            }
            System.out.println();
        }
    }

    private void printMenu() {
        System.out.println(SEPARATOR);
        System.out.println("                    GUESS MARKET");
        System.out.println(SEPARATOR);
        System.out.println("  1) Load events file");
        System.out.println("  2) Display all events");
        System.out.println("  3) View event trading status");
        System.out.println("  4) Participate in an event");
        System.out.println("  5) Close an event");
        System.out.println("  6) Exit");
        System.out.println(SEPARATOR);
    }

    // ---------- Command 1: Load events file ----------
    private void handleLoadFile() {
        System.out.print("Enter the full path to the XML file: ");
        String path = scanner.nextLine().trim();
        System.out.println(engine.loadDataFromXml(path));
    }

    // ---------- Command 2: Display all events ----------
    private void handleDisplayEvents() {
        List<Event> events = engine.getAllEvents();
        if (events.isEmpty()) {
            System.out.println("No events to display. Load a valid events file first (option 1).");
            return;
        }
        printEventList(events);
    }

    /**
     * Shared formatter: prints a numbered list of events. The [N] shown is
     * the event's position in THIS list (always 1-based, per spec) — not
     * the same thing as the event's own ID field, which is shown separately
     * since it's part of the event's actual data.
     */
    private void printEventList(List<Event> events) {
        System.out.println(DIVIDER);
        int position = 1;
        for (Event event : events) {
            System.out.printf("[%d] (ID: %d) %s%n", position, event.getId(), event.getName());
            System.out.println("    Description: " + event.getDescription());
            System.out.printf("    Commission: %d%% (%s)%n",
                    (int) event.getCommissionRate(), event.getCommissionType());
            System.out.print("    Options: ");
            List<Option> options = event.getOptions();
            for (int i = 0; i < options.size(); i++) {
                System.out.print((i + 1) + ") " + options.get(i).getName() + "   ");
            }
            System.out.println();
            System.out.println("    Status: " + (event.isActive() ? "ACTIVE" : "CLOSED"));
            System.out.println(DIVIDER);
            position++;
        }
    }

    // ---------- Command 3: View event trading status ----------
    private void handleEventStatus() {
        List<Event> events = engine.getAllEvents();
        if (events.isEmpty()) {
            System.out.println("No events to display. Load a valid events file first (option 1).");
            return;
        }
        Event selected = selectEventFromList(events);
        if (selected == null) {
            return;
        }
        printEventStatus(selected);
    }

    // ---------- Command 4: Participate in an event ----------
    private void handleParticipate() {
        List<Event> active = getActiveEvents();
        if (active.isEmpty()) {
            System.out.println("No active events to participate in right now.");
            return;
        }
        Event selected = selectEventFromList(active);
        if (selected == null) {
            return;
        }
        printEventStatus(selected);

        List<Option> options = selected.getOptions();
        int optionChoice = readInt("Select an option to buy by number: ");
        if (optionChoice < 1 || optionChoice > options.size()) {
            System.out.println("Invalid selection: no option numbered " + optionChoice + " for this event.");
            return;
        }

        int amount = readInt("How many shares do you want to buy? ");
        if (amount <= 0) {
            System.out.println("Share amount must be a positive number.");
            return;
        }

        String result = engine.buyShares(selected.getId(), optionChoice - 1, amount);
        System.out.println(result);
        System.out.println();
        printEventStatus(selected);
    }

    // ---------- Command 5: Close an event ----------
    private void handleCloseEvent() {
        List<Event> active = getActiveEvents();
        if (active.isEmpty()) {
            System.out.println("No active events to close right now.");
            return;
        }
        Event selected = selectEventFromList(active);
        if (selected == null) {
            return;
        }
        printEventStatus(selected);

        List<Option> options = selected.getOptions();
        int winningChoice = readInt("Select the winning option by number: ");
        if (winningChoice < 1 || winningChoice > options.size()) {
            System.out.println("Invalid selection: no option numbered " + winningChoice + " for this event.");
            return;
        }

        String result = engine.closeEvent(selected.getId(), winningChoice - 1);
        System.out.println(result);
        System.out.println();
        printEventStatus(selected);
    }

    // ---------- Shared helpers for commands 3-5 ----------

    /**
     * Prints a full status view of one event: current LMSR prices per
     * option, total commission collected on this specific event, its full
     * trade history (most recent first), and — if the event is closed —
     * the winning option plus final share counts.
     */
    private void printEventStatus(Event event) {
        List<Option> options = event.getOptions();
        List<Integer> quantities = new ArrayList<>();
        for (Option option : options) {
            quantities.add(option.getShares());
        }

        System.out.println(DIVIDER);
        System.out.printf("Event Status: %s (ID: %d)%n", event.getName(), event.getId());
        System.out.println(DIVIDER);
        System.out.println("Current prices:");
        for (int i = 0; i < options.size(); i++) {
            double price = LmsrCalculator.calculateOptionPrice(quantities.get(i), quantities, event.getbParameter());
            System.out.printf("  %d) %-10s price: %.2f   (%d shares purchased)%n",
                    i + 1, options.get(i).getName(), price, options.get(i).getShares());
        }
        System.out.println(DIVIDER);
        System.out.printf("Commission collected so far: %.2f%n", event.getTotalCommissionsCollected());
        System.out.println(DIVIDER);
        System.out.println("Trade history (most recent first):");
        List<TradeRecord> history = event.getTradeHistory();
        if (history.isEmpty()) {
            System.out.println("  No trades yet.");
        } else {
            for (int i = history.size() - 1; i >= 0; i--) {
                TradeRecord record = history.get(i);
                System.out.printf("  - Bought %d share(s) of \"%s\" for %.2f%n",
                        record.getQuantity(), record.getOptionName(), record.getPricePaid());
            }
        }
        System.out.println(DIVIDER);

        if (!event.isActive()) {
            String winnerName = options.get(event.getWinningOptionIndex()).getName();
            System.out.println("This event is CLOSED. Winning option: " + winnerName);
            System.out.print("Final shares — ");
            for (int i = 0; i < options.size(); i++) {
                System.out.print(options.get(i).getName() + ": " + options.get(i).getShares());
                if (i < options.size() - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println();
            System.out.println(DIVIDER);
        }
    }

    /**
     * Prints the given events as a numbered list and prompts the user to
     * pick one by that 1-based number. Returns null (with a message) on an
     * out-of-range choice rather than crashing.
     */
    private Event selectEventFromList(List<Event> events) {
        printEventList(events);
        int choice = readInt("Select an event by number: ");
        if (choice < 1 || choice > events.size()) {
            System.out.println("Invalid selection: no event numbered " + choice + " in this list.");
            return null;
        }
        return events.get(choice - 1);
    }

    /** Filters the engine's full event list down to only the active ones. */
    private List<Event> getActiveEvents() {
        List<Event> active = new ArrayList<>();
        for (Event event : engine.getAllEvents()) {
            if (event.isActive()) {
                active.add(event);
            }
        }
        return active;
    }

    // ---------- Input helpers ----------
    /**
     * Reads an integer, re-prompting on non-numeric input rather than
     * crashing — required everywhere the spec asks for numeric input.
     */
    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }
}
