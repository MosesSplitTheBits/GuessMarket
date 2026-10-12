package com.guessmarket.client.refresh;

import com.guessmarket.client.ClientConstants;
import com.guessmarket.client.http.HttpClientUtil;
import com.guessmarket.dto.ApiConstants;
import com.guessmarket.dto.EventDetailsDto;
import javafx.application.Platform;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Polls GET /event?event=<selected> and hands the fresh EventDetailsDto to
 * the detail panel — the "pull" the spec asks for, so trades made by OTHER
 * users show up on this screen within one tick.
 *
 * Same shape as ChatAreaRefresher in the course chat client:
 *   a java.util.Timer calls run() every REFRESH_RATE_MS on the Timer's thread
 *   -> run() fires an ASYNC request (returns at once, never blocks the Timer)
 *   -> the answer arrives on an OkHttp thread
 *   -> parsed there, then handed to the UI via Platform.runLater.
 * So the consumer always runs on the JavaFX thread and may touch the UI.
 */
public class EventDetailRefresher extends TimerTask {

    private final Supplier<String> selectedEventName;
    private final Consumer<EventDetailsDto> detailsConsumer;

    public EventDetailRefresher(Supplier<String> selectedEventName, Consumer<EventDetailsDto> detailsConsumer) {
        this.selectedEventName = selectedEventName;
        this.detailsConsumer = detailsConsumer;
    }

    @Override
    public void run() {
        String eventName = selectedEventName.get();
        if (eventName == null) {
            return; // nothing selected — nothing to fetch
        }

        HttpClientUtil.getAsync(ApiConstants.EVENT, Map.of(ApiConstants.EVENT_NAME, eventName), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Server unreachable this tick — just try again on the next one.
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = HttpClientUtil.bodyOf(response);
                if (response.code() != 200) {
                    return; // e.g. 401 / 400 — nothing useful to show this tick
                }
                // Parse on THIS thread (CPU work stays off the UI thread);
                // only the finished DTO crosses over to JavaFX.
                EventDetailsDto details = ClientConstants.GSON.fromJson(body, EventDetailsDto.class);
                Platform.runLater(() -> detailsConsumer.accept(details));
            }
        });
    }
}
