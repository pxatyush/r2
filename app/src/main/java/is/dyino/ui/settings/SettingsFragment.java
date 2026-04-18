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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import is.dyino.R;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;

public class SettingsFragment extends Fragment {

    private AppPrefs    prefs;
    private ColorConfig colors;

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

    // ── Dynamically injected controls (stored for re-theming) ─────
    private SwitchCompat swWaveNotif, swPowerSaving;
    private LinearLayout navBtnsRow;
    // Labels for extra toggles (need re-theming when theme changes)
    private TextView tvWaveNotifTitle, tvWaveNotifSub;
    private TextView tvPowerSavingTitle, tvPowerSavingSub;
    private TextView tvNavPosTitle;

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

        if (swHaptic   != null) swHaptic.setChecked(prefs.isHapticEnabled());
        if (swBtnSound != null) swBtnSound.setChecked(prefs.isButtonSoundEnabled());
        if (swPersistent!=null) swPersistent.setChecked(prefs.isPersistentPlayingEnabled());
        if (etColorCfg != null) etColorCfg.setText(colors.readRaw());
        if (etCountry  != null) etCountry.setText(prefs.getRadioCountry());

        if (swHaptic   != null) swHaptic.setOnCheckedChangeListener((b,v)->prefs.setHapticEnabled(v));
        if (swBtnSound != null) swBtnSound.setOnCheckedChangeListener((b,v)->{prefs.setButtonSoundEnabled(v);if(listener!=null)listener.onButtonSoundChanged(v);});
        if (swPersistent!=null) swPersistent.setOnCheckedChangeListener((b,v)->prefs.setPersistentPlayingEnabled(v));

        if (btnSaveColor!=null) btnSaveColor.setOnClickListener(v->{
            if(etColorCfg==null)return;
            colors.saveRaw(etColorCfg.getText().toString());
            prefs.setActiveThemeName("Custom");
            if(listener!=null)listener.onThemeChanged();
            colors=new ColorConfig(requireContext());
            applyTheme(getView());
            buildThemePresets();
            // No toast — theme change is visually obvious
        });

        if (btnSaveCountry!=null) btnSaveCountry.setOnClickListener(v->{
            if(etCountry==null)return;
            String c=etCountry.getText().toString().trim();
            prefs.setRadioCountry(c);
            prefs.saveRadioCache(""); // Invalidate cache so new country loads fresh
            android.widget.Toast.makeText(requireContext(),
                "Country set to \"" + (c.isEmpty()?"Global":c) + "\". Go to Radio tab.",
                android.widget.Toast.LENGTH_LONG).show();
        });

