package com.guessmarket.server.servlets;

import com.guessmarket.dto.ApiConstants;
import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.server.utils.ServletUtils;
import com.guessmarket.server.utils.SessionUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * GET /event?event=NAME -> EventDetailsDto (books, holdings, trades...).
 * 400 if the event doesn't exist.
 */
@WebServlet(urlPatterns = ApiConstants.EVENT)
public class EventDetailsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = SessionUtils.requireUsername(request, response);
        if (username == null) {
            return;
        }
        String eventName = ServletUtils.getParameter(request, ApiConstants.EVENT_NAME);
        if (eventName == null) {
            ServletUtils.writeText(response, HttpServletResponse.SC_BAD_REQUEST, "Missing event name");
            return;
        }
        EngineManager engine = ServletUtils.getEngine(getServletContext());
        ServletUtils.respondWithJson(response, () -> engine.getEventSnapshot(eventName));
    }
}
