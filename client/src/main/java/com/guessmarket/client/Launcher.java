package com.guessmarket.client;

import javafx.application.Application;

/**
 * Separate entry point so the packaged jar (and IDE "Run" buttons) work
 * without extra VM flags — same trick as Ex2's Launcher: the JDK launcher
 * refuses to start a main class that itself extends Application unless
 * JavaFX is on the module path. Launching ClientApp indirectly avoids that.
 */
public class Launcher {

    public static void main(String[] args) {
        Application.launch(ClientApp.class, args);
    }
}
