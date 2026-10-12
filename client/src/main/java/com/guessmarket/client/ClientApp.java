package com.guessmarket.client;

import com.guessmarket.client.http.HttpClientUtil;
import com.guessmarket.client.login.LoginController;
import com.guessmarket.client.main.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * JavaFX entry point. Shows the login screen; once the server accepts a
 * username, swaps the same window over to the main view (Events + Account
 * tabs) — like the course chat client's switchToChatRoom().
 */
public class ClientApp extends Application {

    private Stage stage;
    private MainController mainController; // null until logged in

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        FXMLLoader loader = load(ClientConstants.LOGIN_FXML);
        LoginController loginController = loader.getController();
        loginController.setOnLoggedIn(this::showMainView);

        stage.setScene(new Scene(loader.getRoot(), 420, 260));
        stage.setTitle("Guess Market — Login");
        stage.show();
    }

    /** Called by LoginController (on the JavaFX thread) after a successful login. */
    private void showMainView(String username) {
        FXMLLoader loader = load(ClientConstants.MAIN_FXML);
        mainController = loader.getController();
        mainController.start(username);

        // Same sizing reasoning as Ex2: a sensible starting size plus a floor
        // below which no layout could reasonably work (spec requires resize to
        // keep everything usable, not literally any size).
        Scene scene = new Scene(loader.getRoot(), 1200, 800);
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setTitle("Guess Market — " + username);
        stage.centerOnScreen();
    }

    /** Window closed: stop polling timers and OkHttp's threads so the JVM can exit. */
    @Override
    public void stop() {
        if (mainController != null) {
            mainController.close();
        }
        HttpClientUtil.shutdown();
    }

    private FXMLLoader load(String fxml) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
        try {
            loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + fxml, e);
        }
        return loader;
    }
}
