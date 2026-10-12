package com.guessmarket.client;

import com.google.gson.Gson;
import com.guessmarket.dto.ApiConstants;

/**
 * Client-side constants. Endpoint paths and parameter names are NOT here —
 * they come from the shared ApiConstants, so client and server can't drift.
 */
public final class ClientConstants {

    private ClientConstants() {
    }

    // Spec: the client may assume the server is localhost:8080 and that the
    // WAR keeps its name (so the context path is fixed).
    public static final String BASE_URL = "http://localhost:8080" + ApiConstants.CONTEXT_PATH;

    // Spec: pull every <= 2s; ~0.5s is "reasonable".
    public static final long REFRESH_RATE_MS = 500;

    // Gson is thread-safe: one instance for the whole client.
    public static final Gson GSON = new Gson();

    public static final String LOGIN_FXML = "/com/guessmarket/client/login.fxml";
    public static final String MAIN_FXML = "/com/guessmarket/client/main-view.fxml";
}
