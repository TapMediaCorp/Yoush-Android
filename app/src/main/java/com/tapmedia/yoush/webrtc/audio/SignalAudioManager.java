package com.tapmedia.yoush.webrtc.audio;


import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SignalAudioManager {

  @SuppressWarnings("unused")
  private static final String TAG = SignalAudioManager.class.getSimpleName();

  private final Context        context;
  private final IncomingRinger incomingRinger;
  private final OutgoingRinger outgoingRinger;

  public SignalAudioManager(@NonNull Context context) {
    this.context            = context.getApplicationContext();
    this.incomingRinger     = new IncomingRinger(context);
    this.outgoingRinger     = new OutgoingRinger(context);
  }

  public void startIncomingRinger(@Nullable Uri ringtoneUri, boolean vibrate) {
    incomingRinger.start(ringtoneUri, vibrate);
  }

  public void startOutgoingRinger(OutgoingRinger.Type type) {
    outgoingRinger.start(type);
  }

  public void stop() {
    incomingRinger.stop();
    outgoingRinger.stop();
  }
}
