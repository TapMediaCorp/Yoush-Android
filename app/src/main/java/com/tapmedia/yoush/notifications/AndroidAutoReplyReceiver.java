/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tapmedia.yoush.notifications;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.core.app.RemoteInput;

import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.MessagingDatabase.MarkedMessageInfo;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.mms.OutgoingMediaMessage;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.sms.MessageSender;
import com.tapmedia.yoush.sms.OutgoingTextMessage;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Get the response text from the Android Auto and sends an message as a reply
 */
public class AndroidAutoReplyReceiver extends BroadcastReceiver {

  public static final String TAG             = Log.tag(AndroidAutoReplyReceiver.class);
  public static final String REPLY_ACTION    = "com.tapmedia.yoush.notifications.ANDROID_AUTO_REPLY";
  public static final String RECIPIENT_EXTRA = "car_recipient";
  public static final String VOICE_REPLY_KEY = "car_voice_reply_key";
  public static final String THREAD_ID_EXTRA = "car_reply_thread_id";

  @SuppressLint("StaticFieldLeak")
  @Override
  public void onReceive(final Context context, Intent intent)
  {
    if (!REPLY_ACTION.equals(intent.getAction())) return;

    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);

    if (remoteInput == null) return;

    final long         threadId     = intent.getLongExtra(THREAD_ID_EXTRA, -1);
    final CharSequence responseText = getMessageText(intent);
    final Recipient    recipient    = Recipient.resolved(intent.getParcelableExtra(RECIPIENT_EXTRA));

    if (responseText != null) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {

          long replyThreadId;

          int  subscriptionId = recipient.getDefaultSubscriptionId().or(-1);
          long expiresIn      = recipient.getExpireMessages() * 1000L;

          if (recipient.resolve().isGroup()) {
            Log.w(TAG, "GroupRecipient, Sending media message");
            OutgoingMediaMessage reply = new OutgoingMediaMessage(recipient, responseText.toString(), new LinkedList<>(), System.currentTimeMillis(), subscriptionId, expiresIn, false, 0, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "");
            replyThreadId = MessageSender.send(context, reply, threadId, false, null);
          } else {
            Log.w(TAG, "Sending regular message ");
            OutgoingTextMessage reply = new OutgoingTextMessage(recipient, responseText.toString(), expiresIn, subscriptionId);
            replyThreadId = MessageSender.send(context, reply, threadId, false, null);
          }

          List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(replyThreadId, true);

          ApplicationDependencies.getMessageNotifier().updateNotification(context);
          MarkReadReceiver.process(context, messageIds);

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private CharSequence getMessageText(Intent intent) {
    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
    if (remoteInput != null) {
      return remoteInput.getCharSequence(VOICE_REPLY_KEY);
    }
    return null;
  }

}
