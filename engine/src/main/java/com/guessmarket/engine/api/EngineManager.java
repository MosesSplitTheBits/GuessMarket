package com.guessmarket.engine.api;

import com.guessmarket.dto.*;
import com.guessmarket.engine.model.*;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import com.guessmarket.engine.util.DtoMapper;
import com.guessmarket.engine.util.XmlParser;
import com.guessmarket.engine.util.LmsrCalculator;
import com.guessmarket.engine.xml.GmEventXml;
import com.guessmarket.engine.xml.GuessMarketXml;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

/**
 * The whole Guess Market system state and rules. Passive: never prints,
 * never reads input. Every action either returns a user-facing success
 * message or throws EngineException with a user-facing failure message
 */
public class EngineManager {


    // ==========================================
    // 1. STATE
    // ==========================================

    // JAXBContext is expensive to build and thread-safe once built, so one
    // shared instance is created up front. (Unmarshaller is NOT thread-safe,
    // so a fresh one is made per upload.)
    private static final JAXBContext JAXB_CONTEXT = createJaxbContext();

    // Ex3: events are keyed by NAME — schema v3 has no event id, and events
    // from different uploaded files are told apart only by their names.
    // LinkedHashMap keeps a stable, predictable order (upload order) for the UI.
    private final Map<String, Event> activeEvents;
    private final Map<String, User> activeUsers;

    public EngineManager() {
        this.activeEvents = new LinkedHashMap<>();
        this.activeUsers = new LinkedHashMap<>();
    }

    private static JAXBContext createJaxbContext() {
        try {
            return JAXBContext.newInstance(GuessMarketXml.class);
        } catch (JAXBException e) {
            throw new IllegalStateException("JAXB setup failed", e);
        }
    }

    // ==========================================
    // 2. USERS
    // ==========================================

