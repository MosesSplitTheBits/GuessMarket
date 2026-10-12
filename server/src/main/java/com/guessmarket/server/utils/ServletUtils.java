package com.guessmarket.server.utils;

import com.google.gson.Gson;
import com.guessmarket.engine.api.EngineException;
import com.guessmarket.engine.api.EngineManager;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Small helpers every servlet uses (same role as ServletUtils in the course
 * chat example).
 *
 * Response convention (mirrors the chat example):
 *   - success with data   -> 200 + JSON body
 *   - success, no data    -> 200 + plain-text message
 *   - failure             -> 4xx + plain-text message the client shows as-is
 *       400 Bad Request  : missing/invalid parameter, or the engine refused (EngineException)
 *       401 Unauthorized : no logged-in user in the session
 *       409 Conflict     : username already taken (login)
 */
public final class ServletUtils {

    public static final String ENGINE_ATTRIBUTE = "engine";

    // Gson is thread-safe, so one shared instance serves every request.
    public static final Gson GSON = new Gson();

    private ServletUtils() {
    }

    public static EngineManager getEngine(ServletContext context) {
        return (EngineManager) context.getAttribute(ENGINE_ATTRIBUTE);
    }

    public static void writeJson(HttpServletResponse response, Object data) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print(GSON.toJson(data));
    }

    public static void writeText(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().print(message);
    }

    /** The parameter trimmed, or null if it's missing or blank. */
    public static String getParameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** The parameter as an int, or null if it's missing or not a number. */
    public static Integer getIntParameter(HttpServletRequest request, String name) {
        String value = getParameter(request, name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The parameter as a double, or null if it's missing or not a number. */
    public static Double getDoubleParameter(HttpServletRequest request, String name) {
        String value = getParameter(request, name);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Runs one engine ACTION and answers with its outcome: 200 + the engine's
     * success message, or 400 + its EngineException message. This is the
     * try/catch from LoginServlet, written once for every action servlet.
     */
    public static void respondWithAction(HttpServletResponse response, Supplier<String> action) throws IOException {
        String message;
        try {
            message = action.get();
        } catch (EngineException e) {
            writeText(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }
        writeText(response, HttpServletResponse.SC_OK, message);
    }

    /** Same idea for a QUERY: 200 + the result as JSON, or 400 + the engine's message. */
    public static void respondWithJson(HttpServletResponse response, Supplier<?> query) throws IOException {
        Object data;
        try {
            data = query.get();
        } catch (EngineException e) {
            writeText(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }
        writeJson(response, data);
    }
}
