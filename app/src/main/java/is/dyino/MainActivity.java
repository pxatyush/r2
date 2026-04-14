package is.dyino;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import is.dyino.service.AudioService;
import is.dyino.ui.home.HomeFragment;
import is.dyino.ui.radio.RadioFragment;
import is.dyino.ui.settings.SettingsFragment;
import is.dyino.ui.sounds.SoundsFragment;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;
import is.dyino.util.SleepTimerManager;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_NOTIF = 1001;

    // Bottom nav height in dp (includes padding above system nav)
    private static final int BOTTOM_NAV_H_DP = 60;
    private static final int SIDE_NAV_W_DP   = 56;

    private AudioService audioService;
    private boolean      serviceBound = false;

    private TextView    navHomeText, navRadioText, navSoundsText, navSettingsText;
    private FrameLayout fragmentHome, fragmentRadio, fragmentSounds, fragmentSettings;
    private FrameLayout navHome, navRadio, navSounds, navSettings;
    private LinearLayout navBar;
    private FrameLayout  contentArea;

    private HomeFragment     homeFragment;
    private RadioFragment    radioFragment;
    private SoundsFragment   soundsFragment;
    private SettingsFragment settingsFragment;

    private AppPrefs    prefs;
    private ColorConfig colors;
    private int         currentTab = 0;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            audioService = ((AudioService.LocalBinder) b).getService();
            serviceBound  = true;
            homeFragment.setAudioService(audioService);
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

        requestNotifPermission();

        // Start service — START_STICKY keeps it alive; bind for IPC
        Intent svc = new Intent(this, AudioService.class);
        startService(svc);
        bindService(svc, conn, BIND_AUTO_CREATE);

        navBar        = findViewById(R.id.navBar);
        contentArea   = findViewById(R.id.contentArea);
        navHome       = findViewById(R.id.navHome);
        navRadio      = findViewById(R.id.navRadio);
        navSounds     = findViewById(R.id.navSounds);
        navSettings   = findViewById(R.id.navSettings);
        navHomeText     = findViewById(R.id.navHomeText);
        navRadioText    = findViewById(R.id.navRadioText);
        navSoundsText   = findViewById(R.id.navSoundsText);
        navSettingsText = findViewById(R.id.navSettingsText);
        fragmentHome    = findViewById(R.id.fragmentHome);
        fragmentRadio   = findViewById(R.id.fragmentRadio);
        fragmentSounds  = findViewById(R.id.fragmentSounds);
        fragmentSettings= findViewById(R.id.fragmentSettings);

        homeFragment     = new HomeFragment();
        radioFragment    = new RadioFragment();
        soundsFragment   = new SoundsFragment();
        settingsFragment = new SettingsFragment();

        settingsFragment.setListener(new SettingsFragment.OnSettingsChanged() {
            @Override public void onThemeChanged() {
                colors = new ColorConfig(MainActivity.this);
                applyWindowColors();
                applyNavColors();
                homeFragment.refreshTheme();
                radioFragment.refresh();
                soundsFragment.refresh();
                settingsFragment.applyTheme(settingsFragment.getView());
            }
            @Override public void onButtonSoundChanged(boolean en) {
                if (audioService != null) audioService.setButtonSoundEnabled(en);
            }
            @Override public void onNavPositionChanged() {
                applyNavPosition();
            }
        });

        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentHome,     homeFragment)
                .add(R.id.fragmentRadio,    radioFragment)
                .add(R.id.fragmentSounds,   soundsFragment)
                .add(R.id.fragmentSettings, settingsFragment)
                .commit();

        navHome    .setOnClickListener(v -> { doHaptic(); doClick(); switchTab(0); });
        navRadio   .setOnClickListener(v -> { doHaptic(); doClick(); switchTab(1); });
        navSounds  .setOnClickListener(v -> { doHaptic(); doClick(); switchTab(2); });
        navSettings.setOnClickListener(v -> { doHaptic(); doClick(); switchTab(3); });

        applyNavPosition();
        applyNavColors();
        switchTab(0);
    }

    // ── Nav position ──────────────────────────────────────────────

    public void applyNavPosition() {
        if (navBar == null || contentArea == null) return;
        String pos = prefs.getNavPosition();
        float  dp  = getResources().getDisplayMetrics().density;

        boolean isBottom = "bottom".equals(pos);
        boolean isRight  = "right".equals(pos);

        if (isBottom) {
            applyBottomNav(dp);
        } else if (isRight) {
            applyVerticalNav(dp, Gravity.END, /* leftMargin */ 0, /* rightMargin */ (int)(SIDE_NAV_W_DP * dp));
            setNavLabelRotation(90f);
        } else {
            // Default: left
            applyVerticalNav(dp, Gravity.START, /* leftMargin */ (int)(SIDE_NAV_W_DP * dp), /* rightMargin */ 0);
            setNavLabelRotation(-90f);
        }

        navBar.setBackgroundColor(colors.bgNav());
        navBar.requestLayout();
        contentArea.requestLayout();
    }

    /**
     * Bottom nav:
     *  • navBar is horizontal, MATCH_PARENT width, fixed height, gravity=BOTTOM
     *  • Items are ordered HOME | RADIO | SOUNDS | SETTINGS (left → right)
     *  • Each item gets weight=1 so they fill the bar evenly
     *  • Labels are horizontal (no rotation)
     *  • contentArea gets a bottom margin equal to nav height
     */
    private void applyBottomNav(float dp) {
        int navH = (int)(BOTTOM_NAV_H_DP * dp);

        // Resize navBar
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, navH);
        navLp.gravity = Gravity.BOTTOM;
        navBar.setLayoutParams(navLp);

        // Make it horizontal
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(0, 0, 0, 0);

        // Remove weight-spacer child if present (the spacer View added for vertical mode)
        View spacer = navBar.findViewWithTag("nav_spacer");
        if (spacer != null) navBar.removeView(spacer);

        // Re-order children: HOME first, SETTINGS last
        navBar.removeAllViews();

        // Each nav item: weight=1, full bar height
        addNavItemToBar(navHome,     navH, true);
        addNavItemToBar(navRadio,    navH, true);
        addNavItemToBar(navSounds,   navH, true);
        addNavItemToBar(navSettings, navH, true);

        // Labels: no rotation for bottom bar
        setNavLabelRotation(0f);

        // Content fills screen minus nav bar height
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        cLp.setMargins(0, 0, 0, navH);
        contentArea.setLayoutParams(cLp);
    }

    private void addNavItemToBar(FrameLayout item, int h, boolean weighted) {
        if (item.getParent() != null) ((ViewGroup) item.getParent()).removeView(item);
        LinearLayout.LayoutParams lp;
        if (weighted)
            lp = new LinearLayout.LayoutParams(0, h, 1f);
        else
            lp = new LinearLayout.LayoutParams(h, h);
        item.setLayoutParams(lp);
        navBar.addView(item);
    }

    /**
     * Side nav (left or right):
     *  • navBar is vertical, fixed width, MATCH_PARENT height
     *  • Items stack bottom → top (Home at bottom, Settings at top)
     *  • Content gets a side margin equal to nav width
     */
    private void applyVerticalNav(float dp, int gravity, int leftMargin, int rightMargin) {
        int navW = (int)(SIDE_NAV_W_DP * dp);

        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(
                navW, ViewGroup.LayoutParams.MATCH_PARENT);
        navLp.gravity = gravity;
        navBar.setLayoutParams(navLp);

        navBar.setOrientation(LinearLayout.VERTICAL);
        navBar.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        navBar.setPadding(0, 0, 0, (int)(28 * dp));

        // Rebuild children: spacer + Settings + Sounds + Radio + Home (bottom)
        navBar.removeAllViews();

        View spacer = new View(this);
        spacer.setTag("nav_spacer");
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, 0, 1f));
        navBar.addView(spacer);

        addNavItemVertical(navSettings, navW, (int)(64 * dp));
        addNavItemVertical(navSounds,   navW, (int)(64 * dp));
        addNavItemVertical(navRadio,    navW, (int)(64 * dp));
        addNavItemVertical(navHome,     navW, (int)(64 * dp));

        // Content margins
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        cLp.setMargins(leftMargin, 0, rightMargin, 0);
        contentArea.setLayoutParams(cLp);
    }

    private void addNavItemVertical(FrameLayout item, int w, int h) {
        if (item.getParent() != null) ((ViewGroup) item.getParent()).removeView(item);
        item.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        navBar.addView(item);
    }

    private void setNavLabelRotation(float deg) {
        for (TextView tv : new TextView[]{navHomeText, navRadioText, navSoundsText, navSettingsText})
            if (tv != null) tv.setRotation(deg);
    }

    // ── Tab switching ─────────────────────────────────────────────
    private void switchTab(int tab) {
        currentTab = tab;
        fragmentHome.setVisibility(tab == 0     ? View.VISIBLE : View.GONE);
        fragmentRadio.setVisibility(tab == 1    ? View.VISIBLE : View.GONE);
        fragmentSounds.setVisibility(tab == 2   ? View.VISIBLE : View.GONE);
        fragmentSettings.setVisibility(tab == 3 ? View.VISIBLE : View.GONE);
        setNavSelected(tab);

        if (tab == 0 && serviceBound && audioService != null) {
            homeFragment.setAudioService(audioService);
            homeFragment.refresh();
        }
        if (serviceBound && audioService != null) {
            if (tab == 1) radioFragment.setAudioService(audioService);
            if (tab == 2) soundsFragment.setAudioService(audioService);
        }
    }

    private void setNavSelected(int tab) {
        int sel   = colors.navLabelSelected();
        int unsel = colors.navLabelUnselected();
        if (navHomeText     != null) { navHomeText.setTextColor(tab==0?sel:unsel);     navHomeText.setTextSize(tab==0?13f:11f); }
        if (navRadioText    != null) { navRadioText.setTextColor(tab==1?sel:unsel);    navRadioText.setTextSize(tab==1?13f:11f); }
        if (navSoundsText   != null) { navSoundsText.setTextColor(tab==2?sel:unsel);   navSoundsText.setTextSize(tab==2?13f:11f); }
        if (navSettingsText != null) { navSettingsText.setTextColor(tab==3?sel:unsel); navSettingsText.setTextSize(tab==3?13f:11f); }
    }

    private void applyNavColors() {
        setNavSelected(currentTab);
        if (navBar != null) navBar.setBackgroundColor(colors.bgNav());
    }

    private void applyWindowColors() {
        int bg = colors.bgPrimary();
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setBackgroundColor(bg);
        View root = findViewById(R.id.rootLayout);
        if (root != null) root.setBackgroundColor(bg);
    }

    // ── Haptic ────────────────────────────────────────────────────
    private void doHaptic() {
        if (!prefs.isHapticEnabled()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) { vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(50, 255)); return; }
            }
            @SuppressWarnings("deprecation")
            Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vib.vibrate(VibrationEffect.createOneShot(50, 255));
            else vib.vibrate(50);
        } catch (Exception ignored) {}
    }

    private void doClick() {
        if (serviceBound && audioService != null && prefs.isButtonSoundEnabled())
            audioService.playClickSound();
    }

    // ── Permissions ───────────────────────────────────────────────
    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERM_NOTIF);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
    }

    @Override
    protected void onDestroy() {
        SleepTimerManager.get().cancel();
        if (serviceBound) { unbindService(conn); serviceBound = false; }
        super.onDestroy();
    }
}
