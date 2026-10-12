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

/** POST /event/activate?event=NAME -> the MM starts trading (pays subsidy / initial investment). */
@WebServlet(urlPatterns = ApiConstants.ACTIVATE)
public class ActivateEventServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
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
        ServletUtils.respondWithAction(response, () -> engine.activateEvent(eventName, username));
    }
}
