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
import is.dyino.util.SoundLoader;

public class HomeFragment extends Fragment {

    private LinearLayout nowPlayingRadioCard;
    private ImageView    ivStationIcon;
    private TextView     tvStationName;
    private LinearLayout nowPlayingSoundsWrap;  // wrapping container for sounds chips
    private LinearLayout favRadioWrap;          // wrapping container for fav radio chips
    private LinearLayout favSoundsWrap;         // wrapping container for fav sounds chips
    private TextView     tvNowPlayingLabel;
    private TextView     tvFavRadioLabel;
    private TextView     tvFavSoundsLabel;
    private View         dividerNowPlaying;
    private View         dividerFav;
    private TextView     tvEmpty;

    private AppPrefs     prefs;
    private ColorConfig  colors;
    private AudioService audioService;

    // Double-tap detection for chips
    private final java.util.Map<String, Long> lastTapTime = new java.util.HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setAudioService(AudioService svc) {
        this.audioService = svc;
        if (audioService != null) {
            audioService.setRadioListener(new AudioService.RadioListener() {
                @Override public void onPlaybackStarted(String n)  { mainHandler.post(() -> refresh()); }
                @Override public void onPlaybackStopped()           { mainHandler.post(() -> refresh()); }
                @Override public void onError(String m)            { mainHandler.post(() -> refresh()); }
                @Override public void onBuffering()                { mainHandler.post(() -> refresh()); }
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
            tvStationName.setText(name);

            int cornerPx = dp(18);
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

        // Sounds chips — use wrapping LinearLayout (multi-row)
        nowPlayingSoundsWrap.removeAllViews();
        List<SoundCategory> allSounds = SoundLoader.load(requireContext());
        if (soundsPlaying) {
            for (Map.Entry<String, Float> e : activeSounds.entrySet()) {
                String fn  = e.getKey();
                String lbl = soundDisplayName(fn, allSounds);
                addChipToWrap(nowPlayingSoundsWrap, lbl + "  ✕",
                    colors.homeChipPlayBg(), colors.homeChipBorder(), colors.homeChipText(),
                    v -> {
                        if (audioService != null) audioService.stopSound(fn);
                        refresh();
                    });
            }
        }

        boolean anyPlaying = radioPlaying || soundsPlaying;
        tvNowPlayingLabel.setVisibility(anyPlaying ? View.VISIBLE : View.GONE);
        dividerNowPlaying.setVisibility(anyPlaying ? View.VISIBLE : View.GONE);
        tvEmpty.setVisibility(anyPlaying ? View.GONE : View.VISIBLE);
    }

    // ── Favourites ────────────────────────────────────────────────

    private void buildFavourites() {
        List<SoundCategory> allSounds = SoundLoader.load(requireContext());

        // Favourite Radio — all of them, wrapped across multiple rows
        favRadioWrap.removeAllViews();
        Set<String> favRadio = prefs.getFavourites();
        for (String key : favRadio) {
            String[] p = AppPrefs.splitKey(key);
            if (p.length < 2) continue;
            String name = p[0], url = p[1];
            final String fkey = key;
            addChipToWrap(favRadioWrap, name,
                colors.stationBg(), colors.stationActiveBorder(), colors.stationText(),
                v -> {
                    long now = System.currentTimeMillis();
                    Long last = lastTapTime.get(fkey);
                    if (last != null && (now - last) < DOUBLE_TAP_MS) {
                        // Double-tap → unfavourite
                        lastTapTime.remove(fkey);
                        prefs.removeFavourite(fkey);
                        Toast.makeText(requireContext(), "Removed from Favourites", Toast.LENGTH_SHORT).show();
                        refresh();
                    } else {
                        lastTapTime.put(fkey, now);
                        if (audioService != null) audioService.playRadio(name, url);
                        refresh();
                    }
                });
        }
        tvFavRadioLabel.setVisibility(favRadio.isEmpty() ? View.GONE : View.VISIBLE);

        // Favourite Sounds
        favSoundsWrap.removeAllViews();
        Set<String> favSounds = prefs.getFavSounds();
        for (String fn : favSounds) {
            String lbl = soundDisplayName(fn, allSounds);
            boolean playing = audioService != null && audioService.isSoundPlaying(fn);
            final String ffn = fn;
            addChipToWrap(favSoundsWrap, lbl + (playing ? " ▶" : ""),
                playing ? colors.soundBtnActiveBg() : colors.soundBtnBg(),
                playing ? colors.soundBtnActiveBorder() : colors.divider(),
                colors.soundBtnText(),
                v -> {
                    long now = System.currentTimeMillis();
                    Long last = lastTapTime.get("snd_" + ffn);
                    if (last != null && (now - last) < DOUBLE_TAP_MS) {
                        // Double-tap → unfavourite sound
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
        }
        tvFavSoundsLabel.setVisibility(favSounds.isEmpty() ? View.GONE : View.VISIBLE);

        boolean anyFavs = !favRadio.isEmpty() || !favSounds.isEmpty();
        dividerFav.setVisibility(anyFavs ? View.VISIBLE : View.GONE);
    }

    // ── Chip builder — adds to a wrapping multi-row LinearLayout ──

    /**
     * Adds a chip to a vertical LinearLayout that acts as a "wrap" container.
     * Each row is a horizontal LinearLayout; when a chip won't fit on the current row
     * a new row is added. This gives proper wrapping without FlexboxLayout dependency.
     */
    private void addChipToWrap(LinearLayout wrap, String label,
                                int bg, int border, int textColor,
                                View.OnClickListener click) {
        TextView chip = new TextView(requireContext());
        chip.setText(label);
        chip.setTextColor(textColor);
        chip.setTextSize(13f);
        chip.setPadding(dp(14), dp(7), dp(14), dp(7));
        chip.setBackground(makeChipBg(bg, border));
        chip.setMaxLines(1);
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setOnClickListener(click);

        // Find the last row, or create first one
        LinearLayout row = null;
        if (wrap.getChildCount() > 0) {
            View last = wrap.getChildAt(wrap.getChildCount() - 1);
            if (last instanceof LinearLayout) row = (LinearLayout) last;
        }

        int rowChipCount = row == null ? 0 : row.getChildCount();
        // Put max 3 chips per row before wrapping
        if (row == null || rowChipCount >= 3) {
            row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(6));
            row.setLayoutParams(rowLp);
            wrap.addView(row);
        }

        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        chipLp.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(chipLp);
        row.addView(chip);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private GradientDrawable makeChipBg(int bg, int border) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(20));
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

    private int dp(int v) {
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
        applyTheme(getView());
        refresh();
    }
}