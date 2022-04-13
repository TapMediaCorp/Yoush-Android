package com.tapmedia.yoush.conversation.job;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.net.Uri;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.contacts.sync.DirectoryHelper;
import com.tapmedia.yoush.conversation.ConversationActivity;
import com.tapmedia.yoush.conversation.ConversationAdapter;
import com.tapmedia.yoush.database.DatabaseContentProviders;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.RecipientDatabase;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.jobs.PushGroupSendJob;
import com.tapmedia.yoush.jobs.PushMediaSendJob;
import com.tapmedia.yoush.mms.GlideApp;
import com.tapmedia.yoush.mms.GlideRequests;
import com.tapmedia.yoush.permissions.Permissions;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.sms.MessageSender;
import com.tapmedia.yoush.util.TextSecurePreferences;

import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;


public class MessageJob {

    /**
     * set on {@link ConversationActivity}onCreate
     */
    public static long threadId;

    /**
     * set on {@link ConversationActivity}onCreate
     */
    public static Recipient recipient;

    public static GlideRequests glideRequests = GlideApp.with(ApplicationContext.getInstance());

    public static void onSmsPermissionGranted(Activity activity, Runnable onGranted) {
        Permissions.with(activity)
                .request(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS)
                .ifNecessary(false)
                .withPermanentDenialDialog(activity.getString(R.string.ConversationActivity_signal_needs_sms_permission_in_order_to_send_an_sms))
                .onAllGranted(onGranted)
                .execute();
    }

    public static int shouldScrollPosition(
            ConversationAdapter adapter,
            LinearLayoutManager layoutManager,
            int position
    ) {
        if (position > adapter.getItemCount() - 4) {
            return adapter.getItemCount() - 1;
        }
        if (position < 3) {
            return 0;
        }
        int currentPosition = layoutManager.findFirstVisibleItemPosition();
        if (currentPosition > position) {
            return position - 3;
        } else {
            return position + 3;
        }
    }

    public static void notifyDataChanged(long threadId) {
        ContentResolver resolver = ApplicationContext.getInstance().getContentResolver();
        Uri threadUri = DatabaseContentProviders.Conversation.getUriForThread(threadId);
        resolver.notifyChange(threadUri, null);
        Uri threadVerboseUri = DatabaseContentProviders.Conversation.getVerboseUriForThread(threadId);
        resolver.notifyChange(threadVerboseUri, null);
    }

    public static void onIo(Runnable runnable) {
        Single
                .fromCallable(() -> {
                    runnable.run();
                    return true;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    public static void onMessageSent(long messageId, Recipient recipient) {
        Job  job = null;
        if (isGroupPushSend(recipient)) {
            Job.Parameters jobParameters = new Job.Parameters.Builder()
                    .setQueue(recipient.getId().toQueueKey(false))
                    .addConstraint(NetworkConstraint.KEY)
                    .setLifespan(TimeUnit.DAYS.toMillis(1))
                    .setMaxAttempts(Job.Parameters.UNLIMITED)
                    .build();
            job = new PushGroupSendJob(
                    jobParameters,
                    messageId,
                    null
            );
        } else if (isPushMediaSend(recipient)) {
            job = new PushMediaSendJob(
                    messageId,
                    recipient
            );
        }
        if (job == null) return;
        ApplicationDependencies.getJobManager()
                .add(job, new HashSet<>(), messageId + "");
        MessageSender.onMessageSent();
    }

    private static boolean isGroupPushSend(Recipient recipient) {
        return recipient.isGroup() && !recipient.isMmsGroup();
    }

    private static boolean isPushMediaSend(Recipient recipient) {
        if (!TextSecurePreferences.isPushRegistered(ApplicationContext.getInstance())) {
            return false;
        }

        if (recipient.isGroup()) {
            return false;
        }

        return isPushDestination(recipient);
    }

    private static boolean isPushDestination(Recipient destination) {
        if (destination.resolve().getRegistered() == RecipientDatabase.RegisteredState.REGISTERED) {
            return true;
        } else if (destination.resolve().getRegistered() == RecipientDatabase.RegisteredState.NOT_REGISTERED) {
            return false;
        } else {
            try {
                RecipientDatabase.RegisteredState state = DirectoryHelper.refreshDirectoryFor(ApplicationContext.getInstance(), destination, false);
                return state == RecipientDatabase.RegisteredState.REGISTERED;
            } catch (IOException e1) {
                return false;
            }
        }
    }

}
