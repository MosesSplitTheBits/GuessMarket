package com.guessmarket.client.refresh;

import com.guessmarket.client.ClientConstants;
import com.guessmarket.client.http.HttpClientUtil;
import com.guessmarket.dto.ApiConstants;
import com.guessmarket.dto.EventSummaryDto;
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
 * Polls GET /events every tick and hands the list to the events screen, so
 * events uploaded by OTHER users (and status changes) appear automatically.
 *
 * Read EventDetailRefresher first — this is the same pattern, simpler (no
 * "which event?" question: always the whole list).
 */
public class EventsRefresher extends TimerTask {

    private final Consumer<List<EventSummaryDto>> eventsConsumer;

    public EventsRefresher(Consumer<List<EventSummaryDto>> eventsConsumer) {
        this.eventsConsumer = eventsConsumer;
    }

    @Override
    public void run() {
        // Async on purpose: ONE Timer thread runs every refresher in turn, so
        // run() must return at once — a blocking call would stall them all.
        HttpClientUtil.getAsync(ApiConstants.EVENTS, Map.of(), new Callback() {
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
                // only the finished list crosses over to JavaFX. The JSON is an
                // ARRAY ([...]), so parse it as an array type.
                EventSummaryDto[] events = ClientConstants.GSON.fromJson(body, EventSummaryDto[].class);
                Platform.runLater(() -> eventsConsumer.accept(List.of(events)));
            }
        });
    }
}
