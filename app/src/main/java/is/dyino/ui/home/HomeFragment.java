package is.dyino.ui.home;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import is.dyino.R;
import is.dyino.model.SoundCategory;
import is.dyino.model.SoundItem;
import is.dyino.service.AudioService;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;
import is.dyino.util.SettingsConfig;
import is.dyino.util.SoundLoader;

public class HomeFragment extends Fragment {

    // ── Views ─────────────────────────────────────────────────────
    private LinearLayout nowPlayingRadioCard;
    private ImageView    ivStationIcon;
    private TextView     tvStationName, tvBuffering, tvNowPlayingToggle, tvNowPlayingStop;
    private LinearLayout nowPlayingSoundsWrap;
    private LinearLayout favRadioWrap, favSoundsWrap, lastPlayedWrap;
    private TextView     tvNowPlayingLabel, tvFavRadioLabel, tvFavSoundsLabel, tvLastPlayedLabel;
    private View         dividerNowPlaying, dividerFav, dividerLastPlayed;
    private TextView     tvEmpty;

    // ── Dependencies ──────────────────────────────────────────────
    private AppPrefs      prefs;
    private ColorConfig   colors;
    private SettingsConfig cfg;
    private AudioService  audioService;

    // Double-tap detection
    private final Map<String, Long> lastTapTime = new java.util.HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Service connection ────────────────────────────────────────
    public void setAudioService(AudioService svc) {
        this.audioService = svc;
        if (audioService != null) {
            audioService.setRadioListener(new AudioService.RadioListener() {
                @Override public void onPlaybackStarted(String n) { mainHandler.post(HomeFragment.this::refresh); }
                @Override public void onPlaybackStopped()          { mainHandler.post(HomeFragment.this::refresh); }
                @Override public void onError(String m)           { mainHandler.post(HomeFragment.this::refresh); }
                @Override public void onBuffering()               { mainHandler.post(HomeFragment.this::refresh); }
            });
        }
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

        nowPlayingRadioCard   = view.findViewById(R.id.nowPlayingRadioCard);
        ivStationIcon         = view.findViewById(R.id.ivStationIcon);
        tvStationName         = view.findViewById(R.id.tvNowPlayingStation);
        tvBuffering           = view.findViewById(R.id.tvBuffering);
        tvNowPlayingToggle    = view.findViewById(R.id.tvNowPlayingToggle);
        tvNowPlayingStop      = view.findViewById(R.id.tvNowPlayingStop);
        nowPlayingSoundsWrap  = view.findViewById(R.id.nowPlayingSoundsContainer);
        favRadioWrap          = view.findViewById(R.id.favRadioContainer);
        favSoundsWrap         = view.findViewById(R.id.favSoundsContainer);
        lastPlayedWrap        = view.findViewById(R.id.lastPlayedContainer);
        tvNowPlayingLabel     = view.findViewById(R.id.tvNowPlayingLabel);
        tvFavRadioLabel       = view.findViewById(R.id.tvFavRadioLabel);
        tvFavSoundsLabel      = view.findViewById(R.id.tvFavSoundsLabel);
        tvLastPlayedLabel     = view.findViewById(R.id.tvLastPlayedLabel);
        dividerNowPlaying     = view.findViewById(R.id.dividerNowPlaying);
        dividerFav            = view.findViewById(R.id.dividerFav);
        dividerLastPlayed     = view.findViewById(R.id.dividerLastPlayed);
        tvEmpty               = view.findViewById(R.id.tvEmpty);

        applyTheme(view);
        refresh();
    }

    @Override public void onResume() { super.onResume(); refresh(); }

    public void refresh() {
        if (getView() == null || colors == null) return;
        buildNowPlaying();
        buildFavourites();
        buildLastPlayed();
    }

    // ═══════════════════════════════════════════════════════════════
    // NOW PLAYING
    // ═══════════════════════════════════════════════════════════════

