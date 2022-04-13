package com.tapmedia.yoush.ui.base;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

public abstract class BasePagedAdapter<T> extends PagedListAdapter<T, RecyclerView.ViewHolder> {
    protected BasePagedAdapter(@NonNull @NotNull DiffUtil.ItemCallback<T> diffCallback) {
        super(diffCallback);
    }

    protected BasePagedAdapter(@NonNull @NotNull AsyncDifferConfig<T> config) {
        super(config);
    }

    @Override
    public int getItemViewType(int position) {
        return itemLayoutResource(getItem(position), position);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return null;
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull RecyclerView.ViewHolder holder, int position) {

    }

    public abstract int itemLayoutResource(T item, int position);
}
