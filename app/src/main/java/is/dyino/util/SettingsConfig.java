package is.dyino.util;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;

/**
 * Loads layout/UI config from configs/settings.json.
 * All values have safe fallbacks matching the original hardcoded values.
 */
public class SettingsConfig {
    private static final String TAG   = "SettingsConfig";
    private static final String FILE  = "settings.json";
    private static final String ASSET = "configs/settings.json";

    private JSONObject root;
    private final Context ctx;

    public SettingsConfig(Context ctx) { this.ctx = ctx; load(); }

    private void load() {
        try { root = new JSONObject(readRaw()); }
        catch (Exception e) { Log.e(TAG, "load", e); root = new JSONObject(); }
    }

    private float f(String section, String key, float fallback) {
        try {
            JSONObject sec = root.optJSONObject(section);
            if (sec != null && sec.has(key)) return (float) sec.getDouble(key);
        } catch (Exception ignored) {}
        return fallback;
    }

    private int i(String section, String key, int fallback) {
        try {
            JSONObject sec = root.optJSONObject(section);
            if (sec != null && sec.has(key)) return sec.getInt(key);
        } catch (Exception ignored) {}
        return fallback;
    }

    private boolean b(String section, String key, boolean fallback) {
        try {
            JSONObject sec = root.optJSONObject(section);
            if (sec != null && sec.has(key)) return sec.getBoolean(key);
        } catch (Exception ignored) {}
        return fallback;
    }

    private String s(String section, String key, String fallback) {
        try {
            JSONObject sec = root.optJSONObject(section);
            if (sec != null && sec.has(key)) return sec.getString(key);
        } catch (Exception ignored) {}
        return fallback;
    }

    // ── App ──────────────────────────────────────────────────────
    public String defaultTab()      { return s("app","default_tab","home"); }
    public String navPosition()     { return s("app","nav_position","left"); }

    // ── Nav ──────────────────────────────────────────────────────
    public int   navWidthDp()       { return i("nav","width_dp",52); }
    public int   navItemHeightDp()  { return i("nav","item_height_dp",64); }
    public int   navPaddingBottomDp(){ return i("nav","bottom_padding_dp",28); }
    public float navTextSizeSel()   { return f("nav","text_size_selected_sp",13f); }
    public float navTextSizeUnsel() { return f("nav","text_size_unselected_sp",11f); }

    // ── Sound buttons ────────────────────────────────────────────
    public int   soundBtnHeightDp()    { return i("sound_buttons","height_dp",52); }
    public float soundBtnCornerDp()    { return f("sound_buttons","corner_radius_dp",14f); }
    public int   soundBtnColumns()     { return i("sound_buttons","columns",2); }
    public int   soundBtnGapDp()       { return i("sound_buttons","gap_dp",5); }
    public int   soundBtnMarginDp()    { return i("sound_buttons","margin_dp",3); }
    public float waveAmplitude()       { return f("sound_buttons","wave_amplitude_factor",0.04f); }
    public int   waveSpeedMs()         { return i("sound_buttons","wave_speed_ms",2200); }

    // ── Radio ────────────────────────────────────────────────────
    public int   stationHeightDp()     { return i("radio_stations","item_height_dp",52); }
    public float stationCornerDp()     { return f("radio_stations","corner_radius_dp",12f); }
    public float groupFontSizeSp()     { return f("radio_stations","group_font_size_sp",15f); }
    public float stationFontSizeSp()   { return f("radio_stations","station_font_size_sp",14f); }
    public boolean defaultExpanded()   { return b("radio_stations","default_expanded",false); }
    public int   swipeThresholdDp()    { return i("radio_stations","swipe_threshold_dp",90); }
    public int   swipeDurationMs()     { return i("radio_stations","swipe_duration_ms",400); }

    // ── Home ─────────────────────────────────────────────────────
    public int   favRadioPerRow()      { return i("home","fav_radio_per_row",2); }
    public int   chipHeightDp()        { return i("home","chip_height_dp",36); }
    public float chipCornerDp()        { return f("home","chip_corner_radius_dp",20f); }

    // ── Cards ─────────────────────────────────────────────────────
    public float cardCornerDp()        { return f("cards","corner_radius_dp",12f); }
    public float cardBorderDp()        { return f("cards","border_width_dp",1f); }

    // ── Header ────────────────────────────────────────────────────
    public int   headerIconSizeDp()    { return i("header","icon_size_dp",48); }
    public float headerTitleSp()       { return f("header","title_size_sp",26f); }
    public float headerSubtitleSp()    { return f("header","subtitle_size_sp",13f); }
    public int   headerPaddingTopDp()  { return i("header","padding_top_dp",20); }
    public int   headerPaddingBotDp()  { return i("header","padding_bottom_dp",12); }

    // ── Volume bar ────────────────────────────────────────────────
    public int   volBarHeightDp()      { return i("volume_bar","height_dp",6); }
    public int   volBarDismissMs()     { return i("volume_bar","auto_dismiss_ms",3000); }

    // ── Persist ───────────────────────────────────────────────────
    public void saveRaw(String raw) {
        try {
            new JSONObject(raw);
            FileWriter fw = new FileWriter(new File(ctx.getFilesDir(), FILE));
            fw.write(raw); fw.close();
        } catch (Exception e) { Log.e(TAG, "saveRaw", e); }
    }

    public String readRaw() {
        File f = new File(ctx.getFilesDir(), FILE);
        try {
            BufferedReader br = f.exists()
                ? new BufferedReader(new FileReader(f))
                : new BufferedReader(new InputStreamReader(ctx.getAssets().open(ASSET)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } catch (Exception e) { Log.e(TAG, "readRaw", e); return "{}"; }
    }
}