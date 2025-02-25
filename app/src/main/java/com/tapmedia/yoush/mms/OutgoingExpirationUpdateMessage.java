package com.tapmedia.yoush.mms;

import com.tapmedia.yoush.attachments.Attachment;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.recipients.Recipient;

import java.util.Collections;
import java.util.LinkedList;

public class OutgoingExpirationUpdateMessage extends OutgoingSecureMediaMessage {

  public OutgoingExpirationUpdateMessage(Recipient recipient, long sentTimeMillis, long expiresIn) {
    super(recipient, "", new LinkedList<Attachment>(), sentTimeMillis,
          ThreadDatabase.DistributionTypes.CONVERSATION, expiresIn, false, null, Collections.emptyList(),
          Collections.emptyList());
  }

  @Override
  public boolean isExpirationUpdate() {
    return true;
  }

}
