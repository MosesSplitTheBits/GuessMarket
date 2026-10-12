package com.guessmarket.server.servlets;

import com.guessmarket.dto.ApiConstants;
import com.guessmarket.dto.EventSummaryDto;
import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.server.utils.ServletUtils;
import com.guessmarket.server.utils.SessionUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * GET /events -> JSON array of every event in the system (any state, any
 * uploader). Polled by every client to keep its events list fresh.
 *
 * Reference example for the other servlets — every one follows the same
 * three steps: (1) who is calling?  (2) ask the engine  (3) write the answer.
 */
@WebServlet(urlPatterns = ApiConstants.EVENTS)
public class EventsListServlet extends HttpServlet {

    // Tomcat creates ONE instance of this class and calls doGet from many
    // threads at once — so no per-request data may ever live in a field.
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // (1) Only logged-in users may see the system. requireUsername has
        // already answered 401 when it returns null — we just stop.
        String username = SessionUtils.requireUsername(request, response);
        if (username == null) {
            return; // <- the line the course's ChatServlet forgot
        }

        // (2) The engine hands back immutable snapshots, built inside its own
        // lock — safe to use here with no further synchronization.
        EngineManager engine = ServletUtils.getEngine(getServletContext());
        List<EventSummaryDto> events = engine.getEventsSnapshot();

        // (3) Serialize to JSON with status 200.
        ServletUtils.writeJson(response, events);
    }
}
