package com.guessmarket.gui.task;

import com.guessmarket.engine.api.EngineManager;
import javafx.concurrent.Task;

import java.io.File;

/**
 * Runs EngineManager.loadDataFromXml(...) off the JavaFX Application Thread.
 * The spec requires a visible progress indicator during load, plus a short
 * artificial delay (the real parse is too fast to actually see progress on).
 * We'll walk through Task's threading model properly before filling this in.
 */
public class LoadFileTask extends Task<String> {

    private final EngineManager engine;
    private final File file;

    public LoadFileTask(EngineManager engine, File file) {
        this.engine = engine;
        this.file = file;
    }

    @Override
    protected String call() throws Exception {
        updateProgress(0, 1);
        Thread.sleep(1000);
        updateProgress(1, 2);
        Thread.sleep(1000);
        String result = engine.loadDataFromXml(file.getAbsolutePath());
        updateProgress(1, 1);
        return result;
    }
}
