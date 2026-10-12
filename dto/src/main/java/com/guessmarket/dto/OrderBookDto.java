package com.guessmarket.dto;

import java.util.List;

/** One option's book. Price stats are null when that side of the book is empty. */
public record OrderBookDto(
        Double lastTradePrice,
        Double bestBid,
        Double bestAsk,
        Double midPrice,
        Double spread,
        List<OrderDto> bids,   // best first
        List<OrderDto> asks) { // best first
}
