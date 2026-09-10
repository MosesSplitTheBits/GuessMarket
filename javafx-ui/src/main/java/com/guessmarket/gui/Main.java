package com.guessmarket.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * JavaFX entry point for this module — the GUI equivalent of console-ui's
 * Main. It only bootstraps the window; everything after that happens through
 * FXML + controllers, starting with MainController.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/guessmarket/gui/main-view.fxml"));
        Parent root = loader.load();

        // Without an explicit size, JavaFX sizes the window from whatever
        // content is showing at startup — before an event is selected, the
        // detail pane is nearly empty, so the window starts small and never
        // auto-grows later when a busier pane (like Order Book's) fills in.
        // A sensible fixed starting size avoids that trap; minWidth/minHeight
        // keep the window from being shrunk down to something no layout
        // could reasonably handle (the spec requires resizing to work, not
        // that it works at literally any size).
        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.setTitle("Guess Market");
        primaryStage.show();

    }

    public static void main(String[] args) {
        launch(args);
    }
}
