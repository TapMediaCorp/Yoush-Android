package com.tapmedia.yoush.components;

import android.content.Context;
import android.graphics.Typeface;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.components.emoji.EmojiTextView;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.FeatureFlags;
import com.tapmedia.yoush.util.ResUtil;
import com.tapmedia.yoush.util.spans.CenterAlignedRelativeSizeSpan;

public class FromTextView extends EmojiTextView {

  private static final String TAG = FromTextView.class.getSimpleName();

  public FromTextView(Context context) {
    super(context);
  }

  public FromTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setText(Recipient recipient) {
    setText(recipient, true);
  }

  public void setText(Recipient recipient, boolean read) {
    setText(recipient, read, null);
  }

  public void setText(Recipient recipient, boolean read, @Nullable String suffix) {
    String fromString = recipient.getDisplayName(getContext());

    int typeface;

    if (!read) {
      typeface = Typeface.BOLD;
    } else {
      typeface = Typeface.NORMAL;
    }

    SpannableStringBuilder builder = new SpannableStringBuilder();

    SpannableString fromSpan = new SpannableString(fromString);
    fromSpan.setSpan(new StyleSpan(typeface), 0, builder.length(),
                     Spannable.SPAN_INCLUSIVE_EXCLUSIVE);


    if (recipient.isLocalNumber()) {
      builder.append(getContext().getString(R.string.note_to_self));
    } else {
      builder.append(fromSpan);
    }

    if (suffix != null) {
      builder.append(suffix);
    }

    setText(builder);

    if      (recipient.isBlocked()) setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_block_grey600_18dp, 0, 0, 0);
    else if (recipient.isMuted())   setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_volume_off_grey600_18dp, 0, 0, 0);
    else                            setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
  }


}
