package com.guessmarket.server.servlets;

import com.guessmarket.dto.ApiConstants;
import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.engine.model.OrderSide;
import com.guessmarket.server.utils.ServletUtils;
import com.guessmarket.server.utils.SessionUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** POST /event/order?event=NAME&option=I&side=BUY|SELL&quantity=Q&price=P -> Order Book order. */
@WebServlet(urlPatterns = ApiConstants.ORDER)
public class PlaceOrderServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = SessionUtils.requireUsername(request, response);
        if (username == null) {
            return;
        }
        String eventName = ServletUtils.getParameter(request, ApiConstants.EVENT_NAME);
        Integer option = ServletUtils.getIntParameter(request, ApiConstants.OPTION_INDEX);
        Integer quantity = ServletUtils.getIntParameter(request, ApiConstants.QUANTITY);
        Double price = ServletUtils.getDoubleParameter(request, ApiConstants.PRICE);
        OrderSide side = parseSide(ServletUtils.getParameter(request, ApiConstants.SIDE));
        if (eventName == null || option == null || quantity == null || price == null || side == null) {
            ServletUtils.writeText(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing or invalid event, option, side (BUY/SELL), quantity or price");
            return;
        }
        EngineManager engine = ServletUtils.getEngine(getServletContext());
        ServletUtils.respondWithAction(response,
                () -> engine.placeOrder(eventName, option, side, quantity, price, username));
    }

    /** "buy"/"BUY" -> BUY, "sell"/"SELL" -> SELL, anything else -> null. */
    private static OrderSide parseSide(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return OrderSide.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
