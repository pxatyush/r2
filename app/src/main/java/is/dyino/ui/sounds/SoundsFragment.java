package is.dyino.ui.sounds;

import android.annotation.SuppressLint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
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
        float dp = getResources().getDisplayMetrics().density;
        int navW = (int)(52 * dp);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int avail = screenW - navW;
        int gap   = (int)(6 * dp);
        int btnW  = (avail - gap * 3) / 2;
        int btnH  = (int)(88 * dp);

        for (SoundCategory cat : categories) {
            View catView = inf.inflate(R.layout.item_sound_category, categoriesContainer, false);
            TextView tvCat  = catView.findViewById(R.id.tvCategoryName);
            GridLayout grid = catView.findViewById(R.id.soundGrid);

            tvCat.setText(cat.getName());
            tvCat.setTextColor(colors.textPrimary());
            grid.setColumnCount(2);
            grid.removeAllViews();

            for (SoundItem sound : cat.getSounds()) {
                View btn = inf.inflate(R.layout.item_sound_button, null);
                TextView tvIcon = btn.findViewById(R.id.tvSoundIcon);
                TextView tvName = btn.findViewById(R.id.tvSoundName);

                tvIcon.setText(sound.getEmoji());
                tvName.setText(sound.getName());
                tvName.setTextColor(colors.textPrimary());

                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = btnW; lp.height = btnH;
                lp.setMargins(gap/2, gap/2, gap/2, gap/2);
                btn.setLayoutParams(lp);

                updateButtonState(btn, sound);
                btn.setOnClickListener(v -> toggleSound(btn, sound));
                setupVolumeSlider(btn, sound);
                grid.addView(btn);
            }
            categoriesContainer.addView(catView);
        }
    }

    private void toggleSound(View btn, SoundItem sound) {
        if (audioService == null) return;
        if (audioService.isSoundPlaying(sound.getFileName())) {
            audioService.stopSound(sound.getFileName());
            sound.setPlaying(false);
        } else {
            audioService.playSound(sound.getFileName(), sound.getVolume());
            sound.setPlaying(true);
        }
        updateButtonState(btn, sound);
    }

    private void updateButtonState(View btn, SoundItem sound) {
        boolean playing = audioService != null && audioService.isSoundPlaying(sound.getFileName());

        // Background
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(18 * getResources().getDisplayMetrics().density);
        if (playing) {
            gd.setColor(colors.soundBtnActiveBg());
            gd.setStroke((int)(1.5f * getResources().getDisplayMetrics().density),
                    colors.soundBtnActiveBorder());
        } else {
            gd.setColor(colors.soundBtnBg());
        }
        btn.setBackground(gd);

        View overlay = btn.findViewById(R.id.volumeOverlay);
        if (overlay == null) return;
        if (playing) {
            overlay.setVisibility(View.VISIBLE);
            float vol = audioService != null
                    ? audioService.getSoundVolume(sound.getFileName()) : sound.getVolume();
            btn.post(() -> {
                ViewGroup.LayoutParams olp = overlay.getLayoutParams();
                olp.width = (int)(btn.getWidth() * vol);
                overlay.setLayoutParams(olp);
            });
        } else {
            overlay.setVisibility(View.GONE);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupVolumeSlider(View btn, SoundItem sound) {
        final boolean[] longPressing = {false};

        btn.setOnLongClickListener(v -> {
            longPressing[0] = true;
            View overlay = btn.findViewById(R.id.volumeOverlay);
            if (overlay != null) overlay.setVisibility(View.VISIBLE);
            return true;
        });

        btn.setOnTouchListener((v, ev) -> {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    if (longPressing[0]) {
                        float ratio = Math.max(0f, Math.min(1f, ev.getX() / v.getWidth()));
                        sound.setVolume(ratio);
                        if (audioService != null)
                            audioService.setSoundVolume(sound.getFileName(), ratio);

                        View overlay = btn.findViewById(R.id.volumeOverlay);
                        if (overlay != null) {
                            ViewGroup.LayoutParams lp = overlay.getLayoutParams();
                            // Clamp to button bounds
                            lp.width = Math.min((int)(v.getWidth() * ratio), v.getWidth());
                            overlay.setLayoutParams(lp);
                        }
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    longPressing[0] = false;
                    break;
            }
            return false;
        });
    }

    private void refreshAllButtons() {
        if (categoriesContainer == null) return;
        for (int i = 0; i < categoriesContainer.getChildCount(); i++) {
            View catView = categoriesContainer.getChildAt(i);
            GridLayout grid = catView.findViewById(R.id.soundGrid);
            if (grid == null) continue;
            for (int j = 0; j < grid.getChildCount(); j++) {
                View btn = grid.getChildAt(j);
                TextView tvName = btn.findViewById(R.id.tvSoundName);
                if (tvName == null) continue;
                String name = tvName.getText().toString();
                for (SoundCategory cat : categories)
                    for (SoundItem s : cat.getSounds())
                        if (s.getName().equals(name)) updateButtonState(btn, s);
            }
        }
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
