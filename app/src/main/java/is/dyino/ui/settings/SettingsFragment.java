package is.dyino.ui.settings;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
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
    private LinearLayout customEditorWrap;   // wraps the raw JSON editor, hidden by default
    private View         dividerSettings;
    private TextView     tvSettingsTitle;
    private ImageView    ivSettingsIcon;

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
        ivSettingsIcon       = view.findViewById(R.id.ivSettingsIcon);
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
        swBtnSound.setOnCheckedChangeListener((b, v) -> { prefs.setButtonSoundEnabled(v); if(listener!=null) listener.onButtonSoundChanged(v); });
        swPersistent.setOnCheckedChangeListener((b, v) -> prefs.setPersistentPlayingEnabled(v));

        if (btnSaveColor != null) btnSaveColor.setOnClickListener(v -> {
            if (etColorCfg == null) return;
            colors.saveRaw(etColorCfg.getText().toString());
            if (listener != null) listener.onThemeChanged();
            Toast.makeText(requireContext(), "Theme saved", Toast.LENGTH_SHORT).show();
        });
        if (btnSaveCountry != null) btnSaveCountry.setOnClickListener(v -> {
            if (etCountry == null) return;
            String c = etCountry.getText().toString().trim(); prefs.setRadioCountry(c); prefs.saveRadioCache("");
            Toast.makeText(requireContext(),"Set to \""+(c.isEmpty()?"Global":c)+"\". Go to Radio tab.",Toast.LENGTH_LONG).show();
        });
        if (btnFetch != null) btnFetch.setOnClickListener(v -> {
            if (etStationUrl==null) return;
            String url=etStationUrl.getText().toString().trim();
            if(url.isEmpty()){if(tvFetchStatus!=null)tvFetchStatus.setText("Enter a URL first");return;}
            if(tvFetchStatus!=null)tvFetchStatus.setText("Fetching…"); fetchAndMerge(url);
        });

        buildThemePresets();
        applyTheme(view);
    }

    // ═══════════════════════════════════════════════════════════════
    // 13 THEME PRESETS (all self-contained JSON strings)
    // ═══════════════════════════════════════════════════════════════

    private static final String[][] BUILTIN_THEMES = {
        {"Dark",    buildTheme("#0D0D14","#16161F","#1E1E2A","#6C63FF","#3D3880","#22223A","#FFFFFF","#8888AA","#44445A","#0D0D14","#44445A")},
        {"Light",   buildTheme("#F4F4F8","#FFFFFF","#EBEBF2","#5B52E8","#BDB9F7","#D8D8E8","#12121A","#666688","#AAAACC","#F4F4F8","#AAAACC")},
        {"Dusk",    buildTheme("#1A0F1F","#221528","#2C1A35","#E07AFF","#6A2E80","#3A2048","#F5E8FF","#AA88CC","#6A4080","#1A0F1F","#6A4080")},
        {"Ocean",   buildTheme("#080F1A","#0D1A2A","#122035","#1ECBE1","#0E5F6A","#183048","#E0F8FF","#6A9BB0","#2A5068","#080F1A","#2A5068")},
        {"Forest",  buildTheme("#0A1208","#121C10","#1A2818","#4CAF50","#2E6B30","#1E3020","#E8F5E9","#88AA88","#3A5A3A","#0A1208","#3A5A3A")},
        {"Sunset",  buildTheme("#1A0A00","#2A1200","#3A1E06","#FF7043","#8B3000","#3D1C0A","#FFF3E0","#FFAB80","#7A3010","#1A0A00","#7A3010")},
        {"Rose",    buildTheme("#18090E","#24111A","#301828","#F06292","#882244","#3A1828","#FFE4EC","#CC88AA","#7A3050","#18090E","#7A3050")},
        {"Slate",   buildTheme("#0C0D10","#141519","#1C1D22","#7986CB","#3D4A8A","#22232A","#E8EAF6","#7A80A0","#3A3D50","#0C0D10","#3A3D50")},
        {"Amber",   buildTheme("#110D00","#1C1500","#281E00","#FFB300","#7A5600","#302400","#FFF8E1","#CCAA55","#7A6020","#110D00","#7A6020")},
        {"Mint",    buildTheme("#081210","#10201E","#182E2C","#26A69A","#0E5E58","#1A3030","#E0F2F1","#70A8A4","#2A5050","#081210","#2A5050")},
        {"Mono",    buildTheme("#0A0A0A","#141414","#1E1E1E","#FFFFFF","#888888","#2A2A2A","#FFFFFF","#888888","#444444","#0A0A0A","#444444")},
        {"Neon",    buildTheme("#000814","#001028","#001840","#00F5FF","#004D6B","#002240","#E0FFFF","#00A0B0","#003040","#000814","#003040")},
        {"Crimson", buildTheme("#120408","#1E0810","#2A1018","#E53935","#8B1A1A","#301018","#FFE8E8","#CC8888","#7A2020","#120408","#7A2020")},
    };

    /** Build a minimal complete theme JSON for a given palette */
    private static String buildTheme(String bg, String card, String card2, String accent,
                                     String accentDim, String divider, String textPri,
                                     String textSec, String navUnsel, String navBg, String navLabelUnsel) {
        return "{\n" +
          "\"global\":{\"bg_primary\":\""+bg+"\",\"bg_card\":\""+card+"\",\"bg_card2\":\""+card2+"\"," +
          "\"accent\":\""+accent+"\",\"accent_dim\":\""+accentDim+"\",\"divider\":\""+divider+"\"," +
          "\"text_primary\":\""+textPri+"\",\"text_secondary\":\""+textSec+"\",\"text_section_title\":\""+textPri+"\"," +
          "\"icon_note_color\":\""+accent+"\",\"icon_note_vec_tint\":\""+accent+"\"},\n" +
          "\"nav\":{\"bg\":\""+navBg+"\",\"selected\":\""+textPri+"\",\"unselected\":\""+navUnsel+"\"," +
          "\"label_selected\":\""+textPri+"\",\"label_unselected\":\""+navLabelUnsel+"\"},\n" +
          "\"home\":{\"section_title\":\""+textPri+"\",\"card_bg\":\""+card+"\"," +
          "\"chip_playing_bg\":\""+accentDim+"\",\"chip_playing_border\":\""+accent+"\"," +
          "\"chip_text\":\""+textPri+"\",\"empty_text\":\""+navUnsel+"\",\"page_title\":\""+textPri+"\"," +
          "\"now_playing_anim\":\""+accent+"\",\"now_playing_card_bg\":\""+card2+"\"," +
          "\"now_playing_card_border\":\""+accent+"\",\"now_playing_icon_bg\":\""+accentDim+"\"," +
          "\"now_playing_icon_tint\":\""+accent+"\"},\n" +
          "\"radio\":{\"station_bg\":\""+card2+"\",\"station_bg_active\":\""+accentDim+"\"," +
          "\"station_border_active\":\""+accent+"\",\"station_text\":\""+textPri+"\"," +
          "\"station_text_active\":\""+accent+"\",\"station_click_glow\":\""+accent+"\"," +
          "\"eq_bar\":\""+accent+"\",\"group_name_text\":\""+accent+"\"," +
          "\"group_name_collapsed_text\":\""+textSec+"\",\"search_bg\":\""+card2+"\"," +
          "\"search_text\":\""+textPri+"\",\"search_hint\":\""+textSec+"\"," +
          "\"page_title\":\""+textPri+"\",\"checkbox_color\":\""+accent+"\"},\n" +
          "\"sounds\":{\"btn_bg\":\""+card+"\",\"btn_active_bg\":\""+accentDim+"\"," +
          "\"btn_border_active\":\""+accent+"\",\"btn_click_glow\":\""+accent+"\"," +
          "\"btn_text\":\""+textPri+"\",\"wave_color\":\""+accent+"\"," +
          "\"vol_bar_track\":\"#44FFFFFF\",\"vol_bar_fill\":\""+textPri+"\"," +
          "\"stop_all_bg\":\""+card2+"\",\"stop_all_border\":\""+accent+"\"," +
          "\"stop_all_text\":\""+textPri+"\"},\n" +
          "\"controls\":{\"stop_bg\":\""+card2+"\",\"stop_border\":\""+accent+"\"," +
          "\"stop_text\":\""+textPri+"\"},\n" +
          "\"settings\":{\"card_bg\":\""+card+"\",\"card_border\":\""+divider+"\"," +
          "\"label_text\":\""+textPri+"\",\"header_text\":\""+textSec+"\"," +
          "\"hint_text\":\""+textSec+"\",\"version_text\":\""+navUnsel+"\"," +
          "\"input_bg\":\""+card2+"\",\"input_border\":\""+divider+"\"," +
          "\"input_text\":\""+textPri+"\",\"btn_bg\":\""+card2+"\"," +
          "\"btn_border\":\""+accent+"\",\"btn_text\":\""+textPri+"\"," +
          "\"about_text\":\""+accent+"\",\"divider\":\""+divider+"\"," +
          "\"page_title\":\""+textPri+"\",\"switch_thumb_on\":\""+accent+"\"," +
          "\"switch_track_on\":\""+accentDim+"\",\"switch_thumb_off\":\""+textSec+"\"," +
          "\"switch_track_off\":\""+card2+"\",\"made_by_text\":\""+navUnsel+"\"," +
          "\"made_by_brand\":\""+accent+"\",\"tune_icon_color\":\""+accent+"\"},\n" +
          "\"notification\":{\"bg\":\""+card+"\",\"icon_bg\":\""+accent+"\"}\n}";
    }

    private void buildThemePresets() {
        if (themePresetsContainer == null) return;
        themePresetsContainer.removeAllViews();
        float dp = density();

        // Built-in presets
        for (String[] preset : BUILTIN_THEMES) {
            final String name = preset[0], json = preset[1];
            addPresetButton(themePresetsContainer, name, dp, false, () -> applyThemeJson(json, name));
        }

        // User-supplied themes from assets/themes/
        try {
            String[] assetThemes = requireContext().getAssets().list("themes");
            if (assetThemes != null) {
                for (String file : assetThemes) {
                    if (!file.endsWith(".json")) continue;
                    String themeName = SoundLoader.prettify(file.replace(".json", ""));
                    final String themeFile = file;
                    addPresetButton(themePresetsContainer, themeName, dp, false, () -> {
                        try {
                            BufferedReader br = new BufferedReader(new InputStreamReader(
                                    requireContext().getAssets().open("themes/" + themeFile)));
                            StringBuilder sb = new StringBuilder(); String line;
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

        // "Custom" button — last, toggles the JSON editor visibility
        addPresetButton(themePresetsContainer, "Custom ✎", dp, true, () -> {
            if (customEditorWrap != null) {
                boolean nowVisible = customEditorWrap.getVisibility() != View.VISIBLE;
                customEditorWrap.setVisibility(nowVisible ? View.VISIBLE : View.GONE);
                if (nowVisible && etColorCfg != null)
                    etColorCfg.setText(colors.readRaw());
            }
        });
    }

    private void addPresetButton(LinearLayout container, String label, float dp,
                                 boolean isCustom, Runnable action) {
        TextView btn = new TextView(requireContext());
        btn.setText(label);
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setTextSize(13f);
        btn.setPadding((int)(16*dp), (int)(10*dp), (int)(16*dp), (int)(10*dp));
        btn.setClickable(true); btn.setFocusable(true);
        if (isCustom) styleBtnAccent(btn); else styleBtn(btn);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, (int)(8*dp), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> action.run());
        container.addView(btn);
    }

    private void applyThemeJson(String json, String name) {
        colors.saveRaw(json);
        colors = new ColorConfig(requireContext());
        if (listener != null) listener.onThemeChanged();
        if (etColorCfg != null) etColorCfg.setText(colors.readRaw());
        applyTheme(getView());
        Toast.makeText(requireContext(), name + " theme applied", Toast.LENGTH_SHORT).show();
    }

    // ── Network ──────────────────────────────────────────────────
    private void fetchAndMerge(String url) {
        new OkHttpClient().newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call c, IOException e) {
                requireActivity().runOnUiThread(()->{ if(tvFetchStatus!=null) tvFetchStatus.setText("Failed: "+e.getMessage()); });
            }
            @Override public void onResponse(Call c, Response r) throws IOException {
                if (!r.isSuccessful()) { requireActivity().runOnUiThread(()->{ if(tvFetchStatus!=null)tvFetchStatus.setText("HTTP "+r.code()); }); return; }
                String body = r.body()!=null?r.body().string():"";
                try { java.io.File f=new java.io.File(requireContext().getFilesDir(),"radio_extra.cfg"); java.io.FileWriter fw=new java.io.FileWriter(f,true); fw.write("\n"+body); fw.close(); } catch(Exception ignored){}
                requireActivity().runOnUiThread(()->{ if(tvFetchStatus!=null)tvFetchStatus.setText("Done. Go to Radio tab."); });
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

        // Tint ic_note_vec icon
        if (ivSettingsIcon != null) {
            ivSettingsIcon.setColorFilter(colors.iconNoteVecTint(), PorterDuff.Mode.SRC_IN);
        }

        styleCard(cardToggles); styleCard(cardTheme); styleCard(cardStations); styleCard(cardAdvanced);

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

        // Restyle preset buttons
        if (themePresetsContainer != null) {
            for (int i = 0; i < themePresetsContainer.getChildCount(); i++) {
                View child = themePresetsContainer.getChildAt(i);
                if (!(child instanceof TextView)) continue;
                TextView tv = (TextView) child;
                boolean isLast = i == themePresetsContainer.getChildCount() - 1;
                if (isLast) styleBtnAccent(tv); else styleBtn(tv);
            }
        }

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

    /** Accent-filled variant for the Custom button */
    private void styleBtnAccent(TextView btn) {
        if (btn == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(10*dp);
        gd.setColor(colors.accentDim()); gd.setStroke((int)(1.5f*dp), colors.accent());
        btn.setBackground(gd); btn.setTextColor(colors.accent());
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

    // Expose SoundLoader.prettify for theme file names
    private static class SoundLoader { static String prettify(String s) { if(s==null||s.isEmpty()) return s; return Character.toUpperCase(s.charAt(0))+s.substring(1); } }
}
