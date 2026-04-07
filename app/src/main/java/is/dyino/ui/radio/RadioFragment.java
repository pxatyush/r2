package is.dyino.ui.radio;

import android.annotation.SuppressLint;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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

    // ── Country dialog ───────────────────────────────────────────

    private void showCountryDialog() {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(colors.bgCard());
        int pad = (int)(24 * dp);
        container.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(requireContext());
        title.setText("Radio Country");
        title.setTextColor(colors.textPrimary());
        title.setTextSize(20f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        container.addView(title);

        TextView sub = new TextView(requireContext());
        sub.setText("Enter your country to fetch local stations");
        sub.setTextColor(colors.textSecondary());
        sub.setTextSize(13f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, (int)(6*dp), 0, (int)(20*dp));
        sub.setLayoutParams(subLp);
        container.addView(sub);

        EditText input = new EditText(requireContext());
        input.setHint("India, Germany, USA…");
        input.setTextColor(colors.radioSearchText());
        input.setHintTextColor(colors.radioSearchHint());
        input.setTextSize(15f);
        input.setPadding((int)(14*dp), (int)(12*dp), (int)(14*dp), (int)(12*dp));
        input.setSingleLine(true);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setShape(GradientDrawable.RECTANGLE);
        inputBg.setCornerRadius(10 * dp);
        inputBg.setColor(colors.settingsInputBg());
        inputBg.setStroke((int)(1*dp), colors.divider());
        input.setBackground(inputBg);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.setMargins(0, 0, 0, (int)(20*dp));
        input.setLayoutParams(inputLp);
        container.addView(input);

        LinearLayout btnRow = new LinearLayout(requireContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnSkip = makeDialogBtn("Use Global", false);
        TextView btnFetch = makeDialogBtn("Fetch", true);
        LinearLayout.LayoutParams bpSkip = new LinearLayout.LayoutParams(0, (int)(44*dp), 1f);
        bpSkip.setMargins(0, 0, (int)(8*dp), 0);
        btnSkip.setLayoutParams(bpSkip);
        btnFetch.setLayoutParams(new LinearLayout.LayoutParams(0, (int)(44*dp), 1f));
        btnRow.addView(btnSkip);
        btnRow.addView(btnFetch);
        container.addView(btnRow);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setView(container).setCancelable(false).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnSkip.setOnClickListener(v -> { prefs.setRadioCountry(""); loadRadioStations(); dialog.dismiss(); });
        btnFetch.setOnClickListener(v -> {
            String c = input.getText().toString().trim();
            prefs.setRadioCountry(c); loadRadioStations(); dialog.dismiss();
        });
        dialog.show();
    }

    private TextView makeDialogBtn(String label, boolean primary) {
        float dp = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(requireContext());
        tv.setText(label); tv.setGravity(Gravity.CENTER); tv.setTextSize(14f);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(10 * dp);
        if (primary) { gd.setColor(colors.accent()); tv.setTextColor(0xFFFFFFFF); }
        else { gd.setColor(colors.bgCard2()); gd.setStroke((int)(1*dp), colors.divider()); tv.setTextColor(colors.textSecondary()); }
        tv.setBackground(gd); tv.setClickable(true); tv.setFocusable(true);
        return tv;
    }

    // ── Category manager dialog ─────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private void showCategoryManager() {
        if (allGroups.isEmpty()) return;
        float dp = getResources().getDisplayMetrics().density;

        // Build ordered working list from saved prefs order, then any new ones
        List<String> savedOrder = prefs.getGroupOrder();
        Set<String>  hidden     = prefs.getHiddenCategories();
        List<String> catNames   = new ArrayList<>();

        // First: saved order entries that still exist
        for (String n : savedOrder)
            for (RadioGroup g : allGroups)
                if (g.getName().equals(n) && !catNames.contains(n)) { catNames.add(n); break; }
        // Then: any new groups not yet in saved order
        for (RadioGroup g : allGroups)
            if (!catNames.contains(g.getName())) catNames.add(g.getName());

        // Mutable working copy of visibility
        List<boolean[]> visibleFlags = new ArrayList<>();
        for (String n : catNames) visibleFlags.add(new boolean[]{!hidden.contains(n)});

        // Root container
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setShape(GradientDrawable.RECTANGLE); rootBg.setCornerRadius(16 * dp);
        rootBg.setColor(colors.bgCard());
        root.setBackground(rootBg);
        root.setClipToOutline(true);

        // Title bar
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("Categories");
        tvTitle.setTextColor(colors.textPrimary());
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        tvTitle.setPadding((int)(20*dp), (int)(18*dp), (int)(20*dp), (int)(12*dp));
        root.addView(tvTitle);

        View divTop = new View(requireContext());
        divTop.setBackgroundColor(colors.divider());
        divTop.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        root.addView(divTop);

        // RecyclerView for the category rows
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rv.setLayoutParams(rvLp);
        root.addView(rv);

        View divBot = new View(requireContext());
        divBot.setBackgroundColor(colors.divider());
        divBot.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        root.addView(divBot);

        // Done button
        TextView btnDone = new TextView(requireContext());
        btnDone.setText("Done");
        btnDone.setGravity(Gravity.CENTER);
        btnDone.setTextColor(colors.accent());
        btnDone.setTextSize(15f);
        btnDone.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        btnDone.setPadding(0, (int)(14*dp), 0, (int)(16*dp));
        btnDone.setClickable(true); btnDone.setFocusable(true);
        root.addView(btnDone);

        // Adapter
        CatManagerAdapter catAdapter = new CatManagerAdapter(catNames, visibleFlags, colors, dp);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(catAdapter);

        // Drag-to-reorder via ItemTouchHelper
        ItemTouchHelper.Callback cb = new ItemTouchHelper.Callback() {
            @Override public int getMovementFlags(@NonNull RecyclerView r2, @NonNull RecyclerView.ViewHolder vh) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }
            @Override public boolean onMove(@NonNull RecyclerView r2,
                                            @NonNull RecyclerView.ViewHolder from,
                                            @NonNull RecyclerView.ViewHolder to) {
                int f = from.getAdapterPosition(), t = to.getAdapterPosition();
                if (f < 0 || t < 0) return false;
                Collections.swap(catNames, f, t);
                Collections.swap(visibleFlags, f, t);
                catAdapter.notifyItemMoved(f, t);
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int d) {}
            @Override public boolean isLongPressDragEnabled() { return false; }
            @Override public void onSelectedChanged(RecyclerView.ViewHolder vh, int state) {
                super.onSelectedChanged(vh, state);
                if (state == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                    haptic(); vh.itemView.setAlpha(0.85f); vh.itemView.setScaleX(1.02f); vh.itemView.setScaleY(1.02f);
                }
            }
            @Override public void clearView(@NonNull RecyclerView r2, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(r2, vh);
                vh.itemView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
            }
        };
        ItemTouchHelper ith = new ItemTouchHelper(cb);
        ith.attachToRecyclerView(rv);
        catAdapter.setTouchHelper(ith);

        // Max height = 60% of screen
        int maxH = (int)(getResources().getDisplayMetrics().heightPixels * 0.60f);
        FrameLayout wrapper = new FrameLayout(requireContext());
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.min(maxH, catNames.size() * (int)(56*dp) + (int)(140*dp)));
        wrapper.setLayoutParams(wlp);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setView(root).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnDone.setOnClickListener(v -> {
            // Save new order
            prefs.saveGroupOrder(catNames);
            // Save hidden set
            Set<String> newHidden = new java.util.HashSet<>();
            for (int i = 0; i < catNames.size(); i++)
                if (!visibleFlags.get(i)[0]) newHidden.add(catNames.get(i));
            prefs.setHiddenCategories(newHidden);
            dialog.dismiss();
            refreshAdapter();
        });

        dialog.show();
        // Size the dialog to 90% screen width
        if (dialog.getWindow() != null) {
            int w = (int)(getResources().getDisplayMetrics().widthPixels * 0.90f);
            dialog.getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // ── Category manager RecyclerView adapter ────────────────────

    static class CatManagerAdapter extends RecyclerView.Adapter<CatManagerAdapter.VH> {
        private final List<String>    names;
        private final List<boolean[]> visible;
        private final ColorConfig     colors;
        private final float           dp;
        private ItemTouchHelper       touchHelper;

        CatManagerAdapter(List<String> names, List<boolean[]> visible, ColorConfig colors, float dp) {
            this.names = names; this.visible = visible; this.colors = colors; this.dp = dp;
        }
        void setTouchHelper(ItemTouchHelper ith) { this.touchHelper = ith; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            // Row: [drag handle] [name] [checkbox]
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int)(4*dp), 0, (int)(16*dp), 0);
            row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(56*dp)));

            // Drag handle
            TextView drag = new TextView(parent.getContext());
            drag.setId(View.generateViewId());
            drag.setText("☰");
            drag.setTextSize(18f);
            drag.setTextColor(Color.parseColor("#44445A"));
            drag.setPadding((int)(12*dp), 0, (int)(12*dp), 0);
            drag.setTag("drag");
            row.addView(drag);

            // Name
            TextView name = new TextView(parent.getContext());
            name.setId(View.generateViewId());
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            name.setLayoutParams(nlp);
            name.setTextSize(14f);
            name.setTag("name");
            row.addView(name);

            // Checkbox
            CheckBox cb = new CheckBox(parent.getContext());
            cb.setTag("check");
            row.addView(cb);

            return new VH(row);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            String name  = names.get(pos);
            boolean[] vis = visible.get(pos);

            TextView tvName = h.row.findViewWithTag("name");
            CheckBox cb     = h.row.findViewWithTag("check");
            View     drag   = h.row.findViewWithTag("drag");

            tvName.setText(name);
            tvName.setTextColor(vis[0] ? colors.textPrimary() : colors.textSecondary());

            cb.setOnCheckedChangeListener(null);
            cb.setChecked(vis[0]);
            cb.setOnCheckedChangeListener((btn, checked) -> {
                vis[0] = checked;
                tvName.setTextColor(checked ? colors.textPrimary() : colors.textSecondary());
            });

            drag.setOnTouchListener((v, ev) -> {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && touchHelper != null)
                    touchHelper.startDrag(h);
                return false;
            });

            // Divider between rows (except last)
            h.row.setBackground(null);
        }

        @Override public int getItemCount() { return names.size(); }

        static class VH extends RecyclerView.ViewHolder {
            LinearLayout row;
            VH(LinearLayout v) { super(v); row = v; }
        }
    }

    // ── Station loading ──────────────────────────────────────────

    private void loadRadioStations() {
        if (tvNowPlaying != null) tvNowPlaying.setText("Loading…");
        RadioLoader.load(requireContext(), prefs, groups -> {
            allGroups = groups;
            refreshAdapter();
            if (tvNowPlaying != null) tvNowPlaying.setText("Select a station");
        });
    }

    private void refreshAdapter() {
        if (recycler == null) return;

        Set<String> hidden = prefs.getHiddenCategories();
        List<String> savedOrder = prefs.getGroupOrder();

        // 1. Apply saved order to allGroups
        List<RadioGroup> ordered = new ArrayList<>();
        for (String n : savedOrder)
            for (RadioGroup g : allGroups)
                if (g.getName().equals(n)) { ordered.add(g); break; }
        for (RadioGroup g : allGroups)
            if (!savedOrder.contains(g.getName())) ordered.add(g);

        // 2. Filter hidden, then apply search
        List<RadioGroup> display = new ArrayList<>();
        for (RadioGroup g : ordered) {
            if (hidden.contains(g.getName())) continue;
            if (searchQuery.isEmpty()) {
                display.add(g);
            } else {
                String q = searchQuery.toLowerCase();
                List<RadioStation> m = new ArrayList<>();
                for (RadioStation s : g.getStations())
                    if (s.getName().toLowerCase().contains(q)) m.add(s);
                if (!m.isEmpty()) display.add(new RadioGroup(g.getName(), m));
            }
        }

        // 3. Append Archive group at the very bottom (always, regardless of search)
        Set<String> archivedKeys = prefs.getArchived();
        if (!archivedKeys.isEmpty()) {
            List<RadioStation> archStations = new ArrayList<>();
            for (String key : archivedKeys) {
                String[] parts = AppPrefs.splitKey(key);
                if (parts.length >= 2)
                    archStations.add(new RadioStation(parts[0], parts[1],
                        parts.length >= 3 ? parts[2] : "", ""));
            }
            if (!archStations.isEmpty())
                display.add(new RadioGroup("__ARCHIVED__", archStations));
        }

        adapter = new RadioGroupAdapter(display, this::onStationClicked,
            (station, isFav) -> {
                haptic();
                Toast.makeText(requireContext(),
                    isFav ? "♥ Added to Favourites" : "Removed from Favourites",
                    Toast.LENGTH_SHORT).show();
            },
            prefs, colors,
            new RadioGroupAdapter.SwipeActionListener() {
                @Override public void onArchive(RadioStation s) {
                    haptic();
                    prefs.addArchived(AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup()));
                    Toast.makeText(requireContext(), "Archived", Toast.LENGTH_SHORT).show();
                    refreshAdapter();
                }
                @Override public void onUnarchive(String key) {
                    haptic(); prefs.removeArchived(key); refreshAdapter();
                }
            });

        if (selectedStation != null) adapter.setActiveStation(selectedStation);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        adapter.attachToRecyclerView(recycler);
    }

    private void onStationClicked(RadioStation station) {
        haptic(); clickSound();
        if (audioService == null) return;
        if (selectedStation != null && selectedStation.getUrl().equals(station.getUrl())
                && audioService.isRadioPlaying()) {
            audioService.stopRadio(); selectedStation = null;
            if (tvNowPlaying != null) tvNowPlaying.setText("Select a station");
            if (adapter != null) adapter.setActiveStation(null);
            return;
        }
        selectedStation = station;
        if (tvNowPlaying != null) tvNowPlaying.setText(station.getName());
        if (adapter != null) adapter.setActiveStation(station);
        audioService.playRadio(station.getName(), station.getUrl(), station.getFaviconUrl());
        audioService.setRadioListener(makeListener());
    }

    private AudioService.RadioListener makeListener() {
        return new AudioService.RadioListener() {
            @Override public void onPlaybackStarted(String n) {
                mainHandler.post(() -> { if (tvNowPlaying != null) tvNowPlaying.setText(n); });
            }
            @Override public void onPlaybackStopped() {
                mainHandler.post(() -> { if (tvNowPlaying != null) tvNowPlaying.setText("Select a station"); });
            }
            @Override public void onError(String m) {
                mainHandler.post(() -> { if (tvNowPlaying != null) tvNowPlaying.setText("Error – " + m); });
            }
            @Override public void onBuffering() {
                mainHandler.post(() -> { if (tvNowPlaying != null) tvNowPlaying.setText("Buffering…"); });
            }
        };
    }

    private void haptic() {
        if (prefs == null || !prefs.isHapticEnabled()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) requireContext()
                    .getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(18, 200));
                    return;
                }
            }
            @SuppressWarnings("deprecation")
            Vibrator vib = (Vibrator) requireContext()
                .getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vib.vibrate(VibrationEffect.createOneShot(18, 200));
            else vib.vibrate(18);
        } catch (Exception ignored) {}
    }

    private void clickSound() {
        if (audioService != null && prefs != null && prefs.isButtonSoundEnabled())
            audioService.playClickSound();
    }

    public void applyTheme(View root) {
        if (root == null || colors == null) return;
        root.setBackgroundColor(colors.bgPrimary());
        if (tvNowPlaying != null) tvNowPlaying.setTextColor(colors.textSecondary());
        if (etSearch != null) {
            etSearch.setTextColor(colors.radioSearchText());
            etSearch.setHintTextColor(colors.radioSearchHint());
            float dp = getResources().getDisplayMetrics().density;
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(10 * dp);
            gd.setColor(colors.radioSearchBg());
            etSearch.setBackground(gd);
        }
        if (btnCategoryFilter != null) {
            btnCategoryFilter.setTextColor(colors.textSecondary());
        }
    }

    public void refresh() {
        if (getView() == null) return;
        colors = new ColorConfig(requireContext());
        applyTheme(getView());
        refreshAdapter();
    }

    public RadioStation getSelectedStation() { return selectedStation; }
}
