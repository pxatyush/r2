package is.dyino.ui.radio;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

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

        if (!prefs.hasRadioCountry()) showCountryDialog();
        else loadRadioStations();
    }

    // ── Country dialog styled to match app ──────────────────────

    private void showCountryDialog() {
        float dp = getResources().getDisplayMetrics().density;

        // Root container
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(colors.bgCard());
        int pad = (int)(24 * dp);
        container.setPadding(pad, pad, pad, pad);

        // Title
        TextView title = new TextView(requireContext());
        title.setText("Radio Country");
        title.setTextColor(colors.textPrimary());
        title.setTextSize(20f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        container.addView(title);

        // Subtitle
        TextView sub = new TextView(requireContext());
        sub.setText("Enter your country to fetch local stations");
        sub.setTextColor(colors.textSecondary());
        sub.setTextSize(13f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, (int)(6*dp), 0, (int)(20*dp));
        sub.setLayoutParams(subLp);
        container.addView(sub);

        // Input field
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

        // Button row
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
            .setView(container)
            .setCancelable(false)
            .create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnSkip.setOnClickListener(v -> {
            prefs.setRadioCountry("");
            loadRadioStations();
            dialog.dismiss();
        });
        btnFetch.setOnClickListener(v -> {
            String c = input.getText().toString().trim();
            prefs.setRadioCountry(c);
            loadRadioStations();
            dialog.dismiss();
        });

        dialog.show();
    }

    private TextView makeDialogBtn(String label, boolean primary) {
        float dp = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setTextSize(14f);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(10 * dp);
        if (primary) {
            gd.setColor(colors.accent());
            tv.setTextColor(0xFFFFFFFF);
        } else {
            gd.setColor(colors.bgCard2());
            gd.setStroke((int)(1*dp), colors.divider());
            tv.setTextColor(colors.textSecondary());
        }
        tv.setBackground(gd);
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

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
        List<RadioGroup> display = filter(allGroups, searchQuery);
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

    private List<RadioGroup> filter(List<RadioGroup> groups, String query) {
        if (query.isEmpty()) return groups;
        String q = query.toLowerCase();
        List<RadioGroup> r = new ArrayList<>();
        for (RadioGroup g : groups) {
            List<RadioStation> m = new ArrayList<>();
            for (RadioStation s : g.getStations())
                if (s.getName().toLowerCase().contains(q)) m.add(s);
            if (!m.isEmpty()) r.add(new RadioGroup(g.getName(), m));
        }
        return r;
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
                    vm.getDefaultVibrator().vibrate(
                        VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
                    return;
                }
            }
            @SuppressWarnings("deprecation")
            Vibrator vib = (Vibrator) requireContext()
                .getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vib.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
            else vib.vibrate(12);
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
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(10 * dp);
            gd.setColor(colors.radioSearchBg());
            etSearch.setBackground(gd);
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