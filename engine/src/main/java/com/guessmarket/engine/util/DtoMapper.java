package com.guessmarket.engine.util;

import com.guessmarket.dto.*;
import com.guessmarket.engine.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts live, mutable engine models into immutable DTO "photos".
 *
 * Must only be called while holding the EngineManager lock (i.e. from inside
 * one of its synchronized methods): it reads many objects that other
 * threads change, and the whole photo has to be taken at one consistent
 * moment. Every collection is copied (List.copyOf / Map.copyOf), so nothing
 * in a DTO is a live view of engine state.
 */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static EventSummaryDto toSummary(Event event) {
        return new EventSummaryDto(
                event.getName(),
                event.getDescription(),
                event.getMethod().name(),
                event.getStatus().name(),
                event.getCommissionType(),
                (int) event.getCommissionRate(),
                event.getOwnerUsername(),
                event.getOptions().stream().map(Option::getName).toList());
    }

    public static EventDetailsDto toDetails(Event event) {
        List<Integer> allQuantities = event.getOptions().stream().map(Option::getShares).toList();
        boolean isLmsr = event.getMethod() == TradingMethod.LMSR;

        List<OptionDto> options = new ArrayList<>();
        for (Option option : event.getOptions()) {
            Double lmsrPrice = isLmsr
                    ? LmsrCalculator.calculateOptionPrice(option.getShares(), allQuantities, event.getbParameter())
                    : null;
            OrderBookDto book = isLmsr ? null : toOrderBook(option.getOrderBook());
            options.add(new OptionDto(option.getName(), option.getShares(), lmsrPrice, book,
                    Map.copyOf(option.getAllHoldings())));
        }

        List<TradeDto> trades = event.getTradeHistory().stream().map(DtoMapper::toTrade).toList();

        return new EventDetailsDto(
                event.getName(),
                event.getDescription(),
                event.getMethod().name(),
                event.getStatus().name(),
                event.getCommissionType(),
                (int) event.getCommissionRate(),
                event.getOwnerUsername(),
                event.getWinningOptionIndex(),
                event.getAccountBalance(),
                event.getTotalCommissionsCollected(),
                event.getbParameter(),
                event.getBaseValue(),
                event.isAllowMint(),
                event.getInitialInvestment(),
                List.copyOf(options),
                trades);
    }

    private static OrderBookDto toOrderBook(OrderBook book) {
        return new OrderBookDto(
                book.getLastTradePrice(),
                book.getBestBidPrice(),
                book.getBestAskPrice(),
                book.getMidPrice(),
                book.getSpread(),
                book.getBids().stream().map(DtoMapper::toOrder).toList(),
                book.getAsks().stream().map(DtoMapper::toOrder).toList());
    }

    private static OrderDto toOrder(Order order) {
        return new OrderDto(order.getUsername(), order.getPrice(), order.getRemainingQuantity());
    }

    private static TradeDto toTrade(TradeRecord trade) {
        return new TradeDto(trade.getTimestamp(), trade.getUsername(), trade.getSide().name(),
                trade.getOptionName(), trade.getQuantity(), trade.getPricePaid(), trade.getCommissionAmount());
    }

    public static UserSummaryDto toSummary(User user) {
        return new UserSummaryDto(user.getName(), user.getBalance(), user.isMarketMaker(), user.isBlocked());
    }

    /**
     * allEvents is needed to find which events this user has traded in
     * ("participating" counts from the first trade, not from being MM — same
     * rule as Ex2's user screen).
     */
    public static UserDetailsDto toDetails(User user, List<Event> allEvents) {
        List<String> managed = new ArrayList<>();
        List<String> participating = new ArrayList<>();
        for (Event event : allEvents) {
            if (user.isMarketMakerFor(event.getName())) {
                managed.add(event.getName());
            }
            boolean traded = event.getTradeHistory().stream()
                    .anyMatch(trade -> trade.getUsername().equals(user.getName()));
            if (traded) {
                participating.add(event.getName());
            }
        }
        return new UserDetailsDto(user.getName(), user.getBalance(), user.isBlocked(),
                List.copyOf(managed), List.copyOf(participating));
    }

    public static LedgerEntryDto toDto(LedgerEntry entry) {
        return new LedgerEntryDto(entry.timestamp(), entry.description(), entry.amount(), entry.balanceAfter());
    }
}
