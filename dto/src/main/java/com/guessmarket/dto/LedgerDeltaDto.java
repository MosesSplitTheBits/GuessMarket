package com.guessmarket.dto;

import java.util.List;

/**
 * Delta-fetch response, same idea as the course chat example's
 * "entries + version": the client sends the version it already has, gets
 * back only the newer entries, and remembers the new version for next time.
 */
public record LedgerDeltaDto(List<LedgerEntryDto> entries, int version) {
}
