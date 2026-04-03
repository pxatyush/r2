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
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.HashMap;
import java.util.Map;

import is.dyino.MainActivity;
import is.dyino.R;

public class AudioService extends Service {

    private static final String TAG         = "AudioService";
    private static final String CH_ID       = "dyino_ch";
    private static final int    NID         = 1001;
    public  static final String ACTION_STOP = "is.dyino.STOP_ALL";

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

    /* ── Now-playing state (exposed for Home screen) ── */
    private String  currentName       = "";
    private String  currentFaviconUrl = "";
    private float   radioVolume       = 0.8f;
    private boolean radioPlaying      = false;

    /* ── Radio ── */
    private MediaPlayer radioPlayer;

    /* ── Sounds: gapless via setNextMediaPlayer ── */
    // value[0] = currently playing, value[1] = pre-prepared next
    private final Map<String, MediaPlayer[]> soundPlayers = new HashMap<>();
    private final Map<String, Float>         soundVolumes  = new HashMap<>();

    /* ── Click ── */
    private SoundPool clickPool;
    private int       clickId             = -1;
    private boolean   buttonSoundEnabled  = true;
    private boolean   fgStarted           = false;

    private MediaSessionCompat mediaSession;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setButtonSoundEnabled(boolean v) { buttonSoundEnabled = v; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        initClick();
        initMediaSession();
    }

