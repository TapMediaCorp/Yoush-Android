/**
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
package com.tapmedia.yoush.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.logging.Log;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.jobs.MmsReceiveJob;
import com.tapmedia.yoush.util.Util;

public class MmsListener extends BroadcastReceiver {

  private static final String TAG = MmsListener.class.getSimpleName();

  private boolean isRelevant(Context context, Intent intent) {
    if (!ApplicationMigrationService.isDatabaseImported(context)) {
      return false;
    }

    if (Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION.equals(intent.getAction()) && Util.isDefaultSmsProvider(context)) {
      return false;
    }

    return false;
  }

  @Override
    public void onReceive(Context context, Intent intent) {
    Log.i(TAG, "Got MMS broadcast..." + intent.getAction());

    if ((Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(intent.getAction())  &&
        Util.isDefaultSmsProvider(context))                                        ||
        (Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION.equals(intent.getAction()) &&
         isRelevant(context, intent)))
    {
      Log.i(TAG, "Relevant!");
      int subscriptionId = intent.getExtras().getInt("subscription", -1);

      ApplicationDependencies.getJobManager().add(new MmsReceiveJob(intent.getByteArrayExtra("data"), subscriptionId));

      abortBroadcast();
    }
  }



}
