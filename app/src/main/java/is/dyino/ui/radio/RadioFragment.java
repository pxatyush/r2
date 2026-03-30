package is.dyino.ui.radio;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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

    private TextView      tvNowPlaying, tvNowPlayingGroup;
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

    public void setAudioService(AudioService svc) {
        this.audioService = svc;
    }

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
        tvNowPlayingGroup     = null; // removed from new layout
        btnPlayPause          = view.findViewById(R.id.btnPlayPause);
        btnFavourite          = view.findViewById(R.id.btnFavourite);
        radioVolumeSeek       = view.findViewById(R.id.radioVolumeSeek);
        volumeSliderContainer = view.findViewById(R.id.volumeSliderContainer);
        recycler              = view.findViewById(R.id.radioRecycler);

        applyTheme(view);

        // Build radio groups - Favourites first
        List<RadioGroup> groups = buildGroupsWithFavourites();
        adapter = new RadioGroupAdapter(groups, this::onStationClicked, prefs, colors);
        adapter.setSwipeListener(new RadioGroupAdapter.SwipeActionListener() {
            @Override public void onFavourite(RadioStation s) {
                String key = AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup());
                if (!prefs.isFavourite(key)) {
                    prefs.addFavourite(key);
                    Toast.makeText(requireContext(), "Added to Favourites", Toast.LENGTH_SHORT).show();
                }
                refreshGroups();
            }
            @Override public void onArchive(RadioStation s) {
                String key = AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup());
                prefs.addArchived(key);
                Toast.makeText(requireContext(), "Archived", Toast.LENGTH_SHORT).show();
                refreshGroups();
            }
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        // Play/Pause
        btnPlayPause.setOnClickListener(v -> {
            if (selectedStation == null) return;
            if (audioService != null) audioService.pauseResumeRadio();
            updatePlayPauseIcon();
        });

        // Long press play button = show volume slider
        btnPlayPause.setOnLongClickListener(v -> {
            boolean show = volumeSliderContainer.getVisibility() == View.GONE;
            volumeSliderContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            return true;
        });

        // Volume seek
        radioVolumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                if (audioService != null) audioService.setRadioVolume(prog / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        // Favourite button
        btnFavourite.setOnClickListener(v -> {
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
            refreshGroups();
        });
    }

    private List<RadioGroup> buildGroupsWithFavourites() {
        List<RadioGroup> all = RadioLoader.load(requireContext());
        List<RadioStation> favStations = new ArrayList<>();

        for (String key : prefs.getFavourites()) {
            String[] p = AppPrefs.splitKey(key);
            if (p.length >= 3) favStations.add(new RadioStation(p[0], p[1], "Favourites"));
        }

        List<RadioGroup> result = new ArrayList<>();
        if (!favStations.isEmpty()) result.add(new RadioGroup("Favourites ♥", favStations));
        result.addAll(all);
        return result;
    }

    private void refreshGroups() {
        List<RadioGroup> groups = buildGroupsWithFavourites();
        adapter = new RadioGroupAdapter(groups, this::onStationClicked, prefs, colors);
        adapter.setSwipeListener(new RadioGroupAdapter.SwipeActionListener() {
            @Override public void onFavourite(RadioStation s) {
                prefs.addFavourite(AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup()));
                Toast.makeText(requireContext(), "Added to Favourites", Toast.LENGTH_SHORT).show();
                refreshGroups();
            }
            @Override public void onArchive(RadioStation s) {
                prefs.addArchived(AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup()));
                Toast.makeText(requireContext(), "Archived", Toast.LENGTH_SHORT).show();
                refreshGroups();
            }
        });
        if (selectedStation != null) adapter.setActiveStation(selectedStation);
        recycler.setAdapter(adapter);
    }

    private void onStationClicked(RadioStation station) {
        selectedStation = station;
        if (tvNowPlaying != null) tvNowPlaying.setText(station.getName());

        // Update fav icon
        if (btnFavourite != null) {
            String key = AppPrefs.stationKey(station.getName(), station.getUrl(), station.getGroup());
            btnFavourite.setImageResource(
                    prefs.isFavourite(key) ? R.drawable.ic_fav_on : R.drawable.ic_fav_off);
        }

        if (audioService != null) {
            audioService.playRadio(station.getName(), station.getUrl());
            audioService.setRadioListener(new AudioService.RadioListener() {
                @Override public void onPlaybackStarted(String name) {
                    mainHandler.post(() -> { updatePlayPauseIcon(); if (tvNowPlaying != null) tvNowPlaying.setText(name); });
                }
                @Override public void onPlaybackStopped() { mainHandler.post(() -> updatePlayPauseIcon()); }
                @Override public void onError(String msg) { mainHandler.post(() -> { if (tvNowPlaying != null) tvNowPlaying.setText("Error"); }); }
                @Override public void onBuffering()       { mainHandler.post(() -> { if (tvNowPlaying != null) tvNowPlaying.setText("Buffering…"); }); }
            });
        }
    }

    private void updatePlayPauseIcon() {
        if (btnPlayPause == null) return;
        boolean playing = audioService != null && audioService.isRadioPlaying();
        btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    public void applyTheme(View root) {
        if (root == null || colors == null) return;
        root.setBackgroundColor(colors.bgPrimary());
        if (tvNowPlaying != null) tvNowPlaying.setTextColor(colors.textSecondary());
    }

    public void refresh() {
        if (recycler == null) return;
        colors = new ColorConfig(requireContext());
        if (getView() != null) applyTheme(getView());
        refreshGroups();
    }
}
