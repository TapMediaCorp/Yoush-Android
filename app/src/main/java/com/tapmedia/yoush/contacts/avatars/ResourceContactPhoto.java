package com.tapmedia.yoush.contacts.avatars;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.appcompat.content.res.AppCompatResources;

import com.amulyakhare.textdrawable.TextDrawable;
import com.makeramen.roundedimageview.RoundedDrawable;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.util.ThemeUtil;

public class ResourceContactPhoto implements FallbackContactPhoto {

  private final int resourceId;
  private final int smallResourceId;
  private final int callCardResourceId;

  public ResourceContactPhoto(@DrawableRes int resourceId) {
    this(resourceId, resourceId, resourceId);
  }

  public ResourceContactPhoto(@DrawableRes int resourceId, @DrawableRes int smallResourceId) {
    this(resourceId, smallResourceId, resourceId);
  }

  public ResourceContactPhoto(@DrawableRes int resourceId, @DrawableRes int smallResourceId, @DrawableRes int callCardResourceId) {
    this.resourceId         = resourceId;
    this.callCardResourceId = callCardResourceId;
    this.smallResourceId    = smallResourceId;
  }

  @Override
  public Drawable asDrawable(Context context, int color) {
    return asDrawable(context, color, false);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    return buildDrawable(context, resourceId, color, inverted);
  }

  @Override
  public Drawable asSmallDrawable(Context context, int color, boolean inverted) {
    return buildDrawable(context, smallResourceId, color, inverted);
  }

  private Drawable buildDrawable(Context context, int resourceId, int color, boolean inverted) {
    Drawable        background = TextDrawable.builder().buildRound(" ", inverted ? Color.WHITE : color);
    RoundedDrawable foreground = (RoundedDrawable) RoundedDrawable.fromDrawable(AppCompatResources.getDrawable(context, resourceId));

    foreground.setScaleType(ImageView.ScaleType.CENTER);

    if (inverted) {
      foreground.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
    }

    Drawable gradient = context.getResources().getDrawable(ThemeUtil.isDarkTheme(context) ? R.drawable.avatar_gradient_dark
                                                                                          : R.drawable.avatar_gradient_light);

    return new ExpandingLayerDrawable(new Drawable[] {background, foreground, gradient});
  }

  @Override
  public Drawable asCallCard(Context context) {
    return AppCompatResources.getDrawable(context, callCardResourceId);
  }

  private static class ExpandingLayerDrawable extends LayerDrawable {
    public ExpandingLayerDrawable(Drawable[] layers) {
      super(layers);
    }

    @Override
    public int getIntrinsicWidth() {
      return -1;
    }

    @Override
    public int getIntrinsicHeight() {
      return -1;
    }
  }

}
