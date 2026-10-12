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
 * GET /me -> UserDetailsDto of the logged-in user (balance, blocked, managed
 * and participating events). The user comes from the session, never from a
 * parameter: you can only ever see your own private page (spec).
 */
@WebServlet(urlPatterns = ApiConstants.ME)
public class MeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = SessionUtils.requireUsername(request, response);
        if (username == null) {
            return;
        }
        EngineManager engine = ServletUtils.getEngine(getServletContext());
        ServletUtils.respondWithJson(response, () -> engine.getUserSnapshot(username));
    }
}
