package com.guessmarket.gui.controller;

import com.guessmarket.engine.api.EngineManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

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
        // TODO:
        //  1. Open a FileChooser and let the user pick an .xml file (spec
        //     requires this — no typed paths, no hardcoded directories).
        //  2. If they picked one, build a LoadFileTask(engine, file) (see the
        //     task package), bind loadProgressBar.progressProperty() to
        //     task.progressProperty(), and make the bar visible.
        //  3. On task success: read task.getValue() (the message EngineManager
        //     returned), update filePathLabel, and tell eventsTabController to
        //     refresh from the engine.
        //  4. Run the task on a background thread — new Thread(task).start() —
        //     never call slow engine methods directly here, this handler runs
        //     on the JavaFX Application Thread.
    }
}
