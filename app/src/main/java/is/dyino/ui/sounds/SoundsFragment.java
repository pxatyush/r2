package is.dyino.ui.sounds;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import is.dyino.R;
import is.dyino.model.SoundCategory;
import is.dyino.model.SoundItem;
import is.dyino.service.AudioService;
import is.dyino.util.AppPrefs;
import is.dyino.util.GifFetcher;
import is.dyino.util.SoundLoader;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class SoundsFragment extends Fragment {

    private GifImageView gifView;
    private ProgressBar gifLoading;
    private ViewGroup gifContainer;
    private LinearLayout categoriesContainer;
    private TextView btnStopAll;

    private AppPrefs prefs;
    private GifFetcher gifFetcher;
    private AudioService audioService;
    private List<SoundCategory> categories;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setAudioService(AudioService svc) {
        this.audioService = svc;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sounds, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs      = new AppPrefs(requireContext());
        gifFetcher = new GifFetcher();

        gifView              = view.findViewById(R.id.gifViewSounds);
        gifLoading           = view.findViewById(R.id.gifLoadingSounds);
        gifContainer         = view.findViewById(R.id.gifContainerSounds);
        categoriesContainer  = view.findViewById(R.id.soundsCategoriesContainer);
        btnStopAll           = view.findViewById(R.id.btnStopAll);

        applyTheme(view);

        boolean showGif = prefs.isGifEnabled();
        gifContainer.setVisibility(showGif ? View.VISIBLE : View.GONE);
        if (showGif) fetchAndShowGif();

        categories = SoundLoader.load(requireContext());
        buildSoundGrid();

        btnStopAll.setOnClickListener(v -> {
            if (audioService != null) audioService.stopAllSounds();
            for (SoundCategory cat : categories)
                for (SoundItem s : cat.getSounds()) s.setPlaying(false);
            refreshAllButtons();
        });
    }

    private void buildSoundGrid() {
        if (categoriesContainer == null || categories == null) return;
        categoriesContainer.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        float density = getResources().getDisplayMetrics().density;
        int navWidthPx  = (int) (64 * density);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int available   = screenWidth - navWidthPx - 1;
        int gap         = (int) (8 * density);
        int btnWidth    = (available - gap * 3) / 2;
        int btnHeight   = (int) (80 * density);

        for (SoundCategory cat : categories) {
            View catView = inflater.inflate(R.layout.item_sound_category, categoriesContainer, false);
            TextView tvCat  = catView.findViewById(R.id.tvCategoryName);
            GridLayout grid = catView.findViewById(R.id.soundGrid);

            tvCat.setText(cat.getName());
            tvCat.setTextColor(prefs.getTextColor());
            grid.setColumnCount(2);
            grid.removeAllViews();

            for (SoundItem sound : cat.getSounds()) {
                View btn = inflater.inflate(R.layout.item_sound_button, null);
                TextView tvIcon = btn.findViewById(R.id.tvSoundIcon);
                TextView tvName = btn.findViewById(R.id.tvSoundName);

                tvIcon.setText(sound.getEmoji());
                tvName.setText(sound.getName());
                tvName.setTextColor(prefs.getTextColor());

                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width  = btnWidth;
                lp.height = btnHeight;
                lp.setMargins(gap / 2, gap / 2, gap / 2, gap / 2);
                btn.setLayoutParams(lp);

                updateButtonState(btn, sound);

                btn.setOnClickListener(v -> {
                    if (audioService == null) return;
                    if (audioService.isSoundPlaying(sound.getFileName())) {
                        audioService.stopSound(sound.getFileName());
                        sound.setPlaying(false);
                    } else {
                        audioService.playSound(sound.getFileName(), sound.getVolume());
                        sound.setPlaying(true);
                    }
                    updateButtonState(btn, sound);
                });

                setupVolumeSlider(btn, sound);
                grid.addView(btn);
            }

            categoriesContainer.addView(catView);
        }
    }

    private void updateButtonState(View btn, SoundItem sound) {
        boolean playing = audioService != null && audioService.isSoundPlaying(sound.getFileName());
        btn.setBackgroundResource(playing
                ? R.drawable.bg_sound_button_active
                : R.drawable.bg_sound_button);

        View overlay = btn.findViewById(R.id.volumeOverlay);
        if (overlay == null) return;

        if (playing) {
            overlay.setVisibility(View.VISIBLE);
            float vol = audioService != null
                    ? audioService.getSoundVolume(sound.getFileName())
                    : sound.getVolume();
            btn.post(() -> {
                ViewGroup.LayoutParams olp = overlay.getLayoutParams();
                olp.width = (int) (btn.getWidth() * vol);
                overlay.setLayoutParams(olp);
            });
        } else {
            overlay.setVisibility(View.GONE);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupVolumeSlider(View btn, SoundItem sound) {
        final boolean[] longPressed = {false};

        btn.setOnLongClickListener(v -> {
            longPressed[0] = true;
            View overlay = btn.findViewById(R.id.volumeOverlay);
            if (overlay != null) overlay.setVisibility(View.VISIBLE);
            return true;
        });

        btn.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_MOVE && longPressed[0]) {
                float ratio = Math.max(0f, Math.min(1f, event.getX() / v.getWidth()));
                sound.setVolume(ratio);
                if (audioService != null) audioService.setSoundVolume(sound.getFileName(), ratio);
                View overlay = btn.findViewById(R.id.volumeOverlay);
                if (overlay != null) {
                    ViewGroup.LayoutParams lp = overlay.getLayoutParams();
                    lp.width = (int) (v.getWidth() * ratio);
                    overlay.setLayoutParams(lp);
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                longPressed[0] = false;
            }
            return false;
        });
    }

    private void refreshAllButtons() {
        if (categoriesContainer == null || categories == null) return;
        for (int i = 0; i < categoriesContainer.getChildCount(); i++) {
            View catView = categoriesContainer.getChildAt(i);
            GridLayout grid = catView.findViewById(R.id.soundGrid);
            if (grid == null) continue;
            for (int j = 0; j < grid.getChildCount(); j++) {
                View btn = grid.getChildAt(j);
                TextView tvName = btn.findViewById(R.id.tvSoundName);
                if (tvName == null) continue;
                String name = tvName.getText().toString();
                for (SoundCategory cat : categories) {
                    for (SoundItem sound : cat.getSounds()) {
                        if (sound.getName().equals(name)) {
                            updateButtonState(btn, sound);
                        }
                    }
                }
            }
        }
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
    }

    private void fetchAndShowGif() {
        if (gifLoading != null) gifLoading.setVisibility(View.VISIBLE);
        gifFetcher.fetchRandomGif(prefs.getGifTag(), new GifFetcher.GifCallback() {
            @Override public void onGifUrl(String gifUrl) { loadGifFromUrl(gifUrl); }
            @Override public void onError(String e) {
                mainHandler.post(() -> { if (gifLoading != null) gifLoading.setVisibility(View.GONE); });
            }
        });
    }

    private void loadGifFromUrl(String url) {
        new Thread(() -> {
            try {
                InputStream is = new URL(url).openStream();
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096]; int n;
                while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
                is.close();
                GifDrawable drawable = new GifDrawable(buf.toByteArray());
                mainHandler.post(() -> {
                    if (gifView   != null) gifView.setImageDrawable(drawable);
                    if (gifLoading != null) gifLoading.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                mainHandler.post(() -> { if (gifLoading != null) gifLoading.setVisibility(View.GONE); });
            }
        }).start();
    }
}
