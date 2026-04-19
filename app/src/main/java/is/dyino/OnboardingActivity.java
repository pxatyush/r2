package is.dyino;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;

/**
 * Onboarding screen — shown only on first launch.
 *
 * System theme detection:
 *   Dark mode  → applies the AMOLED preset (deep black, accent #6C63FF)
 *   Light mode → applies the Day Blue preset (bright background, accent #2979FF)
 *
 * This runs BEFORE setContentView so the ColorConfig is ready and colors
 * are applied programmatically to the root view and all text.
 */
public class OnboardingActivity extends AppCompatActivity {

    // Minimal AMOLED theme JSON — applied on first dark-mode launch
    private static final String AMOLED_JSON =
        "{\"global\":{\"bg_primary\":\"#000000\",\"bg_card\":\"#0A0A0A\",\"bg_card2\":\"#111111\"," +
        "\"accent\":\"#6C63FF\",\"accent_dim\":\"#2A2880\",\"divider\":\"#1A1A1A\"," +
        "\"text_primary\":\"#FFFFFF\",\"text_secondary\":\"#888888\",\"text_section_title\":\"#FFFFFF\"," +
        "\"icon_note_vec_tint\":\"#6C63FF\",\"page_header_text\":\"#FFFFFF\",\"page_header_subtitle_text\":\"#888888\"}," +
        "\"nav\":{\"bg\":\"#000000\",\"label_selected\":\"#FFFFFF\",\"label_unselected\":\"#333333\"}," +
        "\"home\":{\"section_title\":\"#FFFFFF\",\"chip_playing_bg\":\"#2A2880\",\"chip_playing_border\":\"#6C63FF\"," +
        "\"chip_text\":\"#FFFFFF\",\"empty_text\":\"#333333\",\"now_playing_anim\":\"#6C63FF\"," +
        "\"now_playing_card_bg\":\"#111111\",\"now_playing_card_border\":\"#6C63FF\",\"now_playing_icon_tint\":\"#6C63FF\"," +
        "\"visualizer_bg\":\"#000000\",\"visualizer_bar\":\"#6C63FF\"}," +
        "\"radio\":{\"station_bg\":\"#111111\",\"station_bg_active\":\"#2A2880\",\"station_border_active\":\"#6C63FF\"," +
        "\"station_text\":\"#FFFFFF\",\"station_text_active\":\"#6C63FF\",\"station_click_glow\":\"#6C63FF\"," +
        "\"eq_bar\":\"#6C63FF\",\"group_header_bg\":\"#0A0A0A\",\"group_header_border\":\"#1A1A1A\"," +
        "\"group_name_text\":\"#6C63FF\",\"group_name_collapsed_text\":\"#888888\"," +
        "\"group_badge_bg\":\"#2A2880\",\"group_badge_text\":\"#6C63FF\"," +
        "\"station_card_bg\":\"#111111\",\"station_card_border\":\"#1A1A1A\"," +
        "\"search_bg\":\"#111111\",\"search_text\":\"#FFFFFF\",\"search_hint\":\"#888888\"," +
        "\"checkbox_color\":\"#6C63FF\"}," +
        "\"sounds\":{\"btn_bg\":\"#0A0A0A\",\"btn_active_bg\":\"#2A2880\",\"btn_border_active\":\"#6C63FF\"," +
        "\"btn_text\":\"#FFFFFF\",\"wave_color\":\"#6C63FF\",\"stop_all_bg\":\"#111111\"," +
        "\"stop_all_border\":\"#6C63FF\",\"stop_all_text\":\"#FFFFFF\"}," +
        "\"settings\":{\"card_bg\":\"#0A0A0A\",\"card_border\":\"#1A1A1A\",\"label_text\":\"#FFFFFF\"," +
        "\"hint_text\":\"#888888\",\"version_text\":\"#333333\",\"input_bg\":\"#111111\"," +
        "\"input_border\":\"#1A1A1A\",\"input_text\":\"#FFFFFF\",\"btn_bg\":\"#111111\"," +
        "\"btn_border\":\"#6C63FF\",\"btn_text\":\"#FFFFFF\",\"divider\":\"#1A1A1A\"," +
        "\"switch_thumb_on\":\"#6C63FF\",\"switch_track_on\":\"#2A2880\"," +
        "\"switch_thumb_off\":\"#888888\",\"switch_track_off\":\"#1A1A1A\"," +
        "\"made_by_text\":\"#333333\",\"made_by_brand\":\"#6C63FF\"}," +
        "\"notification\":{\"icon_bg\":\"#6C63FF\"}}";

