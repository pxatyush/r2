package is.dyino.util;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {
    private static final String PREFS_NAME = "dyino_prefs";
    private SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isHapticEnabled() { return prefs.getBoolean("haptic", true); }
    public void setHapticEnabled(boolean v) { prefs.edit().putBoolean("haptic", v).apply(); }

    public boolean isButtonSoundEnabled() { return prefs.getBoolean("btn_sound", true); }
    public void setButtonSoundEnabled(boolean v) { prefs.edit().putBoolean("btn_sound", v).apply(); }

    public boolean isDarkMode() { return prefs.getBoolean("dark_mode", true); }
    public void setDarkMode(boolean v) { prefs.edit().putBoolean("dark_mode", v).apply(); }

    public boolean isGifEnabled() { return prefs.getBoolean("gif_enabled", true); }
    public void setGifEnabled(boolean v) { prefs.edit().putBoolean("gif_enabled", v).apply(); }

    public String getGifTag() { return prefs.getString("gif_tag", "nature"); }
    public void setGifTag(String v) { prefs.edit().putString("gif_tag", v).apply(); }

    public int getAccentColor() { return prefs.getInt("accent_color", 0xFF6C63FF); }
    public void setAccentColor(int v) { prefs.edit().putInt("accent_color", v).apply(); }

    public int getBgColor() { return prefs.getInt("bg_color", 0xFF0D0D14); }
    public void setBgColor(int v) { prefs.edit().putInt("bg_color", v).apply(); }

    public int getTextColor() { return prefs.getInt("text_color", 0xFFFFFFFF); }
    public void setTextColor(int v) { prefs.edit().putInt("text_color", v).apply(); }

    public int getFontIndex() { return prefs.getInt("font_index", 0); }
    public void setFontIndex(int v) { prefs.edit().putInt("font_index", v).apply(); }
}
