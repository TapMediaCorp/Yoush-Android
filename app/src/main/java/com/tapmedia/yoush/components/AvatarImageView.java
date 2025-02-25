package com.tapmedia.yoush.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.color.MaterialColor;
import com.tapmedia.yoush.contacts.avatars.ContactColors;
import com.tapmedia.yoush.contacts.avatars.ContactPhoto;
import com.tapmedia.yoush.contacts.avatars.ResourceContactPhoto;
import com.tapmedia.yoush.groups.ui.managegroup.ManageGroupActivity;
import com.tapmedia.yoush.mms.GlideApp;
import com.tapmedia.yoush.mms.GlideRequests;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;
import com.tapmedia.yoush.recipients.ui.managerecipient.ManageRecipientActivity;
import com.tapmedia.yoush.ui.base.ViewClickListener;
import com.tapmedia.yoush.util.AvatarUtil;
import com.tapmedia.yoush.util.DevLogger;
import com.tapmedia.yoush.util.ThemeUtil;

import java.util.Objects;

public final class AvatarImageView extends AppCompatImageView {

  private static final int SIZE_LARGE = 1;
  private static final int SIZE_SMALL = 2;

  @SuppressWarnings("unused")
  private static final String TAG = AvatarImageView.class.getSimpleName();

  private static final Paint LIGHT_THEME_OUTLINE_PAINT = new Paint();
  private static final Paint DARK_THEME_OUTLINE_PAINT  = new Paint();

  static {
    LIGHT_THEME_OUTLINE_PAINT.setColor(Color.argb((int) (255 * 0.2), 0, 0, 0));
    LIGHT_THEME_OUTLINE_PAINT.setStyle(Paint.Style.STROKE);
    LIGHT_THEME_OUTLINE_PAINT.setStrokeWidth(1);
    LIGHT_THEME_OUTLINE_PAINT.setAntiAlias(true);

    DARK_THEME_OUTLINE_PAINT.setColor(Color.argb((int) (255 * 0.2), 255, 255, 255));
    DARK_THEME_OUTLINE_PAINT.setStyle(Paint.Style.STROKE);
    DARK_THEME_OUTLINE_PAINT.setStrokeWidth(1);
    DARK_THEME_OUTLINE_PAINT.setAntiAlias(true);
  }

  private int                             size;
  private boolean                         inverted;
  private Paint                           outlinePaint;
  private OnClickListener                 listener;
  private Recipient.FallbackPhotoProvider fallbackPhotoProvider;

  private @Nullable RecipientContactPhoto recipientContactPhoto;
  private @NonNull  Drawable              unknownRecipientDrawable;

  public AvatarImageView(Context context) {
    super(context);
    initialize(context, null);
  }

