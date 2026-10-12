package com.guessmarket.dto;

/** What every user may see about every other user (spec: name, balance, is-MM). */
public record UserSummaryDto(String name, double balance, boolean marketMaker, boolean blocked) {
}
