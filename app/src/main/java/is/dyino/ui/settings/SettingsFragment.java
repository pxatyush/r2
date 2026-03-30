package is.dyino.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import is.dyino.R;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class SettingsFragment extends Fragment {

    private AppPrefs   prefs;
    private ColorConfig colors;

    public interface OnSettingsChanged {
        void onThemeChanged();
        void onButtonSoundChanged(boolean enabled);
        void onAboutClicked();
    }
    private OnSettingsChanged listener;
    public void setListener(OnSettingsChanged l) { this.listener = l; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_settings, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs  = new AppPrefs(requireContext());
        colors = new ColorConfig(requireContext());

        SwitchCompat swHaptic    = view.findViewById(R.id.switchHaptic);
        SwitchCompat swBtnSound  = view.findViewById(R.id.switchButtonSound);
        EditText     etColor     = view.findViewById(R.id.etColorCfg);
        TextView     btnSave     = view.findViewById(R.id.btnSaveColors);
        EditText     etStationUrl= view.findViewById(R.id.etStationUrl);
        TextView     btnFetch    = view.findViewById(R.id.btnFetchStations);
        TextView     tvStatus    = view.findViewById(R.id.tvFetchStatus);
        TextView     btnAbout    = view.findViewById(R.id.btnAbout);

        swHaptic.setChecked(prefs.isHapticEnabled());
        swBtnSound.setChecked(prefs.isButtonSoundEnabled());
        etColor.setText(colors.readRaw());

        swHaptic.setOnCheckedChangeListener((b, v2)   -> prefs.setHapticEnabled(v2));
        swBtnSound.setOnCheckedChangeListener((b, v2) -> {
            prefs.setButtonSoundEnabled(v2);
            if (listener != null) listener.onButtonSoundChanged(v2);
        });

        btnSave.setOnClickListener(v -> {
            String raw = etColor.getText().toString();
            colors.saveRaw(raw);
            if (listener != null) listener.onThemeChanged();
            Toast.makeText(requireContext(), "Colors saved — restart to fully apply", Toast.LENGTH_SHORT).show();
        });

        btnFetch.setOnClickListener(v -> {
            String url = etStationUrl.getText().toString().trim();
            if (url.isEmpty()) { tvStatus.setText("Enter a URL first"); return; }
            tvStatus.setText("Fetching…");
            fetchAndMerge(url, tvStatus);
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
                // Append to internal radio/config.txt
                mergeStations(body);
                requireActivity().runOnUiThread(() -> {
                    statusView.setText("Merged! Restart to see new stations.");
                });
            }
        });
    }

    private void mergeStations(String content) {
        // Write to internal files so RadioLoader picks it up
        try {
            java.io.File f = new java.io.File(requireContext().getFilesDir(), "radio_extra.cfg");
            java.io.FileWriter fw = new java.io.FileWriter(f, true); // append
            fw.write("\n" + content);
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void applyTheme(View root) {
        if (root == null || colors == null) return;
        root.setBackgroundColor(colors.bgPrimary());
    }
}