    // Day Blue theme JSON — applied on first light-mode launch
    private static final String DAY_BLUE_JSON =
        "{\"global\":{\"bg_primary\":\"#EEF4FF\",\"bg_card\":\"#FFFFFF\",\"bg_card2\":\"#DCE8FF\"," +
        "\"accent\":\"#2979FF\",\"accent_dim\":\"#82B1FF\",\"divider\":\"#BBCCE8\"," +
        "\"text_primary\":\"#0D1A33\",\"text_secondary\":\"#5577AA\",\"text_section_title\":\"#0D1A33\"," +
        "\"icon_note_vec_tint\":\"#2979FF\",\"page_header_text\":\"#0D1A33\",\"page_header_subtitle_text\":\"#5577AA\"}," +
        "\"nav\":{\"bg\":\"#EEF4FF\",\"label_selected\":\"#0D1A33\",\"label_unselected\":\"#9AADCC\"}," +
        "\"home\":{\"section_title\":\"#0D1A33\",\"chip_playing_bg\":\"#82B1FF\",\"chip_playing_border\":\"#2979FF\"," +
        "\"chip_text\":\"#0D1A33\",\"empty_text\":\"#9AADCC\",\"now_playing_anim\":\"#2979FF\"," +
        "\"now_playing_card_bg\":\"#DCE8FF\",\"now_playing_card_border\":\"#2979FF\",\"now_playing_icon_tint\":\"#2979FF\"," +
        "\"visualizer_bg\":\"#EEF4FF\",\"visualizer_bar\":\"#2979FF\"}," +
        "\"radio\":{\"station_bg\":\"#DCE8FF\",\"station_bg_active\":\"#82B1FF\",\"station_border_active\":\"#2979FF\"," +
        "\"station_text\":\"#0D1A33\",\"station_text_active\":\"#2979FF\",\"station_click_glow\":\"#2979FF\"," +
        "\"eq_bar\":\"#2979FF\",\"group_header_bg\":\"#FFFFFF\",\"group_header_border\":\"#BBCCE8\"," +
        "\"group_name_text\":\"#2979FF\",\"group_name_collapsed_text\":\"#5577AA\"," +
        "\"group_badge_bg\":\"#82B1FF\",\"group_badge_text\":\"#2979FF\"," +
        "\"station_card_bg\":\"#DCE8FF\",\"station_card_border\":\"#BBCCE8\"," +
        "\"search_bg\":\"#DCE8FF\",\"search_text\":\"#0D1A33\",\"search_hint\":\"#5577AA\"," +
        "\"checkbox_color\":\"#2979FF\"}," +
        "\"sounds\":{\"btn_bg\":\"#FFFFFF\",\"btn_active_bg\":\"#82B1FF\",\"btn_border_active\":\"#2979FF\"," +
        "\"btn_text\":\"#0D1A33\",\"wave_color\":\"#2979FF\",\"stop_all_bg\":\"#DCE8FF\"," +
        "\"stop_all_border\":\"#2979FF\",\"stop_all_text\":\"#0D1A33\"}," +
        "\"settings\":{\"card_bg\":\"#FFFFFF\",\"card_border\":\"#BBCCE8\",\"label_text\":\"#0D1A33\"," +
        "\"hint_text\":\"#5577AA\",\"version_text\":\"#9AADCC\",\"input_bg\":\"#DCE8FF\"," +
        "\"input_border\":\"#BBCCE8\",\"input_text\":\"#0D1A33\",\"btn_bg\":\"#DCE8FF\"," +
        "\"btn_border\":\"#2979FF\",\"btn_text\":\"#0D1A33\",\"divider\":\"#BBCCE8\"," +
        "\"switch_thumb_on\":\"#2979FF\",\"switch_track_on\":\"#82B1FF\"," +
        "\"switch_thumb_off\":\"#5577AA\",\"switch_track_off\":\"#BBCCE8\"," +
        "\"made_by_text\":\"#9AADCC\",\"made_by_brand\":\"#2979FF\"}," +
        "\"notification\":{\"icon_bg\":\"#2979FF\"}}";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppPrefs prefs = new AppPrefs(this);

