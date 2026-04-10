package is.dyino.ui.settings;

import android.content.res.ColorStateList;
import android.graphics.Color;
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

    /** SharedPrefs key to persist the currently applied theme name */
    private static final String PREF_ACTIVE_THEME = "active_theme_name";

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
    private LinearLayout customEditorWrap;
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
        customEditorWrap     = view.findViewById(R.id.customEditorWrap);
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

        swHaptic.setChecked(prefs.isHapticEnabled());
        swBtnSound.setChecked(prefs.isButtonSoundEnabled());
        swPersistent.setChecked(prefs.isPersistentPlayingEnabled());
        if (etColorCfg != null) etColorCfg.setText(colors.readRaw());
        if (etCountry  != null) etCountry.setText(prefs.getRadioCountry());

        swHaptic.setOnCheckedChangeListener((b, v) -> prefs.setHapticEnabled(v));
        swBtnSound.setOnCheckedChangeListener((b, v) -> {
            prefs.setButtonSoundEnabled(v);
            if (listener != null) listener.onButtonSoundChanged(v);
        });
        swPersistent.setOnCheckedChangeListener((b, v) -> prefs.setPersistentPlayingEnabled(v));

        if (btnSaveColor != null) btnSaveColor.setOnClickListener(v -> {
            if (etColorCfg == null) return;
            colors.saveRaw(etColorCfg.getText().toString());
            setActiveThemeName("Custom");
            if (listener != null) listener.onThemeChanged();
            Toast.makeText(requireContext(), "Custom theme saved", Toast.LENGTH_SHORT).show();
            refreshPresetButtons();
        });
        if (btnSaveCountry != null) btnSaveCountry.setOnClickListener(v -> {
            if (etCountry == null) return;
            String c = etCountry.getText().toString().trim();
            prefs.setRadioCountry(c); prefs.saveRadioCache("");
            Toast.makeText(requireContext(),
                    "Set to \"" + (c.isEmpty() ? "Global" : c) + "\". Go to Radio tab.",
                    Toast.LENGTH_LONG).show();
        });
        if (btnFetch != null) btnFetch.setOnClickListener(v -> {
            if (etStationUrl == null) return;
            String url = etStationUrl.getText().toString().trim();
            if (url.isEmpty()) { if (tvFetchStatus != null) tvFetchStatus.setText("Enter a URL first"); return; }
            if (tvFetchStatus != null) tvFetchStatus.setText("Fetching…");
            fetchAndMerge(url);
        });

        buildThemePresets();
        applyTheme(view);
    }

    // ── Active theme persistence ──────────────────────────────────
    private String getActiveThemeName() {
        return requireContext().getSharedPreferences("dyino_prefs", 0)
                .getString(PREF_ACTIVE_THEME, "");
    }

    private void setActiveThemeName(String name) {
        requireContext().getSharedPreferences("dyino_prefs", 0)
                .edit().putString(PREF_ACTIVE_THEME, name).apply();
    }

    // ══════════════════════════════════════════════════════════════
    // 30 THEME PRESETS
    // bg, card, card2, accent, accentDim, divider, textPri, textSec, navUnsel, navBg, navLabelUnsel
    // ══════════════════════════════════════════════════════════════
    private static final String[][] THEMES = {
      // ── Dark / AMOLED ──
      {"Dark",         "#0D0D14","#16161F","#1E1E2A","#6C63FF","#3D3880","#22223A","#FFFFFF","#8888AA","#44445A","#0D0D14","#44445A"},
      {"AMOLED Black", "#000000","#0A0A0A","#111111","#6C63FF","#2A2880","#1A1A1A","#FFFFFF","#888888","#333333","#000000","#333333"},
      {"AMOLED Purple","#000000","#0A0008","#110012","#BB86FC","#6200EA","#1E001E","#FFFFFF","#CC99FF","#440044","#000000","#440044"},
      {"AMOLED Blue",  "#000000","#000A14","#001020","#0AF0FF","#003A50","#001830","#FFFFFF","#6AAABB","#003344","#000000","#003344"},
      {"AMOLED Green", "#000000","#001208","#001A08","#00E676","#007A3A","#001E10","#FFFFFF","#66BB88","#003018","#000000","#003018"},
      // ── Day / Light ──
      {"Light",        "#F4F4F8","#FFFFFF","#EBEBF2","#5B52E8","#BDB9F7","#D8D8E8","#12121A","#666688","#AAAACC","#F4F4F8","#AAAACC"},
      {"Day Blue",     "#EEF4FF","#FFFFFF","#DCE8FF","#2979FF","#82B1FF","#BBCCE8","#0D1A33","#5577AA","#9AADCC","#EEF4FF","#9AADCC"},
      {"Day Green",    "#EDFAF4","#FFFFFF","#DAFCE9","#00897B","#80CBC4","#B2DFDB","#0D2018","#447766","#99BBAA","#EDFAF4","#99BBAA"},
      {"Cream",        "#FAF7F0","#FFFFFF","#F5EED8","#B8860B","#F0D080","#E0D0A0","#1A1208","#887744","#CCBB88","#FAF7F0","#CCBB88"},
      {"Paper",        "#F2EDE8","#FFFFFF","#EDE5DA","#8B5E3C","#D4A27A","#D5C8B8","#1A0E06","#886644","#C8A88A","#F2EDE8","#C8A88A"},
      // ── Colours / Themes ──
      {"Dusk",         "#1A0F1F","#221528","#2C1A35","#E07AFF","#6A2E80","#3A2048","#F5E8FF","#AA88CC","#6A4080","#1A0F1F","#6A4080"},
      {"Ocean",        "#080F1A","#0D1A2A","#122035","#1ECBE1","#0E5F6A","#183048","#E0F8FF","#6A9BB0","#2A5068","#080F1A","#2A5068"},
      {"Forest",       "#0A1208","#121C10","#1A2818","#4CAF50","#2E6B30","#1E3020","#E8F5E9","#88AA88","#3A5A3A","#0A1208","#3A5A3A"},
      {"Sunset",       "#1A0A00","#2A1200","#3A1E06","#FF7043","#8B3000","#3D1C0A","#FFF3E0","#FFAB80","#7A3010","#1A0A00","#7A3010"},
      {"Rose",         "#18090E","#24111A","#301828","#F06292","#882244","#3A1828","#FFE4EC","#CC88AA","#7A3050","#18090E","#7A3050"},
      {"Slate",        "#0C0D10","#141519","#1C1D22","#7986CB","#3D4A8A","#22232A","#E8EAF6","#7A80A0","#3A3D50","#0C0D10","#3A3D50"},
      {"Amber",        "#110D00","#1C1500","#281E00","#FFB300","#7A5600","#302400","#FFF8E1","#CCAA55","#7A6020","#110D00","#7A6020"},
      {"Mint",         "#081210","#10201E","#182E2C","#26A69A","#0E5E58","#1A3030","#E0F2F1","#70A8A4","#2A5050","#081210","#2A5050"},
      {"Mono",         "#0A0A0A","#141414","#1E1E1E","#FFFFFF","#888888","#2A2A2A","#FFFFFF","#888888","#444444","#0A0A0A","#444444"},
      {"Neon",         "#000814","#001028","#001840","#00F5FF","#004D6B","#002240","#E0FFFF","#00A0B0","#003040","#000814","#003040"},
      {"Crimson",      "#120408","#1E0810","#2A1018","#E53935","#8B1A1A","#301018","#FFE8E8","#CC8888","#7A2020","#120408","#7A2020"},
      {"Midnight",     "#080818","#0F102A","#161830","#4FC3F7","#1565C0","#1A1E38","#E3F2FD","#6A9BC0","#2A3060","#080818","#2A3060"},
      {"Aurora",       "#060C14","#0A1522","#0F1E30","#69FF47","#1B7A00","#102218","#E8FFE0","#66AA66","#204A28","#060C14","#204A28"},
      {"Sand",         "#1A1208","#261C10","#342818","#F4A460","#8B5E1A","#3A2C1A","#FFF5E6","#CCAA77","#7A5030","#1A1208","#7A5030"},
      {"Grape",        "#120818","#1E1028","#2A1838","#CE93D8","#6A1B9A","#301848","#F3E5F5","#BB88CC","#6A3888","#120818","#6A3888"},
      // ── Specialty ──
      {"Dracula",      "#282A36","#44475A","#21222C","#BD93F9","#6272A4","#44475A","#F8F8F2","#6272A4","#44475A","#282A36","#44475A"},
      {"Solarized",    "#002B36","#073642","#002B36","#268BD2","#586E75","#073642","#EEE8D5","#657B83","#586E75","#002B36","#586E75"},
      {"Nord",         "#2E3440","#3B4252","#434C5E","#88C0D0","#5E81AC","#4C566A","#ECEFF4","#D8DEE9","#4C566A","#2E3440","#4C566A"},
      {"Material You", "#1C1B1F","#2B2930","#37353D","#D0BCFF","#6650A4","#48464F","#E6E1E5","#CAC4D0","#49454E","#1C1B1F","#49454E"},
      {"Catppuccin",   "#1E1E2E","#181825","#313244","#CBA6F7","#45475A","#45475A","#CDD6F4","#A6ADC8","#6C7086","#1E1E2E","#6C7086"},
    };

    private String buildThemeJson(String[] t) {
        // t: [name, bg, card, card2, accent, accentDim, divider, textPri, textSec, navUnsel, navBg, navLabelUnsel]
        String bg=t[1],card=t[2],card2=t[3],acc=t[4],accD=t[5],div=t[6],
               tPri=t[7],tSec=t[8],navU=t[9],navBg=t[10],navLU=t[11];
        return "{\n" +
          "\"global\":{\"bg_primary\":\""+bg+"\",\"bg_card\":\""+card+"\",\"bg_card2\":\""+card2+"\"," +
          "\"accent\":\""+acc+"\",\"accent_dim\":\""+accD+"\",\"divider\":\""+div+"\"," +
          "\"text_primary\":\""+tPri+"\",\"text_secondary\":\""+tSec+"\"," +
          "\"text_section_title\":\""+tPri+"\"," +
          "\"icon_note_color\":\""+acc+"\",\"icon_note_vec_tint\":\""+acc+"\"," +
          "\"page_header_text\":\""+tPri+"\",\"page_header_subtitle_text\":\""+tSec+"\"},\n" +
          "\"nav\":{\"bg\":\""+navBg+"\",\"selected\":\""+tPri+"\",\"unselected\":\""+navU+"\"," +
          "\"label_selected\":\""+tPri+"\",\"label_unselected\":\""+navLU+"\"},\n" +
          "\"home\":{\"section_title\":\""+tPri+"\",\"card_bg\":\""+card+"\"," +
          "\"chip_playing_bg\":\""+accD+"\",\"chip_playing_border\":\""+acc+"\"," +
          "\"chip_text\":\""+tPri+"\",\"empty_text\":\""+navU+"\",\"page_title\":\""+tPri+"\"," +
          "\"now_playing_anim\":\""+acc+"\",\"now_playing_card_bg\":\""+card2+"\"," +
          "\"now_playing_card_border\":\""+acc+"\",\"now_playing_icon_bg\":\""+accD+"\"," +
          "\"now_playing_icon_tint\":\""+acc+"\"},\n" +
          "\"radio\":{\"station_bg\":\""+card2+"\",\"station_bg_active\":\""+accD+"\"," +
          "\"station_border_active\":\""+acc+"\",\"station_text\":\""+tPri+"\"," +
          "\"station_text_active\":\""+acc+"\",\"station_click_glow\":\""+acc+"\"," +
          "\"eq_bar\":\""+acc+"\",\"group_name_text\":\""+acc+"\"," +
          "\"group_name_collapsed_text\":\""+tSec+"\",\"search_bg\":\""+card2+"\"," +
          "\"search_text\":\""+tPri+"\",\"search_hint\":\""+tSec+"\"," +
          "\"page_title\":\""+tPri+"\",\"checkbox_color\":\""+acc+"\"},\n" +
          "\"sounds\":{\"btn_bg\":\""+card+"\",\"btn_active_bg\":\""+accD+"\"," +
          "\"btn_border_active\":\""+acc+"\",\"btn_click_glow\":\""+acc+"\"," +
          "\"btn_text\":\""+tPri+"\",\"wave_color\":\""+acc+"\"," +
          "\"vol_bar_track\":\"#44FFFFFF\",\"vol_bar_fill\":\""+tPri+"\"," +
          "\"stop_all_bg\":\""+card2+"\",\"stop_all_border\":\""+acc+"\"," +
          "\"stop_all_text\":\""+tPri+"\"},\n" +
          "\"controls\":{\"stop_bg\":\""+card2+"\",\"stop_border\":\""+acc+"\",\"stop_text\":\""+tPri+"\"},\n" +
          "\"settings\":{\"card_bg\":\""+card+"\",\"card_border\":\""+div+"\"," +
          "\"label_text\":\""+tPri+"\",\"header_text\":\""+tSec+"\"," +
          "\"hint_text\":\""+tSec+"\",\"version_text\":\""+navU+"\"," +
          "\"input_bg\":\""+card2+"\",\"input_border\":\""+div+"\"," +
          "\"input_text\":\""+tPri+"\",\"btn_bg\":\""+card2+"\"," +
          "\"btn_border\":\""+acc+"\",\"btn_text\":\""+tPri+"\"," +
          "\"about_text\":\""+acc+"\",\"divider\":\""+div+"\"," +
          "\"page_title\":\""+tPri+"\",\"switch_thumb_on\":\""+acc+"\"," +
          "\"switch_track_on\":\""+accD+"\",\"switch_thumb_off\":\""+tSec+"\"," +
          "\"switch_track_off\":\""+card2+"\",\"made_by_text\":\""+navU+"\"," +
          "\"made_by_brand\":\""+acc+"\",\"tune_icon_color\":\""+acc+"\"},\n" +
          "\"notification\":{\"bg\":\""+card+"\",\"icon_bg\":\""+acc+"\"}\n}";
    }

    private void buildThemePresets() {
        if (themePresetsContainer == null) return;
        themePresetsContainer.removeAllViews();
        float dp = density();
        String active = getActiveThemeName();

        // Built-in 30 themes
        for (String[] t : THEMES) {
            final String name = t[0];
            final String json = buildThemeJson(t);
            boolean isActive  = name.equals(active);
            addPresetBtn(themePresetsContainer, name, dp, false, isActive,
                    () -> applyThemeJson(json, name));
        }

        // User asset themes
        try {
            String[] assetThemes = requireContext().getAssets().list("themes");
            if (assetThemes != null) {
                for (String file : assetThemes) {
                    if (!file.endsWith(".json")) continue;
                    String name = prettify(file.replace(".json", ""));
                    boolean isActive = name.equals(active);
                    addPresetBtn(themePresetsContainer, name, dp, false, isActive, () -> {
                        try {
                            BufferedReader br = new BufferedReader(new InputStreamReader(
                                    requireContext().getAssets().open("themes/" + file)));
                            StringBuilder sb = new StringBuilder(); String line;
                            while ((line = br.readLine()) != null) sb.append(line).append('\n');
                            br.close();
                            applyThemeJson(sb.toString(), name);
                        } catch (Exception e) {
                            Toast.makeText(requireContext(), "Failed to load theme", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        } catch (Exception ignored) {}

        // Custom button (last)
        boolean customActive = "Custom".equals(active);
        addPresetBtn(themePresetsContainer, "Custom ✎", dp, true, customActive,
                () -> {
                    if (customEditorWrap != null) {
                        boolean show = customEditorWrap.getVisibility() != View.VISIBLE;
                        customEditorWrap.setVisibility(show ? View.VISIBLE : View.GONE);
                        if (show && etColorCfg != null) etColorCfg.setText(colors.readRaw());
                    }
                });
    }

    private void refreshPresetButtons() {
        // Rebuild to update highlight state
        buildThemePresets();
    }

    private void addPresetBtn(LinearLayout container, String label, float dp,
                              boolean isCustom, boolean isActive, Runnable action) {
        TextView btn = new TextView(requireContext());
        btn.setText(label);
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setTextSize(13f);
        btn.setPadding((int)(16*dp),(int)(10*dp),(int)(16*dp),(int)(10*dp));
        btn.setClickable(true); btn.setFocusable(true);

        if (isActive) {
            styleBtnActive(btn);
        } else if (isCustom) {
            styleBtnAccent(btn);
        } else {
            styleBtn(btn);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, (int)(8*dp), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> action.run());
        container.addView(btn);
    }

    private void applyThemeJson(String json, String name) {
        colors.saveRaw(json);
        setActiveThemeName(name);
        colors = new ColorConfig(requireContext());
        if (listener != null) listener.onThemeChanged();
        if (etColorCfg != null) etColorCfg.setText(colors.readRaw());
        applyTheme(getView());
        refreshPresetButtons();
        Toast.makeText(requireContext(), name + " applied", Toast.LENGTH_SHORT).show();
    }

    // ── Network ──────────────────────────────────────────────────
    private void fetchAndMerge(String url) {
        new OkHttpClient().newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call c, IOException e) {
                requireActivity().runOnUiThread(() -> { if(tvFetchStatus!=null)tvFetchStatus.setText("Failed: "+e.getMessage()); });
            }
            @Override public void onResponse(Call c, Response r) throws IOException {
                if (!r.isSuccessful()) { requireActivity().runOnUiThread(()->{ if(tvFetchStatus!=null)tvFetchStatus.setText("HTTP "+r.code()); }); return; }
                String body = r.body()!=null?r.body().string():"";
                try { java.io.File f=new java.io.File(requireContext().getFilesDir(),"radio_extra.cfg"); java.io.FileWriter fw=new java.io.FileWriter(f,true); fw.write("\n"+body); fw.close(); } catch(Exception ignored){}
                requireActivity().runOnUiThread(()->{ if(tvFetchStatus!=null)tvFetchStatus.setText("Done. Go to Radio tab."); });
            }
        });
    }

    // ── Theming ───────────────────────────────────────────────────
    public void applyTheme(View root) {
        if (root == null || colors == null) return;
        root.setBackgroundColor(colors.bgPrimary());

        // Page header title uses pageHeaderText
        if (tvSettingsTitle != null) tvSettingsTitle.setTextColor(colors.pageHeaderText());
        if (dividerSettings != null) dividerSettings.setBackgroundColor(colors.settingsDivider());

        // No ic_note_vec icon in header — it was removed from the XML

        styleCard(cardToggles); styleCard(cardTheme);
        styleCard(cardStations); styleCard(cardAdvanced);

        lbl(root.findViewById(R.id.tvToggleHeader));
        lbl(root.findViewById(R.id.tvHapticLabel));
        lbl(root.findViewById(R.id.tvBtnSoundLabel));
        lbl(root.findViewById(R.id.tvPersistentLabel));
        sub(root.findViewById(R.id.tvPersistentSub));
        lbl(root.findViewById(R.id.tvThemePresetsHeader));
        lbl(root.findViewById(R.id.tvThemeHeader));
        lbl(root.findViewById(R.id.tvCountryHeader));
        lbl(root.findViewById(R.id.tvCountryNote));
        lbl(root.findViewById(R.id.tvAdvancedHeader));
        lbl(root.findViewById(R.id.tvStationsHeader));

        styleInput(etColorCfg); styleInput(etCountry); styleInput(etStationUrl);
        styleBtn(btnSaveColor); styleBtn(btnSaveCountry); styleBtn(btnFetch);

        refreshPresetButtons();

        if (tvFetchStatus != null) tvFetchStatus.setTextColor(colors.textSettingsHint());
        if (tvVersion     != null) tvVersion.setTextColor(colors.textSettingsVersion());
        if (tvMadeBy      != null) applyMadeByColors();
        styleSwitches();
    }

    private void styleSwitches() {
        ColorStateList thumbStates = new ColorStateList(
                new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} },
                new int[]{ colors.settingsSwitchThumbOn(), colors.settingsSwitchThumbOff() });
        ColorStateList trackStates = new ColorStateList(
                new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} },
                new int[]{ colors.settingsSwitchTrackOn(), colors.settingsSwitchTrackOff() });
        for (SwitchCompat sw : new SwitchCompat[]{ swHaptic, swBtnSound, swPersistent }) {
            if (sw == null) continue;
            sw.setThumbTintList(thumbStates);
            sw.setTrackTintList(trackStates);
        }
    }

    private void styleCard(View card) {
        if (card == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(12*dp);
        gd.setColor(colors.bgSettingsCard()); gd.setStroke((int)(1*dp), colors.settingsCardBorder());
        card.setBackground(gd);
    }

    private void styleInput(EditText et) {
        if (et == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(8*dp);
        gd.setColor(colors.settingsInputBg()); gd.setStroke((int)(1*dp), colors.settingsInputBorder());
        et.setBackground(gd); et.setTextColor(colors.settingsInputText()); et.setHintTextColor(colors.textSettingsHint());
    }

    private void styleBtn(TextView btn) {
        if (btn == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(10*dp);
        gd.setColor(colors.settingsBtnBg()); gd.setStroke((int)(1.5f*dp), colors.settingsBtnBorder());
        btn.setBackground(gd); btn.setTextColor(colors.settingsBtnText());
    }

    /** Active/selected theme — filled accent background */
    private void styleBtnActive(TextView btn) {
        if (btn == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(10*dp);
        gd.setColor(colors.accent()); gd.setStroke((int)(2f*dp), colors.accent());
        btn.setBackground(gd);
        // Contrast text over accent
        btn.setTextColor(isLight(colors.accent()) ? 0xFF111111 : 0xFFFFFFFF);
    }

    /** Custom editor button — accent-bordered */
    private void styleBtnAccent(TextView btn) {
        if (btn == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(10*dp);
        gd.setColor(colors.accentDim()); gd.setStroke((int)(1.5f*dp), colors.accent());
        btn.setBackground(gd); btn.setTextColor(colors.accent());
    }

    private static boolean isLight(int color) {
        double r = ((color >> 16) & 0xFF) / 255.0;
        double g = ((color >>  8) & 0xFF) / 255.0;
        double b = ( color        & 0xFF) / 255.0;
        return (0.299*r + 0.587*g + 0.114*b) > 0.5;
    }

    private void lbl(View v) { if(v instanceof TextView) ((TextView)v).setTextColor(colors.textSettingsLabel()); }
    private void sub(View v) { if(v instanceof TextView) ((TextView)v).setTextColor(colors.textSettingsHint()); }

    private void applyMadeByColors() {
        if (tvMadeBy == null) return;
        android.text.SpannableString ss = new android.text.SpannableString("Made by pxatyush");
        ss.setSpan(new android.text.style.ForegroundColorSpan(colors.settingsMadeByText()),0,8,android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new android.text.style.ForegroundColorSpan(colors.settingsMadeByBrand()),8,ss.length(),android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvMadeBy.setText(ss);
    }

    private float density() { return getResources().getDisplayMetrics().density; }

    private static String prettify(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
