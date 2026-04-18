package is.dyino.ui.radio;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import java.util.Locale;
import java.util.Set;

import is.dyino.R;
import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;
import is.dyino.service.AudioService;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;
import is.dyino.util.RadioLoader;

public class RadioFragment extends Fragment {

    private TextView     tvNowPlaying, tvRadioPageTitle;
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

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            mainHandler.post(() -> {
                if (audioService == null || tvNowPlaying == null) return;
                if (!audioService.isRadioSelected()) {
                    tvNowPlaying.setText("Select a station"); selectedStation = null;
                    if (adapter != null) adapter.setActiveStation(null);
                } else if (audioService.isRadioPlaying()) {
                    tvNowPlaying.setText(audioService.getCurrentName());
                } else if (audioService.isRadioPaused()) {
                    tvNowPlaying.setText("⏸  " + audioService.getCurrentName());
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

        tvRadioPageTitle      = view.findViewById(R.id.tvRadioPageTitle);
        tvNowPlaying          = view.findViewById(R.id.tvNowPlayingTitle);
        etSearch              = view.findViewById(R.id.etSearch);
        radioVolumeSeek       = view.findViewById(R.id.radioVolumeSeek);
        volumeSliderContainer = view.findViewById(R.id.volumeSliderContainer);
        recycler              = view.findViewById(R.id.radioRecycler);
        btnCategoryFilter     = view.findViewById(R.id.btnCategoryFilter);

        applyTheme(view);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c2, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c2) {
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

    @Override public void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(requireContext(), stateReceiver,
                new IntentFilter(AudioService.BROADCAST_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override public void onStop() {
        super.onStop();
        try { requireContext().unregisterReceiver(stateReceiver); } catch (Exception ignored) {}
    }

    // ── Country picker dialog ─────────────────────────────────────
    private void showCountryDialog() {
        float dp      = getResources().getDisplayMetrics().density;
        int screenW   = getResources().getDisplayMetrics().widthPixels;
        int screenH   = getResources().getDisplayMetrics().heightPixels;

        // Root
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setShape(GradientDrawable.RECTANGLE);
        rootBg.setCornerRadius(16 * dp);
        rootBg.setColor(colors.bgCard());
        root.setBackground(rootBg);
        root.setClipToOutline(true);

        // Title
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("Your Country");
        tvTitle.setTextColor(colors.textPrimary()); tvTitle.setTextSize(18f);
        tvTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        tvTitle.setPadding((int)(20*dp), (int)(18*dp), (int)(20*dp), (int)(2*dp));
        root.addView(tvTitle);

        TextView tvSub = new TextView(requireContext());
        tvSub.setText("Select to load local radio stations");
        tvSub.setTextColor(colors.textSecondary()); tvSub.setTextSize(12f);
        tvSub.setPadding((int)(20*dp), 0, (int)(20*dp), (int)(10*dp));
        root.addView(tvSub);

        // Search
        EditText etSearch2 = new EditText(requireContext());
        etSearch2.setHint("Search countries…");
        etSearch2.setTextColor(colors.textPrimary());
        etSearch2.setHintTextColor(colors.textSecondary()); etSearch2.setTextSize(14f); etSearch2.setSingleLine(true);
        etSearch2.setPadding((int)(14*dp),(int)(10*dp),(int)(14*dp),(int)(10*dp));
        GradientDrawable sBg = new GradientDrawable(); sBg.setShape(GradientDrawable.RECTANGLE);
        sBg.setCornerRadius(10*dp); sBg.setColor(colors.bgCard2()); sBg.setStroke((int)(1*dp),colors.divider());
        etSearch2.setBackground(sBg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins((int)(16*dp),0,(int)(16*dp),(int)(8*dp)); etSearch2.setLayoutParams(slp);
        root.addView(etSearch2);

        // Loading
        TextView tvLoading = new TextView(requireContext());
        tvLoading.setText("Loading countries…");
        tvLoading.setTextColor(colors.textSecondary()); tvLoading.setGravity(Gravity.CENTER);
        tvLoading.setPadding(0,(int)(32*dp),0,(int)(32*dp));
        root.addView(tvLoading);

        // RecyclerView
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setVisibility(View.GONE);
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rv.setLayoutParams(rvLp);
        root.addView(rv);

        // Bottom divider
        View div = new View(requireContext());
        div.setBackgroundColor(colors.divider());
        div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        root.addView(div);

        // Button row
        LinearLayout btnRow = new LinearLayout(requireContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnGlobal = makeDialogBtn2("Use Global", false);
        btnGlobal.setLayoutParams(new LinearLayout.LayoutParams(0, (int)(48*dp), 1f));
        btnRow.addView(btnGlobal);

        View btnDiv = new View(requireContext());
        btnDiv.setBackgroundColor(colors.divider());
        btnDiv.setLayoutParams(new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
        btnRow.addView(btnDiv);

        TextView btnConfirm = makeDialogBtn2("Confirm", true);
        btnConfirm.setLayoutParams(new LinearLayout.LayoutParams(0, (int)(48*dp), 1f));
        btnRow.addView(btnConfirm);

        root.addView(btnRow);

        // Dialog
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(root).setCancelable(false).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().setLayout((int)(screenW * 0.90f), (int)(screenH * 0.72f));

        // State
        String deviceIso = Locale.getDefault().getCountry().toUpperCase();
        final String[] selectedCountry = {null};
        final List<RadioLoader.CountryItem> allItems = new ArrayList<>();

        btnGlobal.setOnClickListener(v -> {
            prefs.setRadioCountry(""); prefs.saveRadioCache("");
            loadRadioStations(); dialog.dismiss();
        });
        btnConfirm.setOnClickListener(v -> {
            if (selectedCountry[0] != null) prefs.setRadioCountry(selectedCountry[0]);
            else prefs.setRadioCountry("");
            prefs.saveRadioCache(""); loadRadioStations(); dialog.dismiss();
        });

        // Fetch countries
        RadioLoader.loadCountries(new RadioLoader.CountriesCallback() {
            @Override public void onLoaded(List<RadioLoader.CountryItem> countries) {
                if (!isAdded() || !dialog.isShowing()) return;
                allItems.addAll(countries);

                // Detect device country
                String devCountry = null;
                for (RadioLoader.CountryItem item : countries)
                    if (item.iso.equalsIgnoreCase(deviceIso)) { devCountry = item.name; break; }
                selectedCountry[0] = devCountry;

                final CountryListAdapter adapter2 = new CountryListAdapter(countries, devCountry, colors, dp);
                adapter2.setOnItemClickListener(name -> selectedCountry[0] = name);
                rv.setAdapter(adapter2);

                tvLoading.setVisibility(View.GONE);
                rv.setVisibility(View.VISIBLE);

                // Scroll to device country
                if (devCountry != null) {
                    for (int i = 0; i < countries.size(); i++) {
                        if (countries.get(i).name.equals(devCountry)) {
                            final int pos = Math.max(0, i - 2);
                            rv.post(() -> rv.scrollToPosition(pos));
                            break;
                        }
                    }
                }

                // Live search filter
                etSearch2.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                    @Override public void afterTextChanged(Editable s) {}
                    @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                        String q = s.toString().toLowerCase().trim();
                        if (q.isEmpty()) { adapter2.updateList(allItems); return; }
                        List<RadioLoader.CountryItem> filtered = new ArrayList<>();
                        for (RadioLoader.CountryItem item : allItems)
                            if (item.name.toLowerCase().contains(q)) filtered.add(item);
                        adapter2.updateList(filtered);
                    }
                });
            }

            @Override public void onError() {
                if (!isAdded()) return;
                tvLoading.setText("Could not load countries. Use Global or try again.");
                btnConfirm.setVisibility(View.GONE);
            }
        });
    }

    private TextView makeDialogBtn2(String label, boolean primary) {
        float dp = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(requireContext());
        tv.setText(label); tv.setGravity(Gravity.CENTER); tv.setTextSize(14f);
        tv.setClickable(true); tv.setFocusable(true);
        if (primary) {
            tv.setTextColor(colors.accent());
            tv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        } else {
            tv.setTextColor(colors.textSecondary());
        }
        return tv;
    }

    // ── Country list adapter ──────────────────────────────────────
    static class CountryListAdapter extends RecyclerView.Adapter<CountryListAdapter.VH> {
        interface OnItemClick { void onItem(String name); }

        private List<RadioLoader.CountryItem> items;
        private String selected;
        private final ColorConfig colors;
        private final float dp;
        private OnItemClick listener;

        CountryListAdapter(List<RadioLoader.CountryItem> items, String sel, ColorConfig c, float d) {
            this.items = new ArrayList<>(items); this.selected = sel; this.colors = c; this.dp = d;
        }

        void setOnItemClickListener(OnItemClick l) { listener = l; }

        void updateList(List<RadioLoader.CountryItem> newList) {
            items = new ArrayList<>(newList); notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int)(20*dp),(int)(14*dp),(int)(20*dp),(int)(14*dp));
            row.setClickable(true); row.setFocusable(true);
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView tvName = new TextView(parent.getContext());
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tvName.setTextSize(14f); tvName.setTag("n");
            row.addView(tvName);

            TextView tvCount = new TextView(parent.getContext());
            tvCount.setTextSize(11f); tvCount.setTag("c");
            row.addView(tvCount);

            return new VH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            RadioLoader.CountryItem item = items.get(pos);
            LinearLayout row = (LinearLayout) h.itemView;
            TextView tvName  = row.findViewWithTag("n");
            TextView tvCount = row.findViewWithTag("c");

            boolean isSel = item.name.equals(selected);
            tvName.setText(item.name);
            tvName.setTextColor(isSel ? colors.accent() : colors.textPrimary());
            tvName.setTypeface(android.graphics.Typeface.create(
                    isSel ? "sans-serif-medium" : "sans-serif", android.graphics.Typeface.NORMAL));
            tvCount.setText(String.valueOf(item.stationCount));
            tvCount.setTextColor(isSel ? colors.accent() : colors.textSecondary());

            row.setOnClickListener(v -> {
                selected = item.name;
                if (listener != null) listener.onItem(item.name);
                notifyDataSetChanged();
            });
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }

    // ── Category manager ─────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private void showCategoryManager() {
        if (allGroups.isEmpty()) return;
        float dp = getResources().getDisplayMetrics().density;
        List<String> savedOrder = prefs.getGroupOrder();
        Set<String>  hidden     = prefs.getHiddenCategories();
        List<String> catNames   = new ArrayList<>();
        for (String n : savedOrder) for (RadioGroup g : allGroups) if (g.getName().equals(n) && !catNames.contains(n)) { catNames.add(n); break; }
        for (RadioGroup g : allGroups) if (!catNames.contains(g.getName())) catNames.add(g.getName());
        List<boolean[]> vis = new ArrayList<>();
        for (String n : catNames) vis.add(new boolean[]{!hidden.contains(n)});

        LinearLayout root = new LinearLayout(requireContext()); root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rootBg = new GradientDrawable(); rootBg.setShape(GradientDrawable.RECTANGLE); rootBg.setCornerRadius(16*dp); rootBg.setColor(colors.bgCard());
        root.setBackground(rootBg); root.setClipToOutline(true);

        TextView tvTitle = new TextView(requireContext()); tvTitle.setText("Categories"); tvTitle.setTextColor(colors.textPrimary()); tvTitle.setTextSize(18f);
        tvTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        tvTitle.setPadding((int)(20*dp),(int)(18*dp),(int)(20*dp),(int)(12*dp)); root.addView(tvTitle);
        View divTop = new View(requireContext()); divTop.setBackgroundColor(colors.divider()); divTop.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1)); root.addView(divTop);

        RecyclerView rv = new RecyclerView(requireContext()); rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f)); root.addView(rv);
        View divBot = new View(requireContext()); divBot.setBackgroundColor(colors.divider()); divBot.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1)); root.addView(divBot);

        TextView btnDone = new TextView(requireContext()); btnDone.setText("Done"); btnDone.setGravity(Gravity.CENTER); btnDone.setTextColor(colors.accent()); btnDone.setTextSize(15f);
        btnDone.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        btnDone.setPadding(0,(int)(14*dp),0,(int)(16*dp)); btnDone.setClickable(true); btnDone.setFocusable(true); root.addView(btnDone);

        CatManagerAdapter catAdapter = new CatManagerAdapter(catNames, vis, colors, dp);
        rv.setLayoutManager(new LinearLayoutManager(requireContext())); rv.setAdapter(catAdapter);

        ItemTouchHelper.Callback cb = new ItemTouchHelper.Callback() {
            @Override public int getMovementFlags(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder vh) { return makeMovementFlags(ItemTouchHelper.UP|ItemTouchHelper.DOWN,0); }
            @Override public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder f, @NonNull RecyclerView.ViewHolder t) {
                int fi=f.getAdapterPosition(), ti=t.getAdapterPosition(); if(fi<0||ti<0) return false;
                Collections.swap(catNames,fi,ti); Collections.swap(vis,fi,ti); catAdapter.notifyItemMoved(fi,ti); return true; }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int d) {}
            @Override public boolean isLongPressDragEnabled() { return false; }
            @Override public void onSelectedChanged(RecyclerView.ViewHolder vh, int state) {
                super.onSelectedChanged(vh,state);
                if(state==ItemTouchHelper.ACTION_STATE_DRAG&&vh!=null){haptic();vh.itemView.setAlpha(0.85f);vh.itemView.setScaleX(1.02f);vh.itemView.setScaleY(1.02f);}
            }
            @Override public void clearView(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(r,vh); vh.itemView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start(); }
        };
        ItemTouchHelper ith = new ItemTouchHelper(cb); ith.attachToRecyclerView(rv); catAdapter.setTouchHelper(ith);

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(root).create();
        if(dialog.getWindow()!=null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        btnDone.setOnClickListener(v -> {
            prefs.saveGroupOrder(catNames);
            Set<String> newHidden = new java.util.HashSet<>();
            for (int i=0;i<catNames.size();i++) if(!vis.get(i)[0]) newHidden.add(catNames.get(i));
            prefs.setHiddenCategories(newHidden); dialog.dismiss(); refreshAdapter();
        });
        dialog.show();
        int maxH = (int)(getResources().getDisplayMetrics().heightPixels * 0.60f);
        if(dialog.getWindow()!=null){ int w=(int)(getResources().getDisplayMetrics().widthPixels*0.90f); int h=Math.min(maxH,catNames.size()*(int)(56*dp)+(int)(140*dp)); dialog.getWindow().setLayout(w,h); }
    }

    @SuppressLint({"ClickableViewAccessibility","SetTextI18n"})
    static class CatManagerAdapter extends RecyclerView.Adapter<CatManagerAdapter.VH> {
        final List<String> names; final List<boolean[]> visible; final ColorConfig colors; final float dp;
        ItemTouchHelper touchHelper;
        CatManagerAdapter(List<String> n,List<boolean[]> v,ColorConfig c,float d){names=n;visible=v;colors=c;dp=d;}
        void setTouchHelper(ItemTouchHelper ith){this.touchHelper=ith;}
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent,int type){
            LinearLayout row=new LinearLayout(parent.getContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int)(4*dp),0,(int)(16*dp),0); row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,(int)(56*dp)));
            TextView drag=new TextView(parent.getContext()); drag.setText("☰"); drag.setTextSize(18f); drag.setTextColor(android.graphics.Color.parseColor("#44445A")); drag.setPadding((int)(12*dp),0,(int)(12*dp),0); drag.setTag("drag"); row.addView(drag);
            TextView name=new TextView(parent.getContext()); name.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); name.setTextSize(14f); name.setTag("name"); row.addView(name);
            CheckBox cb=new CheckBox(parent.getContext()); cb.setTag("check");
            try { android.content.res.ColorStateList csl=android.content.res.ColorStateList.valueOf(colors.radioCheckboxColor()); androidx.core.widget.CompoundButtonCompat.setButtonTintList(cb,csl); } catch(Exception ignored){}
            row.addView(cb); return new VH(row); }
        @SuppressLint("ClickableViewAccessibility") @Override public void onBindViewHolder(@NonNull VH h,int pos){
            String name=names.get(pos); boolean[] vis2=visible.get(pos);
            TextView tvName=h.row.findViewWithTag("name"); CheckBox cb=h.row.findViewWithTag("check"); View drag=h.row.findViewWithTag("drag");
            tvName.setText(name); tvName.setTextColor(vis2[0]?colors.textPrimary():colors.textSecondary());
            cb.setOnCheckedChangeListener(null); cb.setChecked(vis2[0]); cb.setOnCheckedChangeListener((b,checked)->{vis2[0]=checked;tvName.setTextColor(checked?colors.textPrimary():colors.textSecondary());});
            drag.setOnTouchListener((v,ev)->{if(ev.getActionMasked()==MotionEvent.ACTION_DOWN&&touchHelper!=null)touchHelper.startDrag(h);return false;});}
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
        Set<String>  hidden = prefs.getHiddenCategories();
        List<String> order  = prefs.getGroupOrder();

        List<RadioGroup> ordered = new ArrayList<>();
        for (String n : order) for (RadioGroup g : allGroups) if (g.getName().equals(n)) { ordered.add(g); break; }
        for (RadioGroup g : allGroups) if (!order.contains(g.getName())) ordered.add(g);

        List<RadioGroup> display = new ArrayList<>();
        for (RadioGroup g : ordered) {
            if (hidden.contains(g.getName())) continue;
            if (searchQuery.isEmpty()) { display.add(g); continue; }
            String q = searchQuery.toLowerCase();
            List<RadioStation> m = new ArrayList<>();
            for (RadioStation s : g.getStations()) if (s.getName().toLowerCase().contains(q)) m.add(s);
            if (!m.isEmpty()) display.add(new RadioGroup(g.getName(), m));
        }

        Set<String> archivedKeys = prefs.getArchived();
        if (!archivedKeys.isEmpty()) {
            List<RadioStation> arch = new ArrayList<>();
            for (String key : archivedKeys) {
                String[] p = AppPrefs.splitKey(key);
                if (p.length >= 2) arch.add(new RadioStation(p[0], p[1], p.length >= 3 ? p[2] : "", ""));
            }
            if (!arch.isEmpty()) display.add(new RadioGroup("__ARCHIVED__", arch));
        }

        adapter = new RadioGroupAdapter(display, this::onStationClicked,
                (station, isFav) -> { haptic(); Toast.makeText(requireContext(), isFav ? "♥ Added to Favourites" : "Removed from Favourites", Toast.LENGTH_SHORT).show(); },
                prefs, colors,
                new RadioGroupAdapter.SwipeActionListener() {
                    @Override public void onArchive(RadioStation s) { haptic(); prefs.addArchived(AppPrefs.stationKey(s.getName(),s.getUrl(),s.getGroup())); Toast.makeText(requireContext(),"Archived",Toast.LENGTH_SHORT).show(); refreshAdapter(); }
                    @Override public void onUnarchive(String key)   { haptic(); prefs.removeArchived(key); refreshAdapter(); }
                });
        if (selectedStation != null) adapter.setActiveStation(selectedStation);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        adapter.attachToRecyclerView(recycler);
    }

    private void onStationClicked(RadioStation station) {
        haptic(); clickSound();
        if (audioService == null) return;
        if (audioService.getCurrentRadioUrl().equals(station.getUrl())) {
            if (audioService.isRadioPlaying()) { audioService.pauseAll(); if(tvNowPlaying!=null)tvNowPlaying.setText("⏸  "+station.getName()); }
            else { audioService.resumeAll(); if(tvNowPlaying!=null)tvNowPlaying.setText(station.getName()); }
            return;
        }
        selectedStation = station;
        if (tvNowPlaying != null) tvNowPlaying.setText("Buffering…");
        if (adapter != null) adapter.setActiveStation(station);
        prefs.addLastPlayed(AppPrefs.stationKey(station.getName(), station.getUrl(), station.getGroup()));
        audioService.playRadio(station.getName(), station.getUrl(), station.getFaviconUrl());
        audioService.setRadioListener(makeListener());
    }

    private AudioService.RadioListener makeListener() {
        return new AudioService.RadioListener() {
            @Override public void onPlaybackStarted(String n) { mainHandler.post(()->{ if(tvNowPlaying!=null)tvNowPlaying.setText(n); }); }
            @Override public void onPlaybackStopped()         { mainHandler.post(()->{ selectedStation=null; if(tvNowPlaying!=null)tvNowPlaying.setText("Select a station"); if(adapter!=null)adapter.setActiveStation(null); }); }
            @Override public void onError(String m)          { mainHandler.post(()->{ if(tvNowPlaying!=null)tvNowPlaying.setText("Error – "+m); }); }
            @Override public void onBuffering()              { mainHandler.post(()->{ if(tvNowPlaying!=null&&selectedStation!=null)tvNowPlaying.setText("Buffering…  "+selectedStation.getName()); }); }
        };
    }

    private void haptic() {
        if (prefs == null || !prefs.isHapticEnabled()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) requireContext().getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) { vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(50,255)); return; }
            }
            @SuppressWarnings("deprecation") Vibrator vib = (Vibrator) requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (vib==null||!vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) vib.vibrate(VibrationEffect.createOneShot(50,255)); else vib.vibrate(50);
        } catch (Exception ignored) {}
    }

    private void clickSound() { if(audioService!=null&&prefs!=null&&prefs.isButtonSoundEnabled()) audioService.playClickSound(); }

    public void applyTheme(View root) {
        if (root==null||colors==null) return;
        root.setBackgroundColor(colors.bgPrimary());
        if (tvRadioPageTitle!=null) tvRadioPageTitle.setTextColor(colors.pageHeaderText());
if (tvNowPlaying!=null)     tvNowPlaying.setTextColor(colors.textSecondary());
if (etSearch!=null) {
etSearch.setTextColor(colors.radioSearchText()); etSearch.setHintTextColor(colors.radioSearchHint());
float dp=getResources().getDisplayMetrics().density;
GradientDrawable gd=new GradientDrawable(); gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(10*dp); gd.setColor(colors.radioSearchBg());
etSearch.setBackground(gd);
}
if (btnCategoryFilter!=null) btnCategoryFilter.setTextColor(colors.textSecondary());
}
public void refresh() {
    if (getView()==null) return;
    colors = new ColorConfig(requireContext());
    applyTheme(getView());
    refreshAdapter();
}

public RadioStation getSelectedStation() { return selectedStation; }
}