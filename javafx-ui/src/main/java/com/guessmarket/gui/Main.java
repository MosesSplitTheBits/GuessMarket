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
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Guess Market");
        primaryStage.show();

    }

    public static void main(String[] args) {
        launch(args);
    }
}
