package com.tapmedia.yoush.components.identity;

import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.IdentityDatabase;
import com.tapmedia.yoush.database.IdentityDatabase.IdentityRecord;

import java.util.List;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

public class UnverifiedSendDialog extends AlertDialog.Builder implements DialogInterface.OnClickListener {

  private final List<IdentityRecord> untrustedRecords;
  private final ResendListener       resendListener;

  public UnverifiedSendDialog(@NonNull Context context,
                              @NonNull String message,
                              @NonNull List<IdentityRecord> untrustedRecords,
                              @NonNull ResendListener resendListener)
  {
    super(context);
    this.untrustedRecords = untrustedRecords;
    this.resendListener   = resendListener;

    setTitle(R.string.UnverifiedSendDialog_send_message);
    setIconAttribute(R.attr.dialog_alert_icon);
    setMessage(message);
    setPositiveButton(R.string.UnverifiedSendDialog_send, this);
    setNegativeButton(android.R.string.cancel, null);
  }

  @Override
  public void onClick(DialogInterface dialog, int which) {
    final IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(getContext());

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        synchronized (SESSION_LOCK) {
          for (IdentityRecord identityRecord : untrustedRecords) {
            identityDatabase.setVerified(identityRecord.getRecipientId(),
                                         identityRecord.getIdentityKey(),
                                         IdentityDatabase.VerifiedStatus.DEFAULT);
          }
        }

        return null;
      }

      @Override
      protected void onPostExecute(Void result) {
        resendListener.onResendMessage();
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  public interface ResendListener {
    public void onResendMessage();
  }
}
