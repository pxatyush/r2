package is.dyino.ui.settings;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import is.dyino.R;
import is.dyino.ui.home.AudioVisualizerView;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;
import is.dyino.util.RadioLoader;

public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";

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
    private SwitchCompat swHaptic, swBtnSound;
    private EditText     etColorCfg;
    private TextView     btnSaveColor;
    private TextView     tvVersion, tvMadeBy, tvSettingsTitle, tvSettingsSubtitle;
    private LinearLayout cardToggles, cardTheme, cardStations;
    private LinearLayout themePresetsContainer;
    private LinearLayout customEditorWrap;
    private View         settingsDivider;

    // ── Dynamically injected controls ─────────────────────────────
    private LinearLayout navBtnsRow;
    private TextView     tvNavPosTitle;
    // Visualizer preset selector
    private LinearLayout visPresetCard;
    private LinearLayout visPresetContainer;
    private TextView     tvVisHeader;
    // Country card
    private TextView     tvCountryHeader, tvCurrentCountry, btnChangeCountry, tvCountryNote;
    // Power saving (kept, just no Wave Notif or Persistent)
    private SwitchCompat swPowerSaving;
    private TextView     tvPowerSavingTitle, tvPowerSavingSub;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_settings, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs  = new AppPrefs(requireContext());
        colors = new ColorConfig(requireContext());

        tvSettingsTitle    = view.findViewById(R.id.tvSettingsTitle);
        tvSettingsSubtitle = view.findViewById(R.id.tvSettingsSubtitle);
        settingsDivider    = view.findViewById(R.id.settingsDivider);
        cardToggles        = view.findViewById(R.id.cardToggles);
        cardTheme          = view.findViewById(R.id.cardTheme);
        cardStations       = view.findViewById(R.id.cardStations);
        themePresetsContainer = view.findViewById(R.id.themePresetsContainer);
        customEditorWrap   = view.findViewById(R.id.customEditorWrap);
        swHaptic           = view.findViewById(R.id.switchHaptic);
        swBtnSound         = view.findViewById(R.id.switchButtonSound);
        etColorCfg         = view.findViewById(R.id.etColorCfg);
        btnSaveColor       = view.findViewById(R.id.btnSaveColors);
        tvMadeBy           = view.findViewById(R.id.tvMadeBy);
        tvVersion          = view.findViewById(R.id.tvVersion);

        if (swHaptic   != null) swHaptic.setChecked(prefs.isHapticEnabled());
        if (swBtnSound != null) swBtnSound.setChecked(prefs.isButtonSoundEnabled());
        if (etColorCfg != null) etColorCfg.setText(colors.readRaw());

        if (swHaptic   != null) swHaptic.setOnCheckedChangeListener((b,v) -> prefs.setHapticEnabled(v));
        if (swBtnSound != null) swBtnSound.setOnCheckedChangeListener((b,v) -> {
            prefs.setButtonSoundEnabled(v); if (listener!=null) listener.onButtonSoundChanged(v);
        });

        if (btnSaveColor != null) btnSaveColor.setOnClickListener(v -> {
            if (etColorCfg==null) return;
            colors.saveRaw(etColorCfg.getText().toString());
            prefs.setActiveThemeName("Custom");
            colors = new ColorConfig(requireContext());
            applyTheme(getView()); buildThemePresets();
            if (listener!=null) listener.onThemeChanged();
        });

        buildCountryCard();
        injectExtraControls();
        buildThemePresets();
        buildVisualizerPresets();
        applyTheme(view);
    }

    // ── Country card ──────────────────────────────────────────────

    private void buildCountryCard() {
        if (cardStations==null) return;
        cardStations.removeAllViews();
        float dp = density();

        tvCountryHeader = new TextView(requireContext());
        tvCountryHeader.setText("RADIO COUNTRY"); tvCountryHeader.setTextSize(11f); tvCountryHeader.setLetterSpacing(0.1f);
        tvCountryHeader.setPadding(0,0,0,(int)(10*dp)); cardStations.addView(tvCountryHeader);

        LinearLayout row = new LinearLayout(requireContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        tvCurrentCountry = new TextView(requireContext()); tvCurrentCountry.setTextSize(15f);
        tvCurrentCountry.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String cc = prefs.getRadioCountry(); tvCurrentCountry.setText(cc.isEmpty() ? "Global (all stations)" : cc);
        row.addView(tvCurrentCountry);
        btnChangeCountry = new TextView(requireContext()); btnChangeCountry.setText("Change"); btnChangeCountry.setTextSize(13f);
        btnChangeCountry.setGravity(Gravity.CENTER); btnChangeCountry.setPadding((int)(14*dp),(int)(8*dp),(int)(14*dp),(int)(8*dp));
        btnChangeCountry.setClickable(true); btnChangeCountry.setFocusable(true); row.addView(btnChangeCountry);
        cardStations.addView(row);
        tvCountryNote = new TextView(requireContext()); tvCountryNote.setText("Radio list is cached and refreshed weekly."); tvCountryNote.setTextSize(12f); tvCountryNote.setPadding(0,(int)(6*dp),0,0); cardStations.addView(tvCountryNote);
        btnChangeCountry.setOnClickListener(v -> showCountryPicker());
    }

    private void refreshCountryDisplay() {
        String cc = prefs.getRadioCountry();
        if (tvCurrentCountry!=null) tvCurrentCountry.setText(cc.isEmpty() ? "Global (all stations)" : cc);
    }

    private void showCountryPicker() {
        float dp=density(); int screenW=getResources().getDisplayMetrics().widthPixels, screenH=getResources().getDisplayMetrics().heightPixels;
        LinearLayout root=new LinearLayout(requireContext()); root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rootBg=new GradientDrawable(); rootBg.setShape(GradientDrawable.RECTANGLE); rootBg.setCornerRadius(16*dp); rootBg.setColor(colors.bgCard()); root.setBackground(rootBg); root.setClipToOutline(true);

        TextView tvTitle=new TextView(requireContext()); tvTitle.setText("Select Country"); tvTitle.setTextColor(colors.textPrimary()); tvTitle.setTextSize(18f);
        tvTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)); tvTitle.setPadding((int)(20*dp),(int)(18*dp),(int)(20*dp),(int)(2*dp)); root.addView(tvTitle);

        EditText etFilter=new EditText(requireContext()); etFilter.setHint("Search countries…"); etFilter.setTextColor(colors.textPrimary()); etFilter.setHintTextColor(colors.textSecondary()); etFilter.setTextSize(14f); etFilter.setSingleLine(true); etFilter.setPadding((int)(14*dp),(int)(10*dp),(int)(14*dp),(int)(10*dp));
        GradientDrawable sBg=new GradientDrawable(); sBg.setShape(GradientDrawable.RECTANGLE); sBg.setCornerRadius(10*dp); sBg.setColor(colors.bgCard2()); sBg.setStroke((int)(1*dp),colors.divider()); etFilter.setBackground(sBg);
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); slp.setMargins((int)(16*dp),0,(int)(16*dp),(int)(8*dp)); etFilter.setLayoutParams(slp); root.addView(etFilter);

        TextView tvLoading=new TextView(requireContext()); tvLoading.setText("Loading countries…"); tvLoading.setTextColor(colors.textSecondary()); tvLoading.setGravity(Gravity.CENTER); tvLoading.setPadding(0,(int)(32*dp),0,(int)(32*dp)); root.addView(tvLoading);

        RecyclerView rv=new RecyclerView(requireContext()); rv.setLayoutManager(new LinearLayoutManager(requireContext())); rv.setOverScrollMode(View.OVER_SCROLL_NEVER); rv.setVisibility(View.GONE); rv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f)); root.addView(rv);

        View div=new View(requireContext()); div.setBackgroundColor(colors.divider()); div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1)); root.addView(div);
        LinearLayout btnRow=new LinearLayout(requireContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnGlobal=makeTextBtn("Use Global",false), btnConfirm=makeTextBtn("Confirm",true);
        btnGlobal.setLayoutParams(new LinearLayout.LayoutParams(0,(int)(48*dp),1f));
        View bd=new View(requireContext()); bd.setBackgroundColor(colors.divider()); bd.setLayoutParams(new LinearLayout.LayoutParams(1,ViewGroup.LayoutParams.MATCH_PARENT)); btnRow.addView(btnGlobal); btnRow.addView(bd);
        btnConfirm.setLayoutParams(new LinearLayout.LayoutParams(0,(int)(48*dp),1f)); btnRow.addView(btnConfirm); root.addView(btnRow);

        AlertDialog dialog=new AlertDialog.Builder(requireContext()).setView(root).setCancelable(true).create();
        if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show(); if(dialog.getWindow()!=null)dialog.getWindow().setLayout((int)(screenW*0.90f),(int)(screenH*0.72f));

        final List<RadioLoader.CountryItem> allItems=new ArrayList<>();
        final String[] selected={null};
        String deviceIso=Locale.getDefault().getCountry().toUpperCase();

        btnGlobal.setOnClickListener(v->{prefs.setRadioCountry("");prefs.saveRadioCache("");dialog.dismiss();refreshCountryDisplay();Toast.makeText(requireContext(),"Set to Global",Toast.LENGTH_SHORT).show();});
        btnConfirm.setOnClickListener(v->{String c=selected[0];prefs.setRadioCountry(c!=null?c:"");prefs.saveRadioCache("");dialog.dismiss();refreshCountryDisplay();Toast.makeText(requireContext(),(c!=null?c:"Global")+" — go to Radio tab.",Toast.LENGTH_SHORT).show();});

        RadioLoader.loadCountries(new RadioLoader.CountriesCallback(){
            @Override public void onLoaded(List<RadioLoader.CountryItem> countries){
                if(!isAdded()||!dialog.isShowing())return;
                allItems.addAll(countries);
                String dev=null; for(RadioLoader.CountryItem it:countries)if(it.iso.equalsIgnoreCase(deviceIso)){dev=it.name;break;}
                selected[0]=dev;
                final CountryListAdapter la=new CountryListAdapter(countries,dev,colors,dp);
                la.setOnItemClickListener(name->selected[0]=name); rv.setAdapter(la);
                tvLoading.setVisibility(View.GONE); rv.setVisibility(View.VISIBLE);
                if(dev!=null)for(int i=0;i<countries.size();i++)if(countries.get(i).name.equals(dev)){final int p=Math.max(0,i-2);rv.post(()->rv.scrollToPosition(p));break;}
                etFilter.addTextChangedListener(new android.text.TextWatcher(){
                    @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
                    @Override public void afterTextChanged(android.text.Editable s){}
                    @Override public void onTextChanged(CharSequence s,int st,int b,int c){
                        String q=s.toString().toLowerCase().trim();
                        if(q.isEmpty()){la.updateList(allItems);return;}
                        List<RadioLoader.CountryItem>f=new ArrayList<>();for(RadioLoader.CountryItem it:allItems)if(it.name.toLowerCase().contains(q))f.add(it);la.updateList(f);}
                });
            }
            @Override public void onError(){if(!isAdded())return;tvLoading.setText("Could not load. Use \"Use Global\".");btnConfirm.setVisibility(View.GONE);}
        });
    }

    private TextView makeTextBtn(String label, boolean primary) {
        TextView tv=new TextView(requireContext()); tv.setText(label); tv.setGravity(Gravity.CENTER); tv.setTextSize(14f); tv.setClickable(true); tv.setFocusable(true);
        if(primary){tv.setTextColor(colors.accent());tv.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));}else tv.setTextColor(colors.textSecondary());
        return tv;
    }

    // ── Country adapter (re-used from RadioFragment) ──────────────

    static class CountryListAdapter extends RecyclerView.Adapter<CountryListAdapter.VH> {
        interface OnItemClick { void onItem(String name); }
        private List<RadioLoader.CountryItem> items; private String selected; private final ColorConfig colors; private final float dp; private OnItemClick listener;
        CountryListAdapter(List<RadioLoader.CountryItem> items,String sel,ColorConfig c,float d){this.items=new ArrayList<>(items);this.selected=sel;this.colors=c;this.dp=d;}
        void setOnItemClickListener(OnItemClick l){listener=l;}
        void updateList(List<RadioLoader.CountryItem> n){items=new ArrayList<>(n);notifyDataSetChanged();}
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p,int vt){
            LinearLayout row=new LinearLayout(p.getContext());row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding((int)(20*dp),(int)(14*dp),(int)(20*dp),(int)(14*dp));row.setClickable(true);row.setFocusable(true);row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView tvN=new TextView(p.getContext());tvN.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));tvN.setTextSize(14f);tvN.setTag("n");row.addView(tvN);
            TextView tvC=new TextView(p.getContext());tvC.setTextSize(11f);tvC.setTag("c");row.addView(tvC);return new VH(row);}
        @Override public void onBindViewHolder(@NonNull VH h,int pos){
            RadioLoader.CountryItem it=items.get(pos);LinearLayout row=(LinearLayout)h.itemView;
            TextView tvN=row.findViewWithTag("n"),tvC=row.findViewWithTag("c");boolean isSel=it.name.equals(selected);
            tvN.setText(it.name);tvN.setTextColor(isSel?colors.accent():colors.textPrimary());tvN.setTypeface(android.graphics.Typeface.create(isSel?"sans-serif-medium":"sans-serif",android.graphics.Typeface.NORMAL));
            tvC.setText(String.valueOf(it.stationCount));tvC.setTextColor(isSel?colors.accent():colors.textSecondary());
            row.setOnClickListener(v->{selected=it.name;if(listener!=null)listener.onItem(it.name);notifyDataSetChanged();});}
        @Override public int getItemCount(){return items.size();}
        static class VH extends RecyclerView.ViewHolder{VH(View v){super(v);}}
    }

    // ── Extra controls (Power Saving + Nav Position) ──────────────

    private void injectExtraControls() {
        if (cardToggles==null) return;
        float dp = density();

        // Power Saving only (removed Persistent Playing + Wave Notification)
        addDivider(cardToggles, dp);
        LinearLayout psRow=makeToggleRow(dp), psText=makeTextCol(dp);
        tvPowerSavingTitle=makeLabel("Power Saving Mode",14f);
        tvPowerSavingSub=makeSub("Disables waves & visualizer to save battery",dp);
        psText.addView(tvPowerSavingTitle); psText.addView(tvPowerSavingSub); psRow.addView(psText);
        swPowerSaving=new SwitchCompat(requireContext());
        swPowerSaving.setChecked(prefs.isPowerSavingEnabled());
        swPowerSaving.setOnCheckedChangeListener((b,v)->prefs.setPowerSavingEnabled(v));
        psRow.addView(swPowerSaving); cardToggles.addView(psRow);

        // Nav position
        addDivider(cardToggles, dp);
        LinearLayout navSection=new LinearLayout(requireContext()); navSection.setOrientation(LinearLayout.VERTICAL); navSection.setPadding((int)(16*dp),(int)(12*dp),(int)(12*dp),(int)(14*dp));
        tvNavPosTitle=makeLabel("Navigation Position",14f); navSection.addView(tvNavPosTitle);
        navBtnsRow=new LinearLayout(requireContext()); navBtnsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams nblp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); nblp.setMargins(0,(int)(8*dp),0,0); navBtnsRow.setLayoutParams(nblp);
        rebuildNavBtns(dp); navSection.addView(navBtnsRow); cardToggles.addView(navSection);

        // Visualizer preset card (injected after cardTheme in the scroll view)
        // We insert it programmatically after cardTheme
        ViewGroup parent = (ViewGroup) cardTheme.getParent();
        if (parent != null) {
            int themeIdx = parent.indexOfChild(cardTheme);
            visPresetCard = buildVisualizerCard(dp);
            parent.addView(visPresetCard, themeIdx + 1);
        }
    }

    private LinearLayout buildVisualizerCard(float dp) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int)(12*dp)); card.setLayoutParams(lp);
        card.setPadding((int)(16*dp),(int)(16*dp),(int)(16*dp),(int)(16*dp));

        tvVisHeader = new TextView(requireContext()); tvVisHeader.setText("VISUALIZER STYLE"); tvVisHeader.setTextSize(11f); tvVisHeader.setLetterSpacing(0.1f); tvVisHeader.setPadding(0,0,0,(int)(10*dp)); card.addView(tvVisHeader);

        HorizontalScrollView hsv = new HorizontalScrollView(requireContext()); hsv.setScrollbars(0); hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        visPresetContainer = new LinearLayout(requireContext()); visPresetContainer.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(visPresetContainer); card.addView(hsv);
        return card;
    }

    private void rebuildNavBtns(float dp) {
        if (navBtnsRow==null) return;
        navBtnsRow.removeAllViews();
        String current = prefs.getNavPosition();
        String[][] opts={{"Left","left"},{"Right","right"},{"Bottom","bottom"}};
        for(String[]opt:opts){
            String lbl=opt[0],val=opt[1];
            TextView btn=new TextView(requireContext());btn.setText(lbl);btn.setGravity(Gravity.CENTER);btn.setTextSize(13f);btn.setPadding((int)(14*dp),(int)(9*dp),(int)(14*dp),(int)(9*dp));btn.setClickable(true);btn.setFocusable(true);
            LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);blp.setMargins(0,0,(int)(6*dp),0);btn.setLayoutParams(blp);
            GradientDrawable gd=new GradientDrawable();gd.setShape(GradientDrawable.RECTANGLE);gd.setCornerRadius(8*dp);
            if(val.equals(current)){gd.setColor(colors.accent());btn.setTextColor(isLight(colors.accent())?0xFF111111:0xFFFFFFFF);}
            else{gd.setColor(colors.bgCard2());gd.setStroke((int)(1*dp),colors.divider());btn.setTextColor(colors.textSecondary());}
            btn.setBackground(gd);btn.setOnClickListener(v->{prefs.setNavPosition(val);if(listener!=null)listener.onNavPositionChanged();rebuildNavBtns(dp);});
            navBtnsRow.addView(btn);}
    }

    // ── Visualizer presets from assets/visualizer/*.json ─────────

    /** VisualizerPreset: name (display), type (engine key), description. */
    private static class VisualizerPreset {
        final String name, type, description;
        VisualizerPreset(String n, String t, String d) { name=n; type=t; description=d; }
    }

    private List<VisualizerPreset> loadVisualizerPresets() {
        List<VisualizerPreset> r = new ArrayList<>();
        try {
            String[] files = requireContext().getAssets().list("visualizer");
            if (files == null) return r;
            java.util.Arrays.sort(files);  // alphabetical
            for (String f : files) {
                if (!f.endsWith(".json")) continue;
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            requireContext().getAssets().open("visualizer/" + f)));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = br.readLine()) != null) sb.append(line); br.close();
                    JSONObject o = new JSONObject(sb.toString());
                    String name = o.optString("name", f.replace(".json",""));
                    String type = o.optString("type", AudioVisualizerView.T_CENTER_BARS);
                    String desc = o.optString("description", "");
                    r.add(new VisualizerPreset(name, type, desc));
                } catch (Exception ignored) {}
            }
        } catch (Exception e) { Log.e(TAG, "loadVisualizerPresets", e); }
        return r;
    }

    public void buildVisualizerPresets() {
        if (visPresetContainer == null) return;
        visPresetContainer.removeAllViews();
        String active = prefs.getVisualizerPreset();
        float  dp     = density();

        List<VisualizerPreset> presets = loadVisualizerPresets();
        for (VisualizerPreset preset : presets) {
            addVisPresetBtn(preset.name, dp, preset.name.equals(active),
                    () -> { prefs.setVisualizerPreset(preset.name); buildVisualizerPresets(); });
        }
    }

    private void addVisPresetBtn(String label, float dp, boolean isActive, Runnable action) {
        TextView btn = new TextView(requireContext());
        btn.setText(label); btn.setGravity(Gravity.CENTER); btn.setTextSize(12f);
        btn.setPadding((int)(14*dp),(int)(9*dp),(int)(14*dp),(int)(9*dp));
        btn.setClickable(true); btn.setFocusable(true);
        GradientDrawable gd = new GradientDrawable(); gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(20*dp);
        if (isActive) { gd.setColor(colors.accent()); gd.setStroke((int)(2*dp),colors.accent()); btn.setTextColor(isLight(colors.accent())?0xFF111111:0xFFFFFFFF); }
        else { gd.setColor(colors.bgCard2()); gd.setStroke((int)(1*dp),colors.divider()); btn.setTextColor(colors.textSecondary()); }
        btn.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,(int)(8*dp),0); btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> action.run());
        visPresetContainer.addView(btn);
    }

    // ── Theme presets from assets/themes/presets.json ─────────────

    private List<String[]> loadPresets() {
        List<String[]> r=new ArrayList<>();
        try{BufferedReader br=new BufferedReader(new InputStreamReader(requireContext().getAssets().open("themes/presets.json")));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);br.close();
            JSONArray arr=new JSONArray(sb.toString());
            for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);r.add(new String[]{o.optString("name","Theme"+i),o.optString("bg","#0D0D14"),o.optString("card","#16161F"),o.optString("card2","#1E1E2A"),o.optString("accent","#6C63FF"),o.optString("accentDim","#3D3880"),o.optString("divider","#22223A"),o.optString("textPrimary","#FFFFFF"),o.optString("textSecondary","#8888AA"),o.optString("navUnselected","#44445A"),o.optString("navBg","#0D0D14")});}}
        catch(Exception e){Log.e(TAG,"loadPresets",e);}return r;
    }

    private void buildThemePresets(){
        if(themePresetsContainer==null)return;
        themePresetsContainer.removeAllViews();
        String active=prefs.getActiveThemeName();float dp=density();
        for(String[]t:loadPresets()){final String name=t[0],json=buildJson(t);addPresetBtn(name,dp,false,name.equals(active),()->applyThemeJson(json,name));}
        try{String[]af=requireContext().getAssets().list("themes");if(af!=null)for(String f:af){if(!f.endsWith(".json")||f.equals("presets.json"))continue;String name=cap(f.replace(".json",""));final String ff=f;addPresetBtn(name,dp,false,name.equals(active),()->{try{BufferedReader br=new BufferedReader(new InputStreamReader(requireContext().getAssets().open("themes/"+ff)));StringBuilder sb=new StringBuilder();String l;while((l=br.readLine())!=null)sb.append(l).append('\n');br.close();applyThemeJson(sb.toString(),name);}catch(Exception ignored){}});}}catch(Exception ignored){}
        addPresetBtn("Custom ✎",dp,true,"Custom".equals(active),()->{if(customEditorWrap!=null){boolean show=customEditorWrap.getVisibility()!=View.VISIBLE;customEditorWrap.setVisibility(show?View.VISIBLE:View.GONE);if(show&&etColorCfg!=null)etColorCfg.setText(colors.readRaw());}});
    }

    private void addPresetBtn(String label,float dp,boolean isCustom,boolean isActive,Runnable action){
        TextView btn=new TextView(requireContext());btn.setText(label);btn.setGravity(Gravity.CENTER);btn.setTextSize(13f);btn.setPadding((int)(16*dp),(int)(10*dp),(int)(16*dp),(int)(10*dp));btn.setClickable(true);btn.setFocusable(true);
        if(isActive)styleBtnActive(btn);else if(isCustom)styleBtnAccent(btn);else styleBtn(btn);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,0,(int)(8*dp),0);btn.setLayoutParams(lp);
        btn.setOnClickListener(v->action.run());themePresetsContainer.addView(btn);}

    private void applyThemeJson(String json,String name){
        colors.saveRaw(json);prefs.setActiveThemeName(name);colors=new ColorConfig(requireContext());
        applyTheme(getView());buildThemePresets();if(listener!=null)listener.onThemeChanged();if(etColorCfg!=null)etColorCfg.setText(colors.readRaw());}

    private String buildJson(String[]t){String bg=t[1],card=t[2],c2=t[3],acc=t[4],accD=t[5],div=t[6],tP=t[7],tS=t[8],nU=t[9],nBg=t[10];return"{\"global\":{\"bg_primary\":\""+bg+"\",\"bg_card\":\""+card+"\",\"bg_card2\":\""+c2+"\",\"accent\":\""+acc+"\",\"accent_dim\":\""+accD+"\",\"divider\":\""+div+"\",\"text_primary\":\""+tP+"\",\"text_secondary\":\""+tS+"\",\"text_section_title\":\""+tP+"\",\"icon_note_vec_tint\":\""+acc+"\",\"page_header_text\":\""+tP+"\",\"page_header_subtitle_text\":\""+tS+"\"},\"nav\":{\"bg\":\""+nBg+"\",\"label_selected\":\""+tP+"\",\"label_unselected\":\""+nU+"\"},\"home\":{\"section_title\":\""+tP+"\",\"chip_playing_bg\":\""+accD+"\",\"chip_playing_border\":\""+acc+"\",\"chip_text\":\""+tP+"\",\"empty_text\":\""+nU+"\",\"now_playing_anim\":\""+acc+"\",\"now_playing_card_bg\":\""+c2+"\",\"now_playing_card_border\":\""+acc+"\",\"now_playing_icon_tint\":\""+acc+"\",\"visualizer_bg\":\""+bg+"\",\"visualizer_bar\":\""+acc+"\"},\"radio\":{\"station_bg\":\""+c2+"\",\"station_bg_active\":\""+accD+"\",\"station_border_active\":\""+acc+"\",\"station_text\":\""+tP+"\",\"station_text_active\":\""+acc+"\",\"station_click_glow\":\""+acc+"\",\"eq_bar\":\""+acc+"\",\"group_header_bg\":\""+card+"\",\"group_header_border\":\""+div+"\",\"group_name_text\":\""+acc+"\",\"group_name_collapsed_text\":\""+tS+"\",\"group_badge_bg\":\""+accD+"\",\"group_badge_text\":\""+acc+"\",\"station_card_bg\":\""+c2+"\",\"station_card_border\":\""+div+"\",\"search_bg\":\""+c2+"\",\"search_text\":\""+tP+"\",\"search_hint\":\""+tS+"\",\"checkbox_color\":\""+acc+"\"},\"sounds\":{\"btn_bg\":\""+card+"\",\"btn_active_bg\":\""+accD+"\",\"btn_border_active\":\""+acc+"\",\"btn_text\":\""+tP+"\",\"wave_color\":\""+acc+"\",\"stop_all_bg\":\""+c2+"\",\"stop_all_border\":\""+acc+"\",\"stop_all_text\":\""+tP+"\"},\"settings\":{\"card_bg\":\""+card+"\",\"card_border\":\""+div+"\",\"label_text\":\""+tP+"\",\"hint_text\":\""+tS+"\",\"version_text\":\""+nU+"\",\"input_bg\":\""+c2+"\",\"input_border\":\""+div+"\",\"input_text\":\""+tP+"\",\"btn_bg\":\""+c2+"\",\"btn_border\":\""+acc+"\",\"btn_text\":\""+tP+"\",\"divider\":\""+div+"\",\"switch_thumb_on\":\""+acc+"\",\"switch_track_on\":\""+accD+"\",\"switch_thumb_off\":\""+tS+"\",\"switch_track_off\":\""+c2+"\",\"made_by_text\":\""+nU+"\",\"made_by_brand\":\""+acc+"\"},\"notification\":{\"icon_bg\":\""+acc+"\"}}";}

    // ── Full applyTheme ───────────────────────────────────────────

    public void applyTheme(View root){
        if(root==null||colors==null)return;
        root.setBackgroundColor(colors.bgPrimary());
        if(tvSettingsTitle!=null)    tvSettingsTitle.setTextColor(colors.pageHeaderText());
        if(tvSettingsSubtitle!=null) tvSettingsSubtitle.setTextColor(colors.pageHeaderSubtitleText());
        if(settingsDivider!=null)    settingsDivider.setBackgroundColor(colors.settingsDivider());
        styleCard(cardToggles);styleCard(cardTheme);styleCard(cardStations);
        if(visPresetCard!=null)      styleCard(visPresetCard);
        lbl(root.findViewById(R.id.tvToggleHeader));lbl(root.findViewById(R.id.tvHapticLabel));lbl(root.findViewById(R.id.tvBtnSoundLabel));lbl(root.findViewById(R.id.tvPersistentLabel));sub(root.findViewById(R.id.tvPersistentSub));
        lbl(root.findViewById(R.id.tvThemePresetsHeader));lbl(root.findViewById(R.id.tvThemeHeader));
        styleInput(etColorCfg);styleBtn(btnSaveColor);buildThemePresets();buildVisualizerPresets();
        if(tvVersion!=null)tvVersion.setTextColor(colors.textSettingsVersion());
        if(tvMadeBy!=null)applyMadeByColors();
        styleSwitches();
        // Injected controls
        if(tvPowerSavingTitle!=null) tvPowerSavingTitle.setTextColor(colors.textSettingsLabel());
        if(tvPowerSavingSub!=null)   tvPowerSavingSub.setTextColor(colors.textSettingsHint());
        if(tvNavPosTitle!=null)      tvNavPosTitle.setTextColor(colors.textSettingsLabel());
        if(tvVisHeader!=null)        tvVisHeader.setTextColor(colors.textSettingsHint());
        if(navBtnsRow!=null)         rebuildNavBtns(density());
        // Country card
        if(tvCountryHeader!=null)    tvCountryHeader.setTextColor(colors.textSettingsHint());
        if(tvCurrentCountry!=null)   tvCurrentCountry.setTextColor(colors.textPrimary());
        if(tvCountryNote!=null)      tvCountryNote.setTextColor(colors.textSettingsHint());
        if(btnChangeCountry!=null)   styleBtn(btnChangeCountry);
    }

    // ── Helpers ───────────────────────────────────────────────────
    private void styleSwitches(){ColorStateList th=new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{colors.settingsSwitchThumbOn(),colors.settingsSwitchThumbOff()});ColorStateList tr=new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{colors.settingsSwitchTrackOn(),colors.settingsSwitchTrackOff()});for(SwitchCompat sw:new SwitchCompat[]{swHaptic,swBtnSound,swPowerSaving}){if(sw==null)continue;sw.setThumbTintList(th);sw.setTrackTintList(tr);}}
    private void styleCard(View card){if(card==null)return;float dp=density();GradientDrawable gd=new GradientDrawable();gd.setShape(GradientDrawable.RECTANGLE);gd.setCornerRadius(12*dp);gd.setColor(colors.bgSettingsCard());gd.setStroke((int)(1*dp),colors.settingsCardBorder());card.setBackground(gd);}
    private void styleInput(EditText et){if(et==null)return;float dp=density();GradientDrawable gd=new GradientDrawable();gd.setShape(GradientDrawable.RECTANGLE);gd.setCornerRadius(8*dp);gd.setColor(colors.settingsInputBg());gd.setStroke((int)(1*dp),colors.settingsInputBorder());et.setBackground(gd);et.setTextColor(colors.settingsInputText());et.setHintTextColor(colors.textSettingsHint());}
    private void styleBtn(TextView btn){if(btn==null)return;float dp=density();GradientDrawable gd=new GradientDrawable();gd.setShape(GradientDrawable.RECTANGLE);gd.setCornerRadius(10*dp);gd.setColor(colors.settingsBtnBg());gd.setStroke((int)(1.5f*dp),colors.settingsBtnBorder());btn.setBackground(gd);btn.setTextColor(colors.settingsBtnText());}
    private void styleBtnActive(TextView btn){if(btn==null)return;float dp=density();GradientDrawable gd=new GradientDrawable();gd.setShape(GradientDrawable.RECTANGLE);gd.setCornerRadius(10*dp);gd.setColor(colors.accent());gd.setStroke((int)(2f*dp),colors.accent());btn.setBackground(gd);btn.setTextColor(isLight(colors.accent())?0xFF111111:0xFFFFFFFF);}
    private void styleBtnAccent(TextView btn){if(btn==null)return;float dp=density();GradientDrawable gd=new GradientDrawable();gd.setShape(GradientDrawable.RECTANGLE);gd.setCornerRadius(10*dp);gd.setColor(colors.accentDim());gd.setStroke((int)(1.5f*dp),colors.accent());btn.setBackground(gd);btn.setTextColor(colors.accent());}
    private static boolean isLight(int c){double r=((c>>16)&0xFF)/255.0,g=((c>>8)&0xFF)/255.0,b=(c&0xFF)/255.0;return(0.299*r+0.587*g+0.114*b)>0.5;}
    private void lbl(View v){if(v instanceof TextView)((TextView)v).setTextColor(colors.textSettingsLabel());}
    private void sub(View v){if(v instanceof TextView)((TextView)v).setTextColor(colors.textSettingsHint());}
    private void applyMadeByColors(){if(tvMadeBy==null)return;android.text.SpannableString ss=new android.text.SpannableString("Made by pxatyush");ss.setSpan(new android.text.style.ForegroundColorSpan(colors.settingsMadeByText()),0,8,android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);ss.setSpan(new android.text.style.ForegroundColorSpan(colors.settingsMadeByBrand()),8,ss.length(),android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);tvMadeBy.setText(ss);}
    private LinearLayout makeToggleRow(float dp){LinearLayout r=new LinearLayout(requireContext());r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding((int)(16*dp),0,(int)(12*dp),0);r.setMinimumHeight((int)(56*dp));return r;}
    private LinearLayout makeTextCol(float dp){LinearLayout c=new LinearLayout(requireContext());c.setOrientation(LinearLayout.VERTICAL);c.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));c.setPadding(0,(int)(10*dp),0,(int)(10*dp));return c;}
    private TextView makeLabel(String text,float size){TextView tv=new TextView(requireContext());tv.setText(text);tv.setTextSize(size);tv.setTextColor(colors.textSettingsLabel());return tv;}
    private TextView makeSub(String text,float dp){TextView tv=new TextView(requireContext());tv.setText(text);tv.setTextSize(12f);tv.setTextColor(colors.textSettingsHint());tv.setPadding(0,(int)(2*dp),0,0);return tv;}
    private void addDivider(LinearLayout parent,float dp){View div=new View(requireContext());div.setBackgroundColor(colors.divider());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1);lp.setMargins((int)(16*dp),0,0,0);div.setLayoutParams(lp);parent.addView(div);}
    private float density(){return getResources().getDisplayMetrics().density;}
    private static String cap(String s){return(s==null||s.isEmpty())?s:Character.toUpperCase(s.charAt(0))+s.substring(1);}
}