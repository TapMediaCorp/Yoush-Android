package com.tapmedia.yoush.conversation;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.tapmedia.yoush.R;

public enum AttachmentKeyboardButton {

  GALLERY(R.string.AttachmentKeyboard_gallery, R.drawable.ic_photo_album_light),
  FILE(R.string.AttachmentKeyboard_file, R.drawable.ic_file_light),
  GIF(R.string.AttachmentKeyboard_gif, R.drawable.ic_gif_light),
//  CONTACT(R.string.AttachmentKeyboard_contact, R.drawable.ic_contact_circle_outline_32),
  LOCATION(R.string.AttachmentKeyboard_location, R.drawable.ic_location_light);

  private final int titleRes;
  private final int iconRes;

  AttachmentKeyboardButton(@StringRes int titleRes, @DrawableRes int iconRes) {
    this.titleRes = titleRes;
    this.iconRes = iconRes;
  }

  public @StringRes int getTitleRes() {
    return titleRes;
  }

  public @DrawableRes int getIconRes() {
    return iconRes;
  }
}
