package is.dyino.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;

public class RadioLoader {

    private static final String TAG           = "RadioLoader";
    private static final String BASE_URL      = "https://de1.api.radio-browser.info/json/stations";
    private static final String BY_COUNTRY    = BASE_URL + "/bycountry/";
    private static final int    MAX_STATIONS  = 1000;

    public interface Callback {
        void onLoaded(List<RadioGroup> groups);
    }

    /**
     * Load radio stations. Uses cache if valid (< 1 week old).
     * If cache is stale or missing, fetches from network.
     * Calls callback on main thread.
     */
    public static void load(Context context, AppPrefs prefs, Callback callback) {
        if (prefs.isRadioCacheValid()) {
            // Serve from cache
            List<RadioGroup> groups = parseJson(prefs.getRadioCacheJson());
            if (!groups.isEmpty()) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onLoaded(groups));
                return;
            }
        }

        // Fetch from network in background
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        exec.execute(() -> {
            String country = prefs.getRadioCountry();
            String json = fetchJson(country);

            if (json != null && !json.isEmpty()) {
                prefs.saveRadioCache(json);
                List<RadioGroup> groups = parseJson(json);
                main.post(() -> callback.onLoaded(groups.isEmpty() ? getDefaults() : groups));
            } else {
                // Fall back to cached (even if stale) or defaults
                String cached = prefs.getRadioCacheJson();
                if (!cached.isEmpty()) {
                    List<RadioGroup> groups = parseJson(cached);
                    main.post(() -> callback.onLoaded(groups));
                } else {
                    main.post(() -> callback.onLoaded(getDefaults()));
                }
            }
        });
    }

    private static String fetchJson(String country) {
        String[] endpoints;
        if (country != null && !country.isEmpty()) {
            endpoints = new String[]{
                BY_COUNTRY + country.trim(),
                BASE_URL + "?limit=" + MAX_STATIONS + "&order=clickcount&reverse=true"
            };
        } else {
            endpoints = new String[]{
                BASE_URL + "?limit=" + MAX_STATIONS + "&order=clickcount&reverse=true"
            };
        }

        for (String endpoint : endpoints) {
            try {
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "dyino/1.0");
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    String body = sb.toString();
                    // Validate it has results
                    JSONArray arr = new JSONArray(body);
                    if (arr.length() > 0) {
                        Log.d(TAG, "Fetched " + arr.length() + " stations from " + endpoint);
                        return body;
                    }
                    // Country returned 0, try next endpoint (general)
                }
            } catch (Exception e) {
                Log.e(TAG, "fetchJson failed: " + endpoint, e);
            }
        }
        return null;
    }

    /**
     * Parse radio-browser.info JSON array into RadioGroups by language/tag.
     */
    static List<RadioGroup> parseJson(String json) {
        Map<String, List<RadioStation>> map = new LinkedHashMap<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length() && i < MAX_STATIONS; i++) {
                JSONObject o = arr.getJSONObject(i);

                // Skip dead stations
                if (o.optInt("lastcheckok", 1) == 0) continue;

                String name    = o.optString("name", "").trim();
                String url     = o.optString("url_resolved", o.optString("url", "")).trim();
                String favicon = o.optString("favicon", "").trim();
                String tags    = o.optString("tags", "").trim();
                String lang    = o.optString("language", "").trim();

                if (name.isEmpty() || url.isEmpty()) continue;

                // Group by primary tag, then language, then "General"
                String group = "General";
                if (!tags.isEmpty()) {
                    String firstTag = tags.split("[,;]")[0].trim();
                    if (!firstTag.isEmpty()) {
                        group = capitalize(firstTag);
                    }
                } else if (!lang.isEmpty()) {
                    group = capitalize(lang.split("[,;]")[0].trim());
                }

                map.computeIfAbsent(group, k -> new ArrayList<>())
                   .add(new RadioStation(name, url, group, favicon));
            }
        } catch (Exception e) {
            Log.e(TAG, "parseJson", e);
        }

        List<RadioGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<RadioStation>> e : map.entrySet()) {
            if (!e.getValue().isEmpty()) result.add(new RadioGroup(e.getKey(), e.getValue()));
        }
        return result;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    public static List<RadioGroup> getDefaults() {
        List<RadioGroup> groups = new ArrayList<>();
        List<RadioStation> ambient = new ArrayList<>();
        ambient.add(new RadioStation("Breathe.fm",       "https://streams.calmradio.com/api/48/128/stream",  "Ambient", ""));
        ambient.add(new RadioStation("Theta Chill Radio","https://streams.calmradio.com/api/7/128/stream",   "Ambient", ""));
        ambient.add(new RadioStation("Delta Sleep Radio","https://listen.openstream.co/4380/audio",           "Ambient", ""));
        ambient.add(new RadioStation("Zion Chillout",    "https://listen.openstream.co/2148/audio",           "Ambient", ""));
        groups.add(new RadioGroup("Ambient", ambient));

        List<RadioStation> india = new ArrayList<>();
        india.add(new RadioStation("Radio Mirchi 98.3", "https://prclive1.listenon.in/",             "India FM", ""));
        india.add(new RadioStation("Big FM 92.7",       "https://bigfm.out.airtime.pro/bigfm_a",     "India FM", ""));
        india.add(new RadioStation("Red FM 93.5",       "https://redfm.out.airtime.pro/redfm_a",     "India FM", ""));
        groups.add(new RadioGroup("India FM", india));
        return groups;
    }
}