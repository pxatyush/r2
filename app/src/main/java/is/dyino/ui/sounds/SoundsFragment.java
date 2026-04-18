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

/**
 * Sounds page — 3-column grid of home-chip-style square cards.
 * Each card has an animated WaveView behind a centred label,
 * long-press for volume drag, single-tap to toggle, double-tap
 * to favourite — identical interaction to the home active-sounds chips.
 */
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

    // ── State broadcast ───────────────────────────────────────────
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            mainHandler.post(() -> { syncAllChips(); updateActiveLabel(); });
        }
    };

    public void setAudioService(AudioService svc) { this.audioService = svc; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup c, @Nullable Bundle s) {
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

    /**
     * Always re-sync chip states when the page becomes visible.
     * This fixes the stale-state bug when sounds are stopped from Home.
     */
    @Override public void onResume() {
        super.onResume();
        syncAllChips();
        updateActiveLabel();
    }

    // ── Grid construction ─────────────────────────────────────────

    private static final int COLS = 3;

    private void buildGrid() {
        if (categoriesContainer == null || categories == null) return;
        categoriesContainer.removeAllViews();

        float dp      = getResources().getDisplayMetrics().density;
        int   screenW = getResources().getDisplayMetrics().widthPixels;
        int   navW    = (int)(52  * dp);
        int   padH    = (int)(12  * dp);   // left + right padding of the container
        int   gap     = (int)(8   * dp);
        int   avail   = screenW - navW - 2 * padH;
        // Square chips: width = (avail - gap*(COLS-1)) / COLS
        int   chip    = (avail - gap * (COLS - 1)) / COLS;

        for (SoundCategory cat : categories) {

            // ── Category header ───────────────────────────────────
            LinearLayout header = new LinearLayout(requireContext());
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setTag("cat_header");

            View bar = new View(requireContext());
            bar.setBackgroundColor(colors.accent());
            LinearLayout.LayoutParams barLp =
                    new LinearLayout.LayoutParams((int)(3*dp), (int)(14*dp));
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
            catLp.setMargins(padH, (int)(20*dp), padH, (int)(10*dp));
            header.setLayoutParams(catLp);
            categoriesContainer.addView(header);

            // ── Chip rows (COLS per row) ──────────────────────────
            List<SoundItem> sounds = cat.getSounds();
            for (int i = 0; i < sounds.size(); i += COLS) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setTag("chip_row");

                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(padH, 0, padH, gap);
                row.setLayoutParams(rowLp);

                for (int col = 0; col < COLS; col++) {
                    if (col > 0) {
                        View sp = new View(requireContext());
                        sp.setLayoutParams(new LinearLayout.LayoutParams(gap, chip));
                        row.addView(sp);
                    }
                    int idx = i + col;
                    if (idx < sounds.size()) {
                        row.addView(buildChip(sounds.get(idx), chip, chip));
                    } else {
                        // Placeholder so partial rows keep correct widths
                        View ph = new View(requireContext());
                        ph.setLayoutParams(new LinearLayout.LayoutParams(chip, chip));
                        row.addView(ph);
                    }
                }
                categoriesContainer.addView(row);
            }
        }
    }

    // ── Build a single chip ───────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private View buildChip(SoundItem sound, int chipW, int chipH) {
        FrameLayout frame = new FrameLayout(requireContext());
        frame.setLayoutParams(new LinearLayout.LayoutParams(chipW, chipH));
        frame.setClipToOutline(true);
        frame.setClickable(true);
        frame.setFocusable(true);
        frame.setLongClickable(true);

        // Wave fill layer
        WaveView wave = new WaveView(requireContext());
        wave.setPowerSaving(prefs.isPowerSavingEnabled());
        wave.setVolume(sound.getVolume());
        FrameLayout.LayoutParams wLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        frame.addView(wave, wLp);

        // Label
        TextView tvName = new TextView(requireContext());
        tvName.setText(sound.getName());
        tvName.setTextColor(colors.soundBtnText());
        tvName.setTextSize(12f);
        tvName.setGravity(Gravity.CENTER);
        tvName.setSingleLine(true);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvName.setPadding(dp(6), dp(4), dp(6), dp(4));
        tvName.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        tvName.setId(R.id.tvSoundName);   // keep id for syncAllChips()
        FrameLayout.LayoutParams tLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        frame.addView(tvName, tLp);

        applyChipShape(frame, wave, sound, false);

        // Volume drag listener
        wave.setVolumeDragListener(vol -> {
            sound.setVolume(vol);
            if (audioService != null) audioService.setSoundVolume(sound.getFileName(), vol);
        });

        final float[]   downY  = {0f};
        final boolean[] inDrag = {false};

        frame.setOnLongClickListener(v -> {
            inDrag[0] = true;
            haptic();
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
                    if (inDrag[0] && wave.isDragging()) {
                        wave.handleDragMove(ev.getY()); return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (inDrag[0]) wave.endDrag();
                    inDrag[0] = false; break;
            }
            return false;
        });

        frame.setOnClickListener(v -> {
            if (wave.isDragging()) return;

            String fn  = sound.getFileName();
            long   now = System.currentTimeMillis();
            Long   lst = lastTapTime.get(fn);

            if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                // Double tap → toggle favourite
                lastTapTime.remove(fn);
                haptic();
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
                    applyChipShape(frame, wave, sound, false);
                } else {
                    audioService.playSound(fn, sound.getVolume());
                    applyChipShape(frame, wave, sound, true);
                }
                updateActiveLabel();
            }
        });

        return frame;
    }

    // ── Chip visual state ─────────────────────────────────────────

    private void applyChipShape(FrameLayout frame, WaveView wave,
                                 SoundItem sound, boolean forceActive) {
        boolean playing = forceActive
                || (audioService != null && audioService.isSoundPlaying(sound.getFileName()));
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
            float vol = audioService != null
                    ? audioService.getSoundVolume(sound.getFileName()) : sound.getVolume();
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

    // ── Sync all chip visual states with AudioService ─────────────

    /**
     * Iterates every chip in the grid and refreshes its visual state
     * from AudioService.  Called on broadcast AND on onResume so that
     * sounds stopped from the Home page are always reflected correctly.
     */
    private void syncAllChips() {
        if (categoriesContainer == null || categories == null) return;
        for (int i = 0; i < categoriesContainer.getChildCount(); i++) {
            View child = categoriesContainer.getChildAt(i);
            if (!"chip_row".equals(child.getTag())) continue;
            LinearLayout row = (LinearLayout) child;
            for (int j = 0; j < row.getChildCount(); j++) {
                View v = row.getChildAt(j);
                if (!(v instanceof FrameLayout)) continue;
                FrameLayout chip = (FrameLayout) v;
                TextView  tv   = chip.findViewById(R.id.tvSoundName);
                WaveView  wave = findWave(chip);
                if (tv == null || wave == null) continue;
                String name = tv.getText().toString();
                for (SoundCategory cat : categories)
                    for (SoundItem s : cat.getSounds())
                        if (s.getName().equals(name))
                            applyChipShape(chip, wave, s, false);
            }
        }
    }

    private WaveView findWave(FrameLayout chip) {
        for (int i = 0; i < chip.getChildCount(); i++)
            if (chip.getChildAt(i) instanceof WaveView)
                return (WaveView) chip.getChildAt(i);
        return null;
    }

    // ── Label ─────────────────────────────────────────────────────

    private void updateActiveLabel() {
        if (tvActiveSoundsLabel == null) return;
        int cnt = audioService != null ? audioService.getAllPlayingSounds().size() : 0;
        tvActiveSoundsLabel.setText(cnt == 0
                ? "No sounds playing"
                : cnt + " sound" + (cnt > 1 ? "s" : "") + " playing");
    }

    // ── Haptic / click ────────────────────────────────────────────

    private void haptic() {
        if (prefs == null || !prefs.isHapticEnabled()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) requireContext()
                        .getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(50, 255));
                    return;
                }
            }
            @SuppressWarnings("deprecation")
            Vibrator vib = (Vibrator) requireContext()
                    .getSystemService(android.content.Context.VIBRATOR_SERVICE);
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

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }

    // ── Theme ─────────────────────────────────────────────────────

    public void applyTheme(View root) {
        if (root == null || colors == null) return;
        root.setBackgroundColor(colors.bgPrimary());
        if (tvSoundsTitle       != null) tvSoundsTitle.setTextColor(colors.pageHeaderText());
        if (tvActiveSoundsLabel != null) tvActiveSoundsLabel.setTextColor(colors.textSecondary());

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
        colors     = new ColorConfig(requireContext());
        categories = SoundLoader.load(requireContext());
        applyTheme(getView());
        buildGrid();
        syncAllChips();
        updateActiveLabel();
    }
}
