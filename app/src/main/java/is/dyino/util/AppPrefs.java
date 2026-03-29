package is.dyino.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class AppPrefs {
    private static final String PREFS     = "dyino_prefs";
    private static final String HAPTIC    = "haptic";
    private static final String BTN_SND   = "btn_sound";
    private static final String DARK      = "dark_mode";
    private static final String GIF       = "show_gif";
    private static final String GIF_TAG   = "gif_tag";
    private static final String ACCENT    = "accent_color";
    private static final String BG        = "bg_color";
    private static final String TEXT_CLR  = "text_color";
    private static final String FONT      = "font_style";

    private final SharedPreferences sp;

    public AppPrefs(Context ctx) { sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public boolean isHapticEnabled()      { return sp.getBoolean(HAPTIC,  true);  }
    public void    setHapticEnabled(boolean v){ sp.edit().putBoolean(HAPTIC, v).apply(); }

    public boolean isButtonSoundEnabled()     { return sp.getBoolean(BTN_SND, true); }
    public void    setButtonSoundEnabled(boolean v){ sp.edit().putBoolean(BTN_SND, v).apply(); }

    public boolean isDarkMode()           { return sp.getBoolean(DARK,    true);  }
    public void    setDarkMode(boolean v) { sp.edit().putBoolean(DARK, v).apply(); }

    public boolean isGifEnabled()         { return sp.getBoolean(GIF,     true);  }
    public void    setGifEnabled(boolean v){ sp.edit().putBoolean(GIF, v).apply(); }

    public String  getGifTag()            { return sp.getString(GIF_TAG,  "nature"); }
    public void    setGifTag(String t)    { sp.edit().putString(GIF_TAG, t).apply();  }

    public int     getAccentColor()       { return sp.getInt(ACCENT, Color.parseColor("#6C63FF")); }
    public void    setAccentColor(int c)  { sp.edit().putInt(ACCENT, c).apply(); }

    public int     getBgColor()           { return sp.getInt(BG,     Color.parseColor("#0D0D14")); }
    public void    setBgColor(int c)      { sp.edit().putInt(BG, c).apply(); }

    public int     getTextColor()         { return sp.getInt(TEXT_CLR, Color.WHITE); }
    public void    setTextColor(int c)    { sp.edit().putInt(TEXT_CLR, c).apply(); }

    public int     getFontStyle()         { return sp.getInt(FONT, 0); }
    public void    setFontStyle(int i)    { sp.edit().putInt(FONT, i).apply(); }
}
