package com.tapmedia.yoush.messagedetails;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.tapmedia.yoush.ConfirmIdentityDialog;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.components.AvatarImageView;
import com.tapmedia.yoush.components.FromTextView;
import com.tapmedia.yoush.util.DateUtils;
import com.tapmedia.yoush.util.TextSecurePreferences;

import java.util.Locale;

final class RecipientViewHolder extends RecyclerView.ViewHolder {
  private final AvatarImageView avatar;
  private final FromTextView    fromView;
  private final TextView        timestamp;
  private final TextView        error;
  private final View            conflictButton;
  private final View            unidentifiedDeliveryIcon;

  RecipientViewHolder(View itemView) {
    super(itemView);

    fromView                 = itemView.findViewById(R.id.message_details_recipient_name);
    avatar                   = itemView.findViewById(R.id.message_details_recipient_avatar);
    timestamp                = itemView.findViewById(R.id.message_details_recipient_timestamp);
    error                    = itemView.findViewById(R.id.message_details_recipient_error_description);
    conflictButton           = itemView.findViewById(R.id.message_details_recipient_conflict_button);
    unidentifiedDeliveryIcon = itemView.findViewById(R.id.message_details_recipient_ud_indicator);
  }

  void bind(RecipientDeliveryStatus data) {
    unidentifiedDeliveryIcon.setVisibility(TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(itemView.getContext()) && data.isUnidentified() ? View.VISIBLE : View.GONE);
    fromView.setText(data.getRecipient());
    avatar.setRecipient(data.getRecipient());

    if (data.getKeyMismatchFailure() != null) {
      timestamp.setVisibility(View.GONE);
      error.setVisibility(View.VISIBLE);
      conflictButton.setVisibility(View.VISIBLE);
      error.setText(itemView.getContext().getString(R.string.message_details_recipient__new_safety_number));
      conflictButton.setOnClickListener(unused -> new ConfirmIdentityDialog(itemView.getContext(), data.getMessageRecord(), data.getKeyMismatchFailure()).show());
    } else if ((data.getNetworkFailure() != null && !data.getMessageRecord().isPending()) || (!data.getMessageRecord().getRecipient().isPushGroup() && data.getMessageRecord().isFailed())) {
      timestamp.setVisibility(View.GONE);
      error.setVisibility(View.VISIBLE);
      conflictButton.setVisibility(View.GONE);
      error.setText(itemView.getContext().getString(R.string.message_details_recipient__failed_to_send));
    } else {
      timestamp.setVisibility(View.VISIBLE);
      error.setVisibility(View.GONE);
      conflictButton.setVisibility(View.GONE);

      if (data.getTimestamp() > 0) {
        Locale dateLocale = Locale.getDefault();
        timestamp.setText(DateUtils.getTimeString(itemView.getContext(), dateLocale, data.getTimestamp()));
      } else {
        timestamp.setText("");
      }
    }
  }
}
