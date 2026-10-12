package com.guessmarket.dto;

/**
 * Every endpoint path and request-parameter name, in ONE place shared by the
 * server (servlet mappings) and the client (URLs it builds). A typo on either
 * side becomes a compile error instead of a silent 404.
 */
public final class ApiConstants {

    private ApiConstants() {
    }

    // The WAR is built as guess-market.war -> Tomcat serves it under this path.
    public static final String CONTEXT_PATH = "/guess-market";

    // ----- endpoints (relative to the context path) -----
    // No logout endpoint, by design: users and their money live on in the
    // engine, so a logged-out "dan" could never log back in ("name taken").
    // A session simply ends when the client closes.
    public static final String LOGIN = "/login";
    public static final String UPLOAD = "/upload";
    public static final String EVENTS = "/events";
    public static final String EVENT = "/event";
    public static final String ACTIVATE = "/event/activate";
    public static final String CLOSE = "/event/close";
    public static final String BUY = "/event/buy";
    public static final String ORDER = "/event/order";
    public static final String USERS = "/users";
    public static final String ME = "/me";
    public static final String LEDGER = "/ledger";
    public static final String DEPOSIT = "/deposit";

    // ----- request parameter names -----
    public static final String USERNAME = "username";
    public static final String EVENT_NAME = "event";
    public static final String OPTION_INDEX = "option";
    public static final String QUANTITY = "quantity";
    public static final String PRICE = "price";
    public static final String SIDE = "side";          // "BUY" or "SELL"
    public static final String AMOUNT = "amount";
    public static final String FROM_INDEX = "from";    // ledger delta fetch
    public static final String FILE = "file";          // multipart part name
}
