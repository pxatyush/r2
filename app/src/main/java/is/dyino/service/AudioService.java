package is.dyino.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import is.dyino.MainActivity;
import is.dyino.R;

public class AudioService extends Service {
    private static final String TAG = "AudioService";
    private static final String CHANNEL_ID = "dyino_channel";
    private static final int NOTIF_ID = 1;

    public class LocalBinder extends Binder {
        public AudioService getService() { return AudioService.this; }
    }

    private final IBinder binder = new LocalBinder();

    // Radio player
    private MediaPlayer radioPlayer;
    private String currentRadioUrl;
    private String currentRadioName;
    private boolean radioPlaying = false;

    // Ambient sound players: assetPath -> MediaPlayer
    private final Map<String, MediaPlayer> soundPlayers = new HashMap<>();
    private final Map<String, Float> soundVolumes = new HashMap<>();

    // Callbacks
    public interface RadioCallback {
        void onRadioStarted(String name);
        void onRadioStopped();
        void onRadioError(String msg);
    }

    private RadioCallback radioCallback;

    public void setRadioCallback(RadioCallback cb) { this.radioCallback = cb; }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    // ─────────────── Radio ───────────────

    public void playRadio(String url, String name) {
        stopRadio();
        currentRadioUrl = url;
        currentRadioName = name;

        radioPlayer = new MediaPlayer();
        radioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());

        try {
            radioPlayer.setDataSource(url);
            radioPlayer.setOnPreparedListener(mp -> {
                mp.start();
                radioPlaying = true;
                startForeground(NOTIF_ID, buildNotification("▶ " + name));
                if (radioCallback != null) radioCallback.onRadioStarted(name);
            });
            radioPlayer.setOnErrorListener((mp, what, extra) -> {
                radioPlaying = false;
                if (radioCallback != null) radioCallback.onRadioError("Stream error");
                return true;
            });
            radioPlayer.prepareAsync();
        } catch (IOException e) {
            Log.e(TAG, "playRadio error", e);
            if (radioCallback != null) radioCallback.onRadioError(e.getMessage());
        }
    }

    public void stopRadio() {
        if (radioPlayer != null) {
            try { radioPlayer.stop(); } catch (Exception ignored) {}
            radioPlayer.release();
            radioPlayer = null;
        }
        radioPlaying = false;
        currentRadioUrl = null;
        currentRadioName = null;
        if (radioCallback != null) radioCallback.onRadioStopped();
        if (soundPlayers.isEmpty()) stopForeground(true);
        else startForeground(NOTIF_ID, buildNotification("Sounds playing"));
    }

    public boolean isRadioPlaying() { return radioPlaying; }
    public String getCurrentRadioName() { return currentRadioName; }

    // ─────────────── Ambient Sounds ───────────────

    public void playSound(String assetPath) {
        if (soundPlayers.containsKey(assetPath)) return;

        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            mp.setDataSource(getAssets().openFd(assetPath));
            mp.setLooping(true);
            float vol = soundVolumes.getOrDefault(assetPath, 1.0f);
            mp.setVolume(vol, vol);
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.prepareAsync();
            soundPlayers.put(assetPath, mp);

            startForeground(NOTIF_ID, buildNotification(radioPlaying ?
                    "▶ " + currentRadioName : "Sounds playing"));
        } catch (IOException e) {
            Log.e(TAG, "playSound error: " + assetPath, e);
        }
    }

    public void stopSound(String assetPath) {
        MediaPlayer mp = soundPlayers.remove(assetPath);
        if (mp != null) {
            try { mp.stop(); } catch (Exception ignored) {}
            mp.release();
        }
        if (soundPlayers.isEmpty() && !radioPlaying) stopForeground(true);
    }

    public void stopAllSounds() {
        for (MediaPlayer mp : soundPlayers.values()) {
            try { mp.stop(); mp.release(); } catch (Exception ignored) {}
        }
        soundPlayers.clear();
        if (!radioPlaying) stopForeground(true);
    }

    public boolean isSoundPlaying(String assetPath) {
        return soundPlayers.containsKey(assetPath);
    }

    public void setSoundVolume(String assetPath, float volume) {
        soundVolumes.put(assetPath, volume);
        MediaPlayer mp = soundPlayers.get(assetPath);
        if (mp != null) {
            try { mp.setVolume(volume, volume); } catch (Exception ignored) {}
        }
    }

    public float getSoundVolume(String assetPath) {
        return soundVolumes.getOrDefault(assetPath, 1.0f);
    }

    public int getActiveSoundCount() { return soundPlayers.size(); }

    // ─────────────── Notification ───────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Dyino Audio", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Background audio playback");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("dyino")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        stopRadio();
        stopAllSounds();
        super.onDestroy();
    }
}
