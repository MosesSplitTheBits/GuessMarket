package com.guessmarket.engine.api;

import com.guessmarket.engine.model.Event;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import com.guessmarket.engine.model.Option;
import com.guessmarket.engine.model.TradeRecord;
import com.guessmarket.engine.model.TradingMethod;
import com.guessmarket.engine.model.User;
import com.guessmarket.engine.util.XmlParser;
import com.guessmarket.engine.util.LmsrCalculator;
import com.guessmarket.engine.xml.GmEventXml;
import com.guessmarket.engine.xml.GmUserXml;
import com.guessmarket.engine.xml.GuessMarketXml;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import java.io.File;

public class EngineManager {

    // ==========================================
    // 1. STATE (The Memory)
    // ==========================================

    // A map to quickly look up an Event by its ID.
    // LinkedHashMap (not HashMap) so events keep a stable, predictable
    // iteration order — the order they were loaded in — which the UI
    // relies on for showing a consistent numbered list.
    private final Map<Integer, Event> activeEvents;
    private final Map<String, User> activeUsers;

    // The global account for the Market Maker's fees and subsidies
    private double marketMakerBalance;

    // Constructor to initialize a clean slate
    public EngineManager() {
        this.activeEvents = new LinkedHashMap<>();
        this.activeUsers = new LinkedHashMap<>();
        this.marketMakerBalance = 0.0;
    }

    // ==========================================
    // 2. BEHAVIORS (The API for the Console UI)
    // ==========================================
    // Every method here RETURNS a message describing what happened
    // Rather than printing. Will be changed in excercise 2. Works for now....

