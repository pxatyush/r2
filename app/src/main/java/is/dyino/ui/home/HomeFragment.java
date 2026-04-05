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

    private LinearLayout nowPlayingRadioCard;
    private ImageView    ivStationIcon;
    private TextView     tvStationName;
    private LinearLayout nowPlayingSoundsWrap;
    private LinearLayout favRadioWrap;
    private LinearLayout favSoundsWrap;
    private TextView     tvNowPlayingLabel;
    private TextView     tvFavRadioLabel;
    private TextView     tvFavSoundsLabel;
    private View         dividerNowPlaying;
    private View         dividerFav;
    private TextView     tvEmpty;

    private AppPrefs      prefs;
    private ColorConfig   colors;
    private SettingsConfig cfg;
    private AudioService  audioService;

    // Double-tap detection
    private final Map<String, Long> lastTapTime = new java.util.HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setAudioService(AudioService svc) {
        this.audioService = svc;
        if (audioService != null) {
            audioService.setRadioListener(new AudioService.RadioListener() {
                @Override public void onPlaybackStarted(String n)  { mainHandler.post(HomeFragment.this::refresh); }
                @Override public void onPlaybackStopped()           { mainHandler.post(HomeFragment.this::refresh); }
                @Override public void onError(String m)            { mainHandler.post(HomeFragment.this::refresh); }
                @Override public void onBuffering()                { mainHandler.post(HomeFragment.this::refresh); }
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
        nowPlayingSoundsWrap  = view.findViewById(R.id.nowPlayingSoundsContainer);
        favRadioWrap          = view.findViewById(R.id.favRadioContainer);
        favSoundsWrap         = view.findViewById(R.id.favSoundsContainer);
        tvNowPlayingLabel     = view.findViewById(R.id.tvNowPlayingLabel);
        tvFavRadioLabel       = view.findViewById(R.id.tvFavRadioLabel);
        tvFavSoundsLabel      = view.findViewById(R.id.tvFavSoundsLabel);
        dividerNowPlaying     = view.findViewById(R.id.dividerNowPlaying);
        dividerFav            = view.findViewById(R.id.dividerFav);
        tvEmpty               = view.findViewById(R.id.tvEmpty);

        applyTheme(view);
        refresh();
    }

    @Override public void onResume() { super.onResume(); refresh(); }

    public void refresh() {
        if (getView() == null || colors == null) return;
        buildNowPlaying();
        buildFavourites();
    }

    // ── Now Playing ───────────────────────────────────────────────

    private void buildNowPlaying() {
        boolean radioPlaying  = audioService != null && audioService.isRadioPlaying();
        Map<String, Float> activeSounds = audioService != null
            ? audioService.getAllPlayingSounds() : new java.util.HashMap<>();
        boolean soundsPlaying = !activeSounds.isEmpty();

        // Radio card
        if (radioPlaying) {
            nowPlayingRadioCard.setVisibility(View.VISIBLE);
            String name    = audioService.getCurrentName();
            String favicon = audioService.getCurrentFavicon();
            tvStationName.setText(name.isEmpty() ? "Radio" : name);

            int cornerPx = dp(20);
            if (favicon != null && !favicon.isEmpty()) {
                Glide.with(this).load(favicon)
                    .apply(RequestOptions.bitmapTransform(new RoundedCorners(cornerPx))
                           .placeholder(R.drawable.ic_app_icon).error(R.drawable.ic_app_icon))
                    .into(ivStationIcon);
            } else {
                ivStationIcon.setImageResource(R.drawable.ic_app_icon);
            }
            nowPlayingRadioCard.setOnClickListener(v -> {
                if (audioService != null) audioService.stopRadio();
                refresh();
            });
            styleCard(nowPlayingRadioCard, true);
        } else {
            nowPlayingRadioCard.setVisibility(View.GONE);
        }

        // Sounds chips
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

        boolean anyPlaying = radioPlaying || soundsPlaying;
        tvNowPlayingLabel.setVisibility(anyPlaying ? View.VISIBLE : View.GONE);
        dividerNowPlaying.setVisibility(anyPlaying ? View.VISIBLE : View.GONE);

        // Hide empty state only if something is playing OR favourited
        Set<String> fav = prefs.getFavourites();
        Set<String> favS = prefs.getFavSounds();
        boolean anything = anyPlaying || !fav.isEmpty() || !favS.isEmpty();
        tvEmpty.setVisibility(anything ? View.GONE : View.VISIBLE);
    }

    // ── Favourites ────────────────────────────────────────────────

    private void buildFavourites() {
        List<SoundCategory> allSounds = SoundLoader.load(requireContext());
        int perRow = cfg.favRadioPerRow();   // from settings.json, default 2
        int chipH  = cfg.chipHeightDp() + 8; // slightly taller for fav chips
        float chipR = cfg.chipCornerDp();

        // Favourite Radio — N per row
        favRadioWrap.removeAllViews();
        Set<String> favRadio = prefs.getFavourites();
        List<String> radioKeys = new ArrayList<>(favRadio);
        for (int i = 0; i < radioKeys.size(); i += perRow) {
            LinearLayout row = makeRow();
            for (int j = 0; j < perRow && i + j < radioKeys.size(); j++) {
                String key = radioKeys.get(i + j);
                String[] p = AppPrefs.splitKey(key);
                if (p.length < 2) continue;
                String name = p[0], url = p[1];
                final String fkey = key;
                View chip = makeFavChip(name, url, fkey, chipH, chipR, false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, dp(chipH), 1f);
                lp.setMargins(0, 0, j < perRow - 1 ? dp(6) : 0, 0);
                chip.setLayoutParams(lp);
                row.addView(chip);
            }
            // pad last row if odd
            if (radioKeys.size() % perRow != 0 && i + perRow > radioKeys.size()) {
                View ph = new View(requireContext());
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, dp(chipH), 1f);
                plp.setMargins(0, 0, 0, 0);
                ph.setLayoutParams(plp);
                row.addView(ph);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rowLp);
            favRadioWrap.addView(row);
        }
        tvFavRadioLabel.setVisibility(favRadio.isEmpty() ? View.GONE : View.VISIBLE);

        // Favourite Sounds — 3 per row
        favSoundsWrap.removeAllViews();
        Set<String> favSounds = prefs.getFavSounds();
        List<String> soundFns = new ArrayList<>(favSounds);
        for (int i = 0; i < soundFns.size(); i += 3) {
            LinearLayout row = makeRow();
            for (int j = 0; j < 3 && i + j < soundFns.size(); j++) {
                String fn  = soundFns.get(i + j);
                String lbl = soundDisplayName(fn, allSounds);
                boolean playing = audioService != null && audioService.isSoundPlaying(fn);
                final String ffn = fn;
                TextView chip = new TextView(requireContext());
                chip.setText(lbl + (playing ? " ▶" : ""));
                chip.setTextColor(colors.soundBtnText());
                chip.setTextSize(13f);
                chip.setGravity(android.view.Gravity.CENTER);
                chip.setSingleLine(true);
                chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
                chip.setPadding(dp(10), dp(8), dp(10), dp(8));
                chip.setBackground(chipBg(
                    playing ? colors.soundBtnActiveBg() : colors.soundBtnBg(),
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
                lp.setMargins(0, 0, j < 2 ? dp(6) : 0, 0);
                chip.setLayoutParams(lp);
                row.addView(chip);
            }
            // pad
            int rem = soundFns.size() % 3;
            if (rem != 0 && i + 3 > soundFns.size()) {
                for (int k = rem; k < 3; k++) {
                    View ph = new View(requireContext());
                    LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, dp(chipH), 1f);
                    plp.setMargins(0, 0, dp(6), 0);
                    ph.setLayoutParams(plp);
                    row.addView(ph);
                }
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rowLp);
            favSoundsWrap.addView(row);
        }
        tvFavSoundsLabel.setVisibility(favSounds.isEmpty() ? View.GONE : View.VISIBLE);

        boolean anyFavs = !favRadio.isEmpty() || !favSounds.isEmpty();
        dividerFav.setVisibility(anyFavs ? View.VISIBLE : View.GONE);
    }

    // ── Chip factories ────────────────────────────────────────────

    private View makeFavChip(String name, String url, String key,
                             int heightDp, float cornerDp, boolean isSnd) {
        TextView tv = new TextView(requireContext());
        tv.setText(name);
        tv.setTextColor(colors.stationText());
        tv.setTextSize(13f);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setBackground(chipBg(colors.stationBg(), colors.stationActiveBorder(), cornerDp));
        tv.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            Long lst = lastTapTime.get(key);
            if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                lastTapTime.remove(key);
                prefs.removeFavourite(key);
                Toast.makeText(requireContext(), "Removed from Favourites", Toast.LENGTH_SHORT).show();
                refresh();
            } else {
                lastTapTime.put(key, now);
                if (audioService != null) audioService.playRadio(name, url);
                refresh();
            }
        });
        return tv;
    }

    private void addChip(LinearLayout wrap, String label,
                         int bg, int border, int textColor,
                         int heightDp, float cornerDp, View.OnClickListener click) {
        TextView chip = new TextView(requireContext());
        chip.setText(label);
        chip.setTextColor(textColor);
        chip.setTextSize(13f);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setBackground(chipBg(bg, border, cornerDp));
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

    // ── Helpers ───────────────────────────────────────────────────

    private GradientDrawable chipBg(int bg, int border, float cornerDp) {
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
        gd.setCornerRadius(dp(16));
        gd.setColor(active ? colors.stationActiveBg() : colors.bgCard());
        if (active) gd.setStroke(dp(1), colors.stationActiveBorder());
        card.setBackground(gd);
    }

    private String soundDisplayName(String fn, List<SoundCategory> cats) {
        for (SoundCategory cat : cats)
            for (SoundItem s : cat.getSounds())
                if (s.getFileName().equals(fn)) return s.getName();
        return fn.replaceAll("\\.(mp3|ogg)$", "").replace("_", " ");
    }

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }

    public void applyTheme(View root) {
        if (root == null || colors == null) return;
        root.setBackgroundColor(colors.bgPrimary());
        if (tvNowPlayingLabel != null) tvNowPlayingLabel.setTextColor(colors.homeSectionTitle());
        if (tvFavRadioLabel   != null) tvFavRadioLabel.setTextColor(colors.homeSectionTitle());
        if (tvFavSoundsLabel  != null) tvFavSoundsLabel.setTextColor(colors.homeSectionTitle());
        if (tvStationName     != null) tvStationName.setTextColor(colors.textPrimary());
        if (dividerNowPlaying != null) dividerNowPlaying.setBackgroundColor(colors.divider());
        if (dividerFav        != null) dividerFav.setBackgroundColor(colors.divider());
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