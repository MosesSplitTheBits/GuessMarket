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

/** POST /event/buy?event=NAME&option=I&quantity=Q -> LMSR purchase. */
@WebServlet(urlPatterns = ApiConstants.BUY)
public class BuySharesServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = SessionUtils.requireUsername(request, response);
        if (username == null) {
            return;
        }
        String eventName = ServletUtils.getParameter(request, ApiConstants.EVENT_NAME);
        Integer option = ServletUtils.getIntParameter(request, ApiConstants.OPTION_INDEX);
        Integer quantity = ServletUtils.getIntParameter(request, ApiConstants.QUANTITY);
        if (eventName == null || option == null || quantity == null) {
            ServletUtils.writeText(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing or invalid event, option or quantity");
            return;
        }
        EngineManager engine = ServletUtils.getEngine(getServletContext());
        ServletUtils.respondWithAction(response, () -> engine.buyShares(eventName, option, quantity, username));
    }
}
