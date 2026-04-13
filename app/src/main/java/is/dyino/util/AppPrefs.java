package is.dyino.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppPrefs {

    private static final String PREFS            = "dyino_prefs";
    private static final String HAPTIC           = "haptic";
    private static final String BTN_SND          = "btn_sound";
    private static final String FAVOURITES       = "fav_stations";
    private static final String FAV_SOUNDS       = "fav_sounds";
    private static final String ARCHIVED         = "arch_stations";
    private static final String FIRST_RUN        = "first_run";
    private static final String RADIO_COUNTRY    = "radio_country";
    private static final String RADIO_CACHE_JSON = "radio_cache_json";
    private static final String RADIO_CACHE_TIME = "radio_cache_time";
    private static final String PERSISTENT_PLAY  = "persistent_playing";
    private static final String GROUP_ORDER      = "radio_group_order";
    private static final String HIDDEN_CATS      = "hidden_categories";
    private static final String LAST_PLAYED      = "last_played";
    private static final String ACTIVE_THEME     = "active_theme_name";
    private static final String WAVE_NOTIF       = "wave_notif_enabled";

    private static final int  LAST_PLAYED_MAX = 10;
    private static final long CACHE_TTL_MS    = 7L * 24 * 60 * 60 * 1000;

    public static final String ASSET_COLOR    = "configs/color.json";
    public static final String ASSET_SETTINGS = "configs/settings.json";

    private final SharedPreferences sp;

    public AppPrefs(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Behaviour ─────────────────────────────────────────────────
    public boolean isHapticEnabled()            { return sp.getBoolean(HAPTIC,    true); }
    public void    setHapticEnabled(boolean v)  { sp.edit().putBoolean(HAPTIC, v).apply(); }

    public boolean isButtonSoundEnabled()           { return sp.getBoolean(BTN_SND, true); }
    public void    setButtonSoundEnabled(boolean v) { sp.edit().putBoolean(BTN_SND, v).apply(); }

    public boolean isPersistentPlayingEnabled()           { return sp.getBoolean(PERSISTENT_PLAY, false); }
    public void    setPersistentPlayingEnabled(boolean v) { sp.edit().putBoolean(PERSISTENT_PLAY, v).apply(); }

    /** Whether to animate the wave bitmap in the media notification (can disable for battery saving). */
    public boolean isWaveNotifEnabled()           { return sp.getBoolean(WAVE_NOTIF, true); }
    public void    setWaveNotifEnabled(boolean v) { sp.edit().putBoolean(WAVE_NOTIF, v).apply(); }

    public boolean isFirstRun()      { return sp.getBoolean(FIRST_RUN, true); }
    public void    setFirstRunDone() { sp.edit().putBoolean(FIRST_RUN, false).apply(); }

    // ── Active theme name ─────────────────────────────────────────
    public String getActiveThemeName()         { return sp.getString(ACTIVE_THEME, ""); }
    public void   setActiveThemeName(String n) { sp.edit().putString(ACTIVE_THEME, n).apply(); }

    // ── Radio country ─────────────────────────────────────────────
    public String  getRadioCountry()         { return sp.getString(RADIO_COUNTRY, ""); }
    public void    setRadioCountry(String c) { sp.edit().putString(RADIO_COUNTRY, c.trim()).apply(); }
    public boolean hasRadioCountry()         { return !getRadioCountry().isEmpty(); }

    // ── Radio cache ───────────────────────────────────────────────
    public String  getRadioCacheJson() { return sp.getString(RADIO_CACHE_JSON, ""); }
    public long    getRadioCacheTime() { return sp.getLong(RADIO_CACHE_TIME, 0); }
    public void    saveRadioCache(String json) {
        sp.edit().putString(RADIO_CACHE_JSON, json)
                 .putLong(RADIO_CACHE_TIME, System.currentTimeMillis())
                 .apply();
    }
    public boolean isRadioCacheValid() {
        return !getRadioCacheJson().isEmpty()
            && (System.currentTimeMillis() - getRadioCacheTime()) < CACHE_TTL_MS;
    }

    // ── Group order ───────────────────────────────────────────────
    public List<String> getGroupOrder() {
        String raw = sp.getString(GROUP_ORDER, "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\\|\\|\\|", -1)));
    }
    public void saveGroupOrder(List<String> names) {
        sp.edit().putString(GROUP_ORDER, TextUtils.join("|||", names)).apply();
    }

    // ── Favourites (radio) ────────────────────────────────────────
    public Set<String> getFavourites()       { return new HashSet<>(sp.getStringSet(FAVOURITES, new HashSet<>())); }
    public void        addFavourite(String k){ Set<String> s = getFavourites(); s.add(k);    sp.edit().putStringSet(FAVOURITES, s).apply(); }
    public void        removeFavourite(String k){ Set<String> s = getFavourites(); s.remove(k); sp.edit().putStringSet(FAVOURITES, s).apply(); }
    public boolean     isFavourite(String k) { return getFavourites().contains(k); }

    // ── Favourites (sounds) ───────────────────────────────────────
    public Set<String> getFavSounds()             { return new HashSet<>(sp.getStringSet(FAV_SOUNDS, new HashSet<>())); }
    public void        addFavSound(String fn)     { Set<String> s = getFavSounds(); s.add(fn);    sp.edit().putStringSet(FAV_SOUNDS, s).apply(); }
    public void        removeFavSound(String fn)  { Set<String> s = getFavSounds(); s.remove(fn); sp.edit().putStringSet(FAV_SOUNDS, s).apply(); }
    public boolean     isFavSound(String fn)      { return getFavSounds().contains(fn); }

    // ── Archived ─────────────────────────────────────────────────
    public Set<String> getArchived()       { return new HashSet<>(sp.getStringSet(ARCHIVED, new HashSet<>())); }
    public void        addArchived(String k){ Set<String> s = getArchived(); s.add(k);    sp.edit().putStringSet(ARCHIVED, s).apply(); }
    public void        removeArchived(String k){ Set<String> s = getArchived(); s.remove(k); sp.edit().putStringSet(ARCHIVED, s).apply(); }
    public boolean     isArchived(String k) { return getArchived().contains(k); }

    // ── Hidden categories ─────────────────────────────────────────
    public Set<String> getHiddenCategories()          { return new HashSet<>(sp.getStringSet(HIDDEN_CATS, new HashSet<>())); }
    public void        setHiddenCategories(Set<String> c){ sp.edit().putStringSet(HIDDEN_CATS, c).apply(); }
    public boolean     isCategoryHidden(String name)  { return getHiddenCategories().contains(name); }

    // ── Last played ───────────────────────────────────────────────
    public List<String> getLastPlayed() {
        String raw = sp.getString(LAST_PLAYED, "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\\|\\|\\|", -1)));
    }
    public void addLastPlayed(String key) {
        List<String> list = getLastPlayed();
        list.remove(key);
        list.add(0, key);
        if (list.size() > LAST_PLAYED_MAX) list = list.subList(0, LAST_PLAYED_MAX);
        sp.edit().putString(LAST_PLAYED, TextUtils.join("|||", list)).apply();
    }

    // ── Station key helpers ───────────────────────────────────────
    public static String   stationKey(String n, String u, String g) { return n + "||" + u + "||" + g; }
    public static String[] splitKey(String key) { return key.split("\\|\\|", 3); }
}