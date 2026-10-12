package com.guessmarket.dto;

import java.util.List;

/** The logged-in user's own account page. */
public record UserDetailsDto(
        String name,
        double balance,
        boolean blocked,
        List<String> managedEvents,       // events this user is MM of
        List<String> participatingEvents) { // events this user has traded in
}
