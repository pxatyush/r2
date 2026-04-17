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
    private FrameLayout         nowPlayingRadioCard;
    private WaveView            radioVolumeWave;
    private AudioVisualizerView audioVisualizer;
    private ImageView           ivStationIcon;
    private TextView            tvStationName, tvBuffering, tvNowPlayingStop;
    private TextView            btnSleepTimer;
    private TextView            tvVisualizerLabel;
    private View                nowPlayingSoundsScroll;
    private LinearLayout        nowPlayingSoundsContainer;
    private TextView            tvNowPlayingLabel, tvNowPlayingSoundsLabel;
    private LinearLayout        favRadioWrap, favSoundsWrap, lastPlayedWrap;
    private TextView            tvFavRadioLabel, tvFavSoundsLabel, tvLastPlayedLabel;
    private View                dividerNowPlaying, dividerFav, dividerLastPlayed;
    private TextView            tvEmpty;

    // ── Dependencies ──────────────────────────────────────────────
    private AppPrefs       prefs;
    private ColorConfig    colors;
    private SettingsConfig cfg;
    private AudioService   audioService;

    private final Map<String, Long> lastTapTime = new java.util.HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Drag state for radio volume wave ──────────────────────────
    private float   radioWaveDownY  = 0f;
    private boolean radioWaveInDrag = false;

    // ── Broadcast ─────────────────────────────────────────────────
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            mainHandler.post(HomeFragment.this::refresh);
        }
    };

    // ── Sleep timer tick ──────────────────────────────────────────
    private final SleepTimerManager.Listener timerListener = new SleepTimerManager.Listener() {
        @Override public void onTick(long ms)  { mainHandler.post(() -> updateSleepBtn(ms)); }
        @Override public void onFinish()       { mainHandler.post(() -> { updateSleepBtn(0); refresh(); }); }
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
        radioVolumeWave          = view.findViewById(R.id.radioVolumeWave);
        audioVisualizer          = view.findViewById(R.id.audioVisualizer);
        ivStationIcon            = view.findViewById(R.id.ivStationIcon);
        tvStationName            = view.findViewById(R.id.tvNowPlayingStation);
        tvBuffering              = view.findViewById(R.id.tvBuffering);
        tvNowPlayingStop         = view.findViewById(R.id.tvNowPlayingStop);
        btnSleepTimer            = view.findViewById(R.id.btnSleepTimer);
        tvVisualizerLabel        = view.findViewById(R.id.tvVisualizerLabel);
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
        setupRadioVolumeWave();
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

    // ── Radio volume wave setup ───────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private void setupRadioVolumeWave() {
        if (radioVolumeWave == null) return;
        boolean ps = prefs.isPowerSavingEnabled();
        radioVolumeWave.setPowerSaving(ps);
        int wc = (colors.nowPlayingAnimColor() & 0x00FFFFFF) | 0x66000000;
        radioVolumeWave.setColors(colors.nowPlayingCardBg(), wc);

        radioVolumeWave.setVolumeDragListener(vol -> {
            if (audioService != null) audioService.setRadioVolume(vol);
        });

        radioVolumeWave.setOnLongClickListener(v -> {
            radioWaveInDrag = true;
            float vol = audioService != null ? audioService.getRadioVolume() : 0.8f;
            radioVolumeWave.setVolume(vol);
            radioVolumeWave.beginVolumeDrag(radioWaveDownY);
            return true;
        });

        radioVolumeWave.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    radioWaveDownY = ev.getY(); radioWaveInDrag = false; break;
                case MotionEvent.ACTION_MOVE:
                    if (radioWaveInDrag && radioVolumeWave.isDragging()) {
                        radioVolumeWave.handleDragMove(ev.getY()); return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (radioWaveInDrag) radioVolumeWave.endDrag();
                    radioWaveInDrag = false; break;
            }
            return false;
        });
    }

    // ── Now Playing ───────────────────────────────────────────────
    private void buildNowPlaying() {
        boolean radioSelected = audioService != null && audioService.isRadioSelected();
        boolean radioPlaying  = audioService != null && audioService.isRadioPlaying();
        boolean radioPaused   = audioService != null && audioService.isRadioPaused();
        Map<String, Float> activeSounds = audioService != null
                ? audioService.getAllPlayingSounds() : new java.util.HashMap<>();
        boolean soundsPlaying = !activeSounds.isEmpty();
        boolean powerSaving   = prefs.isPowerSavingEnabled();

        // ── Radio card ──
        if (radioSelected) {
            nowPlayingRadioCard.setVisibility(View.VISIBLE);
            String name    = audioService.getCurrentName();
            String favicon = audioService.getCurrentFavicon();
            tvStationName.setText(name.isEmpty() ? "Radio" : name);
            if (tvBuffering != null)
                tvBuffering.setVisibility(!radioPlaying && !radioPaused ? View.VISIBLE : View.GONE);

            // Station art / fallback
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

            // Radio volume wave
            if (radioVolumeWave != null) {
                float vol = audioService != null ? audioService.getRadioVolume() : 0.8f;
                radioVolumeWave.setVolume(vol);
                radioVolumeWave.setPowerSaving(powerSaving);
                int wc = (colors.nowPlayingAnimColor() & 0x00FFFFFF) | 0x55000000;
                radioVolumeWave.setColors(colors.nowPlayingCardBg(), wc);
                if (radioPlaying && !powerSaving) {
                    if (!radioVolumeWave.isWaving()) radioVolumeWave.startWave();
                } else {
                    radioVolumeWave.stopWave();
                }
                radioVolumeWave.setVisibility(View.VISIBLE);
            }

            style3DCard(nowPlayingRadioCard, radioPlaying);

            // Single tap = pause/resume, double tap = favourite
            nowPlayingRadioCard.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                Long lst = lastTapTime.get("__np__");
                if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                    lastTapTime.remove("__np__"); toggleFav();
                } else {
                    lastTapTime.put("__np__", now); toggleRadio();
                }
            });

            if (tvNowPlayingStop != null)
                tvNowPlayingStop.setOnClickListener(v -> {
                    SleepTimerManager.get().cancel();
                    if (audioService != null) audioService.stopRadio();
                    refresh();
                });

            // Sleep timer button
            if (btnSleepTimer != null) {
                btnSleepTimer.setVisibility(View.VISIBLE);
                long rem = SleepTimerManager.get().isRunning()
                        ? SleepTimerManager.get().getRemainingMs() : 0;
                updateSleepBtn(rem);
                btnSleepTimer.setOnClickListener(v -> showSleepTimerDialog());
            }

            // Visualizer
            if (audioVisualizer != null && tvVisualizerLabel != null) {
                if (powerSaving) {
                    audioVisualizer.setVisibility(View.GONE);
                    tvVisualizerLabel.setVisibility(View.GONE);
                } else {
                    tvVisualizerLabel.setVisibility(View.VISIBLE);
                    audioVisualizer.setVisibility(View.VISIBLE);
                    audioVisualizer.setColors(colors.accent(), colors.visualizerBg());
                    if (radioPlaying && audioService.getRadioAudioSessionId() != 0) {
                        audioVisualizer.attachAudioSession(audioService.getRadioAudioSessionId());
                    } else {
                        audioVisualizer.startIdle();
                    }
                }
            }

        } else {
            nowPlayingRadioCard.setVisibility(View.GONE);
            if (btnSleepTimer    != null) btnSleepTimer.setVisibility(View.GONE);
            if (audioVisualizer  != null) { audioVisualizer.release(); audioVisualizer.setVisibility(View.GONE); }
            if (tvVisualizerLabel != null) tvVisualizerLabel.setVisibility(View.GONE);
            if (radioVolumeWave  != null) { radioVolumeWave.stopWave(); }
        }

        // ── Active sounds ──
        nowPlayingSoundsContainer.removeAllViews();
        List<SoundCategory> allSounds = SoundLoader.load(requireContext());
        if (soundsPlaying) {
            float dp  = getResources().getDisplayMetrics().density;
            int   chipW = (int)(84 * dp), chipH = (int)(64 * dp), gap = (int)(8 * dp);
            for (Map.Entry<String, Float> e : activeSounds.entrySet()) {
                String fn = e.getKey(); float vol = e.getValue();
                View chip = buildSoundChip(fn, soundDisplayName(fn, allSounds), vol, chipW, chipH, powerSaving);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(chipW, chipH);
                lp.setMargins(0, 0, gap, 0); chip.setLayoutParams(lp);
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

    // ── Sound chip with wave + drag vol ──────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private View buildSoundChip(String fn, String label, float vol, int w, int h, boolean powerSaving) {
        FrameLayout frame = new FrameLayout(requireContext());

        WaveView wave = new WaveView(requireContext());
        wave.setPowerSaving(powerSaving);
        int wc = (colors.soundWaveColor() & 0x00FFFFFF) | 0x6A000000;
        wave.setColors(colors.soundBtnActiveBg(), wc);
        wave.setVolume(vol);
        if (!powerSaving) wave.startWave();
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
        gd.setColor(colors.soundBtnActiveBg()); gd.setStroke(dp(1), colors.soundBtnActiveBorder());
        frame.setBackground(gd); frame.setClipToOutline(true);

        wave.setVolumeDragListener(v -> { if (audioService != null) audioService.setSoundVolume(fn, v); });

        final float[]   downY  = {0f};
        final boolean[] inDrag = {false};

        frame.setOnLongClickListener(v -> {
            inDrag[0] = true;
            float cur = audioService != null ? audioService.getSoundVolume(fn) : vol;
            wave.setVolume(cur); wave.beginVolumeDrag(downY[0]); return true;
        });
        frame.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: downY[0] = ev.getY(); inDrag[0] = false; break;
                case MotionEvent.ACTION_MOVE:
                    if (inDrag[0] && wave.isDragging()) { wave.handleDragMove(ev.getY()); return true; } break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    if (inDrag[0]) wave.endDrag(); inDrag[0] = false; break;
            }
            return false;
        });
        frame.setOnClickListener(v -> { if (!wave.isDragging() && audioService != null) { audioService.stopSound(fn); refresh(); } });
        return frame;
    }

    // ── Sleep timer ───────────────────────────────────────────────
    private void updateSleepBtn(long ms) {
        if (btnSleepTimer == null) return;
        float dp = getResources().getDisplayMetrics().density;
        boolean running = ms > 0;
        btnSleepTimer.setText(running ? SleepTimerManager.formatRemaining(ms) : "Sleep Timer");
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(18 * dp);
        gd.setColor(running ? colors.accentDim() : colors.bgCard2());
        gd.setStroke((int)(1 * dp), running ? colors.accent() : colors.divider());
        btnSleepTimer.setBackground(gd);
        btnSleepTimer.setTextColor(running ? colors.accent() : colors.textSecondary());
    }

    private void showSleepTimerDialog() {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int)(20*dp),(int)(20*dp),(int)(20*dp),(int)(16*dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE); bg.setCornerRadius(16*dp); bg.setColor(colors.bgCard());
        root.setBackground(bg);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("Sleep Timer"); tvTitle.setTextColor(colors.textPrimary()); tvTitle.setTextSize(18f);
        tvTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        tvTitle.setPadding(0,0,0,(int)(16*dp)); root.addView(tvTitle);

        AlertDialog[] dlgRef = {null};

        if (SleepTimerManager.get().isRunning()) {
            TextView tvCancel = new TextView(requireContext());
            tvCancel.setText("Cancel  (" + SleepTimerManager.formatRemaining(SleepTimerManager.get().getRemainingMs()) + " left)");
            tvCancel.setTextColor(colors.accent()); tvCancel.setTextSize(14f);
            tvCancel.setPadding(0,0,0,(int)(12*dp)); tvCancel.setClickable(true); tvCancel.setFocusable(true);
            tvCancel.setOnClickListener(v -> { SleepTimerManager.get().cancel(); updateSleepBtn(0); if(dlgRef[0]!=null)dlgRef[0].dismiss(); });
            root.addView(tvCancel);
        } else {
            long[] minOpts = {15, 30, 60, 90, 120};
            String[] labels = {"15 minutes","30 minutes","1 hour","1.5 hours","2 hours"};
            for (int i = 0; i < minOpts.length; i++) {
                final long mins = minOpts[i]; final String lbl = labels[i];
                TextView row = new TextView(requireContext());
                row.setText(lbl); row.setTextColor(colors.textPrimary()); row.setTextSize(15f);
                row.setPadding(0,(int)(13*dp),0,(int)(13*dp));
                row.setClickable(true); row.setFocusable(true);
                row.setOnClickListener(v -> { SleepTimerManager.get().start(mins*60*1000L,audioService); styleSleepBtn(); if(dlgRef[0]!=null)dlgRef[0].dismiss(); });
                root.addView(row);
                View sep = new View(requireContext()); sep.setBackgroundColor(colors.divider());
                sep.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1));
                root.addView(sep);
            }
            TextView rowCustom = new TextView(requireContext());
            rowCustom.setText("Custom…"); rowCustom.setTextColor(colors.accent()); rowCustom.setTextSize(15f);
            rowCustom.setPadding(0,(int)(13*dp),0,(int)(13*dp));
            rowCustom.setClickable(true); rowCustom.setFocusable(true);
            rowCustom.setOnClickListener(v -> { if(dlgRef[0]!=null)dlgRef[0].dismiss(); showCustomTimer(); });
            root.addView(rowCustom);
        }

        AlertDialog dlg = new AlertDialog.Builder(requireContext()).setView(root).create();
        if(dlg.getWindow()!=null) dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dlgRef[0]=dlg;
        if(dlg.getWindow()!=null){int w=(int)(getResources().getDisplayMetrics().widthPixels*0.80f);dlg.getWindow().setLayout(w,ViewGroup.LayoutParams.WRAP_CONTENT);}
        dlg.show();
    }

    private void showCustomTimer() {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int)(20*dp),(int)(20*dp),(int)(20*dp),(int)(16*dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE); bg.setCornerRadius(16*dp); bg.setColor(colors.bgCard());
        root.setBackground(bg);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("Custom Duration (minutes)"); tvTitle.setTextColor(colors.textPrimary()); tvTitle.setTextSize(16f);
        tvTitle.setPadding(0,0,0,(int)(12*dp)); root.addView(tvTitle);

        android.widget.EditText et = new android.widget.EditText(requireContext());
        et.setHint("e.g. 45"); et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setTextColor(colors.textPrimary()); et.setHintTextColor(colors.textSecondary());
        GradientDrawable iBg = new GradientDrawable(); iBg.setShape(GradientDrawable.RECTANGLE); iBg.setCornerRadius(8*dp);
        iBg.setColor(colors.bgCard2()); iBg.setStroke((int)(1*dp),colors.divider()); et.setBackground(iBg);
        et.setPadding((int)(12*dp),(int)(10*dp),(int)(12*dp),(int)(10*dp));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.setMargins(0,0,0,(int)(16*dp)); et.setLayoutParams(elp); root.addView(et);

        LinearLayout btnRow = new LinearLayout(requireContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = makeDialogBtn("Cancel",false,dp), set = makeDialogBtn("Set",true,dp);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0,(int)(42*dp),1f);
        bp.setMargins(0,0,(int)(8*dp),0); cancel.setLayoutParams(bp);
        set.setLayoutParams(new LinearLayout.LayoutParams(0,(int)(42*dp),1f));
        btnRow.addView(cancel); btnRow.addView(set); root.addView(btnRow);

        AlertDialog dlg = new AlertDialog.Builder(requireContext()).setView(root).create();
        if(dlg.getWindow()!=null)dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        cancel.setOnClickListener(v->dlg.dismiss());
        set.setOnClickListener(v->{
            String txt=et.getText().toString().trim(); if(txt.isEmpty())return;
            try{long m=Long.parseLong(txt);if(m<=0||m>480){Toast.makeText(requireContext(),"1–480 minutes",Toast.LENGTH_SHORT).show();return;}
                SleepTimerManager.get().start(m*60*1000L,audioService);styleSleepBtn();dlg.dismiss();}catch(NumberFormatException ignored){}
        });
        dlg.show();
        if(dlg.getWindow()!=null){int w=(int)(getResources().getDisplayMetrics().widthPixels*0.80f);dlg.getWindow().setLayout(w,ViewGroup.LayoutParams.WRAP_CONTENT);}
    }

    private void styleSleepBtn() {
        long ms = SleepTimerManager.get().isRunning() ? SleepTimerManager.get().getRemainingMs() : 0;
        updateSleepBtn(ms);
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

    // ── Helpers ───────────────────────────────────────────────────
    private void toggleFav() {
        if (audioService == null) return;
        String url = audioService.getCurrentRadioUrl(); if (url.isEmpty()) return;
        String key = AppPrefs.stationKey(audioService.getCurrentName(), url, "");
        if (prefs.isFavourite(key)) { prefs.removeFavourite(key); Toast.makeText(requireContext(),"Removed from Favourites",Toast.LENGTH_SHORT).show(); }
        else { prefs.addFavourite(key); Toast.makeText(requireContext(),"♥ Added to Favourites",Toast.LENGTH_SHORT).show(); }
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
        int perRow=cfg.favRadioPerRow(), chipH=cfg.chipHeightDp()+8; float chipR=20f;

        favRadioWrap.removeAllViews();
        List<String> favRadio = deduplicateByUrl(new ArrayList<>(prefs.getFavourites()));
        buildStationRows(favRadioWrap, favRadio, perRow, chipH, chipR);
        tvFavRadioLabel.setVisibility(favRadio.isEmpty()?View.GONE:View.VISIBLE);

        favSoundsWrap.removeAllViews();
        List<String> favSounds = dedupList(new ArrayList<>(prefs.getFavSounds()));
        buildSoundRows(favSoundsWrap, favSounds, allSounds, 3, chipH, chipR);
        tvFavSoundsLabel.setVisibility(favSounds.isEmpty()?View.GONE:View.VISIBLE);

        dividerFav.setVisibility((!favRadio.isEmpty()||!favSounds.isEmpty())?View.VISIBLE:View.GONE);
    }

    private void buildLastPlayed() {
        lastPlayedWrap.removeAllViews();
        List<String> last = deduplicateByUrl(prefs.getLastPlayed());
        if (last.isEmpty()) { tvLastPlayedLabel.setVisibility(View.GONE); dividerLastPlayed.setVisibility(View.GONE); return; }
        tvLastPlayedLabel.setVisibility(View.VISIBLE);
        buildStationRows(lastPlayedWrap, last, cfg.favRadioPerRow(), cfg.chipHeightDp()+8, 20f);
        dividerLastPlayed.setVisibility(View.VISIBLE);
    }

    private void buildStationRows(LinearLayout c, List<String> keys, int perRow, int chipH, float chipR) {
        for (int i=0;i<keys.size();i+=perRow) {
            LinearLayout row=new LinearLayout(requireContext()); row.setOrientation(LinearLayout.HORIZONTAL);
            int added=0;
            for (int j=0;j<perRow&&i+j<keys.size();j++) {
                String key=keys.get(i+j); String[] p=AppPrefs.splitKey(key);
                if(p.length<2||p[0].trim().isEmpty()||p[1].trim().isEmpty()) continue;
                View chip=makeStationChip(p[0].trim(),p[1].trim(),key,chipH,chipR);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(chipH),1f);
                lp.setMargins(0,0,added<perRow-1?dp(6):0,0); chip.setLayoutParams(lp); row.addView(chip); added++;
            }
            if(added==0) continue;
            for(int k=added;k<perRow;k++){View ph=new View(requireContext());ph.setLayoutParams(new LinearLayout.LayoutParams(0,dp(chipH),1f));row.addView(ph);}
            LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0,0,0,dp(8)); row.setLayoutParams(rowLp); c.addView(row);
        }
    }

    private View makeStationChip(String name, String url, String key, int h, float r) {
        String curUrl=audioService!=null?audioService.getCurrentRadioUrl():"";
        boolean current=curUrl.equals(url), playing=current&&audioService!=null&&audioService.isRadioPlaying();
        int bg=playing?colors.stationActiveBg():colors.stationBg();
        int border=playing?colors.stationActiveBorder():colors.divider();
        int textC=playing?colors.stationTextActive():colors.stationText();
        TextView tv=new TextView(requireContext());
        tv.setText(name); tv.setTextColor(textC); tv.setTextSize(13f);
        tv.setGravity(Gravity.CENTER); tv.setSingleLine(true); tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setPadding(dp(12),dp(8),dp(12),dp(8)); tv.setBackground(squircleBg(bg,border,r));
        tv.setOnClickListener(v->{
            long now=System.currentTimeMillis(); Long lst=lastTapTime.get(key);
            if(lst!=null&&(now-lst)<DOUBLE_TAP_MS){
                lastTapTime.remove(key);
                if(prefs.isFavourite(key)){prefs.removeFavourite(key);Toast.makeText(requireContext(),"Removed",Toast.LENGTH_SHORT).show();}
                else{prefs.addFavourite(key);Toast.makeText(requireContext(),"♥ Added",Toast.LENGTH_SHORT).show();}
                refresh();
            }else{
                lastTapTime.put(key,now);
                if(audioService==null)return;
                if(audioService.getCurrentRadioUrl().equals(url)){if(audioService.isRadioPlaying())audioService.pauseAll();else audioService.resumeAll();}
                else{prefs.addLastPlayed(key);audioService.playRadio(name,url);}
                refresh();
            }
        });
        return tv;
    }

    // Sound rows — ALL playing sounds highlighted
    private void buildSoundRows(LinearLayout c, List<String> fns, List<SoundCategory> all, int perRow, int h, float r) {
        for (int i=0;i<fns.size();i+=perRow) {
            LinearLayout row=new LinearLayout(requireContext()); row.setOrientation(LinearLayout.HORIZONTAL);
            int added=0;
            for(int j=0;j<perRow&&i+j<fns.size();j++){
                String fn=fns.get(i+j); if(fn==null||fn.isEmpty()) continue;
                boolean playing=audioService!=null&&audioService.isSoundPlaying(fn);
                final String ffn=fn;
                TextView chip=new TextView(requireContext());
                chip.setText(soundDisplayName(fn,all)); chip.setTextColor(colors.soundBtnText()); chip.setTextSize(13f);
                chip.setGravity(Gravity.CENTER); chip.setSingleLine(true); chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
                chip.setPadding(dp(10),dp(8),dp(10),dp(8));
                chip.setBackground(squircleBg(playing?colors.soundBtnActiveBg():colors.soundBtnBg(),
                        playing?colors.soundBtnActiveBorder():colors.divider(),r));
                chip.setOnClickListener(v->{
                    long now=System.currentTimeMillis(); Long lst=lastTapTime.get("snd_"+ffn);
                    if(lst!=null&&(now-lst)<DOUBLE_TAP_MS){lastTapTime.remove("snd_"+ffn);prefs.removeFavSound(ffn);Toast.makeText(requireContext(),"Removed",Toast.LENGTH_SHORT).show();refresh();}
                    else{lastTapTime.put("snd_"+ffn,now);if(audioService==null)return;if(audioService.isSoundPlaying(ffn))audioService.stopSound(ffn);else audioService.playSound(ffn,0.8f);refresh();}
                });
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(h),1f);
                lp.setMargins(0,0,added<perRow-1?dp(6):0,0); chip.setLayoutParams(lp); row.addView(chip); added++;
            }
            if(added==0) continue;
            for(int k=added;k<perRow;k++){View ph=new View(requireContext());ph.setLayoutParams(new LinearLayout.LayoutParams(0,dp(h),1f));row.addView(ph);}
            LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0,0,0,dp(8)); row.setLayoutParams(rowLp); c.addView(row);
        }
    }

    // ── Drawing helpers ───────────────────────────────────────────
    private GradientDrawable squircleBg(int bg, int border, float r) {
        GradientDrawable gd=new GradientDrawable(); gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(r)); gd.setColor(bg); gd.setStroke(dp(1),border); return gd;
    }

    private void style3DCard(View card, boolean active) {
        float dp=getResources().getDisplayMetrics().density;
        GradientDrawable gd=new GradientDrawable(); gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(18*dp);
        gd.setColor(active?colors.nowPlayingCardBg():colors.bgCard());
        gd.setStroke((int)(1.5f*dp),active?colors.nowPlayingCardBorder():colors.divider());
        card.setBackground(gd); card.setClipToOutline(true);
        card.setTranslationZ(active?8*dp:3*dp);
    }

    private List<String> deduplicateByUrl(List<String> keys) {
        LinkedHashMap<String,String> map=new LinkedHashMap<>();
        for(String key:keys){if(key==null||key.isEmpty())continue;String[]p=AppPrefs.splitKey(key);if(p.length<2||p[1].trim().isEmpty())continue;map.putIfAbsent(p[1].trim().toLowerCase(),key);}
        return new ArrayList<>(map.values());
    }
    private List<String> dedupList(List<String> list) {
        LinkedHashMap<String,Boolean> seen=new LinkedHashMap<>();
        for(String s:list)if(s!=null&&!s.isEmpty())seen.put(s,true); return new ArrayList<>(seen.keySet());
    }
    private String soundDisplayName(String fn, List<SoundCategory> cats) {
        for(SoundCategory cat:cats)for(SoundItem s:cat.getSounds())if(s.getFileName().equals(fn))return s.getName();
        return fn.replaceAll("\\.(mp3|ogg|wav)$","").replace("_"," ");
    }
    private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density);}

    // ── Theme ─────────────────────────────────────────────────────
    public void applyTheme(View root) {
        if (root==null||colors==null) return;
        root.setBackgroundColor(colors.bgPrimary());
        TextView title=root.findViewById(R.id.tvHomePageTitle); if(title!=null)title.setTextColor(colors.pageHeaderText());
        TextView sub=root.findViewById(R.id.tvHomePageSubtitle); if(sub!=null)sub.setTextColor(colors.pageHeaderSubtitleText());
        if(tvNowPlayingLabel!=null)   tvNowPlayingLabel.setTextColor(colors.homeSectionTitle());
        if(tvNowPlayingSoundsLabel!=null)tvNowPlayingSoundsLabel.setTextColor(colors.homeSectionTitle());
        if(tvVisualizerLabel!=null)   tvVisualizerLabel.setTextColor(colors.homeSectionTitle());
        if(tvFavRadioLabel!=null)     tvFavRadioLabel.setTextColor(colors.homeSectionTitle());
        if(tvFavSoundsLabel!=null)    tvFavSoundsLabel.setTextColor(colors.homeSectionTitle());
        if(tvLastPlayedLabel!=null)   tvLastPlayedLabel.setTextColor(colors.homeSectionTitle());
        if(tvStationName!=null)       tvStationName.setTextColor(colors.textPrimary());
        if(tvBuffering!=null)         tvBuffering.setTextColor(colors.nowPlayingAnimColor());
        if(tvNowPlayingStop!=null)    tvNowPlayingStop.setTextColor(colors.textSecondary());
        if(dividerNowPlaying!=null)   dividerNowPlaying.setBackgroundColor(colors.divider());
        if(dividerFav!=null)          dividerFav.setBackgroundColor(colors.divider());
        if(dividerLastPlayed!=null)   dividerLastPlayed.setBackgroundColor(colors.divider());
        if(tvEmpty!=null)             tvEmpty.setTextColor(colors.homeEmptyText());
        styleSleepBtn();
    }

    public void refreshTheme() {
        if(getView()==null)return;
        colors=new ColorConfig(requireContext()); cfg=new SettingsConfig(requireContext());
        applyTheme(getView()); refresh();
    }
}
