package com.guessmarket.engine.api;

import com.guessmarket.engine.model.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

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

    // Constructor to initialize a clean slate
    public EngineManager() {
        this.activeEvents = new LinkedHashMap<>();
        this.activeUsers = new LinkedHashMap<>();
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
            // Tracks which user was already assigned as MM for each event ID,
            // so a second user claiming the same event is caught as an error
            // instead of silently overwriting the first via setOwnerUsername.
            Map<Integer, String> eventOwners = new LinkedHashMap<>();
            for(GmUserXml xmlUser : root.getUsers()) {
                User user = XmlParser.mapToUser(xmlUser);

                if(tempUsers.containsKey(user.getName()))
                {
                    throw new Exception("Duplicate User name found: " + user.getName());
                }



                for(int eventId : user.getManagedEventIds())
                {
                    Event event = tempEvents.get(eventId);
                    if(event == null)
                    {
                        throw new Exception("Event with id " + eventId + " not found");
                    }
                    if(eventOwners.containsKey(eventId))
                    {
                        throw new Exception("Event " + eventId + " has more than one MM assigned: "
                                + eventOwners.get(eventId) + " and " + user.getName());
                    }
                    eventOwners.put(eventId, user.getName());
                    event.setOwnerUsername(user.getName());
                }

                tempUsers.put(user.getName(), user);
            }

            // Spec requires every event to have exactly one MM assigned from
            // the file's users — the loop above already rejects more than
            // one, this catches events nobody claimed at all.
            for (Event event : tempEvents.values()) {
                if (!eventOwners.containsKey(event.getId())) {
                    throw new Exception("Event " + event.getId() + " has no MM assigned to it.");
                }
            }


            // Every event parsed and validated cleanly — commit the new state.
            // (No subsidy is paid here: each event's LMSR subsidy is now debited
            // from its own MM's balance in activateEvent, not upfront for all
            // events at once — see activateEvent.)
            this.activeEvents.clear();
            this.activeEvents.putAll(tempEvents);
            this.activeUsers.clear();
            this.activeUsers.putAll(tempUsers);

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
     * Activates a NOT_STARTED event, allowing trading to begin.
     * Only the market maker who owns the event may activate it.
     */
    public String activateEvent(int eventId, String username) {

        User user = activeUsers.get(username);
        if(user == null){return "User not found: " + username;}

        Event event = activeEvents.get(eventId);
        if(event == null){return "Event with id " + eventId + " not found";}



        if(!user.isMarketMakerFor(eventId)) {
            return "You are not the MM!";
        }

        // LMSR needs an upfront subsidy to seed the market (the MM's max
        // possible loss): C(0) with every option's shares at 0. Order Book
        // events don't use LMSR pricing, so there's nothing to subsidize.
        double subsidy = 0.0;
        if (event.getMethod() == TradingMethod.LMSR) {
            List<Integer> zeroQuantities = new ArrayList<>();
            for (Option opt : event.getOptions()) {
                zeroQuantities.add(0);
            }
            subsidy = LmsrCalculator.calculateCost(zeroQuantities, event.getbParameter());

            if (user.getBalance() < subsidy) {
                return "Cannot activate: MM balance (" + user.getBalance()
                        + ") is less than the required subsidy (" + subsidy + ")";
            }
        }

        if(!event.activate()){return "Event already started / finished";}

        if (subsidy > 0) {
            user.adjustBalance(-subsidy);
            event.adjustAccountBalance(subsidy);
        }

        return "Event Activated Successfully";
    }

    /**
     * Command 4: Participate in an event by buying shares.
     * Returns a summary of what was paid, split between the shares
     * themselves and any purchase-time commission.
     */
    public String buyShares(int eventId, int optionIndex, int amount, String username) {
        Event currentEvent = activeEvents.get(eventId);
        if (currentEvent == null) {
            return "Error: Event ID " + eventId + " does not exist.";
        }

        // TODO: look up the User by username (same pattern as activateEvent).
        //       If null, return an error message.
        User user =  activeUsers.get(username);
        if(user == null){return "User not found: " + username;}

        // TODO: reject if user.isBlocked() — a blocked user can't trade.
        if(user.isBlocked()){return "User is blocked!";}

        // TODO: reject if currentEvent.getStatus() != EventStatus.ACTIVE —
        //       can't buy shares in an event that hasn't started yet or is closed.
        if(currentEvent.getStatus() != EventStatus.ACTIVE){return "Event is not active!";}

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
            currentEvent.addCommission(commissionAmount);

            // TODO: credit commissionAmount to the MM's own balance, not the
            //       event account — look up the MM via
            //       activeUsers.get(currentEvent.getOwnerUsername()) and call
            //       mm.adjustBalance(+commissionAmount).
            User userMM = activeUsers.get(currentEvent.getOwnerUsername());
            if(userMM == null){return "User not found: " + username;}
            userMM.adjustBalance(+commissionAmount);
        }

        // TODO: credit tradeCost (not commissionAmount) into the event's own
        //       pooled account via currentEvent.adjustAccountBalance(tradeCost)
        //       — this is the money that backs the payout at close time.
        currentEvent.adjustAccountBalance(tradeCost);

        currentEvent.getOptions().get(optionIndex).addShares(amount);

        String optionName = currentEvent.getOptions().get(optionIndex).getName();
        double totalPaid = tradeCost + commissionAmount;

        // TODO: deduct totalPaid from the user's balance via user.adjustBalance(-totalPaid).
        //       adjustBalance already flips the user to blocked if this takes them negative.
        user.adjustBalance(-totalPaid);

        TradeRecord record = new TradeRecord(System.currentTimeMillis(), optionName, amount, tradeCost, commissionAmount, username);
        currentEvent.addTradeRecord(record);

        return String.format(
                "Purchase successful! Total paid: %.2f (shares: %.2f, commission: %.2f)",
                totalPaid, tradeCost, commissionAmount);
    }

    /**
     * Command 5: Resolve and close an active event. Only the MM who owns the
     * event may close it.
     */
    public String closeEvent(int eventId, int winningOptionIndex, String username) {
        Event currentEvent = activeEvents.get(eventId);
        if (currentEvent == null) {
            return "Error: Event ID " + eventId + " does not exist.";
        }

        User userMM = activeUsers.get(username);
        if (userMM == null) {
            return "User not found: " + username;
        }
        if (!userMM.isMarketMakerFor(eventId)) {
            return "You are not the MM!";
        }

        if (!currentEvent.close(winningOptionIndex)) {
            return "Event is not ACTIVE — cannot close it.";
        }

        List<Integer> finalQuantities = new ArrayList<>();
        for (Option opt : currentEvent.getOptions()) {
            finalQuantities.add(opt.getShares());
        }

        double totalPool = LmsrCalculator.calculateCost(finalQuantities, currentEvent.getbParameter());

        if (currentEvent.getCommissionType().equals("on-close")) {
            double commissionAmount = totalPool * (currentEvent.getCommissionRate() / 100.0);
            currentEvent.addCommission(commissionAmount);
            totalPool -= commissionAmount;


            userMM.adjustBalance(+commissionAmount);
        }

        int winningShares = currentEvent.getOptions().get(winningOptionIndex).getShares();
        double payoutPerShare = 0.0;
        if (winningShares > 0) {
            payoutPerShare = totalPool / winningShares;
        }


        //PAY WINNERS
        String winningOption = currentEvent.getOptions().get(winningOptionIndex).getName();
        for(TradeRecord tradeRecord : currentEvent.getTradeHistory()){
            if(tradeRecord.getOptionName().equals(winningOption)){
                User holder = activeUsers.get(tradeRecord.getUsername());
                holder.adjustBalance(+ payoutPerShare* tradeRecord.getQuantity());
            }
        }
        //Adjust event pool
        currentEvent.adjustAccountBalance(-totalPool);

        return String.format(
                "Event Closed! Winning Option: %s%nTotal Pool (after fees): %.2f%nPayout per winning share: %.2f",
                currentEvent.getOptions().get(winningOptionIndex).getName(), totalPool, payoutPerShare);
    }

}
