package is.dyino.util;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

public class ColorConfig {
    private static final String TAG  = "ColorConfig";
    private static final String FILE = "color.cfg";

    private final Map<String, String> vals = new LinkedHashMap<>();
    private final Context ctx;

    public ColorConfig(Context ctx) { this.ctx = ctx; load(); }

    private void load() {
        File f = new File(ctx.getFilesDir(), FILE);
        try {
            BufferedReader br = f.exists()
                ? new BufferedReader(new FileReader(f))
                : new BufferedReader(new InputStreamReader(ctx.getAssets().open(FILE)));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) vals.put(line.substring(0, eq).trim(), line.substring(eq+1).trim());
            }
            br.close();
        } catch (Exception e) { Log.e(TAG, "load", e); }
    }

    private int c(String key, String fallback) {
        String v = vals.get(key);
        try { return Color.parseColor(v != null ? v : fallback); }
        catch (Exception e) { try { return Color.parseColor(fallback); } catch (Exception ex) { return Color.WHITE; } }
    }

    // ── Backgrounds ──
    public int bgPrimary()        { return c("bg_primary",        "#0D0D14"); }
    public int bgCard()           { return c("bg_card",           "#16161F"); }
    public int bgCard2()          { return c("bg_card2",          "#1E1E2A"); }
    public int bgNav()            { return c("bg_nav",            "#0D0D14"); }
    public int bgHeader()         { return c("bg_header",         "#0D0D14"); }
    public int bgSettingsCard()   { return c("bg_settings_card",  "#16161F"); }

    // ── Accent ──
    public int accent()           { return c("accent",            "#6C63FF"); }
    public int accentDim()        { return c("accent_dim",        "#3D3880"); }

    // ── Text ──
    public int textPrimary()      { return c("text_primary",      "#FFFFFF"); }
    public int textSecondary()    { return c("text_secondary",    "#8888AA"); }
    public int navSelected()      { return c("text_nav_selected", "#FFFFFF"); }
    public int navUnselected()    { return c("text_nav_unselected","#44445A"); }
    public int textSectionTitle() { return c("text_section_title","#FFFFFF"); }
    public int textStationActive(){ return c("text_station_active","#6C63FF"); }
    public int textAboutBrand()   { return c("text_about_brand",  "#6C63FF"); }
    public int textLink()         { return c("text_link",         "#FFFFFF"); }

    // ── Dividers ──
    public int divider()          { return c("divider",           "#22223A"); }
    public int borderCard()       { return c("border_card",       "#22223A"); }

    // ── Stations ──
    public int stationBg()        { return c("station_bg",        "#1E1E2A"); }
    public int stationActiveBg()  { return c("station_bg_active", "#2A1E4A"); }
    public int stationActiveBorder(){ return c("station_border_active","#6C63FF"); }
    public int stationText()      { return c("station_text",      "#FFFFFF"); }
    public int stationTextActive(){ return c("station_text_active","#6C63FF"); }
    public int eqBar()            { return c("station_eq_bar",    "#6C63FF"); }

    // ── Sound Buttons ──
    public int soundBtnBg()         { return c("sound_btn_bg",          "#1A1A26"); }
    public int soundBtnActiveBg()   { return c("sound_btn_active_bg",   "#28265A"); }
    public int soundBtnActiveBorder(){ return c("sound_btn_border_active","#6C63FF"); }
    public int soundBtnText()       { return c("sound_btn_text",        "#FFFFFF"); }
    public int soundWaveColor()     { return c("sound_wave_color",      "#6C63FF"); }

    // ── Controls ──
    public int btnStopBg()        { return c("btn_stop_bg",    "#1E1E2A"); }
    public int btnStopBorder()    { return c("btn_stop_border","#6C63FF"); }
    public int btnStopText()      { return c("btn_stop_text",  "#FFFFFF"); }

    public void saveRaw(String raw) {
        try {
            FileWriter fw = new FileWriter(new File(ctx.getFilesDir(), FILE));
            fw.write(raw); fw.close();
        } catch (Exception e) { Log.e(TAG, "save", e); }
    }

    public String readRaw() {
        File f = new File(ctx.getFilesDir(), FILE);
        try {
            BufferedReader br = f.exists()
                ? new BufferedReader(new FileReader(f))
                : new BufferedReader(new InputStreamReader(ctx.getAssets().open(FILE)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
}
