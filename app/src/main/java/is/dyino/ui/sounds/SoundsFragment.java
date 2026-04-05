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
    private TextView     tvActiveSoundsLabel;

    private AppPrefs     prefs;
    private ColorConfig  colors;
    private AudioService audioService;
    private List<SoundCategory> categories;

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
        tvActiveSoundsLabel = view.findViewById(R.id.tvActiveSoundsLabel);

        applyTheme(view);
        categories = SoundLoader.load(requireContext());
        buildGrid();

        btnStopAll.setOnClickListener(v -> {
            haptic(); clickSound();
            if (audioService != null) audioService.stopAllSounds();
            for (SoundCategory cat : categories)
                for (SoundItem s : cat.getSounds()) s.setPlaying(false);
            refreshAllButtons();
            updateActiveLabel();
        });
    }

    // ── Build using LinearLayout rows — reliable, no GridLayout column spec bugs ──

    private void buildGrid() {
        if (categoriesContainer == null || categories == null) return;
        categoriesContainer.removeAllViews();

        float dp    = getResources().getDisplayMetrics().density;
        int navW    = (int)(52 * dp);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int avail   = screenW - navW;
        int gap     = (int)(5 * dp);
        int btnW    = (avail - gap * 3) / 2;
        int btnH    = (int)(52 * dp);

        for (SoundCategory cat : categories) {
            // Category label
            TextView tvCat = new TextView(requireContext());
            tvCat.setText(cat.getName());
            tvCat.setTextColor(colors.textSectionTitle());
            tvCat.setTextSize(17f);
            tvCat.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
            LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            catLp.setMargins((int)(12*dp), (int)(18*dp), (int)(12*dp), (int)(8*dp));
            tvCat.setLayoutParams(catLp);
            categoriesContainer.addView(tvCat);

            List<SoundItem> sounds = cat.getSounds();
            for (int i = 0; i < sounds.size(); i += 2) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(gap, 0, gap, gap);
                row.setLayoutParams(rowLp);

                View btn0 = inflateBtn(sounds.get(i), btnW, btnH);
                row.addView(btn0);

                View spacer = new View(requireContext());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(gap, btnH));
                row.addView(spacer);

                if (i + 1 < sounds.size()) {
                    View btn1 = inflateBtn(sounds.get(i + 1), btnW, btnH);
                    row.addView(btn1);
                } else {
                    View placeholder = new View(requireContext());
                    placeholder.setLayoutParams(new LinearLayout.LayoutParams(btnW, btnH));
                    row.addView(placeholder);
                }

                categoriesContainer.addView(row);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private View inflateBtn(SoundItem sound, int btnW, int btnH) {
        View btn    = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_sound_button, null, false);
        TextView tvName = btn.findViewById(R.id.tvSoundName);
        WaveView wave   = btn.findViewById(R.id.waveView);
        View volOverlay = btn.findViewById(R.id.volumeBarOverlay);
        View volFill    = btn.findViewById(R.id.volumeBarFill);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnW, btnH);
        btn.setLayoutParams(lp);

        tvName.setText(sound.getName());
        tvName.setTextColor(colors.soundBtnText());

        applyBtnShape(btn, sound, wave);
        if (wave != null) wave.setVolume(sound.getVolume());

        final float[] lpStartX   = {0};
        final float[] lpStartVol = {sound.getVolume()};
        final boolean[] volShowing = {false};
        final Runnable[] dismiss   = {null};

        btn.setOnLongClickListener(v -> {
            haptic();
            if (volShowing[0]) return true;
            lpStartVol[0] = audioService != null
                ? audioService.getSoundVolume(sound.getFileName()) : sound.getVolume();
            if (wave != null) { wave.stopWave(); wave.setVisibility(View.INVISIBLE); }
            if (volOverlay != null) {
                volOverlay.setVisibility(View.VISIBLE);
                updateVolFill(volFill, volOverlay, lpStartVol[0]);
            }
            volShowing[0] = true;
            scheduleDismiss(dismiss, volOverlay, wave, sound, volShowing);
            return true;
        });

        btn.setOnTouchListener((v, ev) -> {
            if (!volShowing[0]) return false;
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lpStartX[0] = ev.getX(); break;
                case MotionEvent.ACTION_MOVE:
                    float delta  = (ev.getX() - lpStartX[0]) / (float) btnW;
                    float newVol = Math.max(0f, Math.min(1f, lpStartVol[0] + delta));
                    sound.setVolume(newVol);
                    if (audioService != null) audioService.setSoundVolume(sound.getFileName(), newVol);
                    updateVolFill(volFill, volOverlay, newVol);
                    if (dismiss[0] != null) mainHandler.removeCallbacks(dismiss[0]);
                    scheduleDismiss(dismiss, volOverlay, wave, sound, volShowing);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    lpStartVol[0] = sound.getVolume(); break;
            }
            return false;
        });

        btn.setOnClickListener(v -> {
            if (volShowing[0]) {
                if (dismiss[0] != null) mainHandler.removeCallbacks(dismiss[0]);
                doDismiss(volOverlay, wave, sound, volShowing);
                return;
            }
            String fn  = sound.getFileName();
            long   now = System.currentTimeMillis();
            Long   last = lastTapTime.get(fn);

            if (last != null && (now - last) < DOUBLE_TAP_MS) {
                lastTapTime.remove(fn);
                haptic();
                boolean isFav = prefs.isFavSound(fn);
                if (isFav) prefs.removeFavSound(fn); else prefs.addFavSound(fn);
                Toast.makeText(requireContext(),
                    isFav ? "Removed from Favourites" : "♥ Added to Favourites",
                    Toast.LENGTH_SHORT).show();
            } else {
                lastTapTime.put(fn, now);
                haptic(); clickSound();
                if (audioService == null) return;
                if (audioService.isSoundPlaying(fn)) {
                    audioService.stopSound(fn);
                    sound.setPlaying(false);
                } else {
                    sound.setPlaying(true);
                    // Instant glow + wave before audio is even prepared
                    applyBtnShape(btn, sound, wave);
                    if (wave != null) {
                        int wc = (colors.soundWaveColor() & 0x00FFFFFF) | 0x5A000000;
                        wave.setColors(colors.soundBtnActiveBg(), wc);
                        wave.setVolume(sound.getVolume());
                        wave.setVisibility(View.VISIBLE);
                        wave.startWave();
                    }
                    audioService.playSound(fn, sound.getVolume());
                }
                applyBtnShape(btn, sound, wave);
                updateActiveLabel();
            }
        });

        return btn;
    }

    private void scheduleDismiss(Runnable[] holder, View ov, WaveView wave,
                                 SoundItem sound, boolean[] showing) {
        holder[0] = () -> doDismiss(ov, wave, sound, showing);
        mainHandler.postDelayed(holder[0], 3000);
    }

    private void doDismiss(View ov, WaveView wave, SoundItem sound, boolean[] showing) {
        if (ov != null) ov.setVisibility(View.GONE);
        showing[0] = false;
        boolean p = audioService != null && audioService.isSoundPlaying(sound.getFileName());
        if (wave != null && p) { wave.setVisibility(View.VISIBLE); if (!wave.isWaving()) wave.startWave(); }
    }

    private void updateVolFill(View fill, View track, float vol) {
        if (fill == null || track == null) return;
        track.post(() -> {
            int w = track.getWidth();
            if (w == 0) return;
            ViewGroup.LayoutParams lp = fill.getLayoutParams();
            lp.width = (int)(w * vol);
            fill.setLayoutParams(lp);
        });
    }

    private void applyBtnShape(View btn, SoundItem sound, WaveView wave) {
        boolean playing = audioService != null && audioService.isSoundPlaying(sound.getFileName());
        float dp = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(14 * dp);
        if (playing) {
            gd.setColor(colors.soundBtnActiveBg());
            gd.setStroke((int)(1.5f * dp), colors.soundBtnActiveBorder());
        } else {
            gd.setColor(colors.soundBtnBg());
        }
        btn.setBackground(gd);

        if (wave != null) {
            if (playing) {
                int wc = (colors.soundWaveColor() & 0x00FFFFFF) | 0x5A000000;
                wave.setColors(colors.soundBtnActiveBg(), wc);
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
            View child = categoriesContainer.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) child;
            for (int j = 0; j < row.getChildCount(); j++) {
                View btn    = row.getChildAt(j);
                TextView tv = btn.findViewById(R.id.tvSoundName);
                WaveView wv = btn.findViewById(R.id.waveView);
                if (tv == null) continue;
                String name = tv.getText().toString();
                for (SoundCategory cat : categories)
                    for (SoundItem s : cat.getSounds())
                        if (s.getName().equals(name)) applyBtnShape(btn, s, wv);
            }
        }
    }

    private void updateActiveLabel() {
        if (tvActiveSoundsLabel == null || audioService == null) return;
        int cnt = audioService.getAllPlayingSounds().size();
        tvActiveSoundsLabel.setText(
            cnt == 0 ? "No sounds playing" : cnt + " sound" + (cnt > 1 ? "s" : "") + " playing");
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
        if (btnStopAll != null) {
            float dp = getResources().getDisplayMetrics().density;
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(20 * dp);
            gd.setColor(colors.stopAllBg());
            gd.setStroke((int)(1.5f * dp), colors.stopAllBorder());
            btnStopAll.setBackground(gd);
            btnStopAll.setTextColor(colors.stopAllText());
        }
    }

    public void refresh() {
        if (getView() == null) return;
        colors = new ColorConfig(requireContext());
        applyTheme(getView());
        buildGrid();
        updateActiveLabel();
    }
}