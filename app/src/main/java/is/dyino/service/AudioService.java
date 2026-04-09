package is.dyino.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.HashMap;
import java.util.Map;

import is.dyino.MainActivity;
import is.dyino.R;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;

public class AudioService extends Service {

    private static final String TAG           = "AudioService";
    private static final String CH_ID         = "dyino_ch";
    private static final int    NID           = 1001;
    public  static final String ACTION_STOP   = "is.dyino.STOP_ALL";
    public  static final String ACTION_PAUSE  = "is.dyino.PAUSE";
    public  static final String ACTION_FAV    = "is.dyino.FAV_TOGGLE";
    /** Broadcast sent whenever playback state changes so UI can sync */
    public  static final String BROADCAST_STATE = "is.dyino.STATE_CHANGED";
    private static final int    MAX_RECONNECT  = 5;

    // Wave animation for notification progress bar
    private final Handler waveHandler  = new Handler(Looper.getMainLooper());
    private float         wavePhase    = 0f;
    private long          streamStart  = 0L;
    private boolean       waveRunning  = false;

    public class LocalBinder extends Binder {
        public AudioService getService() { return AudioService.this; }
    }
    private final IBinder binder = new LocalBinder();

    // ── Radio listener ────────────────────────────────────────────
    public interface RadioListener {
        void onPlaybackStarted(String name);
        void onPlaybackStopped();
        void onError(String msg);
        void onBuffering();
    }
    private RadioListener radioListener;
    public void setRadioListener(RadioListener l) { this.radioListener = l; }

    // ── State ─────────────────────────────────────────────────────
    private String  currentName       = "";
    private String  currentFaviconUrl = "";
    private String  currentRadioUrl   = "";
    private float   radioVolume       = 0.8f;
    private boolean radioPlaying      = false;
    private boolean radioPaused       = false;
    private int     reconnectCount    = 0;

    private MediaPlayer radioPlayer;
    private final Map<String, MediaPlayer[]> soundPlayers = new HashMap<>();
    private final Map<String, Float>         soundVolumes  = new HashMap<>();

    private SoundPool clickPool;
    private int       clickId            = -1;
    private boolean   buttonSoundEnabled = true;
    private boolean   fgStarted          = false;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock  wifiLock;
    private MediaSessionCompat    mediaSession;
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
            String a = intent.getAction();
            if (ACTION_STOP.equals(a))  { stopRadio(); stopAllSounds(); }
            if (ACTION_PAUSE.equals(a)) { togglePauseAll(); }
            if (ACTION_FAV.equals(a))   { toggleFavCurrentStation(); }
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        AppPrefs prefs = new AppPrefs(this);
        if (!prefs.isPersistentPlayingEnabled()) {
            stopRadio(); stopAllSounds(); stopSelf();
        } else {
            try {
                Intent restart = new Intent(getApplicationContext(), AudioService.class);
                PendingIntent pi = PendingIntent.getService(getApplicationContext(), 101, restart,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (am != null)
                    am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000L, pi);
            } catch (Exception ignored) {}
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopWaveAnimation();
        stopRadio(); stopAllSounds();
        if (clickPool    != null) clickPool.release();
        if (mediaSession != null) mediaSession.release();
        releaseWakeLocks();
        super.onDestroy();
    }

