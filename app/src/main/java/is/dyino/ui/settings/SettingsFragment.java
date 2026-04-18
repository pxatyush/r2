package is.dyino.ui.settings;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import is.dyino.R;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;

public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";

    private AppPrefs    prefs;
    private ColorConfig colors;

    // ── Callback to MainActivity ──────────────────────────────────
    public interface OnSettingsChanged {
        void onThemeChanged();
        void onButtonSoundChanged(boolean enabled);
        void onNavPositionChanged();
    }
    private OnSettingsChanged listener;
    public void setListener(OnSettingsChanged l) { this.listener = l; }

    // ── Static XML views ──────────────────────────────────────────
    private SwitchCompat swHaptic, swBtnSound, swPersistent;
    private EditText     etColorCfg, etCountry;
    private TextView     btnSaveColor, btnSaveCountry, tvCountryNote;
    private TextView     tvVersion, tvMadeBy, tvSettingsTitle, tvSettingsSubtitle;
    private LinearLayout cardToggles, cardTheme, cardStations;
    private LinearLayout themePresetsContainer;
    private LinearLayout customEditorWrap;
    private View         settingsDivider;

    // ── Dynamically injected controls (kept as fields for re-theming) ─
    private SwitchCompat swWaveNotif, swPowerSaving;
    private LinearLayout navBtnsRow;
    private TextView     tvWaveNotifTitle, tvWaveNotifSub;
    private TextView     tvPowerSavingTitle, tvPowerSavingSub;
    private TextView     tvNavPosTitle;

    // ─────────────────────────────────────────────────────────────

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup c,
                             @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_settings, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs  = new AppPrefs(requireContext());
        colors = new ColorConfig(requireContext());

        // ── Wire XML views ────────────────────────────────────────
        tvSettingsTitle      = view.findViewById(R.id.tvSettingsTitle);
        tvSettingsSubtitle   = view.findViewById(R.id.tvSettingsSubtitle);
        settingsDivider      = view.findViewById(R.id.settingsDivider);
        cardToggles          = view.findViewById(R.id.cardToggles);
        cardTheme            = view.findViewById(R.id.cardTheme);
        cardStations         = view.findViewById(R.id.cardStations);
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
        tvMadeBy             = view.findViewById(R.id.tvMadeBy);
        tvVersion            = view.findViewById(R.id.tvVersion);

        // ── Set initial switch states ─────────────────────────────
        if (swHaptic    != null) swHaptic.setChecked(prefs.isHapticEnabled());
        if (swBtnSound  != null) swBtnSound.setChecked(prefs.isButtonSoundEnabled());
        if (swPersistent!= null) swPersistent.setChecked(prefs.isPersistentPlayingEnabled());
        if (etColorCfg  != null) etColorCfg.setText(colors.readRaw());
        if (etCountry   != null) etCountry.setText(prefs.getRadioCountry());

        // ── Listeners ─────────────────────────────────────────────
        if (swHaptic   != null) swHaptic.setOnCheckedChangeListener(
                (b, v) -> prefs.setHapticEnabled(v));
        if (swBtnSound != null) swBtnSound.setOnCheckedChangeListener((b, v) -> {
            prefs.setButtonSoundEnabled(v);
            if (listener != null) listener.onButtonSoundChanged(v);
        });
        if (swPersistent != null) swPersistent.setOnCheckedChangeListener(
                (b, v) -> prefs.setPersistentPlayingEnabled(v));

        if (btnSaveColor != null) btnSaveColor.setOnClickListener(v -> {
            if (etColorCfg == null) return;
            colors.saveRaw(etColorCfg.getText().toString());
            prefs.setActiveThemeName("Custom");
            colors = new ColorConfig(requireContext());
            applyTheme(getView());
            buildThemePresets();
            if (listener != null) listener.onThemeChanged();
            // No toast — visual change is self-evident
        });

        if (btnSaveCountry != null) btnSaveCountry.setOnClickListener(v -> {
            if (etCountry == null) return;
            String c = etCountry.getText().toString().trim();
            prefs.setRadioCountry(c);
            prefs.saveRadioCache(""); // invalidate cache so Radio tab re-fetches
            Toast.makeText(requireContext(),
                    "Country set to \"" + (c.isEmpty() ? "Global" : c)
                            + "\". Go to Radio tab to reload.",
                    Toast.LENGTH_LONG).show();
        });

        injectExtraToggles();
        buildThemePresets();
        applyTheme(view);
    }

    // ══════════════════════════════════════════════════════════════
    // EXTRA TOGGLES  (Wave Notif, Power Saving, Nav Position)
    // All labels stored as fields so applyTheme() can re-colour them.
    // ══════════════════════════════════════════════════════════════

    private void injectExtraToggles() {
        if (cardToggles == null) return;
        float dp = density();

        // ── Wave Notification Icon ────────────────────────────────
        addDivider(cardToggles, dp);

        LinearLayout wRow = makeToggleRow(dp);
        LinearLayout wText = makeTextColumn(dp);

        tvWaveNotifTitle = makeLabel("Wave Notification Icon", 14f);
        tvWaveNotifSub   = makeSub("Animated wave graphic shown in the media notification", dp);
        wText.addView(tvWaveNotifTitle);
        wText.addView(tvWaveNotifSub);
        wRow.addView(wText);

        swWaveNotif = new SwitchCompat(requireContext());
        swWaveNotif.setChecked(prefs.isWaveNotifEnabled());
        swWaveNotif.setOnCheckedChangeListener((b, v) -> prefs.setWaveNotifEnabled(v));
        wRow.addView(swWaveNotif);
        cardToggles.addView(wRow);

        // ── Power Saving Mode ─────────────────────────────────────
        addDivider(cardToggles, dp);

        LinearLayout psRow  = makeToggleRow(dp);
        LinearLayout psText = makeTextColumn(dp);

        tvPowerSavingTitle = makeLabel("Power Saving Mode", 14f);
        tvPowerSavingSub   = makeSub("Disables waves & visualizer to save battery", dp);
        psText.addView(tvPowerSavingTitle);
        psText.addView(tvPowerSavingSub);
        psRow.addView(psText);

        swPowerSaving = new SwitchCompat(requireContext());
        swPowerSaving.setChecked(prefs.isPowerSavingEnabled());
        swPowerSaving.setOnCheckedChangeListener((b, v) -> prefs.setPowerSavingEnabled(v));
        psRow.addView(swPowerSaving);
        cardToggles.addView(psRow);

        // ── Navigation Position ───────────────────────────────────
        addDivider(cardToggles, dp);

        LinearLayout navSection = new LinearLayout(requireContext());
        navSection.setOrientation(LinearLayout.VERTICAL);
        navSection.setPadding((int)(16*dp), (int)(12*dp), (int)(12*dp), (int)(14*dp));

        tvNavPosTitle = makeLabel("Navigation Position", 14f);
        navSection.addView(tvNavPosTitle);

        navBtnsRow = new LinearLayout(requireContext());
        navBtnsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams nblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nblp.setMargins(0, (int)(8*dp), 0, 0);
        navBtnsRow.setLayoutParams(nblp);

        rebuildNavBtns(dp);
        navSection.addView(navBtnsRow);
        cardToggles.addView(navSection);
    }

    private void rebuildNavBtns(float dp) {
        if (navBtnsRow == null) return;
        navBtnsRow.removeAllViews();
        String current = prefs.getNavPosition();
        String[][] opts = {{"Left","left"}, {"Right","right"}, {"Bottom","bottom"}};

        for (String[] opt : opts) {
            String lbl = opt[0], val = opt[1];
            TextView btn = new TextView(requireContext());
            btn.setText(lbl);
            btn.setGravity(android.view.Gravity.CENTER);
            btn.setTextSize(13f);
            btn.setPadding((int)(14*dp), (int)(9*dp), (int)(14*dp), (int)(9*dp));
            btn.setClickable(true);
            btn.setFocusable(true);

            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            blp.setMargins(0, 0, (int)(6*dp), 0);
            btn.setLayoutParams(blp);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(8 * dp);
            if (val.equals(current)) {
                gd.setColor(colors.accent());
                btn.setTextColor(isLight(colors.accent()) ? 0xFF111111 : 0xFFFFFFFF);
            } else {
                gd.setColor(colors.bgCard2());
                gd.setStroke((int)(1*dp), colors.divider());
                btn.setTextColor(colors.textSecondary());
            }
            btn.setBackground(gd);
            btn.setOnClickListener(v -> {
                prefs.setNavPosition(val);
                if (listener != null) listener.onNavPositionChanged();
                rebuildNavBtns(dp);
            });
            navBtnsRow.addView(btn);
        }
    }

    // ── Small view factory helpers ────────────────────────────────

    private LinearLayout makeToggleRow(float dp) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding((int)(16*dp), 0, (int)(12*dp), 0);
        row.setMinimumHeight((int)(56*dp));
        return row;
    }

    private LinearLayout makeTextColumn(float dp) {
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.setPadding(0, (int)(10*dp), 0, (int)(10*dp));
        return col;
    }

    private TextView makeLabel(String text, float size) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(colors.textSettingsLabel());
        return tv;
    }

    private TextView makeSub(String text, float dp) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(colors.textSettingsHint());
        tv.setPadding(0, (int)(2*dp), 0, 0);
        return tv;
    }

    private void addDivider(LinearLayout parent, float dp) {
        View div = new View(requireContext());
        div.setBackgroundColor(colors.divider());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins((int)(16*dp), 0, 0, 0);
        div.setLayoutParams(lp);
        parent.addView(div);
    }

    // ══════════════════════════════════════════════════════════════
    // THEME PRESETS  — loaded from assets/themes/presets.json
    // Each entry: { name, bg, card, card2, accent, accentDim,
    //               divider, textPrimary, textSecondary,
    //               navUnselected, navBg }
    // ══════════════════════════════════════════════════════════════

    /** Reads presets.json and returns a list of String[] in the same
     *  order as the old hardcoded THEMES array so buildJson() is unchanged. */
    private List<String[]> loadPresets() {
        List<String[]> result = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    requireContext().getAssets().open("themes/presets.json")));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                result.add(new String[]{
                    o.optString("name",          "Theme " + i),
                    o.optString("bg",            "#0D0D14"),
                    o.optString("card",          "#16161F"),
                    o.optString("card2",         "#1E1E2A"),
                    o.optString("accent",        "#6C63FF"),
                    o.optString("accentDim",     "#3D3880"),
                    o.optString("divider",       "#22223A"),
                    o.optString("textPrimary",   "#FFFFFF"),
                    o.optString("textSecondary", "#8888AA"),
                    o.optString("navUnselected", "#44445A"),
                    o.optString("navBg",         "#0D0D14")
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "loadPresets", e);
        }
        return result;
    }

    private void buildThemePresets() {
        if (themePresetsContainer == null) return;
        themePresetsContainer.removeAllViews();
        String active = prefs.getActiveThemeName();
        float  dp     = density();

        // Built-in presets from assets/themes/presets.json
        for (String[] t : loadPresets()) {
            final String name = t[0], json = buildJson(t);
            addPresetBtn(name, dp, false, name.equals(active),
                    () -> applyThemeJson(json, name));
        }

        // Extra .json files the user may place in assets/themes/
        // (anything that is NOT presets.json)
        try {
            String[] assetThemes = requireContext().getAssets().list("themes");
            if (assetThemes != null) {
                for (String file : assetThemes) {
                    if (!file.endsWith(".json") || file.equals("presets.json")) continue;
                    String name = cap(file.replace(".json", ""));
                    final String fname = file;
                    addPresetBtn(name, dp, false, name.equals(active), () -> {
                        try {
                            BufferedReader br2 = new BufferedReader(new InputStreamReader(
                                    requireContext().getAssets().open("themes/" + fname)));
                            StringBuilder sb2 = new StringBuilder(); String l;
                            while ((l = br2.readLine()) != null) sb2.append(l).append('\n');
                            br2.close();
                            applyThemeJson(sb2.toString(), name);
                        } catch (Exception ex) {
                            Toast.makeText(requireContext(), "Failed to load theme", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        } catch (Exception ignored) {}

        // Custom editor button
        addPresetBtn("Custom ✎", dp, true, "Custom".equals(active), () -> {
            if (customEditorWrap != null) {
                boolean show = customEditorWrap.getVisibility() != View.VISIBLE;
                customEditorWrap.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show && etColorCfg != null) etColorCfg.setText(colors.readRaw());
            }
        });
    }

    private void addPresetBtn(String label, float dp, boolean isCustom,
                               boolean isActive, Runnable action) {
        TextView btn = new TextView(requireContext());
        btn.setText(label);
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setTextSize(13f);
        btn.setPadding((int)(16*dp), (int)(10*dp), (int)(16*dp), (int)(10*dp));
        btn.setClickable(true);
        btn.setFocusable(true);

        if      (isActive) styleBtnActive(btn);
        else if (isCustom) styleBtnAccent(btn);
        else               styleBtn(btn);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, (int)(8*dp), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> action.run());
        themePresetsContainer.addView(btn);
    }

    private void applyThemeJson(String json, String name) {
        colors.saveRaw(json);
        prefs.setActiveThemeName(name);
        colors = new ColorConfig(requireContext());
        applyTheme(getView());
        buildThemePresets();
        if (listener != null) listener.onThemeChanged();
        if (etColorCfg != null) etColorCfg.setText(colors.readRaw());
        // No toast — the colour change is immediate visual feedback
    }

    // ── Full color.json builder from compact 11-field theme array ─
    private String buildJson(String[] t) {
        String bg=t[1], card=t[2], c2=t[3], acc=t[4], accD=t[5],
               div=t[6], tP=t[7], tS=t[8], nU=t[9], nBg=t[10];
        return "{"
            + "\"global\":{"
                + "\"bg_primary\":\""+bg+"\","
                + "\"bg_card\":\""+card+"\","
                + "\"bg_card2\":\""+c2+"\","
                + "\"accent\":\""+acc+"\","
                + "\"accent_dim\":\""+accD+"\","
                + "\"divider\":\""+div+"\","
                + "\"text_primary\":\""+tP+"\","
                + "\"text_secondary\":\""+tS+"\","
                + "\"text_section_title\":\""+tP+"\","
                + "\"icon_note_vec_tint\":\""+acc+"\","
                + "\"page_header_text\":\""+tP+"\","
                + "\"page_header_subtitle_text\":\""+tS+"\""
            + "},"
            + "\"nav\":{"
                + "\"bg\":\""+nBg+"\","
                + "\"label_selected\":\""+tP+"\","
                + "\"label_unselected\":\""+nU+"\""
            + "},"
            + "\"home\":{"
                + "\"section_title\":\""+tP+"\","
                + "\"chip_playing_bg\":\""+accD+"\","
                + "\"chip_playing_border\":\""+acc+"\","
                + "\"chip_text\":\""+tP+"\","
                + "\"empty_text\":\""+nU+"\","
                + "\"now_playing_anim\":\""+acc+"\","
                + "\"now_playing_card_bg\":\""+c2+"\","
                + "\"now_playing_card_border\":\""+acc+"\","
                + "\"now_playing_icon_tint\":\""+acc+"\","
                + "\"visualizer_bg\":\""+bg+"\","
                + "\"visualizer_bar\":\""+acc+"\""
            + "},"
            + "\"radio\":{"
                + "\"station_bg\":\""+c2+"\","
                + "\"station_bg_active\":\""+accD+"\","
                + "\"station_border_active\":\""+acc+"\","
                + "\"station_text\":\""+tP+"\","
                + "\"station_text_active\":\""+acc+"\","
                + "\"station_click_glow\":\""+acc+"\","
                + "\"eq_bar\":\""+acc+"\","
                + "\"group_header_bg\":\""+card+"\","
                + "\"group_header_border\":\""+div+"\","
                + "\"group_name_text\":\""+acc+"\","
                + "\"group_name_collapsed_text\":\""+tS+"\","
                + "\"group_badge_bg\":\""+accD+"\","
                + "\"group_badge_text\":\""+acc+"\","
                + "\"station_card_bg\":\""+c2+"\","
                + "\"station_card_border\":\""+div+"\","
                + "\"search_bg\":\""+c2+"\","
                + "\"search_text\":\""+tP+"\","
                + "\"search_hint\":\""+tS+"\","
                + "\"checkbox_color\":\""+acc+"\""
            + "},"
            + "\"sounds\":{"
                + "\"btn_bg\":\""+card+"\","
                + "\"btn_active_bg\":\""+accD+"\","
                + "\"btn_border_active\":\""+acc+"\","
                + "\"btn_text\":\""+tP+"\","
                + "\"wave_color\":\""+acc+"\","
                + "\"stop_all_bg\":\""+c2+"\","
                + "\"stop_all_border\":\""+acc+"\","
                + "\"stop_all_text\":\""+tP+"\""
            + "},"
            + "\"settings\":{"
                + "\"card_bg\":\""+card+"\","
                + "\"card_border\":\""+div+"\","
                + "\"label_text\":\""+tP+"\","
                + "\"hint_text\":\""+tS+"\","
                + "\"version_text\":\""+nU+"\","
                + "\"input_bg\":\""+c2+"\","
                + "\"input_border\":\""+div+"\","
                + "\"input_text\":\""+tP+"\","
                + "\"btn_bg\":\""+c2+"\","
                + "\"btn_border\":\""+acc+"\","
                + "\"btn_text\":\""+tP+"\","
                + "\"divider\":\""+div+"\","
                + "\"switch_thumb_on\":\""+acc+"\","
                + "\"switch_track_on\":\""+accD+"\","
                + "\"switch_thumb_off\":\""+tS+"\","
                + "\"switch_track_off\":\""+c2+"\","
                + "\"made_by_text\":\""+nU+"\","
                + "\"made_by_brand\":\""+acc+"\""
            + "},"
            + "\"notification\":{"
                + "\"icon_bg\":\""+acc+"\""
            + "}"
            + "}";
    }

    // ══════════════════════════════════════════════════════════════
    // THEMING  — applies color.json to every view in this fragment
    // ══════════════════════════════════════════════════════════════

    public void applyTheme(View root) {
        if (root == null || colors == null) return;
        root.setBackgroundColor(colors.bgPrimary());

        // Page header
        if (tvSettingsTitle    != null) tvSettingsTitle.setTextColor(colors.pageHeaderText());
        if (tvSettingsSubtitle != null) tvSettingsSubtitle.setTextColor(colors.pageHeaderSubtitleText());
        if (settingsDivider    != null) settingsDivider.setBackgroundColor(colors.settingsDivider());

        // Cards
        styleCard(cardToggles);
        styleCard(cardTheme);
        styleCard(cardStations);

        // Static XML labels
        lbl(root.findViewById(R.id.tvToggleHeader));
        lbl(root.findViewById(R.id.tvHapticLabel));
        lbl(root.findViewById(R.id.tvBtnSoundLabel));
        lbl(root.findViewById(R.id.tvPersistentLabel));
        sub(root.findViewById(R.id.tvPersistentSub));
        lbl(root.findViewById(R.id.tvThemePresetsHeader));
        lbl(root.findViewById(R.id.tvThemeHeader));
        lbl(root.findViewById(R.id.tvCountryHeader));
        if (tvCountryNote != null) tvCountryNote.setTextColor(colors.textSettingsHint());

        // Inputs & buttons
        styleInput(etColorCfg);
        styleInput(etCountry);
        styleBtn(btnSaveColor);
        styleBtn(btnSaveCountry);

        // Theme preset buttons (rebuild to pick up new active-colour)
        buildThemePresets();

        // Footer
        if (tvVersion != null) tvVersion.setTextColor(colors.textSettingsVersion());
        if (tvMadeBy  != null) applyMadeByColors();

        // Switches
        styleSwitches();

        // ── Dynamically-injected extra toggle labels ──────────────
        if (tvWaveNotifTitle   != null) tvWaveNotifTitle.setTextColor(colors.textSettingsLabel());
        if (tvWaveNotifSub     != null) tvWaveNotifSub.setTextColor(colors.textSettingsHint());
        if (tvPowerSavingTitle != null) tvPowerSavingTitle.setTextColor(colors.textSettingsLabel());
        if (tvPowerSavingSub   != null) tvPowerSavingSub.setTextColor(colors.textSettingsHint());
        if (tvNavPosTitle      != null) tvNavPosTitle.setTextColor(colors.textSettingsLabel());

        // Nav position buttons re-draw with new colours
        if (navBtnsRow != null) rebuildNavBtns(density());
    }

    // ── Style helpers ─────────────────────────────────────────────

    private void styleSwitches() {
        ColorStateList thumb = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{colors.settingsSwitchThumbOn(), colors.settingsSwitchThumbOff()});
        ColorStateList track = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{colors.settingsSwitchTrackOn(), colors.settingsSwitchTrackOff()});
        for (SwitchCompat sw : new SwitchCompat[]{
                swHaptic, swBtnSound, swPersistent, swWaveNotif, swPowerSaving}) {
            if (sw == null) continue;
            sw.setThumbTintList(thumb);
            sw.setTrackTintList(track);
        }
    }

    private void styleCard(View card) {
        if (card == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(12 * dp);
        gd.setColor(colors.bgSettingsCard());
        gd.setStroke((int)(1*dp), colors.settingsCardBorder());
        card.setBackground(gd);
    }

    private void styleInput(EditText et) {
        if (et == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(8 * dp);
        gd.setColor(colors.settingsInputBg());
        gd.setStroke((int)(1*dp), colors.settingsInputBorder());
        et.setBackground(gd);
        et.setTextColor(colors.settingsInputText());
        et.setHintTextColor(colors.textSettingsHint());
    }

    private void styleBtn(TextView btn) {
        if (btn == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(10 * dp);
        gd.setColor(colors.settingsBtnBg());
        gd.setStroke((int)(1.5f*dp), colors.settingsBtnBorder());
        btn.setBackground(gd);
        btn.setTextColor(colors.settingsBtnText());
    }

    private void styleBtnActive(TextView btn) {
        if (btn == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(10 * dp);
        gd.setColor(colors.accent());
        gd.setStroke((int)(2f*dp), colors.accent());
        btn.setBackground(gd);
        btn.setTextColor(isLight(colors.accent()) ? 0xFF111111 : 0xFFFFFFFF);
    }

    private void styleBtnAccent(TextView btn) {
        if (btn == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(10 * dp);
        gd.setColor(colors.accentDim());
        gd.setStroke((int)(1.5f*dp), colors.accent());
        btn.setBackground(gd);
        btn.setTextColor(colors.accent());
    }

    private static boolean isLight(int c) {
        double r = ((c >> 16) & 0xFF) / 255.0;
        double g = ((c >>  8) & 0xFF) / 255.0;
        double b = ( c        & 0xFF) / 255.0;
        return (0.299*r + 0.587*g + 0.114*b) > 0.5;
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

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private static String cap(String s) {
        return (s == null || s.isEmpty()) ? s
                : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
