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
import android.os.IBinder;
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

    private static final String TAG         = "AudioService";
    private static final String CH_ID       = "dyino_ch";
    private static final int    NID         = 1001;
    public  static final String ACTION_STOP  = "is.dyino.STOP_ALL";
    public  static final String ACTION_PAUSE = "is.dyino.PAUSE";
    public  static final String ACTION_FAV   = "is.dyino.FAV_TOGGLE";
    public  static final String BROADCAST_STATE = "is.dyino.STATE_CHANGED";

    private static final int MAX_RECONNECT = 5;

    // ── Binder ────────────────────────────────────────────────────
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
    public void setRadioListener(RadioListener l) { radioListener = l; }

    // ── State ─────────────────────────────────────────────────────
    private String  currentName       = "";
    private String  currentFaviconUrl = "";
    private String  currentRadioUrl   = "";
    private float   radioVolume       = 0.8f;
    private boolean radioPlaying      = false;
    private boolean radioPaused       = false;
    private int     reconnectCount    = 0;
    private int     radioAudioSession = 0;
    private long    streamStart       = 0L;

    private MediaPlayer radioPlayer;
    private final Map<String, MediaPlayer[]> soundPlayers = new HashMap<>();
    private final Map<String, Float>         soundVolumes  = new HashMap<>();

    private SoundPool clickPool;
    private int       clickId            = -1;
    private boolean   buttonSoundEnabled = true;
    private boolean   fgStarted          = false;

    private PowerManager.WakeLock  wakeLock;
    private WifiManager.WifiLock   wifiLock;
    private MediaSessionCompat     mediaSession;

    public void setButtonSoundEnabled(boolean v) { buttonSoundEnabled = v; }
    public int  getRadioAudioSessionId()          { return radioAudioSession; }

    // ── Lifecycle ─────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        initClickPool();
        initMediaSession();
        acquireWakeLocks();
    }

    @Override public IBinder onBind(Intent i) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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
        if (prefs.isPersistentPlayingEnabled()) {
            scheduleRestart();
        } else {
            stopRadio(); stopAllSounds(); stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopRadio(); stopAllSounds();
        if (clickPool    != null) { clickPool.release(); clickPool = null; }
        if (mediaSession != null) { mediaSession.release(); mediaSession = null; }
        releaseWakeLocks();
        super.onDestroy();
    }

    // ── Wake locks ────────────────────────────────────────────────

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

    private void scheduleRestart() {
        try {
            Intent restart = new Intent(getApplicationContext(), AudioService.class);
            PendingIntent pi = PendingIntent.getService(getApplicationContext(), 101, restart,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null)
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + 1000L, pi);
        } catch (Exception ignored) {}
    }

    // ── Foreground ────────────────────────────────────────────────

    private void ensureFg(String title, String sub) {
        Notification n = buildNotif(title, sub);
        if (!fgStarted) {
            fgStarted = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else
                startForeground(NID, n);
        } else {
            postNotif(title, sub);
        }
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
        pushPlaybackState(false);
    }

    private void pushPlaybackState(boolean playing) {
        if (mediaSession == null) return;
        long pos = playing ? (SystemClock.elapsedRealtime() - streamStart) : 0L;
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setState(playing ? PlaybackStateCompat.STATE_PLAYING
                                  : PlaybackStateCompat.STATE_PAUSED,
                          pos, playing ? 1f : 0f, SystemClock.elapsedRealtime())
                .setActions(PlaybackStateCompat.ACTION_PLAY
                          | PlaybackStateCompat.ACTION_PAUSE
                          | PlaybackStateCompat.ACTION_STOP)
                .build();
        mediaSession.setPlaybackState(state);
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                           currentName.isEmpty() ? "dyino" : currentName)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "dyino")
                .build());
    }

    // ── Click sound ───────────────────────────────────────────────

    private void initClickPool() {
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        clickPool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(aa).build();
        try {
            android.content.res.AssetFileDescriptor afd = getAssets().openFd("sounds/click.mp3");
            clickId = clickPool.load(afd, 1); afd.close();
        } catch (Exception ignored) {}
    }

    public void playClickSound() {
        if (buttonSoundEnabled && clickPool != null && clickId != -1)
            clickPool.play(clickId, 0.4f, 0.4f, 1, 0, 1f);
    }

    // ── Fav from notification ─────────────────────────────────────

    private void toggleFavCurrentStation() {
        if (currentName.isEmpty() || currentRadioUrl.isEmpty()) return;
        AppPrefs prefs = new AppPrefs(this);
        String key = AppPrefs.stationKey(currentName, currentRadioUrl, "");
        if (prefs.isFavourite(key)) prefs.removeFavourite(key);
        else                        prefs.addFavourite(key);
        postNotif(radioPlaying ? "▶  " + currentName : "⏸  " + currentName, "Live radio");
        broadcast();
    }

    public boolean isCurrentStationFavourite() {
        if (currentRadioUrl.isEmpty()) return false;
        return new AppPrefs(this)
                .isFavourite(AppPrefs.stationKey(currentName, currentRadioUrl, ""));
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
        stopRadioInternal();
        if (radioListener != null) radioListener.onBuffering();
        broadcast();
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
                radioPlaying    = true;
                radioPaused     = false;
                reconnectCount  = 0;
                streamStart     = SystemClock.elapsedRealtime();
                radioAudioSession = mp.getAudioSessionId();
                pushPlaybackState(true);
                postNotif("▶  " + name, "Live radio");
                if (radioListener != null) radioListener.onPlaybackStarted(name);
                broadcast();
            });
            radioPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "radio error what=" + what + " extra=" + extra);
                radioPlaying = false;
                if (!radioPaused && reconnectCount < MAX_RECONNECT) {
                    reconnectCount++;
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        stopRadioInternal();
                        doPlayRadio(currentRadioUrl, currentName);
                        if (radioListener != null) radioListener.onBuffering();
                    }, 2000L * reconnectCount);
                } else {
                    if (radioListener != null) radioListener.onError("Stream error");
                }
                broadcast();
                return true;
            });
            radioPlayer.setOnInfoListener((mp, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START && radioListener != null)
                    radioListener.onBuffering();
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END && radioListener != null)
                    radioListener.onPlaybackStarted(currentName);
                return true;
            });
            radioPlayer.setOnCompletionListener(mp -> {
                if (!radioPaused && reconnectCount < MAX_RECONNECT) {
                    reconnectCount++;
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        stopRadioInternal();
                        doPlayRadio(currentRadioUrl, currentName);
                    }, 1500);
                } else {
                    radioPlaying = false;
                    if (radioListener != null) radioListener.onPlaybackStopped();
                    broadcast();
                }
            });
            radioPlayer.prepareAsync();
        } catch (Exception e) {
            if (radioListener != null) radioListener.onError(e.getMessage());
        }
    }

    public void stopRadio() {
        stopRadioInternal();
        currentName = ""; currentFaviconUrl = ""; currentRadioUrl = "";
        radioPaused = false; radioAudioSession = 0;
        if (soundPlayers.isEmpty()) stopFgIfIdle();
        else postNotif("Sounds", buildSoundSubtext());
        pushPlaybackState(false);
        if (radioListener != null) radioListener.onPlaybackStopped();
        broadcast();
    }

    private void stopRadioInternal() {
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
        for (MediaPlayer[] p : soundPlayers.values())
            if (p[0] != null && p[0].isPlaying()) p[0].pause();
        postNotif("⏸  Paused", "Tap to resume");
        pushPlaybackState(false);
        broadcast();
    }

    public void resumeAll() {
        radioPaused = false;
        if (radioPlayer != null && !radioPlayer.isPlaying()) {
            radioPlayer.start(); radioPlaying = true;
        }
        for (MediaPlayer[] p : soundPlayers.values())
            if (p[0] != null && !p[0].isPlaying()) p[0].start();
        pushPlaybackState(true);
        String title = radioPlaying ? "▶  " + currentName : "Sounds";
        postNotif(title, radioPlaying ? "Live radio" : buildSoundSubtext());
        broadcast();
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

    public void setRadioVolume(float vol) {
        radioVolume = vol;
        if (radioPlayer != null) radioPlayer.setVolume(vol, vol);
    }

    public float getRadioVolume()    { return radioVolume; }
    public boolean isRadioPlaying()  { return radioPlayer != null && radioPlayer.isPlaying(); }
    public boolean isRadioPaused()   { return radioPaused; }
    public boolean isRadioSelected() { return !currentName.isEmpty() && !currentRadioUrl.isEmpty(); }
    public String  getCurrentName()     { return currentName; }
    public String  getCurrentFavicon()  { return currentFaviconUrl; }
    public String  getCurrentRadioUrl() { return currentRadioUrl; }

    // ══════════════════════════════════════════════════════════════
    // SOUNDS
    // ══════════════════════════════════════════════════════════════

    public void playSound(String fileName, float volume) {
        if (soundPlayers.containsKey(fileName)) {
            MediaPlayer[] pair = soundPlayers.get(fileName);
            if (pair != null) for (MediaPlayer mp : pair) if (mp != null) mp.setVolume(volume, volume);
            soundVolumes.put(fileName, volume);
            return;
        }
        ensureFg("Sounds", buildSoundSubtext());
        soundVolumes.put(fileName, volume);
        MediaPlayer mpA = buildMediaPlayer(fileName, volume);
        if (mpA == null) return;
        soundPlayers.put(fileName, new MediaPlayer[]{mpA, null});
        mpA.setOnPreparedListener(mp -> {
            MediaPlayer mpB = buildMediaPlayer(fileName, soundVolumes.getOrDefault(fileName, volume));
            if (mpB != null) {
                mpB.setOnPreparedListener(next -> {
                    try { mp.setNextMediaPlayer(next);
                        MediaPlayer[] p = soundPlayers.get(fileName);
                        if (p != null) p[1] = next; }
                    catch (Exception ignored) {}
                });
                try { mpB.prepareAsync(); } catch (Exception ignored) {}
            }
            mp.start();
            postNotif(radioPlaying ? "▶  " + currentName : "Sounds", buildSoundSubtext());
            broadcast();
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
        MediaPlayer next = buildMediaPlayer(fn, vol);
        if (next != null) {
            next.setOnPreparedListener(np -> {
                try { cur.setNextMediaPlayer(np);
                    MediaPlayer[] p = soundPlayers.get(fn); if (p != null) p[1] = np; }
                catch (Exception ignored) {}
            });
            next.setOnCompletionListener(mp2 -> chainNext(fn, mp2));
            cur.setOnCompletionListener(mp2 -> chainNext(fn, mp2));
            try { next.prepareAsync(); } catch (Exception ignored) {}
        }
    }

    private MediaPlayer buildMediaPlayer(String fn, float vol) {
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
        } catch (Exception e) { Log.e(TAG, "buildMediaPlayer: " + fn, e); return null; }
    }

    public void stopSound(String fn) {
        MediaPlayer[] pair = soundPlayers.remove(fn);
        if (pair != null) for (MediaPlayer mp : pair)
            if (mp != null) { try { mp.stop(); } catch (Exception ignored) {} mp.release(); }
        soundVolumes.remove(fn);
        if (soundPlayers.isEmpty() && !radioPlaying) stopFgIfIdle();
        else postNotif(radioPlaying ? "▶  " + currentName : "Sounds", buildSoundSubtext());
        broadcast();
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
            for (MediaPlayer mp : pair)
                if (mp != null) { try { mp.stop(); } catch (Exception ignored) {} mp.release(); }
        soundPlayers.clear(); soundVolumes.clear();
        if (!radioPlaying) stopFgIfIdle();
        else postNotif("▶  " + currentName, "Live radio");
        broadcast();
    }

    private String buildSoundSubtext() {
        int n = soundPlayers.size();
        return n == 0 ? "Tap to open" : n + " sound" + (n > 1 ? "s" : "") + " playing";
    }

    private void stopFgIfIdle() {
        if (fgStarted) { stopForeground(true); fgStarted = false; }
    }

    // ══════════════════════════════════════════════════════════════
    // NOTIFICATION — clean MediaStyle, no animation spam
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
        // Open app on tap
        PendingIntent openPi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Pause / Resume
        Intent pauseI = new Intent(this, AudioService.class).setAction(ACTION_PAUSE);
        PendingIntent pausePi = PendingIntent.getService(this, 2, pauseI,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Stop
        Intent stopI = new Intent(this, AudioService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopI,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Heart / favourite
        Intent favI = new Intent(this, AudioService.class).setAction(ACTION_FAV);
        PendingIntent favPi = PendingIntent.getService(this, 3, favI,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        boolean playing = isAnythingPlaying();
        boolean isFav   = isCurrentStationFavourite();

        ColorConfig colors = new ColorConfig(this);
        AppPrefs    prefs  = new AppPrefs(this);
        int iconBg = colors.notifIconBg();

        // Large icon: static wave style if toggle is on, simple music icon otherwise
        Bitmap largeIcon = prefs.isWaveNotifEnabled()
                ? buildWaveBitmap(iconBg)
                : buildIconBitmap(iconBg);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_note_vec)
                .setColor(iconBg)
                .setColorized(true)
                .setLargeIcon(largeIcon)
                .setContentIntent(openPi)
                .setOngoing(playing)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(
                    playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                    playing ? "Pause" : "Resume",
                    pausePi);

        // Heart button only when a station is loaded
        if (!currentRadioUrl.isEmpty()) {
            b.addAction(
                isFav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline,
                isFav ? "Unfavourite" : "Favourite",
                favPi);
        }

        b.addAction(android.R.drawable.ic_delete, "Stop", stopPi);

        if (mediaSession != null) {
            b.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1));
        }

        return b.build();
    }

    /** Static wave illustration — drawn once, no animation. */
    private Bitmap buildWaveBitmap(int bgColor) {
        final int W = 256, H = 128;
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG); bg.setColor(bgColor);
        c.drawRoundRect(new RectF(0, 0, W, H), W * 0.15f, H * 0.15f, bg);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(0xDDFFFFFF); line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(5f); line.setStrokeCap(Paint.Cap.ROUND);
        float cy = H / 2f, amp = H * 0.30f;
        Path p = new Path();
        for (int i = 0; i <= 80; i++) {
            float t = (float) i / 80, x = W * t;
            float y = cy
                + (float)(Math.sin(t * 2 * Math.PI * 2.5) * amp * 0.65)
                + (float)(Math.sin(t * 2 * Math.PI * 5  ) * amp * 0.20)
                + (float)(Math.sin(t * 2 * Math.PI      ) * amp * 0.15);
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        c.drawPath(p, line);
        return bmp;
    }

    private Bitmap buildIconBitmap(int bgColor) {
        final int S = 128;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(bgColor);
        c.drawRoundRect(new RectF(0, 0, S, S), S * 0.25f, S * 0.25f, p);
        p.setColor(Color.WHITE);
        c.drawRect(S*0.55f, S*0.20f, S*0.65f, S*0.72f, p);
        c.drawCircle(S*0.46f, S*0.72f, S*0.13f, p);
        c.drawRect(S*0.55f, S*0.20f, S*0.78f, S*0.28f, p);
        return bmp;
    }

    private void postNotif(String title, String text) {
        if (!fgStarted) return;
        try { NotificationManagerCompat.from(this).notify(NID, buildNotif(title, text)); }
        catch (Exception ignored) {}
    }

    private void broadcast() {
        sendBroadcast(new Intent(BROADCAST_STATE));
    }
}