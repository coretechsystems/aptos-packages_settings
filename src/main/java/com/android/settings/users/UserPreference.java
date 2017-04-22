package com.android.settings.users;

import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import com.android.settings.R;

public class UserPreference extends Preference {
    private OnClickListener mDeleteClickListener;
    private int mSerialNumber = -1;
    private OnClickListener mSettingsClickListener;
    private int mUserId = -10;

    UserPreference(Context context, AttributeSet attrs, int userId, OnClickListener settingsListener, OnClickListener deleteListener) {
        super(context, attrs);
        if (!(deleteListener == null && settingsListener == null)) {
            setWidgetLayoutResource(R.layout.preference_user_delete_widget);
        }
        this.mDeleteClickListener = deleteListener;
        this.mSettingsClickListener = settingsListener;
        this.mUserId = userId;
    }

    protected void onBindView(View view) {
        UserManager um = (UserManager) getContext().getSystemService("user");
        View deleteDividerView = view.findViewById(R.id.divider_delete);
        View manageDividerView = view.findViewById(R.id.divider_manage);
        View deleteView = view.findViewById(R.id.trash_user);
        if (deleteView != null) {
            if (this.mDeleteClickListener == null || um.hasUserRestriction("no_remove_user")) {
                deleteView.setVisibility(8);
                deleteDividerView.setVisibility(8);
            } else {
                deleteView.setOnClickListener(this.mDeleteClickListener);
                deleteView.setTag(this);
            }
        }
        View manageView = view.findViewById(R.id.manage_user);
        if (manageView != null) {
            if (this.mSettingsClickListener != null) {
                manageView.setOnClickListener(this.mSettingsClickListener);
                manageView.setTag(this);
                if (this.mDeleteClickListener != null) {
                    manageDividerView.setVisibility(8);
                }
            } else {
                manageView.setVisibility(8);
                manageDividerView.setVisibility(8);
            }
        }
        super.onBindView(view);
    }

    private int getSerialNumber() {
        if (this.mUserId == UserHandle.myUserId()) {
            return Integer.MIN_VALUE;
        }
        if (this.mSerialNumber < 0) {
            if (this.mUserId == -10) {
                return Integer.MAX_VALUE;
            }
            if (this.mUserId == -11) {
                return 2147483646;
            }
            this.mSerialNumber = ((UserManager) getContext().getSystemService("user")).getUserSerialNumber(this.mUserId);
            if (this.mSerialNumber < 0) {
                return this.mUserId;
            }
        }
        return this.mSerialNumber;
    }

    public int getUserId() {
        return this.mUserId;
    }

    public int compareTo(Preference another) {
        if (!(another instanceof UserPreference) || getSerialNumber() > ((UserPreference) another).getSerialNumber()) {
            return 1;
        }
        return -1;
    }
}
