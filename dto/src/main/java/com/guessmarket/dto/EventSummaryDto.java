package com.guessmarket.dto;

import java.util.List;

/**
 * One row of the events list. Enum-like fields are plain Strings (the enum
 * constant's name, e.g. "ACTIVE", "ORDER_BOOK") so this module needs no
 * engine types and the JSON stays readable by any client.
 */
public record EventSummaryDto(
        String name,
        String description,
        String method,          // "LMSR" | "ORDER_BOOK"
        String status,          // "NOT_STARTED" | "ACTIVE" | "CLOSED"
        String commissionType,  // "on-purchase" | "on-close"
        int commissionRate,
        String marketMaker,
        List<String> optionNames) {
}
