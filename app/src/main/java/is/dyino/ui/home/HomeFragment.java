package is.dyino.ui.home;

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
import android.view.View;
import android.view.ViewGroup;
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
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;
import is.dyino.util.SettingsConfig;
import is.dyino.util.SoundLoader;

public class HomeFragment extends Fragment {

    private LinearLayout nowPlayingRadioCard;
    private ImageView    ivStationIcon;
    private TextView     tvStationName, tvBuffering, tvNowPlayingStop;
    private LinearLayout nowPlayingSoundsWrap;
    private LinearLayout favRadioWrap, favSoundsWrap, lastPlayedWrap;
    private TextView     tvNowPlayingLabel, tvFavRadioLabel, tvFavSoundsLabel, tvLastPlayedLabel;
    private View         dividerNowPlaying, dividerFav, dividerLastPlayed;
    private TextView     tvEmpty;

    private AppPrefs       prefs;
    private ColorConfig    colors;
    private SettingsConfig cfg;
    private AudioService   audioService;

    private final Map<String, Long> lastTapTime = new java.util.HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            mainHandler.post(HomeFragment.this::refresh);
        }
    };

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

    @Override
    public void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(requireContext(), stateReceiver,
                new IntentFilter(AudioService.BROADCAST_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onStop() {
        super.onStop();
        try { requireContext().unregisterReceiver(stateReceiver); } catch (Exception ignored) {}
    }

    @Override public void onResume() { super.onResume(); refresh(); }

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
        boolean soundsPlaying = !activeSounds.isEmpty();

        if (radioSelected) {
            nowPlayingRadioCard.setVisibility(View.VISIBLE);
            String name    = audioService.getCurrentName();
            String favicon = audioService.getCurrentFavicon();
            tvStationName.setText(name.isEmpty() ? "Radio" : name);

            if (tvBuffering != null)
                tvBuffering.setVisibility(!radioPlaying && !radioPaused ? View.VISIBLE : View.GONE);

            // Art: favicon → ic_note_vec tinted fallback
            if (favicon != null && !favicon.isEmpty()) {
                if (ivStationIcon != null) ivStationIcon.clearColorFilter();
                Glide.with(this).load(favicon)
                        .apply(RequestOptions.bitmapTransform(new RoundedCorners(dp(16)))
                               .placeholder(R.drawable.ic_note_vec).error(R.drawable.ic_note_vec))
                        .into(ivStationIcon);
            } else {
                if (ivStationIcon != null) {
                    ivStationIcon.setImageResource(R.drawable.ic_note_vec);
                    ivStationIcon.setColorFilter(colors.nowPlayingIconTint(), PorterDuff.Mode.SRC_IN);
                }
            }

            styleNowPlayingCard(nowPlayingRadioCard, radioPlaying);

            nowPlayingRadioCard.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                Long lst = lastTapTime.get("__now_playing__");
                if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                    lastTapTime.remove("__now_playing__");
                    toggleFavFromNowPlaying();
                } else {
                    lastTapTime.put("__now_playing__", now);
                    toggleRadio();
                }
            });

            if (tvNowPlayingStop != null)
                tvNowPlayingStop.setOnClickListener(v -> {
                    if (audioService != null) audioService.stopRadio();
                    refresh();
                });
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

        List<String> lastPlayed = prefs.getLastPlayed();
        boolean anything = anyPlaying || !prefs.getFavourites().isEmpty()
                || !prefs.getFavSounds().isEmpty() || !lastPlayed.isEmpty();
        tvEmpty.setVisibility(anything ? View.GONE : View.VISIBLE);
    }

    private void toggleFavFromNowPlaying() {
        if (audioService == null) return;
        String url = audioService.getCurrentRadioUrl();
        if (url.isEmpty()) return;
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

        // Deduplicate radio favourites by URL
        favRadioWrap.removeAllViews();
        List<String> dedupedRadio = deduplicateByUrl(new ArrayList<>(prefs.getFavourites()));
        buildStationRows(favRadioWrap, dedupedRadio, perRow, chipH, chipR);
        tvFavRadioLabel.setVisibility(dedupedRadio.isEmpty() ? View.GONE : View.VISIBLE);

        // Deduplicate sound favourites by filename
        favSoundsWrap.removeAllViews();
        List<String> dedupedSounds = deduplicateStrings(new ArrayList<>(prefs.getFavSounds()));
        buildSoundRows(favSoundsWrap, dedupedSounds, allSounds, 3, chipH, chipR);
        tvFavSoundsLabel.setVisibility(dedupedSounds.isEmpty() ? View.GONE : View.VISIBLE);

        boolean anyFavs = !dedupedRadio.isEmpty() || !dedupedSounds.isEmpty();
        dividerFav.setVisibility(anyFavs ? View.VISIBLE : View.GONE);
    }

    // ── Last Played ───────────────────────────────────────────────
    private void buildLastPlayed() {
        lastPlayedWrap.removeAllViews();
        // Deduplicate last played by URL
        List<String> lastPlayed = deduplicateByUrl(prefs.getLastPlayed());
        if (lastPlayed.isEmpty()) {
            tvLastPlayedLabel.setVisibility(View.GONE);
            dividerLastPlayed.setVisibility(View.GONE);
            return;
        }
        tvLastPlayedLabel.setVisibility(View.VISIBLE);
        buildStationRows(lastPlayedWrap, lastPlayed, cfg.favRadioPerRow(),
                cfg.chipHeightDp() + 8, 20f);
        dividerLastPlayed.setVisibility(View.VISIBLE);
    }

    // ── Deduplication helpers ─────────────────────────────────────

    /**
     * Remove station keys with duplicate URLs.
     * Keeps insertion order (most-recent first for lastPlayed).
     */
    private List<String> deduplicateByUrl(List<String> keys) {
        LinkedHashMap<String, String> urlToKey = new LinkedHashMap<>();
        for (String key : keys) {
            if (key == null || key.trim().isEmpty()) continue;
            String[] p = AppPrefs.splitKey(key);
            if (p.length < 2 || p[1].trim().isEmpty()) continue;
            String url = p[1].trim().toLowerCase();
            urlToKey.putIfAbsent(url, key); // first occurrence wins
        }
        return new ArrayList<>(urlToKey.values());
    }

    /** Simple string deduplication preserving order. */
    private List<String> deduplicateStrings(List<String> list) {
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        for (String s : list) if (s != null && !s.isEmpty()) seen.put(s, true);
        return new ArrayList<>(seen.keySet());
    }

    // ── Station row builder ───────────────────────────────────────
    private void buildStationRows(LinearLayout container, List<String> keys,
                                  int perRow, int chipH, float chipR) {
        for (int i = 0; i < keys.size(); i += perRow) {
            LinearLayout row = makeRow();
            int inRow = 0;
            for (int j = 0; j < perRow && i + j < keys.size(); j++) {
                String   key = keys.get(i + j);
                String[] p   = AppPrefs.splitKey(key);
                if (p.length < 2 || p[0].trim().isEmpty() || p[1].trim().isEmpty()) continue;
                String name = p[0].trim(), url = p[1].trim();

                View chip = makeStationChip(name, url, key, chipH, chipR);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(chipH), 1f);
                lp.setMargins(0, 0, inRow < perRow - 1 ? dp(6) : 0, 0);
                chip.setLayoutParams(lp);
                row.addView(chip);
                inRow++;
            }
            // Pad remaining slots so layout stays grid-like
            for (int k = inRow; k < perRow; k++) {
                View ph = new View(requireContext());
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, dp(chipH), 1f);
                plp.setMargins(0, 0, dp(6), 0);
                ph.setLayoutParams(plp);
                row.addView(ph);
            }
            if (inRow == 0) continue; // skip entirely empty rows
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rowLp);
            container.addView(row);
        }
    }

    private View makeStationChip(String name, String url, String key,
                                  int heightDp, float cornerDp) {
        String curUrl = audioService != null ? audioService.getCurrentRadioUrl() : "";
        boolean isCurrent = curUrl.equals(url);
        boolean isPlaying = isCurrent && audioService != null && audioService.isRadioPlaying();

        int bg     = isPlaying ? colors.stationActiveBg()     : colors.stationBg();
        int border = isPlaying ? colors.stationActiveBorder() : colors.divider();

        TextView tv = new TextView(requireContext());
        tv.setText(name);
        tv.setTextColor(isPlaying ? colors.stationTextActive() : colors.stationText());
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
            LinearLayout row = makeRow();
            int inRow = 0;
            for (int j = 0; j < perRow && i + j < fns.size(); j++) {
                String fn = fns.get(i + j);
                if (fn == null || fn.trim().isEmpty()) continue;
                String  lbl     = soundDisplayName(fn, allSounds);
                boolean playing = audioService != null && audioService.isSoundPlaying(fn);
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

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(chipH), 1f);
                lp.setMargins(0, 0, inRow < perRow - 1 ? dp(6) : 0, 0);
                chip.setLayoutParams(lp);
                row.addView(chip);
                inRow++;
            }
            for (int k = inRow; k < perRow; k++) {
                View ph = new View(requireContext());
                ph.setLayoutParams(new LinearLayout.LayoutParams(0, dp(chipH), 1f));
                row.addView(ph);
            }
            if (inRow == 0) continue;
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rowLp);
            container.addView(row);
        }
    }

    // ── Chip helpers ──────────────────────────────────────────────
    private void addChip(LinearLayout wrap, String label, int bg, int border, int textColor,
                         int heightDp, float cornerDp, View.OnClickListener click) {
        TextView chip = new TextView(requireContext());
        chip.setText(label); chip.setTextColor(textColor); chip.setTextSize(13f);
        chip.setGravity(android.view.Gravity.CENTER); chip.setSingleLine(true);
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

    private GradientDrawable squircleBg(int bg, int border, float cornerDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(cornerDp)); gd.setColor(bg); gd.setStroke(dp(1), border);
        return gd;
    }

    private void styleNowPlayingCard(View card, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(dp(18));
        gd.setColor(active ? colors.nowPlayingCardBg() : colors.bgCard());
        gd.setStroke(dp(1), active ? colors.nowPlayingCardBorder() : colors.divider());
        card.setBackground(gd);
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

        // Page title colours from color.json
        TextView tvPageTitle = root.findViewById(R.id.tvHomePageTitle);
        if (tvPageTitle != null) tvPageTitle.setTextColor(colors.pageHeaderText());

        TextView tvPageSub = root.findViewById(R.id.tvHomePageSubtitle);
        if (tvPageSub != null) tvPageSub.setTextColor(colors.pageHeaderSubtitleText());

        if (tvNowPlayingLabel != null) tvNowPlayingLabel.setTextColor(colors.homeSectionTitle());
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
}