    /**
     * Ex3 login: registers a brand-new user with an empty account.
     * Usernames are unique (spec); surrounding spaces are ignored.
     * Returns the name as actually registered (trimmed) — the caller should
     * use THAT, not its raw input. (Returns only the name, not the User: no
     * live engine object ever leaves the engine.)
     */
    public synchronized String registerUser(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new EngineException("Username must not be empty.");
        }
        if (activeUsers.containsKey(name)) {
            throw new EngineException("Username '" + name + "' is already taken. Please choose another name.");
        }
        User user = new User(name);
        activeUsers.put(name, user);
        return name;
    }

    public synchronized boolean isUserExists(String username) {
        return activeUsers.containsKey(username);
    }

    /** Ex3: a user tops up their own account. */
    public synchronized String deposit(String username, double amount) {
        User user = requireUser(username);
        if (!(amount > 0)) { // also rejects NaN
            throw new EngineException("Deposit amount must be positive.");
        }
        user.adjustBalance(+amount, "Deposit");
        return String.format("Deposited %.2f. New balance: %.2f", amount, user.getBalance());
    }

    // Package-private on purpose: these hand out LIVE, mutable User objects,
    // so only same-package code (the engine's own tests) may touch them.
    // Everything outside the engine uses the DTO snapshot methods below.
    synchronized List<User> getAllUsers() {
        return new ArrayList<>(activeUsers.values());
    }

    synchronized User getUserDetails(String username) {
        return activeUsers.get(username);
    }

    /** Every user's public info (spec: name, balance, is-MM). */
    public synchronized List<UserSummaryDto> getUsersSnapshot() {
        return activeUsers.values().stream().map(DtoMapper::toSummary).toList();
    }

    /** The given user's own account page. */
    public synchronized UserDetailsDto getUserSnapshot(String username) {
        return DtoMapper.toDetails(requireUser(username), new ArrayList<>(activeEvents.values()));
    }

    /**
     * Ledger delta as a DTO: the entries the client hasn't seen, plus the
     * version (= total ledger size) to send as fromIndex next time. Both
     * come from the same locked moment, so no line can be skipped or
     * repeated between polls.
     */
    public synchronized LedgerDeltaDto getLedgerDelta(String username, int fromIndex) {
        List<LedgerEntryDto> entries = getLedger(username, fromIndex).stream().map(DtoMapper::toDto).toList();
        return new LedgerDeltaDto(entries, requireUser(username).getLedger().size());
    }

    /**
     * Delta fetch for the account ledger: only the lines from index
     * {@code fromIndex} onward, so a polling client doesn't re-download its
     * whole history every half second. The client's next fromIndex is
     * fromIndex + (number of lines returned).
     */
    public synchronized List<LedgerEntry> getLedger(String username, int fromIndex) {
        User user = requireUser(username);
        List<LedgerEntry> ledger = user.getLedger();
        if (fromIndex < 0 || fromIndex > ledger.size()) {
            fromIndex = 0;
        }
        return new ArrayList<LedgerEntry>(ledger.subList(fromIndex,ledger.size()));
    }

    // ==========================================
    // 3. LOADING EVENTS
    // ==========================================

    /**
     * Ex3 upload: ADDS the events in this file to the system (files now
     * accumulate instead of replacing each other), with the uploader as MM
     * of every one of them.
     *
     * Rollback-safe, same idea as Ex1/Ex2: the whole file is parsed and
     * validated into temporary storage first; only if every event passes
     * does anything touch live state. A bad file — or one clashing with an
     * already-loaded event name — changes nothing.
     *
     * Takes a stream, not a file path: the server must never write the
     * uploaded file to disk (spec), so it hands the request body straight in.
     */
    public synchronized String loadEventsFromXml(InputStream xmlContent, String uploaderName) {
        User uploader = requireUser(uploaderName);

        GuessMarketXml root;
        try {
            Unmarshaller unmarshaller = JAXB_CONTEXT.createUnmarshaller();
            root = (GuessMarketXml) unmarshaller.unmarshal(xmlContent);
        } catch (JAXBException | ClassCastException e) {
            throw new EngineException("File is not a valid Guess Market XML file.");
        }

        if (root == null || root.getEvents() == null || root.getEvents().isEmpty()) {
            throw new EngineException("File contains no events.");
        }

        Map<String, Event> tempEvents = new LinkedHashMap<>();
        for (GmEventXml xmlEvent : root.getEvents()) {
            Event event = XmlParser.mapToEvent(xmlEvent);
            String name = event.getName();

            if (tempEvents.containsKey(name)) {
                throw new EngineException("Event name appears twice in the file: " + name);
            }
            if (activeEvents.containsKey(name)) {
                throw new EngineException("An event named '" + name + "' already exists in the system.");
            }
            tempEvents.put(name, event);
        }

        // Every event validated cleanly — commit.
        for (Event event : tempEvents.values()) {
            event.setOwnerUsername(uploader.getName());
            uploader.addManagedEvent(event.getName());
        }
        activeEvents.putAll(tempEvents);

        return "File loaded successfully! " + tempEvents.size() + " event(s) added; you are their market maker.";
    }

    // ==========================================
    // 4. EVENTS — queries
    // ==========================================

    // Package-private for the same reason as getAllUsers: live objects.
    synchronized List<Event> getAllEvents() {
        return new ArrayList<>(activeEvents.values());
    }

    synchronized Event getEventDetails(String eventName) {
        return activeEvents.get(eventName);
    }

    /** The events list: one immutable summary per event, in upload order. */
    public synchronized List<EventSummaryDto> getEventsSnapshot() {
        return activeEvents.values().stream().map(DtoMapper::toSummary).toList();
    }

    /** Everything about one event, frozen at this moment. */
    public synchronized EventDetailsDto getEventSnapshot(String eventName) {
        return DtoMapper.toDetails(requireEvent(eventName));
    }

    // ==========================================
    // 5. EVENTS — actions
    // ==========================================

    /**
     * Activates a NOT_STARTED event, allowing trading to begin.
     * Only the market maker who owns the event may activate it.
     */
    public synchronized String activateEvent(String eventName, String username) {
        User user = requireUser(username);
        Event event = requireEvent(eventName);

        if (user.isBlocked()) {
            throw new EngineException("User is blocked!");
        }
        if (!user.isMarketMakerFor(eventName)) {
            throw new EngineException("You are not the MM!");
        }
        if (event.getStatus() != EventStatus.NOT_STARTED) {
            throw new EngineException("Event already started / finished");
        }

        // LMSR needs an upfront subsidy to seed the market (the MM's max
        // possible loss): C(0) with every option's shares at 0. Order Book
        // instead requires the MM's initial share purchase (spec, Order Book appendix).
        double subsidy = 0.0;
        if (event.getMethod() == TradingMethod.LMSR) {
            List<Integer> zeroQuantities = new ArrayList<>();
            for (Option opt : event.getOptions()) {
                zeroQuantities.add(0);
            }
            subsidy = LmsrCalculator.calculateCost(zeroQuantities, event.getbParameter());

            if (user.getBalance() < subsidy) {
                throw new EngineException(String.format(
                        "Cannot activate: MM balance (%.2f) is less than the required subsidy (%.2f)",
                        user.getBalance(), subsidy));
            }
        } else if (event.getMethod() == TradingMethod.ORDER_BOOK) {
            if (user.getBalance() < event.getInitialInvestment()) {
                throw new EngineException(String.format(
                        "Cannot activate: MM balance (%.2f) is less than the required initial investment (%d)",
                        user.getBalance(), event.getInitialInvestment()));
            }
        }

        event.activate();

        if (subsidy > 0) {
            user.adjustBalance(-subsidy, "LMSR subsidy for activating '" + eventName + "'");
            event.adjustAccountBalance(subsidy);
        }

        if (event.getMethod() == TradingMethod.ORDER_BOOK && event.getInitialInvestment() > 0) {
            // Spec example: d=1, initial=100 -> the MM receives 100 YES + 100 NO
            // pairs for $100 total (each pair pays out exactly d at close). The
            // MM's cash funds the event's pool exactly like the LMSR subsidy
            // does; the shares themselves are minted straight into the MM's own
            // holdings on each option.
            int pairs = event.getInitialInvestment() / event.getBaseValue();
            user.adjustBalance(-event.getInitialInvestment(),
                    "Initial investment in '" + eventName + "' (" + pairs + " YES/NO pairs)");
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
     * LMSR: buy shares of one option. Returns a summary of what was paid,
     * split between the shares themselves and any purchase-time commission.
     */
    public synchronized String buyShares(String eventName, int optionIndex, int amount, String username) {
        Event currentEvent = requireEvent(eventName);
        User user = requireUser(username);

        if (currentEvent.getMethod() != TradingMethod.LMSR) {
            throw new EngineException("This event uses Order Book trading — use Place Order instead.");
        }
        if (user.isBlocked()) {
            throw new EngineException("User is blocked!");
        }
        if (currentEvent.getStatus() != EventStatus.ACTIVE) {
            throw new EngineException("Event is not active!");
        }
        requireValidOptionIndex(currentEvent, optionIndex);
        if (amount <= 0) {
            throw new EngineException("Amount must be positive.");
        }
        User userMM = requireUser(currentEvent.getOwnerUsername());

        List<Integer> oldQuantities = new ArrayList<>();
        for (Option opt : currentEvent.getOptions()) {
            oldQuantities.add(opt.getShares());
        }

        int bParameter = currentEvent.getbParameter();
        double oldCost = LmsrCalculator.calculateCost(oldQuantities, bParameter);

        List<Integer> newQuantities = new ArrayList<>(oldQuantities);
        newQuantities.set(optionIndex, newQuantities.get(optionIndex) + amount);

        double newCost = LmsrCalculator.calculateCost(newQuantities, bParameter);
        double tradeCost = newCost - oldCost;

        String optionName = currentEvent.getOptions().get(optionIndex).getName();

        currentEvent.adjustAccountBalance(tradeCost);
        currentEvent.getOptions().get(optionIndex).addShares(amount);
        user.adjustBalance(-tradeCost, String.format("Bought %d x '%s' in '%s'", amount, optionName, eventName));

        double commissionAmount = 0.0;
        if (currentEvent.getCommissionType().equals("on-purchase")) {
            commissionAmount = tradeCost * (currentEvent.getCommissionRate() / 100.0);
            currentEvent.addCommission(commissionAmount);
            user.adjustBalance(-commissionAmount, "Purchase commission in '" + eventName + "'");
            userMM.adjustBalance(+commissionAmount, "Commission earned from " + username + " in '" + eventName + "'");
        }

        double totalPaid = tradeCost + commissionAmount;

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
    public synchronized String placeOrder(String eventName, int optionIndex, OrderSide side, int quantity, double price, String username) {
        Event event = requireEvent(eventName);
        User user = requireUser(username);

        if (event.getMethod() != TradingMethod.ORDER_BOOK) {
            throw new EngineException("This event uses LMSR trading — use Buy Shares instead.");
        }
        if (user.isBlocked()) {
            throw new EngineException("User is blocked!");
        }
        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new EngineException("Event is not active!");
        }
        requireValidOptionIndex(event, optionIndex);
        if (side == null) {
            throw new EngineException("Order side must be BUY or SELL.");
        }
        if (quantity <= 0) {
            throw new EngineException("Quantity must be positive.");
        }

        double maxPrice = event.getBaseValue() - 0.01;
        if (!(price >= 0.01 && price <= maxPrice)) { // also rejects NaN
            throw new EngineException(String.format("Price must be between 0.01 and %.2f", maxPrice));
        }

        Option thisOption = event.getOptions().get(optionIndex);
        Option otherOption = event.getOptions().get(optionIndex == 0 ? 1 : 0);

        if (side == OrderSide.SELL && thisOption.getHolding(username) < quantity) {
            throw new EngineException("Cannot sell " + quantity + " shares — you only hold " + thisOption.getHolding(username) + ".");
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
    private  void executeMatch(Event event, Option option, OrderSide incomingSide, User incomingUser,
                              Order restingOrder, int quantity, double execPrice) {
        User restingUser = activeUsers.get(restingOrder.getUsername());
        User buyer = incomingSide == OrderSide.BUY ? incomingUser : restingUser;
        User seller = incomingSide == OrderSide.BUY ? restingUser : incomingUser;

        double tradeValue = quantity * execPrice;
        String eventName = event.getName();
        buyer.adjustBalance(-tradeValue, String.format("Bought %d x '%s' @ %.2f in '%s'",
                quantity, option.getName(), execPrice, eventName));
        seller.adjustBalance(+tradeValue, String.format("Sold %d x '%s' @ %.2f in '%s'",
                quantity, option.getName(), execPrice, eventName));

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
        String eventName = event.getName();
        incomingUser.adjustBalance(-myCost, String.format("Bought %d x '%s' @ %.2f in '%s' (mint)",
                quantity, myOption.getName(), myPrice, eventName));
        otherUser.adjustBalance(-otherCost, String.format("Bought %d x '%s' @ %.2f in '%s' (mint)",
                quantity, otherOption.getName(), otherBid.getPrice(), eventName));
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
        buyer.adjustBalance(-commission, "Purchase commission in '" + event.getName() + "'");
        activeUsers.get(event.getOwnerUsername()).adjustBalance(+commission,
                "Commission earned from " + buyer.getName() + " in '" + event.getName() + "'");
        event.addCommission(commission);
        return commission;
    }

    /**
     * Order Book close: every share that ever existed was created by a mint
     * (or the MM's initial purchase) that put exactly d dollars into
     * event.accountBalance per YES/NO pair (matches never
     * touch the event account — buyer pays seller directly), so the winning
     * option's total outstanding shares always exactly equals
     * event.accountBalance / baseValue. That means the payout math is
     * identical in shape to LMSR's: figure out the total pool, optionally
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
            userMM.adjustBalance(+commissionAmount, "On-close commission from '" + event.getName() + "'");
        }

        double payoutPool = totalPool - commissionAmount;
        double payoutPerShare = winningShares > 0 ? payoutPool / winningShares : 0.0;

        for (Map.Entry<String, Integer> holder : winners.entrySet()) {
            if (holder.getValue() > 0) {
                activeUsers.get(holder.getKey()).adjustBalance(+payoutPerShare * holder.getValue(),
                        payoutDescription(holder.getValue(), winningOption.getName(), event.getName()));
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
     * Resolve and close an active event. Only the MM who owns the event may
     * close it.
     */
    public synchronized String closeEvent(String eventName, int winningOptionIndex, String username) {
        Event currentEvent = requireEvent(eventName);
        User userMM = requireUser(username);

        if (!userMM.isMarketMakerFor(eventName)) {
            throw new EngineException("You are not the MM!");
        }
        requireValidOptionIndex(currentEvent, winningOptionIndex);
        if (!currentEvent.close(winningOptionIndex)) {
            throw new EngineException("Event is not ACTIVE — cannot close it.");
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

            userMM.adjustBalance(+commissionAmount, "On-close commission from '" + eventName + "'");
        }

        int winningShares = currentEvent.getOptions().get(winningOptionIndex).getShares();
        double payoutPerShare = 0.0;
        if (winningShares > 0) {
            payoutPerShare = totalPool / winningShares;
        }

        // PAY WINNERS — LMSR never allows selling, so a user's winning shares
        // are simply the sum of their buys of that option. Summed per user
        // first so each winner gets ONE ledger line, not one per trade.
        String winningOption = currentEvent.getOptions().get(winningOptionIndex).getName();
        Map<String, Integer> winningSharesByUser = new LinkedHashMap<>();
        for (TradeRecord tradeRecord : currentEvent.getTradeHistory()) {
            if (tradeRecord.getOptionName().equals(winningOption)) {
                winningSharesByUser.merge(tradeRecord.getUsername(), tradeRecord.getQuantity(), Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> holder : winningSharesByUser.entrySet()) {
            activeUsers.get(holder.getKey()).adjustBalance(+payoutPerShare * holder.getValue(),
                    payoutDescription(holder.getValue(), winningOption, eventName));
        }
        currentEvent.adjustAccountBalance(-totalPool);

        return String.format(
                "Event Closed! Winning Option: %s%nTotal Pool (after fees): %.2f%nPayout per winning share: %.2f",
                winningOption, totalPool, payoutPerShare);
    }

    // ==========================================
    // 6. HELPERS
    // ==========================================

    private User requireUser(String username) {
        User user = username == null ? null : activeUsers.get(username);
        if (user == null) {
            throw new EngineException("User not found: " + username);
        }
        return user;
    }

    private Event requireEvent(String eventName) {
        Event event = eventName == null ? null : activeEvents.get(eventName);
        if (event == null) {
            throw new EngineException("Event not found: " + eventName);
        }
        return event;
    }

    // The option index now arrives from an HTTP request — never trust it.
    private void requireValidOptionIndex(Event event, int optionIndex) {
        if (optionIndex < 0 || optionIndex >= event.getOptions().size()) {
            throw new EngineException("Invalid option index: " + optionIndex);
        }
    }

    private static String payoutDescription(int shares, String optionName, String eventName) {
        return String.format("Payout for %d winning '%s' shares in '%s'", shares, optionName, eventName);
    }
}
