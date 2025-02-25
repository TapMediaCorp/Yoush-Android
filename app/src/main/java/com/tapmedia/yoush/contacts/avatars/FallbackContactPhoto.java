package com.tapmedia.yoush.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;

public interface FallbackContactPhoto {

  public Drawable asDrawable(Context context, int color);
  public Drawable asDrawable(Context context, int color, boolean inverted);
  public Drawable asSmallDrawable(Context context, int color, boolean inverted);
  public Drawable asCallCard(Context context);

}
