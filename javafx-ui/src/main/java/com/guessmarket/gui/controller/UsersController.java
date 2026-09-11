package com.guessmarket.gui.controller;

import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.engine.model.User;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import java.util.List;

/**
 * Controller for users-tab.fxml — the left-hand user list, plus the
 * right-hand detail panel (user-detail.fxml, included here).
 */
public class UsersController {

    private EngineManager engine;

    @FXML
    private ListView<User> usersListView;

    // Injected the same way EventsController got eventDetailController:
    // user-detail.fxml is included here with fx:id="userDetail".
    @FXML
    private UserDetailController userDetailController;

    public void setEngine(EngineManager engine) {
        this.engine = engine;

        //Cell factory to turn User into text in the ListView
        usersListView.setCellFactory(listView -> new ListCell<User>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                setText(empty || user == null ? null : user.getName());
            }
        });

        //Set listener to send selected user (plus all events, to find which
        //ones they've traded in) to UserDetailController
        usersListView.getSelectionModel().selectedItemProperty().addListener((obs, oldUser, newUser) -> {
            userDetailController.showUser(newUser, engine.getAllEvents());
        });
    }

    /**
     * Called by MainController after a file load, and after any trading
     * action completes (balances can change on either side of a trade) —
     * also re-shows whichever user is currently selected so their balance
     * label picks up the change (same User object, same list index, so the
     * ListView's own selection listener won't refire on its own).
     */
    public void refreshUsers() {
        List<User> userList = engine.getAllUsers();
        usersListView.getItems().setAll(userList);
        userDetailController.showUser(usersListView.getSelectionModel().getSelectedItem(), engine.getAllEvents());
    }
}
