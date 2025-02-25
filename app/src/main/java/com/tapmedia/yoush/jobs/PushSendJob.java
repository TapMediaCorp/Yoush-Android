package com.tapmedia.yoush.jobs;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.libsignal.metadata.certificate.SenderCertificate;
import com.tapmedia.yoush.TextSecureExpiredException;
import com.tapmedia.yoush.attachments.Attachment;
import com.tapmedia.yoush.attachments.DatabaseAttachment;
import com.tapmedia.yoush.blurhash.BlurHash;
import com.tapmedia.yoush.contactshare.Contact;
import com.tapmedia.yoush.contactshare.ContactModelMapper;
import com.tapmedia.yoush.crypto.ProfileKeyUtil;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.events.PartProgressEvent;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.JobManager;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.linkpreview.LinkPreview;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.mms.DecryptableStreamUriLoader;
import com.tapmedia.yoush.mms.OutgoingMediaMessage;
import com.tapmedia.yoush.mms.PartAuthority;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.recipients.RecipientUtil;
import com.tapmedia.yoush.util.Base64;
import com.tapmedia.yoush.util.BitmapDecodingException;
import com.tapmedia.yoush.util.BitmapUtil;
import com.tapmedia.yoush.util.Hex;
import com.tapmedia.yoush.util.MediaUtil;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Preview;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public abstract class PushSendJob extends SendJob {

  private static final String TAG                           = PushSendJob.class.getSimpleName();
  private static final long   CERTIFICATE_EXPIRATION_BUFFER = TimeUnit.DAYS.toMillis(1);

  protected PushSendJob(Job.Parameters parameters) {
    super(parameters);
  }

  protected static Job.Parameters constructParameters(@NonNull Recipient recipient, boolean hasMedia) {
    return new Parameters.Builder()
                         .setQueue(recipient.getId().toQueueKey(hasMedia))
                         .addConstraint(NetworkConstraint.KEY)
                         .setLifespan(TimeUnit.DAYS.toMillis(1))
                         .setMaxAttempts(Parameters.UNLIMITED)
                         .build();
  }

  @Override
  protected final void onSend() throws Exception {
    if (TextSecurePreferences.getSignedPreKeyFailureCount(context) > 5) {
      ApplicationDependencies.getJobManager().add(new RotateSignedPreKeyJob());
      throw new TextSecureExpiredException("Too many signed prekey rotation failures");
    }

    onPushSend();
  }

  @Override
  public void onRetry() {
    super.onRetry();
    Log.i(TAG, "onRetry()");

    if (getRunAttempt() > 1) {
      Log.i(TAG, "Scheduling service outage detection job.");
      ApplicationDependencies.getJobManager().add(new ServiceOutageDetectionJob());
    }
  }

  protected Optional<byte[]> getProfileKey(@NonNull Recipient recipient) {
    if (!recipient.resolve().isSystemContact() && !recipient.resolve().isProfileSharing()) {
      return Optional.absent();
    }

    return Optional.of(ProfileKeyUtil.getProfileKey(context));
  }

  protected SignalServiceAddress getPushAddress(@NonNull Recipient recipient) {
    return RecipientUtil.toSignalServiceAddress(context, recipient);
  }

  protected List<SignalServiceAttachment> getAttachmentsFor(List<Attachment> parts) {
    List<SignalServiceAttachment> attachments = new LinkedList<>();

    for (final Attachment attachment : parts) {
      SignalServiceAttachment converted = getAttachmentFor(attachment);
      if (converted != null) {
        attachments.add(converted);
      }
    }

    return attachments;
  }

  protected SignalServiceAttachment getAttachmentFor(Attachment attachment) {
    try {
      if (attachment.getDataUri() == null || attachment.getSize() == 0) throw new IOException("Assertion failed, outgoing attachment has no data!");
      InputStream is = PartAuthority.getAttachmentStream(context, attachment.getDataUri());
      return SignalServiceAttachment.newStreamBuilder()
                                    .withStream(is)
                                    .withContentType(attachment.getContentType())
                                    .withLength(attachment.getSize())
                                    .withFileName(attachment.getFileName())
                                    .withVoiceNote(attachment.isVoiceNote())
                                    .withBorderless(attachment.isBorderless())
                                    .withWidth(attachment.getWidth())
                                    .withHeight(attachment.getHeight())
                                    .withCaption(attachment.getCaption())
                                    .withListener((total, progress) -> EventBus.getDefault().postSticky(new PartProgressEvent(attachment, PartProgressEvent.Type.NETWORK, total, progress)))
                                    .build();
    } catch (IOException ioe) {
      Log.w(TAG, "Couldn't open attachment", ioe);
    }
    return null;
  }

  protected static Set<String> enqueueCompressingAndUploadAttachmentsChains(@NonNull JobManager jobManager, OutgoingMediaMessage message) {
    List<Attachment> attachments = new LinkedList<>();

    attachments.addAll(message.getAttachments());

    attachments.addAll(Stream.of(message.getLinkPreviews())
                             .map(LinkPreview::getThumbnail)
                             .filter(Optional::isPresent)
                             .map(Optional::get)
                             .toList());

    attachments.addAll(Stream.of(message.getSharedContacts())
                             .map(Contact::getAvatar).withoutNulls()
                             .map(Contact.Avatar::getAttachment).withoutNulls()
                             .toList());

    return new HashSet<>(Stream.of(attachments).map(a -> {
                                                 AttachmentUploadJob attachmentUploadJob = new AttachmentUploadJob(((DatabaseAttachment) a).getAttachmentId());

                                                 jobManager.startChain(AttachmentCompressionJob.fromAttachment((DatabaseAttachment) a, false, -1))
                                                           .then(new ResumableUploadSpecJob())
                                                           .then(attachmentUploadJob)
                                                           .enqueue();

                                                 return attachmentUploadJob.getId();
                                               })
                                               .toList());
  }

  protected @NonNull List<SignalServiceAttachment> getAttachmentPointersFor(List<Attachment> attachments) {
    return Stream.of(attachments).map(this::getAttachmentPointerFor).filter(a -> a != null).toList();
  }

  protected @Nullable SignalServiceAttachment getAttachmentPointerFor(Attachment attachment) {
    if (TextUtils.isEmpty(attachment.getLocation())) {
      Log.w(TAG, "empty content id");
      return null;
    }

    if (TextUtils.isEmpty(attachment.getKey())) {
      Log.w(TAG, "empty encrypted key");
      return null;
    }

    try {
      final SignalServiceAttachmentRemoteId remoteId = SignalServiceAttachmentRemoteId.from(attachment.getLocation());
      final byte[]                          key      = Base64.decode(attachment.getKey());

      return new SignalServiceAttachmentPointer(attachment.getCdnNumber(),
                                                remoteId,
                                                attachment.getContentType(),
                                                key,
                                                Optional.of(Util.toIntExact(attachment.getSize())),
                                                Optional.absent(),
                                                attachment.getWidth(),
                                                attachment.getHeight(),
                                                Optional.fromNullable(attachment.getDigest()),
                                                Optional.fromNullable(attachment.getFileName()),
                                                attachment.isVoiceNote(),
                                                attachment.isBorderless(),
                                                Optional.fromNullable(attachment.getCaption()),
                                                Optional.fromNullable(attachment.getBlurHash()).transform(BlurHash::getHash),
                                                attachment.getUploadTimestamp());
    } catch (IOException | ArithmeticException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  protected static void notifyMediaMessageDeliveryFailed(Context context, long messageId) {
    long      threadId  = DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId);
    Recipient recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId);

    if (threadId != -1 && recipient != null) {
      ApplicationDependencies.getMessageNotifier().notifyMessageDeliveryFailed(context, recipient, threadId);
    }
  }

  protected Optional<SignalServiceDataMessage.Quote> getQuoteFor(OutgoingMediaMessage message) {
    if (message.getOutgoingQuote() == null) return Optional.absent();

    long                                                  quoteId             = message.getOutgoingQuote().getId();
    String                                                quoteBody           = message.getOutgoingQuote().getText();
    RecipientId                                           quoteAuthor         = message.getOutgoingQuote().getAuthor();
    List<SignalServiceDataMessage.Quote.QuotedAttachment> quoteAttachments    = new LinkedList<>();
    List<Attachment>                                      filteredAttachments = Stream.of(message.getOutgoingQuote().getAttachments())
                                                                                      .filterNot(a -> MediaUtil.isViewOnceType(a.getContentType()))
                                                                                      .toList();

    for (Attachment attachment : filteredAttachments) {
      BitmapUtil.ScaleResult  thumbnailData = null;
      SignalServiceAttachment thumbnail     = null;
      String                  thumbnailType = MediaUtil.IMAGE_JPEG;

      try {
        if (MediaUtil.isImageType(attachment.getContentType()) && attachment.getDataUri() != null) {
          Bitmap.CompressFormat format = BitmapUtil.getCompressFormatForContentType(attachment.getContentType());

          thumbnailData = BitmapUtil.createScaledBytes(context, new DecryptableStreamUriLoader.DecryptableUri(attachment.getDataUri()), 100, 100, 500 * 1024, format);
          thumbnailType = attachment.getContentType();
        } else if (MediaUtil.isVideoType(attachment.getContentType()) && attachment.getThumbnailUri() != null) {
          thumbnailData = BitmapUtil.createScaledBytes(context, new DecryptableStreamUriLoader.DecryptableUri(attachment.getThumbnailUri()), 100, 100, 500 * 1024);
        }

        if (thumbnailData != null) {
          thumbnail = SignalServiceAttachment.newStreamBuilder()
                                             .withContentType(thumbnailType)
                                             .withWidth(thumbnailData.getWidth())
                                             .withHeight(thumbnailData.getHeight())
                                             .withLength(thumbnailData.getBitmap().length)
                                             .withStream(new ByteArrayInputStream(thumbnailData.getBitmap()))
                                             .build();
        }

        quoteAttachments.add(new SignalServiceDataMessage.Quote.QuotedAttachment(attachment.getContentType(),
                                                                                 attachment.getFileName(),
                                                                                 thumbnail));
      } catch (BitmapDecodingException e) {
        Log.w(TAG, e);
      }
    }

    Recipient            quoteAuthorRecipient = Recipient.resolved(quoteAuthor);
    SignalServiceAddress quoteAddress         = RecipientUtil.toSignalServiceAddress(context, quoteAuthorRecipient);
    return Optional.of(new SignalServiceDataMessage.Quote(quoteId, quoteAddress, quoteBody, quoteAttachments));
  }

  protected Optional<SignalServiceDataMessage.Sticker> getStickerFor(OutgoingMediaMessage message) {
    Attachment stickerAttachment = Stream.of(message.getAttachments()).filter(Attachment::isSticker).findFirst().orElse(null);

    if (stickerAttachment == null) {
      return Optional.absent();
    }

    try {
      byte[]                  packId     = Hex.fromStringCondensed(stickerAttachment.getSticker().getPackId());
      byte[]                  packKey    = Hex.fromStringCondensed(stickerAttachment.getSticker().getPackKey());
      int                     stickerId  = stickerAttachment.getSticker().getStickerId();
      SignalServiceAttachment attachment = getAttachmentPointerFor(stickerAttachment);

      return Optional.of(new SignalServiceDataMessage.Sticker(packId, packKey, stickerId, attachment));
    } catch (IOException e) {
      Log.w(TAG, "Failed to decode sticker id/key", e);
      return Optional.absent();
    }
  }

  List<SharedContact> getSharedContactsFor(OutgoingMediaMessage mediaMessage) {
    List<SharedContact> sharedContacts = new LinkedList<>();

    for (Contact contact : mediaMessage.getSharedContacts()) {
      SharedContact.Builder builder = ContactModelMapper.localToRemoteBuilder(contact);
      SharedContact.Avatar  avatar  = null;

      if (contact.getAvatar() != null && contact.getAvatar().getAttachment() != null) {
        avatar = SharedContact.Avatar.newBuilder().withAttachment(getAttachmentFor(contact.getAvatarAttachment()))
                                                  .withProfileFlag(contact.getAvatar().isProfile())
                                                  .build();
      }

      builder.setAvatar(avatar);
      sharedContacts.add(builder.build());
    }

    return sharedContacts;
  }

  List<Preview> getPreviewsFor(OutgoingMediaMessage mediaMessage) {
    return Stream.of(mediaMessage.getLinkPreviews()).map(lp -> {
      SignalServiceAttachment attachment = lp.getThumbnail().isPresent() ? getAttachmentPointerFor(lp.getThumbnail().get()) : null;
      return new Preview(lp.getUrl(), lp.getTitle(), Optional.fromNullable(attachment));
    }).toList();
  }

  protected void rotateSenderCertificateIfNecessary() throws IOException {
    try {
      byte[] certificateBytes = TextSecurePreferences.getUnidentifiedAccessCertificate(context);

      if (certificateBytes == null) {
        throw new InvalidCertificateException("No certificate was present.");
      }

      SenderCertificate certificate = new SenderCertificate(certificateBytes);

      if (System.currentTimeMillis() > (certificate.getExpiration() - CERTIFICATE_EXPIRATION_BUFFER)) {
        throw new InvalidCertificateException("Certificate is expired, or close to it. Expires on: " + certificate.getExpiration() + ", currently: " + System.currentTimeMillis());
      }

      Log.d(TAG, "Certificate is valid.");
    } catch (InvalidCertificateException e) {
      Log.w(TAG, "Certificate was invalid at send time. Fetching a new one.", e);
      RotateCertificateJob certificateJob = new RotateCertificateJob(context);
      certificateJob.onRun();
    }
  }

  protected SignalServiceSyncMessage buildSelfSendSyncMessage(@NonNull Context context, @NonNull SignalServiceDataMessage message, Optional<UnidentifiedAccessPair> syncAccess) {
    SignalServiceAddress  localAddress = new SignalServiceAddress(TextSecurePreferences.getLocalUuid(context), TextSecurePreferences.getLocalNumber(context));
    SentTranscriptMessage transcript   = new SentTranscriptMessage(Optional.of(localAddress),
                                                                   message.getTimestamp(),
                                                                   message,
                                                                   message.getExpiresInSeconds(),
                                                                   Collections.singletonMap(localAddress, syncAccess.isPresent()),
                                                                   false);
    return SignalServiceSyncMessage.forSentTranscript(transcript);
  }


  protected abstract void onPushSend() throws Exception;
}
