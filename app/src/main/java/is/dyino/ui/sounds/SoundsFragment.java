package is.dyino.ui.sounds;

import android.annotation.SuppressLint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

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

    private AppPrefs     prefs;
    private ColorConfig  colors;
    private AudioService audioService;
    private List<SoundCategory> categories;

    public void setAudioService(AudioService svc) { this.audioService = svc; }

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

        applyTheme(view);
        categories = SoundLoader.load(requireContext());
        buildGrid();

        btnStopAll.setOnClickListener(v -> {
            haptic(); clickSound();
            if (audioService != null) audioService.stopAllSounds();
            for (SoundCategory cat : categories)
                for (SoundItem s : cat.getSounds()) s.setPlaying(false);
            refreshAllButtons();
        });
    }

    private void buildGrid() {
        if (categoriesContainer == null || categories == null) return;
        categoriesContainer.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(requireContext());
        float dp     = getResources().getDisplayMetrics().density;
        int navW     = (int)(52 * dp);
        int screenW  = getResources().getDisplayMetrics().widthPixels;
        int avail    = screenW - navW;
        int gap      = (int)(6 * dp);
        int btnW     = (avail - gap * 3) / 2;
        int btnH     = (int)(52 * dp);   // thin

        for (SoundCategory cat : categories) {
            View catView = inf.inflate(R.layout.item_sound_category, categoriesContainer, false);
            TextView tvCat  = catView.findViewById(R.id.tvCategoryName);
            GridLayout grid = catView.findViewById(R.id.soundGrid);
            tvCat.setText(cat.getName());
            tvCat.setTextColor(colors.textSectionTitle());
            grid.setColumnCount(2);
            grid.removeAllViews();

            for (SoundItem sound : cat.getSounds()) {
                View btn = inf.inflate(R.layout.item_sound_button, null);
                setupButton(btn, sound, btnW, btnH);
                grid.addView(btn);
            }
            categoriesContainer.addView(catView);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupButton(View btn, SoundItem sound, int btnW, int btnH) {
        TextView  tvName   = btn.findViewById(R.id.tvSoundName);
        WaveView  wave     = btn.findViewById(R.id.waveView);
        FrameLayout volOverlay = btn.findViewById(R.id.volumeControlOverlay);
        SeekBar   seekBar  = btn.findViewById(R.id.soundVolumeSeek);

        tvName.setText(sound.getName());
        tvName.setTextColor(colors.soundBtnText());

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = btnW; lp.height = btnH;
        lp.setMargins((int)(3*getResources().getDisplayMetrics().density),
                      (int)(3*getResources().getDisplayMetrics().density),
                      (int)(3*getResources().getDisplayMetrics().density),
                      (int)(3*getResources().getDisplayMetrics().density));
        btn.setLayoutParams(lp);

        // Set corner background
        applyBtnShape(btn, sound, wave);

        // SeekBar init
        int prog = (int)(sound.getVolume() * 100);
        seekBar.setProgress(prog);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                float vol = p / 100f;
                sound.setVolume(vol);
                if (audioService != null) audioService.setSoundVolume(sound.getFileName(), vol);
                if (wave != null) wave.setVolume(vol);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        // Click: toggle play
        btn.setOnClickListener(v -> {
            haptic(); clickSound();
            if (audioService == null) return;
            if (audioService.isSoundPlaying(sound.getFileName())) {
                audioService.stopSound(sound.getFileName());
                sound.setPlaying(false);
            } else {
                audioService.playSound(sound.getFileName(), sound.getVolume());
                sound.setPlaying(true);
            }
            applyBtnShape(btn, sound, wave);
        });

        // Long click: show volume seek overlay
        btn.setOnLongClickListener(v -> {
            haptic();
            boolean showing = volOverlay.getVisibility() == View.VISIBLE;
            volOverlay.setVisibility(showing ? View.GONE : View.VISIBLE);
            if (!showing) {
                seekBar.setProgress((int)(sound.getVolume() * 100));
            }
            return true;
        });

        // Touch outside vol overlay dismisses it
        btn.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_DOWN
                    && volOverlay.getVisibility() == View.VISIBLE) {
                // Check touch outside seekBar area
                int[] loc = new int[2]; seekBar.getLocationOnScreen(loc);
                float ex = ev.getRawX(), ey = ev.getRawY();
                boolean inSeek = ex >= loc[0] && ex <= loc[0] + seekBar.getWidth()
                              && ey >= loc[1] && ey <= loc[1] + seekBar.getHeight();
                if (!inSeek) {
                    volOverlay.setVisibility(View.GONE);
                }
            }
            return false;
        });
    }

    private void applyBtnShape(View btn, SoundItem sound, WaveView wave) {
        boolean playing = audioService != null && audioService.isSoundPlaying(sound.getFileName());
        float dp = getResources().getDisplayMetrics().density;
        float r  = 14 * dp;

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(r);
        if (playing) {
            gd.setColor(colors.soundBtnActiveBg());
            gd.setStroke((int)(1.5f * dp), colors.soundBtnActiveBorder());
        } else {
            gd.setColor(colors.soundBtnBg());
        }
        btn.setBackground(gd);

        if (wave != null) {
            if (playing) {
                float vol = audioService != null
                    ? audioService.getSoundVolume(sound.getFileName()) : sound.getVolume();
                // wave color = accent at 35% opacity
                int wc = (colors.soundWaveColor() & 0x00FFFFFF) | 0x5A000000;
                wave.setColors(colors.soundBtnActiveBg(), wc);
                wave.setVolume(vol);
                wave.setVisibility(View.VISIBLE);
                wave.startWave();
            } else {
                wave.stopWave();
                wave.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void refreshAllButtons() {
        if (categoriesContainer == null) return;
        for (int i = 0; i < categoriesContainer.getChildCount(); i++) {
            View catView = categoriesContainer.getChildAt(i);
            GridLayout grid = catView.findViewById(R.id.soundGrid);
            if (grid == null) continue;
            for (int j = 0; j < grid.getChildCount(); j++) {
                View btn = grid.getChildAt(j);
                WaveView wave = btn.findViewById(R.id.waveView);
                TextView tvName = btn.findViewById(R.id.tvSoundName);
                if (tvName == null) continue;
                String name = tvName.getText().toString();
                for (SoundCategory cat : categories)
                    for (SoundItem s : cat.getSounds())
                        if (s.getName().equals(name)) applyBtnShape(btn, s, wave);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void haptic() {
        if (prefs == null || !prefs.isHapticEnabled()) return;
        Vibrator vib = (Vibrator) requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
        if (vib == null || !vib.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vib.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
        else vib.vibrate(12);
    }

    private void clickSound() {
        if (audioService != null && prefs != null && prefs.isButtonSoundEnabled())
            audioService.playClickSound();
    }

    public void applyTheme(View root) {
        if (root == null || colors == null) return;
        root.setBackgroundColor(colors.bgPrimary());
    }

    public void refresh() {
        if (getView() == null) return;
        colors = new ColorConfig(requireContext());
        applyTheme(getView());
        buildGrid();
    }
}
