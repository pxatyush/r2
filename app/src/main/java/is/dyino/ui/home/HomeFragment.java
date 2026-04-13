package is.dyino.ui.home;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import is.dyino.R;
import is.dyino.model.SoundCategory;
import is.dyino.model.SoundItem;
import is.dyino.service.AudioService;
import is.dyino.ui.sounds.WaveView;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;
import is.dyino.util.SettingsConfig;
import is.dyino.util.SoundLoader;

public class HomeFragment extends Fragment {

    // ── Views ─────────────────────────────────────────────────────
    private FrameLayout  nowPlayingRadioCard;
    private WaveView     radioWaveView;
    private ImageView    ivStationIcon;
    private TextView     tvStationName, tvBuffering, tvNowPlayingStop;
    private View         nowPlayingSoundsScroll;
    private LinearLayout nowPlayingSoundsContainer;
    private TextView     tvNowPlayingLabel, tvNowPlayingSoundsLabel;
    private LinearLayout favRadioWrap, favSoundsWrap, lastPlayedWrap;
    private TextView     tvFavRadioLabel, tvFavSoundsLabel, tvLastPlayedLabel;
    private View         dividerNowPlaying, dividerFav, dividerLastPlayed;
    private TextView     tvEmpty;

    // ── Dependencies ──────────────────────────────────────────────
    private AppPrefs       prefs;
    private ColorConfig    colors;
    private SettingsConfig cfg;
    private AudioService   audioService;

    // Active sound wave views — keyed by filename for quick update
    private final Map<String, WaveView> activeSoundWaves = new java.util.HashMap<>();

