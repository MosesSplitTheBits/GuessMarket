package com.guessmarket.dto;

import java.util.List;

/** Everything the event-detail screen shows, frozen at one moment in time. */
public record EventDetailsDto(
        String name,
        String description,
        String method,
        String status,
        String commissionType,
        int commissionRate,
        String marketMaker,
        Integer winningOptionIndex,   // null until CLOSED
        double accountBalance,
        double totalCommissions,
        int bParameter,               // LMSR only
        int baseValue,                // Order Book only ("d")
        boolean allowMint,            // Order Book only
        int initialInvestment,        // Order Book only
        List<OptionDto> options,
        List<TradeDto> trades) {      // oldest first
}