    // ── Wake locks ───────────────────────────────────────────────
    private void acquireWakeLocks() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dyino:audio");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(12 * 60 * 60 * 1000L);
        }
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "dyino:wifi");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
    }

    private void releaseWakeLocks() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) {}
    }

    // ── Foreground ───────────────────────────────────────────────
    private void ensureFg(String title, String sub) {
        Notification n = buildNotif(title, sub);
        if (!fgStarted) {
            fgStarted = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else
                startForeground(NID, n);
        } else {
            updateNotif(title, sub);
        }
    }

    // ── Wave animation (simulates live-stream waveform in notification) ──
    private void startWaveAnimation() {
        if (waveRunning) return;
        waveRunning = true;
        streamStart = SystemClock.elapsedRealtime();
        scheduleWaveTick();
    }

    private void stopWaveAnimation() {
        waveRunning = false;
        waveHandler.removeCallbacksAndMessages(null);
    }

    private void scheduleWaveTick() {
        if (!waveRunning) return;
        waveHandler.postDelayed(() -> {
            if (!waveRunning) return;
            wavePhase += 0.25f; // advance wave phase
            if (wavePhase > (float)(2 * Math.PI)) wavePhase -= (float)(2 * Math.PI);
            // Update notification with animated waveform bitmap
            if (fgStarted && radioPlaying) {
                String title = "▶  " + currentName;
                updateNotifWithWave(title, "Streaming live");
            }
            scheduleWaveTick();
        }, 200); // 5 fps — enough to look animated without battery drain
    }

    // ── MediaSession ─────────────────────────────────────────────
    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "dyino");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay()  { resumeAll(); }
            @Override public void onPause() { pauseAll(); }
            @Override public void onStop()  { stopRadio(); stopAllSounds(); }
        });
        mediaSession.setActive(true);
        setPlaybackState(false);
    }

    /**
     * For live radio streams we use BUFFERING state so Android does NOT show
     * a timeline/scrubber. We publish a fake ever-increasing position so the
     * "snake" waveform animation in media-style notifications keeps moving.
     */
    private void setPlaybackState(boolean playing) {
        if (mediaSession == null) return;

        long pos = playing
            ? (SystemClock.elapsedRealtime() - streamStart) // real elapsed ms
            : 0L;

        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
            .setState(
                playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                pos,
                playing ? 1f : 0f,
                SystemClock.elapsedRealtime())
            .setActions(PlaybackStateCompat.ACTION_PLAY
                      | PlaybackStateCompat.ACTION_PAUSE
                      | PlaybackStateCompat.ACTION_STOP)
            .build();
        mediaSession.setPlaybackState(state);

        // Metadata: omit DURATION entirely for live streams → no scrubber shown
        MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                       currentName.isEmpty() ? "dyino" : currentName)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "dyino · Live");
        // deliberately no DURATION key → no progress bar in media notification
        mediaSession.setMetadata(meta.build());
    }

    // ── Click sound ──────────────────────────────────────────────
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

    // ── Fav toggle from notification ──────────────────────────────
    private void toggleFavCurrentStation() {
        if (currentName.isEmpty() || currentRadioUrl.isEmpty()) return;
        AppPrefs prefs = new AppPrefs(this);
        String key = AppPrefs.stationKey(currentName, currentRadioUrl, "");
        boolean isFav = prefs.isFavourite(key);
        if (isFav) prefs.removeFavourite(key);
        else       prefs.addFavourite(key);
        // Rebuild notification to flip heart icon
        if (fgStarted) updateNotif("▶  " + currentName, "Streaming live");
        sendBroadcast(new Intent(BROADCAST_STATE));
    }

    public boolean isCurrentStationFavourite() {
        if (currentRadioUrl.isEmpty()) return false;
        return new AppPrefs(this).isFavourite(
                AppPrefs.stationKey(currentName, currentRadioUrl, ""));
    }

    // ══════════════════════════════════════════════════════════════
    // RADIO
    // ══════════════════════════════════════════════════════════════

    public void playRadio(String name, String url, String faviconUrl) {
        currentName       = name;
        currentFaviconUrl = faviconUrl != null ? faviconUrl : "";
        currentRadioUrl   = url;
        radioPaused       = false;
        reconnectCount    = 0;
        streamStart       = SystemClock.elapsedRealtime();

        ensureFg("Buffering…", name);
        stopRadioPlayer();
        stopWaveAnimation();

        if (radioListener != null) radioListener.onBuffering();
        sendBroadcast(new Intent(BROADCAST_STATE));

        doPlayRadio(url, name);
    }

    public void playRadio(String name, String url) { playRadio(name, url, ""); }

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
                radioPlaying = true; radioPaused = false; reconnectCount = 0;
                streamStart  = SystemClock.elapsedRealtime();
                setPlaybackState(true);
                startWaveAnimation();
                updateNotifWithWave("▶  " + name, "Streaming live");
                if (radioListener != null) radioListener.onPlaybackStarted(name);
                sendBroadcast(new Intent(BROADCAST_STATE));
            });
            radioPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "radio error what=" + what + " extra=" + extra);
                radioPlaying = false;
                stopWaveAnimation();
                if (!radioPaused && reconnectCount < MAX_RECONNECT) {
                    reconnectCount++;
                    mainHandler.postDelayed(() -> {
                        stopRadioPlayer();
                        doPlayRadio(currentRadioUrl, currentName);
                        if (radioListener != null) radioListener.onBuffering();
                    }, 2000L * reconnectCount);
                } else {
                    if (radioListener != null) radioListener.onError("Stream dropped");
                }
                sendBroadcast(new Intent(BROADCAST_STATE));
                return true;
            });
            radioPlayer.setOnInfoListener((mp, what, extra) -> {
                if (what == 701 && radioListener != null) radioListener.onBuffering();
                if (what == 702 && radioListener != null) radioListener.onPlaybackStarted(currentName);
                return true;
            });
            radioPlayer.setOnCompletionListener(mp -> {
                stopWaveAnimation();
                if (!radioPaused && reconnectCount < MAX_RECONNECT) {
                    reconnectCount++;
                    mainHandler.postDelayed(() -> {
                        stopRadioPlayer();
                        doPlayRadio(currentRadioUrl, currentName);
                    }, 1500);
                } else {
                    radioPlaying = false;
                    if (radioListener != null) radioListener.onPlaybackStopped();
                    sendBroadcast(new Intent(BROADCAST_STATE));
                }
            });
            radioPlayer.prepareAsync();
        } catch (Exception e) {
            if (radioListener != null) radioListener.onError(e.getMessage());
        }
    }

    public void stopRadio() {
        stopWaveAnimation();
        stopRadioPlayer();
        currentName = ""; currentFaviconUrl = ""; currentRadioUrl = ""; radioPaused = false;
        if (soundPlayers.isEmpty()) stopFgIfIdle();
        else updateNotif("Sounds", buildSoundText());
        setPlaybackState(false);
        if (radioListener != null) radioListener.onPlaybackStopped();
        sendBroadcast(new Intent(BROADCAST_STATE));
    }

    private void stopRadioPlayer() {
        if (radioPlayer != null) {
            try { radioPlayer.stop(); } catch (Exception ignored) {}
            radioPlayer.release(); radioPlayer = null;
        }
        radioPlaying = false;
    }

    public void pauseAll() {
        if (radioPlayer != null && radioPlayer.isPlaying()) {
            radioPlayer.pause(); radioPlaying = false; radioPaused = true;
        }
        for (MediaPlayer[] pair : soundPlayers.values())
            if (pair[0] != null && pair[0].isPlaying()) pair[0].pause();
        stopWaveAnimation();
        updateNotif("⏸  Paused", "Tap Resume to continue");
        setPlaybackState(false);
        updateNotifOngoing(false);
        sendBroadcast(new Intent(BROADCAST_STATE));
    }

    public void resumeAll() {
        radioPaused = false;
        if (radioPlayer != null && !radioPlayer.isPlaying()) {
            radioPlayer.start(); radioPlaying = true;
        }
        for (MediaPlayer[] pair : soundPlayers.values())
            if (pair[0] != null && !pair[0].isPlaying()) pair[0].start();
        setPlaybackState(true);
        if (radioPlaying) startWaveAnimation();
        String title = radioPlaying ? "▶  " + currentName : "Sounds";
        updateNotifWithWave(title, radioPlaying ? "Streaming live" : buildSoundText());
        updateNotifOngoing(true);
        sendBroadcast(new Intent(BROADCAST_STATE));
    }

    public void togglePauseAll() {
        if (isAnythingPlaying()) pauseAll(); else resumeAll();
    }

    public boolean isAnythingPlaying() {
        if (radioPlayer != null && radioPlayer.isPlaying()) return true;
        for (MediaPlayer[] p : soundPlayers.values())
            if (p[0] != null && p[0].isPlaying()) return true;
        return false;
    }

    public void pauseResumeRadio() { togglePauseAll(); }

    public void setRadioVolume(float vol) {
        radioVolume = vol;
        if (radioPlayer != null) radioPlayer.setVolume(vol, vol);
    }

    public boolean isRadioPlaying()  { return radioPlayer != null && radioPlayer.isPlaying(); }
    public boolean isRadioPaused()   { return radioPaused; }
    public boolean isRadioSelected() { return !currentName.isEmpty() && !currentRadioUrl.isEmpty(); }
    public String  getCurrentName()     { return currentName; }
    public String  getCurrentFavicon()  { return currentFaviconUrl; }
    public String  getCurrentRadioUrl() { return currentRadioUrl; }

    // ══════════════════════════════════════════════════════════════
    // SOUNDS — gapless via setNextMediaPlayer
    // ══════════════════════════════════════════════════════════════

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
                    try { mp.setNextMediaPlayer(next);
                          MediaPlayer[] p = soundPlayers.get(fileName);
                          if (p != null) p[1] = next; }
                    catch (Exception e) { Log.e(TAG, "setNext", e); }
                });
                try { mpB.prepareAsync(); } catch (Exception ignored) {}
            }
            mp.start();
            updateNotif(radioPlaying ? "▶  " + currentName : "Sounds", buildSoundText());
            sendBroadcast(new Intent(BROADCAST_STATE));
        });
        mpA.setOnCompletionListener(mp -> chainNext(fileName, mp));
        try { mpA.prepareAsync(); } catch (Exception ignored) {}
    }

    private void chainNext(String fn, MediaPlayer finished) {
        MediaPlayer[] pair = soundPlayers.get(fn);
        if (pair == null) return;
        float vol = soundVolumes.getOrDefault(fn, 0.8f);
        MediaPlayer cur = pair[1]; if (cur == null) return;
        pair[0] = cur; pair[1] = null;
        try { finished.release(); } catch (Exception ignored) {}
        MediaPlayer next = createPlayer(fn, vol);
        if (next != null) {
            next.setOnPreparedListener(np -> {
                try { cur.setNextMediaPlayer(np);
                      MediaPlayer[] p = soundPlayers.get(fn);
                      if (p != null) p[1] = np; }
                catch (Exception e) { Log.e(TAG, "chainNext", e); }
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
            mp.setVolume(vol, vol); mp.setLooping(false);
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
        sendBroadcast(new Intent(BROADCAST_STATE));
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
        if (!radioPlaying) stopFgIfIdle();
        else updateNotifWithWave("▶  " + currentName, "Streaming live");
        sendBroadcast(new Intent(BROADCAST_STATE));
    }

    private String buildSoundText() {
        int cnt = soundPlayers.size();
        return cnt == 0 ? (radioPlaying ? currentName : "Tap to open")
                        : cnt + " sound" + (cnt > 1 ? "s" : "") + " playing";
    }

    private void stopFgIfIdle() {
        stopWaveAnimation();
        if (fgStarted) { stopForeground(true); fgStarted = false; }
    }

    // ══════════════════════════════════════════════════════════════
    // NOTIFICATION  (with wave bitmap + heart button)
    // ══════════════════════════════════════════════════════════════

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
        return buildNotifInternal(title, text, false);
    }

    private void updateNotifWithWave(String title, String text) {
        if (!fgStarted) return;
        try { NotificationManagerCompat.from(this).notify(NID, buildNotifInternal(title, text, true)); }
        catch (Exception ignored) {}
    }

    private Notification buildNotifInternal(String title, String text, boolean withWave) {
        Intent open  = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop  = new Intent(this, AudioService.class); stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent pause = new Intent(this, AudioService.class); pause.setAction(ACTION_PAUSE);
        PendingIntent pausePi = PendingIntent.getService(this, 2, pause,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent fav   = new Intent(this, AudioService.class); fav.setAction(ACTION_FAV);
        PendingIntent favPi = PendingIntent.getService(this, 3, fav,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        boolean playing = isAnythingPlaying();
        boolean isFav   = isCurrentStationFavourite();

        ColorConfig colors  = new ColorConfig(this);
        int iconBgColor = colors.notifIconBg();

        // Large icon: waveform bitmap if playing, else solid squircle
        Bitmap largeIcon = withWave && playing
                ? buildWaveBitmap(iconBgColor)
                : buildSolidIconBitmap(iconBgColor);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_note_vec)
                .setColor(iconBgColor)
                .setColorized(true)
                .setLargeIcon(largeIcon)
                .setContentIntent(openPi)
                .setOngoing(playing)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // Pause/Resume
                .addAction(playing ? android.R.drawable.ic_media_pause
                                   : android.R.drawable.ic_media_play,
                           playing ? "Pause" : "Resume", pausePi);

        // Heart/Fav button (only for radio)
        if (!currentRadioUrl.isEmpty()) {
            b.addAction(isFav
                    ? R.drawable.ic_heart_filled
                    : R.drawable.ic_heart_outline,
                    isFav ? "Unfavourite" : "Favourite", favPi);
        }

        // Stop button
        b.addAction(android.R.drawable.ic_delete, "Stop", stopPi);

        if (mediaSession != null) {
            b.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1));
        }
        return b.build();
    }

    /**
     * Draws an animated waveform on the large-icon bitmap so the notification
     * shows a visually oscillating wave (similar to the screenshot reference).
     */
    private Bitmap buildWaveBitmap(int bgColor) {
        int w = 256, h = 128;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        // Background squircle
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(bgColor);
        canvas.drawRoundRect(new RectF(0, 0, w, h), w * 0.15f, w * 0.15f, bg);

        // Wave path
        Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setColor(0xCCFFFFFF);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(4f);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);

        Path path = new Path();
        int steps = 64;
        float cx = h / 2f;
        for (int i = 0; i <= steps; i++) {
            float x = w * i / (float) steps;
            // Combine two sine waves for organic look, modulate by wavePhase
            float amp = h * 0.28f;
            float y = cx
                + (float)(Math.sin(2 * Math.PI * i / steps * 2 + wavePhase) * amp * 0.6)
                + (float)(Math.sin(2 * Math.PI * i / steps * 5 + wavePhase * 1.7) * amp * 0.25)
                + (float)(Math.sin(2 * Math.PI * i / steps     + wavePhase * 0.5) * amp * 0.15);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        canvas.drawPath(path, wavePaint);

        return bmp;
    }

    private Bitmap buildSolidIconBitmap(int bgColor) {
        int size = 128;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(bgColor);
        canvas.drawRoundRect(new RectF(0, 0, size, size), size * 0.25f, size * 0.25f, paint);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(size * 0.55f, size * 0.2f, size * 0.65f, size * 0.72f, paint);
        canvas.drawCircle(size * 0.46f, size * 0.72f, size * 0.13f, paint);
        canvas.drawRect(size * 0.55f, size * 0.2f, size * 0.78f, size * 0.28f, paint);
        return bmp;
    }

    private void updateNotif(String title, String text) {
        if (!fgStarted) return;
        try { NotificationManagerCompat.from(this).notify(NID, buildNotif(title, text)); }
        catch (Exception ignored) {}
    }

    private void updateNotifOngoing(boolean ongoing) {
        if (radioPlaying || !soundPlayers.isEmpty()) {
            String t = radioPlaying ? "▶  " + currentName : "Sounds";
            if (radioPlaying) updateNotifWithWave(ongoing ? t : "⏸  Paused",
                    ongoing ? "Streaming live" : "Tap Resume to continue");
            else updateNotif(ongoing ? t : "⏸  Paused",
                    ongoing ? buildSoundText() : "Tap Resume to continue");
        }
    }
}
