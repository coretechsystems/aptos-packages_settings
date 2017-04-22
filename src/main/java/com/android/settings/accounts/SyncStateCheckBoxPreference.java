package com.android.settings.accounts;

import android.accounts.Account;
import android.app.ActivityManager;
import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.widget.AnimatedImageView;

public class SyncStateCheckBoxPreference extends CheckBoxPreference {
    private Account mAccount;
    private String mAuthority;
    private boolean mFailed = false;
    private boolean mIsActive = false;
    private boolean mIsPending = false;
    private boolean mOneTimeSyncMode = false;

    public SyncStateCheckBoxPreference(Context context, Account account, String authority) {
        super(context, null);
        this.mAccount = account;
        this.mAuthority = authority;
        setWidgetLayoutResource(R.layout.preference_widget_sync_toggle);
    }

    public void onBindView(View view) {
        boolean activeVisible;
        int i;
        boolean failedVisible;
        super.onBindView(view);
        AnimatedImageView syncActiveView = (AnimatedImageView) view.findViewById(R.id.sync_active);
        View syncFailedView = view.findViewById(R.id.sync_failed);
        if (this.mIsActive || this.mIsPending) {
            activeVisible = true;
        } else {
            activeVisible = false;
        }
        if (activeVisible) {
            i = 0;
        } else {
            i = 8;
        }
        syncActiveView.setVisibility(i);
        syncActiveView.setAnimating(this.mIsActive);
        if (!this.mFailed || activeVisible) {
            failedVisible = false;
        } else {
            failedVisible = true;
        }
        if (failedVisible) {
            i = 0;
        } else {
            i = 8;
        }
        syncFailedView.setVisibility(i);
        View checkBox = view.findViewById(16908289);
        if (this.mOneTimeSyncMode) {
            checkBox.setVisibility(8);
            ((TextView) view.findViewById(16908304)).setText(getContext().getString(R.string.sync_one_time_sync, new Object[]{getSummary()}));
            return;
        }
        checkBox.setVisibility(0);
    }

    public void setActive(boolean isActive) {
        this.mIsActive = isActive;
        notifyChanged();
    }

    public void setPending(boolean isPending) {
        this.mIsPending = isPending;
        notifyChanged();
    }

    public void setFailed(boolean failed) {
        this.mFailed = failed;
        notifyChanged();
    }

    public void setOneTimeSyncMode(boolean oneTimeSyncMode) {
        this.mOneTimeSyncMode = oneTimeSyncMode;
        notifyChanged();
    }

    public boolean isOneTimeSyncMode() {
        return this.mOneTimeSyncMode;
    }

    protected void onClick() {
        if (!this.mOneTimeSyncMode) {
            if (ActivityManager.isUserAMonkey()) {
                Log.d("SyncState", "ignoring monkey's attempt to flip sync state");
            } else {
                super.onClick();
            }
        }
    }

    public Account getAccount() {
        return this.mAccount;
    }

    public String getAuthority() {
        return this.mAuthority;
    }
}
