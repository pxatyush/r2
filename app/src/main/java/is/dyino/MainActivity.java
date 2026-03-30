package is.dyino;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import is.dyino.service.AudioService;
import is.dyino.ui.about.AboutFragment;
import is.dyino.ui.radio.RadioFragment;
import is.dyino.ui.settings.SettingsFragment;
import is.dyino.ui.sounds.SoundsFragment;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;

public class MainActivity extends AppCompatActivity {

    private AudioService audioService;
    private boolean      serviceBound = false;

    private TextView     navRadioText, navSoundsText, navSettingsText;
    private FrameLayout  fragmentRadio, fragmentSounds, fragmentSettings, fragmentAbout;

    private RadioFragment    radioFragment;
    private SoundsFragment   soundsFragment;
    private SettingsFragment settingsFragment;
    private AboutFragment    aboutFragment;

    private AppPrefs   prefs;
    private ColorConfig colors;
    private int        currentTab = 0;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            audioService = ((AudioService.LocalBinder) b).getService();
            serviceBound = true;
            radioFragment.setAudioService(audioService);
            soundsFragment.setAudioService(audioService);
            audioService.setButtonSoundEnabled(prefs.isButtonSoundEnabled());
        }
        @Override public void onServiceDisconnected(ComponentName n) { serviceBound = false; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs  = new AppPrefs(this);
        colors = new ColorConfig(this);

        applyWindowColors();
        setContentView(R.layout.activity_main);

        // Start & bind service
        Intent svc = new Intent(this, AudioService.class);
        startService(svc);
        bindService(svc, conn, BIND_AUTO_CREATE);

        navRadioText    = findViewById(R.id.navRadioText);
        navSoundsText   = findViewById(R.id.navSoundsText);
        navSettingsText = findViewById(R.id.navSettingsText);
        fragmentRadio   = findViewById(R.id.fragmentRadio);
        fragmentSounds  = findViewById(R.id.fragmentSounds);
        fragmentSettings= findViewById(R.id.fragmentSettings);
        fragmentAbout   = findViewById(R.id.fragmentAbout);

        radioFragment    = new RadioFragment();
        soundsFragment   = new SoundsFragment();
        settingsFragment = new SettingsFragment();
        aboutFragment    = new AboutFragment();

        settingsFragment.setListener(new SettingsFragment.OnSettingsChanged() {
            @Override public void onThemeChanged() {
                colors = new ColorConfig(MainActivity.this);
                applyWindowColors();
                radioFragment.refresh();
                soundsFragment.refresh();
                settingsFragment.applyTheme(settingsFragment.getView());
            }
            @Override public void onButtonSoundChanged(boolean enabled) {
                if (audioService != null) audioService.setButtonSoundEnabled(enabled);
            }
            @Override public void onAboutClicked() { showAbout(); }
        });

        aboutFragment.setOnBackListener(() -> switchTab(currentTab));

        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentRadio,    radioFragment)
                .add(R.id.fragmentSounds,   soundsFragment)
                .add(R.id.fragmentSettings, settingsFragment)
                .add(R.id.fragmentAbout,    aboutFragment)
                .commit();

        findViewById(R.id.navRadio).setOnClickListener(v    -> switchTab(0));
        findViewById(R.id.navSounds).setOnClickListener(v   -> switchTab(1));
        findViewById(R.id.navSettings).setOnClickListener(v -> switchTab(2));

        switchTab(0);
        applyNavColors();
    }

    private void showAbout() {
        fragmentRadio.setVisibility(View.GONE);
        fragmentSounds.setVisibility(View.GONE);
        fragmentSettings.setVisibility(View.GONE);
        fragmentAbout.setVisibility(View.VISIBLE);
        setNavSelected(-1);
    }

    private void switchTab(int tab) {
        doHaptic();
        currentTab = tab;
        fragmentRadio.setVisibility(tab == 0    ? View.VISIBLE : View.GONE);
        fragmentSounds.setVisibility(tab == 1   ? View.VISIBLE : View.GONE);
        fragmentSettings.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        fragmentAbout.setVisibility(View.GONE);
        setNavSelected(tab);

        if (serviceBound && audioService != null) {
            if (tab == 0) radioFragment.setAudioService(audioService);
            if (tab == 1) soundsFragment.setAudioService(audioService);
            if (prefs.isButtonSoundEnabled()) audioService.playClickSound();
        }
    }

    private void setNavSelected(int tab) {
        int sel   = colors.navSelected();
        int unsel = colors.navUnselected();
        navRadioText.setTextColor(tab == 0    ? sel : unsel);
        navSoundsText.setTextColor(tab == 1   ? sel : unsel);
        navSettingsText.setTextColor(tab == 2 ? sel : unsel);

        float selSize = 13f, unselSize = 11f;
        navRadioText.setTextSize(tab == 0    ? selSize : unselSize);
        navSoundsText.setTextSize(tab == 1   ? selSize : unselSize);
        navSettingsText.setTextSize(tab == 2 ? selSize : unselSize);
    }

    private void applyNavColors() { setNavSelected(currentTab); }

    private void applyWindowColors() {
        int bg = colors.bgPrimary();
        getWindow().getDecorView().setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        View root = findViewById(R.id.rootLayout);
        if (root != null) root.setBackgroundColor(bg);
        View nav = findViewById(R.id.navBar);
        if (nav != null) nav.setBackgroundColor(bg);
    }

    @SuppressWarnings("deprecation")
    private void doHaptic() {
        if (!prefs.isHapticEnabled()) return;
        Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vib == null || !vib.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vib.vibrate(android.os.VibrationEffect.createOneShot(15, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
        else vib.vibrate(15);
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) { unbindService(conn); serviceBound = false; }
        super.onDestroy();
    }
}
