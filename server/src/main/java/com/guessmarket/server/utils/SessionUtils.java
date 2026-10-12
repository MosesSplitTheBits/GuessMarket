package com.guessmarket.server.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Who is calling? HTTP itself is stateless — every request arrives on its
 * own. Tomcat's session (identified by the JSESSIONID cookie the client
 * sends back with each request) is how we remember "this browser/client is
 * logged in as dan" between requests.
 */
public final class SessionUtils {

    public static final String USERNAME_ATTRIBUTE = "username";

    private SessionUtils() {
    }

    /** The logged-in username, or null if this request has no logged-in session. */
    public static String getUsername(HttpServletRequest request) {
        // false = don't CREATE a session just to look — a request without one
        // simply isn't logged in.
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object username = session.getAttribute(USERNAME_ATTRIBUTE);
        return username == null ? null : username.toString();
    }

    /**
     * The "logged in?" block every protected servlet starts with, in one place:
     * returns the username, or — if nobody is logged in — answers 401 and
     * returns null. Callers do:
     *   String username = SessionUtils.requireUsername(request, response);
     *   if (username == null) return;
     */
    public static String requireUsername(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = getUsername(request);
        if (username == null) {
            ServletUtils.writeText(response, HttpServletResponse.SC_UNAUTHORIZED, "Please log in first.");
        }
        return username;
    }
}
