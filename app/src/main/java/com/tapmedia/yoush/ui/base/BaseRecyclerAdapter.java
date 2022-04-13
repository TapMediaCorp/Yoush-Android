package com.tapmedia.yoush.ui.base;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseRecyclerAdapter<T> extends RecyclerView.Adapter<BaseViewHolder> {

    private ArrayList<T> listItem = new ArrayList<>();

    protected  BaseViewHolder getViewHolder(View v){
        return new BaseViewHolder(v);
    }

    protected abstract int getLayoutResource();

    private OnItemClickListener<T> itemClickListener;

    @Override
    public int getItemViewType(int position) {
        return getLayoutResource();
    }

    public abstract void onBindHolder(BaseViewHolder holder, T item, int position);

    @NonNull
    @Override
    public BaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(viewType, parent, false);
        return getViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BaseViewHolder holder, int position) {
        T item = (position > -1 && position < size()) ? listItem.get(position) : null;
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(item, position);
            }
        });
        onBindHolder(holder, item, position);
    }

    @Override
    public int getItemCount() {
        return size();
    }

    public ArrayList<T> getListItem() {
        return listItem;
    }

    public void setListItem(List<T> list) {
        listItem = new ArrayList<>(list);
        notifyDataSetChanged();
    }

    public void addItem(int index, T item) {
        listItem.add(index, item);
        notifyDataSetChanged();
    }

    public void addItem(T item) {
        listItem.add(item);
        notifyDataSetChanged();
    }

    public void removeItem(T item) {
        listItem.remove(item);
        notifyDataSetChanged();
    }

    public void removeItem(int index) {
        getListItem().remove(index);
        notifyItemRemoved(index);
    }

    public T get(int position) {
        if (position >= 0 && position < size()) {
            return listItem.get(position);
        }
        return null;
    }

    public void setOnItemClickListener(OnItemClickListener<T> listener) {
        itemClickListener = listener;
    }

    public boolean has(T item) {
        return listItem.indexOf(item) != -1;
    }

    public int size() {
        return listItem.size();
    }

    public interface OnItemClickListener<T> {
        void onItemClick(T item, int position);
    }

    public void bind(RecyclerView recyclerView,int spanCount){
        GridLayoutManager lm = new GridLayoutManager(recyclerView.getContext(),spanCount);
        recyclerView.setLayoutManager(lm);
        recyclerView.setAdapter(this);
    }
}
