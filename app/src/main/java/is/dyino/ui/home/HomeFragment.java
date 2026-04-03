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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import is.dyino.R;
import is.dyino.model.RadioStation;
import is.dyino.service.AudioService;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;
import is.dyino.util.SoundLoader;
import is.dyino.model.SoundItem;
import is.dyino.model.SoundCategory;

public class HomeFragment extends Fragment {

    private LinearLayout nowPlayingRadioCard;
    private ImageView    ivStationIcon;
    private TextView     tvStationName;
    
    // Changed to ViewGroup to support both LinearLayout and FlexboxLayout dynamically
    private ViewGroup    nowPlayingSoundsContainer;
    private ViewGroup    favRadioContainer;
    private ViewGroup    favSoundsContainer;
    
    private TextView     tvNowPlayingLabel;
    private TextView     tvFavRadioLabel;
    private TextView     tvFavSoundsLabel;
    private View         dividerNowPlaying;
    private View         dividerFav;

    private AppPrefs     prefs;
    private ColorConfig  colors;
    private AudioService audioService;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setAudioService(AudioService svc) {
        this.audioService = svc;
        if (audioService != null) {
            audioService.setRadioListener(new AudioService.RadioListener() {
                @Override public void onPlaybackStarted(String name) { mainHandler.post(() -> refresh()); }
                @Override public void onPlaybackStopped()            { mainHandler.post(() -> refresh()); }
                @Override public void onError(String msg)            { mainHandler.post(() -> refresh()); }
                @Override public void onBuffering()                  { mainHandler.post(() -> refresh()); }
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

        nowPlayingRadioCard      = view.findViewById(R.id.nowPlayingRadioCard);
        ivStationIcon            = view.findViewById(R.id.ivStationIcon);
        tvStationName            = view.findViewById(R.id.tvNowPlayingStation);
        nowPlayingSoundsContainer= view.findViewById(R.id.nowPlayingSoundsContainer);
        favRadioContainer        = view.findViewById(R.id.favRadioContainer);
        favSoundsContainer       = view.findViewById(R.id.favSoundsContainer);
        tvNowPlayingLabel        = view.findViewById(R.id.tvNowPlayingLabel);
        tvFavRadioLabel          = view.findViewById(R.id.tvFavRadioLabel);
        tvFavSoundsLabel         = view.findViewById(R.id.tvFavSoundsLabel);
        dividerNowPlaying        = view.findViewById(R.id.dividerNowPlaying);
        dividerFav               = view.findViewById(R.id.dividerFav);

        applyTheme(view);
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    public void refresh() {
        if (getView() == null || colors == null) return;
        buildNowPlaying();
        buildFavourites();
    }

    private void buildNowPlaying() {
        boolean radioPlaying = audioService != null && audioService.isRadioPlaying();
        boolean soundsPlaying = false;

        if (audioService != null && !audioService.getAllPlayingSounds().isEmpty()) {
            soundsPlaying = true;
        }

        // ── Radio now playing ──
        if (radioPlaying && audioService != null) {
            nowPlayingRadioCard.setVisibility(View.VISIBLE);
            String name    = audioService.getCurrentName();
            String favicon = audioService.getCurrentFavicon();
            tvStationName.setText(name);

            // Squircle icon via Glide
            if (favicon != null && !favicon.isEmpty()) {
                int cornerPx = (int)(getResources().getDisplayMetrics().density * 18);
                Glide.with(this)
                     .load(favicon)
                     .apply(RequestOptions.bitmapTransform(new RoundedCorners(cornerPx))
                            .placeholder(R.drawable.ic_note)
                            .error(R.drawable.ic_note))
                     .into(ivStationIcon);
            } else {
                ivStationIcon.setImageResource(R.drawable.ic_note);
            }

            // Tap radio card to stop
            nowPlayingRadioCard.setOnClickListener(v -> {
                if (audioService != null) audioService.stopRadio();
                refresh();
            });

            styleCard(nowPlayingRadioCard, true);
        } else {
            nowPlayingRadioCard.setVisibility(View.GONE);
        }

        // ── Sounds now playing ──
        nowPlayingSoundsContainer.removeAllViews();
        if (soundsPlaying && audioService != null) {
            List<SoundCategory> allSounds = SoundLoader.load(requireContext());
            for (java.util.Map.Entry<String, Float> entry : audioService.getAllPlayingSounds().entrySet()) {
                String fn  = entry.getKey();
                String displayName = soundDisplayName(fn, allSounds);
                View chip = makeSoundChip(displayName, () -> {
                    if (audioService != null) {
                        audioService.stopSound(fn);
                        refresh();
                    }
                });
                nowPlayingSoundsContainer.addView(chip);
            }
        }

        // Show/hide "Now Playing" section header
        boolean anyPlaying = radioPlaying || soundsPlaying;
        tvNowPlayingLabel.setVisibility(anyPlaying ? View.VISIBLE : View.GONE);
        dividerNowPlaying.setVisibility(anyPlaying ? View.VISIBLE : View.GONE);
    }

    private void buildFavourites() {
        // ── Favourite radio stations ──
        favRadioContainer.removeAllViews();
        Set<String> favRadio = prefs.getFavourites();
        for (String key : favRadio) {
            String[] p = AppPrefs.splitKey(key);
            if (p.length < 2) continue;
            String name = p[0];
            String url  = p[1];
            View chip = makeFavRadioChip(name, url, key);
            favRadioContainer.addView(chip);
        }
        tvFavRadioLabel.setVisibility(favRadio.isEmpty() ? View.GONE : View.VISIBLE);

        // ── Favourite sounds ──
        favSoundsContainer.removeAllViews();
        Set<String> favSounds = prefs.getFavSounds();
        List<SoundCategory> allSounds = SoundLoader.load(requireContext());
        for (String fn : favSounds) {
            String displayName = soundDisplayName(fn, allSounds);
            View chip = makeFavSoundChip(displayName, fn);
            favSoundsContainer.addView(chip);
        }
        tvFavSoundsLabel.setVisibility(favSounds.isEmpty() ? View.GONE : View.VISIBLE);

        boolean anyFavs = !favRadio.isEmpty() || !favSounds.isEmpty();
        dividerFav.setVisibility(anyFavs ? View.VISIBLE : View.GONE);
    }

    // ── Chip builders ──────────────────────────────────────────────

    /** A playing-sound chip — tap to stop */
    private View makeSoundChip(String label, Runnable onStop) {
        TextView tv = new TextView(requireContext());
        tv.setText(label + "  ✕");
        tv.setTextColor(colors.soundBtnText());
        tv.setTextSize(13f);
        tv.setPadding(dp(16), dp(8), dp(16), dp(8));
        tv.setBackground(makeChipDrawable(colors.soundBtnActiveBg(), colors.soundBtnActiveBorder()));
        
        // Changed to MarginLayoutParams to safely support FlexboxLayout and LinearLayout
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), dp(8));
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> onStop.run());
        return tv;
    }

    /** Favourite radio chip — shows name, tap to play */
    private View makeFavRadioChip(String name, String url, String key) {
        TextView tv = new TextView(requireContext());
        tv.setText(name);
        tv.setTextColor(colors.stationText());
        tv.setTextSize(13f);
        tv.setPadding(dp(16), dp(8), dp(16), dp(8));
        tv.setBackground(makeChipDrawable(colors.stationBg(), colors.stationActiveBorder()));
        
        // Changed to MarginLayoutParams
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), dp(8));
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            if (audioService != null) audioService.playRadio(name, url);
            refresh();
        });
        return tv;
    }

    /** Favourite sound chip — tap to play/stop */
    private View makeFavSoundChip(String label, String fn) {
        boolean playing = audioService != null && audioService.isSoundPlaying(fn);
        TextView tv = new TextView(requireContext());
        tv.setText(label + (playing ? " ▶" : ""));
        tv.setTextColor(colors.soundBtnText());
        tv.setTextSize(13f);
        tv.setPadding(dp(16), dp(8), dp(16), dp(8));
        tv.setBackground(makeChipDrawable(
            playing ? colors.soundBtnActiveBg() : colors.soundBtnBg(),
            colors.soundBtnActiveBorder()));
            
        // Changed to MarginLayoutParams
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), dp(8));
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            if (audioService == null) return;
            if (audioService.isSoundPlaying(fn)) audioService.stopSound(fn);
            else audioService.playSound(fn, 0.8f);
            refresh();
        });
        return tv;
    }

    // ── Helpers ───────────────────────────────────────────────────

    private GradientDrawable makeChipDrawable(int bg, int border) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(20));
        gd.setColor(bg);
        gd.setStroke(dp(1), border);
        return gd;
    }

    private void styleCard(View card, boolean active) {
        float dp = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(16 * dp);
        gd.setColor(active ? colors.stationActiveBg() : colors.bgCard());
        if (active) gd.setStroke((int)(1.5f * dp), colors.stationActiveBorder());
        card.setBackground(gd);
    }

    private String soundDisplayName(String fn, List<SoundCategory> cats) {
        for (SoundCategory cat : cats)
            for (SoundItem s : cat.getSounds())
                if (s.getFileName().equals(fn)) return s.getName();
        // Fallback: prettify filename
        return fn.replaceAll("\\.(mp3|ogg)$", "").replace("_", " ");
    }

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    public void applyTheme(View root) {
        if (root == null || colors == null) return;
        root.setBackgroundColor(colors.bgPrimary());
        if (tvNowPlayingLabel  != null) tvNowPlayingLabel.setTextColor(colors.textSectionTitle());
        if (tvFavRadioLabel    != null) tvFavRadioLabel.setTextColor(colors.textSectionTitle());
        if (tvFavSoundsLabel   != null) tvFavSoundsLabel.setTextColor(colors.textSectionTitle());
        if (tvStationName      != null) tvStationName.setTextColor(colors.textPrimary());
        if (dividerNowPlaying  != null) dividerNowPlaying.setBackgroundColor(colors.divider());
        if (dividerFav         != null) dividerFav.setBackgroundColor(colors.divider());
    }

    public void refreshTheme() {
        if (getView() == null) return;
        colors = new ColorConfig(requireContext());
        applyTheme(getView());
        refresh();
    }
}
