package is.dyino.ui.radio;

import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    private AppPrefs      prefs;
    private ColorConfig   colors;
    private AudioService  audioService;
    private RadioGroupAdapter adapter;
    private RadioStation  selectedStation;

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
                searchQuery = s.toString().trim();
                refreshAdapter();
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

    private void showCountryDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("India, Germany, USA …");
        input.setTextColor(colors.textPrimary());
        input.setHintTextColor(colors.textSecondary());
        input.setPadding(48, 32, 48, 16);

        new AlertDialog.Builder(requireContext())
            .setTitle("Radio Country")
            .setMessage("Enter your country to fetch local radio stations.")
            .setView(input)
            .setPositiveButton("Fetch", (d, w) -> {
                String c = input.getText().toString().trim();
                prefs.setRadioCountry(c);
                loadRadioStations();
            })
            .setNegativeButton("Skip (Global)", (d, w) -> {
                prefs.setRadioCountry("");
                loadRadioStations();
            })
            .setCancelable(false)
            .show();
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

        adapter = new RadioGroupAdapter(
            display,
            this::onStationClicked,
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
                    haptic();
                    prefs.removeArchived(key);
                    refreshAdapter();
                }
            });

        if (selectedStation != null) adapter.setActiveStation(selectedStation);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        adapter.attachToRecyclerView(recycler);   // enable long-press drag reorder
    }

    private List<RadioGroup> filter(List<RadioGroup> groups, String query) {
        if (query.isEmpty()) return groups;
        String q = query.toLowerCase();
        List<RadioGroup> result = new ArrayList<>();
        for (RadioGroup g : groups) {
            List<RadioStation> match = new ArrayList<>();
            for (RadioStation s : g.getStations())
                if (s.getName().toLowerCase().contains(q)) match.add(s);
            if (!match.isEmpty()) result.add(new RadioGroup(g.getName(), match));
        }
        return result;
    }

    private void onStationClicked(RadioStation station) {
        haptic(); clickSound();
        if (audioService == null) return;

        // Single click on playing station → stop it
        if (selectedStation != null && selectedStation.getUrl().equals(station.getUrl())
                && audioService.isRadioPlaying()) {
            audioService.stopRadio();
            selectedStation = null;
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

    @SuppressWarnings("deprecation")
    private void haptic() {
        if (prefs == null || !prefs.isHapticEnabled()) return;
        Vibrator vib = (Vibrator) requireContext()
            .getSystemService(android.content.Context.VIBRATOR_SERVICE);
        if (vib == null || !vib.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vib.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
        else vib.vibrate(12);
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
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
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