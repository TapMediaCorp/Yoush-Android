package com.tapmedia.yoush.mediapreview;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.components.ZoomingImageView;
import com.tapmedia.yoush.mms.GlideApp;
import com.tapmedia.yoush.mms.GlideRequests;
import com.tapmedia.yoush.util.MediaUtil;

public final class ImageMediaPreviewFragment extends MediaPreviewFragment {

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    ZoomingImageView zoomingImageView = (ZoomingImageView) inflater.inflate(R.layout.media_preview_image_fragment, container, false);
    GlideRequests    glideRequests    = GlideApp.with(requireActivity());
    Bundle           arguments        = requireArguments();
    Uri              uri              = arguments.getParcelable(DATA_URI);
    String           contentType      = arguments.getString(DATA_CONTENT_TYPE);

    if (!MediaUtil.isImageType(contentType)) {
      throw new AssertionError("This fragment can only display images");
    }

    //noinspection ConstantConditions
    zoomingImageView.setImageUri(glideRequests, uri, contentType);

    zoomingImageView.setOnClickListener(v -> events.singleTapOnMedia());

    return zoomingImageView;
  }
}
