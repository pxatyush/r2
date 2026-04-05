package is.dyino.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.HashMap;
import java.util.Map;

import is.dyino.MainActivity;
import is.dyino.R;
import is.dyino.util.AppPrefs;

public class AudioService extends Service {

    private static final String TAG          = "AudioService";
    private static final String CH_ID        = "dyino_ch";
    private static final int    NID          = 1001;
    public  static final String ACTION_STOP  = "is.dyino.STOP_ALL";
    public  static final String ACTION_PAUSE = "is.dyino.PAUSE";

    // ── Max reconnect attempts for radio stream ──
    private static final int MAX_RECONNECT = 5;

    public class LocalBinder extends Binder {
        public AudioService getService() { return AudioService.this; }
    }
    private final IBinder binder = new LocalBinder();

    /* ── Radio listener ── */
    public interface RadioListener {
        void onPlaybackStarted(String name);
        void onPlaybackStopped();
        void onError(String msg);
        void onBuffering();
    }
    private RadioListener radioListener;
    public void setRadioListener(RadioListener l) { this.radioListener = l; }

    /* ── Now-playing state ── */
    private String  currentName       = "";
    private String  currentFaviconUrl = "";
    private String  currentRadioUrl   = "";
    private float   radioVolume       = 0.8f;
    private boolean radioPlaying      = false;
    private boolean radioPaused       = false;   // paused vs fully stopped
    private int     reconnectCount    = 0;

    /* ── Radio ── */
    private MediaPlayer radioPlayer;

    /* ── Sounds ── */
    private final Map<String, MediaPlayer[]> soundPlayers = new HashMap<>();
    private final Map<String, Float>         soundVolumes  = new HashMap<>();
    private boolean allSoundsPaused = false;

    /* ── Click ── */
    private SoundPool clickPool;
    private int       clickId            = -1;
    private boolean   buttonSoundEnabled = true;
    private boolean   fgStarted          = false;

    /* ── WakeLock + WifiLock — keep CPU + stream alive ── */
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock  wifiLock;

    private MediaSessionCompat mediaSession;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setButtonSoundEnabled(boolean v) { buttonSoundEnabled = v; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        initClick();
        initMediaSession();
        acquireWakeLocks();
    }

