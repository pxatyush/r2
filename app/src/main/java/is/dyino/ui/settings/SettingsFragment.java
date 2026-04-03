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
        void onAboutClicked();
    }
    private OnSettingsChanged listener;
    public void setListener(OnSettingsChanged l) { this.listener = l; }

    // Refs for theming
    private View         rootView;
    private TextView     tvTitle;
    private View         divider;
    private LinearLayout cardToggles;
    private LinearLayout cardTheme;
    private LinearLayout cardStations;
    private TextView     tvToggleHeader;
    private TextView     tvHapticLabel;
    private TextView     tvBtnSoundLabel;
    private SwitchCompat swHaptic, swBtnSound;
    private TextView     tvThemeHeader;
    private EditText     etColorCfg;
    private TextView     btnSave;
    private TextView     tvStationsHeader;
    private EditText     etStationUrl;
    private TextView     btnFetch;
    private TextView     tvFetchStatus;
    private TextView     tvCountryHeader;
    private EditText     etCountry;
    private TextView     btnSaveCountry;
    private TextView     tvCountryNote;
    private TextView     btnAbout;
    private TextView     tvVersion;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_settings, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;
        prefs    = new AppPrefs(requireContext());
        colors   = new ColorConfig(requireContext());

        // Bind views
        tvTitle         = view.findViewById(R.id.tvSettingsTitle);
        divider         = view.findViewById(R.id.settingsDivider);
        cardToggles     = view.findViewById(R.id.cardToggles);
        cardTheme       = view.findViewById(R.id.cardTheme);
        cardStations    = view.findViewById(R.id.cardStations);
        tvToggleHeader  = view.findViewById(R.id.tvToggleHeader);
        tvHapticLabel   = view.findViewById(R.id.tvHapticLabel);
        tvBtnSoundLabel = view.findViewById(R.id.tvBtnSoundLabel);
        swHaptic        = view.findViewById(R.id.switchHaptic);
        swBtnSound      = view.findViewById(R.id.switchButtonSound);
        tvThemeHeader   = view.findViewById(R.id.tvThemeHeader);
        etColorCfg      = view.findViewById(R.id.etColorCfg);
        btnSave         = view.findViewById(R.id.btnSaveColors);
        tvStationsHeader= view.findViewById(R.id.tvStationsHeader);
        etStationUrl    = view.findViewById(R.id.etStationUrl);
        btnFetch        = view.findViewById(R.id.btnFetchStations);
        tvFetchStatus   = view.findViewById(R.id.tvFetchStatus);
        tvCountryHeader = view.findViewById(R.id.tvCountryHeader);
        etCountry       = view.findViewById(R.id.etCountry);
        btnSaveCountry  = view.findViewById(R.id.btnSaveCountry);
        tvCountryNote   = view.findViewById(R.id.tvCountryNote);
        btnAbout        = view.findViewById(R.id.btnAbout);
        tvVersion       = view.findViewById(R.id.tvVersion);

        // Init values
        swHaptic.setChecked(prefs.isHapticEnabled());
        swBtnSound.setChecked(prefs.isButtonSoundEnabled());
        etColorCfg.setText(colors.readRaw());
        etCountry.setText(prefs.getRadioCountry());

        // Listeners
        swHaptic.setOnCheckedChangeListener((b, v2)   -> prefs.setHapticEnabled(v2));
        swBtnSound.setOnCheckedChangeListener((b, v2) -> {
            prefs.setButtonSoundEnabled(v2);
            if (listener != null) listener.onButtonSoundChanged(v2);
        });

        btnSave.setOnClickListener(v -> {
            String raw = etColorCfg.getText().toString();
            colors.saveRaw(raw);
            if (listener != null) listener.onThemeChanged();
            Toast.makeText(requireContext(), "Theme saved", Toast.LENGTH_SHORT).show();
        });

        btnSaveCountry.setOnClickListener(v -> {
            String country = etCountry.getText().toString().trim();
            prefs.setRadioCountry(country);
            // Invalidate cache so next radio load fetches fresh
            prefs.saveRadioCache("");
            Toast.makeText(requireContext(),
                "Country set to \"" + (country.isEmpty() ? "General" : country) + "\". Reload Radio to apply.",
                Toast.LENGTH_LONG).show();
        });

        btnFetch.setOnClickListener(v -> {
            String url = etStationUrl.getText().toString().trim();
            if (url.isEmpty()) { tvFetchStatus.setText("Enter a URL first"); return; }
            tvFetchStatus.setText("Fetching…");
            fetchAndMerge(url, tvFetchStatus);
        });

        btnAbout.setOnClickListener(v -> { if (listener != null) listener.onAboutClicked(); });

        applyTheme(view);
    }

    private void fetchAndMerge(String url, TextView statusView) {
        OkHttpClient client = new OkHttpClient();
        Request req = new Request.Builder().url(url).build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                requireActivity().runOnUiThread(() -> statusView.setText("Failed: " + e.getMessage()));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    requireActivity().runOnUiThread(() -> statusView.setText("HTTP " + response.code()));
                    return;
                }
                String body = response.body() != null ? response.body().string() : "";
                mergeStations(body);
                requireActivity().runOnUiThread(() -> statusView.setText("Merged! Go to Radio to see new stations."));
            }
        });
    }

    private void mergeStations(String content) {
        try {
            java.io.File f = new java.io.File(requireContext().getFilesDir(), "radio_extra.cfg");
            java.io.FileWriter fw = new java.io.FileWriter(f, true);
            fw.write("\n" + content); fw.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /** Full theme applied to all visible elements on the Settings screen */
    public void applyTheme(View root) {
        if (root == null || colors == null) return;

        root.setBackgroundColor(colors.bgPrimary());

        if (tvTitle  != null) tvTitle.setTextColor(colors.textPrimary());
        if (divider  != null) divider.setBackgroundColor(colors.divider());

        // Cards
        styleCard(cardToggles);
        styleCard(cardTheme);
        styleCard(cardStations);

        // Toggle labels
        if (tvToggleHeader  != null) tvToggleHeader.setTextColor(colors.textSecondary());
        if (tvHapticLabel   != null) tvHapticLabel.setTextColor(colors.textSettingsLabel());
        if (tvBtnSoundLabel != null) tvBtnSoundLabel.setTextColor(colors.textSettingsLabel());

        // Theme card
        if (tvThemeHeader != null) tvThemeHeader.setTextColor(colors.textSecondary());
        if (etColorCfg    != null) {
            etColorCfg.setTextColor(colors.textPrimary());
            etColorCfg.setHintTextColor(colors.textSecondary());
            styleInputField(etColorCfg);
        }
        if (btnSave != null) styleButton(btnSave);

        // Country card
        if (tvCountryHeader != null) tvCountryHeader.setTextColor(colors.textSecondary());
        if (etCountry       != null) {
            etCountry.setTextColor(colors.textPrimary());
            etCountry.setHintTextColor(colors.textSecondary());
            styleInputField(etCountry);
        }
        if (btnSaveCountry != null) styleButton(btnSaveCountry);
        if (tvCountryNote  != null) tvCountryNote.setTextColor(colors.textSecondary());

        // Stations card
        if (tvStationsHeader != null) tvStationsHeader.setTextColor(colors.textSecondary());
        if (etStationUrl     != null) {
            etStationUrl.setTextColor(colors.textPrimary());
            etStationUrl.setHintTextColor(colors.textSecondary());
            styleInputField(etStationUrl);
        }
        if (btnFetch    != null) styleButton(btnFetch);
        if (tvFetchStatus!= null) tvFetchStatus.setTextColor(colors.textSecondary());

        // About / version
        if (btnAbout  != null) btnAbout.setTextColor(colors.accent());
        if (tvVersion != null) tvVersion.setTextColor(colors.textSettingsVersion());
    }

    private void styleCard(View card) {
        if (card == null) return;
        float dp = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(12 * dp);
        gd.setColor(colors.bgSettingsCard());
        card.setBackground(gd);
    }

    private void styleInputField(EditText et) {
        float dp = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(8 * dp);
        gd.setColor(colors.bgCard2());
        gd.setStroke((int)(1 * dp), colors.divider());
        et.setBackground(gd);
    }

    private void styleButton(TextView btn) {
        float dp = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(10 * dp);
        gd.setColor(colors.btnStopBg());
        gd.setStroke((int)(1.5f * dp), colors.accent());
        btn.setBackground(gd);
        btn.setTextColor(colors.btnStopText());
    }
}