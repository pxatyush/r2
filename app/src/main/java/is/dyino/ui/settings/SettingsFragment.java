package is.dyino.ui.settings;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import java.io.IOException;

import is.dyino.R;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SettingsFragment extends Fragment {

    private AppPrefs    prefs;
    private ColorConfig colors;

    public interface OnSettingsChanged {
        void onThemeChanged();
        void onButtonSoundChanged(boolean enabled);
    }
    private OnSettingsChanged listener;
    public void setListener(OnSettingsChanged l) { this.listener = l; }

    private SwitchCompat swHaptic, swBtnSound, swPersistent;
    private EditText     etColorCfg, etStationUrl, etCountry;
    private TextView     btnSaveColor, btnFetch, tvFetchStatus, btnSaveCountry;
    private TextView     tvCountryNote, tvVersion, tvMadeBy;
    private LinearLayout cardToggles, cardTheme, cardStations;
    private View         dividerSettings;
    private TextView     tvSettingsTitle;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_settings, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs  = new AppPrefs(requireContext());
        colors = new ColorConfig(requireContext());

        tvSettingsTitle = view.findViewById(R.id.tvSettingsTitle);
        dividerSettings = view.findViewById(R.id.settingsDivider);
        cardToggles     = view.findViewById(R.id.cardToggles);
        cardTheme       = view.findViewById(R.id.cardTheme);
        cardStations    = view.findViewById(R.id.cardStations);
        swHaptic        = view.findViewById(R.id.switchHaptic);
        swBtnSound      = view.findViewById(R.id.switchButtonSound);
        swPersistent    = view.findViewById(R.id.switchPersistent);
        etColorCfg      = view.findViewById(R.id.etColorCfg);
        btnSaveColor    = view.findViewById(R.id.btnSaveColors);
        etCountry       = view.findViewById(R.id.etCountry);
        btnSaveCountry  = view.findViewById(R.id.btnSaveCountry);
        tvCountryNote   = view.findViewById(R.id.tvCountryNote);
        etStationUrl    = view.findViewById(R.id.etStationUrl);
        btnFetch        = view.findViewById(R.id.btnFetchStations);
        tvFetchStatus   = view.findViewById(R.id.tvFetchStatus);
        tvMadeBy        = view.findViewById(R.id.tvMadeBy);
        tvVersion       = view.findViewById(R.id.tvVersion);

        swHaptic.setChecked(prefs.isHapticEnabled());
        swBtnSound.setChecked(prefs.isButtonSoundEnabled());
        swPersistent.setChecked(prefs.isPersistentPlayingEnabled());
        etColorCfg.setText(colors.readRaw());
        etCountry.setText(prefs.getRadioCountry());

        swHaptic.setOnCheckedChangeListener((b, v) -> prefs.setHapticEnabled(v));
        swBtnSound.setOnCheckedChangeListener((b, v) -> {
            prefs.setButtonSoundEnabled(v);
            if (listener != null) listener.onButtonSoundChanged(v);
        });
        swPersistent.setOnCheckedChangeListener((b, v) -> prefs.setPersistentPlayingEnabled(v));

        btnSaveColor.setOnClickListener(v -> {
            colors.saveRaw(etColorCfg.getText().toString());
            if (listener != null) listener.onThemeChanged();
            Toast.makeText(requireContext(), "Theme saved", Toast.LENGTH_SHORT).show();
        });

        btnSaveCountry.setOnClickListener(v -> {
            String c = etCountry.getText().toString().trim();
            prefs.setRadioCountry(c);
            prefs.saveRadioCache("");
            Toast.makeText(requireContext(),
                "Set to \"" + (c.isEmpty() ? "Global" : c) + "\". Go to Radio tab.",
                Toast.LENGTH_LONG).show();
        });

        btnFetch.setOnClickListener(v -> {
            String url = etStationUrl.getText().toString().trim();
            if (url.isEmpty()) { tvFetchStatus.setText("Enter a URL first"); return; }
            tvFetchStatus.setText("Fetching…");
            fetchAndMerge(url);
        });

        applyTheme(view);
    }

    private void fetchAndMerge(String url) {
        new OkHttpClient().newCall(new Request.Builder().url(url).build())
            .enqueue(new Callback() {
                @Override public void onFailure(Call c, IOException e) {
                    requireActivity().runOnUiThread(() -> tvFetchStatus.setText("Failed: " + e.getMessage()));
                }
                @Override public void onResponse(Call c, Response r) throws IOException {
                    if (!r.isSuccessful()) {
                        requireActivity().runOnUiThread(() -> tvFetchStatus.setText("HTTP " + r.code())); return;
                    }
                    String body = r.body() != null ? r.body().string() : "";
                    try {
                        java.io.File f = new java.io.File(requireContext().getFilesDir(), "radio_extra.cfg");
                        java.io.FileWriter fw = new java.io.FileWriter(f, true);
                        fw.write("\n" + body); fw.close();
                    } catch (Exception ignored) {}
                    requireActivity().runOnUiThread(() -> tvFetchStatus.setText("Done. Go to Radio tab."));
                }
            });
    }

    public void applyTheme(View root) {
        if (root == null || colors == null) return;

        root.setBackgroundColor(colors.bgPrimary());
        if (tvSettingsTitle != null) tvSettingsTitle.setTextColor(colors.settingsPageTitle());
        if (dividerSettings != null) dividerSettings.setBackgroundColor(colors.settingsDivider());

        styleCard(cardToggles);
        styleCard(cardTheme);
        styleCard(cardStations);

        lbl(root.findViewById(R.id.tvToggleHeader));
        lbl(root.findViewById(R.id.tvHapticLabel));
        lbl(root.findViewById(R.id.tvBtnSoundLabel));
        lbl(root.findViewById(R.id.tvPersistentLabel));
        sub(root.findViewById(R.id.tvPersistentSub));
        lbl(root.findViewById(R.id.tvThemeHeader));
        lbl(root.findViewById(R.id.tvCountryHeader));
        lbl(root.findViewById(R.id.tvCountryNote));
        lbl(root.findViewById(R.id.tvStationsHeader));

        styleInput(etColorCfg);
        styleInput(etCountry);
        styleInput(etStationUrl);
        styleBtn(btnSaveColor);
        styleBtn(btnSaveCountry);
        styleBtn(btnFetch);

        if (tvFetchStatus != null) tvFetchStatus.setTextColor(colors.textSettingsHint());
        if (tvVersion     != null) tvVersion.setTextColor(colors.textSettingsVersion());
        if (tvMadeBy      != null) applyMadeByColors();
    }

    private void styleCard(View card) {
        if (card == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(12 * dp);
        gd.setColor(colors.bgSettingsCard()); gd.setStroke((int)(1*dp), colors.settingsCardBorder());
        card.setBackground(gd);
    }

    private void styleInput(EditText et) {
        if (et == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(8 * dp);
        gd.setColor(colors.settingsInputBg()); gd.setStroke((int)(1*dp), colors.settingsInputBorder());
        et.setBackground(gd);
        et.setTextColor(colors.settingsInputText());
        et.setHintTextColor(colors.textSettingsHint());
    }

    private void styleBtn(TextView btn) {
        if (btn == null) return;
        float dp = density();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(10 * dp);
        gd.setColor(colors.settingsBtnBg()); gd.setStroke((int)(1.5f*dp), colors.settingsBtnBorder());
        btn.setBackground(gd); btn.setTextColor(colors.settingsBtnText());
    }

    private void lbl(View v) {
        if (v instanceof TextView) ((TextView)v).setTextColor(colors.textSettingsLabel());
    }
    private void sub(View v) {
        if (v instanceof TextView) ((TextView)v).setTextColor(colors.textSettingsHint());
    }

    private void applyMadeByColors() {
        if (tvMadeBy == null) return;
        android.text.SpannableString ss = new android.text.SpannableString("Made by pxatyush");
        ss.setSpan(new android.text.style.ForegroundColorSpan(colors.settingsMadeByText()),
                   0, 8, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new android.text.style.ForegroundColorSpan(colors.settingsMadeByBrand()),
                   8, ss.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvMadeBy.setText(ss);
    }

    private float density() { return getResources().getDisplayMetrics().density; }
}
