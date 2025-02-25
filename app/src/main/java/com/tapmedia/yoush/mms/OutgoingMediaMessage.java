package com.tapmedia.yoush.mms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;

import com.tapmedia.yoush.attachments.Attachment;
import com.tapmedia.yoush.contactshare.Contact;
import com.tapmedia.yoush.database.documents.IdentityKeyMismatch;
import com.tapmedia.yoush.database.documents.NetworkFailure;
import com.tapmedia.yoush.linkpreview.LinkPreview;
import com.tapmedia.yoush.recipients.Recipient;

import java.util.LinkedList;
import java.util.List;

public class OutgoingMediaMessage {

  private   final Recipient                 recipient;
  protected final String                    body;
  protected final List<Attachment>          attachments;
  private   final long                      sentTimeMillis;
  private   final int                       distributionType;
  private   final int                       subscriptionId;
  private   final long                      expiresIn;
  private   final boolean                   viewOnce;
  private   final QuoteModel                outgoingQuote;

  private   final List<NetworkFailure>      networkFailures       = new LinkedList<>();
  private   final List<IdentityKeyMismatch> identityKeyMismatches = new LinkedList<>();
  private   final List<Contact>             contacts              = new LinkedList<>();
  private   final List<LinkPreview>         linkPreviews          = new LinkedList<>();

  protected final String                    messsageType;

  public OutgoingMediaMessage(Recipient recipient, String message,
                              List<Attachment> attachments, long sentTimeMillis,
                              int subscriptionId, long expiresIn, boolean viewOnce,
                              int distributionType,
                              @Nullable QuoteModel outgoingQuote,
                              @NonNull List<Contact> contacts,
                              @NonNull List<LinkPreview> linkPreviews,
                              @NonNull List<NetworkFailure> networkFailures,
                              @NonNull List<IdentityKeyMismatch> identityKeyMismatches, String messsageType)
  {
    this.recipient             = recipient;
    this.body                  = message;
    this.sentTimeMillis        = sentTimeMillis;
    this.distributionType      = distributionType;
    this.attachments           = attachments;
    this.subscriptionId        = subscriptionId;
    this.expiresIn             = expiresIn;
    this.viewOnce              = viewOnce;
    this.outgoingQuote         = outgoingQuote;
    this.messsageType          = messsageType;

    this.contacts.addAll(contacts);
    this.linkPreviews.addAll(linkPreviews);
    this.networkFailures.addAll(networkFailures);
    this.identityKeyMismatches.addAll(identityKeyMismatches);
  }

  public OutgoingMediaMessage(Recipient recipient, SlideDeck slideDeck, String message,
                              long sentTimeMillis, int subscriptionId, long expiresIn,
                              boolean viewOnce, int distributionType,
                              @Nullable QuoteModel outgoingQuote,
                              @NonNull List<Contact> contacts,
                              @NonNull List<LinkPreview> linkPreviews, String messsageType)
  {
    this(recipient,
         buildMessage(slideDeck, message),
         slideDeck.asAttachments(),
         sentTimeMillis, subscriptionId,
         expiresIn, viewOnce, distributionType, outgoingQuote,
         contacts, linkPreviews, new LinkedList<>(), new LinkedList<>(), messsageType);
  }

  public OutgoingMediaMessage(OutgoingMediaMessage that) {
    this.recipient           = that.getRecipient();
    this.body                = that.body;
    this.distributionType    = that.distributionType;
    this.attachments         = that.attachments;
    this.sentTimeMillis      = that.sentTimeMillis;
    this.subscriptionId      = that.subscriptionId;
    this.expiresIn           = that.expiresIn;
    this.viewOnce            = that.viewOnce;
    this.outgoingQuote       = that.outgoingQuote;
    this.messsageType        = that.messsageType;

    this.identityKeyMismatches.addAll(that.identityKeyMismatches);
    this.networkFailures.addAll(that.networkFailures);
    this.contacts.addAll(that.contacts);
    this.linkPreviews.addAll(that.linkPreviews);
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public String getBody() {
    return body;
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public boolean isSecure() {
    return false;
  }

  public boolean isGroup() {
    return false;
  }

  public boolean isExpirationUpdate() {
    return false;
  }

  public long getSentTimeMillis() {
    return sentTimeMillis;
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public boolean isViewOnce() {
    return viewOnce;
  }

  public @Nullable QuoteModel getOutgoingQuote() {
    return outgoingQuote;
  }

  public @NonNull List<Contact> getSharedContacts() {
    return contacts;
  }

  public @NonNull List<LinkPreview> getLinkPreviews() {
    return linkPreviews;
  }

  public @NonNull List<NetworkFailure> getNetworkFailures() {
    return networkFailures;
  }

  public @NonNull List<IdentityKeyMismatch> getIdentityKeyMismatches() {
    return identityKeyMismatches;
  }

  private static String buildMessage(SlideDeck slideDeck, String message) {
    if (!TextUtils.isEmpty(message) && !TextUtils.isEmpty(slideDeck.getBody())) {
      return slideDeck.getBody() + "\n\n" + message;
    } else if (!TextUtils.isEmpty(message)) {
      return message;
    } else {
      return slideDeck.getBody();
    }
  }

}
