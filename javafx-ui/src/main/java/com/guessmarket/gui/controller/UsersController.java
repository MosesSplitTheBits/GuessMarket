package com.guessmarket.gui.controller;

import com.guessmarket.engine.api.EngineManager;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

/**
 * Controller for users-tab.fxml. The engine has no concept of users/accounts
 * yet — that's still-to-do Exercise 2 work (GM-users in the new XML schema).
 * This controller exists so main-view.fxml has something to include and the
 * Users tab isn't blank; it'll get real content once that engine work lands.
 */
public class UsersController {

    private EngineManager engine;

    @FXML
    private ListView<String> usersListView; // TODO: swap String for a real User model once it exists

    public void setEngine(EngineManager engine) {
        this.engine = engine;
    }
}
