package com.android.settings.applications;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.applications.ApplicationsState.AppEntry;

public class AppViewHolder {
    public ImageView appIcon;
    public TextView appName;
    public TextView appSize;
    public CheckBox checkBox;
    public TextView disabled;
    public AppEntry entry;
    public View rootView;

    public static AppViewHolder createOrRecycle(LayoutInflater inflater, View convertView) {
        if (convertView != null) {
            return (AppViewHolder) convertView.getTag();
        }
        convertView = inflater.inflate(R.layout.manage_applications_item, null);
        AppViewHolder holder = new AppViewHolder();
        holder.rootView = convertView;
        holder.appName = (TextView) convertView.findViewById(R.id.app_name);
        holder.appIcon = (ImageView) convertView.findViewById(R.id.app_icon);
        holder.appSize = (TextView) convertView.findViewById(R.id.app_size);
        holder.disabled = (TextView) convertView.findViewById(R.id.app_disabled);
        holder.checkBox = (CheckBox) convertView.findViewById(R.id.app_on_sdcard);
        convertView.setTag(holder);
        return holder;
    }

    void updateSizeText(CharSequence invalidSizeStr, int whichSize) {
        if (this.entry.sizeStr != null) {
            switch (whichSize) {
                case 1:
                    this.appSize.setText(this.entry.internalSizeStr);
                    return;
                case 2:
                    this.appSize.setText(this.entry.externalSizeStr);
                    return;
                default:
                    this.appSize.setText(this.entry.sizeStr);
                    return;
            }
        } else if (this.entry.size == -2) {
            this.appSize.setText(invalidSizeStr);
        }
    }
}