        injectExtraToggles();
        buildThemePresets();
        applyTheme(view);
    }

    // ── Inject extra toggles ──────────────────────────────────────
    private void injectExtraToggles() {
        if (cardToggles == null) return;
        float dp = density();

        // ── Wave Notification Visualiser ──────────────────────────
        addDivider(cardToggles, dp);
        LinearLayout wRow = new LinearLayout(requireContext());
        wRow.setOrientation(LinearLayout.HORIZONTAL);
        wRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        wRow.setPadding((int)(16*dp),0,(int)(12*dp),0);
        wRow.setMinimumHeight((int)(56*dp));

        LinearLayout wText = new LinearLayout(requireContext()); wText.setOrientation(LinearLayout.VERTICAL);
        wText.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        wText.setPadding(0,(int)(10*dp),0,(int)(10*dp));

        tvWaveNotifTitle = new TextView(requireContext());
        tvWaveNotifTitle.setText("Wave Notification Icon");
        tvWaveNotifTitle.setTextSize(14f); wText.addView(tvWaveNotifTitle);

        tvWaveNotifSub = new TextView(requireContext());
        tvWaveNotifSub.setText("Animated wave graphic in media notification");
        tvWaveNotifSub.setTextSize(12f);
        tvWaveNotifSub.setPadding(0,(int)(2*dp),0,0); wText.addView(tvWaveNotifSub);
        wRow.addView(wText);

        swWaveNotif = new SwitchCompat(requireContext());
        swWaveNotif.setChecked(prefs.isWaveNotifEnabled());
        swWaveNotif.setOnCheckedChangeListener((b,v)->prefs.setWaveNotifEnabled(v));
        wRow.addView(swWaveNotif);
        cardToggles.addView(wRow);

        // ── Power Saving ──────────────────────────────────────────
        addDivider(cardToggles, dp);
        LinearLayout psRow = new LinearLayout(requireContext());
        psRow.setOrientation(LinearLayout.HORIZONTAL);
        psRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        psRow.setPadding((int)(16*dp),0,(int)(12*dp),0);
        psRow.setMinimumHeight((int)(56*dp));

        LinearLayout psText = new LinearLayout(requireContext()); psText.setOrientation(LinearLayout.VERTICAL);
        psText.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        psText.setPadding(0,(int)(10*dp),0,(int)(10*dp));

        tvPowerSavingTitle = new TextView(requireContext());
        tvPowerSavingTitle.setText("Power Saving Mode");
        tvPowerSavingTitle.setTextSize(14f); psText.addView(tvPowerSavingTitle);

        tvPowerSavingSub = new TextView(requireContext());
        tvPowerSavingSub.setText("Disables waves & visualizer to save battery");
        tvPowerSavingSub.setTextSize(12f);
        tvPowerSavingSub.setPadding(0,(int)(2*dp),0,0); psText.addView(tvPowerSavingSub);
        psRow.addView(psText);

        swPowerSaving = new SwitchCompat(requireContext());
        swPowerSaving.setChecked(prefs.isPowerSavingEnabled());
        swPowerSaving.setOnCheckedChangeListener((b,v)->prefs.setPowerSavingEnabled(v));
        psRow.addView(swPowerSaving);
        cardToggles.addView(psRow);

        // ── Nav position ──────────────────────────────────────────
        addDivider(cardToggles, dp);
        LinearLayout navSection = new LinearLayout(requireContext());
        navSection.setOrientation(LinearLayout.VERTICAL);
        navSection.setPadding((int)(16*dp),(int)(12*dp),(int)(12*dp),(int)(12*dp));

        tvNavPosTitle = new TextView(requireContext());
        tvNavPosTitle.setText("Navigation Position");
        tvNavPosTitle.setTextSize(14f);
        navSection.addView(tvNavPosTitle);

        navBtnsRow = new LinearLayout(requireContext());
        navBtnsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams nblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nblp.setMargins(0,(int)(8*dp),0,0); navBtnsRow.setLayoutParams(nblp);
        rebuildNavBtns(dp);
        navSection.addView(navBtnsRow);
        cardToggles.addView(navSection);
    }

    private void rebuildNavBtns(float dp) {
        if (navBtnsRow == null) return;
        navBtnsRow.removeAllViews();
        String current = prefs.getNavPosition();
        String[][] opts = {{"Left","left"},{"Right","right"},{"Bottom","bottom"}};
        for (String[] opt : opts) {
            String lbl=opt[0], val=opt[1];
            TextView btn = new TextView(requireContext());
            btn.setText(lbl); btn.setGravity(android.view.Gravity.CENTER); btn.setTextSize(13f);
            btn.setPadding((int)(14*dp),(int)(9*dp),(int)(14*dp),(int)(9*dp));
            btn.setClickable(true); btn.setFocusable(true);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
            blp.setMargins(0,0,(int)(6*dp),0); btn.setLayoutParams(blp);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(8*dp);
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

    private void addDivider(LinearLayout parent, float dp) {
        View div = new View(requireContext()); div.setBackgroundColor(colors.divider());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1);
        lp.setMargins((int)(16*dp),0,0,0); div.setLayoutParams(lp); parent.addView(div);
    }

    // ── 30 Themes ─────────────────────────────────────────────────
    private static final String[][] THEMES = {
      {"Dark",         "#0D0D14","#16161F","#1E1E2A","#6C63FF","#3D3880","#22223A","#FFFFFF","#8888AA","#44445A","#0D0D14"},
      {"AMOLED",       "#000000","#0A0A0A","#111111","#6C63FF","#2A2880","#1A1A1A","#FFFFFF","#888888","#333333","#000000"},
      {"AMOLED Purple","#000000","#0A0008","#110012","#BB86FC","#6200EA","#1E001E","#FFFFFF","#CC99FF","#440044","#000000"},
      {"AMOLED Blue",  "#000000","#000A14","#001020","#0AF0FF","#003A50","#001830","#FFFFFF","#6AAABB","#003344","#000000"},
      {"AMOLED Green", "#000000","#001208","#001A08","#00E676","#007A3A","#001E10","#FFFFFF","#66BB88","#003018","#000000"},
      {"Light",        "#F4F4F8","#FFFFFF","#EBEBF2","#5B52E8","#BDB9F7","#D8D8E8","#12121A","#666688","#AAAACC","#F4F4F8"},
      {"Day Blue",     "#EEF4FF","#FFFFFF","#DCE8FF","#2979FF","#82B1FF","#BBCCE8","#0D1A33","#5577AA","#9AADCC","#EEF4FF"},
      {"Day Green",    "#EDFAF4","#FFFFFF","#DAFCE9","#00897B","#80CBC4","#B2DFDB","#0D2018","#447766","#99BBAA","#EDFAF4"},
      {"Cream",        "#FAF7F0","#FFFFFF","#F5EED8","#B8860B","#F0D080","#E0D0A0","#1A1208","#887744","#CCBB88","#FAF7F0"},
      {"Paper",        "#F2EDE8","#FFFFFF","#EDE5DA","#8B5E3C","#D4A27A","#D5C8B8","#1A0E06","#886644","#C8A88A","#F2EDE8"},
      {"Dusk",         "#1A0F1F","#221528","#2C1A35","#E07AFF","#6A2E80","#3A2048","#F5E8FF","#AA88CC","#6A4080","#1A0F1F"},
      {"Ocean",        "#080F1A","#0D1A2A","#122035","#1ECBE1","#0E5F6A","#183048","#E0F8FF","#6A9BB0","#2A5068","#080F1A"},
      {"Forest",       "#0A1208","#121C10","#1A2818","#4CAF50","#2E6B30","#1E3020","#E8F5E9","#88AA88","#3A5A3A","#0A1208"},
      {"Sunset",       "#1A0A00","#2A1200","#3A1E06","#FF7043","#8B3000","#3D1C0A","#FFF3E0","#FFAB80","#7A3010","#1A0A00"},
      {"Rose",         "#18090E","#24111A","#301828","#F06292","#882244","#3A1828","#FFE4EC","#CC88AA","#7A3050","#18090E"},
      {"Slate",        "#0C0D10","#141519","#1C1D22","#7986CB","#3D4A8A","#22232A","#E8EAF6","#7A80A0","#3A3D50","#0C0D10"},
      {"Amber",        "#110D00","#1C1500","#281E00","#FFB300","#7A5600","#302400","#FFF8E1","#CCAA55","#7A6020","#110D00"},
      {"Mint",         "#081210","#10201E","#182E2C","#26A69A","#0E5E58","#1A3030","#E0F2F1","#70A8A4","#2A5050","#081210"},
      {"Mono",         "#0A0A0A","#141414","#1E1E1E","#FFFFFF","#888888","#2A2A2A","#FFFFFF","#888888","#444444","#0A0A0A"},
      {"Neon",         "#000814","#001028","#001840","#00F5FF","#004D6B","#002240","#E0FFFF","#00A0B0","#003040","#000814"},
      {"Crimson",      "#120408","#1E0810","#2A1018","#E53935","#8B1A1A","#301018","#FFE8E8","#CC8888","#7A2020","#120408"},
      {"Midnight",     "#080818","#0F102A","#161830","#4FC3F7","#1565C0","#1A1E38","#E3F2FD","#6A9BC0","#2A3060","#080818"},
      {"Sand",         "#1A1208","#261C10","#342818","#F4A460","#8B5E1A","#3A2C1A","#FFF5E6","#CCAA77","#7A5030","#1A1208"},
      {"Grape",        "#120818","#1E1028","#2A1838","#CE93D8","#6A1B9A","#301848","#F3E5F5","#BB88CC","#6A3888","#120818"},
      {"Dracula",      "#282A36","#44475A","#21222C","#BD93F9","#6272A4","#44475A","#F8F8F2","#6272A4","#44475A","#282A36"},
      {"Solarized",    "#002B36","#073642","#002B36","#268BD2","#586E75","#073642","#EEE8D5","#657B83","#586E75","#002B36"},
      {"Nord",         "#2E3440","#3B4252","#434C5E","#88C0D0","#5E81AC","#4C566A","#ECEFF4","#D8DEE9","#4C566A","#2E3440"},
      {"Material You", "#1C1B1F","#2B2930","#37353D","#D0BCFF","#6650A4","#48464F","#E6E1E5","#CAC4D0","#49454E","#1C1B1F"},
      {"Catppuccin",   "#1E1E2E","#181825","#313244","#CBA6F7","#45475A","#45475A","#CDD6F4","#A6ADC8","#6C7086","#1E1E2E"},
      {"Aurora",       "#060C14","#0A1522","#0F1E30","#69FF47","#1B7A00","#102218","#E8FFE0","#66AA66","#204A28","#060C14"},
    };

    private String buildJson(String[] t) {
        String bg=t[1],card=t[2],c2=t[3],acc=t[4],accD=t[5],div=t[6],tP=t[7],tS=t[8],nU=t[9],nBg=t[10];
        return "{\"global\":{\"bg_primary\":\""+bg+"\",\"bg_card\":\""+card+"\",\"bg_card2\":\""+c2+"\"," +
               "\"accent\":\""+acc+"\",\"accent_dim\":\""+accD+"\",\"divider\":\""+div+"\"," +
               "\"text_primary\":\""+tP+"\",\"text_secondary\":\""+tS+"\",\"text_section_title\":\""+tP+"\"," +
               "\"icon_note_vec_tint\":\""+acc+"\",\"page_header_text\":\""+tP+"\"," +
               "\"page_header_subtitle_text\":\""+tS+"\"}," +
               "\"nav\":{\"bg\":\""+nBg+"\",\"label_selected\":\""+tP+"\",\"label_unselected\":\""+nU+"\"}," +
               "\"home\":{\"section_title\":\""+tP+"\",\"chip_playing_bg\":\""+accD+"\"," +
               "\"chip_playing_border\":\""+acc+"\",\"chip_text\":\""+tP+"\",\"empty_text\":\""+nU+"\"," +
               "\"now_playing_anim\":\""+acc+"\",\"now_playing_card_bg\":\""+c2+"\"," +
               "\"now_playing_card_border\":\""+acc+"\",\"now_playing_icon_tint\":\""+acc+"\"," +
               "\"visualizer_bg\":\""+bg+"\",\"visualizer_bar\":\""+acc+"\"}," +
               "\"radio\":{\"station_bg\":\""+c2+"\",\"station_bg_active\":\""+accD+"\"," +
               "\"station_border_active\":\""+acc+"\",\"station_text\":\""+tP+"\"," +
               "\"station_text_active\":\""+acc+"\",\"station_click_glow\":\""+acc+"\"," +
               "\"eq_bar\":\""+acc+"\",\"group_header_bg\":\""+card+"\",\"group_header_border\":\""+div+"\"," +
               "\"group_name_text\":\""+acc+"\",\"group_name_collapsed_text\":\""+tS+"\"," +
               "\"group_badge_bg\":\""+accD+"\",\"group_badge_text\":\""+acc+"\"," +
               "\"station_card_bg\":\""+c2+"\",\"station_card_border\":\""+div+"\"," +
               "\"search_bg\":\""+c2+"\",\"search_text\":\""+tP+"\",\"search_hint\":\""+tS+"\"," +
               "\"checkbox_color\":\""+acc+"\"}," +
               "\"sounds\":{\"btn_bg\":\""+card+"\",\"btn_active_bg\":\""+accD+"\"," +
               "\"btn_border_active\":\""+acc+"\",\"btn_text\":\""+tP+"\"," +
               "\"wave_color\":\""+acc+"\",\"stop_all_bg\":\""+c2+"\"," +
               "\"stop_all_border\":\""+acc+"\",\"stop_all_text\":\""+tP+"\"}," +
               "\"settings\":{\"card_bg\":\""+card+"\",\"card_border\":\""+div+"\"," +
               "\"label_text\":\""+tP+"\",\"hint_text\":\""+tS+"\",\"version_text\":\""+nU+"\"," +
               "\"input_bg\":\""+c2+"\",\"input_border\":\""+div+"\",\"input_text\":\""+tP+"\"," +
               "\"btn_bg\":\""+c2+"\",\"btn_border\":\""+acc+"\",\"btn_text\":\""+tP+"\"," +
               "\"divider\":\""+div+"\",\"switch_thumb_on\":\""+acc+"\",\"switch_track_on\":\""+accD+"\"," +
               "\"switch_thumb_off\":\""+tS+"\",\"switch_track_off\":\""+c2+"\"," +
               "\"made_by_text\":\""+nU+"\",\"made_by_brand\":\""+acc+"\"}," +
               "\"notification\":{\"icon_bg\":\""+acc+"\"}}";
    }

    private void buildThemePresets() {
        if (themePresetsContainer == null) return;
        themePresetsContainer.removeAllViews();
        String active = prefs.getActiveThemeName();
        float dp = density();

        for (String[] t : THEMES) {
            final String name=t[0], json=buildJson(t);
            addPresetBtn(name, dp, false, name.equals(active), ()->applyThemeJson(json,name));
        }

        try {
            String[] assetThemes = requireContext().getAssets().list("themes");
            if (assetThemes != null) for (String file : assetThemes) {
                if (!file.endsWith(".json")) continue;
                String name = cap(file.replace(".json",""));
                final String fname=file;
                addPresetBtn(name,dp,false,name.equals(active),()->{
                    try{BufferedReader br=new BufferedReader(new InputStreamReader(requireContext().getAssets().open("themes/"+fname)));
                        StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line).append('\n');br.close();applyThemeJson(sb.toString(),name);}
                    catch(Exception e){android.widget.Toast.makeText(requireContext(),"Failed",android.widget.Toast.LENGTH_SHORT).show();}
                });
            }
        } catch (Exception ignored) {}

        addPresetBtn("Custom ✎", dp, true, "Custom".equals(active), ()->{
            if(customEditorWrap!=null){
                boolean show=customEditorWrap.getVisibility()!=View.VISIBLE;
                customEditorWrap.setVisibility(show?View.VISIBLE:View.GONE);
                if(show&&etColorCfg!=null)etColorCfg.setText(colors.readRaw());
            }
        });
    }

    private void addPresetBtn(String label, float dp, boolean isCustom, boolean isActive, Runnable action) {
        TextView btn = new TextView(requireContext());
        btn.setText(label); btn.setGravity(android.view.Gravity.CENTER); btn.setTextSize(13f);
        btn.setPadding((int)(16*dp),(int)(10*dp),(int)(16*dp),(int)(10*dp));
        btn.setClickable(true); btn.setFocusable(true);
        if (isActive) styleBtnActive(btn); else if (isCustom) styleBtnAccent(btn); else styleBtn(btn);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,(int)(8*dp),0); btn.setLayoutParams(lp);
        btn.setOnClickListener(v->action.run());
        themePresetsContainer.addView(btn);
    }

    private void applyThemeJson(String json, String name) {
        colors.saveRaw(json); prefs.setActiveThemeName(name);
        colors = new ColorConfig(requireContext());
        applyTheme(getView());
        buildThemePresets();
        if (listener != null) listener.onThemeChanged();
        if (etColorCfg != null) etColorCfg.setText(colors.readRaw());
        // No toast — the visual change is the confirmation
    }

    // ── Theming ───────────────────────────────────────────────────
    public void applyTheme(View root) {
        if (root == null || colors == null) return;
        root.setBackgroundColor(colors.bgPrimary());
        if (tvSettingsTitle    != null) tvSettingsTitle.setTextColor(colors.pageHeaderText());
        if (tvSettingsSubtitle != null) tvSettingsSubtitle.setTextColor(colors.pageHeaderSubtitleText());
        if (settingsDivider    != null) settingsDivider.setBackgroundColor(colors.settingsDivider());

        styleCard(cardToggles); styleCard(cardTheme); styleCard(cardStations);

        lbl(root.findViewById(R.id.tvToggleHeader));
        lbl(root.findViewById(R.id.tvHapticLabel));
        lbl(root.findViewById(R.id.tvBtnSoundLabel));
        lbl(root.findViewById(R.id.tvPersistentLabel));
        sub(root.findViewById(R.id.tvPersistentSub));
        lbl(root.findViewById(R.id.tvThemePresetsHeader));
        lbl(root.findViewById(R.id.tvThemeHeader));
        lbl(root.findViewById(R.id.tvCountryHeader));
        if (tvCountryNote != null) tvCountryNote.setTextColor(colors.textSettingsHint());

        styleInput(etColorCfg); styleInput(etCountry);
        styleBtn(btnSaveColor); styleBtn(btnSaveCountry);
        buildThemePresets();

        if (tvVersion != null) tvVersion.setTextColor(colors.textSettingsVersion());
        if (tvMadeBy  != null) applyMadeByColors();
        styleSwitches();

        // Re-theme dynamically-injected extra toggle labels
        if (tvWaveNotifTitle  != null) tvWaveNotifTitle.setTextColor(colors.textSettingsLabel());
        if (tvWaveNotifSub    != null) tvWaveNotifSub.setTextColor(colors.textSettingsHint());
        if (tvPowerSavingTitle != null) tvPowerSavingTitle.setTextColor(colors.textSettingsLabel());
        if (tvPowerSavingSub  != null) tvPowerSavingSub.setTextColor(colors.textSettingsHint());
        if (tvNavPosTitle     != null) tvNavPosTitle.setTextColor(colors.textSettingsLabel());
        if (navBtnsRow        != null) rebuildNavBtns(density());
    }

    private void styleSwitches() {
        ColorStateList thumb=new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{colors.settingsSwitchThumbOn(),colors.settingsSwitchThumbOff()});
        ColorStateList track=new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{colors.settingsSwitchTrackOn(),colors.settingsSwitchTrackOff()});
        for(SwitchCompat sw:new SwitchCompat[]{swHaptic,swBtnSound,swPersistent,swWaveNotif,swPowerSaving}){
            if(sw==null)continue; sw.setThumbTintList(thumb); sw.setTrackTintList(track);
        }
    }

    private void styleCard(View card){if(card==null)return;float dp=density();GradientDrawable gd=new GradientDrawable();gd.setShape(GradientDrawable.RECTANGLE);gd.setCornerRadius(12*dp);gd.setColor(colors.bgSettingsCard());gd.setStroke((int)(1*dp),colors.settingsCardBorder());card.setBackground(gd);}
    private void styleInput(EditText et){if(et==null)return;float dp=density();GradientDrawable gd=new GradientDrawable();gd.setShape(GradientDrawable.RECTANGLE);gd.setCornerRadius(8*dp);gd.setColor(colors.settingsInputBg());gd.setStroke((int)(1*dp),colors.settingsInputBorder());et.setBackground(gd);et.setTextColor(colors.settingsInputText());et.setHintTextColor(colors.textSettingsHint());}
    private void styleBtn(TextView btn){if(btn==null)return;float dp=density();GradientDrawable gd=new GradientDrawable();gd.setShape(GradientDrawable.RECTANGLE);gd.setCornerRadius(10*dp);gd.setColor(colors.settingsBtnBg());gd.setStroke((int)(1.5f*dp),colors.settingsBtnBorder());btn.setBackground(gd);btn.setTextColor(colors.settingsBtnText());}
    private void styleBtnActive(TextView btn){if(btn==null)return;float dp=density();GradientDrawable gd=new GradientDrawable();gd.setShape(GradientDrawable.RECTANGLE);gd.setCornerRadius(10*dp);gd.setColor(colors.accent());gd.setStroke((int)(2f*dp),colors.accent());btn.setBackground(gd);btn.setTextColor(isLight(colors.accent())?0xFF111111:0xFFFFFFFF);}
    private void styleBtnAccent(TextView btn){if(btn==null)return;float dp=density();GradientDrawable gd=new GradientDrawable();gd.setShape(GradientDrawable.RECTANGLE);gd.setCornerRadius(10*dp);gd.setColor(colors.accentDim());gd.setStroke((int)(1.5f*dp),colors.accent());btn.setBackground(gd);btn.setTextColor(colors.accent());}
    private static boolean isLight(int c){double r=((c>>16)&0xFF)/255.0,g=((c>>8)&0xFF)/255.0,b=(c&0xFF)/255.0;return(0.299*r+0.587*g+0.114*b)>0.5;}
    private void lbl(View v){if(v instanceof TextView)((TextView)v).setTextColor(colors.textSettingsLabel());}
    private void sub(View v){if(v instanceof TextView)((TextView)v).setTextColor(colors.textSettingsHint());}
    private void applyMadeByColors(){if(tvMadeBy==null)return;android.text.SpannableString ss=new android.text.SpannableString("Made by pxatyush");ss.setSpan(new android.text.style.ForegroundColorSpan(colors.settingsMadeByText()),0,8,android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);ss.setSpan(new android.text.style.ForegroundColorSpan(colors.settingsMadeByBrand()),8,ss.length(),android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);tvMadeBy.setText(ss);}
    private float density(){return getResources().getDisplayMetrics().density;}
    private static String cap(String s){return(s==null||s.isEmpty())?s:Character.toUpperCase(s.charAt(0))+s.substring(1);}
}