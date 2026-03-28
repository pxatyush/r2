package is.dyino.ui.settings;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import is.dyino.MainActivity;
import is.dyino.R;
import is.dyino.util.AppPrefs;

public class SettingsFragment extends Fragment {

    private AppPrefs prefs;

    private static final int[] ACCENT_COLORS = {
        0xFF6C63FF, 0xFF00BCD4, 0xFF4CAF50, 0xFFFF5722,
        0xFFFF9800, 0xFFE91E63, 0xFFFFFFFF, 0xFF888888
    };
    private static final int[] BG_COLORS = {
        0xFF0D0D14, 0xFF0A0A0A, 0xFF0D1117, 0xFF1A0A0A,
        0xFF0A1A0A, 0xFF0A0A1A, 0xFF1A1A1A, 0xFF121212
    };
    private static final int[] TEXT_COLORS = {
        0xFFFFFFFF, 0xFFEEEEEE, 0xFFCCCCCC, 0xFFAABBCC,
        0xFFCCBBAA, 0xFFAAFFAA, 0xFFFFCCFF, 0xFFFFFFAA
    };

    private static final String[] FONT_OPTIONS = {
        "Default", "Monospace", "Serif", "Sans-serif Light", "Sans-serif Condensed"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);
        prefs = new AppPrefs(requireContext());

        setupToggles(root);
        setupGifTag(root);
        setupColorPicker(root, R.id.accent_colors, ACCENT_COLORS, 0);
        setupColorPicker(root, R.id.bg_colors, BG_COLORS, 1);
        setupColorPicker(root, R.id.text_colors, TEXT_COLORS, 2);
        setupFontSpinner(root);

        return root;
    }

    private void setupToggles(View root) {
        Switch swHaptic = root.findViewById(R.id.sw_haptic);
        Switch swBtnSound = root.findViewById(R.id.sw_button_sound);
        Switch swDark = root.findViewById(R.id.sw_dark_mode);
        Switch swGif = root.findViewById(R.id.sw_show_gif);

        swHaptic.setChecked(prefs.isHapticEnabled());
        swBtnSound.setChecked(prefs.isButtonSoundEnabled());
        swDark.setChecked(prefs.isDarkMode());
        swGif.setChecked(prefs.isGifEnabled());

        swHaptic.setOnCheckedChangeListener((b, v) -> prefs.setHapticEnabled(v));
        swBtnSound.setOnCheckedChangeListener((b, v) -> prefs.setButtonSoundEnabled(v));
        swDark.setOnCheckedChangeListener((b, v) -> {
            prefs.setDarkMode(v);
            MainActivity main = (MainActivity) getActivity();
            if (main != null) main.applyTheme();
        });
        swGif.setOnCheckedChangeListener((b, v) -> {
            prefs.setGifEnabled(v);
            MainActivity main = (MainActivity) getActivity();
            if (main != null) main.onGifSettingChanged();
        });
    }

    private void setupGifTag(View root) {
        EditText etGifTag = root.findViewById(R.id.et_gif_tag);
        etGifTag.setText(prefs.getGifTag());
        etGifTag.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    prefs.setGifTag(s.toString());
                    MainActivity main = (MainActivity) getActivity();
                    if (main != null) main.reloadGifs();
                }
            }
        });
    }

    private void setupColorPicker(View root, int containerId, int[] colors, int type) {
        LinearLayout container = root.findViewById(containerId);
        container.removeAllViews();

        int selectedColor = type == 0 ? prefs.getAccentColor()
                : type == 1 ? prefs.getBgColor() : prefs.getTextColor();

        for (int color : colors) {
            View swatch = new View(getContext());
            int sizePx = (int)(36 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
            params.setMargins(4, 0, 4, 0);
            swatch.setLayoutParams(params);

            // Circle shape
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(color);
            if (color == selectedColor) {
                circle.setStroke(3, 0xFFFFFFFF);
            }
            swatch.setBackground(circle);

            final int c = color;
            swatch.setOnClickListener(v -> {
                if (type == 0) prefs.setAccentColor(c);
                else if (type == 1) prefs.setBgColor(c);
                else prefs.setTextColor(c);
                // Refresh swatches
                setupColorPicker(root, containerId, colors, type);
                // Apply theme
                MainActivity main = (MainActivity) getActivity();
                if (main != null) main.applyTheme();
            });

            container.addView(swatch);
        }
    }

    private void setupFontSpinner(View root) {
        Spinner spinner = root.findViewById(R.id.spinner_font);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                FONT_OPTIONS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(prefs.getFontIndex());
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                prefs.setFontIndex(pos);
                MainActivity main = (MainActivity) getActivity();
                if (main != null) main.applyFont(pos);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
}
