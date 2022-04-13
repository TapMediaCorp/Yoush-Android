package com.tapmedia.yoush.conversation.background;

import android.text.TextUtils;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.mms.GlideApp;
import com.tapmedia.yoush.ui.base.BaseRecyclerAdapter;
import com.tapmedia.yoush.ui.base.BaseViewHolder;


class ConversationBackgroundAdapter extends BaseRecyclerAdapter<String> {

    @Override
    protected int getLayoutResource() {
        return R.layout.conversation_background_item;
    }

    @Override
    public void onBindHolder(BaseViewHolder holder, String item, int position) {
        ImageView imageView = holder.findViewById(R.id.imageView);
        if (TextUtils.isEmpty(item)) {
            imageView.setImageResource(R.color.colorWhite);
            return;
        }
        GlideApp.with(ApplicationContext.getInstance())
                .load(item)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(imageView.getWidth(), imageView.getHeight())
                .skipMemoryCache(false)
                .into(imageView);
    }
}