    private final Map<String, Long> lastTapTime = new java.util.HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Broadcast receiver (notification / service state changes) ─
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            mainHandler.post(HomeFragment.this::refresh);
        }
    };

    public void setAudioService(AudioService svc) {
        this.audioService = svc;
        if (audioService == null) return;
        audioService.setRadioListener(new AudioService.RadioListener() {
            @Override public void onPlaybackStarted(String n) { mainHandler.post(HomeFragment.this::refresh); }
            @Override public void onPlaybackStopped()          { mainHandler.post(HomeFragment.this::refresh); }
            @Override public void onError(String m)           { mainHandler.post(HomeFragment.this::refresh); }
            @Override public void onBuffering()               { mainHandler.post(HomeFragment.this::refresh); }
        });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_home, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs  = new AppPrefs(requireContext());
        colors = new ColorConfig(requireContext());
        cfg    = new SettingsConfig(requireContext());

        nowPlayingRadioCard      = view.findViewById(R.id.nowPlayingRadioCard);
        radioWaveView            = view.findViewById(R.id.radioWaveView);
        ivStationIcon            = view.findViewById(R.id.ivStationIcon);
        tvStationName            = view.findViewById(R.id.tvNowPlayingStation);
        tvBuffering              = view.findViewById(R.id.tvBuffering);
        tvNowPlayingStop         = view.findViewById(R.id.tvNowPlayingStop);
        nowPlayingSoundsScroll   = view.findViewById(R.id.nowPlayingSoundsScroll);
        nowPlayingSoundsContainer= view.findViewById(R.id.nowPlayingSoundsContainer);
        tvNowPlayingLabel        = view.findViewById(R.id.tvNowPlayingLabel);
        tvNowPlayingSoundsLabel  = view.findViewById(R.id.tvNowPlayingSoundsLabel);
        favRadioWrap             = view.findViewById(R.id.favRadioContainer);
        favSoundsWrap            = view.findViewById(R.id.favSoundsContainer);
        lastPlayedWrap           = view.findViewById(R.id.lastPlayedContainer);
        tvFavRadioLabel          = view.findViewById(R.id.tvFavRadioLabel);
        tvFavSoundsLabel         = view.findViewById(R.id.tvFavSoundsLabel);
        tvLastPlayedLabel        = view.findViewById(R.id.tvLastPlayedLabel);
        dividerNowPlaying        = view.findViewById(R.id.dividerNowPlaying);
        dividerFav               = view.findViewById(R.id.dividerFav);
        dividerLastPlayed        = view.findViewById(R.id.dividerLastPlayed);
        tvEmpty                  = view.findViewById(R.id.tvEmpty);

        applyTheme(view);
        refresh();
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

    @Override public void onResume() { super.onResume(); refresh(); }

    // ═══════════════════════════════════════════════════════════════
    public void refresh() {
        if (getView() == null || colors == null) return;
        buildNowPlaying();
        buildFavourites();
        buildLastPlayed();
    }

    // ── Now Playing ───────────────────────────────────────────────
    private void buildNowPlaying() {
        boolean radioSelected = audioService != null && audioService.isRadioSelected();
        boolean radioPlaying  = audioService != null && audioService.isRadioPlaying();
        boolean radioPaused   = audioService != null && audioService.isRadioPaused();
        Map<String, Float> activeSounds = audioService != null
                ? audioService.getAllPlayingSounds() : new java.util.HashMap<>();

        // ── Radio card ──
        if (radioSelected) {
            nowPlayingRadioCard.setVisibility(View.VISIBLE);
            String name    = audioService.getCurrentName();
            String favicon = audioService.getCurrentFavicon();
            tvStationName.setText(name.isEmpty() ? "Radio" : name);

            if (tvBuffering != null)
                tvBuffering.setVisibility(!radioPlaying && !radioPaused ? View.VISIBLE : View.GONE);

            // Favicon / note-icon fallback
            if (favicon != null && !favicon.isEmpty()) {
                ivStationIcon.clearColorFilter();
                Glide.with(this).load(favicon)
                        .apply(RequestOptions.bitmapTransform(new RoundedCorners(dp(14)))
                                .placeholder(R.drawable.ic_note_vec).error(R.drawable.ic_note_vec))
                        .into(ivStationIcon);
            } else {
                ivStationIcon.setImageResource(R.drawable.ic_note_vec);
                ivStationIcon.setColorFilter(colors.nowPlayingIconTint(), PorterDuff.Mode.SRC_IN);
            }

            // Radio wave (stream visualiser)
            if (radioWaveView != null) {
                int wc = (colors.nowPlayingAnimColor() & 0x00FFFFFF) | 0x55000000;
                radioWaveView.setColors(colors.nowPlayingCardBg(), wc);
                radioWaveView.setVolume(1f);
                if (radioPlaying) {
                    radioWaveView.setVisibility(View.VISIBLE);
                    if (!radioWaveView.isWaving()) radioWaveView.startWave();
                } else {
                    radioWaveView.stopWave();
                    radioWaveView.setVisibility(View.INVISIBLE);
                }
            }

            styleNowPlayingCard(nowPlayingRadioCard, radioPlaying);

            // Single tap = play/pause, double tap = favourite
            nowPlayingRadioCard.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                Long lst = lastTapTime.get("__np__");
                if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                    lastTapTime.remove("__np__");
                    toggleFav();
                } else {
                    lastTapTime.put("__np__", now);
                    toggleRadio();
                }
            });

            if (tvNowPlayingStop != null)
                tvNowPlayingStop.setOnClickListener(v -> {
                    if (audioService != null) audioService.stopRadio();
                    refresh();
                });
        } else {
            if (radioWaveView != null) { radioWaveView.stopWave(); radioWaveView.setVisibility(View.INVISIBLE); }
            nowPlayingRadioCard.setVisibility(View.GONE);
        }

        // ── Active sounds (scrollable horizontal row, each with WaveView + drag vol) ──
        nowPlayingSoundsContainer.removeAllViews();
        activeSoundWaves.clear();

        List<SoundCategory> allSounds = SoundLoader.load(requireContext());
        boolean soundsPlaying = !activeSounds.isEmpty();

        if (soundsPlaying) {
            float dp = getResources().getDisplayMetrics().density;
            int chipW = (int)(84 * dp);
            int chipH = (int)(64 * dp);
            int gap   = (int)(8  * dp);

            for (Map.Entry<String, Float> e : activeSounds.entrySet()) {
                String fn  = e.getKey();
                float  vol = e.getValue();
                String lbl = soundDisplayName(fn, allSounds);

                View chip = buildSoundChip(fn, lbl, vol, chipW, chipH, allSounds);
                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(chipW, chipH);
                lp.setMargins(0, 0, gap, 0);
                chip.setLayoutParams(lp);
                nowPlayingSoundsContainer.addView(chip);
            }
        }

        tvNowPlayingSoundsLabel.setVisibility(soundsPlaying ? View.VISIBLE : View.GONE);
        nowPlayingSoundsScroll.setVisibility(soundsPlaying ? View.VISIBLE : View.GONE);

        boolean anyPlaying = radioSelected || soundsPlaying;
        tvNowPlayingLabel.setVisibility(anyPlaying ? View.VISIBLE : View.GONE);
        dividerNowPlaying.setVisibility(anyPlaying ? View.VISIBLE : View.GONE);

        boolean anything = anyPlaying
                || !prefs.getFavourites().isEmpty()
                || !prefs.getFavSounds().isEmpty()
                || !prefs.getLastPlayed().isEmpty();
        tvEmpty.setVisibility(anything ? View.GONE : View.VISIBLE);
    }

    /**
     * Mini sound chip for the now-playing horizontal scroll row.
     * Shows a WaveView behind the label; long-press + vertical drag adjusts volume.
     */
    @SuppressLint("ClickableViewAccessibility")
    private View buildSoundChip(String fn, String label, float initialVol,
                                int chipW, int chipH,
                                List<SoundCategory> allSounds) {
        FrameLayout frame = new FrameLayout(requireContext());

        // Wave fill
        WaveView wave = new WaveView(requireContext());
        int wc = (colors.soundWaveColor() & 0x00FFFFFF) | 0x6A000000;
        wave.setColors(colors.soundBtnActiveBg(), wc);
        wave.setVolume(initialVol);
        wave.setVisibility(View.VISIBLE);
        wave.startWave();
        frame.addView(wave, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activeSoundWaves.put(fn, wave);

        // Label
        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setTextColor(colors.soundBtnText());
        tv.setTextSize(11f);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setPadding(dp(6), dp(6), dp(6), dp(6));
        frame.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Card background
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(14));
        gd.setColor(colors.soundBtnActiveBg());
        gd.setStroke(dp(1), colors.soundBtnActiveBorder != 0
                ? colors.soundBtnActiveBorder() : colors.accent());
        frame.setBackground(gd);
        frame.setClipToOutline(true);

        // Volume drag
        wave.setVolumeDragListener(vol -> {
            if (audioService != null) audioService.setSoundVolume(fn, vol);
        });

        final float[] downY       = {0f};
        final boolean[] longPress = {false};

        frame.setOnLongClickListener(v -> {
            longPress[0] = true;
            float vol = audioService != null
                    ? audioService.getSoundVolume(fn) : initialVol;
            wave.setVolume(vol);
            wave.beginVolumeDrag(downY[0]);
            return true;
        });

        frame.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY[0] = ev.getY(); longPress[0] = false; break;
                case MotionEvent.ACTION_MOVE:
                    if (longPress[0] && wave.isDragging()) {
                        wave.handleDragMove(ev.getY());
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (longPress[0]) wave.endDrag();
                    longPress[0] = false;
                    break;
            }
            return false;
        });

        // Single tap = stop sound
        frame.setOnClickListener(v -> {
            if (wave.isDragging()) return;
            if (audioService != null) audioService.stopSound(fn);
            refresh();
        });

        return frame;
    }

    // ── Helper for soundBtnActiveBorder which may be 0 if not in color map ──
    // (accessed via lambda above — safe because ColorConfig always returns a value)

    private void toggleFav() {
        if (audioService == null) return;
        String url = audioService.getCurrentRadioUrl(); if (url.isEmpty()) return;
        String key = AppPrefs.stationKey(audioService.getCurrentName(), url, "");
        if (prefs.isFavourite(key)) {
            prefs.removeFavourite(key);
            Toast.makeText(requireContext(), "Removed from Favourites", Toast.LENGTH_SHORT).show();
        } else {
            prefs.addFavourite(key);
            Toast.makeText(requireContext(), "♥ Added to Favourites", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void toggleRadio() {
        if (audioService == null) return;
        if (audioService.isRadioPlaying()) audioService.pauseAll();
        else                               audioService.resumeAll();
        refresh();
    }

    // ── Favourites ────────────────────────────────────────────────
    private void buildFavourites() {
        List<SoundCategory> allSounds = SoundLoader.load(requireContext());
        int   perRow = cfg.favRadioPerRow();
        int   chipH  = cfg.chipHeightDp() + 8;
        float chipR  = 20f;

        favRadioWrap.removeAllViews();
        List<String> favRadio = deduplicateByUrl(new ArrayList<>(prefs.getFavourites()));
        buildStationRows(favRadioWrap, favRadio, perRow, chipH, chipR);
        tvFavRadioLabel.setVisibility(favRadio.isEmpty() ? View.GONE : View.VISIBLE);

        favSoundsWrap.removeAllViews();
        List<String> favSounds = dedupList(new ArrayList<>(prefs.getFavSounds()));
        buildSoundRows(favSoundsWrap, favSounds, allSounds, 3, chipH, chipR);
        tvFavSoundsLabel.setVisibility(favSounds.isEmpty() ? View.GONE : View.VISIBLE);

        boolean any = !favRadio.isEmpty() || !favSounds.isEmpty();
        dividerFav.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    // ── Last Played ───────────────────────────────────────────────
    private void buildLastPlayed() {
        lastPlayedWrap.removeAllViews();
        List<String> last = deduplicateByUrl(prefs.getLastPlayed());
        if (last.isEmpty()) {
            tvLastPlayedLabel.setVisibility(View.GONE);
            dividerLastPlayed.setVisibility(View.GONE);
            return;
        }
        tvLastPlayedLabel.setVisibility(View.VISIBLE);
        buildStationRows(lastPlayedWrap, last, cfg.favRadioPerRow(),
                cfg.chipHeightDp() + 8, 20f);
        dividerLastPlayed.setVisibility(View.VISIBLE);
    }

    // ── Station rows ──────────────────────────────────────────────
    private void buildStationRows(LinearLayout container, List<String> keys,
                                  int perRow, int chipH, float chipR) {
        for (int i = 0; i < keys.size(); i += perRow) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            int added = 0;
            for (int j = 0; j < perRow && i + j < keys.size(); j++) {
                String   key = keys.get(i + j);
                String[] p   = AppPrefs.splitKey(key);
                if (p.length < 2 || p[0].trim().isEmpty() || p[1].trim().isEmpty()) continue;
                View chip = makeStationChip(p[0].trim(), p[1].trim(), key, chipH, chipR);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(chipH), 1f);
                lp.setMargins(0, 0, added < perRow - 1 ? dp(6) : 0, 0);
                chip.setLayoutParams(lp);
                row.addView(chip); added++;
            }
            if (added == 0) continue;
            // Pad empty slots
            for (int k = added; k < perRow; k++) {
                View ph = new View(requireContext());
                ph.setLayoutParams(new LinearLayout.LayoutParams(0, dp(chipH), 1f));
                row.addView(ph);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rowLp);
            container.addView(row);
        }
    }

    private View makeStationChip(String name, String url, String key,
                                  int chipH, float cornerDp) {
        String curUrl   = audioService != null ? audioService.getCurrentRadioUrl() : "";
        boolean current = curUrl.equals(url);
        boolean playing = current && audioService != null && audioService.isRadioPlaying();

        TextView tv = new TextView(requireContext());
        tv.setText(name);
        tv.setTextColor(playing ? colors.stationTextActive() : colors.stationText());
        tv.setTextSize(13f);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setBackground(squircleBg(
                playing ? colors.stationActiveBg() : colors.stationBg(),
                playing ? colors.stationActiveBorder() : colors.divider(), cornerDp));

        tv.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            Long lst = lastTapTime.get(key);
            if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                lastTapTime.remove(key);
                if (prefs.isFavourite(key)) {
                    prefs.removeFavourite(key);
                    Toast.makeText(requireContext(), "Removed from Favourites", Toast.LENGTH_SHORT).show();
                } else {
                    prefs.addFavourite(key);
                    Toast.makeText(requireContext(), "♥ Added to Favourites", Toast.LENGTH_SHORT).show();
                }
                refresh();
            } else {
                lastTapTime.put(key, now);
                if (audioService == null) return;
                if (audioService.getCurrentRadioUrl().equals(url)) {
                    if (audioService.isRadioPlaying()) audioService.pauseAll();
                    else                               audioService.resumeAll();
                } else {
                    prefs.addLastPlayed(key);
                    audioService.playRadio(name, url);
                }
                refresh();
            }
        });
        return tv;
    }

    // ── Sound rows ────────────────────────────────────────────────
    private void buildSoundRows(LinearLayout container, List<String> fns,
                                List<SoundCategory> allSounds, int perRow,
                                int chipH, float chipR) {
        for (int i = 0; i < fns.size(); i += perRow) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            int added = 0;
            for (int j = 0; j < perRow && i + j < fns.size(); j++) {
                String fn = fns.get(i + j);
                if (fn == null || fn.isEmpty()) continue;
                String  lbl = soundDisplayName(fn, allSounds);
                boolean pl  = audioService != null && audioService.isSoundPlaying(fn);
                final String ffn = fn;

                TextView chip = new TextView(requireContext());
                chip.setText(lbl);
                chip.setTextColor(colors.soundBtnText());
                chip.setTextSize(13f);
                chip.setGravity(android.view.Gravity.CENTER);
                chip.setSingleLine(true);
                chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
                chip.setPadding(dp(10), dp(8), dp(10), dp(8));
                chip.setBackground(squircleBg(
                        pl ? colors.soundBtnActiveBg() : colors.soundBtnBg(),
                        pl ? colors.soundBtnActiveBorder() : colors.divider(), chipR));
                chip.setOnClickListener(v -> {
                    long now = System.currentTimeMillis();
                    Long lst = lastTapTime.get("snd_" + ffn);
                    if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                        lastTapTime.remove("snd_" + ffn);
                        prefs.removeFavSound(ffn);
                        Toast.makeText(requireContext(), "Removed from Favourites", Toast.LENGTH_SHORT).show();
                        refresh();
                    } else {
                        lastTapTime.put("snd_" + ffn, now);
                        if (audioService == null) return;
                        if (audioService.isSoundPlaying(ffn)) audioService.stopSound(ffn);
                        else audioService.playSound(ffn, 0.8f);
                        refresh();
                    }
                });

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(chipH), 1f);
                lp.setMargins(0, 0, added < perRow - 1 ? dp(6) : 0, 0);
                chip.setLayoutParams(lp); row.addView(chip); added++;
            }
            if (added == 0) continue;
            for (int k = added; k < perRow; k++) {
                View ph = new View(requireContext());
                ph.setLayoutParams(new LinearLayout.LayoutParams(0, dp(chipH), 1f));
                row.addView(ph);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rowLp);
            container.addView(row);
        }
    }

    // ── Deduplication ─────────────────────────────────────────────
    private List<String> deduplicateByUrl(List<String> keys) {
        LinkedHashMap<String, String> urlToKey = new LinkedHashMap<>();
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            String[] p = AppPrefs.splitKey(key);
            if (p.length < 2 || p[1].trim().isEmpty()) continue;
            urlToKey.putIfAbsent(p[1].trim().toLowerCase(), key);
        }
        return new ArrayList<>(urlToKey.values());
    }

    private List<String> dedupList(List<String> list) {
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        for (String s : list) if (s != null && !s.isEmpty()) seen.put(s, true);
        return new ArrayList<>(seen.keySet());
    }

    // ── Helpers ───────────────────────────────────────────────────
    private GradientDrawable squircleBg(int bg, int border, float cornerDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(cornerDp)); gd.setColor(bg); gd.setStroke(dp(1), border);
        return gd;
    }

    private void styleNowPlayingCard(View card, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(18));
        gd.setColor(active ? colors.nowPlayingCardBg() : colors.bgCard());
        gd.setStroke(dp(1), active ? colors.nowPlayingCardBorder() : colors.divider());
        card.setBackground(gd);
        card.setClipToOutline(true);
    }

    private String soundDisplayName(String fn, List<SoundCategory> cats) {
        for (SoundCategory cat : cats)
            for (SoundItem s : cat.getSounds())
                if (s.getFileName().equals(fn)) return s.getName();
        return fn.replaceAll("\\.(mp3|ogg|wav)$", "").replace("_", " ");
    }

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }

    // ── Theme ─────────────────────────────────────────────────────
    public void applyTheme(View root) {
        if (root == null || colors == null) return;
        root.setBackgroundColor(colors.bgPrimary());

        // Page header colours — properly wired
        TextView title = root.findViewById(R.id.tvHomePageTitle);
        if (title != null) title.setTextColor(colors.pageHeaderText());

        TextView subtitle = root.findViewById(R.id.tvHomePageSubtitle);
        if (subtitle != null) subtitle.setTextColor(colors.pageHeaderSubtitleText());

        if (tvNowPlayingLabel != null) tvNowPlayingLabel.setTextColor(colors.homeSectionTitle());
        if (tvNowPlayingSoundsLabel != null) tvNowPlayingSoundsLabel.setTextColor(colors.homeSectionTitle());
        if (tvFavRadioLabel   != null) tvFavRadioLabel.setTextColor(colors.homeSectionTitle());
        if (tvFavSoundsLabel  != null) tvFavSoundsLabel.setTextColor(colors.homeSectionTitle());
        if (tvLastPlayedLabel != null) tvLastPlayedLabel.setTextColor(colors.homeSectionTitle());
        if (tvStationName     != null) tvStationName.setTextColor(colors.textPrimary());
        if (tvBuffering       != null) tvBuffering.setTextColor(colors.nowPlayingAnimColor());
        if (tvNowPlayingStop  != null) tvNowPlayingStop.setTextColor(colors.textSecondary());
        if (dividerNowPlaying != null) dividerNowPlaying.setBackgroundColor(colors.divider());
        if (dividerFav        != null) dividerFav.setBackgroundColor(colors.divider());
        if (dividerLastPlayed != null) dividerLastPlayed.setBackgroundColor(colors.divider());
        if (tvEmpty           != null) tvEmpty.setTextColor(colors.homeEmptyText());
    }

    public void refreshTheme() {
        if (getView() == null) return;
        colors = new ColorConfig(requireContext());
        cfg    = new SettingsConfig(requireContext());
        applyTheme(getView());
        refresh();
    }

    // Expose accent for lambda inside buildSoundChip
    private int soundBtnActiveBorder() { return colors.soundBtnActiveBorder(); }
}