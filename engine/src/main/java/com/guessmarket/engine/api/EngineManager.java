package com.guessmarket.engine.api;

import com.guessmarket.engine.model.Event;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import com.guessmarket.engine.model.Option;
import com.guessmarket.engine.model.TradeRecord;
import com.guessmarket.engine.util.XmlParser;
import com.guessmarket.engine.util.LmsrCalculator;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import java.io.File;

public class EngineManager {

    // ==========================================
    // 1. STATE (The Memory)
    // ==========================================

    // A map to quickly look up an Event by its ID
    private final Map<Integer, Event> activeEvents;

    // The global account for the Market Maker's fees and subsidies
    private double marketMakerBalance;

    // Constructor to initialize a clean slate
    public EngineManager() {
        this.activeEvents = new HashMap<>();
        this.marketMakerBalance = 0.0;
    }

    // ==========================================
    // 2. BEHAVIORS (The API for the Console UI)
    // ==========================================

    /**
     * Command 1: Load event data from an XML file
     */
    public void loadDataFromXml(String filePath) {
    try {
        File xmlFile = new File(filePath);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(xmlFile);

        document.getDocumentElement().normalize();

        // 1. Clear existing data and reset balance
        this.activeEvents.clear();
        this.marketMakerBalance = 0.0;

        // 2. Parse the XML file
        NodeList eventNodes = document.getElementsByTagName("GM-event");
        for (int i = 0; i < eventNodes.getLength(); i++) {
            Element eventElement = (Element) eventNodes.item(i);

            // Call your awesome new utility class!
            Event parsedEvent = XmlParser.parseEventNode(eventElement);

            // 3. Validate unique IDs
            if (activeEvents.containsKey(parsedEvent.getId())) {
                throw new Exception("Duplicate Event ID found: " + parsedEvent.getId());
            }

            // 4. Calculate initial LMSR subsidies and deduct from Market Maker
            // At the start, all quantities are 0
            List<Integer> initialQuantities = new ArrayList<>();
            for (Option opt : parsedEvent.getOptions()) {
                initialQuantities.add(0);
            }
            double initialCost = LmsrCalculator.calculateCost(initialQuantities, parsedEvent.getbParameter());
            this.marketMakerBalance -= initialCost;

            // 5. Populate the activeEvents map
            activeEvents.put(parsedEvent.getId(), parsedEvent);
        }

        System.out.println("XML loaded successfully! Total events: " + activeEvents.size());

    } catch (Exception e) {
        System.out.println("Error loading XML file: " + e.getMessage());
        System.out.println("Please check the file format and path.");
    }
    }


    /**
     * Command 2: Retrieve all events to display to the user
     */
    public List<Event> getAllEvents() {
        // Easily convert the Map values into a List for the UI to loop through
        return new ArrayList<>(activeEvents.values());
    }

    /**
     * Command 3: Retrieve the current trading status and history of a specific event.
     */
    public Event getEventDetails(int eventId) {

        return activeEvents.get(eventId);
    }

    /**
     * Command 4: Participate in an event by buying shares
     */
    public void buyShares(int eventId, int optionIndex, int amount) {
        Event currentEvent = activeEvents.get(eventId);
        if (currentEvent == null) {
            System.out.println("Error: Event ID " + eventId + " does not exist.");
            return;
        }
        // 1. Gather the current shares for ALL options
        List<Integer> oldQuantities = new ArrayList<>();
        for (Option opt : currentEvent.getOptions()) {
            oldQuantities.add(opt.getShares());
        }

        int bParameter = currentEvent.getbParameter();

        // 2. Calculate the cost BEFORE the purchase
        double oldCost = com.guessmarket.engine.util.LmsrCalculator.calculateCost(oldQuantities, bParameter);

        // 3. Create a new list for the theoretical state AFTER purchase
        List<Integer> newQuantities = new ArrayList<>(oldQuantities);

        // Retrieve the current shares for the chosen option, add the new amount, and overwrite it in the list
        int updatedShareCount = newQuantities.get(optionIndex) + amount;
        newQuantities.set(optionIndex, updatedShareCount);

        // 4. Calculate the cost AFTER the purchase
        double newCost = com.guessmarket.engine.util.LmsrCalculator.calculateCost(newQuantities, bParameter);

        // 5. The actual price the user pays
        double tradeCost = newCost - oldCost;

        // 6. Process the Commission (if applicable)
        if (currentEvent.getCommissionType().equals("on-purchase")) {
            // Divide by 100.0 to force floating-point math
            double commissionAmount = tradeCost * (currentEvent.getCommissionRate() / 100.0);
            this.marketMakerBalance += commissionAmount;
        }

        // 7. Update the real Option object's share count
        currentEvent.getOptions().get(optionIndex).addShares(amount);

        // 8. Create a TradeRecord and log it
        String optionName = currentEvent.getOptions().get(optionIndex).getName();
        TradeRecord record = new TradeRecord(System.currentTimeMillis(), optionName, amount, tradeCost);

        currentEvent.addTradeRecord(record);

    }

    /**
     * Command 5: Resolve and close an active event
     */
    public void closeEvent(int eventId, int winningOptionIndex) {
        // 1. Retrieve and Validate
        Event currentEvent = activeEvents.get(eventId);
        if (currentEvent == null) {
            System.out.println("Error: Event ID " + eventId + " does not exist.");
            return;
        }

        // 2. Mark the event as closed
        currentEvent.setActive(false);

        // 3. Gather final share quantities
        List<Integer> finalQuantities = new ArrayList<>();
        for (Option opt : currentEvent.getOptions()) {
            finalQuantities.add(opt.getShares());
        }

        // 4. Calculate the total money pool at the end of trading
        double totalPool = LmsrCalculator.calculateCost(finalQuantities, currentEvent.getbParameter());

        // 5. Process 'on-close' Commission
        if (currentEvent.getCommissionType().equals("on-close")) {
            double commissionAmount = totalPool * (currentEvent.getCommissionRate() / 100.0);
            this.marketMakerBalance += commissionAmount;

            // Deduct the commission from the total pool available for winners
            totalPool -= commissionAmount;
        }

        // 6. Calculate Payouts
        int winningShares = currentEvent.getOptions().get(winningOptionIndex).getShares();
        double payoutPerShare = 0.0;

        // Prevent division by zero if nobody bought the winning option
        if (winningShares > 0) {
            payoutPerShare = totalPool / winningShares;
        }

        // Temporary console output to verify math during our tests
        System.out.println("Event Closed! Winning Option: " + currentEvent.getOptions().get(winningOptionIndex).getName());
        System.out.println("Total Pool (after fees): " + totalPool);
        System.out.println("Payout per winning share: " + payoutPerShare);

    }

    // TEMPORARY TEST MAIN
    public static void main(String[] args) {
        EngineManager manager = new EngineManager();

        // 1. Load the market
        manager.loadDataFromXml("C:\\Users\\Daniel\\IdeaProjects\\GuessMarket\\engine\\src\\main\\java\\com\\guessmarket\\engine\\test_events.xml");

        // 2. Simulate a user buying 10 shares of Option 0 ("Yes") for Event ID 3
        System.out.println("\n--- Buying Shares ---");
        manager.buyShares(3, 0, 10);

        // 3. Print Market Maker Balance to see the 'on-purchase' commission collected
        System.out.println("Market Maker Balance: " + manager.marketMakerBalance);

        // 4. Resolve the event, declaring Option 0 ("Yes") as the winner
        System.out.println("\n--- Closing Event ---");
        manager.closeEvent(3, 0);
    }


}
