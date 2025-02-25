package com.tapmedia.yoush.components.emoji;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.components.emoji.MediaKeyboardProvider.TabIconProvider;
import com.tapmedia.yoush.mms.GlideRequests;

public class MediaKeyboardBottomTabAdapter extends RecyclerView.Adapter<MediaKeyboardBottomTabAdapter.MediaKeyboardBottomTabViewHolder>  {

  private final GlideRequests glideRequests;
  private final EventListener eventListener;
  private final boolean       highlightTop;

  private TabIconProvider tabIconProvider;
  private int             activePosition;
  private int             count;

  public MediaKeyboardBottomTabAdapter(@NonNull GlideRequests glideRequests, @NonNull EventListener eventListener, boolean highlightTop) {
    this.glideRequests = glideRequests;
    this.eventListener = eventListener;
    this.highlightTop  = highlightTop;
  }

  @Override
  public @NonNull MediaKeyboardBottomTabViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new MediaKeyboardBottomTabViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.media_keyboard_bottom_tab_item, viewGroup, false),
                                                highlightTop);
  }

  @Override
  public void onBindViewHolder(@NonNull MediaKeyboardBottomTabViewHolder viewHolder, int i) {
    viewHolder.bind(glideRequests, eventListener, tabIconProvider, i, i == activePosition);
  }

  @Override
  public void onViewRecycled(@NonNull MediaKeyboardBottomTabViewHolder holder) {
    holder.recycle();
  }

  @Override
  public int getItemCount() {
    return count;
  }

  public void setTabIconProvider(@NonNull TabIconProvider iconProvider, int count) {
    this.tabIconProvider = iconProvider;
    this.count           = count;

    notifyDataSetChanged();
  }

  public void setActivePosition(int position) {
    this.activePosition = position;
    notifyDataSetChanged();
  }

  static class MediaKeyboardBottomTabViewHolder extends RecyclerView.ViewHolder {

    private final ImageView image;
    private final View      indicator;

    public MediaKeyboardBottomTabViewHolder(@NonNull View itemView, boolean highlightTop) {
      super(itemView);

      View indicatorTop    = itemView.findViewById(R.id.media_keyboard_top_tab_indicator);
      View indicatorBottom = itemView.findViewById(R.id.media_keyboard_bottom_tab_indicator);

      this.image     = itemView.findViewById(R.id.media_keyboard_bottom_tab_image);
      this.indicator = highlightTop ? indicatorTop : indicatorBottom;

      this.indicator.setVisibility(View.VISIBLE);
    }

    void bind(@NonNull GlideRequests glideRequests,
              @NonNull EventListener eventListener,
              @NonNull TabIconProvider tabIconProvider,
              int index,
              boolean selected)
    {
      tabIconProvider.loadCategoryTabIcon(glideRequests, image, index);
      image.setAlpha(selected ? 1 : 0.5f);
      image.setSelected(selected);

      indicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);

      itemView.setOnClickListener(v -> eventListener.onTabSelected(index));
    }

    void recycle() {
      itemView.setOnClickListener(null);
    }
  }

  interface EventListener {
    void onTabSelected(int index);
  }
}