    @Override public IBinder onBind(Intent i) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int f, int s) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action))  { stopRadio(); stopAllSounds(); }
            if (ACTION_PAUSE.equals(action)) { togglePauseAll(); }
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Check "persistent playing" pref — if OFF, stop on swipe from recents
        AppPrefs prefs = new AppPrefs(this);
        if (!prefs.isPersistentPlayingEnabled()) {
            stopRadio(); stopAllSounds();
            stopSelf();
        }
        // If ON → keep playing (default START_STICKY will restart if killed)
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopRadio(); stopAllSounds();
        if (clickPool != null) clickPool.release();
        if (mediaSession != null) mediaSession.release();
        releaseWakeLocks();
        super.onDestroy();
    }

    /* ── WakeLocks ── */
    private void acquireWakeLocks() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "dyino:AudioWake");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(12 * 60 * 60 * 1000L); // 12h max
        }
        WifiManager wm = (WifiManager) getApplicationContext()
            .getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            wifiLock = wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF, "dyino:WifiWake");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
    }

    private void releaseWakeLocks() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) {}
    }

    /* ── Foreground ── */
    private void ensureFg(String title, String sub) {
        Notification n = buildNotif(title, sub);
        if (!fgStarted) {
            fgStarted = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NID, n);
        } else {
            updateNotif(title, sub);
        }
    }

    /* ── MediaSession ── */
    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "dyino");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay()  { resumeAll(); }
            @Override public void onPause() { pauseAll();  }
            @Override public void onStop()  { stopRadio(); stopAllSounds(); }
        });
        mediaSession.setActive(true);
        updatePlaybackState(false);
    }

    private void updatePlaybackState(boolean playing) {
        if (mediaSession == null) return;
        // Set position near end of a long duration so the wave snake animation shows
        long pos   = playing ? 95_000L : 0L;   // 95 s into a "100 s" track → wave fills ~95%
        long dur   = 100_000L;
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
            .setState(playing ? PlaybackStateCompat.STATE_PLAYING
                              : PlaybackStateCompat.STATE_PAUSED,
                      pos, playing ? 1f : 0f)
            .setActions(PlaybackStateCompat.ACTION_PLAY
                      | PlaybackStateCompat.ACTION_PAUSE
                      | PlaybackStateCompat.ACTION_STOP)
            .build();
        mediaSession.setPlaybackState(state);

        // Set duration metadata so progress bar appears
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                       currentName.isEmpty() ? "dyino" : currentName)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "dyino")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur)
            .build());
    }

    /* ── Click ── */
    private void initClick() {
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        clickPool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(aa).build();
        try {
            android.content.res.AssetFileDescriptor afd = getAssets().openFd("sounds/click.mp3");
            clickId = clickPool.load(afd, 1); afd.close();
        } catch (Exception e) { Log.d(TAG, "No click.mp3"); }
    }

    public void playClickSound() {
        if (buttonSoundEnabled && clickPool != null && clickId != -1)
            clickPool.play(clickId, 0.4f, 0.4f, 1, 0, 1f);
    }

    /* ══════════════════════════════════════════════════════════════
       RADIO — with WakeLock and auto-reconnect on stream drop
       ══════════════════════════════════════════════════════════════ */

    public void playRadio(String name, String url, String faviconUrl) {
        ensureFg("Radio", name);
        stopRadioPlayer();
        currentName       = name;
        currentFaviconUrl = faviconUrl != null ? faviconUrl : "";
        currentRadioUrl   = url;
        radioPaused       = false;
        reconnectCount    = 0;
        if (radioListener != null) radioListener.onBuffering();
        doPlayRadio(url, name);
    }

    private void doPlayRadio(String url, String name) {
        radioPlayer = new MediaPlayer();
        radioPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        radioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        radioPlayer.setVolume(radioVolume, radioVolume);
        try {
            radioPlayer.setDataSource(url);
            radioPlayer.setOnPreparedListener(mp -> {
                mp.start();
                radioPlaying = true;
                radioPaused  = false;
                reconnectCount = 0;
                updateNotif("▶  " + name, "Tap to pause");
                updatePlaybackState(true);
                if (radioListener != null) radioListener.onPlaybackStarted(name);
            });
            radioPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "Radio error what=" + what + " extra=" + extra);
                radioPlaying = false;
                if (!radioPaused && reconnectCount < MAX_RECONNECT) {
                    reconnectCount++;
                    Log.d(TAG, "Auto-reconnect attempt " + reconnectCount);
                    mainHandler.postDelayed(() -> {
                        stopRadioPlayer();
                        doPlayRadio(currentRadioUrl, currentName);
                        if (radioListener != null) radioListener.onBuffering();
                    }, 2000L * reconnectCount);
                } else {
                    if (radioListener != null) radioListener.onError("Stream dropped");
                }
                return true;
            });
            radioPlayer.setOnInfoListener((mp, what, extra) -> {
                // MEDIA_INFO_BUFFERING_START = 701, END = 702
                if (what == 701 && radioListener != null) radioListener.onBuffering();
                if (what == 702 && radioListener != null) radioListener.onPlaybackStarted(currentName);
                return true;
            });
            radioPlayer.setOnCompletionListener(mp -> {
                // For live streams completion = stream ended / kicked off
                if (!radioPaused && reconnectCount < MAX_RECONNECT) {
                    reconnectCount++;
                    mainHandler.postDelayed(() -> {
                        stopRadioPlayer();
                        doPlayRadio(currentRadioUrl, currentName);
                    }, 1500);
                } else {
                    radioPlaying = false;
                    if (radioListener != null) radioListener.onPlaybackStopped();
                }
            });
            radioPlayer.prepareAsync();
        } catch (Exception e) {
            if (radioListener != null) radioListener.onError(e.getMessage());
        }
    }

    public void playRadio(String name, String url) { playRadio(name, url, ""); }

    public void stopRadio() {
        stopRadioPlayer();
        currentName = ""; currentFaviconUrl = ""; currentRadioUrl = "";
        radioPaused = false;
        if (soundPlayers.isEmpty()) stopFgIfIdle();
        else updateNotif("Sounds", buildSoundText());
        updatePlaybackState(false);
        if (radioListener != null) radioListener.onPlaybackStopped();
    }

    private void stopRadioPlayer() {
        if (radioPlayer != null) {
            try { radioPlayer.stop(); } catch (Exception ignored) {}
            radioPlayer.release(); radioPlayer = null;
        }
        radioPlaying = false;
    }

    /* ── Pause / Resume all ── */
    public void pauseAll() {
        if (radioPlayer != null && radioPlayer.isPlaying()) {
            radioPlayer.pause(); radioPlaying = false; radioPaused = true;
        }
        for (MediaPlayer[] pair : soundPlayers.values())
            if (pair[0] != null && pair[0].isPlaying()) pair[0].pause();
        allSoundsPaused = true;
        updateNotif("⏸  Paused", "Tap to resume");
        updatePlaybackState(false);
        // Notification stays — user can swipe to dismiss if they want
    }

    public void resumeAll() {
        radioPaused = false;
        if (radioPlayer != null && !radioPlayer.isPlaying()) {
            radioPlayer.start(); radioPlaying = true;
        }
        for (MediaPlayer[] pair : soundPlayers.values())
            if (pair[0] != null && !pair[0].isPlaying()) pair[0].start();
        allSoundsPaused = false;
        String title = radioPlaying ? "▶  " + currentName : "Sounds";
        updateNotif(title, radioPlaying ? "Tap to pause" : buildSoundText());
        updatePlaybackState(true);
    }

    public void togglePauseAll() {
        if (isAnythingPlaying()) pauseAll(); else resumeAll();
    }

    public boolean isAnythingPlaying() {
        return (radioPlayer != null && radioPlayer.isPlaying())
            || soundPlayers.values().stream()
                   .anyMatch(p -> p[0] != null && p[0].isPlaying());
    }

    public void pauseResumeRadio() { togglePauseAll(); }

    public void setRadioVolume(float vol) {
        radioVolume = vol;
        if (radioPlayer != null) radioPlayer.setVolume(vol, vol);
    }

    public boolean isRadioPlaying()    { return radioPlayer != null && radioPlayer.isPlaying(); }
    public boolean isRadioPaused()     { return radioPaused; }
    public String  getCurrentName()    { return currentName; }
    public String  getCurrentFavicon() { return currentFaviconUrl; }

    /* ══════════════════════════════════════════════════════════════
       SOUNDS — gapless via setNextMediaPlayer
       ══════════════════════════════════════════════════════════════ */

    public void playSound(String fileName, float volume) {
        if (soundPlayers.containsKey(fileName)) {
            MediaPlayer[] pair = soundPlayers.get(fileName);
            if (pair != null) for (MediaPlayer mp : pair) if (mp != null) mp.setVolume(volume, volume);
            soundVolumes.put(fileName, volume);
            return;
        }

        ensureFg("Sounds", buildSoundText());
        soundVolumes.put(fileName, volume);

        MediaPlayer mpA = createPlayer(fileName, volume);
        if (mpA == null) return;
        soundPlayers.put(fileName, new MediaPlayer[]{mpA, null});

        mpA.setOnPreparedListener(mp -> {
            MediaPlayer mpB = createPlayer(fileName, soundVolumes.getOrDefault(fileName, volume));
            if (mpB != null) {
                mpB.setOnPreparedListener(next -> {
                    try {
                        mp.setNextMediaPlayer(next);
                        MediaPlayer[] pair = soundPlayers.get(fileName);
                        if (pair != null) pair[1] = next;
                    } catch (Exception e) { Log.e(TAG, "setNext", e); }
                });
                try { mpB.prepareAsync(); } catch (Exception ignored) {}
            }
            mp.start();
            updateNotif(radioPlaying ? "▶  " + currentName : "Sounds", buildSoundText());
        });
        mpA.setOnCompletionListener(mp -> chainNext(fileName, mp));
        try { mpA.prepareAsync(); } catch (Exception ignored) {}
    }

    private void chainNext(String fn, MediaPlayer finished) {
        MediaPlayer[] pair = soundPlayers.get(fn);
        if (pair == null) return;
        float vol     = soundVolumes.getOrDefault(fn, 0.8f);
        MediaPlayer cur = pair[1];
        if (cur == null) return;
        pair[0] = cur; pair[1] = null;
        try { finished.release(); } catch (Exception ignored) {}
        MediaPlayer next = createPlayer(fn, vol);
        if (next != null) {
            next.setOnPreparedListener(np -> {
                try {
                    cur.setNextMediaPlayer(np);
                    MediaPlayer[] p2 = soundPlayers.get(fn);
                    if (p2 != null) p2[1] = np;
                } catch (Exception e) { Log.e(TAG, "chainNext", e); }
            });
            next.setOnCompletionListener(mp2 -> chainNext(fn, mp2));
            cur.setOnCompletionListener(mp2 -> chainNext(fn, mp2));
            try { next.prepareAsync(); } catch (Exception ignored) {}
        }
    }

    private MediaPlayer createPlayer(String fn, float vol) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            android.content.res.AssetFileDescriptor afd = getAssets().openFd("sounds/" + fn);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setVolume(vol, vol);
            mp.setLooping(false);
            return mp;
        } catch (Exception e) { Log.e(TAG, "createPlayer " + fn, e); return null; }
    }

    public void stopSound(String fn) {
        MediaPlayer[] pair = soundPlayers.remove(fn);
        if (pair != null) for (MediaPlayer mp : pair)
            if (mp != null) { try { mp.stop(); } catch (Exception ignored) {} mp.release(); }
        soundVolumes.remove(fn);
        if (soundPlayers.isEmpty() && !radioPlaying) stopFgIfIdle();
        else updateNotif(radioPlaying ? "▶  " + currentName : "Sounds", buildSoundText());
    }

    public void setSoundVolume(String fn, float vol) {
        soundVolumes.put(fn, vol);
        MediaPlayer[] pair = soundPlayers.get(fn);
        if (pair != null) for (MediaPlayer mp : pair) if (mp != null) mp.setVolume(vol, vol);
    }

    public boolean isSoundPlaying(String fn) {
        MediaPlayer[] p = soundPlayers.get(fn);
        return p != null && p[0] != null && p[0].isPlaying();
    }

    public float getSoundVolume(String fn) {
        Float v = soundVolumes.get(fn); return v != null ? v : 0.8f;
    }

    public Map<String, Float> getAllPlayingSounds() {
        Map<String, Float> r = new HashMap<>();
        for (String fn : soundPlayers.keySet()) r.put(fn, getSoundVolume(fn));
        return r;
    }

    public void stopAllSounds() {
        for (MediaPlayer[] pair : soundPlayers.values())
            for (MediaPlayer mp : pair) if (mp != null) {
                try { mp.stop(); } catch (Exception ignored) {} mp.release();
            }
        soundPlayers.clear(); soundVolumes.clear();
        allSoundsPaused = false;
        if (!radioPlaying) stopFgIfIdle();
        else updateNotif("▶  " + currentName, "Tap to pause");
    }

    private String buildSoundText() {
        int cnt = soundPlayers.size();
        return cnt == 0 ? (radioPlaying ? currentName : "Tap to open")
                        : cnt + " sound" + (cnt > 1 ? "s" : "") + " playing";
    }

    private void stopFgIfIdle() {
        if (fgStarted) { stopForeground(true); fgStarted = false; }
    }

    /* ══════════════════════════════════════════════════════════════
       NOTIFICATION — pause action, live-activity style
       ══════════════════════════════════════════════════════════════ */

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "dyino Playback", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Radio & ambient sounds");
            ch.setSound(null, null);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotif(String title, String text) {
        // Open app
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Stop all
        Intent stop = new Intent(this, AudioService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Pause/resume toggle
        Intent pause = new Intent(this, AudioService.class);
        pause.setAction(ACTION_PAUSE);
        PendingIntent pausePi = PendingIntent.getService(this, 2, pause,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        boolean anythingPlaying = isAnythingPlaying();
        int pauseIcon = anythingPlaying
            ? android.R.drawable.ic_media_pause
            : android.R.drawable.ic_media_play;
        String pauseLabel = anythingPlaying ? "Pause" : "Resume";

        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_app_icon);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_note)
                .setLargeIcon(icon)
                .setContentIntent(openPi)
                .setOngoing(true)       // can't swipe while playing
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // Pause action first, stop action second
                .addAction(pauseIcon, pauseLabel, pausePi)
                .addAction(android.R.drawable.ic_delete, "Stop", stopPi);

        // When paused: allow swipe-to-dismiss
        if (!anythingPlaying) {
            b.setOngoing(false);
        }

        // Media style with progress — position at 95% of a fake 100s track
        // This makes the "snake wave" animation appear in media notifications
        if (mediaSession != null) {
            b.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1));   // show pause + stop in compact
        }

        // Fake progress = 95/100 → wave fills ~95% → snake animation visible
        b.setProgress(100, 95, false);

        return b.build();
    }

    private void updateNotif(String title, String text) {
        if (!fgStarted) return;
        try {
            NotificationManagerCompat.from(this).notify(NID, buildNotif(title, text));
        } catch (Exception ignored) {}
    }
}