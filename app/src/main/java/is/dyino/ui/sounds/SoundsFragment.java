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

public class SoundsFragment extends Fragment {

    private LinearLayout categoriesContainer;
    private TextView     btnStopAll;
    private TextView     tvActiveSoundsLabel;
    private TextView     tvSoundsTitle;

    private AppPrefs    prefs;
    private ColorConfig colors;
    private AudioService audioService;
    private List<SoundCategory> categories;

    // Collapse state per category
    private final Map<String, Boolean> expandedMap = new HashMap<>();
    // Chip views keyed by sound filename for fast sync
    private final Map<String, FrameLayout> chipMap   = new HashMap<>();
    private final Map<String, WaveView>    waveMap   = new HashMap<>();

    private final Map<String, Long> lastTapTime = new HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            mainHandler.post(SoundsFragment.this::syncAllChips);
        }
    };

    /** Called by MainActivity after service is bound. Always re-syncs immediately. */
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

    /** Re-sync every time the page becomes visible — catches stops made from Home. */
    @Override public void onResume() {
        super.onResume();
        syncAllChips();
        updateActiveLabel();
    }

    // ── Compute chip size based on nav position ───────────────────

    private int chipSizeDp() { return 84; }  // same as home active-sounds chips

    private int cols() { return "bottom".equals(prefs.getNavPosition()) ? 4 : 3; }

    /** Available width in px after nav bar and outer padding. */
    private int availWidthPx() {
        float dp   = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int navW    = "bottom".equals(prefs.getNavPosition()) ? 0 : (int)(52 * dp);
        int padH    = (int)(12 * dp) * 2;
        return screenW - navW - padH;
    }

    /** Exact chip size in px derived from available width and column count. */
    private int chipPx() {
        float dp   = getResources().getDisplayMetrics().density;
        int gap     = (int)(8 * dp);
        int cols    = cols();
        int avail   = availWidthPx();
        return (avail - gap * (cols - 1)) / cols;
    }

    // ── Category cards (collapsible, radio-style) ─────────────────

    private void buildCategories() {
        if (categoriesContainer == null || categories == null) return;
        categoriesContainer.removeAllViews();
        chipMap.clear(); waveMap.clear();

        float dp   = getResources().getDisplayMetrics().density;
        int   padH = (int)(12 * dp);

        for (SoundCategory cat : categories) {
            String catName = cat.getName();
            boolean expanded = Boolean.TRUE.equals(expandedMap.get(catName));

            // ── Outer card wrapper ────────────────────────────────
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(padH, (int)(10*dp), padH, 0);
            card.setLayoutParams(cardLp);
            applyCardShape(card, expanded, dp);

            // ── Header row ────────────────────────────────────────
            LinearLayout header = new LinearLayout(requireContext());
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding((int)(14*dp), (int)(14*dp), (int)(12*dp), (int)(14*dp));
            header.setClickable(true); header.setFocusable(true);

            // Category label
            TextView tvName = new TextView(requireContext());
            tvName.setText(catName);
            tvName.setTextSize(15f);
            tvName.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.NORMAL));
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            header.addView(tvName);

            // Count badge
            int count = cat.getSounds().size();
            TextView tvCount = new TextView(requireContext());
            tvCount.setText(String.valueOf(count));
            tvCount.setTextSize(11f);
            tvCount.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.NORMAL));
            tvCount.setPadding((int)(8*dp),(int)(3*dp),(int)(8*dp),(int)(3*dp));
            tvCount.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            header.addView(tvCount);

            // Arrow
            TextView tvArrow = new TextView(requireContext());
            tvArrow.setText("›");
            tvArrow.setTextSize(20f);
            tvArrow.setGravity(Gravity.CENTER);
            tvArrow.setLayoutParams(new LinearLayout.LayoutParams(
                    (int)(24*dp), (int)(24*dp)));
            tvArrow.setRotation(expanded ? 90f : 0f);
            header.addView(tvArrow);

            applyHeaderColors(tvName, tvCount, tvArrow, expanded, dp);
            card.addView(header);

            // ── Chips body (HorizontalScrollView for bottom nav, wrap for side) ─
            // We use a HorizontalScrollView so the grid doesn't get cut off
            // regardless of screen width.
            HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
            hsv.setHorizontalScrollBarEnabled(false); // FIXED HERE
            hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            hsv.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout chipContainer = buildChipGrid(cat.getSounds(), dp);
            hsv.addView(chipContainer);
            hsv.setVisibility(expanded ? View.VISIBLE : View.GONE);
            card.addView(hsv);

            // Expand / collapse on header tap
            header.setOnClickListener(v -> {
                boolean nowExpanded = !Boolean.TRUE.equals(expandedMap.get(catName));
                expandedMap.put(catName, nowExpanded);
                tvArrow.animate().rotation(nowExpanded ? 90f : 0f).setDuration(200).start();
                hsv.setVisibility(nowExpanded ? View.VISIBLE : View.GONE);
                applyCardShape(card, nowExpanded, dp);
                applyHeaderColors(tvName, tvCount, tvArrow, nowExpanded, dp);
            });

            categoriesContainer.addView(card);
        }
    }

    private LinearLayout buildChipGrid(List<SoundItem> sounds, float dp) {
        int cols   = cols();
        int chip   = chipPx();
        int gap    = (int)(8 * dp);
        int padH   = (int)(12 * dp);

        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(padH, (int)(8*dp), padH, (int)(12*dp));

        for (int i = 0; i < sounds.size(); i += cols) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, gap);
            row.setLayoutParams(rowLp);

            for (int col = 0; col < cols; col++) {
                if (col > 0) {
                    View sp = new View(requireContext());
                    sp.setLayoutParams(new LinearLayout.LayoutParams(gap, chip));
                    row.addView(sp);
                }
                int idx = i + col;
                if (idx < sounds.size()) {
                    row.addView(buildChip(sounds.get(idx), chip, dp));
                } else {
                    // Placeholder keeps row width consistent
                    View ph = new View(requireContext());
                    ph.setLayoutParams(new LinearLayout.LayoutParams(chip, chip));
                    row.addView(ph);
                }
            }
            grid.addView(row);
        }
        return grid;
    }

    // ── Single chip (84dp square, wave behind label) ──────────────

    @SuppressLint("ClickableViewAccessibility")
    private FrameLayout buildChip(SoundItem sound, int chipSz, float dp) {
        FrameLayout frame = new FrameLayout(requireContext());
        frame.setLayoutParams(new LinearLayout.LayoutParams(chipSz, chipSz));
        frame.setClipToOutline(true);
        frame.setClickable(true); frame.setFocusable(true); frame.setLongClickable(true);

        WaveView wave = new WaveView(requireContext());
        wave.setPowerSaving(prefs.isPowerSavingEnabled());
        wave.setVolume(sound.getVolume());
        frame.addView(wave, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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

        wave.setVolumeDragListener(vol -> {
            sound.setVolume(vol);
            if (audioService != null) audioService.setSoundVolume(sound.getFileName(), vol);
        });

        applyChipStyle(frame, wave, sound);

        final float[]   downY  = {0f};
        final boolean[] inDrag = {false};

        frame.setOnLongClickListener(v -> {
            inDrag[0] = true; haptic();
            float cur = audioService != null
                    ? audioService.getSoundVolume(sound.getFileName()) : sound.getVolume();
            wave.setVolume(cur); wave.beginVolumeDrag(downY[0]); return true;
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
                lastTapTime.remove(fn); haptic();
                boolean fav = prefs.isFavSound(fn);
                if (fav) prefs.removeFavSound(fn); else prefs.addFavSound(fn);
                Toast.makeText(requireContext(), fav ? "Removed from Favourites" : "♥ Added to Favourites", Toast.LENGTH_SHORT).show();
            } else {
                lastTapTime.put(fn, now); haptic(); clickSound();
                if (audioService == null) return;
                if (audioService.isSoundPlaying(fn)) {
                    audioService.stopSound(fn);
                } else {
                    audioService.playSound(fn, sound.getVolume());
                }
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
            int wc = (colors.soundWaveColor() & 0x00FFFFFF) | 0x6A000000;
            float vol = audioService.getSoundVolume(sound.getFileName());
            wave.setColors(colors.soundBtnActiveBg(), wc);
            wave.setVolume(vol);
            wave.setVisibility(View.VISIBLE);
            if (!wave.isWaving() && !prefs.isPowerSavingEnabled()) wave.startWave();
        } else {
            wave.stopWave();
            wave.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Fast O(1) sync using the chipMap and waveMap populated during buildCategories().
     * Called on every broadcast AND every onResume so the sound page is always
     * consistent with AudioService regardless of where the stop was initiated.
     */
    private void syncAllChips() {
        if (categories == null) return;
        for (SoundCategory cat : categories) {
            for (SoundItem sound : cat.getSounds()) {
                String fn    = sound.getFileName();
                FrameLayout frame = chipMap.get(fn);
                WaveView    wave  = waveMap.get(fn);
                if (frame != null && wave != null) applyChipStyle(frame, wave, sound);
            }
        }
        updateActiveLabel();
    }

    // ── Card visual helpers ───────────────────────────────────────

    private void applyCardShape(View card, boolean expanded, float dp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        if (expanded) {
            gd.setCornerRadii(new float[]{10*dp,10*dp, 10*dp,10*dp, 10*dp,10*dp, 10*dp,10*dp});
            gd.setColor(colors.radioGroupHeaderBg());
            gd.setStroke((int)(1f*dp), colors.radioGroupHeaderBorder());
        } else {
            gd.setCornerRadius(10 * dp);
            gd.setColor(colors.bgCard2());
        }
        card.setBackground(gd);
    }

    private void applyHeaderColors(TextView tvName, TextView tvCount,
                                    TextView tvArrow, boolean expanded, float dp) {
        int textColor = expanded ? colors.radioGroupNameText() : colors.textPrimary();
        tvName.setTextColor(textColor);
        tvArrow.setTextColor(expanded ? colors.radioGroupNameText() : colors.textSecondary());

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.RECTANGLE);
        badgeBg.setCornerRadius(10 * dp);
        badgeBg.setColor(expanded ? colors.radioGroupBadgeBg() : 0x18FFFFFF);
        tvCount.setBackground(badgeBg);
        tvCount.setTextColor(expanded ? colors.radioGroupBadgeText() : colors.textSecondary());
    }

    // ── Label ─────────────────────────────────────────────────────

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
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) vib.vibrate(VibrationEffect.createOneShot(50,255)); else vib.vibrate(50);
        } catch (Exception ignored) {}
    }

    private void clickSound() {
        if (audioService!=null&&prefs!=null&&prefs.isButtonSoundEnabled()) audioService.playClickSound();
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
    }
}
