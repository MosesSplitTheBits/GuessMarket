package com.guessmarket.gui.controller;

import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.engine.model.User;
import com.guessmarket.gui.task.LoadFileTask;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;

/**
 * Controller for main-view.fxml — the top bar (load button + path label) and
 * the TabPane hosting the Events/Users tabs.
 */
public class MainController {

    // One EngineManager for the whole app's lifetime, owned here and handed
    // down to every tab controller — mirrors how console-ui's Main held a
    // single EngineManager and drove it for the whole run.
    private final EngineManager engine = new EngineManager();

    @FXML
    private Button loadFileButton;

    @FXML
    private Label filePathLabel;

    @FXML
    private ProgressBar loadProgressBar;

    @FXML
    private ComboBox<User> currentUserComboBox;

    @FXML
    private Label currentUserLabel;

    // Which user is "acting" right now — null until one's picked from
    // currentUserComboBox. Future features (buying shares, activating
    // events) will read this via getCurrentUser().
    private User currentUser;

    public User getCurrentUser() {
        return currentUser;
    }

    // Injected automatically by FXMLLoader: main-view.fxml includes
    // events-tab.fxml with fx:id="eventsTab", so the loader looks for a field
    // named "eventsTabController" here and wires it to that include's
    // controller instance. Same story for usersTabController below.
    @FXML
    private EventsController eventsTabController;

    @FXML
    private UsersController usersTabController;

    @FXML
    private void initialize() {

        eventsTabController.setEngine(engine);
        usersTabController.setEngine(engine);
        loadProgressBar.setVisible(false);


        currentUserComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(User user) {
                if (user != null) {
                    return user.getName();
                }
                else {
                    return null;
                }
            }
            @Override
            public User fromString(String string) { //Dont receive input here
                return null;
            }
        });


        currentUserComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                this.currentUser = newValue;
                currentUserLabel.setText("Hello, " + currentUser.getName());
            }
            else {this.currentUser = null;
            currentUserLabel.setText("No User Selected");}


        });
    }

    @FXML
    private void handleLoadFile() {
        FileChooser chooser = new FileChooser();
        //Making file explorer only show .xml files
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));
        File file = chooser.showOpenDialog(loadFileButton.getScene().getWindow());
        if (file != null) {
            LoadFileTask task = new LoadFileTask(engine, file);
            loadProgressBar.progressProperty().bind(task.progressProperty());
            task.setOnSucceeded(event -> {
                String message = task.getValue();
                filePathLabel.setText(message);
                if(message.startsWith("XML loaded successfully")){
                    eventsTabController.refreshEvents();
                    currentUserComboBox.getItems().setAll(engine.getAllUsers());
                }
                loadProgressBar.setVisible(false);

            });
            loadProgressBar.setVisible(true);
            new Thread(task).start();
        }
    }
}