  public AvatarImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(context, attrs);
  }

  private void initialize(@NonNull Context context, @Nullable AttributeSet attrs) {
    setScaleType(ScaleType.CENTER_CROP);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AvatarImageView, 0, 0);
      inverted              = typedArray.getBoolean(R.styleable.AvatarImageView_inverted, false);
      size                  = typedArray.getInt(R.styleable.AvatarImageView_fallbackImageSize, SIZE_LARGE);
      typedArray.recycle();
    }

    outlinePaint = ThemeUtil.isDarkTheme(getContext()) ? DARK_THEME_OUTLINE_PAINT : LIGHT_THEME_OUTLINE_PAINT;

    unknownRecipientDrawable = new ResourceContactPhoto(R.drawable.ic_profile_outline_40_new, R.drawable.ic_profile_outline_20_new).asDrawable(getContext(), ContactColors.UNKNOWN_COLOR.toConversationColor(getContext()), inverted);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    float width  = getWidth()  - getPaddingRight()  - getPaddingLeft();
    float height = getHeight() - getPaddingBottom() - getPaddingTop();
    float cx     = width  / 2f;
    float cy     = height / 2f;
    float radius = Math.min(cx, cy) - (outlinePaint.getStrokeWidth() / 2f);

    canvas.translate(getPaddingLeft(), getPaddingTop());
    canvas.drawCircle(cx, cy, radius, outlinePaint);
  }

  @Override
  public void setOnClickListener(OnClickListener listener) {
    this.listener = listener;
    super.setOnClickListener(listener);
  }

  public void setFallbackPhotoProvider(Recipient.FallbackPhotoProvider fallbackPhotoProvider) {
    this.fallbackPhotoProvider = fallbackPhotoProvider;
  }

  /**
   * Shows self as the actual profile picture.
   */
  public void setRecipient(@NonNull Recipient recipient) {
    if (recipient.isLocalNumber()) {
      setAvatar(GlideApp.with(this), null, false);
      AvatarUtil.loadIconIntoImageView(recipient, this);
    } else {
      setAvatar(GlideApp.with(this), recipient, false);
    }
  }

  /**
   * Shows self as the note to self icon.
   */
  public void setAvatar(@Nullable Recipient recipient) {
    setAvatar(GlideApp.with(this), recipient, false);
  }

  public void setAvatar(@NonNull GlideRequests requestManager, @Nullable Recipient recipient, boolean quickContactEnabled) {
    if (recipient != null) {
      RecipientContactPhoto photo = new RecipientContactPhoto(recipient);

      if (!photo.equals(recipientContactPhoto)) {
        requestManager.clear(this);
        recipientContactPhoto = photo;

        Drawable fallbackContactPhotoDrawable = size == SIZE_SMALL
            ? photo.recipient.getSmallFallbackContactPhotoDrawable(getContext(), inverted, fallbackPhotoProvider)
            : photo.recipient.getFallbackContactPhotoDrawable(getContext(), inverted, fallbackPhotoProvider);

        if (photo.contactPhoto != null) {
          requestManager.load(photo.contactPhoto)
                        .fallback(fallbackContactPhotoDrawable)
                        .error(fallbackContactPhotoDrawable)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .circleCrop()
                        .into(this);
        } else {
          setImageDrawable(fallbackContactPhotoDrawable);
        }
      }

      setAvatarClickHandler(recipient, quickContactEnabled);
    } else {
      recipientContactPhoto = null;
      requestManager.clear(this);
      if (fallbackPhotoProvider != null) {
        setImageDrawable(fallbackPhotoProvider.getPhotoForRecipientWithoutName()
                                              .asDrawable(getContext(), MaterialColor.STEEL.toAvatarColor(getContext()), inverted));
      } else {
        setImageDrawable(unknownRecipientDrawable);
      }

      super.setOnClickListener(listener);
    }
  }

  private void setAvatarClickHandler(@NonNull final Recipient recipient, boolean quickContactEnabled) {
    if (quickContactEnabled) {
      setOnClickListener(new ViewClickListener() {
        @Override
        public void onClicks(View v) {
          Context context = getContext();
          if (recipient.isPushGroup()) {
            context.startActivity(
                    ManageGroupActivity.newIntent(context, recipient.requireGroupId().requirePush()),
                    ManageGroupActivity.createTransitionBundle(context, AvatarImageView.this)
            );
            return;
          }

          if (context instanceof FragmentActivity) {
            RecipientBottomSheetDialogFragment.create(recipient.getId(), null)
                    .show(((FragmentActivity) context).getSupportFragmentManager(), "BOTTOM");
            return;
          }

          context.startActivity(
                  ManageRecipientActivity.newIntent(context, recipient.getId()),
                  ManageRecipientActivity.createTransitionBundle(context, AvatarImageView.this)
          );


        }
      });
    } else {
      super.setOnClickListener(listener);
      setClickable(listener != null);
    }
  }

  private static class RecipientContactPhoto {

    private final @NonNull  Recipient    recipient;
    private final @Nullable ContactPhoto contactPhoto;
    private final           boolean      ready;

    RecipientContactPhoto(@NonNull Recipient recipient) {
      this.recipient    = recipient;
      this.ready        = !recipient.isResolving();
      this.contactPhoto = recipient.getContactPhoto();
    }

    public boolean equals(@Nullable RecipientContactPhoto other) {
      if (other == null) return false;

      return other.recipient.equals(recipient) &&
             other.recipient.getColor().equals(recipient.getColor()) &&
             other.ready == ready &&
             Objects.equals(other.contactPhoto, contactPhoto);
    }
  }
}
