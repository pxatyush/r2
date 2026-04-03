package is.dyino.ui.sounds;

import android.annotation.SuppressLint;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

import is.dyino.R;
import is.dyino.model.SoundCategory;
import is.dyino.model.SoundItem;
import is.dyino.service.AudioService;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;
import is.dyino.util.SoundLoader;

public class SoundsFragment extends Fragment {

    private LinearLayout categoriesContainer;
    private TextView     btnStopAll;

    private AppPrefs     prefs;
    private ColorConfig  colors;
    private AudioService audioService;
    private List<SoundCategory> categories;

    // Double-tap detection per sound file
    private final java.util.Map<String, Long> lastTapTime = new java.util.HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setAudioService(AudioService svc) { this.audioService = svc; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_sounds, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs  = new AppPrefs(requireContext());
        colors = new ColorConfig(requireContext());

        categoriesContainer = view.findViewById(R.id.soundsCategoriesContainer);
        btnStopAll          = view.findViewById(R.id.btnStopAll);

        applyTheme(view);
        categories = SoundLoader.load(requireContext());
        buildGrid();

        btnStopAll.setOnClickListener(v -> {
            haptic(); clickSound();
            if (audioService != null) audioService.stopAllSounds();
            for (SoundCategory cat : categories)
                for (SoundItem s : cat.getSounds()) s.setPlaying(false);
            refreshAllButtons();
        });
    }

