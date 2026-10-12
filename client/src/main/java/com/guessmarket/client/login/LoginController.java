package com.guessmarket.client.login;

import com.guessmarket.client.http.HttpClientUtil;
import com.guessmarket.dto.ApiConstants;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Login screen (spec: username only, unique; on a taken name show an error and
 * let the user try again). Compare LoginController in the course chat client.
 */
public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private Button loginButton;

    @FXML
    private Label errorLabel;

    // Set by ClientApp: what to do once the server accepted us (switch screens).
    private Consumer<String> onLoggedIn;

    public void setOnLoggedIn(Consumer<String> onLoggedIn) {
        this.onLoggedIn = onLoggedIn;
    }

    @FXML
    private void initialize() {
        // Typing again clears a stale error message.
        usernameField.textProperty().addListener((obs, oldText, newText) -> errorLabel.setText(""));
    }

    // Wired to the button AND to Enter in the text field (see login.fxml).
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();

        // No point asking the server about an empty name.
        if (username.isEmpty()) {
            errorLabel.setText("Please enter username");
            return;
        }

        // One request at a time: without this, a double-click sends two
        // logins — the first succeeds, the second comes back 409 "name taken"
        // (by then the name IS taken — by us) and shows a confusing error.
        loginButton.setDisable(true);

        // Returns IMMEDIATELY. The answer arrives later, on an OkHttp worker
        // thread, in one of the two callback methods below — so the window
        // never freezes while we wait.
        HttpClientUtil.postAsync(ApiConstants.LOGIN, Map.of(ApiConstants.USERNAME, username), new Callback() {

            // The server could not be reached at all (Tomcat down, wrong port...).
            @Override
            public void onFailure(Call call, IOException e) {
                Platform.runLater(() -> showError("Cannot reach the server: " + e.getMessage()));
            }

            // The server answered — with ANY status, including 409/400.
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // Read here, on the OkHttp thread: reading the body is network
                // I/O, and a body can be read only ONCE — so read it now and
                // pass plain values into the UI code.
                String body = HttpClientUtil.bodyOf(response);
                int code = response.code();

                // Still on the OkHttp thread: everything that touches the UI
                // (or switches screens) is handed over to the JavaFX thread.
                Platform.runLater(() -> {
                    if (code == 200) {
                        onLoggedIn.accept(username);
                    } else {
                        showError(body); // e.g. 409 "Username 'dan' is already taken..."
                    }
                });
            }
        });
    }

    // Always runs on the JavaFX thread (called from inside Platform.runLater).
    private void showError(String message) {
        errorLabel.setText(message);
        loginButton.setDisable(false); // let the user fix the name and retry
    }
}
