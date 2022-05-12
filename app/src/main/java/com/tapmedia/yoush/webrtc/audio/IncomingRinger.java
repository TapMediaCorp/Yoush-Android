package com.tapmedia.yoush.webrtc.audio;


import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Vibrator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tapmedia.yoush.logging.Log;

import java.io.IOException;

public class IncomingRinger {

  private static final String TAG = IncomingRinger.class.getSimpleName();

  private final Context context;
  private final Vibrator vibrator;

  private MediaPlayer player;

  IncomingRinger(Context context) {
    this.context  = context.getApplicationContext();
    this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
  }

  public void start(@Nullable Uri uri, boolean vibrate) {

    if (player != null) player.release();
    if (uri != null)    player = createPlayer(uri);

    if (player != null) {
      try {
        if (!player.isPlaying()) {
          player.prepare();
          player.start();
          Log.i(TAG, "Playing ringtone now...");
        } else {
          Log.w(TAG, "Ringtone is already playing, declining to restart.");
        }
      } catch (IllegalStateException | IOException e) {
        Log.w(TAG, e);
        player = null;
      }
    } else {
      Log.w(TAG, "Not ringing, mode: " + vibrate);
    }
  }

  public void stop() {
    if (player != null) {
      Log.i(TAG, "Stopping ringer");
      player.release();
      player = null;
    }

    Log.i(TAG, "Cancelling vibrator");
    vibrator.cancel();
  }

  private MediaPlayer createPlayer(@NonNull Uri ringtoneUri) {
    try {
      MediaPlayer mediaPlayer = new MediaPlayer();

      mediaPlayer.setOnErrorListener(new MediaPlayerErrorListener());
      mediaPlayer.setDataSource(context, ringtoneUri);
      mediaPlayer.setLooping(true);

      return mediaPlayer;
    } catch (IOException e) {
      Log.e(TAG, "Failed to create player for incoming call ringer");
      return null;
    }
  }


  private class MediaPlayerErrorListener implements MediaPlayer.OnErrorListener {
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
      Log.w(TAG, "onError(" + mp + ", " + what + ", " + extra);
      player = null;
      return false;
    }
  }

}