    private void buildGrid() {
        if (categoriesContainer == null || categories == null) return;
        categoriesContainer.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(requireContext());
        float dp     = getResources().getDisplayMetrics().density;
        int navW     = (int)(52 * dp);
        int screenW  = getResources().getDisplayMetrics().widthPixels;
        int avail    = screenW - navW;
        int gap      = (int)(6 * dp);
        // Each button gets exactly half the available width minus gaps
        int btnW     = (avail - gap * 3) / 2;
        int btnH     = (int)(56 * dp);

        for (SoundCategory cat : categories) {
            View catView = inf.inflate(R.layout.item_sound_category, categoriesContainer, false);
            TextView tvCat  = catView.findViewById(R.id.tvCategoryName);
            GridLayout grid = catView.findViewById(R.id.soundGrid);
            tvCat.setText(cat.getName());
            tvCat.setTextColor(colors.textSectionTitle());
            grid.setColumnCount(2);
            grid.removeAllViews();

            List<SoundItem> sounds = cat.getSounds();
            for (int i = 0; i < sounds.size(); i++) {
                SoundItem sound = sounds.get(i);
                View btn = inf.inflate(R.layout.item_sound_button, null);
                int col = i % 2;
                int row = i / 2;
                setupButton(btn, sound, btnW, btnH, col, row);
                grid.addView(btn);
            }
            categoriesContainer.addView(catView);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupButton(View btn, SoundItem sound, int btnW, int btnH, int col, int row) {
        TextView   tvName   = btn.findViewById(R.id.tvSoundName);
        WaveView   wave     = btn.findViewById(R.id.waveView);
        View       volBar   = btn.findViewById(R.id.volumeBarOverlay);
        View       volFill  = btn.findViewById(R.id.volumeBarFill);

        tvName.setText(sound.getName());
        tvName.setTextColor(colors.soundBtnText());

        float dp = getResources().getDisplayMetrics().density;

        // ── Fix GridLayout proportions: both columns equal ──
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.columnSpec = GridLayout.spec(col, 1, 1f);
        lp.rowSpec    = GridLayout.spec(row);
        lp.width      = btnW;
        lp.height     = btnH;
        int margin    = (int)(3 * dp);
        lp.setMargins(margin, margin, margin, margin);
        btn.setLayoutParams(lp);

        applyBtnShape(btn, sound, wave);

        // ── Init wave volume immediately (no delay) ──
        if (wave != null) wave.setVolume(sound.getVolume());

        // ── Long press: show horizontal iOS-style volume bar ──
        final float[] longPressStartX = {0};
        final float[] longPressStartVol = {sound.getVolume()};
        final boolean[] volumeBarVisible = {false};
        final Runnable[] autoDismiss = {null};

        btn.setOnLongClickListener(v -> {
            haptic();
            longPressStartVol[0] = audioService != null
                ? audioService.getSoundVolume(sound.getFileName()) : sound.getVolume();
            showVolumeBar(volBar, volFill, longPressStartVol[0], wave);
            volumeBarVisible[0] = true;

            // Auto-dismiss after 3 seconds
            if (autoDismiss[0] != null) mainHandler.removeCallbacks(autoDismiss[0]);
            autoDismiss[0] = () -> {
                hideVolumeBar(volBar, wave, audioService != null
                        && audioService.isSoundPlaying(sound.getFileName()));
                volumeBarVisible[0] = false;
            };
            mainHandler.postDelayed(autoDismiss[0], 3000);
            return true;
        });

        btn.setOnTouchListener((v, ev) -> {
            if (!volumeBarVisible[0]) return false;

            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    longPressStartX[0] = ev.getX();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = ev.getX() - longPressStartX[0];
                    float delta = dx / (float) btnW;     // -1..+1 across full width
                    float newVol = Math.max(0f, Math.min(1f, longPressStartVol[0] + delta));
                    sound.setVolume(newVol);
                    if (audioService != null) audioService.setSoundVolume(sound.getFileName(), newVol);
                    // Update fill width
                    updateVolumeBarFill(volFill, newVol);
                    // Reset auto-dismiss timer on interaction
                    if (autoDismiss[0] != null) mainHandler.removeCallbacks(autoDismiss[0]);
                    mainHandler.postDelayed(autoDismiss[0], 3000);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    longPressStartVol[0] = sound.getVolume();
                    break;
            }
            return false;
        });

        // ── Single/double tap: play/stop or toggle favourite ──
        btn.setOnClickListener(v -> {
            if (volumeBarVisible[0]) {
                // Tap while volume bar is up: dismiss it
                if (autoDismiss[0] != null) mainHandler.removeCallbacks(autoDismiss[0]);
                hideVolumeBar(volBar, wave, audioService != null
                        && audioService.isSoundPlaying(sound.getFileName()));
                volumeBarVisible[0] = false;
                return;
            }

            String fn = sound.getFileName();
            long now = System.currentTimeMillis();
            Long last = lastTapTime.get(fn);

            if (last != null && (now - last) < DOUBLE_TAP_MS) {
                // ── Double-tap: toggle favourite ──
                lastTapTime.remove(fn);
                haptic();
                boolean isFav = prefs.isFavSound(fn);
                if (isFav) prefs.removeFavSound(fn);
                else       prefs.addFavSound(fn);
                Toast.makeText(requireContext(),
                    isFav ? "Removed from Favourites" : "♥ Added to Favourites",
                    Toast.LENGTH_SHORT).show();
            } else {
                // ── Single tap: toggle play/stop ──
                lastTapTime.put(fn, now);
                haptic(); clickSound();
                if (audioService == null) return;
                if (audioService.isSoundPlaying(fn)) {
                    audioService.stopSound(fn);
                    sound.setPlaying(false);
                } else {
                    // Start wave immediately before prepareAsync
                    sound.setPlaying(true);
                    if (wave != null) {
                        float wc = (colors.soundWaveColor() & 0x00FFFFFF) | 0x5A000000;
                        wave.setColors((int) colors.soundBtnActiveBg(), (int) wc);
                        wave.setVolume(sound.getVolume());
                        wave.setVisibility(View.VISIBLE);
                        wave.startWave();   // instant — no prepare delay
                    }
                    audioService.playSound(fn, sound.getVolume());
                }
                applyBtnShape(btn, sound, wave);
            }
        });
    }

    private void showVolumeBar(View volBar, View volFill, float currentVol, WaveView wave) {
        if (volBar == null) return;
        // Hide wave while volume bar is showing
        if (wave != null) {
            wave.stopWave();
            wave.setVisibility(View.INVISIBLE);
        }
        volBar.setVisibility(View.VISIBLE);
        updateVolumeBarFill(volFill, currentVol);
    }

    private void hideVolumeBar(View volBar, WaveView wave, boolean soundIsPlaying) {
        if (volBar == null) return;
        volBar.setVisibility(View.GONE);
        // Restore wave if sound still playing
        if (wave != null && soundIsPlaying) {
            wave.setVisibility(View.VISIBLE);
            wave.startWave();
        }
    }

    private void updateVolumeBarFill(View volFill, float vol) {
        if (volFill == null || volFill.getParent() == null) return;
        View parent = (View) volFill.getParent();
        parent.post(() -> {
            int parentW = parent.getWidth();
            ViewGroup.LayoutParams lp = volFill.getLayoutParams();
            lp.width = (int)(parentW * vol);
            volFill.setLayoutParams(lp);
        });
    }

    private void applyBtnShape(View btn, SoundItem sound, WaveView wave) {
        boolean playing = audioService != null && audioService.isSoundPlaying(sound.getFileName());
        float dp = getResources().getDisplayMetrics().density;
        float r  = 14 * dp;

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(r);
        if (playing) {
            gd.setColor(colors.soundBtnActiveBg());
            gd.setStroke((int)(1.5f * dp), colors.soundBtnActiveBorder());
        } else {
            gd.setColor(colors.soundBtnBg());
        }
        btn.setBackground(gd);

        if (wave != null) {
            if (playing) {
                float wc = (colors.soundWaveColor() & 0x00FFFFFF) | 0x5A000000;
                wave.setColors((int) colors.soundBtnActiveBg(), (int) wc);
                float vol = audioService != null
                    ? audioService.getSoundVolume(sound.getFileName()) : sound.getVolume();
                wave.setVolume(vol);
                wave.setVisibility(View.VISIBLE);
                if (!wave.isWaving()) wave.startWave();
            } else {
                wave.stopWave();
                wave.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void refreshAllButtons() {
        if (categoriesContainer == null) return;
        for (int i = 0; i < categoriesContainer.getChildCount(); i++) {
            View catView = categoriesContainer.getChildAt(i);
            GridLayout grid = catView.findViewById(R.id.soundGrid);
            if (grid == null) continue;
            for (int j = 0; j < grid.getChildCount(); j++) {
                View btn = grid.getChildAt(j);
                WaveView wave = btn.findViewById(R.id.waveView);
                TextView tvName = btn.findViewById(R.id.tvSoundName);
                if (tvName == null) continue;
                String name = tvName.getText().toString();
                for (SoundCategory cat : categories)
                    for (SoundItem s : cat.getSounds())
                        if (s.getName().equals(name)) applyBtnShape(btn, s, wave);
            }
        }
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
    }

    public void refresh() {
        if (getView() == null) return;
        colors = new ColorConfig(requireContext());
        applyTheme(getView());
        buildGrid();
    }
}