package com.afollestad.cabinet.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.ui.base.ThemableActivity;

/**
 * @author Aidan Follestad (afollestad)
 */
public class ArchiveSubFileAdapter extends FileAdapter {

    public interface ItemClickListener {
        void onClick(int index);
    }

    public ArchiveSubFileAdapter(ThemableActivity context, final ItemClickListener listener) {
        super(context, new FileAdapter.ItemClickListener() {
            @Override
            public void onItemClicked(int index, File file) {
                listener.onClick(index);
            }

            @Override
            public void onItemLongClick(int index, File file, boolean added) {
            }
        }, null, false);
    }

    @Override
    public FileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_file, parent, false);
        FileViewHolder fileViewHolder = new FileViewHolder(v) {
            @Override
            public void onClick(View view) {
                mListener.onItemClicked(getLayoutPosition(), null);
            }

            @Override
            public boolean onLongClick(View view) {
                return false;
            }
        };
        fileViewHolder.menu.setVisibility(View.GONE);
        fileViewHolder.content2.setVisibility(View.GONE);
        return fileViewHolder;
    }
}