package com.guessmarket.client.refresh;

import com.guessmarket.client.ClientConstants;
import com.guessmarket.client.http.HttpClientUtil;
import com.guessmarket.dto.ApiConstants;
import com.guessmarket.dto.UserDetailsDto;
import javafx.application.Platform;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;
import java.util.TimerTask;
import java.util.function.Consumer;

/**
 * Polls GET /me — the logged-in user's own balance, blocked flag, and the
 * events they manage / take part in. Balance changes caused by OTHER users
 * (commission earned, payouts) show up here within one tick.
 */
public class MeRefresher extends TimerTask {

    private final Consumer<UserDetailsDto> meConsumer;

    public MeRefresher(Consumer<UserDetailsDto> meConsumer) {
        this.meConsumer = meConsumer;
    }

    @Override
    public void run() {
        HttpClientUtil.getAsync(ApiConstants.ME, Map.of(), new Callback() {
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
                UserDetailsDto me = ClientConstants.GSON.fromJson(body, UserDetailsDto.class);
                Platform.runLater(() -> meConsumer.accept(me));
            }
        });
    }
}