        // ── On first run: detect system dark/light mode and apply default theme ──
        if (prefs.isFirstRun()) {
            boolean isDark = isDarkMode();
            ColorConfig colors = new ColorConfig(this);
            if (isDark) {
                colors.saveRaw(AMOLED_JSON);
                prefs.setActiveThemeName("AMOLED");
            } else {
                colors.saveRaw(DAY_BLUE_JSON);
                prefs.setActiveThemeName("Day Blue");
            }
        } else {
            // Not first run — skip onboarding entirely
            startMain();
            return;
        }

        // Now ColorConfig has the correct theme — apply colors to the window
        ColorConfig colors = new ColorConfig(this);
        int bg      = parseColor(colors.bgPrimary());
        int accent  = parseColor(colors.accent());
        int textP   = parseColor(colors.textPrimary());
        int textS   = parseColor(colors.textSecondary());
        int divider = parseColor(colors.divider());

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setBackgroundColor(bg);

        setContentView(R.layout.activity_onboarding);

        // ── Apply theme colors to all onboarding views ────────────
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) rootView.setBackgroundColor(bg);

        // Find and colour each view (all IDs exist in activity_onboarding.xml)
        colorView(R.id.tvOnboardingAppName,    textP);
        colorView(R.id.tvOnboardingTagline,    textS);
        colorView(R.id.tvOnboardingDot1,       textP);
        colorView(R.id.tvOnboardingDot2,       textP);
        colorView(R.id.tvOnboardingDot3,       textP);
        colorView(R.id.tvOnboardingDot4,       textP);
        colorView(R.id.tvOnboardingStep1,      textP);
        colorView(R.id.tvOnboardingStep2,      textP);
        colorView(R.id.tvOnboardingStep3,      textP);
        colorView(R.id.tvOnboardingStep4,      textP);
        colorView(R.id.tvOnboardingBy,         textS);
        colorAccentBar(R.id.viewOnboardingAccentBar, accent);
        styleGetStartedBtn(R.id.btnGetStarted, bg, accent, textP);

        // Style bullet dots with accent color
        colorView(R.id.dotBullet1, accent);
        colorView(R.id.dotBullet2, accent);
        colorView(R.id.dotBullet3, accent);
        colorView(R.id.dotBullet4, accent);

        // Logo icon tint
        ImageView logo = findViewById(R.id.ivOnboardingLogo);
        if (logo != null) logo.setColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN);

        // Get Started button
        TextView btnStart = findViewById(R.id.btnGetStarted);
        if (btnStart != null) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            gd.setCornerRadius(26 * getResources().getDisplayMetrics().density);
            gd.setColor(accent);
            btnStart.setBackground(gd);
            btnStart.setTextColor(isLight(accent) ? 0xFF111111 : 0xFFFFFFFF);
            btnStart.setOnClickListener(v -> {
                prefs.setFirstRunDone();
                startMain();
            });
        }
    }

    private void startMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private boolean isDarkMode() {
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private int parseColor(int colorInt) { return colorInt; } // ColorConfig already returns int

    private void colorView(int id, int color) {
        View v = findViewById(id);
        if (v instanceof TextView) ((TextView) v).setTextColor(color);
    }

    private void colorAccentBar(int id, int color) {
        View v = findViewById(id);
        if (v != null) v.setBackgroundColor(color);
    }

    private void styleGetStartedBtn(int id, int bg, int accent, int textColor) {
        // Handled above with the direct view reference; this is a no-op alias
    }

    private static boolean isLight(int c) {
        double r=((c>>16)&0xFF)/255.0, g=((c>>8)&0xFF)/255.0, b=(c&0xFF)/255.0;
        return (0.299*r + 0.587*g + 0.114*b) > 0.5;
    }
}