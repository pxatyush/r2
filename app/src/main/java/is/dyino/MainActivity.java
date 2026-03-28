package is.dyino;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import is.dyino.service.AudioService;
import is.dyino.ui.radio.RadioFragment;
import is.dyino.ui.settings.SettingsFragment;
import is.dyino.ui.sounds.SoundsFragment;
import is.dyino.util.AppPrefs;

public class MainActivity extends AppCompatActivity {

    private AudioService audioService;
    private boolean serviceBound = false;

    private TextView navRadioText, navSoundsText, navSettingsText;
    private FrameLayout fragmentRadio, fragmentSounds, fragmentSettings;

    private RadioFragment radioFragment;
    private SoundsFragment soundsFragment;
    private SettingsFragment settingsFragment;

    private AppPrefs prefs;
    private int currentTab = 0;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioService.LocalBinder binder = (AudioService.LocalBinder) service;
            audioService = binder.getService();
            serviceBound = true;
            if (radioFragment != null) radioFragment.setAudioService(audioService);
            if (soundsFragment != null) soundsFragment.setAudioService(audioService);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new AppPrefs(this);

        getWindow().getDecorView().setBackgroundColor(prefs.getBgColor());
        getWindow().setStatusBarColor(prefs.getBgColor());
        getWindow().setNavigationBarColor(prefs.getBgColor());

        setContentView(R.layout.activity_main);

        Intent svcIntent = new Intent(this, AudioService.class);
        startService(svcIntent);
        bindService(svcIntent, serviceConnection, BIND_AUTO_CREATE);

        navRadioText    = findViewById(R.id.navRadioText);
        navSoundsText   = findViewById(R.id.navSoundsText);
        navSettingsText = findViewById(R.id.navSettingsText);
        fragmentRadio   = findViewById(R.id.fragmentRadio);
        fragmentSounds  = findViewById(R.id.fragmentSounds);
        fragmentSettings= findViewById(R.id.fragmentSettings);

        View navRadio    = findViewById(R.id.navRadio);
        View navSounds   = findViewById(R.id.navSounds);
        View navSettings = findViewById(R.id.navSettings);

        radioFragment    = new RadioFragment();
        soundsFragment   = new SoundsFragment();
        settingsFragment = new SettingsFragment();

        settingsFragment.setListener(new SettingsFragment.OnSettingsChanged() {
            @Override public void onGifToggled(boolean enabled) {
                if (radioFragment != null)  radioFragment.refreshGif();
                if (soundsFragment != null) soundsFragment.refreshGif();
            }
            @Override public void onThemeChanged() { applyThemeToAll(); }
            @Override public void onButtonSoundChanged(boolean enabled) {
                if (audioService != null) audioService.setButtonSoundEnabled(enabled);
            }
        });

        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentRadio,    radioFragment)
                .add(R.id.fragmentSounds,   soundsFragment)
                .add(R.id.fragmentSettings, settingsFragment)
                .commit();

        navRadio.setOnClickListener(v    -> switchTab(0));
        navSounds.setOnClickListener(v   -> switchTab(1));
        navSettings.setOnClickListener(v -> switchTab(2));

        switchTab(0);
        applyThemeToAll();
    }

    @SuppressWarnings("deprecation")
    private void doHaptic() {
        if (!prefs.isHapticEnabled()) return;
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                android.os.VibrationEffect.createOneShot(
                    18, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(18);
        }
    }

    private void switchTab(int tab) {
        doHaptic();
        currentTab = tab;

        fragmentRadio.setVisibility(tab == 0    ? View.VISIBLE : View.GONE);
        fragmentSounds.setVisibility(tab == 1   ? View.VISIBLE : View.GONE);
        fragmentSettings.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);

        int selectedColor   = prefs.getTextColor();
        int unselectedColor = Color.parseColor("#44445A");

        navRadioText.setTextColor(tab == 0    ? selectedColor : unselectedColor);
        navSoundsText.setTextColor(tab == 1   ? selectedColor : unselectedColor);
        navSettingsText.setTextColor(tab == 2 ? selectedColor : unselectedColor);

        navRadioText.setTextSize(tab == 0    ? 12.5f : 11f);
        navSoundsText.setTextSize(tab == 1   ? 12.5f : 11f);
        navSettingsText.setTextSize(tab == 2 ? 12.5f : 11f);

        if (serviceBound && audioService != null) {
            if (tab == 0) radioFragment.setAudioService(audioService);
            if (tab == 1) soundsFragment.setAudioService(audioService);
        }

        if (audioService != null && prefs.isButtonSoundEnabled()) {
            audioService.playClickSound();
        }
    }

    private void applyThemeToAll() {
        int bg = prefs.getBgColor();
        getWindow().getDecorView().setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        View root   = findViewById(R.id.rootLayout);
        View navBar = findViewById(R.id.navBar);
        if (root   != null) root.setBackgroundColor(bg);
        if (navBar != null) navBar.setBackgroundColor(bg);

        if (radioFragment.getView()    != null) radioFragment.applyTheme(radioFragment.getView());
        if (soundsFragment.getView()   != null) soundsFragment.applyTheme(soundsFragment.getView());

        switchTab(currentTab);
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        super.onDestroy();
    }
}
