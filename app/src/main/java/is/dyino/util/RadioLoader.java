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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;

public class RadioLoader {

    private static final String TAG          = "RadioLoader";
    private static final String BASE_URL     = "https://de1.api.radio-browser.info/json/stations";
    private static final String BY_COUNTRY   = BASE_URL + "/bycountry/";
    private static final String COUNTRIES_URL= "https://de1.api.radio-browser.info/json/countries";
    private static final int    MAX_STATIONS = 1000;

    // ── Country support ───────────────────────────────────────────

    public static class CountryItem {
        public final String name;
        public final String iso;
        public final int    stationCount;
        public CountryItem(String name, String iso, int stationCount) {
            this.name = name; this.iso = iso; this.stationCount = stationCount;
        }
    }

    public interface CountriesCallback {
        void onLoaded(List<CountryItem> countries);
        void onError();
    }

    /** Fetches all countries from radio-browser; sorted by name; only countries with stations. */
    public static void loadCountries(CountriesCallback callback) {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        exec.execute(() -> {
            try {
                URL url = new URL(COUNTRIES_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "dyino/1.0");
                conn.setRequestProperty("Accept", "application/json");

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONArray arr = new JSONArray(sb.toString());
                    List<CountryItem> items = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String name  = o.optString("name", "").trim();
                        String iso   = o.optString("iso_3166_1", "").trim();
                        int    count = o.optInt("stationcount", 0);
                        if (!name.isEmpty() && count > 0) items.add(new CountryItem(name, iso, count));
                    }
                    Collections.sort(items, (a, b) -> a.name.compareToIgnoreCase(b.name));
                    main.post(() -> callback.onLoaded(items));
                } else {
                    main.post(callback::onError);
                }
            } catch (Exception e) {
                Log.e(TAG, "loadCountries", e);
                main.post(callback::onError);
            }
        });
    }

    // ── Stations ──────────────────────────────────────────────────

    public interface Callback {
        void onLoaded(List<RadioGroup> groups);
    }

    public static void load(Context context, AppPrefs prefs, Callback callback) {
        if (prefs.isRadioCacheValid()) {
            List<RadioGroup> groups = parseJson(prefs.getRadioCacheJson());
            if (!groups.isEmpty()) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onLoaded(groups));
                return;
            }
        }

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

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    String body = sb.toString();
                    JSONArray arr = new JSONArray(body);
                    if (arr.length() > 0) {
                        Log.d(TAG, "Fetched " + arr.length() + " stations from " + endpoint);
                        return body;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "fetchJson failed: " + endpoint, e);
            }
        }
        return null;
    }

    static List<RadioGroup> parseJson(String json) {
        Map<String, List<RadioStation>> map = new LinkedHashMap<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length() && i < MAX_STATIONS; i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optInt("lastcheckok", 1) == 0) continue;
                String name    = o.optString("name", "").trim();
                String url     = o.optString("url_resolved", o.optString("url", "")).trim();
                String favicon = o.optString("favicon", "").trim();
                String tags    = o.optString("tags", "").trim();
                String lang    = o.optString("language", "").trim();
                if (name.isEmpty() || url.isEmpty()) continue;

                String group = "General";
                if (!tags.isEmpty()) {
                    String firstTag = tags.split("[,;]")[0].trim();
                    if (!firstTag.isEmpty()) group = capitalize(firstTag);
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
        for (Map.Entry<String, List<RadioStation>> e : map.entrySet())
            if (!e.getValue().isEmpty()) result.add(new RadioGroup(e.getKey(), e.getValue()));
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
        groups.add(new RadioGroup("Ambient", ambient));

        List<RadioStation> india = new ArrayList<>();
        india.add(new RadioStation("Radio Mirchi 98.3", "https://prclive1.listenon.in/",         "India FM", ""));
        india.add(new RadioStation("Big FM 92.7",       "https://bigfm.out.airtime.pro/bigfm_a", "India FM", ""));
        groups.add(new RadioGroup("India FM", india));
        return groups;
    }
}