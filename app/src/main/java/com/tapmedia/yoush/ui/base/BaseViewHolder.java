package com.tapmedia.yoush.ui.base;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;


public class BaseViewHolder extends RecyclerView.ViewHolder {

    public BaseViewHolder(View itemView) {
        super(itemView);
    }

    public View view(@IdRes int id) {
        return (View) itemView.findViewById(id);
    }

    public TextView text(@IdRes int id) {
        return findViewById(id);
    }

    public ImageView imageView(@IdRes int id) {
        return findViewById(id);
    }

    public <T extends View> T findViewById(@IdRes int id) {
        return (T) itemView.findViewById(id);
    }

    public Context context() {
        return itemView.getContext();
    }

}