package com.guessmarket.client.http;

import com.guessmarket.client.ClientConstants;
import com.guessmarket.dto.ApiConstants;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * The ONE place the client talks HTTP — same role as HttpClientUtil in the
 * course chat example. Every call is asynchronous (enqueue): it returns
 * immediately, and OkHttp runs the callback later on one of ITS OWN worker
 * threads — never on the JavaFX thread. So a callback that touches the UI
 * must hop back with Platform.runLater(...).
 */
public final class HttpClientUtil {

    // One shared client for the whole app: it owns the thread pool, the
    // connection pool, and — crucially — the cookie jar holding JSESSIONID.
    // A second OkHttpClient would have its own empty jar = a logged-out user.
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .cookieJar(new SimpleCookieManager())
            .followRedirects(false)
            .build();

    private HttpClientUtil() {
    }

    /** GET path?params... */
    public static void getAsync(String path, Map<String, String> params, Callback callback) {
        Request request = new Request.Builder().url(buildUrl(path, params)).get().build();
        HTTP_CLIENT.newCall(request).enqueue(callback);
    }

    /**
     * POST path?params... with an empty body. Parameters travel in the query
     * string (properly URL-encoded by HttpUrl — event names contain spaces and
     * '?'); the servlets read them with getParameter either way.
     */
    public static void postAsync(String path, Map<String, String> params, Callback callback) {
        Request request = new Request.Builder()
                .url(buildUrl(path, params))
                .post(RequestBody.create(new byte[0]))
                .build();
        HTTP_CLIENT.newCall(request).enqueue(callback);
    }

    /** POST /upload as multipart/form-data, the file in a part named "file" (cf. course FileUpload). */
    public static void uploadAsync(File file, Callback callback) {
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(ApiConstants.FILE, file.getName(),
                        RequestBody.create(file, MediaType.parse("application/xml")))
                .build();
        Request request = new Request.Builder()
                .url(buildUrl(ApiConstants.UPLOAD, Map.of()))
                .post(body)
                .build();
        HTTP_CLIENT.newCall(request).enqueue(callback);
    }

    /** Called from ClientApp.stop(): lets the JVM exit instead of waiting on idle OkHttp threads. */
    public static void shutdown() {
        HTTP_CLIENT.dispatcher().executorService().shutdown();
        HTTP_CLIENT.connectionPool().evictAll();
    }

    private static HttpUrl buildUrl(String path, Map<String, String> params) {
        HttpUrl.Builder builder = HttpUrl.parse(ClientConstants.BASE_URL + path).newBuilder();
        params.forEach(builder::addQueryParameter);
        return builder.build();
    }

    /** The response body as text ("" if none). A body can only be read ONCE. */
    public static String bodyOf(Response response) throws IOException {
        return response.body() == null ? "" : response.body().string();
    }
}
