package is.dyino.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class AppPrefs {
    private static final String PREFS       = "dyino_prefs";
    private static final String HAPTIC      = "haptic";
    private static final String BTN_SND     = "btn_sound";
    private static final String DARK        = "dark_mode";
    private static final String GIF_TAG     = "gif_tag";
    private static final String FAVOURITES  = "fav_stations";   // Set<String> "name||url||group"
    private static final String ARCHIVED    = "arch_stations";  // Set<String>
    private static final String FIRST_RUN   = "first_run";
    private static final String STATION_URLS= "station_urls";   // Set<String> of extra config URLs

    private final SharedPreferences sp;

    public AppPrefs(Context ctx) { sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public boolean isHapticEnabled()           { return sp.getBoolean(HAPTIC,  true); }
    public void    setHapticEnabled(boolean v) { sp.edit().putBoolean(HAPTIC, v).apply(); }

    public boolean isButtonSoundEnabled()          { return sp.getBoolean(BTN_SND, true); }
    public void    setButtonSoundEnabled(boolean v){ sp.edit().putBoolean(BTN_SND, v).apply(); }

    public boolean isDarkMode()           { return sp.getBoolean(DARK, true); }
    public void    setDarkMode(boolean v) { sp.edit().putBoolean(DARK, v).apply(); }

    public String  getGifTag()            { return sp.getString(GIF_TAG, "nature"); }
    public void    setGifTag(String t)    { sp.edit().putString(GIF_TAG, t).apply(); }

    public boolean isFirstRun()           { return sp.getBoolean(FIRST_RUN, true); }
    public void    setFirstRunDone()      { sp.edit().putBoolean(FIRST_RUN, false).apply(); }

    // ── Favourites ──────────────────────────────────────────────
    public Set<String> getFavourites()    { return new HashSet<>(sp.getStringSet(FAVOURITES, new HashSet<>())); }

    public void addFavourite(String key)  {
        Set<String> s = getFavourites(); s.add(key);
        sp.edit().putStringSet(FAVOURITES, s).apply();
    }
    public void removeFavourite(String key) {
        Set<String> s = getFavourites(); s.remove(key);
        sp.edit().putStringSet(FAVOURITES, s).apply();
    }
    public boolean isFavourite(String key){ return getFavourites().contains(key); }

    // ── Archived ─────────────────────────────────────────────────
    public Set<String> getArchived()      { return new HashSet<>(sp.getStringSet(ARCHIVED, new HashSet<>())); }

    public void addArchived(String key)   {
        Set<String> s = getArchived(); s.add(key);
        sp.edit().putStringSet(ARCHIVED, s).apply();
    }
    public void removeArchived(String key){
        Set<String> s = getArchived(); s.remove(key);
        sp.edit().putStringSet(ARCHIVED, s).apply();
    }
    public boolean isArchived(String key) { return getArchived().contains(key); }

    // ── Extra station config URLs ─────────────────────────────────
    public Set<String> getStationUrls()   { return new HashSet<>(sp.getStringSet(STATION_URLS, new HashSet<>())); }

    public void addStationUrl(String url)  {
        Set<String> s = getStationUrls(); s.add(url);
        sp.edit().putStringSet(STATION_URLS, s).apply();
    }
    public void removeStationUrl(String url){
        Set<String> s = getStationUrls(); s.remove(url);
        sp.edit().putStringSet(STATION_URLS, s).apply();
    }

    /** Serialise a station to a single storable string key */
    public static String stationKey(String name, String url, String group){
        return name + "||" + url + "||" + group;
    }
    public static String[] splitKey(String key){ return key.split("\\|\\|", 3); }
}
