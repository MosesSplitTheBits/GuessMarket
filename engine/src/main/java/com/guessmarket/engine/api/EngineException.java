package com.guessmarket.engine.api;

/**
 * Thrown by EngineManager whenever a requested action breaks a rule
 * (unknown event, not the MM, bad file, insufficient holdings...).
 * The message is user-facing — the server sends it back as-is.
 *
 * Unchecked (RuntimeException) on purpose: every public engine method can
 * fail this way, so a checked exception would just clutter every signature.
 * Callers that care (the servlets) catch it in one place and turn it into
 * an HTTP error status; a successful call simply returns its message.
 */
public class EngineException extends RuntimeException {

    public EngineException(String message) {
        super(message);
    }
}
