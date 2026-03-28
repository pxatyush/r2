package is.dyino.ui.settings;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import is.dyino.R;
import is.dyino.util.AppPrefs;

public class SettingsFragment extends Fragment {

    private AppPrefs prefs;

    public interface OnSettingsChanged {
        void onGifToggled(boolean enabled);
        void onThemeChanged();
        void onButtonSoundChanged(boolean enabled);
    }

    private OnSettingsChanged listener;
    public void setListener(OnSettingsChanged l) { this.listener = l; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = new AppPrefs(requireContext());

        SwitchCompat swHaptic      = view.findViewById(R.id.switchHaptic);
        SwitchCompat swButtonSound = view.findViewById(R.id.switchButtonSound);
        SwitchCompat swDarkMode    = view.findViewById(R.id.switchDarkMode);
        SwitchCompat swGif         = view.findViewById(R.id.switchGif);
        EditText     etGifTag      = view.findViewById(R.id.etGifTag);

        LinearLayout accentRow = view.findViewById(R.id.accentColorRow);
        LinearLayout bgRow     = view.findViewById(R.id.bgColorRow);
        LinearLayout textRow   = view.findViewById(R.id.textColorRow);
        LinearLayout fontRow   = view.findViewById(R.id.fontStyleRow);

        swHaptic.setChecked(prefs.isHapticEnabled());
        swButtonSound.setChecked(prefs.isButtonSoundEnabled());
        swDarkMode.setChecked(prefs.isDarkMode());
        swGif.setChecked(prefs.isGifEnabled());
        if (etGifTag != null) etGifTag.setText(prefs.getGifTag());

        swHaptic.setOnCheckedChangeListener((b, v2) -> prefs.setHapticEnabled(v2));

        swButtonSound.setOnCheckedChangeListener((b, v2) -> {
            prefs.setButtonSoundEnabled(v2);
            if (listener != null) listener.onButtonSoundChanged(v2);
        });

        swDarkMode.setOnCheckedChangeListener((b, v2) -> {
            prefs.setDarkMode(v2);
            if (v2) {
                prefs.setBgColor(Color.parseColor("#0D0D14"));
                prefs.setTextColor(Color.WHITE);
            } else {
                prefs.setBgColor(Color.parseColor("#F0F0F0"));
                prefs.setTextColor(Color.parseColor("#111111"));
            }
            if (listener != null) listener.onThemeChanged();
        });

        swGif.setOnCheckedChangeListener((b, v2) -> {
            prefs.setGifEnabled(v2);
            if (listener != null) listener.onGifToggled(v2);
        });

        if (etGifTag != null) {
            etGifTag.setOnEditorActionListener((tv, actionId, event) -> {
                prefs.setGifTag(tv.getText().toString().trim());
                if (listener != null) listener.onGifToggled(prefs.isGifEnabled());
                return false;
            });
        }

        int[] accentColors = {
                Color.parseColor("#6C63FF"), Color.parseColor("#FF6584"),
                Color.parseColor("#43E97B"), Color.parseColor("#FA8231"),
                Color.parseColor("#00B5FF"), Color.parseColor("#F7B731"),
        };
        buildColorRow(accentRow, accentColors, prefs.getAccentColor(), color -> {
            prefs.setAccentColor(color);
            if (listener != null) listener.onThemeChanged();
        });

        int[] bgColors = {
                Color.parseColor("#0D0D14"), Color.parseColor("#0A1628"),
                Color.parseColor("#0D1A0D"), Color.parseColor("#1A0D0D"),
                Color.parseColor("#1A1A0D"), Color.parseColor("#F0F0F0"),
        };
        buildColorRow(bgRow, bgColors, prefs.getBgColor(), color -> {
            prefs.setBgColor(color);
            if (listener != null) listener.onThemeChanged();
        });

        int[] textColors = {
                Color.WHITE,                Color.parseColor("#E0E0FF"),
                Color.parseColor("#FFE0E0"), Color.parseColor("#E0FFE0"),
                Color.parseColor("#FFFFCC"), Color.parseColor("#111111"),
        };
        buildColorRow(textRow, textColors, prefs.getTextColor(), color -> {
            prefs.setTextColor(color);
            if (listener != null) listener.onThemeChanged();
        });

        buildFontRow(fontRow);
    }

    interface ColorPicker { void onColorSelected(int color); }

    private void buildColorRow(LinearLayout row, int[] colors, int selected, ColorPicker picker) {
        if (row == null) return;
        row.removeAllViews();
        float dp   = getResources().getDisplayMetrics().density;
        int size   = (int) (32 * dp);
        int margin = (int) (6 * dp);

        for (int color : colors) {
            View circle = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, margin, 0);
            circle.setLayoutParams(lp);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(color);
            if (color == selected) gd.setStroke((int)(2.5f * dp), Color.WHITE);
            circle.setBackground(gd);

            final int c = color;
            circle.setOnClickListener(v -> {
                picker.onColorSelected(c);
                buildColorRow(row, colors, c, picker);
            });
            row.addView(circle);
        }
    }

    private void buildFontRow(LinearLayout row) {
        if (row == null) return;
        row.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;

        String[] names = {"Default", "Serif", "Mono"};
        Typeface[] faces = {Typeface.DEFAULT, Typeface.SERIF, Typeface.MONOSPACE};
        int selected = prefs.getFontStyle();

        for (int i = 0; i < names.length; i++) {
            TextView tv = new TextView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, (int)(36 * dp));
            lp.setMargins(0, 0, (int)(8 * dp), 0);
            tv.setLayoutParams(lp);
            tv.setText(names[i]);
            tv.setTypeface(faces[i]);
            tv.setTextColor(i == selected ? Color.WHITE : Color.parseColor("#666680"));
            tv.setTextSize(13);
            tv.setPadding((int)(12 * dp), 0, (int)(12 * dp), 0);
            tv.setGravity(Gravity.CENTER_VERTICAL);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(8 * dp);
            bg.setColor(i == selected ? Color.parseColor("#2A2A4A") : Color.parseColor("#1E1E2A"));
            tv.setBackground(bg);

            final int idx = i;
            tv.setOnClickListener(v -> {
                prefs.setFontStyle(idx);
                buildFontRow(row);
            });
            row.addView(tv);
        }
    }
}
