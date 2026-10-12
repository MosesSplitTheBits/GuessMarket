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

/** POST /deposit?amount=X -> adds X to the caller's own balance. */
@WebServlet(urlPatterns = ApiConstants.DEPOSIT)
public class DepositServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = SessionUtils.requireUsername(request, response);
        if (username == null) {
            return;
        }
        Double amount = ServletUtils.getDoubleParameter(request, ApiConstants.AMOUNT);
        if (amount == null) {
            ServletUtils.writeText(response, HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid amount");
            return;
        }
        EngineManager engine = ServletUtils.getEngine(getServletContext());
        ServletUtils.respondWithAction(response, () -> engine.deposit(username, amount));
    }
}
