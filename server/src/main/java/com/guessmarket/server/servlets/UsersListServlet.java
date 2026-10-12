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
 * GET /users -> every user's public info (name, balance, is-MM, blocked).
 * Includes the caller; the client decides whether to show its own row.
 */
@WebServlet(urlPatterns = ApiConstants.USERS)
public class UsersListServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = SessionUtils.requireUsername(request, response);
        if (username == null) {
            return;
        }
        EngineManager engine = ServletUtils.getEngine(getServletContext());
        ServletUtils.writeJson(response, engine.getUsersSnapshot());
    }
}
