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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import is.dyino.R;
import is.dyino.model.SoundCategory;
import is.dyino.model.SoundItem;
import is.dyino.service.AudioService;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;
import is.dyino.util.SoundLoader;

/**
 * Sounds page.
 *
 * Layout per category:
 * • Category name (bold section heading)
 * • HorizontalScrollView containing small square chips
 * (same size as Home page active-sounds chips: 84dp × 64dp)
 *
 * Visual update is INSTANT on tap — no waiting for a broadcast.
 * Broadcasts still call syncAllChips() as a safety net for remote stops.
 */
public class SoundsFragment extends Fragment {

    private LinearLayout categoriesContainer;
    private TextView     btnStopAll;
    private TextView     tvActiveSoundsLabel;
    private TextView     tvSoundsTitle;

    private AppPrefs    prefs;
    private ColorConfig colors;
    private AudioService audioService;
    private List<SoundCategory> categories;

    // Keyed by fileName for O(1) sync
    private final Map<String, FrameLayout> chipMap = new HashMap<>();
    private final Map<String, WaveView>    waveMap = new HashMap<>();

    private final Map<String, Long> lastTapTime = new HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            // Safety sync for stops triggered externally (home page, notification)
            mainHandler.post(() -> { syncAllChips(); updateActiveLabel(); });
        }
    };

    public void setAudioService(AudioService svc) {
        this.audioService = svc;
        syncAllChips();
        updateActiveLabel();
    }

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
        buildCategories();

        btnStopAll.setOnClickListener(v -> {
            haptic(); clickSound();
            if (audioService != null) audioService.stopAllSounds();
            syncAllChips();
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

    /** Re-sync whenever page becomes visible (covers stops from Home). */
    @Override public void onResume() {
        super.onResume();
        syncAllChips();
        updateActiveLabel();
    }

    // ── Chip dimensions ───────────────────────────────────────────
    // Match Home page active-sounds chips exactly.
    private static final int CHIP_W_DP = 84;
    private static final int CHIP_H_DP = 64;

    // ── Build layout ──────────────────────────────────────────────

    private void buildCategories() {
        if (categoriesContainer == null || categories == null) return;
        categoriesContainer.removeAllViews();
        chipMap.clear();
        waveMap.clear();

        float dp   = getResources().getDisplayMetrics().density;
        int chipW  = (int)(CHIP_W_DP * dp);
        int chipH  = (int)(CHIP_H_DP * dp);
        int gap    = (int)(8  * dp);
        int padH   = (int)(16 * dp);

        for (SoundCategory cat : categories) {

            // ── Category heading ──────────────────────────────────
            LinearLayout heading = new LinearLayout(requireContext());
            heading.setOrientation(LinearLayout.HORIZONTAL);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            headLp.setMargins(padH, (int)(22*dp), padH, (int)(8*dp));
            heading.setLayoutParams(headLp);

            // Accent left bar
            View bar = new View(requireContext());
            bar.setBackgroundColor(colors.accent());
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams((int)(3*dp), (int)(14*dp));
            barLp.setMargins(0, 0, (int)(10*dp), 0);
            bar.setLayoutParams(barLp);
            heading.addView(bar);

            TextView tvCat = new TextView(requireContext());
            tvCat.setText(cat.getName().toUpperCase());
            tvCat.setTextColor(colors.textSectionTitle());
            tvCat.setTextSize(11f);
            tvCat.setLetterSpacing(0.12f);
            tvCat.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.NORMAL));
            heading.addView(tvCat);
            categoriesContainer.addView(heading);

            // ── Horizontal scroll row of chips ────────────────────
            HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
            hsv.setHorizontalScrollBarEnabled(false);
            hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hsvLp.setMargins(0, 0, 0, (int)(6*dp));
            hsv.setLayoutParams(hsvLp);

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(padH, 0, padH, 0);

            List<SoundItem> sounds = cat.getSounds();
            for (int i = 0; i < sounds.size(); i++) {
                if (i > 0) {
                    View sp = new View(requireContext());
                    sp.setLayoutParams(new LinearLayout.LayoutParams(gap, chipH));
                    row.addView(sp);
                }
                row.addView(buildChip(sounds.get(i), chipW, chipH, dp));
            }

            hsv.addView(row);
            categoriesContainer.addView(hsv);
        }
    }

    // ── Build a single chip ───────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private FrameLayout buildChip(SoundItem sound, int chipW, int chipH, float dp) {
        FrameLayout frame = new FrameLayout(requireContext());
        frame.setLayoutParams(new LinearLayout.LayoutParams(chipW, chipH));
        frame.setClipToOutline(true);
        frame.setClickable(true);
        frame.setFocusable(true);
        frame.setLongClickable(true);

        // Wave fill (behind label)
        WaveView wave = new WaveView(requireContext());
        wave.setPowerSaving(prefs.isPowerSavingEnabled());
        wave.setVolume(sound.getVolume());
        frame.addView(wave, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Label
        TextView tvName = new TextView(requireContext());
        tvName.setText(sound.getName());
        tvName.setTextColor(colors.soundBtnText());
        tvName.setTextSize(11f);
        tvName.setGravity(Gravity.CENTER);
        tvName.setSingleLine(true);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvName.setPadding((int)(4*dp), (int)(4*dp), (int)(4*dp), (int)(4*dp));
        tvName.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        frame.addView(tvName, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Register for fast sync
        chipMap.put(sound.getFileName(), frame);
        waveMap.put(sound.getFileName(), wave);

        // Apply initial visual state
        applyChipStyle(frame, wave, sound);

        // Volume drag listener
        wave.setVolumeDragListener(vol -> {
            sound.setVolume(vol);
            if (audioService != null) audioService.setSoundVolume(sound.getFileName(), vol);
        });

        final float[]   downY  = {0f};
        final boolean[] inDrag = {false};

        frame.setOnLongClickListener(v -> {
            inDrag[0] = true; haptic();
            float cur = audioService != null
                    ? audioService.getSoundVolume(sound.getFileName()) : sound.getVolume();
            wave.setVolume(cur);
            wave.beginVolumeDrag(downY[0]);
            return true;
        });

        frame.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY[0] = ev.getY(); inDrag[0] = false; break;
                case MotionEvent.ACTION_MOVE:
                    if (inDrag[0] && wave.isDragging()) { wave.handleDragMove(ev.getY()); return true; } break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    if (inDrag[0]) wave.endDrag(); inDrag[0] = false; break;
            }
            return false;
        });

        frame.setOnClickListener(v -> {
            if (wave.isDragging()) return;

            String fn  = sound.getFileName();
            long   now = System.currentTimeMillis();
            Long   lst = lastTapTime.get(fn);

            if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                // Double-tap → favourite
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
                } else {
                    audioService.playSound(fn, sound.getVolume());
                }

                // ── INSTANT visual update — don't wait for broadcast ──
                applyChipStyle(frame, wave, sound);
                updateActiveLabel();
            }
        });

        return frame;
    }

    // ── Chip visual state ─────────────────────────────────────────

    private void applyChipStyle(FrameLayout frame, WaveView wave, SoundItem sound) {
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
        frame.setBackground(gd);

        if (playing) {
            float vol = audioService.getSoundVolume(sound.getFileName());
            int wc = (colors.soundWaveColor() & 0x00FFFFFF) | 0x6A000000;
            wave.setColors(colors.soundBtnActiveBg(), wc);
            wave.setVolume(vol);
            wave.setVisibility(View.VISIBLE);
            if (!wave.isWaving() && !prefs.isPowerSavingEnabled()) wave.startWave();
        } else {
            wave.stopWave();
            wave.setVisibility(View.INVISIBLE);
        }
    }

    // ── Sync all chips from AudioService state ────────────────────

    private void syncAllChips() {
        if (categories == null) return;
        for (SoundCategory cat : categories) {
            for (SoundItem sound : cat.getSounds()) {
                FrameLayout frame = chipMap.get(sound.getFileName());
                WaveView    wave  = waveMap.get(sound.getFileName());
                if (frame != null && wave != null) applyChipStyle(frame, wave, sound);
            }
        }
    }

    // ── Active label ──────────────────────────────────────────────

    private void updateActiveLabel() {
        if (tvActiveSoundsLabel == null) return;
        int cnt = audioService != null ? audioService.getAllPlayingSounds().size() : 0;
        tvActiveSoundsLabel.setText(cnt == 0
                ? "No sounds playing"
                : cnt + " sound" + (cnt > 1 ? "s" : "") + " playing");
        tvActiveSoundsLabel.setTextColor(colors.textSecondary());
    }

    // ── Haptic / click ────────────────────────────────────────────

    private void haptic() {
        if (prefs == null || !prefs.isHapticEnabled()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) requireContext()
                        .getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) { vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(50,255)); return; }
            }
            @SuppressWarnings("deprecation")
            Vibrator vib = (Vibrator) requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (vib==null||!vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) vib.vibrate(VibrationEffect.createOneShot(50,255));
            else vib.vibrate(50);
        } catch (Exception ignored) {}
    }

    private void clickSound() {
        if (audioService!=null && prefs!=null && prefs.isButtonSoundEnabled())
            audioService.playClickSound();
    }

    // ── Theme ─────────────────────────────────────────────────────

    public void applyTheme(View root) {
        if (root==null||colors==null) return;
        root.setBackgroundColor(colors.bgPrimary());
        if (tvSoundsTitle       != null) tvSoundsTitle.setTextColor(colors.pageHeaderText());
        if (tvActiveSoundsLabel != null) tvActiveSoundsLabel.setTextColor(colors.textSecondary());
        if (btnStopAll != null) {
            float dp = getResources().getDisplayMetrics().density;
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(20*dp);
            gd.setColor(colors.stopAllBg()); gd.setStroke((int)(1.5f*dp), colors.stopAllBorder());
            btnStopAll.setBackground(gd); btnStopAll.setTextColor(colors.stopAllText());
        }
    }

    public void refresh() {
        if (getView()==null) return;
        colors     = new ColorConfig(requireContext());
        categories = SoundLoader.load(requireContext());
        applyTheme(getView());
        buildCategories();
        syncAllChips();
        updateActiveLabel();
    }
}
