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

    private static final String TAG     = "AudioService";
    private static final String CH_ID   = "dyino_ch";
    private static final int    NID     = 1001;
    // Action sent by notification Stop button
    public  static final String ACTION_STOP = "is.dyino.STOP_ALL";

    public class LocalBinder extends Binder {
        public AudioService getService() { return AudioService.this; }
    }
    private final IBinder binder = new LocalBinder();

    public interface RadioListener {
        void onPlaybackStarted(String name);
        void onPlaybackStopped();
        void onError(String msg);
        void onBuffering();
    }
    private RadioListener radioListener;
    public void setRadioListener(RadioListener l) { this.radioListener = l; }

    private MediaPlayer radioPlayer;
    private String      currentName   = "";
    private float       radioVolume   = 0.8f;
    private boolean     radioPlaying  = false;

    private final Map<String, MediaPlayer> soundPlayers = new HashMap<>();
    private final Map<String, Float>       soundVolumes  = new HashMap<>();

    private SoundPool clickPool;
    private int       clickId            = -1;
    private boolean   buttonSoundEnabled = true;
    private boolean   fgStarted          = false;

    public void setButtonSoundEnabled(boolean v) { buttonSoundEnabled = v; }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        initClick();
    }
    @Override public IBinder onBind(Intent i) { return binder; }
    @Override public int onStartCommand(Intent intent, int f, int s) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRadio(); stopAllSounds();
        }
        return START_STICKY;
    }
    @Override public void onDestroy() {
        stopRadio(); stopAllSounds();
        if (clickPool != null) clickPool.release();
        super.onDestroy();
    }

    private void ensureFg(String title, String text) {
        Notification n = buildNotif(title, text);
        if (!fgStarted) {
            fgStarted = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NID, n);
        } else {
            updateNotif(title, text);
        }
    }

    /* ── Click sound ── */
    private void initClick() {
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        clickPool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(aa).build();
        try {
            android.content.res.AssetFileDescriptor afd = getAssets().openFd("sounds/click.mp3");
            clickId = clickPool.load(afd, 1); afd.close();
        } catch (Exception e) { Log.d(TAG, "No click.mp3"); }
    }
    public void playClickSound() {
        if (buttonSoundEnabled && clickPool != null && clickId != -1)
            clickPool.play(clickId, 0.5f, 0.5f, 1, 0, 1f);
    }

    /* ── Radio ── */
    public void playRadio(String name, String url) {
        ensureFg("dyino  ·  " + name, "Buffering…");
        stopRadioPlayer();
        currentName = name;
        if (radioListener != null) radioListener.onBuffering();

        radioPlayer = new MediaPlayer();
        radioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        radioPlayer.setVolume(radioVolume, radioVolume);
        try {
            radioPlayer.setDataSource(url);
            radioPlayer.setOnPreparedListener(mp -> {
                mp.start(); radioPlaying = true;
                ensureFg("dyino  ·  " + name, "Radio  ▶  tap to open");
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

    public void stopRadio() {
        stopRadioPlayer();
        if (soundPlayers.isEmpty()) stopFgIfIdle();
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
            updateNotif("dyino  ·  " + currentName, "Paused");
        } else {
            radioPlayer.start(); radioPlaying = true;
            updateNotif("dyino  ·  " + currentName, "Radio  ▶  tap to open");
        }
    }

    public void setRadioVolume(float vol) {
        radioVolume = vol;
        if (radioPlayer != null) radioPlayer.setVolume(vol, vol);
    }

    public boolean isRadioPlaying() { return radioPlayer != null && radioPlayer.isPlaying(); }
    public String  getCurrentName() { return currentName; }

    /* ── Sounds ── */
    public void playSound(String fileName, float volume) {
        ensureFg("dyino", buildSoundNotifText());
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
            // Seamless looping: prepare then immediately seek to start on completion
            mp.setOnPreparedListener(m -> {
                m.seekTo(0); m.start();
                updateNotif("dyino", buildSoundNotifText());
            });
            mp.prepareAsync();
            soundPlayers.put(fileName, mp);
            soundVolumes.put(fileName, volume);
        } catch (Exception e) { Log.e(TAG, "playSound " + fileName, e); }
    }

    public void stopSound(String fileName) {
        MediaPlayer mp = soundPlayers.remove(fileName);
        if (mp != null) { try { mp.stop(); } catch (Exception ignored) {} mp.release(); }
        soundVolumes.remove(fileName);
        if (soundPlayers.isEmpty() && !radioPlaying) stopFgIfIdle();
        else updateNotif("dyino", buildSoundNotifText());
    }

    public void setSoundVolume(String fn, float vol) {
        soundVolumes.put(fn, vol);
        MediaPlayer mp = soundPlayers.get(fn);
        if (mp != null) mp.setVolume(vol, vol);
    }

    public boolean isSoundPlaying(String fn) {
        MediaPlayer mp = soundPlayers.get(fn);
        return mp != null && mp.isPlaying();
    }

    public float getSoundVolume(String fn) {
        Float v = soundVolumes.get(fn); return v != null ? v : 0.8f;
    }

    public void stopAllSounds() {
        for (MediaPlayer mp : soundPlayers.values()) {
            try { mp.stop(); } catch (Exception ignored) {} mp.release();
        }
        soundPlayers.clear(); soundVolumes.clear();
        if (!radioPlaying) stopFgIfIdle();
        else updateNotif("dyino  ·  " + currentName, "Radio  ▶  tap to open");
    }

    private String buildSoundNotifText() {
        int cnt = soundPlayers.size();
        if (cnt == 0) return radioPlaying ? "Radio  ▶  tap to open" : "Ready";
        return cnt + " ambient sound" + (cnt > 1 ? "s" : "") + " playing  ·  tap to open";
    }

    private void stopFgIfIdle() {
        if (fgStarted) { stopForeground(true); fgStarted = false; }
    }

    /* ── Notification ── */
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "dyino Playback", NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotif(String title, String text) {
        // Open app intent
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Stop action intent
        Intent stop = new Intent(this, AudioService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CH_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_note)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setSilent(true)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
                .build();
    }

    private void updateNotif(String title, String text) {
        if (!fgStarted) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NID, buildNotif(title, text));
    }
}
