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
    // 2. BEHAVIORS (The API for console-ui / javafx-ui)
    // ==========================================
    // Every method here RETURNS a message describing what happened,
    // rather than printing.

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
        // instead requires the MM's initial share purchase (spec line 695).
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
        } else if (event.getMethod() == TradingMethod.ORDER_BOOK) {
            if (user.getBalance() < event.getInitialInvestment()) {
                return "Cannot activate: MM balance (" + user.getBalance()
                        + ") is less than the required initial investment (" + event.getInitialInvestment() + ")";
            }
        }

        if(!event.activate()){return "Event already started / finished";}

        if (subsidy > 0) {
            user.adjustBalance(-subsidy);
            event.adjustAccountBalance(subsidy);
        }

        if (event.getMethod() == TradingMethod.ORDER_BOOK && event.getInitialInvestment() > 0) {
            // Spec example: d=1, initial=100 -> the MM receives 100 YES + 100 NO
            // pairs for $100 total. The MM's cash funds the event's pool exactly
            // like the LMSR subsidy does; the shares themselves are minted
            // straight into the MM's own holdings on each option.
            int pairs = event.getInitialInvestment() / event.getBaseValue();
            user.adjustBalance(-event.getInitialInvestment());
            event.adjustAccountBalance(event.getInitialInvestment());
            long now = System.currentTimeMillis();
            for (Option option : event.getOptions()) {
                option.addHolding(username, pairs);
                option.addShares(pairs);
                event.addTradeRecord(new TradeRecord(now, option.getName(), pairs, (double) pairs * event.getBaseValue(), 0.0, username, OrderSide.BUY));
            }
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

        if (currentEvent.getMethod() != TradingMethod.LMSR) {
            return "This event uses Order Book trading — use Place Order instead.";
        }

        User user = activeUsers.get(username);
        if(user == null){return "User not found: " + username;}

        if(user.isBlocked()){return "User is blocked!";}

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

            User userMM = activeUsers.get(currentEvent.getOwnerUsername());
            if(userMM == null){return "User not found: " + username;}
            userMM.adjustBalance(+commissionAmount);
        }

        currentEvent.adjustAccountBalance(tradeCost);

        currentEvent.getOptions().get(optionIndex).addShares(amount);

        String optionName = currentEvent.getOptions().get(optionIndex).getName();
        double totalPaid = tradeCost + commissionAmount;

        user.adjustBalance(-totalPaid);

        TradeRecord record = new TradeRecord(System.currentTimeMillis(), optionName, amount, tradeCost, commissionAmount, username);
        currentEvent.addTradeRecord(record);

        return String.format(
                "Purchase successful! Total paid: %.2f (shares: %.2f, commission: %.2f)",
                totalPaid, tradeCost, commissionAmount);
    }

    /**
     * Order Book equivalent of buyShares: submits a BUY or SELL order for
     * one option. The engine immediately tries to fill it — first against
     * resting opposite-side orders on the SAME option (price-time
     * priority), then (BUY orders only, if the event allows mint) against
     * the best resting BUY order on the OTHER option, minting a new pair of
     * shares whenever the two prices together cover the event's base
     * value. Whatever's left after that rests in the book as a new order.
     */
    public String placeOrder(int eventId, int optionIndex, OrderSide side, int quantity, double price, String username) {
        Event event = activeEvents.get(eventId);
        if (event == null) {
            return "Error: Event ID " + eventId + " does not exist.";
        }
        if (event.getMethod() != TradingMethod.ORDER_BOOK) {
            return "This event uses LMSR trading — use Buy Shares instead.";
        }

        User user = activeUsers.get(username);
        if (user == null) {
            return "User not found: " + username;
        }
        if (user.isBlocked()) {
            return "User is blocked!";
        }
        if (event.getStatus() != EventStatus.ACTIVE) {
            return "Event is not active!";
        }
        if (quantity <= 0) {
            return "Quantity must be positive.";
        }

        double maxPrice = event.getBaseValue() - 0.01;
        if (price < 0.01 || price > maxPrice) {
            return String.format("Price must be between 0.01 and %.2f", maxPrice);
        }

        Option thisOption = event.getOptions().get(optionIndex);
        Option otherOption = event.getOptions().get(optionIndex == 0 ? 1 : 0);

        if (side == OrderSide.SELL && thisOption.getHolding(username) < quantity) {
            return "Cannot sell " + quantity + " shares — you only hold " + thisOption.getHolding(username) + ".";
        }

        OrderBook book = thisOption.getOrderBook();
        int remaining = quantity;
        int totalFilled = 0;
        double netCashOut = 0.0; // positive = user paid this much overall, negative = user received

        while (remaining > 0) {
            Order opposing = (side == OrderSide.BUY) ? book.peekBestAsk() : book.peekBestBid();
            boolean crosses = opposing != null &&
                    (side == OrderSide.BUY ? price >= opposing.getPrice() : price <= opposing.getPrice());

            if (crosses) {
                int fillQty = Math.min(remaining, opposing.getRemainingQuantity());
                double execPrice = opposing.getPrice();
                executeMatch(event, thisOption, side, user, opposing, fillQty, execPrice);
                remaining -= fillQty;
                totalFilled += fillQty;
                netCashOut += (side == OrderSide.BUY ? 1 : -1) * fillQty * execPrice;
                book.removeFilledOrders();
                continue;
            }

            if (event.isAllowMint() && side == OrderSide.BUY) {
                Order otherBid = otherOption.getOrderBook().peekBestBid();
                boolean mintPossible = otherBid != null && price + otherBid.getPrice() >= event.getBaseValue();
                if (mintPossible) {
                    int fillQty = Math.min(remaining, otherBid.getRemainingQuantity());
                    double myPrice = event.getBaseValue() - otherBid.getPrice();
                    executeMint(event, thisOption, otherOption, user, myPrice, otherBid, fillQty);
                    remaining -= fillQty;
                    totalFilled += fillQty;
                    netCashOut += fillQty * myPrice;
                    otherOption.getOrderBook().removeFilledOrders();
                    continue;
                }
            }

            break;
        }

        if (remaining > 0) {
            Order restingOrder = new Order(username, side, price, remaining);
            if (side == OrderSide.BUY) {
                book.addBid(restingOrder);
            } else {
                book.addAsk(restingOrder);
            }
        }

        return String.format(
                "Order processed: %d filled (%.2f %s), %d resting in the book.",
                totalFilled, Math.abs(netCashOut), netCashOut >= 0 ? "paid" : "received", remaining);
    }

    /**
     * Executes a same-option match between the incoming order (side/user)
     * and a resting opposite-side order, at the resting order's price.
     */
    private void executeMatch(Event event, Option option, OrderSide incomingSide, User incomingUser,
                               Order restingOrder, int quantity, double execPrice) {
        User restingUser = activeUsers.get(restingOrder.getUsername());
        User buyer = incomingSide == OrderSide.BUY ? incomingUser : restingUser;
        User seller = incomingSide == OrderSide.BUY ? restingUser : incomingUser;

        double tradeValue = quantity * execPrice;
        buyer.adjustBalance(-tradeValue);
        seller.adjustBalance(+tradeValue);

        option.removeHolding(seller.getName(), quantity);
        option.addHolding(buyer.getName(), quantity);

        restingOrder.reduceQuantity(quantity);
        option.getOrderBook().setLastTradePrice(execPrice);

        double commission = chargeOnPurchaseCommissionIfApplicable(event, buyer, tradeValue);

        long now = System.currentTimeMillis();
        event.addTradeRecord(new TradeRecord(now, option.getName(), quantity, tradeValue, commission, buyer.getName(), OrderSide.BUY));
        event.addTradeRecord(new TradeRecord(now, option.getName(), quantity, tradeValue, 0.0, seller.getName(), OrderSide.SELL));
    }

    /**
     * Executes a cross-option mint: `quantity` brand-new pairs of shares are
     * created, one option's worth going to the incoming buyer (at
     * myPrice) and the other's to the resting order's buyer (at its own
     * quoted price) — myPrice + restingOrder.getPrice() always equals
     * event.getBaseValue() by construction.
     */
    private void executeMint(Event event, Option myOption, Option otherOption, User incomingUser,
                              double myPrice, Order otherBid, int quantity) {
        User otherUser = activeUsers.get(otherBid.getUsername());

        double myCost = quantity * myPrice;
        double otherCost = quantity * otherBid.getPrice();
        incomingUser.adjustBalance(-myCost);
        otherUser.adjustBalance(-otherCost);
        event.adjustAccountBalance(myCost + otherCost);

        myOption.addHolding(incomingUser.getName(), quantity);
        otherOption.addHolding(otherUser.getName(), quantity);
        myOption.addShares(quantity);
        otherOption.addShares(quantity);

        otherBid.reduceQuantity(quantity);
        myOption.getOrderBook().setLastTradePrice(myPrice);
        otherOption.getOrderBook().setLastTradePrice(otherBid.getPrice());

        double myCommission = chargeOnPurchaseCommissionIfApplicable(event, incomingUser, myCost);
        double otherCommission = chargeOnPurchaseCommissionIfApplicable(event, otherUser, otherCost);

        long now = System.currentTimeMillis();
        event.addTradeRecord(new TradeRecord(now, myOption.getName(), quantity, myCost, myCommission, incomingUser.getName(), OrderSide.BUY));
        event.addTradeRecord(new TradeRecord(now, otherOption.getName(), quantity, otherCost, otherCommission, otherUser.getName(), OrderSide.BUY));
    }

    /**
     * If this event charges commission on-purchase, deducts it from the
     * buyer and credits it to the event's MM. Returns the amount charged
     * (0 if this event's commission is on-close instead, which is settled
     * later at closeEvent).
     */
    private double chargeOnPurchaseCommissionIfApplicable(Event event, User buyer, double tradeValue) {
        if (!"on-purchase".equals(event.getCommissionType())) {
            return 0.0;
        }
        double commission = tradeValue * (event.getCommissionRate() / 100.0);
        buyer.adjustBalance(-commission);
        activeUsers.get(event.getOwnerUsername()).adjustBalance(+commission);
        event.addCommission(commission);
        return commission;
    }

    /**
     * Order Book close: every YES/NO share that ever existed was created by
     * a mint that put exactly d dollars into event.accountBalance per pair
     * (matches never touch the event account — buyer pays seller directly),
     * so the winning option's total outstanding shares always exactly
     * equals event.accountBalance / baseValue. That means the payout math
     * is identical in shape to LMSR's: figure out the total pool, optionally
     * carve out an on-close commission, split what's left evenly per
     * winning share. The only real difference is where "who holds how many
     * winning shares" comes from — Option.holdings instead of TradeRecord
     * history, since Order Book (unlike LMSR) allows selling.
     */
    private String closeOrderBookEvent(Event event, int winningOptionIndex, User userMM) {
        Option winningOption = event.getOptions().get(winningOptionIndex);
        Map<String, Integer> winners = winningOption.getAllHoldings();

        int winningShares = 0;
        for (int shares : winners.values()) {
            winningShares += shares;
        }

        double totalPool = winningShares * (double) event.getBaseValue();
        double commissionAmount = 0.0;

        if ("on-close".equals(event.getCommissionType())) {
            commissionAmount = totalPool * (event.getCommissionRate() / 100.0);
            event.addCommission(commissionAmount);
            userMM.adjustBalance(+commissionAmount);
        }

        double payoutPool = totalPool - commissionAmount;
        double payoutPerShare = winningShares > 0 ? payoutPool / winningShares : 0.0;

        for (Map.Entry<String, Integer> holder : winners.entrySet()) {
            if (holder.getValue() > 0) {
                activeUsers.get(holder.getKey()).adjustBalance(+payoutPerShare * holder.getValue());
            }
        }

        // The full pool is drained regardless of commission mode — on-close
        // commission comes out of what winners receive, not extra on top.
        event.adjustAccountBalance(-totalPool);

        // The book is closed to new orders at this point (spec: resolution
        // cancels whatever's still resting) — nothing was escrowed, so
        // dropping them has no further financial effect.
        for (Option option : event.getOptions()) {
            option.getOrderBook().cancelAll();
        }

        return String.format(
                "Event Closed! Winning Option: %s%nTotal Pool: %.2f%nPayout per winning share: %.2f",
                winningOption.getName(), totalPool, payoutPerShare);
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

        if (currentEvent.getMethod() == TradingMethod.ORDER_BOOK) {
            return closeOrderBookEvent(currentEvent, winningOptionIndex, userMM);
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
