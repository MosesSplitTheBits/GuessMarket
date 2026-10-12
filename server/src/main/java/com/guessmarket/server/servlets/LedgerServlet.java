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
 * GET /ledger?from=N -> LedgerDeltaDto: the caller's ledger lines from index
 * N onward + the new version. Delta fetching (like the course chat's
 * "chatversion"): the client polls with the version it already has, so each
 * poll only transfers what's new. Missing/invalid "from" = 0 (everything).
 */
@WebServlet(urlPatterns = ApiConstants.LEDGER)
public class LedgerServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = SessionUtils.requireUsername(request, response);
        if (username == null) {
            return;
        }
        Integer from = ServletUtils.getIntParameter(request, ApiConstants.FROM_INDEX);
        int fromIndex = from == null ? 0 : from;
        EngineManager engine = ServletUtils.getEngine(getServletContext());
        ServletUtils.respondWithJson(response, () -> engine.getLedgerDelta(username, fromIndex));
    }
}
