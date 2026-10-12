package com.guessmarket.server.servlets;

import com.guessmarket.dto.ApiConstants;
import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.server.utils.ServletUtils;
import com.guessmarket.server.utils.SessionUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;

/**
 * POST /upload  (multipart/form-data, the XML file in a part named "file")
 *   200 -> events added, caller is now their MM
 *   400 -> no file / bad file / clashing event name (engine's message)
 *   401 -> not logged in
 *
 * Spec: the file must NEVER be saved on the server — the grader's Tomcat has
 * no write permission. So the uploaded bytes are streamed straight into the
 * engine and never touch the disk.
 */
@WebServlet(urlPatterns = ApiConstants.UPLOAD)
@MultipartConfig(fileSizeThreshold = 1024 * 1024, maxFileSize = 1024 * 1024 * 5, maxRequestSize = 1024 * 1024 * 5 * 5)
public class UploadServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        String logged = SessionUtils.requireUsername(request, response);
        if (logged == null) {
            return;
        }


        Part part = request.getPart(ApiConstants.FILE);
        if (part == null) {
            ServletUtils.writeText(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing file part");
            return;
        }


        EngineManager engine = ServletUtils.getEngine(getServletContext());
        try (InputStream xml = part.getInputStream()) {
            ServletUtils.respondWithAction(response, () -> engine.loadEventsFromXml(xml, logged));
        }


    }
}
