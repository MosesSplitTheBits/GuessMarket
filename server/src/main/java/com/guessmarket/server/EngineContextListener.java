package com.guessmarket.server;

import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.server.utils.ServletUtils;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Runs once, when Tomcat deploys the web app — before any request arrives.
 * Creates THE single EngineManager and stores it in the ServletContext, the
 * one storage area shared by every servlet and every request.
 *
 */
@WebListener
public class EngineContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        sce.getServletContext().setAttribute(ServletUtils.ENGINE_ATTRIBUTE, new EngineManager());
    }
}
