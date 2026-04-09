package is.dyino.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppPrefs {
    private static final String PREFS            = "dyino_prefs";
    private static final String HAPTIC           = "haptic";
    private static final String BTN_SND          = "btn_sound";
    private static final String DARK             = "dark_mode";
    private static final String GIF_TAG          = "gif_tag";
    private static final String FAVOURITES       = "fav_stations";
    private static final String FAV_SOUNDS       = "fav_sounds";
    private static final String ARCHIVED         = "arch_stations";
    private static final String FIRST_RUN        = "first_run";
    private static final String STATION_URLS     = "station_urls";
    private static final String RADIO_COUNTRY    = "radio_country";
    private static final String RADIO_CACHE_JSON = "radio_cache_json";
    private static final String RADIO_CACHE_TIME = "radio_cache_time";
    private static final String PERSISTENT_PLAY  = "persistent_playing";
    private static final String GROUP_ORDER      = "radio_group_order";
    private static final String HIDDEN_CATS      = "hidden_categories";
    private static final String LAST_PLAYED      = "last_played";     // |||‑separated station keys
    private static final int    LAST_PLAYED_MAX  = 10;
    private static final long   CACHE_TTL_MS     = 7L * 24 * 60 * 60 * 1000;

    public static final String ASSET_COLOR    = "configs/color.json";
    public static final String ASSET_SETTINGS = "configs/settings.json";

    private final SharedPreferences sp;

    public AppPrefs(Context ctx) { sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public boolean isHapticEnabled()           { return sp.getBoolean(HAPTIC,   true); }
    public void    setHapticEnabled(boolean v) { sp.edit().putBoolean(HAPTIC, v).apply(); }

    public boolean isButtonSoundEnabled()           { return sp.getBoolean(BTN_SND, true); }
    public void    setButtonSoundEnabled(boolean v) { sp.edit().putBoolean(BTN_SND, v).apply(); }

    public boolean isDarkMode()           { return sp.getBoolean(DARK, true); }
    public void    setDarkMode(boolean v) { sp.edit().putBoolean(DARK, v).apply(); }

    public String  getGifTag()         { return sp.getString(GIF_TAG, "nature"); }
    public void    setGifTag(String t) { sp.edit().putString(GIF_TAG, t).apply(); }

    public boolean isFirstRun()      { return sp.getBoolean(FIRST_RUN, true); }
    public void    setFirstRunDone() { sp.edit().putBoolean(FIRST_RUN, false).apply(); }

    public boolean isPersistentPlayingEnabled()           { return sp.getBoolean(PERSISTENT_PLAY, false); }
    public void    setPersistentPlayingEnabled(boolean v) { sp.edit().putBoolean(PERSISTENT_PLAY, v).apply(); }

    // ── Country ──────────────────────────────────────────────────
    public String  getRadioCountry()         { return sp.getString(RADIO_COUNTRY, ""); }
    public void    setRadioCountry(String c) { sp.edit().putString(RADIO_COUNTRY, c.trim()).apply(); }
    public boolean hasRadioCountry()         { return !getRadioCountry().isEmpty(); }

    // ── Radio cache ───────────────────────────────────────────────
    public String  getRadioCacheJson()         { return sp.getString(RADIO_CACHE_JSON, ""); }
    public long    getRadioCacheTime()         { return sp.getLong(RADIO_CACHE_TIME, 0); }
    public void    saveRadioCache(String json) {
        sp.edit().putString(RADIO_CACHE_JSON, json)
                 .putLong(RADIO_CACHE_TIME, System.currentTimeMillis()).apply();
    }
    public boolean isRadioCacheValid() {
        return !getRadioCacheJson().isEmpty()
            && (System.currentTimeMillis() - getRadioCacheTime()) < CACHE_TTL_MS;
    }

    // ── Radio group order ─────────────────────────────────────────
    public List<String> getGroupOrder() {
        String raw = sp.getString(GROUP_ORDER, "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\\|\\|\\|", -1)));
    }
    public void saveGroupOrder(List<String> names) {
        sp.edit().putString(GROUP_ORDER, android.text.TextUtils.join("|||", names)).apply();
    }

    // ── Favourites (radio) ────────────────────────────────────────
    public Set<String> getFavourites()      { return new HashSet<>(sp.getStringSet(FAVOURITES, new HashSet<>())); }
    public void addFavourite(String key)    { Set<String> s = getFavourites(); s.add(key);    sp.edit().putStringSet(FAVOURITES, s).apply(); }
    public void removeFavourite(String key) { Set<String> s = getFavourites(); s.remove(key); sp.edit().putStringSet(FAVOURITES, s).apply(); }
    public boolean isFavourite(String key)  { return getFavourites().contains(key); }

    // ── Favourites (sounds) ───────────────────────────────────────
    public Set<String> getFavSounds()            { return new HashSet<>(sp.getStringSet(FAV_SOUNDS, new HashSet<>())); }
    public void        addFavSound(String fn)    { Set<String> s = getFavSounds(); s.add(fn);    sp.edit().putStringSet(FAV_SOUNDS, s).apply(); }
    public void        removeFavSound(String fn) { Set<String> s = getFavSounds(); s.remove(fn); sp.edit().putStringSet(FAV_SOUNDS, s).apply(); }
    public boolean     isFavSound(String fn)     { return getFavSounds().contains(fn); }

    // ── Archived ─────────────────────────────────────────────────
    public Set<String> getArchived()       { return new HashSet<>(sp.getStringSet(ARCHIVED, new HashSet<>())); }
    public void addArchived(String key)    { Set<String> s = getArchived(); s.add(key);    sp.edit().putStringSet(ARCHIVED, s).apply(); }
    public void removeArchived(String key) { Set<String> s = getArchived(); s.remove(key); sp.edit().putStringSet(ARCHIVED, s).apply(); }
    public boolean isArchived(String key)  { return getArchived().contains(key); }

    // ── Extra station URLs ────────────────────────────────────────
    public Set<String> getStationUrls()      { return new HashSet<>(sp.getStringSet(STATION_URLS, new HashSet<>())); }
    public void addStationUrl(String url)    { Set<String> s = getStationUrls(); s.add(url);    sp.edit().putStringSet(STATION_URLS, s).apply(); }
    public void removeStationUrl(String url) { Set<String> s = getStationUrls(); s.remove(url); sp.edit().putStringSet(STATION_URLS, s).apply(); }

    // ── Last Played (ordered, max 10) ─────────────────────────────
    /** Returns station keys in recency order (most recent first). */
    public List<String> getLastPlayed() {
        String raw = sp.getString(LAST_PLAYED, "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\\|\\|\\|", -1)));
    }

    /** Prepend key to last-played list, dedup, truncate to LAST_PLAYED_MAX. */
    public void addLastPlayed(String key) {
        List<String> list = getLastPlayed();
        list.remove(key);          // remove duplicate if exists
        list.add(0, key);          // prepend (most recent first)
        if (list.size() > LAST_PLAYED_MAX) list = list.subList(0, LAST_PLAYED_MAX);
        sp.edit().putString(LAST_PLAYED, android.text.TextUtils.join("|||", list)).apply();
    }

    public void clearLastPlayed() { sp.edit().remove(LAST_PLAYED).apply(); }

    // ── Hidden categories ─────────────────────────────────────────
    public Set<String> getHiddenCategories()          { return new HashSet<>(sp.getStringSet(HIDDEN_CATS, new HashSet<>())); }
    public void setHiddenCategories(Set<String> cats) { sp.edit().putStringSet(HIDDEN_CATS, cats).apply(); }
    public boolean isCategoryHidden(String name)      { return getHiddenCategories().contains(name); }

    // ── Key helpers ───────────────────────────────────────────────
    public static String   stationKey(String n, String u, String g) { return n + "||" + u + "||" + g; }
    public static String[] splitKey(String key) { return key.split("\\|\\|", 3); }
}
