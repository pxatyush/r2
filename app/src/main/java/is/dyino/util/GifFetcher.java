package is.dyino.util;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GifFetcher {

    // Free Tenor API key (demo key - works for low volume)
    private static final String TENOR_API_KEY = "AIzaSyAyimkuYQYF_FXVALexPubfQgzFqpqqDFg";
    private static final String TENOR_BASE = "https://tenor.googleapis.com/v2/search";

    public interface GifCallback {
        void onGifUrl(String gifUrl);
        void onError(String error);
    }

    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void fetchRandomGif(String tag, GifCallback callback) {
        String url = TENOR_BASE + "?q=" + tag
                + "&key=" + TENOR_API_KEY
                + "&limit=8&media_filter=gif&contentfilter=high";

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONArray results = json.getJSONArray("results");

                    List<String> urls = new ArrayList<>();
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject result = results.getJSONObject(i);
                        JSONObject media = result
                                .getJSONArray("media_formats")
                                .optJSONObject(0);
                        if (media == null) {
                            // v2 API: media_formats is an object
                            media = result.getJSONObject("media_formats");
                        }
                        // Try "gif" format first, fall back to "mediumgif"
                        String gifUrl = null;
                        if (media.has("gif")) {
                            gifUrl = media.getJSONObject("gif").getString("url");
                        } else if (media.has("mediumgif")) {
                            gifUrl = media.getJSONObject("mediumgif").getString("url");
                        } else if (media.has("nanogif")) {
                            gifUrl = media.getJSONObject("nanogif").getString("url");
                        }
                        if (gifUrl != null) urls.add(gifUrl);
                    }

                    if (!urls.isEmpty()) {
                        String selected = urls.get((int) (Math.random() * urls.size()));
                        mainHandler.post(() -> callback.onGifUrl(selected));
                    } else {
                        mainHandler.post(() -> callback.onError("No GIFs found"));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError(e.getMessage()));
                }
            }
        });
    }
}