    /**
     * Command 1: Load event data from an XML file.
     * The new file is fully parsed and validated into temporary storage
     * first; only if the entire file loads cleanly does it become the
     * real state. An invalid file never overwrites the last valid load.
     */
    public String loadDataFromXml(String filePath) {
        File xmlFile = new File(filePath);

        if (!xmlFile.exists()) {
            return "File not found: " + filePath;
        }

        if (!xmlFile.getName().toLowerCase().endsWith(".xml")) {
            return "File is not an XML file (must end in .xml): " + filePath;
        }

        try {
            JAXBContext context = JAXBContext.newInstance(GuessMarketXml.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            GuessMarketXml root = (GuessMarketXml) unmarshaller.unmarshal(xmlFile);

            // Build the new state into temporary storage first.
            Map<Integer, Event> tempEvents = new LinkedHashMap<>();

            for (GmEventXml xmlEvent : root.getEvents()) {
                Event event = XmlParser.mapToEvent(xmlEvent);

                if (tempEvents.containsKey(event.getId())) {
                    throw new Exception("Duplicate Event ID found: " + event.getId());
                }

                tempEvents.put(event.getId(), event);
            }

            //Check if userlist is correct, only then pushes it to app
            Map<String, User> tempUsers = new LinkedHashMap<>();
            for(GmUserXml xmlUser : root.getUsers()) {
                User user = XmlParser.mapToUser(xmlUser);

                if(tempUsers.containsKey(user.getName()))
                {
                    throw new Exception("Duplicate User name found: " + user.getName());
                }



                for(int eventId : user.getManagedEventIds())
                {
                    Event event = tempEvents.get(eventId);
                    if(event != null)
                    {
                        event.setOwnerUsername(user.getName());
                    }
                    else{throw new Exception("Event with id " + eventId + " not found");}
                }

                tempUsers.put(user.getName(), user);
            }


            // Every event parsed and validated cleanly — compute the total
            // initial LMSR subsidy across all of them.
            double totalCost = 0;
            for (Event event : tempEvents.values()) {
                if (event.getMethod() != TradingMethod.LMSR) {
                    continue; // no LMSR subsidy for Order Book events
                }
                List<Integer> tempOptionList = new ArrayList<>();
                for (Option eventOption : event.getOptions()) {
                    tempOptionList.add(0);
                }
                totalCost += LmsrCalculator.calculateCost(tempOptionList, event.getbParameter());
            }

            // Only now, after everything above succeeded, commit the new state.
            this.activeEvents.clear();
            this.activeEvents.putAll(tempEvents);
            this.activeUsers.clear();
            this.activeUsers.putAll(tempUsers);
            this.marketMakerBalance = -totalCost;

            return "XML loaded successfully! Total events: " + activeEvents.size();

        } catch (Exception e) {
            return "Error loading XML file: " + e.getMessage();
        }
    }

    /**
     * Command 2: Retrieve all events to display to the user
     */
    public List<Event> getAllEvents() {
        return new ArrayList<>(activeEvents.values());
    }

    /**
     * Command 3: Retrieve the current trading status and history of a specific event.
     */
    public Event getEventDetails(int eventId) {
        return activeEvents.get(eventId);
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(activeUsers.values());
    }

    public User getUserDetails(String username) {
        return activeUsers.get(username);
    }

    /**
     * Command 4: Participate in an event by buying shares.
     * Returns a summary of what was paid, split between the shares
     * themselves and any purchase-time commission.
     */
    public String buyShares(int eventId, int optionIndex, int amount) {
        Event currentEvent = activeEvents.get(eventId);
        if (currentEvent == null) {
            return "Error: Event ID " + eventId + " does not exist.";
        }

        List<Integer> oldQuantities = new ArrayList<>();
        for (Option opt : currentEvent.getOptions()) {
            oldQuantities.add(opt.getShares());
        }

        int bParameter = currentEvent.getbParameter();
        double oldCost = LmsrCalculator.calculateCost(oldQuantities, bParameter);

        List<Integer> newQuantities = new ArrayList<>(oldQuantities);
        int updatedShareCount = newQuantities.get(optionIndex) + amount;
        newQuantities.set(optionIndex, updatedShareCount);

        double newCost = LmsrCalculator.calculateCost(newQuantities, bParameter);
        double tradeCost = newCost - oldCost;

        double commissionAmount = 0.0;
        if (currentEvent.getCommissionType().equals("on-purchase")) {
            commissionAmount = tradeCost * (currentEvent.getCommissionRate() / 100.0);
            this.marketMakerBalance += commissionAmount;
            currentEvent.addCommission(commissionAmount);
        }

        currentEvent.getOptions().get(optionIndex).addShares(amount);

        String optionName = currentEvent.getOptions().get(optionIndex).getName();
        double totalPaid = tradeCost + commissionAmount;
        TradeRecord record = new TradeRecord(System.currentTimeMillis(), optionName, amount, totalPaid);
        currentEvent.addTradeRecord(record);

        return String.format(
                "Purchase successful! Total paid: %.2f (shares: %.2f, commission: %.2f)",
                totalPaid, tradeCost, commissionAmount);
    }

    /**
     * Command 5: Resolve and close an active event.
     */
    public String closeEvent(int eventId, int winningOptionIndex) {
        Event currentEvent = activeEvents.get(eventId);
        if (currentEvent == null) {
            return "Error: Event ID " + eventId + " does not exist.";
        }

        currentEvent.close(winningOptionIndex);

        List<Integer> finalQuantities = new ArrayList<>();
        for (Option opt : currentEvent.getOptions()) {
            finalQuantities.add(opt.getShares());
        }

        double totalPool = LmsrCalculator.calculateCost(finalQuantities, currentEvent.getbParameter());

        if (currentEvent.getCommissionType().equals("on-close")) {
            double commissionAmount = totalPool * (currentEvent.getCommissionRate() / 100.0);
            this.marketMakerBalance += commissionAmount;
            currentEvent.addCommission(commissionAmount);
            totalPool -= commissionAmount;
        }

        int winningShares = currentEvent.getOptions().get(winningOptionIndex).getShares();
        double payoutPerShare = 0.0;
        if (winningShares > 0) {
            payoutPerShare = totalPool / winningShares;
        }

        return String.format(
                "Event Closed! Winning Option: %s%nTotal Pool (after fees): %.2f%nPayout per winning share: %.2f",
                currentEvent.getOptions().get(winningOptionIndex).getName(), totalPool, payoutPerShare);
    }

}
