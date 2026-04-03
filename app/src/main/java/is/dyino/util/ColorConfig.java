package is.dyino.util;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;

/**
 * Reads theme colors from color.json (assets or internal storage).
 * JSON structure is nested by section (nav, radio, sounds, settings, …).
 */
public class ColorConfig {
    private static final String TAG  = "ColorConfig";
    private static final String FILE = "color.json";

    private JSONObject root;
    private final Context ctx;

    public ColorConfig(Context ctx) {
        this.ctx = ctx;
        load();
    }

    private void load() {
        try {
            String raw = readRaw();
            root = new JSONObject(raw);
        } catch (Exception e) {
            Log.e(TAG, "load", e);
            root = new JSONObject();
        }
    }

    private String c(String section, String key, String fallback) {
        try {
            JSONObject sec = root.optJSONObject(section);
            if (sec != null) {
                String v = sec.optString(key, null);
                if (v != null && !v.isEmpty()) return v;
            }
            return fallback;
        } catch (Exception e) { return fallback; }
    }

    private int color(String section, String key, String fallback) {
        try { return Color.parseColor(c(section, key, fallback)); }
        catch (Exception e) {
            try { return Color.parseColor(fallback); }
            catch (Exception ex) { return Color.WHITE; }
        }
    }

    // ── Global backgrounds ────────────────────────────────────────
    public int bgPrimary()       { return color("global", "bg_primary",  "#0D0D14"); }
    public int bgCard()          { return color("global", "bg_card",     "#16161F"); }
    public int bgCard2()         { return color("global", "bg_card2",    "#1E1E2A"); }
    public int accent()          { return color("global", "accent",      "#6C63FF"); }
    public int accentDim()       { return color("global", "accent_dim",  "#3D3880"); }
    public int divider()         { return color("global", "divider",     "#22223A"); }

    // ── Text (global) ────────────────────────────────────────────
    public int textPrimary()     { return color("global", "text_primary",   "#FFFFFF"); }
    public int textSecondary()   { return color("global", "text_secondary",  "#8888AA"); }
    public int textSectionTitle(){ return color("global", "text_section_title","#FFFFFF"); }

    // ── Navigation bar ────────────────────────────────────────────
    public int bgNav()           { return color("nav", "bg",        "#0D0D14"); }
    public int navSelected()     { return color("nav", "selected",  "#FFFFFF"); }
    public int navUnselected()   { return color("nav", "unselected","#44445A"); }

    // ── Home screen ───────────────────────────────────────────────
    public int homeSectionTitle(){ return color("home", "section_title","#FFFFFF"); }
    public int homeCardBg()      { return color("home", "card_bg",     "#16161F"); }

    // ── Radio page ────────────────────────────────────────────────
    public int stationBg()            { return color("radio", "station_bg",          "#1E1E2A"); }
    public int stationActiveBg()      { return color("radio", "station_bg_active",   "#2A1E4A"); }
    public int stationActiveBorder()  { return color("radio", "station_border_active","#6C63FF"); }
    public int stationText()          { return color("radio", "station_text",         "#FFFFFF"); }
    public int stationTextActive()    { return color("radio", "station_text_active",  "#6C63FF"); }
    public int eqBar()                { return color("radio", "eq_bar",              "#6C63FF"); }
    public int textStationActive()    { return color("radio", "station_text_active",  "#6C63FF"); }
    public int textAboutBrand()       { return color("global", "accent",             "#6C63FF"); }
    public int textLink()             { return color("global", "text_primary",       "#FFFFFF"); }

    // ── Sounds page ───────────────────────────────────────────────
    public int soundBtnBg()           { return color("sounds", "btn_bg",           "#1A1A26"); }
    public int soundBtnActiveBg()     { return color("sounds", "btn_active_bg",    "#28265A"); }
    public int soundBtnActiveBorder() { return color("sounds", "btn_border_active","#6C63FF"); }
    public int soundBtnText()         { return color("sounds", "btn_text",         "#FFFFFF"); }
    public int soundWaveColor()       { return color("sounds", "wave_color",       "#6C63FF"); }

