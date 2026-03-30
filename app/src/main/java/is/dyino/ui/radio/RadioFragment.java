package is.dyino.ui.radio;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

    private TextView      tvNowPlaying;
    private ImageButton   btnPlayPause, btnFavourite;
    private SeekBar       radioVolumeSeek;
    private View          volumeSliderContainer;
    private RecyclerView  recycler;

    private AppPrefs      prefs;
    private ColorConfig   colors;
    private AudioService  audioService;
    private RadioGroupAdapter adapter;
    private RadioStation  selectedStation;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setAudioService(AudioService svc) { this.audioService = svc; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_radio, c, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs  = new AppPrefs(requireContext());
        colors = new ColorConfig(requireContext());

        tvNowPlaying          = view.findViewById(R.id.tvNowPlayingTitle);
        btnPlayPause          = view.findViewById(R.id.btnPlayPause);
        btnFavourite          = view.findViewById(R.id.btnFavourite);
        radioVolumeSeek       = view.findViewById(R.id.radioVolumeSeek);
        volumeSliderContainer = view.findViewById(R.id.volumeSliderContainer);
        recycler              = view.findViewById(R.id.radioRecycler);

        applyTheme(view);
        refreshAdapter();

        btnPlayPause.setOnClickListener(v -> {
            haptic(); clickSound();
            if (selectedStation == null) return;
            if (audioService != null) audioService.pauseResumeRadio();
            updatePlayPauseIcon();
        });

        // Long press play = volume slider
        btnPlayPause.setOnLongClickListener(v -> {
            haptic();
            boolean show = volumeSliderContainer.getVisibility() == View.GONE;
            volumeSliderContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            return true;
        });

        radioVolumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && audioService != null) audioService.setRadioVolume(p / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        btnFavourite.setOnClickListener(v -> {
            haptic(); clickSound();
            if (selectedStation == null) return;
            String key = AppPrefs.stationKey(selectedStation.getName(),
                    selectedStation.getUrl(), selectedStation.getGroup());
            if (prefs.isFavourite(key)) {
                prefs.removeFavourite(key);
                btnFavourite.setImageResource(R.drawable.ic_fav_off);
            } else {
                prefs.addFavourite(key);
                btnFavourite.setImageResource(R.drawable.ic_fav_on);
            }
            refreshAdapter();
        });
    }

    private void refreshAdapter() {
        if (recycler == null) return;
        List<RadioGroup> groups = buildGroups();
        adapter = new RadioGroupAdapter(groups, this::onStationClicked, prefs, colors,
                new RadioGroupAdapter.SwipeActionListener() {
                    @Override public void onFavourite(RadioStation s) {
                        haptic();
                        prefs.addFavourite(AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup()));
                        Toast.makeText(requireContext(), "♥ Added to Favourites", Toast.LENGTH_SHORT).show();
                        refreshAdapter();
                    }
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
    }

    private List<RadioGroup> buildGroups() {
        // Favourites group first
        List<RadioStation> favStations = new ArrayList<>();
        for (String key : prefs.getFavourites()) {
            String[] p = AppPrefs.splitKey(key);
            if (p.length >= 3) favStations.add(new RadioStation(p[0], p[1], "Favourites"));
        }

        // All groups, filter out fully-archived ones
        List<RadioGroup> allGroups = RadioLoader.load(requireContext());
        List<RadioGroup> result    = new ArrayList<>();

        if (!favStations.isEmpty()) result.add(new RadioGroup("Favourites ♥", favStations));

        for (RadioGroup g : allGroups) {
            // Count non-archived stations
            long visible = g.getStations().stream().filter(s ->
                    !prefs.isArchived(AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup()))
            ).count();
            if (visible > 0) result.add(g);
        }

        // Archived section as its own group
        if (!prefs.getArchived().isEmpty()) {
            List<RadioStation> archived = new ArrayList<>();
            for (String key : prefs.getArchived()) {
                String[] p = AppPrefs.splitKey(key);
                if (p.length >= 3) archived.add(new RadioStation(p[0] + " ↩", p[1], key));
            }
            result.add(new RadioGroup("__ARCHIVED__", archived)); // special marker
        }

        return result;
    }

    private void onStationClicked(RadioStation station) {
        haptic(); clickSound();
        selectedStation = station;
        if (tvNowPlaying != null) tvNowPlaying.setText(station.getName());

        if (btnFavourite != null) {
            String key = AppPrefs.stationKey(station.getName(), station.getUrl(), station.getGroup());
            btnFavourite.setImageResource(prefs.isFavourite(key)
                    ? R.drawable.ic_fav_on : R.drawable.ic_fav_off);
        }
        if (adapter != null) adapter.setActiveStation(station);

        if (audioService != null) {
            audioService.playRadio(station.getName(), station.getUrl());
            audioService.setRadioListener(new AudioService.RadioListener() {
                @Override public void onPlaybackStarted(String n) {
                    mainHandler.post(() -> {
                        updatePlayPauseIcon();
                        if (tvNowPlaying != null) tvNowPlaying.setText(n);
                    });
                }
                @Override public void onPlaybackStopped() { mainHandler.post(() -> updatePlayPauseIcon()); }
                @Override public void onError(String m) { mainHandler.post(() -> {
                    if (tvNowPlaying != null) tvNowPlaying.setText("Error – " + m);
                }); }
                @Override public void onBuffering() { mainHandler.post(() -> {
                    if (tvNowPlaying != null) tvNowPlaying.setText("Buffering…");
                }); }
            });
        }
    }

    private void updatePlayPauseIcon() {
        if (btnPlayPause == null) return;
        boolean playing = audioService != null && audioService.isRadioPlaying();
        btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    @SuppressWarnings("deprecation")
    private void haptic() {
        if (prefs == null || !prefs.isHapticEnabled()) return;
        Vibrator vib = (Vibrator) requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
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
    }

    public void refresh() {
        if (getView() == null) return;
        colors = new ColorConfig(requireContext());
        applyTheme(getView());
        refreshAdapter();
    }
}
