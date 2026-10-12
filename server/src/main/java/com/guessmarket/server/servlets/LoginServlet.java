package com.guessmarket.server.servlets;

import com.guessmarket.dto.ApiConstants;
import com.guessmarket.engine.api.EngineException;
import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.server.utils.ServletUtils;
import com.guessmarket.server.utils.SessionUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * POST /login?username=dan
 *   200 + welcome message -> registered, and this session is now "dan"
 *   400                   -> no username given
 *   409                   -> name already taken (spec: show an error, let them retry)
 *
 */
@WebServlet(urlPatterns = ApiConstants.LOGIN)
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // This session is already logged in — nothing to do.
        String alreadyLoggedIn = SessionUtils.getUsername(request);
        if (alreadyLoggedIn != null) {
            ServletUtils.writeText(response, HttpServletResponse.SC_OK,
                    "Already logged in as " + alreadyLoggedIn);
            return;
        }

        String username = ServletUtils.getParameter(request, ApiConstants.USERNAME);
        if (username == null) {
            ServletUtils.writeText(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing username");
            return;
        }

        // No synchronized(this) needed here, unlike the course example:
        // registerUser is itself a synchronized engine method that does the
        // "does the name exist? -> add it" check-then-act as ONE atomic step,
        // so two simultaneous logins with the same name can't both succeed.
        EngineManager engine = ServletUtils.getEngine(getServletContext());
        String registeredName;
        try {
            registeredName = engine.registerUser(username);
        } catch (EngineException e) {
            ServletUtils.writeText(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
            return;
        }

        // getSession(true) creates the session; Tomcat sends its id back as the
        // JSESSIONID cookie, and every later request carrying that cookie is
        // recognized as this user. Store the name the ENGINE registered, so the
        // session and the engine can never disagree on who this is.
        request.getSession(true).setAttribute(SessionUtils.USERNAME_ATTRIBUTE, registeredName);

        ServletUtils.writeText(response, HttpServletResponse.SC_OK, "Welcome " + registeredName);
    }
}
