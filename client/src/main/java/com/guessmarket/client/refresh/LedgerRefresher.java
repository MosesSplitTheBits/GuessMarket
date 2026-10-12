package com.guessmarket.client.refresh;

import com.guessmarket.client.ClientConstants;
import com.guessmarket.client.http.HttpClientUtil;
import com.guessmarket.dto.ApiConstants;
import com.guessmarket.dto.LedgerDeltaDto;
import com.guessmarket.dto.LedgerEntryDto;
import javafx.application.Platform;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Polls GET /ledger?from=N with DELTA fetching (like the course chat's
 * "chatversion"): the ledger only ever grows, so instead of re-downloading the
 * whole history every tick we send the version we already have and receive
 * only the newer lines, plus the new version to use next time.
 *
 * The consumer APPENDS what it receives — so the same lines must never be
 * delivered twice. That could happen if a tick fires while the previous
 * request is still on its way (both would ask "from 5"). Hence inFlight: a
 * tick is skipped until the previous answer (or failure) has come back.
 */
public class LedgerRefresher extends TimerTask {

    private final Consumer<List<LedgerEntryDto>> newEntriesConsumer;

    // Touched by the Timer thread (run) and OkHttp threads (callbacks), never
    // at the same moment thanks to inFlight — volatile makes each thread see
    // the other's latest write.
    private volatile int version = 0;
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    public LedgerRefresher(Consumer<List<LedgerEntryDto>> newEntriesConsumer) {
        this.newEntriesConsumer = newEntriesConsumer;
    }

    @Override
    public void run() {
        // compareAndSet: "if no request is running, mark one as running" as ONE
        // atomic step — two quick calls can't both get through.
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }

        HttpClientUtil.getAsync(ApiConstants.LEDGER, Map.of(ApiConstants.FROM_INDEX, String.valueOf(version)), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                inFlight.set(false); // try again next tick, same version
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = HttpClientUtil.bodyOf(response);
                    if (response.code() != 200) {
                        return;
                    }
                    LedgerDeltaDto delta = ClientConstants.GSON.fromJson(body, LedgerDeltaDto.class);
                    version = delta.version();
                    if (!delta.entries().isEmpty()) {
                        List<LedgerEntryDto> entries = List.copyOf(delta.entries());
                        Platform.runLater(() -> newEntriesConsumer.accept(entries));
                    }
                } finally {
                    inFlight.set(false); // whatever happened, allow the next tick
                }
            }
        });
    }
}
