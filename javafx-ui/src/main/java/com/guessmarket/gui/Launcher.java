package com.guessmarket.gui;

import javafx.application.Application;

/**
 * Separate entry point so IDE "Run" buttons work without extra VM flags.
 * The JDK launcher special-cases a main class that itself extends
 * Application: it demands JavaFX be on the module path (--module-path /
 * --add-modules), not just the classpath, and refuses to start otherwise —
 * even though this project deliberately isn't set up as a modular app.
 * Launching Main indirectly from a non-Application class sidesteps that check.
 */
public class Launcher {

    public static void main(String[] args) {
        Application.launch(Main.class, args);
    }
}
