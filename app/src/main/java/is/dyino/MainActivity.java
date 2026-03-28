package is.dyino;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import java.util.ArrayList;
import java.util.List;

import is.dyino.service.AudioService;
import is.dyino.ui.radio.RadioFragment;
import is.dyino.ui.settings.SettingsFragment;
import is.dyino.ui.sounds.SoundsFragment;
import is.dyino.util.AppPrefs;
import pl.droidsonroids.gif.GifImageView;

public class MainActivity extends AppCompatActivity {

    private AppPrefs prefs;
    private AudioService audioService;
    private boolean serviceBound = false;

    private TextView navRadioText, navSoundsText, navSettingsText;
    private View navRadio, navSounds, navSettings;

    private RadioFragment radioFragment;
    private SoundsFragment soundsFragment;
    private SettingsFragment settingsFragment;

    private int currentTab = 0; // 0=radio, 1=sounds, 2=settings

    // GIF URLs loaded from Giphy/Tenor
    private List<String> gifUrls = new ArrayList<>();
    private int gifIndex = 0;
    private static final String GIPHY_API_KEY = "dc6zaTOxFJmzC"; // public test key

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioService.LocalBinder binder = (AudioService.LocalBinder) service;
            audioService = binder.getService();
            serviceBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            audioService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new AppPrefs(this);
        setContentView(R.layout.activity_main);

        // Bind audio service
        Intent intent = new Intent(this, AudioService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Nav refs
        navRadio = findViewById(R.id.nav_radio);
        navSounds = findViewById(R.id.nav_sounds);
        navSettings = findViewById(R.id.nav_settings);
        navRadioText = findViewById(R.id.nav_radio_text);
        navSoundsText = findViewById(R.id.nav_sounds_text);
        navSettingsText = findViewById(R.id.nav_settings_text);

        navRadio.setOnClickListener(v -> selectTab(0));
        navSounds.setOnClickListener(v -> selectTab(1));
        navSettings.setOnClickListener(v -> selectTab(2));

        // Fragments
        radioFragment = new RadioFragment();
        soundsFragment = new SoundsFragment();
        settingsFragment = new SettingsFragment();

        // Default tab
        selectTab(0);

        // Load GIFs
        fetchGifs(prefs.getGifTag());
    }

    public void selectTab(int tab) {
        if (prefs.isHapticEnabled()) hapticFeedback();
        currentTab = tab;

        // Update nav highlighting
        int selectedColor = prefs.getTextColor();
        int unselectedColor = 0xFF44445A;

        navRadioText.setTextColor(tab == 0 ? selectedColor : unselectedColor);
        navSoundsText.setTextColor(tab == 1 ? selectedColor : unselectedColor);
        navSettingsText.setTextColor(tab == 2 ? selectedColor : unselectedColor);

        // Bold selected
        navRadioText.setTypeface(null, tab == 0 ? Typeface.BOLD : Typeface.NORMAL);
        navSoundsText.setTypeface(null, tab == 1 ? Typeface.BOLD : Typeface.NORMAL);
        navSettingsText.setTypeface(null, tab == 2 ? Typeface.BOLD : Typeface.NORMAL);

        // Switch fragment
        Fragment fragment;
        switch (tab) {
            case 1: fragment = soundsFragment; break;
            case 2: fragment = settingsFragment; break;
            default: fragment = radioFragment; break;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content_container, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
    }

    public void applyTheme() {
        // Apply background color
        int bg = prefs.getBgColor();
        findViewById(R.id.content_container).setBackgroundColor(bg);
        findViewById(R.id.nav_rail).setBackgroundColor(bg);

        // Re-select tab to update nav colors
        selectTab(currentTab);
    }

    public void applyFont(int fontIndex) {
        // Font is applied per-activity level via system fonts for now
        // Custom .ttf from assets will be loaded in future releases
    }

    public void onGifSettingChanged() {
        if (radioFragment.isAdded()) radioFragment.refreshGifVisibility();
        if (soundsFragment.isAdded()) soundsFragment.refreshGifVisibility();
    }

    public void reloadGifs() {
        fetchGifs(prefs.getGifTag());
    }

    // ─────────────── GIF Loading ───────────────

    public void loadGifInto(GifImageView view) {
        if (gifUrls.isEmpty()) {
            view.setImageResource(0);
            return;
        }
        String url = gifUrls.get(gifIndex % gifUrls.size());
        gifIndex++;
        new Thread(() -> {
            try {
                java.net.URL u = new java.net.URL(url);
                java.io.InputStream is = u.openStream();
                byte[] bytes = readAllBytes(is);
                runOnUiThread(() -> {
                    try {
                        view.setImageDrawable(
                                new pl.droidsonroids.gif.GifDrawable(bytes));
                    } catch (Exception e) {
                        // ignore
                    }
                });
            } catch (Exception e) {
                // ignore
            }
        }).start();
    }

    private byte[] readAllBytes(java.io.InputStream is) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    private void fetchGifs(String tag) {
        new Thread(() -> {
            try {
                // Use Giphy public beta API
                String apiUrl = "https://api.giphy.com/v1/gifs/search?api_key="
                        + GIPHY_API_KEY
                        + "&q=" + tag.replace(" ", "+")
                        + "&limit=10&rating=g";
                java.net.URL u = new java.net.URL(apiUrl);
                java.io.InputStream is = u.openStream();
                byte[] bytes = readAllBytes(is);
                String json = new String(bytes);

                // Simple JSON parsing without Gson
                List<String> urls = new ArrayList<>();
                int idx = 0;
                while (true) {
                    int pos = json.indexOf("\"downsized\"", idx);
                    if (pos < 0) break;
                    int urlPos = json.indexOf("\"url\"", pos);
                    if (urlPos < 0) break;
                    int start = json.indexOf("\"", urlPos + 6) + 1;
                    int end = json.indexOf("\"", start);
                    if (start > 0 && end > start) {
                        urls.add(json.substring(start, end).replace("\\u002F", "/"));
                    }
                    idx = pos + 1;
                }

                if (!urls.isEmpty()) {
                    runOnUiThread(() -> {
                        gifUrls.clear();
                        gifUrls.addAll(urls);
                    });
                }
            } catch (Exception e) {
                // Use fallback static gif
                runOnUiThread(() -> {
                    gifUrls.clear();
                    gifUrls.add("https://media.giphy.com/media/l0HlBO7eyXzSZkJri/giphy.gif");
                    gifUrls.add("https://media.giphy.com/media/3o7TKA3w3KnCWGBtqo/giphy.gif");
                    gifUrls.add("https://media.giphy.com/media/xT9IgvpBFr9YFmmfq8/giphy.gif");
                });
            }
        }).start();
    }

    // ─────────────── Haptic ───────────────

    private void hapticFeedback() {
        try {
            Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null && vib.hasVibrator()) {
                vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) {}
    }

    public AppPrefs getPrefs() { return prefs; }
    public AudioService getAudioService() { return audioService; }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        super.onDestroy();
    }
}
