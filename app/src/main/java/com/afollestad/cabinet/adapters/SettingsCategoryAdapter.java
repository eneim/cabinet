package com.afollestad.cabinet.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.ui.base.ThemableActivity;
import com.afollestad.materialdialogs.util.DialogUtils;

/**
 * @author Aidan Follestad (afollestad)
 */
public class SettingsCategoryAdapter extends RecyclerView.Adapter<SettingsCategoryAdapter.CategoryViewHolder> implements View.OnClickListener {

    public SettingsCategoryAdapter(ThemableActivity context, boolean tablet, int index, ClickListener callback) {
        mActivated = tablet ? index : -1;
        mActivatedColor = context.getThemeUtils().accentColor();
        mNormalColor = DialogUtils.resolveColor(context, android.R.attr.textColorPrimary);
        mItems = context.getResources().getStringArray(R.array.settings_categories);
        mCallback = callback;
    }

    private int mActivated;
    private final int mActivatedColor;
    private final int mNormalColor;
    private final String[] mItems;
    private final ClickListener mCallback;

    @Override
    public CategoryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_category, parent, false);
        return new CategoryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(CategoryViewHolder holder, int position) {
        holder.title.setText(mItems[position]);
        final boolean isActivated = mActivated == position;
        holder.title.setTextColor(isActivated ? mActivatedColor : mNormalColor);
        holder.view.setActivated(isActivated);
        holder.view.setTag(position);
        holder.view.setOnClickListener(this);
    }

    @Override
    public int getItemCount() {
        return mItems != null ? mItems.length : 0;
    }

    @Override
    public void onClick(View v) {
        if (v.getTag() != null) {
            int index = (Integer) v.getTag();
            if (mActivated > -1) {
                int old = mActivated;
                mActivated = index;
                notifyItemChanged(mActivated);
                notifyItemChanged(old);
            }
            if (mCallback != null)
                mCallback.onClick(index);
        }
    }

    public class CategoryViewHolder extends RecyclerView.ViewHolder {

        final View view;
        final TextView title;

        public CategoryViewHolder(View itemView) {
            super(itemView);
            this.view = itemView;
            this.title = (TextView) itemView.findViewById(R.id.title);
        }
    }

    public interface ClickListener {
        void onClick(int index);
    }
}