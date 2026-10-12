package com.guessmarket.dto;

import java.util.Map;

/**
 * One outcome of an event. lmsrPrice is set only for LMSR events, orderBook
 * only for Order Book events. holdings = username -> shares currently held
 * (Order Book only; LMSR holdings are derived from the trade list).
 */
public record OptionDto(
        String name,
        int shares,
        Double lmsrPrice,
        OrderBookDto orderBook,
        Map<String, Integer> holdings) {
}
