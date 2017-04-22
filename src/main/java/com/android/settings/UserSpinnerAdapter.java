package com.android.settings;

import android.content.Context;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import com.android.internal.util.UserIcons;
import java.util.ArrayList;

public class UserSpinnerAdapter implements SpinnerAdapter {
    private ArrayList<UserDetails> data;
    private final LayoutInflater mInflater;

    public static class UserDetails {
        private final Drawable icon;
        private final UserHandle mUserHandle;
        private final String name;

        public UserDetails(UserHandle userHandle, UserManager um, Context context) {
            this.mUserHandle = userHandle;
            UserInfo userInfo = um.getUserInfo(this.mUserHandle.getIdentifier());
            if (userInfo.isManagedProfile()) {
                this.name = context.getString(R.string.managed_user_title);
                this.icon = Resources.getSystem().getDrawable(17302357);
                return;
            }
            this.name = userInfo.name;
            int userId = userInfo.id;
            if (um.getUserIcon(userId) != null) {
                this.icon = new BitmapDrawable(context.getResources(), um.getUserIcon(userId));
            } else {
                this.icon = UserIcons.getDefaultUserIcon(userId, false);
            }
        }
    }

    public UserSpinnerAdapter(Context context, ArrayList<UserDetails> users) {
        if (users == null) {
            throw new IllegalArgumentException("A list of user details must be provided");
        }
        this.data = users;
        this.mInflater = (LayoutInflater) context.getSystemService("layout_inflater");
    }

    public UserHandle getUserHandle(int position) {
        if (position < 0 || position >= this.data.size()) {
            return null;
        }
        return ((UserDetails) this.data.get(position)).mUserHandle;
    }

    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        View row = convertView != null ? convertView : createUser(parent);
        UserDetails user = (UserDetails) this.data.get(position);
        ((ImageView) row.findViewById(16908294)).setImageDrawable(user.icon);
        ((TextView) row.findViewById(16908310)).setText(user.name);
        return row;
    }

    private View createUser(ViewGroup parent) {
        return this.mInflater.inflate(R.layout.user_preference, parent, false);
    }

    public void registerDataSetObserver(DataSetObserver observer) {
    }

    public void unregisterDataSetObserver(DataSetObserver observer) {
    }

    public int getCount() {
        return this.data.size();
    }

    public UserDetails getItem(int position) {
        return (UserDetails) this.data.get(position);
    }

    public long getItemId(int position) {
        return (long) ((UserDetails) this.data.get(position)).mUserHandle.getIdentifier();
    }

    public boolean hasStableIds() {
        return false;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        return getDropDownView(position, convertView, parent);
    }

    public int getItemViewType(int position) {
        return 0;
    }

    public int getViewTypeCount() {
        return 1;
    }

    public boolean isEmpty() {
        return this.data.isEmpty();
    }
}
