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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CountryLoader {

    private static final String TAG = "CountryLoader";
    private static final String COUNTRIES_URL = "https://de1.api.radio-browser.info/json/countries";

    public static class Country {
        public String name;
        public String iso;
        public int stationCount;

        public Country(String name, String iso, int count) {
            this.name = name;
            this.iso = iso;
            this.stationCount = count;
        }
    }

    public interface Callback {
        void onLoaded(List<Country> countries);
    }

    public static void load(Context context, Callback callback) {
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
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    List<Country> countries = parseCountries(sb.toString());
                    main.post(() -> callback.onLoaded(countries));
                } else {
                    main.post(() -> callback.onLoaded(new ArrayList<>()));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load countries", e);
                main.post(() -> callback.onLoaded(new ArrayList<>()));
            }
        });
    }

    private static List<Country> parseCountries(String json) {
        List<Country> countries = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.optString("name", "");
                String iso = obj.optString("iso_3166_1", "");
                int count = obj.optInt("stationcount", 0);
                if (!name.isEmpty() && !iso.isEmpty()) {
                    countries.add(new Country(name, iso, count));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseCountries failed", e);
        }
        return countries;
    }
}
