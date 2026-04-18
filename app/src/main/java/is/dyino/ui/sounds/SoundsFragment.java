package is.dyino.ui.sounds;

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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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
    private TextView     tvSoundsTitle;

    private AppPrefs     prefs;
    private ColorConfig  colors;
    private AudioService audioService;
    private List<SoundCategory> categories;

    private final java.util.Map<String, Long> lastTapTime = new java.util.HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            mainHandler.post(() -> { refreshAllButtons(); updateActiveLabel(); });
        }
    };

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
        tvSoundsTitle       = view.findViewById(R.id.tvSoundsPageTitle);

        applyTheme(view);
        categories = SoundLoader.load(requireContext());
        buildGrid();

        btnStopAll.setOnClickListener(v -> {
            haptic(); clickSound();
            if (audioService != null) audioService.stopAllSounds();
            if (categories != null)
                for (SoundCategory cat : categories)
                    for (SoundItem s : cat.getSounds()) s.setPlaying(false);
            refreshAllButtons();
            updateActiveLabel();
        });
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

    // ── Build grid ────────────────────────────────────────────────
    private void buildGrid() {
        if (categoriesContainer == null || categories == null) return;
        categoriesContainer.removeAllViews();

        float dp    = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int navW    = (int)(52 * dp);
        int avail   = screenW - navW;
        int gap     = (int)(6 * dp);
        int btnW    = (avail - gap * 3) / 2;
        int btnH    = (int)(80 * dp); // taller to show emoji + name

        for (SoundCategory cat : categories) {
            // ── Category header ───────────────────────────────────
            LinearLayout header = new LinearLayout(requireContext());
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(android.view.Gravity.CENTER_VERTICAL);
            header.setTag("cat_header"); // skip in refreshAllButtons

            // Accent left bar
            View bar = new View(requireContext());
            bar.setBackgroundColor(colors.accent());
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams((int)(3*dp), (int)(16*dp));
            barLp.setMargins(0, 0, (int)(10*dp), 0);
            bar.setLayoutParams(barLp);
            header.addView(bar);

            TextView tvCat = new TextView(requireContext());
            tvCat.setText(cat.getName().toUpperCase());
            tvCat.setTextColor(colors.textSectionTitle());
            tvCat.setTextSize(11f);
            tvCat.setLetterSpacing(0.12f);
            tvCat.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.NORMAL));
            header.addView(tvCat);

            LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            catLp.setMargins((int)(12*dp), (int)(22*dp), (int)(12*dp), (int)(10*dp));
            header.setLayoutParams(catLp);
            categoriesContainer.addView(header);

            // ── Sound buttons in 2-column rows ────────────────────
            List<SoundItem> sounds = cat.getSounds();
            for (int i = 0; i < sounds.size(); i += 2) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setTag("button_row");
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(gap, 0, gap, gap);
                row.setLayoutParams(rowLp);

                row.addView(inflateBtn(sounds.get(i), btnW, btnH));

                View sp = new View(requireContext());
                sp.setLayoutParams(new LinearLayout.LayoutParams(gap, btnH));
                row.addView(sp);

                if (i + 1 < sounds.size()) {
                    row.addView(inflateBtn(sounds.get(i + 1), btnW, btnH));
                } else {
                    View ph = new View(requireContext());
                    ph.setLayoutParams(new LinearLayout.LayoutParams(btnW, btnH));
                    row.addView(ph);
                }
                categoriesContainer.addView(row);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private View inflateBtn(SoundItem sound, int btnW, int btnH) {
        View btn        = LayoutInflater.from(requireContext())
                            .inflate(R.layout.item_sound_button, null, false);
        TextView tvEmoji = btn.findViewById(R.id.tvSoundEmoji);
        TextView tvName  = btn.findViewById(R.id.tvSoundName);
        WaveView wave    = btn.findViewById(R.id.waveView);

        btn.setLayoutParams(new LinearLayout.LayoutParams(btnW, btnH));
        if (tvEmoji != null) tvEmoji.setText(sound.getEmoji());
        tvName.setText(sound.getName());
        tvName.setTextColor(colors.soundBtnText());

        applyBtnShape(btn, sound, wave);
        if (wave != null) {
            wave.setVolume(sound.getVolume());
            wave.setVolumeDragListener(vol -> {
                sound.setVolume(vol);
                if (audioService != null) audioService.setSoundVolume(sound.getFileName(), vol);
            });
        }

        final float[]   downY  = {0f};
        final boolean[] inDrag = {false};

        btn.setOnLongClickListener(v -> {
            inDrag[0] = true;
            haptic();
            float vol = audioService != null
                    ? audioService.getSoundVolume(sound.getFileName()) : sound.getVolume();
            if (wave != null) {
                wave.setVolume(vol); sound.setVolume(vol);
                if (!wave.isWaving()) {
                    int wc = (colors.soundWaveColor() & 0x00FFFFFF) | 0x88000000;
                    wave.setColors(colors.soundBtnActiveBg(), wc);
                    wave.setVisibility(View.VISIBLE); wave.startWave();
                }
                wave.beginVolumeDrag(downY[0]);
            }
            return true;
        });

        btn.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY[0] = ev.getY(); inDrag[0] = false; break;
                case MotionEvent.ACTION_MOVE:
                    if (inDrag[0] && wave != null && wave.isDragging()) {
                        wave.handleDragMove(ev.getY()); return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (inDrag[0] && wave != null) wave.endDrag();
                    inDrag[0] = false; break;
            }
            return false;
        });

        btn.setOnClickListener(v -> {
            if (wave != null && wave.isDragging()) return;
            String fn  = sound.getFileName();
            long   now = System.currentTimeMillis();
            Long   lst = lastTapTime.get(fn);

            if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                lastTapTime.remove(fn); haptic();
                boolean fav = prefs.isFavSound(fn);
                if (fav) prefs.removeFavSound(fn); else prefs.addFavSound(fn);
                Toast.makeText(requireContext(),
                        fav ? "Removed from Favourites" : "♥ Added to Favourites",
                        Toast.LENGTH_SHORT).show();
            } else {
                lastTapTime.put(fn, now);
                haptic(); clickSound();
                if (audioService == null) return;
                if (audioService.isSoundPlaying(fn)) {
                    audioService.stopSound(fn);
                    sound.setPlaying(false);
                    if (wave != null) { wave.stopWave(); wave.setVisibility(View.INVISIBLE); }
                } else {
                    sound.setPlaying(true);
                    if (wave != null) {
                        int wc = (colors.soundWaveColor() & 0x00FFFFFF) | 0x5A000000;
                        wave.setColors(colors.soundBtnActiveBg(), wc);
                        wave.setVolume(sound.getVolume());
                        wave.setVisibility(View.VISIBLE); wave.startWave();
                    }
                    applyBtnShapeActive(btn);
                    audioService.playSound(fn, sound.getVolume());
                }
                if (!sound.isPlaying()) applyBtnShape(btn, sound, wave);
                updateActiveLabel();
            }
        });

        return btn;
    }

    private void applyBtnShape(View btn, SoundItem sound, WaveView wave) {
        boolean playing = audioService != null && audioService.isSoundPlaying(sound.getFileName());
        float dp = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(16 * dp);
        if (playing) {
            gd.setColor(colors.soundBtnActiveBg());
            gd.setStroke((int)(1.5f*dp), colors.soundBtnActiveBorder());
        } else {
            gd.setColor(colors.soundBtnBg());
        }
        btn.setBackground(gd);
        btn.setClipToOutline(true);

        if (wave != null) {
            if (playing) {
                int wc = (colors.soundWaveColor() & 0x00FFFFFF) | 0x5A000000;
                wave.setColors(colors.soundBtnActiveBg(), wc);
                float vol = audioService != null
                        ? audioService.getSoundVolume(sound.getFileName()) : sound.getVolume();
                wave.setVolume(vol); wave.setVisibility(View.VISIBLE);
                if (!wave.isWaving()) wave.startWave();
            } else { wave.stopWave(); wave.setVisibility(View.INVISIBLE); }
        }
    }

    private void applyBtnShapeActive(View btn) {
        float dp = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(16 * dp);
        gd.setColor(colors.soundBtnActiveBg());
        gd.setStroke((int)(1.5f*dp), colors.soundBtnActiveBorder());
        btn.setBackground(gd);
        btn.setClipToOutline(true);
    }

    private void refreshAllButtons() {
        if (categoriesContainer == null) return;
        for (int i = 0; i < categoriesContainer.getChildCount(); i++) {
            View child = categoriesContainer.getChildAt(i);
            // Skip category headers (tagged) and non-row views
            if (!"button_row".equals(child.getTag())) continue;
            LinearLayout row = (LinearLayout) child;
            for (int j = 0; j < row.getChildCount(); j++) {
                View     btn = row.getChildAt(j);
                TextView tv  = btn.findViewById(R.id.tvSoundName);
                WaveView wv  = btn.findViewById(R.id.waveView);
                if (tv == null || categories == null) continue;
                for (SoundCategory cat : categories)
                    for (SoundItem s : cat.getSounds())
                        if (s.getName().equals(tv.getText().toString()))
                            applyBtnShape(btn, s, wv);
            }
        }
    }

    private void updateActiveLabel() {
        if (tvActiveSoundsLabel == null) return;
        int cnt = audioService != null ? audioService.getAllPlayingSounds().size() : 0;
        tvActiveSoundsLabel.setText(cnt == 0
                ? "No sounds playing"
                : cnt + " sound" + (cnt > 1 ? "s" : "") + " playing");
    }

    private void haptic() {
        if (prefs == null || !prefs.isHapticEnabled()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) requireContext()
                        .getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) { vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(50, 255)); return; }
            }
            @SuppressWarnings("deprecation")
            Vibrator vib = (Vibrator) requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vib.vibrate(VibrationEffect.createOneShot(50, 255));
            else vib.vibrate(50);
        } catch (Exception ignored) {}
    }

    private void clickSound() {
        if (audioService != null && prefs != null && prefs.isButtonSoundEnabled())
            audioService.playClickSound();
    }

    public void applyTheme(View root) {
        if (root == null || colors == null) return;
        root.setBackgroundColor(colors.bgPrimary());

        if (tvSoundsTitle != null)       tvSoundsTitle.setTextColor(colors.pageHeaderText());
        if (tvActiveSoundsLabel != null) tvActiveSoundsLabel.setTextColor(colors.textSecondary());

        if (btnStopAll != null) {
            float dp = getResources().getDisplayMetrics().density;
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(20 * dp);
            gd.setColor(colors.stopAllBg()); gd.setStroke((int)(1.5f*dp), colors.stopAllBorder());
            btnStopAll.setBackground(gd); btnStopAll.setTextColor(colors.stopAllText());
        }
    }

    public void refresh() {
        if (getView() == null) return;
        colors = new ColorConfig(requireContext());
        applyTheme(getView());
        categories = SoundLoader.load(requireContext());
        buildGrid();
        updateActiveLabel();
    }
}