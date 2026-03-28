package is.dyino.ui.radio;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import is.dyino.R;
import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;
import is.dyino.service.AudioService;
import is.dyino.util.AppPrefs;
import is.dyino.util.GifFetcher;
import is.dyino.util.RadioLoader;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class RadioFragment extends Fragment {

    private GifImageView gifView;
    private ProgressBar gifLoading;
    private ViewGroup gifContainer;
    private TextView tvNowPlaying, tvNowPlayingGroup;
    private ImageButton btnPlayPause;
    private RecyclerView recycler;
    private TextView tvGroupTitle;

    private AppPrefs prefs;
    private GifFetcher gifFetcher;
    private AudioService audioService;
    private RadioGroupAdapter adapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setAudioService(AudioService svc) {
        this.audioService = svc;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_radio, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = new AppPrefs(requireContext());
        gifFetcher = new GifFetcher();

        gifView        = view.findViewById(R.id.gifView);
        gifLoading     = view.findViewById(R.id.gifLoading);
        gifContainer   = view.findViewById(R.id.gifContainer);
        tvNowPlaying   = view.findViewById(R.id.tvNowPlayingTitle);
        tvNowPlayingGroup = view.findViewById(R.id.tvNowPlayingGroup);
        btnPlayPause   = view.findViewById(R.id.btnPlayPause);
        recycler       = view.findViewById(R.id.radioRecycler);
        tvGroupTitle   = view.findViewById(R.id.tvGroupTitle);

        applyTheme(view);

        boolean showGif = prefs.isGifEnabled();
        gifContainer.setVisibility(showGif ? View.VISIBLE : View.GONE);
        if (showGif) fetchAndShowGif();

        List<RadioGroup> groups = RadioLoader.load(requireContext());
        adapter = new RadioGroupAdapter(groups, this::onStationClicked);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        btnPlayPause.setOnClickListener(v -> {
            if (audioService != null) audioService.pauseResumeRadio();
            updatePlayPauseIcon();
        });
    }

    private void onStationClicked(RadioStation station) {
        if (tvNowPlaying != null)      tvNowPlaying.setText(station.getName());
        if (tvNowPlayingGroup != null) tvNowPlayingGroup.setText(station.getGroup());

        if (adapter != null) adapter.setActiveStation(station);

        if (audioService != null) {
            audioService.playRadio(station.getName(), station.getUrl());
            audioService.setRadioListener(new AudioService.RadioListener() {
                @Override public void onPlaybackStarted(String name) {
                    mainHandler.post(() -> updatePlayPauseIcon());
                }
                @Override public void onPlaybackStopped() {
                    mainHandler.post(() -> updatePlayPauseIcon());
                }
                @Override public void onError(String msg) {
                    mainHandler.post(() -> {
                        if (tvNowPlaying != null) tvNowPlaying.setText("Error – " + msg);
                    });
                }
                @Override public void onBuffering() {
                    mainHandler.post(() -> {
                        if (tvNowPlaying != null) tvNowPlaying.setText("Buffering…");
                    });
                }
            });
        }
    }

    private void updatePlayPauseIcon() {
        if (btnPlayPause == null) return;
        boolean playing = audioService != null && audioService.isRadioPlaying();
        btnPlayPause.setImageResource(playing
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play);
    }

    public void refreshGif() {
        if (prefs == null) return;
        boolean showGif = prefs.isGifEnabled();
        if (gifContainer != null) gifContainer.setVisibility(showGif ? View.VISIBLE : View.GONE);
        if (showGif) fetchAndShowGif();
    }

    public void applyTheme(View root) {
        if (root == null || prefs == null) return;
        root.setBackgroundColor(prefs.getBgColor());
        if (tvGroupTitle != null)  tvGroupTitle.setTextColor(prefs.getTextColor());
        if (tvNowPlaying != null)  tvNowPlaying.setTextColor(prefs.getTextColor());
    }

    private void fetchAndShowGif() {
        if (gifLoading != null) gifLoading.setVisibility(View.VISIBLE);
        String tag = prefs.getGifTag();
        gifFetcher.fetchRandomGif(tag, new GifFetcher.GifCallback() {
            @Override public void onGifUrl(String gifUrl) { loadGifFromUrl(gifUrl); }
            @Override public void onError(String error) {
                mainHandler.post(() -> { if (gifLoading != null) gifLoading.setVisibility(View.GONE); });
            }
        });
    }

    private void loadGifFromUrl(String url) {
        new Thread(() -> {
            try {
                InputStream is = new URL(url).openStream();
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
                is.close();
                GifDrawable drawable = new GifDrawable(buf.toByteArray());
                mainHandler.post(() -> {
                    if (gifView  != null) gifView.setImageDrawable(drawable);
                    if (gifLoading != null) gifLoading.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                mainHandler.post(() -> { if (gifLoading != null) gifLoading.setVisibility(View.GONE); });
            }
        }).start();
    }
}
