package com.tapmedia.yoush.database.model;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tapmedia.yoush.contactshare.Contact;
import com.tapmedia.yoush.database.documents.IdentityKeyMismatch;
import com.tapmedia.yoush.database.documents.NetworkFailure;
import com.tapmedia.yoush.linkpreview.LinkPreview;
import com.tapmedia.yoush.mms.Slide;
import com.tapmedia.yoush.mms.SlideDeck;
import com.tapmedia.yoush.recipients.Recipient;

import java.util.LinkedList;
import java.util.List;

public abstract class MmsMessageRecord extends MessageRecord {

  private final @NonNull  SlideDeck         slideDeck;
  private final @Nullable Quote             quote;
  private final @NonNull  List<Contact>     contacts     = new LinkedList<>();
  private final @NonNull  List<LinkPreview> linkPreviews = new LinkedList<>();

  private final boolean viewOnce;

  MmsMessageRecord(long id, String body, Recipient conversationRecipient,
                   Recipient individualRecipient, int recipientDeviceId, long dateSent,
                   long dateReceived, long dateServer, long threadId, int deliveryStatus, int deliveryReceiptCount,
                   long type, List<IdentityKeyMismatch> mismatches,
                   List<NetworkFailure> networkFailures, int subscriptionId, long expiresIn,
                   long expireStarted, boolean viewOnce,
                   @NonNull SlideDeck slideDeck, int readReceiptCount,
                   @Nullable Quote quote, @NonNull List<Contact> contacts,
                   @NonNull List<LinkPreview> linkPreviews, boolean unidentified,
                   @NonNull List<ReactionRecord> reactions, boolean remoteDelete)
  {
    super(id, body, conversationRecipient, individualRecipient, recipientDeviceId, dateSent, dateReceived, dateServer, threadId, deliveryStatus, deliveryReceiptCount, type, mismatches, networkFailures, subscriptionId, expiresIn, expireStarted, readReceiptCount, unidentified, reactions, remoteDelete);

    this.slideDeck = slideDeck;
    this.quote     = quote;
    this.viewOnce  = viewOnce;

    this.contacts.addAll(contacts);
    this.linkPreviews.addAll(linkPreviews);
  }

  @Override
  public boolean isMms() {
    return true;
  }

  @NonNull
  public SlideDeck getSlideDeck() {
    return slideDeck;
  }

  @Override
  public boolean isMediaPending() {
    for (Slide slide : getSlideDeck().getSlides()) {
      if (slide.isInProgress() || slide.isPendingDownload()) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean isViewOnce() {
    return viewOnce;
  }

  public boolean containsMediaSlide() {
    return slideDeck.containsMediaSlide();
  }

  public @Nullable Quote getQuote() {
    return quote;
  }

  public @NonNull List<Contact> getSharedContacts() {
    return contacts;
  }

  public @NonNull List<LinkPreview> getLinkPreviews() {
    return linkPreviews;
  }
}
