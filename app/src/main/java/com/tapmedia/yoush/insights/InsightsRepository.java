package com.tapmedia.yoush.insights;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.color.MaterialColor;
import com.tapmedia.yoush.contacts.avatars.ContactColors;
import com.tapmedia.yoush.contacts.avatars.GeneratedContactPhoto;
import com.tapmedia.yoush.contacts.avatars.ProfileContactPhoto;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.MmsSmsDatabase;
import com.tapmedia.yoush.database.RecipientDatabase;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.sms.MessageSender;
import com.tapmedia.yoush.sms.OutgoingTextMessage;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.Util;
import com.tapmedia.yoush.util.concurrent.SimpleTask;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;

public class InsightsRepository implements InsightsDashboardViewModel.Repository, InsightsModalViewModel.Repository {

  private final Context context;

  public InsightsRepository(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void getInsightsData(@NonNull Consumer<InsightsData> insightsDataConsumer) {
    SimpleTask.run(() -> {
      MmsSmsDatabase mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(context);
      int            insecure       = mmsSmsDatabase.getInsecureMessageCountForInsights();
      int            secure         = mmsSmsDatabase.getSecureMessageCountForInsights();

      if (insecure + secure == 0) {
        return new InsightsData(false, 0);
      } else {
        return new InsightsData(true, Util.clamp((int) Math.ceil((insecure * 100f) / (insecure + secure)), 0, 100));
      }
    }, insightsDataConsumer::accept);
  }

  @Override
  public void getInsecureRecipients(@NonNull Consumer<List<Recipient>> insecureRecipientsConsumer) {
    SimpleTask.run(() -> {
      RecipientDatabase recipientDatabase      = DatabaseFactory.getRecipientDatabase(context);
      List<RecipientId> unregisteredRecipients = recipientDatabase.getUninvitedRecipientsForInsights();

      return Stream.of(unregisteredRecipients)
                   .map(Recipient::resolved)
                   .toList();
    },
    insecureRecipientsConsumer::accept);
  }

  @Override
  public void getUserAvatar(@NonNull Consumer<InsightsUserAvatar> avatarConsumer) {
    SimpleTask.run(() -> {
      Recipient     self          = Recipient.self().resolve();
      String        name          = Optional.fromNullable(self.getName(context)).or("");
      MaterialColor fallbackColor = self.getColor();

      if (fallbackColor == ContactColors.UNKNOWN_COLOR && !TextUtils.isEmpty(name)) {
        fallbackColor = ContactColors.generateFor(name);
      }

      return new InsightsUserAvatar(new ProfileContactPhoto(self, self.getProfileAvatar()),
                                    fallbackColor,
                                    new GeneratedContactPhoto(name, R.drawable.ic_profile_outline_40_new));
    }, avatarConsumer::accept);
  }

  @Override
  public void sendSmsInvite(@NonNull Recipient recipient, Runnable onSmsMessageSent) {
    SimpleTask.run(() -> {
      Recipient resolved       = recipient.resolve();
      int       subscriptionId = resolved.getDefaultSubscriptionId().or(-1);
      String    message        = context.getString(R.string.InviteActivity_lets_switch_to_signal, context.getString(R.string.install_url));

      MessageSender.send(context, new OutgoingTextMessage(resolved, message, subscriptionId), -1L, true, null);

      RecipientDatabase database = DatabaseFactory.getRecipientDatabase(context);
      database.setHasSentInvite(recipient.getId());

      return null;
    }, v -> onSmsMessageSent.run());
  }
}
