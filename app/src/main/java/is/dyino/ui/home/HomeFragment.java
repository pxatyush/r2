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
import android.view.Gravity;
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
import androidx.appcompat.app.AlertDialog;
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
import is.dyino.util.SleepTimerManager;
import is.dyino.util.SoundLoader;

public class HomeFragment extends Fragment {

    // ── Views ─────────────────────────────────────────────────────
    private FrameLayout  nowPlayingRadioCard;
    private WaveView     radioWaveView;
    private ImageView    ivStationIcon;
    private TextView     tvStationName, tvBuffering, tvNowPlayingStop, btnSleepTimer;
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

    private final Map<String, Long> lastTapTime = new java.util.HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Broadcast: service state changes ─────────────────────────
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            mainHandler.post(HomeFragment.this::refresh);
        }
    };

    // ── Sleep timer tick listener ─────────────────────────────────
    private final SleepTimerManager.Listener timerListener = new SleepTimerManager.Listener() {
        @Override public void onTick(long ms) {
            mainHandler.post(() -> updateSleepTimerBtn(ms));
        }
        @Override public void onFinish() {
            mainHandler.post(() -> {
                updateSleepTimerBtn(0);
                refresh();
            });
        }
    };

    // ─────────────────────────────────────────────────────────────

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
        btnSleepTimer            = view.findViewById(R.id.btnSleepTimer);
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

        SleepTimerManager.get().setListener(timerListener);

        applyTheme(view);
        refresh();
    }

    @Override public void onStart() {
        super.onStart();
        SleepTimerManager.get().setListener(timerListener);
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
        boolean soundsPlaying = !activeSounds.isEmpty();

        // ── Radio card ──
        if (radioSelected) {
            nowPlayingRadioCard.setVisibility(View.VISIBLE);
            String name    = audioService.getCurrentName();
            String favicon = audioService.getCurrentFavicon();
            tvStationName.setText(name.isEmpty() ? "Radio" : name);

            if (tvBuffering != null)
                tvBuffering.setVisibility(!radioPlaying && !radioPaused ? View.VISIBLE : View.GONE);

            // Station art or ic_note_vec fallback
            if (favicon != null && !favicon.isEmpty()) {
                if (ivStationIcon != null) ivStationIcon.clearColorFilter();
                Glide.with(this).load(favicon)
                        .apply(RequestOptions.bitmapTransform(new RoundedCorners(dp(14)))
                                .placeholder(R.drawable.ic_note_vec).error(R.drawable.ic_note_vec))
                        .into(ivStationIcon);
            } else {
                if (ivStationIcon != null) {
                    ivStationIcon.setImageResource(R.drawable.ic_note_vec);
                    ivStationIcon.setColorFilter(colors.nowPlayingIconTint(), PorterDuff.Mode.SRC_IN);
                }
            }

            // Radio wave visualiser
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

            // 3D card style (elevation set in XML; background from code)
            style3DCard(nowPlayingRadioCard, radioPlaying);

            // Sleep timer button
            styleSleepTimerBtn();
            if (btnSleepTimer != null) {
                btnSleepTimer.setVisibility(View.VISIBLE);
                btnSleepTimer.setOnClickListener(v -> showSleepTimerDialog());
            }

            // Card tap: single = toggle pause/resume; double = favourite
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
                    SleepTimerManager.get().cancel();
                    if (audioService != null) audioService.stopRadio();
                    refresh();
                });

        } else {
            // No station — hide card, cancel timer button
            if (radioWaveView != null) { radioWaveView.stopWave(); radioWaveView.setVisibility(View.INVISIBLE); }
            nowPlayingRadioCard.setVisibility(View.GONE);
            if (btnSleepTimer != null) btnSleepTimer.setVisibility(View.GONE);
        }

        // ── Active sounds — scrollable horizontal row ──
        nowPlayingSoundsContainer.removeAllViews();
        List<SoundCategory> allSounds = SoundLoader.load(requireContext());

        if (soundsPlaying) {
            float dp  = getResources().getDisplayMetrics().density;
            int chipW = (int)(84 * dp);
            int chipH = (int)(64 * dp);
            int gap   = (int)(8  * dp);

            for (Map.Entry<String, Float> e : activeSounds.entrySet()) {
                String fn  = e.getKey();
                float  vol = e.getValue();
                String lbl = soundDisplayName(fn, allSounds);
                View chip = buildSoundChip(fn, lbl, vol, chipW, chipH);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(chipW, chipH);
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

    // ── Sleep timer ───────────────────────────────────────────────

    private void styleSleepTimerBtn() {
        if (btnSleepTimer == null) return;
        float dp = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(14 * dp);
        boolean running = SleepTimerManager.get().isRunning();
        gd.setColor(running ? colors.accentDim() : colors.bgCard2());
        gd.setStroke((int)(1 * dp), running ? colors.accent() : colors.divider());
        btnSleepTimer.setBackground(gd);
        btnSleepTimer.setTextColor(running ? colors.accent() : colors.textSecondary());
    }

    private void updateSleepTimerBtn(long remainingMs) {
        if (btnSleepTimer == null) return;
        if (remainingMs > 0) {
            btnSleepTimer.setText(SleepTimerManager.formatRemaining(remainingMs));
        } else {
            btnSleepTimer.setText("Sleep");
        }
        styleSleepTimerBtn();
    }

    private void showSleepTimerDialog() {
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int)(20*dp), (int)(20*dp), (int)(20*dp), (int)(16*dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE); bg.setCornerRadius(16 * dp);
        bg.setColor(colors.bgCard());
        root.setBackground(bg);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("Sleep Timer");
        tvTitle.setTextColor(colors.textPrimary()); tvTitle.setTextSize(18f);
        tvTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        tvTitle.setPadding(0, 0, 0, (int)(16*dp));
        root.addView(tvTitle);

        // Cancel option (if running)
        if (SleepTimerManager.get().isRunning()) {
            TextView tvCancel = new TextView(requireContext());
            tvCancel.setText("Cancel Timer  (" +
                    SleepTimerManager.formatRemaining(SleepTimerManager.get().getRemainingMs()) + " left)");
            tvCancel.setTextColor(colors.accent()); tvCancel.setTextSize(14f);
            tvCancel.setPadding(0, 0, 0, (int)(12*dp));
            root.addView(tvCancel);

            AlertDialog[] dlgRef = {null};
            tvCancel.setOnClickListener(v -> {
                SleepTimerManager.get().cancel();
                updateSleepTimerBtn(0);
                if (dlgRef[0] != null) dlgRef[0].dismiss();
            });

            View div = new View(requireContext());
            div.setBackgroundColor(colors.divider());
            div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            div.setPadding(0, 0, 0, (int)(12*dp));
            root.addView(div);

            AlertDialog dlg = new AlertDialog.Builder(requireContext())
                    .setView(root).create();
            if (dlg.getWindow() != null)
                dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dlgRef[0] = dlg;
            dlg.show();
            return;
        }

        long[][] options = {
            {15,  "15 minutes"},
            {30,  "30 minutes"},
            {60,  "1 hour"},
            {90,  "1.5 hours"},
            {120, "2 hours"},
        };

        AlertDialog[] dlgRef = {null};

        for (long[] opt : options) {
            long minutes   = opt[0];
            String label   = (String)(Object) opt[1]; // cast trick for array literal
            // Re-declare as final
            final long mins = minutes; final String lbl = (minutes == 15 ? "15 minutes"
                    : minutes == 30 ? "30 minutes" : minutes == 60 ? "1 hour"
                    : minutes == 90 ? "1.5 hours" : "2 hours");

            TextView row = new TextView(requireContext());
            row.setText(lbl);
            row.setTextColor(colors.textPrimary()); row.setTextSize(15f);
            row.setPadding(0, (int)(13*dp), 0, (int)(13*dp));
            row.setClickable(true); row.setFocusable(true);
            root.addView(row);

            View sep = new View(requireContext());
            sep.setBackgroundColor(colors.divider());
            sep.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            root.addView(sep);

            row.setOnClickListener(v -> {
                SleepTimerManager.get().start(mins * 60 * 1000L, audioService);
                styleSleepTimerBtn();
                if (dlgRef[0] != null) dlgRef[0].dismiss();
            });
        }

        // Custom option
        TextView rowCustom = new TextView(requireContext());
        rowCustom.setText("Custom…");
        rowCustom.setTextColor(colors.accent()); rowCustom.setTextSize(15f);
        rowCustom.setPadding(0, (int)(13*dp), 0, (int)(13*dp));
        rowCustom.setClickable(true); rowCustom.setFocusable(true);
        root.addView(rowCustom);
        rowCustom.setOnClickListener(v -> {
            if (dlgRef[0] != null) dlgRef[0].dismiss();
            showCustomTimerDialog();
        });

        AlertDialog dlg = new AlertDialog.Builder(requireContext())
                .setView(root).create();
        if (dlg.getWindow() != null)
            dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dlgRef[0] = dlg;
        if (dlg.getWindow() != null) {
            int w = (int)(getResources().getDisplayMetrics().widthPixels * 0.80f);
            dlg.getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dlg.show();
    }

    private void showCustomTimerDialog() {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int)(20*dp),(int)(20*dp),(int)(20*dp),(int)(16*dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE); bg.setCornerRadius(16*dp); bg.setColor(colors.bgCard());
        root.setBackground(bg);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("Custom Duration (minutes)");
        tvTitle.setTextColor(colors.textPrimary()); tvTitle.setTextSize(16f);
        tvTitle.setPadding(0,0,0,(int)(12*dp)); root.addView(tvTitle);

        android.widget.EditText etMinutes = new android.widget.EditText(requireContext());
        etMinutes.setHint("e.g. 45");
        etMinutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etMinutes.setTextColor(colors.textPrimary()); etMinutes.setHintTextColor(colors.textSecondary());
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setShape(GradientDrawable.RECTANGLE); inputBg.setCornerRadius(8*dp);
        inputBg.setColor(colors.bgCard2()); inputBg.setStroke((int)(1*dp), colors.divider());
        etMinutes.setBackground(inputBg); etMinutes.setPadding((int)(12*dp),(int)(10*dp),(int)(12*dp),(int)(10*dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,(int)(16*dp)); etMinutes.setLayoutParams(lp); root.addView(etMinutes);

        LinearLayout btnRow = new LinearLayout(requireContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnCancel = makeDialogBtn("Cancel", false, dp);
        TextView btnSet    = makeDialogBtn("Set",    true,  dp);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0,(int)(42*dp),1f);
        bp.setMargins(0,0,(int)(8*dp),0); btnCancel.setLayoutParams(bp);
        btnSet.setLayoutParams(new LinearLayout.LayoutParams(0,(int)(42*dp),1f));
        btnRow.addView(btnCancel); btnRow.addView(btnSet); root.addView(btnRow);

        AlertDialog dlg = new AlertDialog.Builder(requireContext()).setView(root).create();
        if (dlg.getWindow()!=null) dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        btnCancel.setOnClickListener(v -> dlg.dismiss());
        btnSet.setOnClickListener(v -> {
            String txt = etMinutes.getText().toString().trim();
            if (txt.isEmpty()) return;
            try {
                long mins = Long.parseLong(txt);
                if (mins <= 0 || mins > 480) { Toast.makeText(requireContext(),"Enter 1–480 minutes",Toast.LENGTH_SHORT).show(); return; }
                SleepTimerManager.get().start(mins * 60 * 1000L, audioService);
                styleSleepTimerBtn();
                dlg.dismiss();
            } catch (NumberFormatException ignored) {}
        });
        dlg.show();
        if (dlg.getWindow()!=null){int w=(int)(getResources().getDisplayMetrics().widthPixels*0.80f);dlg.getWindow().setLayout(w,ViewGroup.LayoutParams.WRAP_CONTENT);}
    }

    private TextView makeDialogBtn(String label, boolean primary, float dp) {
        TextView tv = new TextView(requireContext());
        tv.setText(label); tv.setGravity(Gravity.CENTER); tv.setTextSize(14f);
        GradientDrawable gd = new GradientDrawable(); gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(10*dp);
        if (primary) { gd.setColor(colors.accent()); tv.setTextColor(0xFFFFFFFF); }
        else { gd.setColor(colors.bgCard2()); gd.setStroke((int)(1*dp),colors.divider()); tv.setTextColor(colors.textSecondary()); }
        tv.setBackground(gd); tv.setClickable(true); tv.setFocusable(true);
        return tv;
    }

    // ── Sound chip (scrollable now-playing) ───────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private View buildSoundChip(String fn, String label, float initialVol, int chipW, int chipH) {
        FrameLayout frame = new FrameLayout(requireContext());

        WaveView wave = new WaveView(requireContext());
        int wc = (colors.soundWaveColor() & 0x00FFFFFF) | 0x6A000000;
        wave.setColors(colors.soundBtnActiveBg(), wc);
        wave.setVolume(initialVol);
        wave.startWave();
        frame.addView(wave, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView tv = new TextView(requireContext());
        tv.setText(label); tv.setTextColor(colors.soundBtnText()); tv.setTextSize(11f);
        tv.setGravity(Gravity.CENTER); tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setPadding(dp(4), dp(4), dp(4), dp(4));
        frame.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(dp(14));
        gd.setColor(colors.soundBtnActiveBg());
        gd.setStroke(dp(1), colors.soundBtnActiveBorder()); // uses method — no NPE
        frame.setBackground(gd); frame.setClipToOutline(true);

        wave.setVolumeDragListener(vol -> {
            if (audioService != null) audioService.setSoundVolume(fn, vol);
        });

        final float[]   downY  = {0f};
        final boolean[] inDrag = {false};

        frame.setOnLongClickListener(v -> {
            inDrag[0] = true;
            float vol = audioService != null ? audioService.getSoundVolume(fn) : initialVol;
            wave.setVolume(vol);
            wave.beginVolumeDrag(downY[0]);
            return true;
        });
        frame.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: downY[0] = ev.getY(); inDrag[0] = false; break;
                case MotionEvent.ACTION_MOVE:
                    if (inDrag[0] && wave.isDragging()) { wave.handleDragMove(ev.getY()); return true; } break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (inDrag[0]) wave.endDrag(); inDrag[0] = false; break;
            }
            return false;
        });
        frame.setOnClickListener(v -> {
            if (wave.isDragging()) return;
            if (audioService != null) audioService.stopSound(fn);
            refresh();
        });
        return frame;
    }

    // ── Helpers ───────────────────────────────────────────────────
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

    private void buildLastPlayed() {
        lastPlayedWrap.removeAllViews();
        List<String> last = deduplicateByUrl(prefs.getLastPlayed());
        if (last.isEmpty()) {
            tvLastPlayedLabel.setVisibility(View.GONE);
            dividerLastPlayed.setVisibility(View.GONE);
            return;
        }
        tvLastPlayedLabel.setVisibility(View.VISIBLE);
        buildStationRows(lastPlayedWrap, last, cfg.favRadioPerRow(), cfg.chipHeightDp() + 8, 20f);
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

    private View makeStationChip(String name, String url, String key, int chipH, float cornerDp) {
        String curUrl   = audioService != null ? audioService.getCurrentRadioUrl() : "";
        boolean current = curUrl.equals(url);
        boolean playing = current && audioService != null && audioService.isRadioPlaying();
        boolean paused  = current && audioService != null && audioService.isRadioPaused();

        int bg     = (playing || paused) ? colors.stationActiveBg()     : colors.stationBg();
        int border = (playing || paused) ? colors.stationActiveBorder() : colors.divider();
        int textC  = (playing || paused) ? colors.stationTextActive()   : colors.stationText();

        TextView tv = new TextView(requireContext());
        tv.setText(name); tv.setTextColor(textC); tv.setTextSize(13f);
        tv.setGravity(Gravity.CENTER); tv.setSingleLine(true);
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
                    else audioService.resumeAll();
                } else {
                    prefs.addLastPlayed(key);
                    audioService.playRadio(name, url);
                }
                refresh();
            }
        });
        return tv;
    }

    // ── Sound rows — highlight ALL playing sounds ─────────────────
    private void buildSoundRows(LinearLayout container, List<String> fns,
                                List<SoundCategory> allSounds, int perRow, int chipH, float chipR) {
        for (int i = 0; i < fns.size(); i += perRow) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            int added = 0;
            for (int j = 0; j < perRow && i + j < fns.size(); j++) {
                String fn = fns.get(i + j);
                if (fn == null || fn.isEmpty()) continue;
                // Check real-time playing state from AudioService
                boolean playing = audioService != null && audioService.isSoundPlaying(fn);
                String  lbl     = soundDisplayName(fn, allSounds);
                final String ffn = fn;

                TextView chip = new TextView(requireContext());
                chip.setText(lbl); chip.setTextColor(colors.soundBtnText()); chip.setTextSize(13f);
                chip.setGravity(Gravity.CENTER); chip.setSingleLine(true);
                chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
                chip.setPadding(dp(10), dp(8), dp(10), dp(8));
                // Highlight if currently playing
                chip.setBackground(squircleBg(
                        playing ? colors.soundBtnActiveBg()     : colors.soundBtnBg(),
                        playing ? colors.soundBtnActiveBorder() : colors.divider(), chipR));

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

    // ── Drawing helpers ───────────────────────────────────────────

    private GradientDrawable squircleBg(int bg, int border, float cornerDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(cornerDp)); gd.setColor(bg); gd.setStroke(dp(1), border);
        return gd;
    }

    /** 3D raised card: background + subtle shadow tint on bottom edge */
    private void style3DCard(View card, boolean active) {
        float dp = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(18 * dp);
        gd.setColor(active ? colors.nowPlayingCardBg() : colors.bgCard());
        gd.setStroke((int)(1.5f * dp), active ? colors.nowPlayingCardBorder() : colors.divider());
        card.setBackground(gd);
        card.setClipToOutline(true);
        // elevation is set in XML; boost translationZ when active for stronger shadow
        card.setTranslationZ(active ? 8 * dp : 3 * dp);
    }

    // ── Deduplication ─────────────────────────────────────────────
    private List<String> deduplicateByUrl(List<String> keys) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            String[] p = AppPrefs.splitKey(key);
            if (p.length < 2 || p[1].trim().isEmpty()) continue;
            map.putIfAbsent(p[1].trim().toLowerCase(), key);
        }
        return new ArrayList<>(map.values());
    }

    private List<String> dedupList(List<String> list) {
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        for (String s : list) if (s != null && !s.isEmpty()) seen.put(s, true);
        return new ArrayList<>(seen.keySet());
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

        TextView title = root.findViewById(R.id.tvHomePageTitle);
        if (title != null) title.setTextColor(colors.pageHeaderText());

        TextView subtitle = root.findViewById(R.id.tvHomePageSubtitle);
        if (subtitle != null) subtitle.setTextColor(colors.pageHeaderSubtitleText());

        if (tvNowPlayingLabel    != null) tvNowPlayingLabel.setTextColor(colors.homeSectionTitle());
        if (tvNowPlayingSoundsLabel != null) tvNowPlayingSoundsLabel.setTextColor(colors.homeSectionTitle());
        if (tvFavRadioLabel      != null) tvFavRadioLabel.setTextColor(colors.homeSectionTitle());
        if (tvFavSoundsLabel     != null) tvFavSoundsLabel.setTextColor(colors.homeSectionTitle());
        if (tvLastPlayedLabel    != null) tvLastPlayedLabel.setTextColor(colors.homeSectionTitle());
        if (tvStationName        != null) tvStationName.setTextColor(colors.textPrimary());
        if (tvBuffering          != null) tvBuffering.setTextColor(colors.nowPlayingAnimColor());
        if (tvNowPlayingStop     != null) tvNowPlayingStop.setTextColor(colors.textSecondary());
        if (dividerNowPlaying    != null) dividerNowPlaying.setBackgroundColor(colors.divider());
        if (dividerFav           != null) dividerFav.setBackgroundColor(colors.divider());
        if (dividerLastPlayed    != null) dividerLastPlayed.setBackgroundColor(colors.divider());
        if (tvEmpty              != null) tvEmpty.setTextColor(colors.homeEmptyText());
        styleSleepTimerBtn();
    }

    public void refreshTheme() {
        if (getView() == null) return;
        colors = new ColorConfig(requireContext());
        cfg    = new SettingsConfig(requireContext());
        applyTheme(getView());
        refresh();
    }
}