    // ── Controls / buttons ────────────────────────────────────────
    public int btnStopBg()       { return color("controls", "stop_bg",    "#1E1E2A"); }
    public int btnStopBorder()   { return color("controls", "stop_border","#6C63FF"); }
    public int btnStopText()     { return color("controls", "stop_text",  "#FFFFFF"); }

    // ── Settings page ─────────────────────────────────────────────
    public int bgSettingsCard()      { return color("settings", "card_bg",    "#16161F"); }
    public int textSettingsLabel()   { return color("settings", "label_text", "#FFFFFF"); }
    public int textSettingsHint()    { return color("settings", "hint_text",  "#8888AA"); }
    public int textSettingsVersion() { return color("settings", "version_text","#44445A"); }

    // ── Persist / read raw ────────────────────────────────────────
    public void saveRaw(String raw) {
        try {
            new JSONObject(raw); // validate JSON
            FileWriter fw = new FileWriter(new File(ctx.getFilesDir(), FILE));
            fw.write(raw); fw.close();
        } catch (Exception e) { Log.e(TAG, "saveRaw", e); }
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
        } catch (Exception e) {
            Log.e(TAG, "readRaw", e);
            return getDefaultJson();
        }
    }

    public static String getDefaultJson() {
        return "{\n" +
            "  \"global\": {\n" +
            "    \"bg_primary\": \"#0D0D14\",\n" +
            "    \"bg_card\": \"#16161F\",\n" +
            "    \"bg_card2\": \"#1E1E2A\",\n" +
            "    \"accent\": \"#6C63FF\",\n" +
            "    \"accent_dim\": \"#3D3880\",\n" +
            "    \"divider\": \"#22223A\",\n" +
            "    \"text_primary\": \"#FFFFFF\",\n" +
            "    \"text_secondary\": \"#8888AA\",\n" +
            "    \"text_section_title\": \"#FFFFFF\"\n" +
            "  },\n" +
            "  \"nav\": {\n" +
            "    \"bg\": \"#0D0D14\",\n" +
            "    \"selected\": \"#FFFFFF\",\n" +
            "    \"unselected\": \"#44445A\"\n" +
            "  },\n" +
            "  \"home\": {\n" +
            "    \"section_title\": \"#FFFFFF\",\n" +
            "    \"card_bg\": \"#16161F\"\n" +
            "  },\n" +
            "  \"radio\": {\n" +
            "    \"station_bg\": \"#1E1E2A\",\n" +
            "    \"station_bg_active\": \"#2A1E4A\",\n" +
            "    \"station_border_active\": \"#6C63FF\",\n" +
            "    \"station_text\": \"#FFFFFF\",\n" +
            "    \"station_text_active\": \"#6C63FF\",\n" +
            "    \"eq_bar\": \"#6C63FF\"\n" +
            "  },\n" +
            "  \"sounds\": {\n" +
            "    \"btn_bg\": \"#1A1A26\",\n" +
            "    \"btn_active_bg\": \"#28265A\",\n" +
            "    \"btn_border_active\": \"#6C63FF\",\n" +
            "    \"btn_text\": \"#FFFFFF\",\n" +
            "    \"wave_color\": \"#6C63FF\"\n" +
            "  },\n" +
            "  \"controls\": {\n" +
            "    \"stop_bg\": \"#1E1E2A\",\n" +
            "    \"stop_border\": \"#6C63FF\",\n" +
            "    \"stop_text\": \"#FFFFFF\"\n" +
            "  },\n" +
            "  \"settings\": {\n" +
            "    \"card_bg\": \"#16161F\",\n" +
            "    \"label_text\": \"#FFFFFF\",\n" +
            "    \"hint_text\": \"#8888AA\",\n" +
            "    \"version_text\": \"#44445A\"\n" +
            "  }\n" +
            "}";
    }
}