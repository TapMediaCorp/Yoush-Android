package com.tapmedia.yoush.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.JobManager;
import com.tapmedia.yoush.logging.Log;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.database.SmsDatabase;
import com.tapmedia.yoush.jobs.SmsSentJob;

public class SmsDeliveryListener extends BroadcastReceiver {

  private static final String TAG = SmsDeliveryListener.class.getSimpleName();

  public static final String SENT_SMS_ACTION      = "com.tapmedia.yoush.SendReceiveService.SENT_SMS_ACTION";
  public static final String DELIVERED_SMS_ACTION = "com.tapmedia.yoush.SendReceiveService.DELIVERED_SMS_ACTION";

  @Override
  public void onReceive(Context context, Intent intent) {
    JobManager  jobManager = ApplicationDependencies.getJobManager();
    long        messageId  = intent.getLongExtra("message_id", -1);
    int         runAttempt = intent.getIntExtra("run_attempt", 0);

    switch (intent.getAction()) {
      case SENT_SMS_ACTION:
        int result = getResultCode();

        jobManager.add(new SmsSentJob(messageId, SENT_SMS_ACTION, result, runAttempt));
        break;
      case DELIVERED_SMS_ACTION:
        byte[] pdu = intent.getByteArrayExtra("pdu");

        if (pdu == null) {
          Log.w(TAG, "No PDU in delivery receipt!");
          break;
        }

        SmsMessage message = SmsMessage.createFromPdu(pdu);

        if (message == null) {
          Log.w(TAG, "Delivery receipt failed to parse!");
          break;
        }

        int status = message.getStatus();

        Log.i(TAG, "Original status: " + status);

        // Note: https://developer.android.com/reference/android/telephony/SmsMessage.html#getStatus()
        //       " CDMA: For not interfering with status codes from GSM, the value is shifted to the bits 31-16"
        // Note: https://stackoverflow.com/a/33240109
        if ("3gpp2".equals(intent.getStringExtra("format"))) {
          Log.w(TAG, "Correcting for CDMA delivery receipt...");
          if      (status >> 24 <= 0) status = SmsDatabase.Status.STATUS_COMPLETE;
          else if (status >> 24 == 2) status = SmsDatabase.Status.STATUS_PENDING;
          else if (status >> 24 == 3) status = SmsDatabase.Status.STATUS_FAILED;
        }

        jobManager.add(new SmsSentJob(messageId, DELIVERED_SMS_ACTION, status, runAttempt));
        break;
      default:
        Log.w(TAG, "Unknown action: " + intent.getAction());
    }
  }
}
