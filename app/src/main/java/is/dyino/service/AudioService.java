package is.dyino.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Map;

import is.dyino.MainActivity;
import is.dyino.R;

public class AudioService extends Service {

    private static final String TAG      = "AudioService";
    private static final String CH_ID    = "dyino_ch";
    private static final int    NOTIF_ID = 1001;

    /* ── Binder ── */
    public class LocalBinder extends Binder {
        public AudioService getService() { return AudioService.this; }
    }
    private final IBinder binder = new LocalBinder();

    /* ── Radio listener interface ── */
    public interface RadioListener {
        void onPlaybackStarted(String stationName);
        void onPlaybackStopped();
        void onError(String msg);
        void onBuffering();
    }
    private RadioListener radioListener;
    public void setRadioListener(RadioListener l) { this.radioListener = l; }

    /* ── Radio state ── */
    private MediaPlayer radioPlayer;
    private String currentStationName = "";
    private boolean radioPlaying      = false;

    /* ── Ambient sounds ── */
    private final Map<String, MediaPlayer> soundPlayers = new HashMap<>();
    private final Map<String, Float>       soundVolumes  = new HashMap<>();

    /* ── Button click sound ── */
    private SoundPool clickPool;
    private int       clickId            = -1;
    private boolean   buttonSoundEnabled = true;
    private boolean   foregroundStarted  = false;

    public void setButtonSoundEnabled(boolean v) { buttonSoundEnabled = v; }

    /* ════════════════════════════════════════════════
       Lifecycle
       ════════════════════════════════════════════════ */

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        initClickPool();
        // Don't call startForeground here - call it lazily when audio actually starts
        // to avoid ForegroundServiceStartNotAllowedException on API 31+
    }

    @Override public IBinder onBind(Intent i)                       { return binder;       }
    @Override public int    onStartCommand(Intent i, int f, int s)  { return START_STICKY; }

    @Override
    public void onDestroy() {
        stopRadio();
        stopAllSounds();
        if (clickPool != null) clickPool.release();
        super.onDestroy();
    }

    /** Called before any audio starts to promote service to foreground. */
    private void ensureForeground() {
        if (foregroundStarted) return;
        foregroundStarted = true;
        Notification n = buildNotification("dyino", "Playing");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    /* ════════════════════════════════════════════════
       Click sound
       ════════════════════════════════════════════════ */

    private void initClickPool() {
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        clickPool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(aa).build();
        try {
            android.content.res.AssetFileDescriptor afd = getAssets().openFd("sounds/click.mp3");
            clickId = clickPool.load(afd, 1);
            afd.close();
        } catch (Exception e) { Log.d(TAG, "No click.mp3 asset"); }
    }

    public void playClickSound() {
        if (buttonSoundEnabled && clickPool != null && clickId != -1)
            clickPool.play(clickId, 0.5f, 0.5f, 1, 0, 1f);
    }

    /* ════════════════════════════════════════════════
       Radio
       ════════════════════════════════════════════════ */

    public void playRadio(String name, String url) {
        ensureForeground();
        stopRadioPlayer();
        currentStationName = name;
        if (radioListener != null) radioListener.onBuffering();

        radioPlayer = new MediaPlayer();
        radioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        try {
            radioPlayer.setDataSource(url);
            radioPlayer.setOnPreparedListener(mp -> {
                mp.start();
                radioPlaying = true;
                updateNotification(name, "Playing");
                if (radioListener != null) radioListener.onPlaybackStarted(name);
            });
            radioPlayer.setOnErrorListener((mp, w, e) -> {
                radioPlaying = false;
                if (radioListener != null) radioListener.onError("Stream error " + w);
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

    public void stopRadio() {
        stopRadioPlayer();
        updateNotification("dyino", "Ready");
        if (radioListener != null) radioListener.onPlaybackStopped();
    }

    private void stopRadioPlayer() {
        if (radioPlayer != null) {
            try { radioPlayer.stop(); } catch (Exception ignored) {}
            radioPlayer.release();
            radioPlayer = null;
        }
        radioPlaying = false;
    }

    public void pauseResumeRadio() {
        if (radioPlayer == null) return;
        if (radioPlayer.isPlaying()) {
            radioPlayer.pause();
            radioPlaying = false;
            updateNotification(currentStationName, "Paused");
        } else {
            radioPlayer.start();
            radioPlaying = true;
            updateNotification(currentStationName, "Playing");
        }
    }

    public boolean isRadioPlaying()        { return radioPlayer != null && radioPlayer.isPlaying(); }
    public String  getCurrentStationName() { return currentStationName; }

    /* ════════════════════════════════════════════════
       Ambient sounds
       ════════════════════════════════════════════════ */

    public void playSound(String fileName, float volume) {
        ensureForeground();
        if (soundPlayers.containsKey(fileName)) {
            MediaPlayer mp = soundPlayers.get(fileName);
            if (mp != null) mp.setVolume(volume, volume);
            soundVolumes.put(fileName, volume);
            return;
        }
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            android.content.res.AssetFileDescriptor afd = getAssets().openFd("sounds/" + fileName);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setLooping(true);
            mp.setVolume(volume, volume);
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.prepareAsync();
            soundPlayers.put(fileName, mp);
            soundVolumes.put(fileName, volume);
        } catch (Exception e) { Log.e(TAG, "playSound: " + fileName, e); }
    }

    public void stopSound(String fileName) {
        MediaPlayer mp = soundPlayers.remove(fileName);
        if (mp != null) { try { mp.stop(); } catch (Exception ignored) {} mp.release(); }
        soundVolumes.remove(fileName);
    }

    public void setSoundVolume(String fileName, float volume) {
        soundVolumes.put(fileName, volume);
        MediaPlayer mp = soundPlayers.get(fileName);
        if (mp != null) mp.setVolume(volume, volume);
    }

    public boolean isSoundPlaying(String fileName) {
        MediaPlayer mp = soundPlayers.get(fileName);
        return mp != null && mp.isPlaying();
    }

    public float getSoundVolume(String fileName) {
        Float v = soundVolumes.get(fileName);
        return v != null ? v : 0.8f;
    }

    public void stopAllSounds() {
        for (MediaPlayer mp : soundPlayers.values()) {
            try { mp.stop(); } catch (Exception ignored) {}
            mp.release();
        }
        soundPlayers.clear();
        soundVolumes.clear();
    }

    /* ════════════════════════════════════════════════
       Notification
       ════════════════════════════════════════════════ */

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "dyino Playback", NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String title, String text) {
        Intent i   = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CH_ID)
                .setContentTitle(title).setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_fg)
                .setContentIntent(pi).setOngoing(true).setSilent(true).build();
    }

    private void updateNotification(String title, String text) {
        if (!foregroundStarted) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(title, text));
    }
}
