package com.guessmarket.client.refresh;

import com.guessmarket.client.ClientConstants;
import com.guessmarket.client.http.HttpClientUtil;
import com.guessmarket.dto.ApiConstants;
import com.guessmarket.dto.UserSummaryDto;
import javafx.application.Platform;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.function.Consumer;

/**
 * Polls GET /users (every user's name, balance, is-MM) — same pattern as
 * EventsRefresher: full fetch each tick, the list is small.
 */
public class UsersRefresher extends TimerTask {

    private final Consumer<List<UserSummaryDto>> usersConsumer;

    public UsersRefresher(Consumer<List<UserSummaryDto>> usersConsumer) {
        this.usersConsumer = usersConsumer;
    }

    @Override
    public void run() {
        HttpClientUtil.getAsync(ApiConstants.USERS, Map.of(), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Server unreachable this tick — try again on the next one.
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = HttpClientUtil.bodyOf(response);
                if (response.code() != 200) {
                    return;
                }
                UserSummaryDto[] users = ClientConstants.GSON.fromJson(body, UserSummaryDto[].class);
                Platform.runLater(() -> usersConsumer.accept(List.of(users)));
            }
        });
    }
}
