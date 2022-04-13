package com.tapmedia.yoush.events;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tapmedia.yoush.recipients.Recipient;
import org.whispersystems.libsignal.IdentityKey;

public class WebRtcViewModel {

  public enum State {
    // Normal states
    CALL_INCOMING,
    CALL_OUTGOING,
    CALL_CONNECTED,
    CALL_RINGING,
    CALL_BUSY,
    CALL_DISCONNECTED,
    CALL_NEEDS_PERMISSION,

    // Error states
    NETWORK_FAILURE,
    RECIPIENT_UNAVAILABLE,
    NO_SUCH_USER,
    UNTRUSTED_IDENTITY,

    // Multiring Hangup States
    CALL_ACCEPTED_ELSEWHERE,
    CALL_DECLINED_ELSEWHERE,
    CALL_ONGOING_ELSEWHERE
  }


  private final @NonNull  State       state;
  private final @NonNull  Recipient   recipient;

  public WebRtcViewModel(@NonNull  State               state,
                         @NonNull  Recipient           recipient)
  {
    this.state                = state;
    this.recipient            = recipient;
  }

  public @NonNull State getState() {
    return state;
  }

  public @NonNull Recipient getRecipient() {
    return recipient;
  }

  public @NonNull String toString() {
    return "[State: "               + state +
           "]";
  }
}
