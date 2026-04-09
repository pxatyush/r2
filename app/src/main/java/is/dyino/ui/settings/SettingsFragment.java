package is.dyino.ui.settings;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import is.dyino.R;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SettingsFragment extends Fragment {

    private AppPrefs    prefs;
    private ColorConfig colors;

    public interface OnSettingsChanged {
        void onThemeChanged();
        void onButtonSoundChanged(boolean enabled);
    }
    private OnSettingsChanged listener;
    public void setListener(OnSettingsChanged l) { this.listener = l; }

    // ── Views ─────────────────────────────────────────────────────
    private SwitchCompat swHaptic, swBtnSound, swPersistent;
    private EditText     etColorCfg, etStationUrl, etCountry;
    private TextView     btnSaveColor, btnFetch, tvFetchStatus, btnSaveCountry;
    private TextView     tvCountryNote, tvVersion, tvMadeBy;
    private LinearLayout cardToggles, cardTheme, cardStations, cardAdvanced;
    private LinearLayout themePresetsContainer;
    private View         dividerSettings;
    private TextView     tvSettingsTitle;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_settings, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs  = new AppPrefs(requireContext());
        colors = new ColorConfig(requireContext());

        tvSettingsTitle      = view.findViewById(R.id.tvSettingsTitle);
        dividerSettings      = view.findViewById(R.id.settingsDivider);
        cardToggles          = view.findViewById(R.id.cardToggles);
        cardTheme            = view.findViewById(R.id.cardTheme);
        cardStations         = view.findViewById(R.id.cardStations);
        cardAdvanced         = view.findViewById(R.id.cardAdvanced);
        themePresetsContainer= view.findViewById(R.id.themePresetsContainer);
        swHaptic             = view.findViewById(R.id.switchHaptic);
        swBtnSound           = view.findViewById(R.id.switchButtonSound);
        swPersistent         = view.findViewById(R.id.switchPersistent);
        etColorCfg           = view.findViewById(R.id.etColorCfg);
        btnSaveColor         = view.findViewById(R.id.btnSaveColors);
        etCountry            = view.findViewById(R.id.etCountry);
        btnSaveCountry       = view.findViewById(R.id.btnSaveCountry);
        tvCountryNote        = view.findViewById(R.id.tvCountryNote);
        etStationUrl         = view.findViewById(R.id.etStationUrl);
        btnFetch             = view.findViewById(R.id.btnFetchStations);
        tvFetchStatus        = view.findViewById(R.id.tvFetchStatus);
        tvMadeBy             = view.findViewById(R.id.tvMadeBy);
        tvVersion            = view.findViewById(R.id.tvVersion);

        // ── Populate values ──
        swHaptic.setChecked(prefs.isHapticEnabled());
        swBtnSound.setChecked(prefs.isButtonSoundEnabled());
        swPersistent.setChecked(prefs.isPersistentPlayingEnabled());
        if (etColorCfg != null) etColorCfg.setText(colors.readRaw());
        if (etCountry  != null) etCountry.setText(prefs.getRadioCountry());

        // ── Listeners ──
        swHaptic.setOnCheckedChangeListener((b, v) -> prefs.setHapticEnabled(v));
        swBtnSound.setOnCheckedChangeListener((b, v) -> {
            prefs.setButtonSoundEnabled(v);
            if (listener != null) listener.onButtonSoundChanged(v);
        });
        swPersistent.setOnCheckedChangeListener((b, v) -> prefs.setPersistentPlayingEnabled(v));

        if (btnSaveColor != null) {
            btnSaveColor.setOnClickListener(v -> {
                if (etColorCfg == null) return;
                colors.saveRaw(etColorCfg.getText().toString());
                if (listener != null) listener.onThemeChanged();
                Toast.makeText(requireContext(), "Theme saved", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnSaveCountry != null) {
            btnSaveCountry.setOnClickListener(v -> {
                if (etCountry == null) return;
                String c = etCountry.getText().toString().trim();
                prefs.setRadioCountry(c);
                prefs.saveRadioCache("");
                Toast.makeText(requireContext(),
                        "Set to \"" + (c.isEmpty() ? "Global" : c) + "\". Go to Radio tab.",
                        Toast.LENGTH_LONG).show();
            });
        }

        if (btnFetch != null) {
            btnFetch.setOnClickListener(v -> {
                if (etStationUrl == null) return;
                String url = etStationUrl.getText().toString().trim();
                if (url.isEmpty()) { if (tvFetchStatus != null) tvFetchStatus.setText("Enter a URL first"); return; }
                if (tvFetchStatus != null) tvFetchStatus.setText("Fetching…");
                fetchAndMerge(url);
            });
        }

        buildThemePresets();
        applyTheme(view);
    }

    // ═══════════════════════════════════════════════════════════════
    // THEME PRESETS
    // ═══════════════════════════════════════════════════════════════

    /** Built-in theme presets — no asset files required. */
    private static final String[][] BUILTIN_THEMES = {
        { "Dark",  "{\n  \"global\":{\"bg_primary\":\"#0D0D14\",\"bg_card\":\"#16161F\",\"bg_card2\":\"#1E1E2A\",\"accent\":\"#6C63FF\",\"accent_dim\":\"#3D3880\",\"divider\":\"#22223A\",\"text_primary\":\"#FFFFFF\",\"text_secondary\":\"#8888AA\",\"text_section_title\":\"#FFFFFF\",\"icon_note_color\":\"#6C63FF\"},\n  \"nav\":{\"bg\":\"#0D0D14\",\"selected\":\"#FFFFFF\",\"unselected\":\"#44445A\",\"label_selected\":\"#FFFFFF\",\"label_unselected\":\"#44445A\"},\n  \"home\":{\"section_title\":\"#FFFFFF\",\"card_bg\":\"#16161F\",\"chip_playing_bg\":\"#28265A\",\"chip_playing_border\":\"#6C63FF\",\"chip_text\":\"#FFFFFF\",\"empty_text\":\"#44445A\",\"page_title\":\"#FFFFFF\",\"now_playing_anim\":\"#6C63FF\"},\n  \"radio\":{\"station_bg\":\"#1E1E2A\",\"station_bg_active\":\"#2A1E4A\",\"station_border_active\":\"#6C63FF\",\"station_text\":\"#FFFFFF\",\"station_text_active\":\"#6C63FF\",\"station_click_glow\":\"#6C63FF\",\"eq_bar\":\"#6C63FF\",\"group_name_text\":\"#6C63FF\",\"group_name_collapsed_text\":\"#8888AA\",\"search_bg\":\"#1E1E2A\",\"search_text\":\"#FFFFFF\",\"search_hint\":\"#8888AA\",\"page_title\":\"#FFFFFF\",\"checkbox_color\":\"#6C63FF\"},\n  \"sounds\":{\"btn_bg\":\"#1A1A26\",\"btn_active_bg\":\"#28265A\",\"btn_border_active\":\"#6C63FF\",\"btn_click_glow\":\"#6C63FF\",\"btn_text\":\"#FFFFFF\",\"wave_color\":\"#6C63FF\",\"vol_bar_track\":\"#44FFFFFF\",\"vol_bar_fill\":\"#FFFFFF\",\"stop_all_bg\":\"#1E1E2A\",\"stop_all_border\":\"#6C63FF\",\"stop_all_text\":\"#FFFFFF\"},\n  \"controls\":{\"stop_bg\":\"#1E1E2A\",\"stop_border\":\"#6C63FF\",\"stop_text\":\"#FFFFFF\"},\n  \"settings\":{\"card_bg\":\"#16161F\",\"card_border\":\"#22223A\",\"label_text\":\"#FFFFFF\",\"header_text\":\"#8888AA\",\"hint_text\":\"#8888AA\",\"version_text\":\"#44445A\",\"input_bg\":\"#1E1E2A\",\"input_border\":\"#22223A\",\"input_text\":\"#FFFFFF\",\"btn_bg\":\"#1E1E2A\",\"btn_border\":\"#6C63FF\",\"btn_text\":\"#FFFFFF\",\"about_text\":\"#6C63FF\",\"divider\":\"#22223A\",\"page_title\":\"#FFFFFF\",\"switch_thumb_on\":\"#6C63FF\",\"switch_track_on\":\"#3D3880\",\"switch_thumb_off\":\"#8888AA\",\"switch_track_off\":\"#2A2A3A\",\"made_by_text\":\"#44445A\",\"made_by_brand\":\"#6C63FF\",\"tune_icon_color\":\"#6C63FF\"},\n  \"notification\":{\"bg\":\"#1A1A26\",\"icon_bg\":\"#6C63FF\"}\n}" },
        { "Light", "{\n  \"global\":{\"bg_primary\":\"#F4F4F8\",\"bg_card\":\"#FFFFFF\",\"bg_card2\":\"#EBEBF2\",\"accent\":\"#5B52E8\",\"accent_dim\":\"#BDB9F7\",\"divider\":\"#D8D8E8\",\"text_primary\":\"#12121A\",\"text_secondary\":\"#666688\",\"text_section_title\":\"#12121A\",\"icon_note_color\":\"#5B52E8\"},\n  \"nav\":{\"bg\":\"#F4F4F8\",\"selected\":\"#12121A\",\"unselected\":\"#AAAACC\",\"label_selected\":\"#12121A\",\"label_unselected\":\"#AAAACC\"},\n  \"home\":{\"section_title\":\"#12121A\",\"card_bg\":\"#FFFFFF\",\"chip_playing_bg\":\"#DDDAF9\",\"chip_playing_border\":\"#5B52E8\",\"chip_text\":\"#12121A\",\"empty_text\":\"#AAAACC\",\"page_title\":\"#12121A\",\"now_playing_anim\":\"#5B52E8\"},\n  \"radio\":{\"station_bg\":\"#EBEBF2\",\"station_bg_active\":\"#DDDAF9\",\"station_border_active\":\"#5B52E8\",\"station_text\":\"#12121A\",\"station_text_active\":\"#5B52E8\",\"station_click_glow\":\"#5B52E8\",\"eq_bar\":\"#5B52E8\",\"group_name_text\":\"#5B52E8\",\"group_name_collapsed_text\":\"#666688\",\"search_bg\":\"#EBEBF2\",\"search_text\":\"#12121A\",\"search_hint\":\"#AAAACC\",\"page_title\":\"#12121A\",\"checkbox_color\":\"#5B52E8\"},\n  \"sounds\":{\"btn_bg\":\"#EBEBF2\",\"btn_active_bg\":\"#DDDAF9\",\"btn_border_active\":\"#5B52E8\",\"btn_click_glow\":\"#5B52E8\",\"btn_text\":\"#12121A\",\"wave_color\":\"#5B52E8\",\"vol_bar_track\":\"#44000000\",\"vol_bar_fill\":\"#5B52E8\",\"stop_all_bg\":\"#EBEBF2\",\"stop_all_border\":\"#5B52E8\",\"stop_all_text\":\"#5B52E8\"},\n  \"controls\":{\"stop_bg\":\"#EBEBF2\",\"stop_border\":\"#5B52E8\",\"stop_text\":\"#5B52E8\"},\n  \"settings\":{\"card_bg\":\"#FFFFFF\",\"card_border\":\"#D8D8E8\",\"label_text\":\"#12121A\",\"header_text\":\"#666688\",\"hint_text\":\"#AAAACC\",\"version_text\":\"#AAAACC\",\"input_bg\":\"#EBEBF2\",\"input_border\":\"#D8D8E8\",\"input_text\":\"#12121A\",\"btn_bg\":\"#EBEBF2\",\"btn_border\":\"#5B52E8\",\"btn_text\":\"#5B52E8\",\"about_text\":\"#5B52E8\",\"divider\":\"#D8D8E8\",\"page_title\":\"#12121A\",\"switch_thumb_on\":\"#5B52E8\",\"switch_track_on\":\"#BDB9F7\",\"switch_thumb_off\":\"#AAAACC\",\"switch_track_off\":\"#D8D8E8\",\"made_by_text\":\"#AAAACC\",\"made_by_brand\":\"#5B52E8\",\"tune_icon_color\":\"#5B52E8\"},\n  \"notification\":{\"bg\":\"#FFFFFF\",\"icon_bg\":\"#5B52E8\"}\n}" },
        { "Dusk",  "{\n  \"global\":{\"bg_primary\":\"#1A0F1F\",\"bg_card\":\"#221528\",\"bg_card2\":\"#2C1A35\",\"accent\":\"#E07AFF\",\"accent_dim\":\"#6A2E80\",\"divider\":\"#3A2048\",\"text_primary\":\"#F5E8FF\",\"text_secondary\":\"#AA88CC\",\"text_section_title\":\"#F5E8FF\",\"icon_note_color\":\"#E07AFF\"},\n  \"nav\":{\"bg\":\"#1A0F1F\",\"selected\":\"#F5E8FF\",\"unselected\":\"#6A4080\",\"label_selected\":\"#F5E8FF\",\"label_unselected\":\"#6A4080\"},\n  \"home\":{\"section_title\":\"#F5E8FF\",\"card_bg\":\"#221528\",\"chip_playing_bg\":\"#451860\",\"chip_playing_border\":\"#E07AFF\",\"chip_text\":\"#F5E8FF\",\"empty_text\":\"#6A4080\",\"page_title\":\"#F5E8FF\",\"now_playing_anim\":\"#E07AFF\"},\n  \"radio\":{\"station_bg\":\"#2C1A35\",\"station_bg_active\":\"#451860\",\"station_border_active\":\"#E07AFF\",\"station_text\":\"#F5E8FF\",\"station_text_active\":\"#E07AFF\",\"station_click_glow\":\"#E07AFF\",\"eq_bar\":\"#E07AFF\",\"group_name_text\":\"#E07AFF\",\"group_name_collapsed_text\":\"#AA88CC\",\"search_bg\":\"#2C1A35\",\"search_text\":\"#F5E8FF\",\"search_hint\":\"#AA88CC\",\"page_title\":\"#F5E8FF\",\"checkbox_color\":\"#E07AFF\"},\n  \"sounds\":{\"btn_bg\":\"#221528\",\"btn_active_bg\":\"#451860\",\"btn_border_active\":\"#E07AFF\",\"btn_click_glow\":\"#E07AFF\",\"btn_text\":\"#F5E8FF\",\"wave_color\":\"#E07AFF\",\"vol_bar_track\":\"#44F5E8FF\",\"vol_bar_fill\":\"#E07AFF\",\"stop_all_bg\":\"#2C1A35\",\"stop_all_border\":\"#E07AFF\",\"stop_all_text\":\"#F5E8FF\"},\n  \"controls\":{\"stop_bg\":\"#2C1A35\",\"stop_border\":\"#E07AFF\",\"stop_text\":\"#F5E8FF\"},\n  \"settings\":{\"card_bg\":\"#221528\",\"card_border\":\"#3A2048\",\"label_text\":\"#F5E8FF\",\"header_text\":\"#AA88CC\",\"hint_text\":\"#AA88CC\",\"version_text\":\"#6A4080\",\"input_bg\":\"#2C1A35\",\"input_border\":\"#3A2048\",\"input_text\":\"#F5E8FF\",\"btn_bg\":\"#2C1A35\",\"btn_border\":\"#E07AFF\",\"btn_text\":\"#F5E8FF\",\"about_text\":\"#E07AFF\",\"divider\":\"#3A2048\",\"page_title\":\"#F5E8FF\",\"switch_thumb_on\":\"#E07AFF\",\"switch_track_on\":\"#6A2E80\",\"switch_thumb_off\":\"#6A4080\",\"switch_track_off\":\"#2C1A35\",\"made_by_text\":\"#6A4080\",\"made_by_brand\":\"#E07AFF\",\"tune_icon_color\":\"#E07AFF\"},\n  \"notification\":{\"bg\":\"#221528\",\"icon_bg\":\"#E07AFF\"}\n}" },
        { "Ocean", "{\n  \"global\":{\"bg_primary\":\"#080F1A\",\"bg_card\":\"#0D1A2A\",\"bg_card2\":\"#122035\",\"accent\":\"#1ECBE1\",\"accent_dim\":\"#0E5F6A\",\"divider\":\"#183048\",\"text_primary\":\"#E0F8FF\",\"text_secondary\":\"#6A9BB0\",\"text_section_title\":\"#E0F8FF\",\"icon_note_color\":\"#1ECBE1\"},\n  \"nav\":{\"bg\":\"#080F1A\",\"selected\":\"#E0F8FF\",\"unselected\":\"#2A5068\",\"label_selected\":\"#E0F8FF\",\"label_unselected\":\"#2A5068\"},\n  \"home\":{\"section_title\":\"#E0F8FF\",\"card_bg\":\"#0D1A2A\",\"chip_playing_bg\":\"#0A3D4A\",\"chip_playing_border\":\"#1ECBE1\",\"chip_text\":\"#E0F8FF\",\"empty_text\":\"#2A5068\",\"page_title\":\"#E0F8FF\",\"now_playing_anim\":\"#1ECBE1\"},\n  \"radio\":{\"station_bg\":\"#122035\",\"station_bg_active\":\"#0A3D4A\",\"station_border_active\":\"#1ECBE1\",\"station_text\":\"#E0F8FF\",\"station_text_active\":\"#1ECBE1\",\"station_click_glow\":\"#1ECBE1\",\"eq_bar\":\"#1ECBE1\",\"group_name_text\":\"#1ECBE1\",\"group_name_collapsed_text\":\"#6A9BB0\",\"search_bg\":\"#122035\",\"search_text\":\"#E0F8FF\",\"search_hint\":\"#6A9BB0\",\"page_title\":\"#E0F8FF\",\"checkbox_color\":\"#1ECBE1\"},\n  \"sounds\":{\"btn_bg\":\"#0D1A2A\",\"btn_active_bg\":\"#0A3D4A\",\"btn_border_active\":\"#1ECBE1\",\"btn_click_glow\":\"#1ECBE1\",\"btn_text\":\"#E0F8FF\",\"wave_color\":\"#1ECBE1\",\"vol_bar_track\":\"#44E0F8FF\",\"vol_bar_fill\":\"#1ECBE1\",\"stop_all_bg\":\"#122035\",\"stop_all_border\":\"#1ECBE1\",\"stop_all_text\":\"#E0F8FF\"},\n  \"controls\":{\"stop_bg\":\"#122035\",\"stop_border\":\"#1ECBE1\",\"stop_text\":\"#E0F8FF\"},\n  \"settings\":{\"card_bg\":\"#0D1A2A\",\"card_border\":\"#183048\",\"label_text\":\"#E0F8FF\",\"header_text\":\"#6A9BB0\",\"hint_text\":\"#6A9BB0\",\"version_text\":\"#2A5068\",\"input_bg\":\"#122035\",\"input_border\":\"#183048\",\"input_text\":\"#E0F8FF\",\"btn_bg\":\"#122035\",\"btn_border\":\"#1ECBE1\",\"btn_text\":\"#E0F8FF\",\"about_text\":\"#1ECBE1\",\"divider\":\"#183048\",\"page_title\":\"#E0F8FF\",\"switch_thumb_on\":\"#1ECBE1\",\"switch_track_on\":\"#0E5F6A\",\"switch_thumb_off\":\"#2A5068\",\"switch_track_off\":\"#122035\",\"made_by_text\":\"#2A5068\",\"made_by_brand\":\"#1ECBE1\",\"tune_icon_color\":\"#1ECBE1\"},\n  \"notification\":{\"bg\":\"#0D1A2A\",\"icon_bg\":\"#1ECBE1\"}\n}" },
    };

    private void buildThemePresets() {
        if (themePresetsContainer == null) return;
        themePresetsContainer.removeAllViews();
        float dp = density();

        // First try assets/themes/ directory for user-supplied themes
        boolean hasAssetThemes = false;
        try {
            String[] assetThemes = requireContext().getAssets().list("themes");
            if (assetThemes != null && assetThemes.length > 0) {
                hasAssetThemes = true;
                for (String file : assetThemes) {
                    if (!file.endsWith(".json")) continue;
                    String themeName = prettify(file.replace(".json", ""));
                    final String themeFile = file;
                    addPresetButton(themePresetsContainer, themeName, dp, () -> {
                        try {
                            BufferedReader br = new BufferedReader(new InputStreamReader(
                                    requireContext().getAssets().open("themes/" + themeFile)));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line).append('\n');
                            br.close();
                            applyThemeJson(sb.toString(), themeName);
                        } catch (Exception e) {
                            Toast.makeText(requireContext(), "Failed to load theme", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        } catch (Exception ignored) {}

        // Always add built-in presets
        for (String[] preset : BUILTIN_THEMES) {
            final String name = preset[0], json = preset[1];
            addPresetButton(themePresetsContainer, name, dp, () -> applyThemeJson(json, name));
        }
    }

    private void addPresetButton(LinearLayout container, String label, float dp, Runnable action) {
        TextView btn = new TextView(requireContext());
        btn.setText(label);
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setTextSize(13f);
        btn.setPadding((int)(16*dp), (int)(10*dp), (int)(16*dp), (int)(10*dp));
        btn.setClickable(true); btn.setFocusable(true);
        styleBtn(btn);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, (int)(8*dp), (int)(8*dp));
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> action.run());
        container.addView(btn);
    }

    private void applyThemeJson(String json, String name) {
        colors.saveRaw(json);
        colors = new ColorConfig(requireContext());
        if (listener != null) listener.onThemeChanged();
        // Refresh raw editor if visible
        if (etColorCfg != null) etColorCfg.setText(colors.readRaw());
        applyTheme(getView());
        Toast.makeText(requireContext(), name + " theme applied", Toast.LENGTH_SHORT).show();
    }

    private static String prettify(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── Network ──────────────────────────────────────────────────
    private void fetchAndMerge(String url) {
        new OkHttpClient().newCall(new Request.Builder().url(url).build())
                .enqueue(new Callback() {
                    @Override public void onFailure(Call c, IOException e) {
                        requireActivity().runOnUiThread(() -> {
                            if (tvFetchStatus != null) tvFetchStatus.setText("Failed: " + e.getMessage());
                        });
                    }
                    @Override public void onResponse(Call c, Response r) throws IOException {
                        if (!r.isSuccessful()) {
                            requireActivity().runOnUiThread(() -> {
                                if (tvFetchStatus != null) tvFetchStatus.setText("HTTP " + r.code());
                            });
                            return;
                        }
                        String body = r.body() != null ? r.body().string() : "";
                        try {
                            java.io.File f = new java.io.File(requireContext().getFilesDir(), "radio_extra.cfg");
                            java.io.FileWriter fw = new java.io.FileWriter(f, true);
                            fw.write("\n" + body); fw.close();
                        } catch (Exception ignored) {}
                        requireActivity().runOnUiThread(() -> {
                            if (tvFetchStatus != null) tvFetchStatus.setText("Done. Go to Radio tab.");
                        });
                    }
                });
    }

    // ═══════════════════════════════════════════════════════════════
    // THEMING
    // ═══════════════════════════════════════════════════════════════

    public void applyTheme(View root) {
        if (root == null || colors == null) return;

        root.setBackgroundColor(colors.bgPrimary());
        if (tvSettingsTitle != null) tvSettingsTitle.setTextColor(colors.settingsPageTitle());
        if (dividerSettings != null) dividerSettings.setBackgroundColor(colors.settingsDivider());

        styleCard(cardToggles);
        styleCard(cardTheme);
        styleCard(cardStations);
        styleCard(cardAdvanced);

        lbl(root.findViewById(R.id.tvToggleHeader));
        lbl(root.findViewById(R.id.tvHapticLabel));
        lbl(root.findViewById(R.id.tvBtnSoundLabel));
        lbl(root.findViewById(R.id.tvPersistentLabel));
        sub(root.findViewById(R.id.tvPersistentSub));
        lbl(root.findViewById(R.id.tvThemeHeader));
        lbl(root.findViewById(R.id.tvThemePresetsHeader));
        lbl(root.findViewById(R.id.tvCountryHeader));
        lbl(root.findViewById(R.id.tvCountryNote));
        lbl(root.findViewById(R.id.tvAdvancedHeader));
        lbl(root.findViewById(R.id.tvStationsHeader));

        styleInput(etColorCfg);
        styleInput(etCountry);
        styleInput(etStationUrl);
        styleBtn(btnSaveColor);
        styleBtn(btnSaveCountry);
        styleBtn(btnFetch);

        // Restyle all preset buttons after theme change
        if (themePresetsContainer != null) {
            float dp = density();
            for (int i = 0; i < themePresetsContainer.getChildCount(); i++) {
                View child = themePresetsContainer.getChildAt(i);
                if (child instanceof TextView) styleBtn((TextView) child);
            }
        }

        if (tvFetchStatus != null) tvFetchStatus.setTextColor(colors.textSettingsHint());
        if (tvVersion     != null) tvVersion.setTextColor(colors.textSettingsVersion());
        if (tvMadeBy      != null) applyMadeByColors();

        styleSwitches();
    }

    private void styleSwitches() {
        if (swHaptic == null && swBtnSound == null && swPersistent == null) return;
        ColorStateList thumbStates = new ColorStateList(
                new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} },
                new int[]{ colors.settingsSwitchThumbOn(), colors.settingsSwitchThumbOff() });
        ColorStateList trackStates = new ColorStateList(
                new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} },
                new int[]{ colors.settingsSwitchTrackOn(), colors.settingsSwitchTrackOff() });
        for (SwitchCompat sw : new SwitchCompat[]{swHaptic, swBtnSound, swPersistent}) {
            if (sw == null) continue;
            sw.setThumbTintList(thumbStates);
            sw.setTrackTintList(trackStates);
        }
    }

    private void styleCard(View card) {
        if (card == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(12 * dp);
        gd.setColor(colors.bgSettingsCard()); gd.setStroke((int)(1*dp), colors.settingsCardBorder());
        card.setBackground(gd);
    }

    private void styleInput(EditText et) {
        if (et == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(8 * dp);
        gd.setColor(colors.settingsInputBg()); gd.setStroke((int)(1*dp), colors.settingsInputBorder());
        et.setBackground(gd);
        et.setTextColor(colors.settingsInputText());
        et.setHintTextColor(colors.textSettingsHint());
    }

    private void styleBtn(TextView btn) {
        if (btn == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(10 * dp);
        gd.setColor(colors.settingsBtnBg()); gd.setStroke((int)(1.5f*dp), colors.settingsBtnBorder());
        btn.setBackground(gd); btn.setTextColor(colors.settingsBtnText());
    }

    private void lbl(View v) {
        if (v instanceof TextView) ((TextView) v).setTextColor(colors.textSettingsLabel());
    }
    private void sub(View v) {
        if (v instanceof TextView) ((TextView) v).setTextColor(colors.textSettingsHint());
    }

    private void applyMadeByColors() {
        if (tvMadeBy == null) return;
        android.text.SpannableString ss = new android.text.SpannableString("Made by pxatyush");
        ss.setSpan(new android.text.style.ForegroundColorSpan(colors.settingsMadeByText()),
                0, 8, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new android.text.style.ForegroundColorSpan(colors.settingsMadeByBrand()),
                8, ss.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvMadeBy.setText(ss);
    }

    private float density() { return getResources().getDisplayMetrics().density; }
}
