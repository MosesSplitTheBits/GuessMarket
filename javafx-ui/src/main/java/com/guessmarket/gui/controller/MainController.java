package com.guessmarket.gui.controller;

import com.guessmarket.engine.api.EngineManager;
import com.guessmarket.gui.task.LoadFileTask;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.FileChooser;

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
                }
                loadProgressBar.setVisible(false);

            });
            loadProgressBar.setVisible(true);
            new Thread(task).start();
        }
    }
}
