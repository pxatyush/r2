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
    private static final String TAG      = "ColorConfig";
    private static final String FILENAME = "color.cfg";

    private final Map<String, String> values = new LinkedHashMap<>();
    private final Context ctx;

    public ColorConfig(Context ctx) {
        this.ctx = ctx;
        load();
    }

    private void load() {
        // Try internal files dir first (user-edited copy)
        File f = new File(ctx.getFilesDir(), FILENAME);
        try {
            BufferedReader br;
            if (f.exists()) {
                br = new BufferedReader(new FileReader(f));
            } else {
                br = new BufferedReader(new InputStreamReader(ctx.getAssets().open(FILENAME)));
            }
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    values.put(key, val);
                }
            }
            br.close();
        } catch (Exception e) {
            Log.e(TAG, "load failed", e);
        }
    }

    public int get(String key, int fallback) {
        String v = values.get(key);
        if (v == null) return fallback;
        try { return Color.parseColor(v); } catch (Exception e) { return fallback; }
    }

    public int bgPrimary()          { return get("bg_primary",             Color.parseColor("#0D0D14")); }
    public int bgCard()             { return get("bg_card",                Color.parseColor("#16161F")); }
    public int bgCard2()            { return get("bg_card2",               Color.parseColor("#1E1E2A")); }
    public int accent()             { return get("accent",                 Color.parseColor("#6C63FF")); }
    public int textPrimary()        { return get("text_primary",           Color.WHITE);                 }
    public int textSecondary()      { return get("text_secondary",         Color.parseColor("#8888AA")); }
    public int navSelected()        { return get("nav_selected",           Color.WHITE);                 }
    public int navUnselected()      { return get("nav_unselected",         Color.parseColor("#44445A")); }
    public int stationActiveBg()    { return get("station_active_bg",      Color.parseColor("#2A1E4A")); }
    public int stationActiveBorder(){ return get("station_active_border",  Color.parseColor("#6C63FF")); }
    public int stationTextActive()  { return get("station_text_active",    Color.parseColor("#6C63FF")); }
    public int divider()            { return get("divider",                Color.parseColor("#22223A")); }
    public int soundBtnBg()         { return get("sound_btn_bg",           Color.parseColor("#1E1E2A")); }
    public int soundBtnActiveBg()   { return get("sound_btn_active_bg",    Color.parseColor("#2A2A4A")); }
    public int soundBtnActiveBorder(){ return get("sound_btn_active_border",Color.parseColor("#6C63FF"));}

    /** Save an edited copy to internal storage so it persists across restarts */
    public void saveRaw(String raw) {
        try {
            File f = new File(ctx.getFilesDir(), FILENAME);
            FileWriter fw = new FileWriter(f);
            fw.write(raw);
            fw.close();
        } catch (Exception e) { Log.e(TAG, "save failed", e); }
    }

    /** Read raw text (user-editable copy or asset fallback) */
    public String readRaw() {
        File f = new File(ctx.getFilesDir(), FILENAME);
        try {
            BufferedReader br = f.exists()
                    ? new BufferedReader(new FileReader(f))
                    : new BufferedReader(new InputStreamReader(ctx.getAssets().open(FILENAME)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
}