    private void buildNowPlaying() {
        // Show card as soon as a station is selected (even during buffering)
        boolean radioSelected = audioService != null && audioService.isRadioSelected();
        boolean radioPlaying  = audioService != null && audioService.isRadioPlaying();
        boolean radioPaused   = audioService != null && audioService.isRadioPaused();
        Map<String, Float> activeSounds = audioService != null
                ? audioService.getAllPlayingSounds() : new java.util.HashMap<>();
        boolean soundsPlaying = !activeSounds.isEmpty();

        if (radioSelected) {
            nowPlayingRadioCard.setVisibility(View.VISIBLE);
            String name    = audioService.getCurrentName();
            String favicon = audioService.getCurrentFavicon();
            tvStationName.setText(name.isEmpty() ? "Radio" : name);

            // Buffering indicator
            if (tvBuffering != null) {
                tvBuffering.setVisibility(!radioPlaying && !radioPaused ? View.VISIBLE : View.GONE);
            }

            // Toggle icon: ▶ when paused, ⏸ when playing/buffering
            if (tvNowPlayingToggle != null) {
                tvNowPlayingToggle.setText(radioPlaying ? "⏸" : "▶");
            }

            // Favicon
            int cornerPx = dp(20);
            if (favicon != null && !favicon.isEmpty()) {
                Glide.with(this).load(favicon)
                        .apply(RequestOptions.bitmapTransform(new RoundedCorners(cornerPx))
                               .placeholder(R.drawable.ic_app_icon)
                               .error(R.drawable.ic_app_icon))
                        .into(ivStationIcon);
            } else {
                ivStationIcon.setImageResource(R.drawable.ic_app_icon);
            }

            // Card tap = toggle pause/resume (NOT stop)
            nowPlayingRadioCard.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                Long lst = lastTapTime.get("__now_playing__");
                if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                    // Double tap → favourite
                    lastTapTime.remove("__now_playing__");
                    if (audioService != null) {
                        String key = AppPrefs.stationKey(
                                audioService.getCurrentName(),
                                audioService.getCurrentRadioUrl(), "");
                        if (prefs.isFavourite(key)) {
                            prefs.removeFavourite(key);
                            Toast.makeText(requireContext(), "Removed from Favourites", Toast.LENGTH_SHORT).show();
                        } else {
                            prefs.addFavourite(key);
                            Toast.makeText(requireContext(), "♥ Added to Favourites", Toast.LENGTH_SHORT).show();
                        }
                        refresh();
                    }
                } else {
                    lastTapTime.put("__now_playing__", now);
                    toggleRadio();
                }
            });

            // Toggle button
            if (tvNowPlayingToggle != null) {
                tvNowPlayingToggle.setOnClickListener(v -> toggleRadio());
            }

            // Stop button (✕)
            if (tvNowPlayingStop != null) {
                tvNowPlayingStop.setOnClickListener(v -> {
                    if (audioService != null) audioService.stopRadio();
                    refresh();
                });
            }

            styleCard(nowPlayingRadioCard, radioPlaying);
        } else {
            nowPlayingRadioCard.setVisibility(View.GONE);
        }

        // Active sound chips
        nowPlayingSoundsWrap.removeAllViews();
        List<SoundCategory> allSounds = SoundLoader.load(requireContext());
        if (soundsPlaying) {
            for (Map.Entry<String, Float> e : activeSounds.entrySet()) {
                String fn  = e.getKey();
                String lbl = soundDisplayName(fn, allSounds) + "  ✕";
                addChip(nowPlayingSoundsWrap, lbl,
                        colors.homeChipPlayBg(), colors.homeChipBorder(),
                        colors.homeChipText(), cfg.chipHeightDp(), cfg.chipCornerDp(), v -> {
                            if (audioService != null) audioService.stopSound(fn);
                            refresh();
                        });
            }
        }

        boolean anyPlaying = radioSelected || soundsPlaying;
        tvNowPlayingLabel.setVisibility(anyPlaying ? View.VISIBLE : View.GONE);
        dividerNowPlaying.setVisibility(anyPlaying ? View.VISIBLE : View.GONE);

        Set<String> fav  = prefs.getFavourites();
        Set<String> favS = prefs.getFavSounds();
        List<String> lastPlayed = prefs.getLastPlayed();
        boolean anything = anyPlaying || !fav.isEmpty() || !favS.isEmpty() || !lastPlayed.isEmpty();
        tvEmpty.setVisibility(anything ? View.GONE : View.VISIBLE);
    }

    private void toggleRadio() {
        if (audioService == null) return;
        if (audioService.isRadioPlaying()) audioService.pauseAll();
        else                               audioService.resumeAll();
        refresh();
    }

    // ═══════════════════════════════════════════════════════════════
    // FAVOURITES
    // ═══════════════════════════════════════════════════════════════

    private void buildFavourites() {
        List<SoundCategory> allSounds = SoundLoader.load(requireContext());
        int   perRow = cfg.favRadioPerRow();
        int   chipH  = cfg.chipHeightDp() + 8;
        float chipR  = 20f; // squircle

        // ── Favourite Radio ──
        favRadioWrap.removeAllViews();
        Set<String>  favRadio  = prefs.getFavourites();
        List<String> radioKeys = new ArrayList<>(favRadio);
        buildStationRows(favRadioWrap, radioKeys, perRow, chipH, chipR, false);
        tvFavRadioLabel.setVisibility(favRadio.isEmpty() ? View.GONE : View.VISIBLE);

        // ── Favourite Sounds ──
        favSoundsWrap.removeAllViews();
        Set<String>  favSounds = prefs.getFavSounds();
        List<String> soundFns  = new ArrayList<>(favSounds);
        buildSoundRows(favSoundsWrap, soundFns, allSounds, 3, chipH, chipR);
        tvFavSoundsLabel.setVisibility(favSounds.isEmpty() ? View.GONE : View.VISIBLE);

        boolean anyFavs = !favRadio.isEmpty() || !favSounds.isEmpty();
        dividerFav.setVisibility(anyFavs ? View.VISIBLE : View.GONE);
    }

    // ═══════════════════════════════════════════════════════════════
    // LAST PLAYED
    // ═══════════════════════════════════════════════════════════════

    private void buildLastPlayed() {
        lastPlayedWrap.removeAllViews();
        List<String> lastPlayed = prefs.getLastPlayed();
        if (lastPlayed.isEmpty()) {
            tvLastPlayedLabel.setVisibility(View.GONE);
            dividerLastPlayed.setVisibility(View.GONE);
            return;
        }

        tvLastPlayedLabel.setVisibility(View.VISIBLE);
        int   perRow = cfg.favRadioPerRow();
        int   chipH  = cfg.chipHeightDp() + 8;
        float chipR  = 20f;
        buildStationRows(lastPlayedWrap, lastPlayed, perRow, chipH, chipR, true);
        dividerLastPlayed.setVisibility(View.VISIBLE);
    }

    // ── Station row builder (shared by Favs & Last Played) ────────
    private void buildStationRows(LinearLayout container, List<String> keys,
                                  int perRow, int chipH, float chipR, boolean isLastPlayed) {
        for (int i = 0; i < keys.size(); i += perRow) {
            LinearLayout row = makeRow();
            for (int j = 0; j < perRow && i + j < keys.size(); j++) {
                String   key  = keys.get(i + j);
                String[] p    = AppPrefs.splitKey(key);
                if (p.length < 2) continue;
                String name = p[0], url = p[1];
                final String fkey = key;

                View chip = makeStationChip(name, url, fkey, chipH, chipR, isLastPlayed);
                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(0, dp(chipH), 1f);
                lp.setMargins(0, 0, j < perRow - 1 ? dp(6) : 0, 0);
                chip.setLayoutParams(lp);
                row.addView(chip);
            }
            // pad last row
            int rem = keys.size() % perRow;
            if (rem != 0 && i + perRow > keys.size()) {
                for (int k = rem; k < perRow; k++) {
                    View ph = new View(requireContext());
                    LinearLayout.LayoutParams plp =
                            new LinearLayout.LayoutParams(0, dp(chipH), 1f);
                    plp.setMargins(0, 0, dp(6), 0);
                    ph.setLayoutParams(plp);
                    row.addView(ph);
                }
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rowLp);
            container.addView(row);
        }
    }

    private View makeStationChip(String name, String url, String key,
                                 int heightDp, float cornerDp, boolean isLastPlayed) {
        boolean isCurrentlyPlaying = audioService != null
                && audioService.isRadioPlaying()
                && url.equals(audioService.getCurrentRadioUrl());
        boolean isCurrentlyPaused  = audioService != null
                && audioService.isRadioPaused()
                && url.equals(audioService.getCurrentRadioUrl());

        int bg     = isCurrentlyPlaying ? colors.stationActiveBg()     : colors.stationBg();
        int border = isCurrentlyPlaying ? colors.stationActiveBorder() : colors.divider();
        String label = name + (isCurrentlyPlaying ? " ▶" : isCurrentlyPaused ? " ⏸" : "");

        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setTextColor(isCurrentlyPlaying ? colors.stationTextActive() : colors.stationText());
        tv.setTextSize(13f);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setBackground(squircleBg(bg, border, cornerDp));

        tv.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            Long lst = lastTapTime.get(key);
            if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                // Double tap → toggle favourite
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
                // Single tap → toggle play / pause for this station
                lastTapTime.put(key, now);
                if (audioService == null) return;

                String curUrl = audioService.getCurrentRadioUrl();
                if (curUrl.equals(url)) {
                    // Same station → toggle pause/resume
                    if (audioService.isRadioPlaying()) audioService.pauseAll();
                    else                               audioService.resumeAll();
                } else {
                    // Different station → play it & record in last played
                    prefs.addLastPlayed(key);
                    audioService.playRadio(name, url);
                }
                refresh();
            }
        });
        return tv;
    }

    // ── Sound row builder ─────────────────────────────────────────
    private void buildSoundRows(LinearLayout container, List<String> fns,
                                List<SoundCategory> allSounds, int perRow,
                                int chipH, float chipR) {
        for (int i = 0; i < fns.size(); i += perRow) {
            LinearLayout row = makeRow();
            for (int j = 0; j < perRow && i + j < fns.size(); j++) {
                String fn      = fns.get(i + j);
                String lbl     = soundDisplayName(fn, allSounds);
                boolean playing= audioService != null && audioService.isSoundPlaying(fn);
                final String ffn = fn;

                TextView chip = new TextView(requireContext());
                chip.setText(lbl + (playing ? " ▶" : ""));
                chip.setTextColor(colors.soundBtnText());
                chip.setTextSize(13f);
                chip.setGravity(android.view.Gravity.CENTER);
                chip.setSingleLine(true);
                chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
                chip.setPadding(dp(10), dp(8), dp(10), dp(8));
                chip.setBackground(squircleBg(
                        playing ? colors.soundBtnActiveBg()     : colors.soundBtnBg(),
                        playing ? colors.soundBtnActiveBorder() : colors.divider(),
                        chipR));
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

                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(0, dp(chipH), 1f);
                lp.setMargins(0, 0, j < perRow - 1 ? dp(6) : 0, 0);
                chip.setLayoutParams(lp);
                row.addView(chip);
            }
            // pad
            int rem = fns.size() % perRow;
            if (rem != 0 && i + perRow > fns.size()) {
                for (int k = rem; k < perRow; k++) {
                    View ph = new View(requireContext());
                    LinearLayout.LayoutParams plp =
                            new LinearLayout.LayoutParams(0, dp(chipH), 1f);
                    plp.setMargins(0, 0, dp(6), 0);
                    ph.setLayoutParams(plp);
                    row.addView(ph);
                }
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rowLp);
            container.addView(row);
        }
    }

    // ── Chip factory ──────────────────────────────────────────────
    private void addChip(LinearLayout wrap, String label, int bg, int border, int textColor,
                         int heightDp, float cornerDp, View.OnClickListener click) {
        TextView chip = new TextView(requireContext());
        chip.setText(label);
        chip.setTextColor(textColor);
        chip.setTextSize(13f);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setBackground(squircleBg(bg, border, cornerDp));
        chip.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(heightDp));
        lp.setMargins(0, 0, dp(8), dp(8));
        chip.setLayoutParams(lp);
        wrap.addView(chip);
    }

    private LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    // ── Drawing helpers ───────────────────────────────────────────

    /** Squircle background (large corner radius ≈ squircle feel) */
    private GradientDrawable squircleBg(int bg, int border, float cornerDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(cornerDp));
        gd.setColor(bg);
        gd.setStroke(dp(1), border);
        return gd;
    }

    private void styleCard(View card, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(18));
        gd.setColor(active ? colors.stationActiveBg() : colors.bgCard());
        if (active) gd.setStroke(dp(1), colors.stationActiveBorder());
        card.setBackground(gd);
    }

    // ── Helpers ───────────────────────────────────────────────────
    private String soundDisplayName(String fn, List<SoundCategory> cats) {
        for (SoundCategory cat : cats)
            for (SoundItem s : cat.getSounds())
                if (s.getFileName().equals(fn)) return s.getName();
        return fn.replaceAll("\\.(mp3|ogg)$", "").replace("_", " ");
    }

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }

    // ── Theme ─────────────────────────────────────────────────────
    public void applyTheme(View root) {
        if (root == null || colors == null) return;
        root.setBackgroundColor(colors.bgPrimary());
        if (tvNowPlayingLabel != null) tvNowPlayingLabel.setTextColor(colors.homeSectionTitle());
        if (tvFavRadioLabel   != null) tvFavRadioLabel.setTextColor(colors.homeSectionTitle());
        if (tvFavSoundsLabel  != null) tvFavSoundsLabel.setTextColor(colors.homeSectionTitle());
        if (tvLastPlayedLabel != null) tvLastPlayedLabel.setTextColor(colors.homeSectionTitle());
        if (tvStationName     != null) tvStationName.setTextColor(colors.textPrimary());
        if (tvBuffering       != null) tvBuffering.setTextColor(colors.nowPlayingAnimColor());
        if (tvNowPlayingToggle!= null) tvNowPlayingToggle.setTextColor(colors.textSecondary());
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
}
