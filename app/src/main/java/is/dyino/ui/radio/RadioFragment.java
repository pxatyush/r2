package is.dyino.ui.radio;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import is.dyino.R;
import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;
import is.dyino.service.AudioService;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;
import is.dyino.util.RadioLoader;

public class RadioFragment extends Fragment {

    private TextView     tvNowPlaying;
    private EditText     etSearch;
    private SeekBar      radioVolumeSeek;
    private View         volumeSliderContainer;
    private RecyclerView recycler;
    private TextView     btnCategoryFilter;

    private AppPrefs     prefs;
    private ColorConfig  colors;
    private AudioService audioService;
    private RadioGroupAdapter adapter;
    private RadioStation selectedStation;

    private List<RadioGroup> allGroups   = new ArrayList<>();
    private String           searchQuery = "";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Syncs currently-playing label when notification changes station/pause state */
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            mainHandler.post(() -> {
                if (audioService == null) return;
                // Update now-playing title
                if (tvNowPlaying != null) {
                    if (!audioService.isRadioSelected()) {
                        tvNowPlaying.setText("Select a station");
                        selectedStation = null;
                    } else if (audioService.isRadioPlaying()) {
                        tvNowPlaying.setText(audioService.getCurrentName());
                    } else if (audioService.isRadioPaused()) {
                        tvNowPlaying.setText("⏸  " + audioService.getCurrentName());
                    }
                }
                if (adapter != null) {
                    // Sync active highlight in list
                    if (!audioService.isRadioSelected()) adapter.setActiveStation(null);
                    else if (selectedStation != null)    adapter.setActiveStation(selectedStation);
                }
            });
        }
    };

    public void setAudioService(AudioService svc) {
        this.audioService = svc;
        if (audioService != null) audioService.setRadioListener(makeListener());
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_radio, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs  = new AppPrefs(requireContext());
        colors = new ColorConfig(requireContext());

        tvNowPlaying          = view.findViewById(R.id.tvNowPlayingTitle);
        etSearch              = view.findViewById(R.id.etSearch);
        radioVolumeSeek       = view.findViewById(R.id.radioVolumeSeek);
        volumeSliderContainer = view.findViewById(R.id.volumeSliderContainer);
        recycler              = view.findViewById(R.id.radioRecycler);
        btnCategoryFilter     = view.findViewById(R.id.btnCategoryFilter);

        applyTheme(view);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c2, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c2) {
                searchQuery = s.toString().trim(); refreshAdapter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        radioVolumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && audioService != null) audioService.setRadioVolume(p / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        if (btnCategoryFilter != null)
            btnCategoryFilter.setOnClickListener(v -> { haptic(); showCategoryManager(); });

        if (!prefs.hasRadioCountry()) showCountryDialog();
        else loadRadioStations();
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(AudioService.BROADCAST_STATE);
        ContextCompat.registerReceiver(requireContext(), stateReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onStop() {
        super.onStop();
        try { requireContext().unregisterReceiver(stateReceiver); } catch (Exception ignored) {}
    }

    // ── Country dialog ───────────────────────────────────────────
    private void showCountryDialog() {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(colors.bgCard());
        int pad = (int)(24 * dp);
        container.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(requireContext());
        title.setText("Radio Country"); title.setTextColor(colors.textPrimary()); title.setTextSize(20f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        container.addView(title);

        TextView sub = new TextView(requireContext());
        sub.setText("Enter your country to fetch local stations");
        sub.setTextColor(colors.textSecondary()); sub.setTextSize(13f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0,(int)(6*dp),0,(int)(20*dp)); sub.setLayoutParams(subLp);
        container.addView(sub);

        EditText input = new EditText(requireContext());
        input.setHint("India, Germany, USA…"); input.setTextColor(colors.radioSearchText());
        input.setHintTextColor(colors.radioSearchHint()); input.setTextSize(15f);
        input.setPadding((int)(14*dp),(int)(12*dp),(int)(14*dp),(int)(12*dp)); input.setSingleLine(true);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setShape(GradientDrawable.RECTANGLE); inputBg.setCornerRadius(10*dp);
        inputBg.setColor(colors.settingsInputBg()); inputBg.setStroke((int)(1*dp), colors.divider());
        input.setBackground(inputBg);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.setMargins(0,0,0,(int)(20*dp)); input.setLayoutParams(inputLp);
        container.addView(input);

        LinearLayout btnRow = new LinearLayout(requireContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnSkip  = makeDialogBtn("Use Global", false);
        TextView btnFetch = makeDialogBtn("Fetch", true);
        LinearLayout.LayoutParams bpSkip = new LinearLayout.LayoutParams(0,(int)(44*dp),1f);
        bpSkip.setMargins(0,0,(int)(8*dp),0); btnSkip.setLayoutParams(bpSkip);
        btnFetch.setLayoutParams(new LinearLayout.LayoutParams(0,(int)(44*dp),1f));
        btnRow.addView(btnSkip); btnRow.addView(btnFetch); container.addView(btnRow);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(container).setCancelable(false).create();
        if (dialog.getWindow()!=null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        btnSkip.setOnClickListener(v->{prefs.setRadioCountry("");loadRadioStations();dialog.dismiss();});
        btnFetch.setOnClickListener(v->{String c=input.getText().toString().trim();prefs.setRadioCountry(c);loadRadioStations();dialog.dismiss();});
        dialog.show();
    }

    private TextView makeDialogBtn(String label, boolean primary) {
        float dp = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(requireContext());
        tv.setText(label); tv.setGravity(Gravity.CENTER); tv.setTextSize(14f);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(10*dp);
        if (primary) { gd.setColor(colors.accent()); tv.setTextColor(0xFFFFFFFF); }
        else { gd.setColor(colors.bgCard2()); gd.setStroke((int)(1*dp),colors.divider()); tv.setTextColor(colors.textSecondary()); }
        tv.setBackground(gd); tv.setClickable(true); tv.setFocusable(true);
        return tv;
    }

    // ── Category manager dialog ──────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private void showCategoryManager() {
        if (allGroups.isEmpty()) return;
        float dp = getResources().getDisplayMetrics().density;
        List<String> savedOrder = prefs.getGroupOrder();
        Set<String>  hidden     = prefs.getHiddenCategories();
        List<String> catNames   = new ArrayList<>();
        for (String n : savedOrder) for (RadioGroup g : allGroups) if (g.getName().equals(n) && !catNames.contains(n)) { catNames.add(n); break; }
        for (RadioGroup g : allGroups) if (!catNames.contains(g.getName())) catNames.add(g.getName());
        List<boolean[]> visibleFlags = new ArrayList<>();
        for (String n : catNames) visibleFlags.add(new boolean[]{!hidden.contains(n)});

        LinearLayout root = new LinearLayout(requireContext()); root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setShape(GradientDrawable.RECTANGLE); rootBg.setCornerRadius(16*dp); rootBg.setColor(colors.bgCard());
        root.setBackground(rootBg); root.setClipToOutline(true);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("Categories"); tvTitle.setTextColor(colors.textPrimary()); tvTitle.setTextSize(18f);
        tvTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        tvTitle.setPadding((int)(20*dp),(int)(18*dp),(int)(20*dp),(int)(12*dp)); root.addView(tvTitle);

        View divTop = new View(requireContext()); divTop.setBackgroundColor(colors.divider());
        divTop.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1)); root.addView(divTop);

        RecyclerView rv = new RecyclerView(requireContext()); rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f)); root.addView(rv);

        View divBot = new View(requireContext()); divBot.setBackgroundColor(colors.divider());
        divBot.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1)); root.addView(divBot);

        TextView btnDone = new TextView(requireContext());
        btnDone.setText("Done"); btnDone.setGravity(Gravity.CENTER); btnDone.setTextColor(colors.accent());
        btnDone.setTextSize(15f); btnDone.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        btnDone.setPadding(0,(int)(14*dp),0,(int)(16*dp)); btnDone.setClickable(true); btnDone.setFocusable(true); root.addView(btnDone);

        CatManagerAdapter catAdapter = new CatManagerAdapter(catNames, visibleFlags, colors, dp);
        rv.setLayoutManager(new LinearLayoutManager(requireContext())); rv.setAdapter(catAdapter);

        ItemTouchHelper.Callback cb = new ItemTouchHelper.Callback() {
            @Override public int getMovementFlags(@NonNull RecyclerView r2,@NonNull RecyclerView.ViewHolder vh) { return makeMovementFlags(ItemTouchHelper.UP|ItemTouchHelper.DOWN,0); }
            @Override public boolean onMove(@NonNull RecyclerView r2,@NonNull RecyclerView.ViewHolder from,@NonNull RecyclerView.ViewHolder to) {
                int f=from.getAdapterPosition(),t=to.getAdapterPosition(); if(f<0||t<0) return false;
                Collections.swap(catNames,f,t); Collections.swap(visibleFlags,f,t); catAdapter.notifyItemMoved(f,t); return true; }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int d) {}
            @Override public boolean isLongPressDragEnabled() { return false; }
            @Override public void onSelectedChanged(RecyclerView.ViewHolder vh,int state) {
                super.onSelectedChanged(vh,state);
                if(state==ItemTouchHelper.ACTION_STATE_DRAG&&vh!=null){haptic();vh.itemView.setAlpha(0.85f);vh.itemView.setScaleX(1.02f);vh.itemView.setScaleY(1.02f);}
            }
            @Override public void clearView(@NonNull RecyclerView r2,@NonNull RecyclerView.ViewHolder vh) {
                super.clearView(r2,vh); vh.itemView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start(); }
        };
        ItemTouchHelper ith = new ItemTouchHelper(cb); ith.attachToRecyclerView(rv); catAdapter.setTouchHelper(ith);

        int maxH = (int)(getResources().getDisplayMetrics().heightPixels*0.60f);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(root).create();
        if(dialog.getWindow()!=null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        btnDone.setOnClickListener(v->{
            prefs.saveGroupOrder(catNames);
            Set<String> newHidden=new java.util.HashSet<>();
            for(int i=0;i<catNames.size();i++) if(!visibleFlags.get(i)[0]) newHidden.add(catNames.get(i));
            prefs.setHiddenCategories(newHidden); dialog.dismiss(); refreshAdapter();
        });
        dialog.show();
        if(dialog.getWindow()!=null){int w=(int)(getResources().getDisplayMetrics().widthPixels*0.90f);int h=Math.min(maxH,catNames.size()*(int)(56*dp)+(int)(140*dp));dialog.getWindow().setLayout(w,h);}
    }

    static class CatManagerAdapter extends RecyclerView.Adapter<CatManagerAdapter.VH> {
        private final List<String> names; private final List<boolean[]> visible;
        private final ColorConfig colors; private final float dp; private ItemTouchHelper touchHelper;
        CatManagerAdapter(List<String> n,List<boolean[]> v,ColorConfig c,float d){names=n;visible=v;colors=c;dp=d;}
        void setTouchHelper(ItemTouchHelper ith){this.touchHelper=ith;}
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent,int type){
            LinearLayout row=new LinearLayout(parent.getContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int)(4*dp),0,(int)(16*dp),0); row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,(int)(56*dp)));
            TextView drag=new TextView(parent.getContext()); drag.setText("☰"); drag.setTextSize(18f); drag.setTextColor(Color.parseColor("#44445A")); drag.setPadding((int)(12*dp),0,(int)(12*dp),0); drag.setTag("drag"); row.addView(drag);
            TextView name=new TextView(parent.getContext()); name.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); name.setTextSize(14f); name.setTag("name"); row.addView(name);
            CheckBox cb=new CheckBox(parent.getContext()); cb.setTag("check");
            try { android.content.res.ColorStateList csl=android.content.res.ColorStateList.valueOf(colors.radioCheckboxColor()); androidx.core.widget.CompoundButtonCompat.setButtonTintList(cb,csl); } catch(Exception ignored){}
            row.addView(cb); return new VH(row); }
        @SuppressLint("ClickableViewAccessibility") @Override public void onBindViewHolder(@NonNull VH h,int pos){
            String name=names.get(pos); boolean[] vis=visible.get(pos);
            TextView tvName=h.row.findViewWithTag("name"); CheckBox cb=h.row.findViewWithTag("check"); View drag=h.row.findViewWithTag("drag");
            tvName.setText(name); tvName.setTextColor(vis[0]?colors.textPrimary():colors.textSecondary());
            cb.setOnCheckedChangeListener(null); cb.setChecked(vis[0]); cb.setOnCheckedChangeListener((btn,checked)->{vis[0]=checked;tvName.setTextColor(checked?colors.textPrimary():colors.textSecondary());});
            drag.setOnTouchListener((v,ev)->{if(ev.getActionMasked()==MotionEvent.ACTION_DOWN&&touchHelper!=null)touchHelper.startDrag(h);return false;}); }
        @Override public int getItemCount(){return names.size();}
        static class VH extends RecyclerView.ViewHolder{final LinearLayout row;VH(LinearLayout v){super(v);row=v;}}
    }

    // ── Station loading ──────────────────────────────────────────
    private void loadRadioStations() {
        if (tvNowPlaying != null) tvNowPlaying.setText("Loading…");
        RadioLoader.load(requireContext(), prefs, groups -> {
            allGroups = groups; refreshAdapter();
            if (tvNowPlaying != null) tvNowPlaying.setText("Select a station");
        });
    }

    private void refreshAdapter() {
        if (recycler == null) return;
        Set<String> hidden = prefs.getHiddenCategories();
        List<String> savedOrder = prefs.getGroupOrder();
        List<RadioGroup> ordered = new ArrayList<>();
        for (String n : savedOrder) for (RadioGroup g : allGroups) if (g.getName().equals(n)){ordered.add(g);break;}
        for (RadioGroup g : allGroups) if (!savedOrder.contains(g.getName())) ordered.add(g);

        List<RadioGroup> display = new ArrayList<>();
        for (RadioGroup g : ordered) {
            if (hidden.contains(g.getName())) continue;
            if (searchQuery.isEmpty()) { display.add(g); }
            else {
                String q = searchQuery.toLowerCase(); List<RadioStation> m = new ArrayList<>();
                for (RadioStation s : g.getStations()) if (s.getName().toLowerCase().contains(q)) m.add(s);
                if (!m.isEmpty()) display.add(new RadioGroup(g.getName(), m));
            }
        }
        Set<String> archivedKeys = prefs.getArchived();
        if (!archivedKeys.isEmpty()) {
            List<RadioStation> arch = new ArrayList<>();
            for (String key : archivedKeys) { String[] p=AppPrefs.splitKey(key); if(p.length>=2) arch.add(new RadioStation(p[0],p[1],p.length>=3?p[2]:"","")); }
            if (!arch.isEmpty()) display.add(new RadioGroup("__ARCHIVED__", arch));
        }

        adapter = new RadioGroupAdapter(display, this::onStationClicked,
                (station,isFav)->{haptic();Toast.makeText(requireContext(),isFav?"♥ Added to Favourites":"Removed from Favourites",Toast.LENGTH_SHORT).show();},
                prefs, colors,
                new RadioGroupAdapter.SwipeActionListener(){
                    @Override public void onArchive(RadioStation s){haptic();prefs.addArchived(AppPrefs.stationKey(s.getName(),s.getUrl(),s.getGroup()));Toast.makeText(requireContext(),"Archived",Toast.LENGTH_SHORT).show();refreshAdapter();}
                    @Override public void onUnarchive(String key){haptic();prefs.removeArchived(key);refreshAdapter();}
                });
        if (selectedStation != null) adapter.setActiveStation(selectedStation);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        adapter.attachToRecyclerView(recycler);
    }

    private void onStationClicked(RadioStation station) {
        haptic(); clickSound();
        if (audioService == null) return;
        String curUrl = audioService.getCurrentRadioUrl();
        if (curUrl.equals(station.getUrl())) {
            if (audioService.isRadioPlaying()) { audioService.pauseAll(); if(tvNowPlaying!=null) tvNowPlaying.setText("⏸  "+station.getName()); }
            else { audioService.resumeAll(); if(tvNowPlaying!=null) tvNowPlaying.setText(station.getName()); }
            return;
        }
        selectedStation = station;
        if (tvNowPlaying != null) tvNowPlaying.setText("Buffering…");
        if (adapter != null) adapter.setActiveStation(station);
        String key = AppPrefs.stationKey(station.getName(), station.getUrl(), station.getGroup());
        prefs.addLastPlayed(key);
        audioService.playRadio(station.getName(), station.getUrl(), station.getFaviconUrl());
        audioService.setRadioListener(makeListener());
    }

    private AudioService.RadioListener makeListener() {
        return new AudioService.RadioListener() {
            @Override public void onPlaybackStarted(String n) { mainHandler.post(()->{if(tvNowPlaying!=null) tvNowPlaying.setText(n);}); }
            @Override public void onPlaybackStopped() { mainHandler.post(()->{selectedStation=null;if(tvNowPlaying!=null)tvNowPlaying.setText("Select a station");if(adapter!=null)adapter.setActiveStation(null);}); }
            @Override public void onError(String m) { mainHandler.post(()->{if(tvNowPlaying!=null)tvNowPlaying.setText("Error – "+m);}); }
            @Override public void onBuffering() { mainHandler.post(()->{if(tvNowPlaying!=null&&selectedStation!=null)tvNowPlaying.setText("Buffering…  "+selectedStation.getName());}); }
        };
    }

    /** Max intensity: 50 ms / amplitude 255 */
    private void haptic() {
        if (prefs == null || !prefs.isHapticEnabled()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) requireContext().getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) { vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(50, 255)); return; }
            }
            @SuppressWarnings("deprecation")
            Vibrator vib = (Vibrator) requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (vib==null||!vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) vib.vibrate(VibrationEffect.createOneShot(50,255)); else vib.vibrate(50);
        } catch (Exception ignored) {}
    }

    private void clickSound() { if(audioService!=null&&prefs!=null&&prefs.isButtonSoundEnabled()) audioService.playClickSound(); }

    public void applyTheme(View root) {
        if(root==null||colors==null) return;
        root.setBackgroundColor(colors.bgPrimary());
        if(tvNowPlaying!=null) tvNowPlaying.setTextColor(colors.textSecondary());
        if(etSearch!=null){ etSearch.setTextColor(colors.radioSearchText()); etSearch.setHintTextColor(colors.radioSearchHint());
            float dp=getResources().getDisplayMetrics().density; GradientDrawable gd=new GradientDrawable(); gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(10*dp); gd.setColor(colors.radioSearchBg()); etSearch.setBackground(gd); }
        if(btnCategoryFilter!=null) btnCategoryFilter.setTextColor(colors.textSecondary());
    }

    public void refresh() {
        if(getView()==null) return; colors=new ColorConfig(requireContext()); applyTheme(getView()); refreshAdapter();
    }

    public RadioStation getSelectedStation() { return selectedStation; }
}
