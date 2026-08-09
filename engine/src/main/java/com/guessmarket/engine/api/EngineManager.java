package com.guessmarket.engine.api;

import com.guessmarket.engine.model.Event;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import com.guessmarket.engine.util.XmlParser;
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
            // At the start, qYes = 0 and qNo = 0
            double initialCost = com.guessmarket.engine.util.LmsrCalculator.calculateCost(0, 0, parsedEvent.getbParameter());
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
        // TODO: Retrieve the event from the map
        // TODO: Calculate the cost using LmsrCalculator.calculateCost()
        // TODO: Add 'on-purchase' commission to the Market Maker balance
        // TODO: Update the Option's share count
        // TODO: Create a TradeRecord and add it to the event's history
    }

    /**
     * Command 5: Resolve and close an active event
     */
    public void closeEvent(int eventId, int winningOptionIndex) {
        // TODO: Retrieve the event and mark it as CLOSED
        // TODO: If the fee is 'on-close', deduct it from the pool and add to Market Maker balance
        // TODO: Process payouts
    }


}
