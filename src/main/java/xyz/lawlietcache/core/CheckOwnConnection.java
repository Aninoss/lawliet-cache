package xyz.lawlietcache.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckOwnConnection {

    private final static Logger LOGGER = LoggerFactory.getLogger(CheckOwnConnection.class);

    public CheckOwnConnection(OkHttpClient client) {
        String url = String.format("http://localhost:%d/api/ping", Integer.parseInt(System.getenv("PORT")));
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", System.getenv("AUTH"))
                .build();
        startScheduler(client, request);
    }

    private void startScheduler(OkHttpClient client, Request request) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                if (code != 200) {
                    onConnectionError();
                }
            } catch (Throwable e) {
                LOGGER.error("Connection error", e);
                onConnectionError();
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    private void onConnectionError() {
        LOGGER.error("Connection error");
        if (Boolean.parseBoolean(System.getenv("STOP_ON_CONNECTION_ERROR"))) {
            System.exit(3);
        }
    }

}
