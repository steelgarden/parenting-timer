package com.steelgarden.timer;

import android.content.Context;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AlarmSound")
public class AlarmSoundPlugin extends Plugin {

    private MediaPlayer currentPlayer;

    @PluginMethod()
    public void listTones(PluginCall call) {
        JSArray tones = new JSArray();
        Context ctx = getContext();

        // Add alarm tones
        addTones(ctx, RingtoneManager.TYPE_ALARM, "alarm", tones);
        // Add notification tones
        addTones(ctx, RingtoneManager.TYPE_NOTIFICATION, "notification", tones);
        // Add ringtones
        addTones(ctx, RingtoneManager.TYPE_RINGTONE, "ringtone", tones);

        JSObject ret = new JSObject();
        ret.put("tones", tones);
        call.resolve(ret);
    }

    private void addTones(Context ctx, int type, String category, JSArray tones) {
        RingtoneManager rm = new RingtoneManager(ctx);
        rm.setType(type);
        Cursor cursor = rm.getCursor();
        while (cursor.moveToNext()) {
            String title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
            Uri uri = rm.getRingtoneUri(cursor.getPosition());
            JSObject tone = new JSObject();
            tone.put("name", title);
            tone.put("uri", uri.toString());
            tone.put("category", category);
            tones.put(tone);
        }
    }

    @PluginMethod()
    public void play(PluginCall call) {
        String uriStr = call.getString("uri", "");
        Context ctx = getContext();
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

        // Check ringer mode — respect silent/vibrate
        int ringerMode = am.getRingerMode();
        if (ringerMode == AudioManager.RINGER_MODE_SILENT ||
            ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            call.resolve();
            return;
        }

        // Stop any currently playing tone
        stopCurrent();

        try {
            Uri uri;
            if (uriStr.isEmpty()) {
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            } else {
                uri = Uri.parse(uriStr);
            }

            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
            mp.setDataSource(ctx, uri);
            mp.setLooping(false);
            mp.prepare();
            mp.start();

            // Stop after 3 seconds (preview / alert duration)
            mp.setOnCompletionListener(MediaPlayer::release);
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    if (mp.isPlaying()) {
                        mp.stop();
                    }
                    mp.release();
                } catch (Exception e) {
                    try { mp.release(); } catch (Exception ignored) {}
                }
            }).start();

            currentPlayer = mp;
        } catch (Exception e) {
            // ignore
        }

        call.resolve();
    }

    @PluginMethod()
    public void stop(PluginCall call) {
        stopCurrent();
        call.resolve();
    }

    private void stopCurrent() {
        if (currentPlayer != null) {
            try {
                if (currentPlayer.isPlaying()) {
                    currentPlayer.stop();
                }
                currentPlayer.release();
            } catch (Exception ignored) {}
            currentPlayer = null;
        }
    }

    @PluginMethod()
    public void getRingerMode(PluginCall call) {
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = am.getRingerMode();
        String mode;
        switch (ringerMode) {
            case AudioManager.RINGER_MODE_SILENT:
                mode = "silent";
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                mode = "vibrate";
                break;
            default:
                mode = "normal";
                break;
        }
        JSObject ret = new JSObject();
        ret.put("mode", mode);
        call.resolve(ret);
    }
}
