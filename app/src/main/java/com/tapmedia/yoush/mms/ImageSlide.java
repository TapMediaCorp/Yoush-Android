/*
 * Copyright (C) 2011 Whisper Systems
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tapmedia.yoush.mms;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.net.Uri;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.attachments.Attachment;
import com.tapmedia.yoush.blurhash.BlurHash;
import com.tapmedia.yoush.util.MediaUtil;

public class ImageSlide extends Slide {

  private final boolean borderless;

  @SuppressWarnings("unused")
  private static final String TAG = ImageSlide.class.getSimpleName();

  public ImageSlide(@NonNull Context context, @NonNull Attachment attachment) {
    super(context, attachment);
    this.borderless = attachment.isBorderless();
  }

  public ImageSlide(Context context, Uri uri, long size, int width, int height, @Nullable BlurHash blurHash) {
    this(context, uri, MediaUtil.IMAGE_JPEG, size, width, height, false, null, blurHash);
  }

  public ImageSlide(Context context, Uri uri, String contentType, long size, int width, int height, boolean borderless, @Nullable String caption, @Nullable BlurHash blurHash) {
    super(context, constructAttachmentFromUri(context, uri, contentType, size, width, height, true, null, caption, null, blurHash, null, false, borderless, false));
    this.borderless = borderless;
  }

  @Override
  public @DrawableRes int getPlaceholderRes(Theme theme) {
    return 0;
  }

  @Override
  public @Nullable Uri getThumbnailUri() {
    return getUri();
  }

  @Override
  public boolean hasImage() {
    return true;
  }

  @Override
  public boolean hasPlaceholder() {
    return getPlaceholderBlur() != null;
  }

  @Override
  public boolean isBorderless() {
    return borderless;
  }

  @NonNull
  @Override
  public String getContentDescription() {
    return context.getString(R.string.Slide_image);
  }
}
