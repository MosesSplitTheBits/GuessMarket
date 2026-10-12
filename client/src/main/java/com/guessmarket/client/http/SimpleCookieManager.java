package com.guessmarket.client.http;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's cookie jar — the reason the server remembers who we are.
 *
 * On login, the server's response carries "Set-Cookie: JSESSIONID=ABC123".
 * OkHttp hands every response's cookies to saveFromResponse(); before every
 * request it asks loadForRequest() which cookies to send. If we store
 * JSESSIONID and send it back each time, the server finds our session (and
 * our username in it). If we don't, every request looks like a stranger -> 401.
 *
 * Based on SimpleCookieManager from the course http-client / chat examples,
 * minus its bug: it never replaced a cookie it already had (see saveFromResponse).
 *
 * Thread safety: OkHttp runs several calls at once on its own worker threads,
 * and both methods touch the same HashMap — hence synchronized (this) around
 * every access, and a COPY handed out so nobody iterates the live map later.
 */
public class SimpleCookieManager implements CookieJar {

    // host (e.g. "localhost") -> (cookie name -> cookie)
    private final Map<String, Map<String, Cookie>> cookies = new HashMap<>();

    /** OkHttp calls this BEFORE sending each request: which cookies go with it? */
    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        String host = url.host();
        List<Cookie> cookiesPerDomain = Collections.emptyList();
        synchronized (this) {
            if (cookies.containsKey(host)) {
                cookiesPerDomain = new ArrayList<>(cookies.get(host).values());
            }
        }
        return cookiesPerDomain;
    }

    /**
     * OkHttp calls this AFTER each response, with whatever cookies the server set.
     * A newer cookie ALWAYS replaces an older one of the same name: if the server
     * issues a new JSESSIONID (Tomcat redeployed, session expired), keeping the
     * old, dead one would make every following request look logged-out.
     */
    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> responseCookies) {
        String host = url.host();
        synchronized (this) {
            Map<String, Cookie> cookiesMap = cookies.computeIfAbsent(host, key -> new HashMap<>());
            responseCookies.forEach(cookie -> cookiesMap.put(cookie.name(), cookie));
        }
    }
}