    @Override public IBinder onBind(Intent i) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int f, int s) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRadio(); stopAllSounds();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopRadio(); stopAllSounds();
        if (clickPool != null) clickPool.release();
        if (mediaSession != null) mediaSession.release();
        super.onDestroy();
    }

    /* ── Foreground ── */
    private void ensureFg(String title, String sub) {
        if (!fgStarted) {
            fgStarted = true;
            Notification n = buildNotif(title, sub);
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
            @Override public void onPlay()  { if (radioPlayer != null) { radioPlayer.start(); radioPlaying = true; } }
            @Override public void onPause() { if (radioPlayer != null) { radioPlayer.pause(); radioPlaying = false; } }
            @Override public void onStop()  { stopRadio(); stopAllSounds(); }
        });
        mediaSession.setActive(true);
    }

    /* ── Click sound ── */
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

    /* ══════════════════════════════════════════════════════════════════
       RADIO
       ══════════════════════════════════════════════════════════════════ */

    public void playRadio(String name, String url, String faviconUrl) {
        ensureFg("Radio", name);
        stopRadioPlayer();
        currentName       = name;
        currentFaviconUrl = faviconUrl != null ? faviconUrl : "";
        if (radioListener != null) radioListener.onBuffering();
        updateNotif("Radio", "Buffering…  " + name);

        radioPlayer = new MediaPlayer();
        radioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        radioPlayer.setVolume(radioVolume, radioVolume);
        try {
            radioPlayer.setDataSource(url);
            radioPlayer.setOnPreparedListener(mp -> {
                mp.start(); radioPlaying = true;
                updateNotif("Radio  ▶", name);
                updateMediaSession(name);
                if (radioListener != null) radioListener.onPlaybackStarted(name);
            });
            radioPlayer.setOnErrorListener((mp, w, e) -> {
                radioPlaying = false;
                if (radioListener != null) radioListener.onError("Stream error");
                return true;
            });
            radioPlayer.setOnCompletionListener(mp -> {
                radioPlaying = false;
                if (radioListener != null) radioListener.onPlaybackStopped();
            });
            radioPlayer.prepareAsync();
        } catch (Exception e) {
            if (radioListener != null) radioListener.onError(e.getMessage());
        }
    }

    /** Convenience overload without favicon */
    public void playRadio(String name, String url) { playRadio(name, url, ""); }

    public void stopRadio() {
        stopRadioPlayer();
        currentName = "";
        currentFaviconUrl = "";
        if (soundPlayers.isEmpty()) stopFgIfIdle();
        else updateNotif("Sounds", buildSoundText());
        if (radioListener != null) radioListener.onPlaybackStopped();
    }

    private void stopRadioPlayer() {
        if (radioPlayer != null) {
            try { radioPlayer.stop(); } catch (Exception ignored) {}
            radioPlayer.release(); radioPlayer = null;
        }
        radioPlaying = false;
    }

    public void pauseResumeRadio() {
        if (radioPlayer == null) return;
        if (radioPlayer.isPlaying()) {
            radioPlayer.pause(); radioPlaying = false;
            updateNotif("Radio  ⏸", currentName);
        } else {
            radioPlayer.start(); radioPlaying = true;
            updateNotif("Radio  ▶", currentName);
        }
    }

    public void setRadioVolume(float vol) {
        radioVolume = vol;
        if (radioPlayer != null) radioPlayer.setVolume(vol, vol);
    }

    public boolean isRadioPlaying()   { return radioPlayer != null && radioPlayer.isPlaying(); }
    public String  getCurrentName()   { return currentName; }
    public String  getCurrentFavicon(){ return currentFaviconUrl; }

    /* ══════════════════════════════════════════════════════════════════
       SOUNDS — true gapless via setNextMediaPlayer
       ══════════════════════════════════════════════════════════════════ */

    public void playSound(String fileName, float volume) {
        if (soundPlayers.containsKey(fileName)) {
            // Already playing — just update volume
            MediaPlayer[] pair = soundPlayers.get(fileName);
            if (pair != null) for (MediaPlayer mp : pair) if (mp != null) mp.setVolume(volume, volume);
            soundVolumes.put(fileName, volume);
            return;
        }

        ensureFg("Sounds", buildSoundText());
        soundVolumes.put(fileName, volume);

        // Create and start player A
        MediaPlayer mpA = createPlayer(fileName, volume);
        if (mpA == null) return;

        soundPlayers.put(fileName, new MediaPlayer[]{mpA, null});

        mpA.setOnPreparedListener(mp -> {
            // Create player B and chain it for gapless looping
            MediaPlayer mpB = createPlayer(fileName, soundVolumes.getOrDefault(fileName, volume));
            if (mpB != null) {
                mpB.setOnPreparedListener(nextMp -> {
                    try {
                        mp.setNextMediaPlayer(nextMp);
                        MediaPlayer[] pair = soundPlayers.get(fileName);
                        if (pair != null) pair[1] = nextMp;
                    } catch (Exception e) {
                        Log.e(TAG, "setNextMediaPlayer", e);
                    }
                });
                try { mpB.prepareAsync(); } catch (Exception ignored) {}
            }
            mp.start();
            updateNotif(radioPlaying ? "Radio  ▶" : "Sounds", buildSoundText());
        });

        mpA.setOnCompletionListener(mp -> chainNext(fileName, mp));

        try { mpA.prepareAsync(); } catch (Exception ignored) {}
    }

    /**
     * Called when the current player finishes. The next player (B) has already started
     * playing via setNextMediaPlayer. We now prepare a new "C" to follow B gaplessly.
     */
    private void chainNext(String fileName, MediaPlayer finished) {
        MediaPlayer[] pair = soundPlayers.get(fileName);
        if (pair == null) return;

        float vol = soundVolumes.getOrDefault(fileName, 0.8f);

        // pair[1] (B) is now the current; prepare pair[2] (C) = new next
        MediaPlayer current = pair[1];
        if (current == null) return;

        pair[0] = current;
        pair[1] = null;
        try { finished.release(); } catch (Exception ignored) {}

        // Prepare the new next player
        MediaPlayer nextMp = createPlayer(fileName, vol);
        if (nextMp != null) {
            nextMp.setOnPreparedListener(np -> {
                try {
                    current.setNextMediaPlayer(np);
                    MediaPlayer[] p2 = soundPlayers.get(fileName);
                    if (p2 != null) p2[1] = np;
                } catch (Exception e) { Log.e(TAG, "chainNext setNext", e); }
            });
            nextMp.setOnCompletionListener(mp2 -> chainNext(fileName, mp2));
            current.setOnCompletionListener(mp2 -> chainNext(fileName, mp2));
            try { nextMp.prepareAsync(); } catch (Exception ignored) {}
        }
    }

    private MediaPlayer createPlayer(String fileName, float volume) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            android.content.res.AssetFileDescriptor afd = getAssets().openFd("sounds/" + fileName);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setVolume(volume, volume);
            mp.setLooping(false);
            return mp;
        } catch (Exception e) {
            Log.e(TAG, "createPlayer " + fileName, e);
            return null;
        }
    }

    public void stopSound(String fileName) {
        MediaPlayer[] pair = soundPlayers.remove(fileName);
        if (pair != null) {
            for (MediaPlayer mp : pair) {
                if (mp != null) { try { mp.stop(); } catch (Exception ignored) {} mp.release(); }
            }
        }
        soundVolumes.remove(fileName);
        if (soundPlayers.isEmpty() && !radioPlaying) stopFgIfIdle();
        else updateNotif(radioPlaying ? "Radio  ▶" : "Sounds", buildSoundText());
    }

    public void setSoundVolume(String fn, float vol) {
        soundVolumes.put(fn, vol);
        MediaPlayer[] pair = soundPlayers.get(fn);
        if (pair != null) for (MediaPlayer mp : pair) if (mp != null) mp.setVolume(vol, vol);
    }

    public boolean isSoundPlaying(String fn) {
        MediaPlayer[] pair = soundPlayers.get(fn);
        return pair != null && pair[0] != null && pair[0].isPlaying();
    }

    public float getSoundVolume(String fn) {
        Float v = soundVolumes.get(fn); return v != null ? v : 0.8f;
    }

    public Map<String, Float> getAllPlayingSounds() {
        Map<String, Float> result = new HashMap<>();
        for (String fn : soundPlayers.keySet()) {
            result.put(fn, getSoundVolume(fn));
        }
        return result;
    }

    public void stopAllSounds() {
        for (MediaPlayer[] pair : soundPlayers.values())
            for (MediaPlayer mp : pair) if (mp != null) {
                try { mp.stop(); } catch (Exception ignored) {} mp.release();
            }
        soundPlayers.clear(); soundVolumes.clear();
        if (!radioPlaying) stopFgIfIdle();
        else updateNotif("Radio  ▶", currentName);
    }

    private String buildSoundText() {
        int cnt = soundPlayers.size();
        if (cnt == 0) return radioPlaying ? currentName : "Tap to open";
        return cnt + " sound" + (cnt > 1 ? "s" : "") + " playing";
    }

    private void stopFgIfIdle() {
        if (fgStarted) { stopForeground(true); fgStarted = false; }
    }

    /* ══════════════════════════════════════════════════════════════════
       NOTIFICATION
       ══════════════════════════════════════════════════════════════════ */

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
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, AudioService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_app_icon);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_note)
                .setLargeIcon(icon)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_pause, "Stop All", stopPi);

        if (mediaSession != null) {
            b.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0));
        }
        return b.build();
    }

    private void updateNotif(String title, String text) {
        if (!fgStarted) return;
        try {
            NotificationManagerCompat nm = NotificationManagerCompat.from(this);
            nm.notify(NID, buildNotif(title, text));
        } catch (Exception ignored) {}
    }

    private void updateMediaSession(String title) {
        if (mediaSession == null) return;
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "dyino Radio")
                .build());
    }
}